# 05. 左键技能：客户端事件 → 网络包 → 自定义实体

本篇实现黑方块之星的主动技能：

> **主手拿着黑方块之星点击左键**，沿角色**面朝方向**（含俯仰）产生一根黑方块柱：
> 先以 **30 格/秒** 伸长到约 **30 格**（耗时 1 秒），随后**尖端固定、后端以同样速度前移**，
> 整根柱子像被射出去一样在远端消散（再 1 秒），总寿命 2 秒。

这个实体「像一支不会下坠的箭」：**不受重力、不是弹射物**（不继承 `Projectile`/`AbstractArrow`，没有命中判定、不能被拾取），
而且它的黑方块外观完全由客户端渲染器用方块模型画出来（**只有 learn/08 的「两头被顶住」分支会真的破坏方块**）。

这是本项目第一个「跨客户端与服务端」的功能，也是最有教学价值的一环：很多人第一次做「按键放技能」时会卡在「客户端改了世界但服务端不认」上。我们一步一步拆。

## 1. 先想清楚数据流

```
玩家按左键
   │  （按键只存在于客户端）
   ▼
① 客户端事件 ClientPreAttackCallback
   │  ClientPlayNetworking.send(包)
   ▼
② 服务端接收 ServerPlayNetworking.registerGlobalReceiver
   │  校验：主手是不是这把剑？冷却好了吗？
   ▼
③ 生成实体 BlackBlockPillarEntity（只设置位置与朝向）
   │  实体两端各自推进同一条时间线：伸长 → 消散（视觉不改世界）
   ▼
④ 客户端渲染器按「后端距离 / 尖端距离 / 朝向」用黑方块模型把柱子画出来
```

**为什么不能客户端自己生成？**
客户端生成的实体只是「本地幽灵」，服务端不会承认，其他玩家也看不到。凡是**影响游戏状态**的东西都要由服务端生成，客户端只发一个「我按了左键」的信号。

**为什么包体是空的？**
朝向由服务端从玩家自己身上读取（`player.getViewVector()`）。如果让客户端把方向传上来，作弊客户端就能朝任意方向发射。**「客户端只表达意图，服务端决定细节」**是网络包设计的基本原则。

## 2. 客户端：拦截左键

Fabric 提供了 `ClientPreAttackCallback`（在 `fabric-events-interaction-v0` 模块里），语义正合适：

- 攻击键被按下（含长按）**每 tick** 都会触发；
- 参数 `clickCount` 是「本 tick 攻击键被按下的次数」——**刚点下那一下不为 0，长按期间一直是 0**，这就是区分「点」与「按」的依据；
- 返回 `true` 会**取消原版处理**（挖方块、攻击实体、挥手动画都会跳过）。

```java
public class NewModClient implements ClientModInitializer {
	/** false：只要主手拿着这把剑，每次左键都发射（会屏蔽普通攻击与挖掘）。 */
	private static final boolean FIRE_ONLY_WHEN_AIMING_AT_NOTHING = false;

	@Override
	public void onInitializeClient() {
		// 四个柱子类型共用一个渲染器（外观方块由实体类型决定，见 learn/10）
		EntityRenderers.register(ModEntities.SHORT_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
		EntityRenderers.register(ModEntities.OBSIDIAN_PILLAR, BlackBlockPillarRenderer::new);

		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (clickCount == 0) return false;                 // 长按不连发

			ItemStack stack = player.getMainHandItem();
			if (!(stack.getItem() instanceof PillarWeaponItem)) return false;

			if (FIRE_ONLY_WHEN_AIMING_AT_NOTHING
					&& client.hitResult != null
					&& client.hitResult.getType() != HitResult.Type.MISS) {
				return false;                                  // 保留普通攻击/挖掘
			}

			// 注意：客户端**不**用原版冷却做门禁（那样右键的动画会挡住左键），冷却由服务端独立判定
			if (!ClientPlayNetworking.canSend(PillarPayload.TYPE)) return false;

			ClientPlayNetworking.send(PillarPayload.SHORT);
			player.getCooldowns().addCooldown(stack, ModNetworking.FIRE_COOLDOWN_TICKS);
			// 原版挥手被拦截了，自己补一个（26.3 签名：手、动画、是否发给其他玩家）
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);

			return true;
		});
	}
}
```

