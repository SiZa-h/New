# 12. 技能冷却的独立计算

黑方块之星有两个技能（左键短柱、右键长柱）。最初的实现直接用原版冷却：

```java
player.getCooldowns().addCooldown(stack, 20);   // ← 有问题
```

结果就是**放完左键，右键也一起进冷却**，反之亦然。本篇讲清原因，以及「独立计算」的正确做法。

> 后来行为增加到**六种**（左键短柱、右键长柱、四个手势对撞柱），机制一行没改——只是把 `Slot`
> 枚举写全、每个行为在自己的调用点判定与记账，见 [learn/16](16-six-cooldown-slots-and-mark-barrage.md)。

## 1. 为什么原版冷却做不到两套

`ItemCooldowns` 内部是「冷却组 → 结束刻」的映射，而一个物品的冷却组是固定的：

```java
public boolean isOnCooldown(final ItemStack item) { return this.getCooldownPercent(item, 0.0F) > 0.0F; }

public Identifier getCooldownGroup(final ItemStack item) {
	UseCooldown useCooldown = item.get(DataComponents.USE_COOLDOWN);
	Identifier defaultItemGroup = BuiltInRegistries.ITEM.getKey(item.getItem());
	return useCooldown == null ? defaultItemGroup : useCooldown.cooldownGroup().orElse(defaultItemGroup);
}
```

- 冷却组来自 **物品的 `USE_COOLDOWN` 组件**，或者退化成**物品 id**；
- 组件是「物品」级别的数据，**同一个物品的两个技能无法拥有两个不同的冷却组**；
- 而且公开 API 只有 `isOnCooldown(ItemStack)`——**没有**「按冷却组查询」的方法，想绕过也绕不了。

所以「左键 / 右键各自独立」必须自己记账。

## 2. 自己记账：玩家 + 物品 + 技能槽

```java
public final class WeaponCooldowns {
	public enum Slot {
		LEFT,
		RIGHT
	}

	/** 玩家 UUID → （物品+槽位 → 可再次使用的游戏刻）。 */
	private static final Map<UUID, Map<String, Long>> READY_AT = new HashMap<>();

	public static boolean isReady(ServerPlayer player, ItemStack stack, Slot slot) {
		Map<String, Long> slots = READY_AT.get(player.getUUID());
		if (slots == null) return true;

		Long readyAt = slots.get(key(stack, slot));
		return readyAt == null || readyAt <= player.level().getGameTime();
	}

	public static void apply(ServerPlayer player, ItemStack stack, Slot slot, int ticks) {
		READY_AT.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
				.put(key(stack, slot), player.level().getGameTime() + ticks);
	}

	public static void clear(UUID playerId) {
		READY_AT.remove(playerId);
	}

	private static String key(ItemStack stack, Slot slot) {
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return itemId + "/" + slot.name().toLowerCase(Locale.ROOT);
	}
}
```

四个设计点：

1. **键里有物品**，所以黑方块之星与黑曜石方的冷却**也互不影响**（换武器不会串冷却）；
2. **键里有槽位**，所以左键与右键互不影响——这就是「独立计算」；
3. **用游戏刻（`level.getGameTime()`）而不是墙上时间**：受暂停、`/tick` 控制，也不会因为系统时间跳变而出错；
4. **用 `UUID` 而不是 `Player` 实例**：玩家重连后是同一个 UUID，记录不会因为掉线而失效或泄漏（实例作为 key 还会阻止 GC）。

调用点：

```java
// 左键（服务端）
if (!WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.LEFT)) return false;
... 生成柱子 ...
WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.LEFT, FIRE_COOLDOWN_TICKS);

// 右键（服务端）
if (!WeaponCooldowns.isReady(serverPlayer, stack, WeaponCooldowns.Slot.RIGHT)) return InteractionResult.SUCCESS;
... 生成柱子 ...
WeaponCooldowns.apply(serverPlayer, stack, WeaponCooldowns.Slot.RIGHT, LONG_FIRE_COOLDOWN_TICKS);

// 玩家退出时清账
ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> WeaponCooldowns.clear(listener.player.getUUID()));
```

