# 08. 两头被顶住时，破坏硬度更小的一侧

上一篇让柱子在**前端撞墙**时改成向后伸长。但如果后方也有方块，柱子的两头就都被顶住了——
长度再也补不到 30 格。本篇处理这种情况：

> 如果**两头都有实体方块**，就瞬间破坏两者中**硬度更小**的那个；硬度相同时破坏**尖端侧**；
> **1 号行为（左键短柱）遇到基岩等不可破坏方块永不破坏**，其余行为（右键长柱、四个手势对撞柱、弹幕柱）
> 打开了 `PillarSpec.breaksUnbreakable`，**连基岩一起破坏**——不过还受设置页里的「基岩」开关总控
> （关掉就全都无视基岩，见 [learn/17](17-settings-screen-for-world-breaking-toggles.md)）。
> 破坏之后继续把长度补齐，**依旧保证柱子有 30 格**。

## 1. 前提：伸长阶段的目标是「长度」而不是「时间」

要「保证 30 格」，生长逻辑就不能写成「长 20 tick 就停」，而要写成**长度驱动**：

```java
double need = Math.min(SPEED_PER_TICK, this.getMaxLength() - (front - base));   // 这一 tick 还差多少

if (need <= 1.0E-6D) {
	this.growing = false;   // 长度满了，转入消散
	return;
}
```

这样即使中途因为撞墙、破方块而「浪费」了几 tick，伸长阶段也会顺延到长度真的到 30 格为止
（名义上仍是 1 秒长满 20 格 × 1.5）。兜底由 `HARD_LIFETIME_TICKS` 负责，避免实体永远不消失。

生长分两条路走，先试前端、再试后端：

```java
// ① 前端优先：没被挡住就往前长
if (!this.frontBlocked) {
	double limit = this.limitForward(level, front, front + need);
	double gained = limit - front;
	front = limit;
	if (gained < need - 1.0E-6D) this.frontBlocked = true;
	need -= Math.min(gained, need);
}

// ② 剩下的靠后端后退补齐（反向伸长，见 learn/07）
if (need > 1.0E-6D) {
	double limit = this.limitBackward(level, base, base - need);
	need -= Math.min(base - limit, need);
	base = limit;
}
```

两条路都走完 `need` 还是大于 0，就说明**两头都有方块**——这正是本篇要处理的分支。

## 2. 找出两头的方块：两端面中点 0.75 格半径的球

方块不是只在轴线上找，而是**以柱子两个端面的中点为球心、0.75 格为半径的球**：

- 柱子截面是 1×1 且以轴线为中心，所以**端面中点就是轴线上的前端点 / 后端点**（`origin + direction × front`、`origin + direction × base`）；
- 球内的**实体方块**（有碰撞箱）都是候选，因此斜贴着的方块、斜对角的方块也算数；
- 每个球里再挑**最软**的一个（挖掘硬度最小；硬度相同则取离球心更近的）作为这一端的候选。

```java
private @Nullable BlockPos softestBlockNear(ServerLevel level, Vec3 center) {
	double radiusSqr = BREAK_RADIUS * BREAK_RADIUS;
	BlockPos min = BlockPos.containing(center.x - BREAK_RADIUS, center.y - BREAK_RADIUS, center.z - BREAK_RADIUS);
	BlockPos max = BlockPos.containing(center.x + BREAK_RADIUS, center.y + BREAK_RADIUS, center.z + BREAK_RADIUS);

	BlockPos best = null;
	float bestHardness = Float.MAX_VALUE;
	double bestDistanceSqr = Double.MAX_VALUE;

	for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
		BlockState state = level.getBlockState(pos);

		if (state.getCollisionShape(level, pos).isEmpty()) continue;   // 空气、草、花、水不算

		double distanceSqr = new AABB(pos).distanceToSqr(center);
		if (distanceSqr > radiusSqr) continue;                         // 整个方块都在球外

		float hardness = state.getDestroySpeed(level, pos);

		if (best == null || hardness < bestHardness
				|| (hardness == bestHardness && distanceSqr < bestDistanceSqr)) {
			best = pos.immutable();      // betweenClosed 复用同一个可变对象，必须固定下来
			bestHardness = hardness;
			bestDistanceSqr = distanceSqr;
		}
	}

	return best;
}
```