### 2.1 一些取舍

- **`FIRE_ONLY_WHEN_AIMING_AT_NOTHING`**：默认 `false` 表示「手持剑时左键就是技能键」，代价是没法用这把剑普通攻击/挖方块；改成 `true` 则只在准心空指时发射。
- **冷却两端都写**：客户端写为了**立刻有反馈**，服务端写才是**权威**（防连点）。共用常量 `FIRE_COOLDOWN_TICKS`。
- **`canSend` 判断**让模组能连原版服务器：服务器不认识这个包时不会崩，只是技能无效。

`ClientModInitializer` 必须在 `fabric.mod.json` 注册：

```json
"entrypoints": {
	"main":   [ "com.sizah.NewMod" ],
	"client": [ "com.sizah.client.NewModClient" ]
}
```

> `main` 两端都跑，`client` 只在客户端跑。**任何引用 `net.minecraft.client.*` 的类都只能从 client 入口触发**，否则独立服务器启动时会 `NoClassDefFoundError`。

## 3. 包体：一个空记录类

> **修订（本次）**：左键短柱原本就是这个"无字段包"（`FirePillarPayload`）。现在它被
> **`PillarPayload(Kind.SHORT / Kind.LONG)`** 取代——因为 1/2 号都改成了双击触发，
> 又要由客户端在待命窗口到期时才发，所以一个包同时服务两根柱子。下面先看最小形态（当时的样子），
> 再看现在带一个字段的版本。

```java
public record FirePillarPayload() implements CustomPacketPayload {
	public static final FirePillarPayload INSTANCE = new FirePillarPayload();

	public static final CustomPacketPayload.Type<FirePillarPayload> TYPE =
			new CustomPacketPayload.Type<>(NewMod.id("fire_pillar"));

	/** 无字段包体用 StreamCodec.unit，不必手写编解码。 */
	public static final StreamCodec<RegistryFriendlyByteBuf, FirePillarPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
```

现在多了一个"哪一种柱子"的字段（枚举走 ordinal）：

```java
public record PillarPayload(Kind kind) implements CustomPacketPayload {
	public enum Kind { SHORT, LONG }

	public static final PillarPayload SHORT = new PillarPayload(Kind.SHORT);
	public static final PillarPayload LONG = new PillarPayload(Kind.LONG);

	public static final StreamCodec<RegistryFriendlyByteBuf, PillarPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT,
					payload -> payload.kind().ordinal(),
					ordinal -> new PillarPayload(Kind.values()[ordinal]));
	...
}
```

- `Type` 的名字（`new-mod:fire_pillar` / `new-mod:pillar`）就是网络频道名，**两端必须一致**。
- 26.3 用 `StreamCodec` 取代了老的 `PacketByteBuf` 手写读写；要传数据就把 record 加字段并改用
  `StreamCodec.composite(...)`（`ByteBufCodecs` 里已经有常见类型的编解码器）。
- 注册（收发两端都要，且早于注册处理器）：`PayloadTypeRegistry.serverboundPlay().register(TYPE, STREAM_CODEC);`，反方向是 `clientboundPlay()`。
- **包里只放"选哪个技能"，不要放坐标/朝向**：那些仍由服务端从玩家身上读，客户端伪造不了。

## 4. 服务端：校验 + 生成实体