## 3. 客户端只做「视觉冷却」

原版物品冷却还兼职一件事：在物品栏上画那个转圈的**冷却动画**。既然判定不收它管了，就把它降级成纯视觉：

```java
// 左键：客户端按键回调里
player.getCooldowns().addCooldown(stack, ModNetworking.FIRE_COOLDOWN_TICKS);

// 右键：fireLongPillar 的客户端分支里（事件两端都会触发，客户端这次就是用来画动画的）
if (level.isClientSide()) {
	if (hand == InteractionHand.MAIN_HAND
			&& player.getMainHandItem().getItem() instanceof PillarWeaponItem weapon
			&& weapon.kind() == PillarKind.BLACK_BLOCK) {
		player.getCooldowns().addCooldown(player.getMainHandItem(), LONG_FIRE_COOLDOWN_TICKS);
	}
	return InteractionResult.PASS;    // 放行，让原版把使用包发给服务端
}
```

要点：

- **客户端不能再拿原版冷却做门禁**。之前左键回调里有 `if (player.getCooldowns().isOnCooldown(stack)) return true;`，
  那会让「右键的动画」把左键也一起挡住——判定与视觉就又不独立了。现在客户端只发请求，服务端说了算；
- 视觉上**同一个物品只有一个转圈动画**（这是原版限制），所以连续放两个技能时，动画显示的是最近一次的那个冷却。
  判定不受影响；
- 服务端**不再**调用 `addCooldown`，避免服务端下发的冷却包把客户端的动画值覆盖成另一个技能的长度。

## 4. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 左键冷却 | `ModNetworking.FIRE_COOLDOWN_TICKS`（默认 **60 tick = 3 秒**） |
| 右键冷却 | `ModNetworking.LONG_FIRE_COOLDOWN_TICKS`（默认 20 tick） |
| 让两把武器共享冷却 | `WeaponCooldowns.key(...)` 里去掉 `itemId` |
| 让冷却在退出后保留 | 把 `READY_AT` 换成 Fabric 的 Attachment（`AttachmentRegistry.createPersistent(...)`）并去掉 DISCONNECT 清理 |
| 完全不要冷却 | `apply(...)` 里传 `0`，或调用点直接跳过判定 |

## 5. 常见坑

| 现象 | 原因 |
| --- | --- |
| 放了左键，右键也不能用 | 还在用 `player.getCooldowns()` 当门禁（它按物品记账） |
| 客户端能连点，服务端不理 | 正常：客户端只做视觉，判定在服务端；若嫌包多，可在客户端加一个「纯视觉冷却」判断来抑制发送（但那会让左右键互相影响） |
| 冷却时间在暂停后不走了 | 用了 `System.currentTimeMillis()`；应改用 `level.getGameTime()` |
| 玩家退出后内存里一直留着记录 | 忘了 `ServerPlayConnectionEvents.DISCONNECT` 清理 |
| 重连后冷却突然重置/错乱 | 用 `Player` 实例当 key；应使用 `UUID` |

## 6. 验证方式

```bash
./gradlew build
./gradlew runDatagen   # 验证事件注册
./gradlew runClient    # 进游戏：连点左键后立刻右键，右键应能立刻生效（反之亦然）
```

## 7. 小结

- **原版 `ItemCooldowns` 是「一物一冷却」**，同一个物品的两个技能必然互相顶掉，公开 API 也没有按组查询的口子。
- 「独立计算」= 自己记账，键取 **玩家 + 物品 + 技能槽**，用**游戏刻**计时，玩家退出时清理。
- 客户端只保留**纯视觉**的冷却动画，且不能拿它做门禁，否则判定又会退回互相影响。
- 破坏方块改为「正常掉落物品」（`destroyBlock(pos, true)`），与这次一并生效。