四个要注意的点：

1. **`BlockPos.betweenClosed(...)` 复用同一个可变对象**！直接把它存进变量，下一轮循环就会被改掉，必须 `pos.immutable()`。
2. **「是否在球内」按整个方块立方体到球心的距离判断**（`new AABB(pos).distanceToSqr(center)`），比用方块中心更符合直觉：方块表面碰到球就算在内。
3. **`getCollisionShape(...).isEmpty()`** 与柱子推进时用的 `ClipContext.Block.COLLIDER` 口径一致——草、花、水不会被当成阻挡。
4. **排序必须确定**：硬度优先、其次距离；否则同一场景下破坏哪一块会随遍历顺序变化，看起来像「随机破坏」。

**`BREAK_RADIUS = 0.75` 就是「破坏方块范围」**：它刚好覆盖紧贴端点的方块，以及截面方向上斜贴一格的方块（斜贴时方块立方体到球心的距离约 `0.707` 格），但不会波及更远的一层。调大就会开始吃到两格外的方块，调小（例如 `0.2`）就只认紧贴端点的那一格。

## 3. 比硬度：`getDestroySpeed`，负数就是不可破坏

「硬度」在代码里是 **`BlockState#getDestroySpeed(level, pos)`**（就是 F3 里那个挖掘时间系数）。
下面比较用的 `frontPos` / `rearPos`，就是上面两个球里各自挑出的最软方块：

```java
float frontHardness = level.getBlockState(frontPos).getDestroySpeed(level, frontPos);
float rearHardness = level.getBlockState(rearPos).getDestroySpeed(level, rearPos);
boolean frontBreakable = frontHardness >= 0.0F;
boolean rearBreakable = rearHardness >= 0.0F;

if (!frontBreakable && !rearBreakable) return false;   // 两头都是基岩之类：只能僵持
if (!rearBreakable) return this.breakSide(level, frontPos, true);
if (!frontBreakable) return this.breakSide(level, rearPos, false);

// 硬度小的先破；硬度相同则破尖端侧
return frontHardness <= rearHardness
		? this.breakSide(level, frontPos, true)
		: this.breakSide(level, rearPos, false);
```

三个要点：

1. **`getDestroySpeed` 返回 `-1` 表示不可破坏**——基岩、屏障、末地传送门框架等都是这个值。
   所以「不可破坏基岩」不需要特判 `Blocks.BEDROCK`，用 `< 0` 就能覆盖所有同类方块，也更不容易漏。
2. **不要用 `getExplosionResistance`（爆炸抗性）**。黑曜石爆炸抗性极高但硬度只有 50；基岩是两个都防。
   需求说的是「硬度」，对应的是挖掘硬度 `getDestroySpeed`。
3. **平局破尖端侧**：`frontHardness <= rearHardness` 时选前端。等号归前端，正是「相同硬度则破坏尖端处方块」。

## 4. 破坏方式与「解除封锁」

```java
/**
 * @param frontSide 破坏的是否尖端侧的方块（破了前端，就要解除前端封锁）
 */
private boolean breakSide(ServerLevel level, BlockPos pos, boolean frontSide) {
	if (!level.destroyBlock(pos, false)) {
		return false;
	}

	if (frontSide) {
		this.frontBlocked = false;   // 前端重新可以前进
	}

	return true;
}
```

- **`destroyBlock(pos, false)`**：第二个参数是「是否掉落物品」。这里传 `false`——这是武器效果而不是挖掘，想要掉落改成 `true` 即可。
- **破坏前端方块后必须 `frontBlocked = false`**，否则下一 tick 仍然走「后端后退」分支，长度补不上；
  破坏后端方块则不需要改标记（下一 tick 的后端射线自然会通过）。
- 破坏成功后把 `stallTicks` 清零；**如果连续 `MAX_STALL_TICKS`（默认 10）刻都推不动**，说明两头都是不可破坏方块，
  就结束伸长转入消散——绝不能在这里死循环。