```java
ServerPlayNetworking.registerGlobalReceiver(PillarPayload.TYPE, (payload, context) -> {
	ServerPlayer player = context.player();          // 处理器在服务端主线程，可安全改世界
	ItemStack stack = player.getMainHandItem();

	if (payload.kind() == PillarPayload.Kind.SHORT) {
		if (firePillar(player)) {
			WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.LEFT, FIRE_COOLDOWN_TICKS);
		}
	} else if (fireLongPillar(player)) {            // 2 号：右键长柱，见 learn/11、learn/15
		WeaponCooldowns.apply(player, stack, WeaponCooldowns.Slot.RIGHT, LONG_FIRE_COOLDOWN_TICKS);
	}
});

private static boolean canFireShortPillar(ServerPlayer player, ItemStack stack) {
	return stack.getItem() instanceof PillarWeaponItem                    // 主手校验（两种柱子武器都算）
			&& WeaponCooldowns.isReady(player, stack, WeaponCooldowns.Slot.LEFT);   // 冷却校验（独立记账）
}

private static boolean firePillar(ServerPlayer player) {
	ItemStack stack = player.getMainHandItem();
	if (!canFireShortPillar(player, stack)) return false;
	if (!(player.level() instanceof ServerLevel level)) return false;

	Vec3 direction = player.getViewVector(1.0F).normalize();              // 完整视线方向

	BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.typeFor(weapon.kind()), level);
	pillar.setYRot((float) (Mth.atan2(-direction.x, direction.z) * (180.0D / Math.PI)));
	pillar.setXRot((float) (-Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)) * (180.0D / Math.PI)));

	Vec3 start = player.position()
			.add(0.0D, player.getEyeHeight() - 0.5D, 0.0D)
			.add(direction.scale(1.0D));
	pillar.setPos(start.x, start.y, start.z);

	level.addFreshEntity(pillar);
	return true;
}
```

### 4.1 朝向为什么写进「实体旋转」而不是自定义字段

`xRot`/`yRot` 是**原版实体自带的同步字段**：生成实体的数据包就会带上它们，客户端 `entity.getViewVector(1.0F)` 立刻能还原出方向。这样就不必自己写 `SynchedEntityData` 同步一个 `Vec3`。

两个换算公式值得记（它们与 `Entity#calculateViewVector` 互逆）：

```
yRot = atan2(-direction.x, direction.z)   // 偏航
xRot = -asin(direction.y)                 // 俯仰
```

## 5. 实体：注册类型与时间线

柱子有四个实体类型（短黑方块柱 / 黑曜石柱 / 备用的长、永），共用同一个实体类，
差异由 `PillarSpec` 描述——注册代码见 [learn/10](10-entity-types-carry-the-differences.md)。
这里先看类型是怎么建出来的：

```java
private static EntityType<BlackBlockPillarEntity> build(ResourceKey<EntityType<?>> key, boolean noSummon) {
	EntityType.Builder<BlackBlockPillarEntity> builder = EntityType.Builder
			.of(BlackBlockPillarEntity::new, MobCategory.MISC)  // 构造器引用 + 生成分类
			.sized(1.0F, 1.0F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.noSave();                                          // 短命实体，不进存档

	if (noSummon) {
		builder = builder.noSummon();                           // 武器发射的类型禁止 /summon
	}

	return builder.build(key);                                  // 26.3 需要 ResourceKey
}
```

实体本身**不放置任何方块**，只维护一条时间线：

```java
public static final double SPEED_PER_TICK = 1.5D;     // 30 格/秒 ÷ 20 tick
public static final int GROWTH_TICKS = 20;            // 名义伸长 1 秒
public static final int DISSIPATE_TICKS = 20;         // 名义消散 1 秒
// 最大长度不写死在类里：常规类型 30 格，备用「长」类型 60 格 —— 由 ModEntities.PillarSpec 提供
// 用 this.getMaxLength() 读取

private int lifeTicks;

@Override
public void tick() {
	super.tick();
	this.lifeTicks++;

	// 由服务端负责生命周期：消散收完（长度归零）或超过兜底寿命就移除。
	if (this.level() instanceof ServerLevel serverLevel) {
		this.updateGeometry(serverLevel);
		// ...后坐、触碰结算（见 learn/06、07、08）

		if (this.dissipates()
				&& (this.lifeTicks >= HARD_LIFETIME_TICKS
						|| (!this.growing && this.getLength() <= 1.0E-6D))) {
			this.discard();
		}
	}
}

/** 前端（尖端）到发射点的距离：由服务端推进并同步给客户端。 */
public double getTipDistance() {
	return this.entityData.get(DATA_FRONT);
}

/** 后端到发射点的距离（反向伸长时为负数，见 learn/07）。 */
public double getBaseDistance() {
	return this.entityData.get(DATA_BASE);
}

/** 朝向：由实体旋转推出，客户端与服务端算法一致。 */
public Vec3 getBeamDirection() {
	return this.getViewVector(1.0F);
}
```

