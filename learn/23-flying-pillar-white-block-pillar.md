# 23. 飞行型柱子：白方块柱（细如发丝、碰墙即停、残留一秒）

需求（原文）：

> 创建白方块柱实体：套用白方块贴图，柱子粗细为 0.1 格，长度为 5 格
> 按住左键：在玩家周边持续生成单体白方块柱，然后向着玩家准心发射，以 256 格每秒速度飞行 128 格，超出距离后消散
> 白方块柱不受重力影响，碰到方块后残留模型一段时间，然后消散

这里的关键是：**它和黑方块柱不是同一种东西**，所以没有塞进 `PillarSpec`。

## 1. 为什么单独成类：生长型 vs 飞行型

| | 黑方块柱 `BlackBlockPillarEntity` | 白方块柱 `WhitePillarEntity` |
| --- | --- | --- |
| 形态 | **生长型光束**：锚在发射点，前端/后端推进 | **飞行型弹丸**：整体沿方向平移 |
| 参数 | `PillarSpec` 一张表管 8 种变体（长度、速度、是否消散、碰撞组…） | 一份常量（粗细/长度/速度/射程） |
| 表现 | 由「已填充有效段」决定 | 由「当前位置 + 方向」决定 |

生长型需要 `backDistance/frontDistance/fillFrontier` 这类字段，飞行型一个都不需要。硬塞进同一套字段，就会出现"一半字段对另一种类型没意义"的设计——所以直接写第二个类更省事。

## 2. 尺寸：碰撞盒 = 画出来的大小

```java
public static final float THICKNESS = 0.1F;   // 粗细（两轴）
public static final double LENGTH = 5.0D;     // 长度
```

实体类型用同一个常量：

```java
.sized(WhitePillarEntity.THICKNESS, (float) WhitePillarEntity.LENGTH)
```

**渲染器也要跟着缩**——这是第一个容易漏的地方：渲染是"沿方向堆方块模型"，而方块模型天然是 1×1，直接画出来会是**一整块砖**，与 0.1 格的碰撞盒完全不符：

```java
poseStack.translate(0.0D, 0.0D, center);
// 白柱只有 0.1 格粗：模型必须同步缩小，否则画出来是 1x1，跟碰撞盒对不上
poseStack.scale((float) WhitePillarEntity.THICKNESS, (float) WhitePillarEntity.THICKNESS, 1.0F);
poseStack.translate(-0.5D, -0.5D, -0.5D);   // 先缩放再平移，等效"居中的 1x1 压成 0.1x0.1"
```

> 缩放只动横向（x/y），纵向保持 1，于是长度仍是 5 格（5 段方块）。

## 3. 高速飞行：必须沿路径采样

12.8 格/tick（= 256 格/秒）意味着**一 tick 跨过 12 个方块**。如果只检查终点位置：

- 一堵一格厚的墙会被直接穿过；
- 站在路径中间的生物会被跳过。

所以把这一 tick 的位移切成小步，逐步检查：

```java
double step = Math.min(PATH_SAMPLE_STEP, SPEED_PER_TICK);   // 0.25 格
int steps = Math.max(1, (int) Math.ceil(SPEED_PER_TICK / step));
double perStep = SPEED_PER_TICK / steps;

for (int i = 0; i < steps; i++) {
	Vec3 next = this.position().add(direction.scale(perStep));
	this.travelled += perStep;
	...
}
```

用 `BlockPos.containing(next)`（方块本身，不做细粒度 clip）就够：误差不超过 1 格，而 0.1 格粗的柱子在视觉上也不要求格级精度。

## 4. 对世界无害：碰到方块停下，而且残留一会儿

```java
// Solid blocks **stop** the bolt: it never breaks anything, it simply stays where it hit.
if (this.blocked(level, next)) {
	this.rest();
	return;
}
```

判据是**碰撞形状是否为空**：空气、水、草可以穿过；实心方块则停。

停下不是立刻消失，而是进入**残留阶段**（第二个容易漏的地方）：