## 5. 完整分支

```java
// ③ 两头都推不动：破坏硬度更小的一侧（相同则破尖端侧），基岩等永不破坏
if (need > 1.0E-6D) {
	if (this.breakBlockedEnd(level, front, base)) {
		this.stallTicks = 0;
	} else if (++this.stallTicks >= MAX_STALL_TICKS) {
		this.growing = false;   // 谁都推不动了，结束伸长
	}
} else {
	this.stallTicks = 0;
}
```

于是柱子面对一堵墙时会「啃」过去：前端顶住 → 后端后退 → 后端也顶住 → 破掉较软的一侧 → 继续推进。
每一轮最多破一个方块，且**始终优先啃软的一侧**；只有没开 `breaksUnbreakable` 的行为（1 号）遇到基岩才会僵持后消散。

## 6. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 破坏时**不**掉落物品 | `breakSide` 里的 `destroyBlock(pos, false)`（当前是 `true`，正常掉落） |
| 端点破坏范围更大 / 更小 | `BREAK_RADIUS`（默认 0.75 格；`0.2` ≈ 只认紧贴的那一格，>1 会吃到斜后方更远的方块） |
| 每端改成破坏「最近的」而不是「最软的」 | 调整 `softestBlockNear` 的排序条件（距离优先、硬度其次） |
| 连基岩也能破 | 把该类型的 `PillarSpec.breaksUnbreakable` 设为 `true`（**除 1 号行为外都已开启**；注意这等于放弃「世界底线」） |
| 平局改破后端 | `frontHardness <= rearHardness` 改成 `<` |
| 僵持多久放弃 | `MAX_STALL_TICKS`（默认 10 tick） |
| 柱子最长春多长时间 | `HARD_LIFETIME_TICKS`（兜底寿命） |
| 破坏时带音效/粒子 | 已经带（`destroyBlock` 会播破坏效果）；想静默改成 `removeBlock(pos, false)` |

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 破坏后柱子还是不长 | 破了**前端**方块却没把 `frontBlocked` 置回 `false` |
| 基岩被莫名其妙挖掉 | 用 `getExplosionResistance` 或忽略 `getDestroySpeed < 0` 的判定 |
| 实体不消失（卡住） | 两头都是不可破坏方块时没有僵持保护，`growing` 永远为真 |
| 柱子长度总差一点到 30 | 生长按 tick 计数（「长 20 tick 就停」）而不是按长度目标，撞墙那一 tick 的不足没被补回 |
| 草/花/水挡住了柱子 | 判断用了 `isAir()` 之类的宽松条件，而不是碰撞箱口径 |
| 客户端看到的长度不一致 | 破坏方块属于「依赖世界」的状态，`front`/`base` 必须同步（见 learn/07 §3） |

## 8. 验证方式

```bash
./gradlew build
./gradlew runDatagen   # 无界面启动，确认注册与同步字段无误
./gradlew runClient    # 进游戏：站在「前方土墙 + 背后石头」的夹缝里开火
```

三个观察点：

1. **前方泥土、背后石头** → 每轮破坏泥土（硬度 0.5 < 1.5），柱子最终仍长到 30 格；
2. **两头都是石头** → 平局破尖端侧，注意看是被破坏的是前方那块；
3. **前方基岩、背后泥土** → 破泥土（后端），基岩毫发无损；**两头都是基岩** → 柱子僵持约 10 tick 后消散。

## 9. 小结

- **长度驱动**的生长目标（而不是固定的 tick 数）是「保证 30 格」的前提。
- 「两头都被顶住」的判据是：这一 tick 前端与后端都推不动，`need` 仍大于 0。
- 比的是 **`getDestroySpeed`（挖掘硬度）**，`< 0` 即不可破坏——一条规则同时满足「不可破坏基岩」。
- 平局时 `<=` 取前端，实现「相同硬度破尖端侧」。
- 破坏后要维护状态：破了前端就解除前端封锁，并始终保留僵持保护，避免实体卡死。