时间线（无阻挡时的理想情况，`t` 为生存刻数）：

| 阶段 | 刻数 | 后端 | 尖端 | 效果 |
| --- | --- | --- | --- | --- |
| 伸长 | 1 – 20 | 0 | 1.5 → 30 | 柱子从面前长出去 |
| 消散 | 21 – 40 | 1.5 → 30 | 30 | 后端追上来，远端消失 |

> 撞墙、两头封锁等情况下，伸长阶段会顺延（见 [learn/07](07-reverse-extension-and-recoil.md)、[learn/08](08-breaking-the-weaker-blocked-end.md)），
> 但阶段目标不变：**先把总长补到该类型的最大长度（常规 30 格），再消散**。

### 5.1 关于「要不要同步长度」

如果几何只由 `lifeTicks` 决定，两端各算各的即可——但**这个结论有前提**：
它要求行为完全确定、不依赖世界。

后来 [learn/07](07-reverse-extension-and-recoil.md) 加上了「撞到实体方块就反向伸长」的机制，
撞不撞墙取决于地形，客户端无法自行推算，于是改成用 `SynchedEntityData` 同步
`front` / `base` 两个 float：

**判断标准很简单：逻辑依赖世界（地形、随机、其他实体）→ 必须同步真实状态，客户端只做展示；
逻辑纯确定 → 可以两端各算各的，省掉同步开销。**

### 5.2 「像箭但不是弹射物」

- **不像箭的地方**：没有抛物线（`setNoGravity(true)`，且我们从不调用 `move()`，位置只在生成时设定一次）；
- **不是弹射物**：不继承 `Projectile`/`AbstractArrow`，因此没有命中检测、没有击退、不能被拾取、不受 PvP/游戏规则影响；
- `noPhysics = true`：不参与碰撞解算（柱子是视觉实体，不该把玩家挤开）。

## 6. 渲染：用黑方块模型把柱子画出来

实体本体只有 1×1 的碰撞箱，视觉全靠渲染器。26.3 的渲染器采用 **render state 模式**：`createRenderState()` 造状态、`extractRenderState(...)` 填数据、`submit(...)` 提交绘制。

### 6.1 渲染状态

```java
public class BlackBlockPillarRenderState extends EntityRenderState {
	public double baseDistance;                     // 后端距离
	public double tipDistance;                      // 尖端距离
	public Vec3 direction = Vec3.ZERO;              // 朝向
	public final MovingBlockRenderState blockTemplate = new MovingBlockRenderState();  // 方块/光照模板
}
```

### 6.2 数据提取

```java
@Override
public void extractRenderState(BlackBlockPillarEntity entity, BlackBlockPillarRenderState state, float partialTicks) {
	super.extractRenderState(entity, state, partialTicks);

	state.baseDistance = entity.getBaseDistance();
	state.tipDistance = entity.getTipDistance();
	state.direction = entity.getBeamDirection();

	MovingBlockRenderState template = state.blockTemplate;
	template.blockState = ModBlocks.BLACK_BLOCK.defaultBlockState();
	template.blockPos = entity.blockPosition();
	template.randomSeedPos = entity.blockPosition();

	if (entity.level() instanceof ClientLevel clientLevel) {
		template.biome = clientLevel.getBiome(template.blockPos);
		template.cardinalLighting = clientLevel.cardinalLighting();
		template.lightEngine = clientLevel.getLightEngine();
	}
}
```