```java
if (this.restTicks > 0) {
	this.restTicks--;
	this.setDeltaMovement(Vec3.ZERO);
	this.syncVelocity = true;      // 让客户端也知道它静止了
	if (this.restTicks <= 0) this.discard();
	return;
}
```

- `REST_TICKS = 20`（1 秒）：模型留在原地，玩家能看清"打哪儿了"；
- 残留期间**不再造成伤害、也不再挡住别的柱子**（`strike()` 里 `restTicks > 0` 直接返回）——否则按住左键连射时，后面的柱子会全部撞在已经钉住的那根上排队堆叠；
- `setDeltaMovement(Vec3.ZERO)` + `syncVelocity = true`：不这么做，客户端可能按旧的动量继续位移，看起来就像"停在半空又往下掉"。

**无重力**由两件事共同保证：实体构造里 `setNoGravity(true)`，以及位置**只由 `setPos` 显式推进**（不靠速度积分）。所以哪怕有重力值也不会被应用，残留阶段也不会下沉。

## 5. 命中：伤害 + 白标记

```java
DamageSource source = level.damageSources().source(ModDamageTypes.WHITE_BLOCK_STAR, this.owner);
target.hurtServer(level, source, TOUCH_DAMAGE);
Marks.applyAndSync(target, Marks.WHITE);        // 白标记 W，另一半是黑柱给的黑标记 B
```

- `this.owner == target` 直接跳过 → **发射者免疫**（不被伤害、不被标记）；
- `hitTargets` 保证**一根柱子对同一目标只结算一次**；
- 命中即 `discard()`（单体弹丸），与"碰方块残留"是两条不同的结束路径。

## 6. 发射方式：周边生成 → 朝准心汇聚

服务端每 2 tick 一根（`WhiteStarFire`）：

```java
Vec3 aim = player.getEyePosition().add(view.scale(AIM_DISTANCE));   // 准心前方 48 格
// 以视线为轴的圆环上取一点，作为出生点
Vec3 around = side.scale(cos(angle) * SPAWN_RADIUS)
		.add(0, SPAWN_HEIGHT + sin(angle) * SPAWN_RADIUS * 0.5, 0)
		.add(view.scale(SPAWN_RADIUS));
pillar.setFlightDirection(aim.subtract(spawn));                     // 于是所有柱子"汇聚"到准心
```

- 出生点**不在准心正前方**，而是在玩家周围的环上 → 视觉上是"从身边射出去"；
- 方向是"出生点 → 准心前方一点"，所以柱子会**收敛**，而不是互相平行的管道；
- 发射频率、射程判断都在服务端（客户端只说"按住/松开"），改过的客户端无法把它变成机枪。

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 柱子看起来是"一整块砖" | 渲染没按粗细缩放（碰撞盒是 0.1，画的是 1×1） |
| 一堵墙挡不住它 | 只检查终点位置，没沿路径采样 |
| 停下后模型突然消失 | 没有残留阶段（直接 `discard`） |
| 停下后模型"往下掉"或漂移 | 残留时没清零速度 / 没设 `syncVelocity` |
| 连射时后面的柱子堆在一起 | 残留期间还在参与碰撞（`strike` 没判 `restTicks`） |
| 按住左键时顺带挖方块 | 原版"按住"走的是挖掘循环，需要用 `AttackBlockCallback` 屏蔽（见 [learn/24](24-marks-and-decay.md) 所在的触发层说明） |
| 挂到墙上后射击没反应 | 每次命中都会新增一根残留柱，注意别让它参与实体碰撞判定 |

## 8. 小结

- **行为不同就换类**：生长型与飞行型共享的是"柱子"这个名字，而不是字段；
- 高速实体必须**沿路径采样**（步长 ≤ 0.25 格），否则会穿墙穿人；
- **画出来的尺寸要跟碰撞盒一致**：改 `THICKNESS` 一处，实体与渲染同时变；
- 结束状态可以不止一个：命中即止、飞满射程消散、碰墙进入"残留 → 消散"；
- 残留期间要主动**退出所有交互**（伤害、碰撞），否则连射会互相堵塞。
