# 27. 白方块之星的输入层：按住技能、潜行优先、副手连击

白星的技能触发方式和黑星完全不同：黑星是**点击类**（双击、单击+滑动），白星是**按住类**（按住左键/右键）。
点击类可以用现成的回调，按住类不行——这一篇就是那套输入层。

## 1. 为什么点击回调不够用

原版只在**按下那一帧**走 `startAttack()`，之后按住期间走的是 `continueAttack()`（挖掘循环）：

```java
// Minecraft#tick（26.3 简化）
instantAttack |= this.startAttack();                                    // 只有按下这一帧
this.continueAttack(this.gui.screen() == null && !instantAttack && this.options.keyAttack.isDown() && ...);
```

而 `ClientPreAttackCallback` 只在 `startAttack()` 那条路上被调用（且 `clickCount != 0` 才是新按下）。
所以"按住持续放技能"只能**自己采样按键状态**：

```java
// client/input/WhiteStarInput.java —— 每客户端 tick 采样一次
boolean leftDown = holdingStar && !sneaking && client.options.keyAttack.isDown();
boolean rightDown = holdingStar && !sneaking && client.options.keyUse.isDown();

if (leftDown != leftWasDown) {                 // 只在"边沿"发包，不是每 tick 都发
	send(leftDown ? WhiteActionPayload.FIRE_START : WhiteActionPayload.FIRE_STOP);
	leftWasDown = leftDown;
}
```

三个要点：

1. **要么发 START 要么发 STOP**，绝不每 tick 重复发（那会变成每 tick 一次网络包）；
2. 用**边沿检测**（`leftDown != leftWasDown`），所以按一次只发一次；
3. 释放场景要写全：换武器、开界面、离开世界（`ClientPlayConnectionEvents.DISCONNECT` → `releaseAll()`）。

## 2. 服务端不信任"按住"，只信任"意图"

包体只有动作，没有坐标、没有目标：

```java
public record WhiteActionPayload(Action action) implements CustomPacketPayload {
	public enum Action { FIRE_START, FIRE_STOP, PULL_START, PULL_STOP, CORRUPT }
	...
}
```

服务端从**自己的视角**决定一切：

```java
private static void handleWhiteAction(ServerPlayer player, WhiteActionPayload.Action action) {
	boolean holdingStar = player.getMainHandItem().getItem() == ModItems.WHITE_BLOCK_STAR;
	boolean sneaking = player.isShiftKeyDown();

	switch (action) {
		case FIRE_START -> { if (holdingStar && !sneaking) WhiteStarFire.start(player); }
		case FIRE_STOP  -> WhiteStarFire.stop(player);
		case PULL_START -> { if (holdingStar && !sneaking) PinTargets.grab(player); }
		case PULL_STOP  -> PinTargets.release(player);
		case CORRUPT    -> { if (holdingStar && sneaking) corruptTo(player, ModBlocks.WHITE_BLOCK); }
	}
}
```

于是：射速、抓取判定、感染的目标方块全部由服务端说了算；改过的客户端无法靠伪造包变成机枪，也无法
"不潜行就感染"，更无法指定感染成什么方块。

## 3. 潜行优先：一个真实 bug 的修法

**症状**：潜行左键本来只该感染方块，结果**同时射出一根白柱**。

**原因**：白柱是"按住左键"触发的，而潜行左键时 `keyAttack` 本来就处于按下状态——
这次"按住"的起点发生在潜行之前（或同一 tick），按下沿早就判过了，`FIRE_START` 已经发出去；
客户端随后发 `CORRUPT` 并不影响服务端那条**已经在跑**的发射流。

**修法**：把"潜行"作为两个按住技能的**否决条件**，而且在四处都判：

| 位置 | 判定 |
| --- | --- |
| `WhiteStarInput`（客户端采样） | `!sneaking` 才算按下 → 一潜行就发 `FIRE_STOP` / `PULL_STOP` |
| `WhiteStarFire.tick`（服务端权威） | 主手不是白星**或潜行中** → 移除该玩家的发射流 |
| `PinTargets.tick`（服务端权威） | 同上 → 潜行时松手，被钉的生物立刻自由 |
| `ModNetworking.handleWhiteAction` | `FIRE_START`/`PULL_START` 要求非潜行；`CORRUPT` 要求正在潜行 |