`MovingBlockRenderState` 是原版给「移动中的方块」（下落沙、活塞推动的方块）用的载体，它同时实现了 `BlockAndTintGetter`，所以方块模型渲染时能查光照、生物群系染色与邻居方块。

### 6.3 提交几何

```java
@Override
public void submit(BlackBlockPillarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
	int cubes = (int) Math.floor(state.tipDistance - state.baseDistance);
	if (cubes <= 0) return;

	poseStack.pushPose();

	// 把局部 +Z 轴转到柱子方向
	float yaw = (float) Mth.atan2(state.direction.x, state.direction.z);
	float pitch = (float) Math.asin(-state.direction.y);
	poseStack.rotate(Axis.YP, yaw);     // 26.3：mulPose 只接受 Matrix4fc/Transformation
	poseStack.rotate(Axis.XP, pitch);   //       四元数/轴角旋转要用 rotate(Axis, 弧度)

	for (int i = 0; i < cubes; i++) {
		double center = state.baseDistance + i + 0.5D;   // 第 i 个方块的中心

		MovingBlockRenderState block = new MovingBlockRenderState();   // 每个方块一份！
		block.blockState = state.blockTemplate.blockState;
		block.biome = state.blockTemplate.biome;
		block.cardinalLighting = state.blockTemplate.cardinalLighting;
		block.lightEngine = state.blockTemplate.lightEngine;
		block.randomSeedPos = state.blockTemplate.randomSeedPos;
		block.blockPos = BlockPos.containing(
				state.x + state.direction.x * center,
				state.y + state.direction.y * center,
				state.z + state.direction.z * center);

		poseStack.pushPose();
		poseStack.translate(0.0D, 0.0D, center);
		poseStack.translate(-0.5D, -0.5D, -0.5D);        // 方块模型占 0..1，平移半格对齐轴线
		collector.submitMovingBlock(poseStack, block, 0);
		poseStack.popPose();
	}

	poseStack.popPose();
	super.submit(state, poseStack, collector, camera);
}
```

**三个必须注意的点：**

1. **每个方块要独立的 `MovingBlockRenderState`。** `SubmitNodeCollection#submitMovingBlock` 只是把 state 的**引用**塞进提交节点，真正绘制发生在后面的特征渲染阶段；复用同一个实例会让所有方块都按最后一个 `blockPos` 取光照和邻居方块。
2. **`mulPose` 在 26.3 不能吃四元数。** 轴角旋转要用 `poseStack.rotate(Axis.YP, 弧度)`；`Axis.rotation(float)` 返回的 `Quaternionf` 只能喂给 `rotate(Quaternionfc)`。
3. **剔除包围盒要自己扩大。** 实体本体始终停在发射点，默认 1×1 的包围盒会让「抬头看柱子中段」时整根柱子被视锥剔除：

```java
@Override
protected AABB getBoundingBoxForCulling(BlackBlockPillarEntity entity, float partialTicks) {
	// 柱子可能朝两侧伸：撞墙反向伸长时后端会退到发射点身后（距离为负）。
	Vec3 direction = entity.getBeamDirection();
	Vec3 from = entity.position().add(direction.scale(entity.getBaseDistance()));
	Vec3 to = entity.position().add(direction.scale(entity.getTipDistance()));
	return new AABB(from, to).inflate(1.0D);
}
```

> 旋转顺序（先 Y 后 X）与「局部 +Z 指向柱子方向」的推导：`R_y(yaw)·R_x(pitch)·(0,0,1) = (cos(pitch)sin(yaw), −sin(pitch), cos(pitch)cos(yaw))`，代入 `yaw = atan2(dx, dz)`、`pitch = asin(−dy)` 正好得到单位方向 `(dx, dy, dz)`。

