# 17. 设置页：把「破坏世界」的开关交给玩家

新增两个玩法开关，用一个游戏内设置页改：

| 开关 | 关闭时 | 打开时（默认） |
| --- | --- | --- |
| **基岩：破坏 / 无视** | 基岩挡得住柱子（1 号行为本来就挡得住） | 除 1 号行为外，柱子连基岩一起清 |
| **爆炸破坏方块：是 / 否** | 爆炸只伤生物，不炸地形 | 爆炸同 TNT，破坏地形 |

打开方式：默认 **K** 键（可在「选项 → 按键控制 → New Mod」里改）。

## 1. 为什么这两个开关必须放在服务端

「能不能挖穿基岩」「爆炸炸不炸地形」都会**改写世界**，而世界由服务端说了算。
如果只改客户端，多人游戏里别人看到的世界就跟你不一样了。所以结构是：

```
设置页（客户端 UI）
   ├─ 写 config/new-mod.json      ← 记住你的选择
   └─ 发 settings 包 ──→ 服务端 Settings（内存）
                            ├─ 柱子 breaksUnbreakable()
                            └─ 爆炸用 TNT 还是 MOB
```

服务端只存内存、不写存档：客户端**每次进世界/服务器都会重发一次**，所以两边永远一致，
也不会出现「配置文件跟存档对不上」的问题。

## 2. 服务端：两个静态开关 + 一个「口径」函数

```java
public final class Settings {
	private static boolean breakBedrock = DEFAULT_BREAK_BEDROCK;
	private static boolean explosionBreaksBlocks = DEFAULT_EXPLOSION_BREAKS_BLOCKS;

	/** 当前设置下爆炸该用哪种交互：破坏方块用 TNT 口径，不破坏则只伤生物。 */
	public static Level.ExplosionInteraction explosionInteraction() {
		return explosionBreaksBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.MOB;
	}
}
```

两个应用点：

```java
// ① 柱子能不能破基岩：类型开关 与 全局开关 同时成立才行
public boolean breaksUnbreakable() {
	return Settings.breakBedrock() && this.spec().breaksUnbreakable();
}

// ② 爆炸口径
level.explode(this, impact.x, impact.y, impact.z, radius, Settings.explosionInteraction());
```

> 注意 `ExplosionInteraction` 的四种口径：`TNT`（破坏 + 伤害）、`BLOCK`（只破坏）、
> `MOB`（只伤害）、`NONE`（都不）。「不破坏地形但要保留伤害」正好是 `MOB`。

## 3. 网络包：两个布尔

```java
public record SettingsPayload(boolean breakBedrock, boolean explosionBreaksBlocks)
		implements CustomPacketPayload {

	public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, SettingsPayload::breakBedrock,
					ByteBufCodecs.BOOL, SettingsPayload::explosionBreaksBlocks,
					SettingsPayload::new);
}
```

服务端收到就直接落到开关上（`Settings.apply(...)`），不需要任何权限判断——
这是单机/小服自用模组，设置页本来就是给玩家自己用的。

## 4. 客户端：设置页 + 记住 + 同步

```java
public class ModSettingsScreen extends Screen {
	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(label("option.new-mod.break_bedrock", ClientSettings.breakBedrock()),
				button -> {
					ClientSettings.setBreakBedrock(!ClientSettings.breakBedrock());
					button.setMessage(label("option.new-mod.break_bedrock", ClientSettings.breakBedrock()));
					ClientSettings.saveAndSend();      // 存盘 + 立刻通知服务端
				}).bounds(x, y, 220, 20).build());
		...
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);     // 关掉回上一页（从游戏里开就是回到游戏）
	}
}
```

三个容易踩的点：

1. **26.3 里 `Minecraft` 没有公开的 `setScreen`**：屏幕在 `Minecraft.gui` 上，
   所以要写 `client.gui.setScreen(screen)`，读当前屏幕用 `client.gui.screen()`。
2. **按钮文字用「翻译键 + 参数」**：`option.new-mod.break_bedrock` = `基岩：%s`，
   再按值取后缀键 `.on`（破坏）/ `.off`（无视），于是中英文各两行就够。
3. **关掉屏幕要走 `onClose()`** 而不是自己 `setScreen(null)`，否则会漏掉原版的善后逻辑。

## 5. 26.3 的按键绑定 API 变了（重要）

写这个设置页时踩到的两个变化，记下来省得再查：

| 事项 | 旧写法 | 26.3 写法 |
| --- | --- | --- |
| 注册按键的 Fabric 助手 | `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper#registerKeyBinding` | **`net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper#registerKeyMapping`**（模块也改名成 `fabric-key-mapping-api-v1`） |
| 按键分类 | 一个 `String` 分类名 | **`KeyMapping.Category`**（`KeyMapping.Category.register(Identifier)`，用 `KeyMapping.Category.MISC` 也行） |
| 按键码 | `GLFW.GLFW_KEY_K` | **`InputConstants.KEY_K`**（26.3 起输入后端换成 SDL，用的是 **SDL scancode**：`KEY_A = 4`、`KEY_W = 26`、`KEY_K = 14`） |

所以本模组的写法是：

```java
private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(NewMod.id("main"));

settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.new-mod.settings", InputConstants.KEY_K, KEY_CATEGORY));

ClientTickEvents.END_CLIENT_TICK.register(client -> {
	while (settingsKey.consumeClick()) {
		if (client.gui.screen() == null && client.player != null) {
			client.gui.setScreen(new ModSettingsScreen(null));
		}
	}
});
```

`KeyMapping.Category.register` **重复注册会抛异常**，所以分类要放在静态常量里只注册一次。

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 设置改了但没用 | 只改了客户端；破坏世界的行为必须在服务端生效（要发包） |
| 重进游戏设置又变回默认 | 没写 `config/new-mod.json`；或服务端重启了而客户端没重发（进世界时发一次即可） |
| 配置文件坏了游戏进不去 | `load()` 没做异常兜底；坏配置应该退回默认值 |
| 按键里找不到、或按键无效 | 用了旧的 `KeyBindingHelper` / `GLFW.GLFW_KEY_*`（26.3 已改名/换后端） |
| 屏幕打不开 | `Minecraft` 没有公开 `setScreen`；要用 `client.gui.setScreen(...)` |
| 启动时崩：`Category 'xxx' is already registered` | `KeyMapping.Category.register` 被调用了两次（放进静态常量） |

## 7. 小结

- **凡是会改写世界的开关，判定必须在服务端**；客户端只负责 UI 与把选择发过去。
- 服务端不持久化、客户端每次进服重发，是「单机自用」场景下最省事又不会错位的做法。
- 客户端的持久化（`config/new-mod.json`）只承担「记住上次的选择」，坏了也不该拦住进游戏。
- 26.3 的界面与按键 API 变化不小：`Minecraft.gui.screen()`、`KeyMappingHelper`、`KeyMapping.Category`、
  `InputConstants.KEY_*`（SDL scancode）——照旧写法会直接编译不过。