> 教训：**"同一个按键有两种含义"时，优先级必须显式写出来，并且在权威端也写一遍**。
> 只在客户端拦一次，等于把规则交给了可以被修改的一侧。

## 4. 按住右键：把生物钉在视线上（`PinTargets`）

需求：**30 格内被准心指向的生物保持在玩家视线上。**

- **抓取**用射线 vs 包围盒的 slab 判定（自己写，不借 `Projectile` 的私有方法）：

```java
double tMin = 0, tMax = GRAB_RANGE;                   // 30 格
for (int axis = 0; axis < 3; axis++) { ... }          // 每轴求交区间，取交集
return origin.add(direction.scale(tMin));             // 命中点（或 null）
```

- **保持**是每 tick 重新定位：

```java
Vec3 point = player.getEyePosition().add(view.scale(grip.distance));   // 视线上、抓取时那个距离
Vec3 feet = point.subtract(0, grip.heightOffset, 0);                   // 目标中心 → 脚下
living.teleportTo(feet.x, feet.y, feet.z);
living.setDeltaMovement(Vec3.ZERO);
living.syncVelocity = true;
living.fallDistance = 0.0F;                                            // 松开后不会摔伤
```

细节：抓取**只发生一次**（之后即使准心扫到别的生物也不换目标），距离按抓取时算，所以它不会被吸到脸前，
也不会拖在身后；`fallDistance = 0` 是那种"看起来该写、忘了就出事"的一行。

## 5. 副手：任何伤害都追加一轮齐射（`OffhandWhiteStar`）

需求：**副手持白星时，任何造成伤害的行为都生成 2~5 根白柱打击受害目标。**

```java
ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
	if (blocked || damageTaken <= 0.0F) return;                   // 没真打中就不算
	Entity attacker = source.getEntity();
	if (!(attacker instanceof ServerPlayer player)) return;
	if (player.getOffhandItem().getItem() != ModItems.WHITE_BLOCK_STAR) return;
	if (entity == player) return;                                 // 自己炸自己不算
	fire(level, player, entity);                                  // 2..5 根，从持有者方向打过来
});
```

两个设计点：

1. **用伤害事件而不是攻击事件**：需求写的是"任何造成伤害"（含其他模组武器、本模组技能的伤害），
   而 `AttackEntityCallback` 只覆盖近战点击，抓不到法术/弹幕伤害；
2. **加最小间隔**（`VOLLEY_COOLDOWN_TICKS = 2`）：齐射**自己也会造成伤害**，而它又是"任何伤害都触发"，
   没有这个限流就会自我触发成无限链。2 tick 的间隔把链条变成"一轮爆发"。

发射方向是"沿持有者到目标的方向、在目标后方 3 格生成"，所以柱子是**穿过**目标而不是停在它前面。

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 按住没反应 / 只放一次 | 只用了点击回调；按住要自己采样按键状态 |
| 网络包每 tick 狂发 | 没有做边沿检测，`isDown()` 直接发包 |
| 松开后技能还在继续 | 没有处理换武器 / 开界面 / 断开连接 |
| 潜行左键同时射柱 | 没有把"潜行优先"写到权威端 |
| 副手连击停不下来 | 齐射伤害触发自身；需要最小间隔 |
| 抓住的生物乱动 / 松手摔死 | 每 tick 未清速度，或忘了清 `fallDistance` |
| 准心扫过就换目标 | 抓取写进了每 tick 的循环，而不是"按下时抓一次" |

## 7. 小结

- **点击用回调、按住用采样**：`ClientPreAttackCallback` 只报"按下"，按住类技能必须自己每 tick 看键位；
- 包体只传**意图**（开始/停止），数值与判定全部留在服务端；
- "一个按键两种含义"时，**优先级要显式、且在客户端与权威端各写一遍**；
- 伤害事件适合做"任何来源都触发"的联动，但要给**自己造成的伤害**留一条防自激的限流。