### 6.4 实体不可见也必须注册渲染器

原版 `EntityRenderDispatcher#getRenderer` 对未注册的实体类型返回 `null`，紧接着 `shouldRender` 就会空指针。本项目注册的是真正会画东西的渲染器；如果你的实体确实不需要外观，也至少要注册一个「空渲染状态」的渲染器。

## 7. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 伸长/消散更快 | `BlackBlockPillarEntity.SPEED_PER_TICK`（1.5 = 30 格/秒） |
| 更长 / 更短 | `ModEntities.PillarSpec.maxLength`（常规 30 / 备用「长」60） |
| 伸长或消散阶段时长 | `GROWTH_TICKS` / `DISSIPATE_TICKS`（同时调整 `maxLength` 保持 `速度×刻数` 一致） |
| 柱子变粗 | `submit` 里在垂直与水平垂直方向（`new Vec3(-dir.z, 0, dir.x)`）加偏移循环 |
| 换成别的方块 | `extractRenderState` 里的 `template.blockState` |
| 发射间隔 | `ModNetworking.FIRE_COOLDOWN_TICKS`（**60 = 3 秒**） |
| 加一条发射门禁 | 现在是「**双击判定** + 单击后的 4 tick 手势窗口」，见 [learn/15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md)（旧的视角门禁见 [learn/19](19-view-angle-gate.md)，已删除） |
| 起点高低 | `ModNetworking` 里的 `player.getEyeHeight() - 0.5D` 与 `direction.scale(1.0D)` |

## 8. 常见坑

| 现象 | 原因 |
| --- | --- |
| 用同一个 `MovingBlockRenderState` 画多段 → 光照/相邻面错乱 | 提交节点持有引用，必须每个方块一份 |
| `mulPose(Quaternionf)` 编译不过 | 26.3 的 `PoseStack` 只留了矩阵/Transformation 重载，用 `rotate(Axis, 弧度)` |
| 看向柱子中段时整根消失 | 没重写 `getBoundingBoxForCulling`，被视锥剔除 |
| 客户端启动崩溃 `NoClassDefFoundError: net/minecraft/client/...` | 把客户端类接到了 `main` 入口 |
| 单人能看到、多人看不到 | 只在客户端生成实体，没走服务端 |
| 长按左键疯狂发射 | 没判断 `clickCount != 0`，或只在客户端判冷却 |
| 包发出去了服务端没反应 | 忘了 `PayloadTypeRegistry` 注册，或两端频道名不一致 |
| 柱子方向反了 / 上下颠倒 | 偏航俯仰换算写错，见 §4.1 的两个公式 |

## 9. 验证方式

```bash
./gradlew build        # 编译：能抓出 API 签名（例如 mulPose）与泛型错误
./gradlew runDatagen   # 无界面启动：执行 main 与 client 两端的注册逻辑，验证不会崩
./gradlew runClient    # 进游戏：手持黑方块之星点左键，看 1 秒伸长 + 1 秒远端消散
```

`build.gradle` 里 `configureDataGeneration { client = true }` 让数据生成跑在**客户端环境**，所以 `runDatagen` 能顺带验证客户端入口（渲染器注册、事件注册）。

## 10. 小结

- **按键在客户端，游戏状态在服务端**，中间必须用自定义包连接；包体只表达意图，细节由服务端决定。
- `ClientPreAttackCallback` 是拦左键的正规入口：`clickCount != 0` 判「点击」、返回 `true` 取消原版处理。
- 「像箭但不是弹射物」= 不继承 `Projectile`、`setNoGravity(true)`、永不 `move()`。
- 纯视觉实体可以让**两端各自推进同一条确定性时间线**，省掉同步；一旦影响玩法就必须改成 `SynchedEntityData`。
- 从实体渲染器画方块用 `submitMovingBlock` + `MovingBlockRenderState`，**每个方块一份 state**；旋转用 `rotate(Axis, 弧度)`；别忘了扩大剔除包围盒。
