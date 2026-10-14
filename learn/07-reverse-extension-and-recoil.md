# 07. 撞墙反向伸长与玩家后坐

本篇给黑方块柱加一个「打不穿就顶着自己退」的机制：

> 柱子伸长时**前端撞到实体方块**，就停下来不再前进；剩下的长度改由**后端向后后退**补齐
> ——保证柱子一定有 **30 格**长。此时**发射者被一路向后推，始终待在柱子后端**；
> 后坐期间玩家不受重力影响，**这段后坐不造成伤害**。

这是整个项目里几何最绕的一块，但只要把「后端 / 前端两个距离」想清楚，代码其实很短。

## 1. 用两个距离描述一根柱子

不要用「位置 + 长度」描述柱子，而要用**沿朝向的两个距离**（都以发射点为原点）：

| 名字 | 含义 | 正常伸长 | 撞墙反向伸长 |
| --- | --- | --- | --- |
| `front`（前端） | 尖端到发射点的距离 | 0 → 30 | 停在方块面上（比如 12） |
| `length`（长度） | 柱子总长 | 0 → 30 | 继续 0 → 30 |
| `base = front − length` | 后端到发射点的距离 | 恒为 0 | 0 → **负数**（退到玩家身后） |

这样两种情形就是同一套公式：

```java
public double getTipDistance()  { return this.entityData.get(DATA_FRONT); }
public double getBaseDistance() { return this.getTipDistance() - this.getLength(); }
```

- **没撞墙**：`front` 一直前进，`length = front`，所以 `base` 恒为 0——后端钉在发射点；
- **撞墙**：`front` 冻结在方块面上，`length` 继续涨，于是 `base` 变成负数——**后端在后退**。
  这就是「反向伸长」，并且因为 `length` 的涨速没变，**总长仍然恰好 30 格**。

> 一个容易忽略的好处：消散阶段（后端以 30 格/秒向前收）的代码完全不用改，
> `length -= SPEED_PER_TICK` 在两种情形下都成立——没撞墙时后端从发射点收到前端，
> 撞墙时后端从玩家身后收到方块面。

## 2. 前端撞墙怎么判定

用原版的**方块碰撞射线**（`ClipContext`）：

```java
private double blockLimit(ServerLevel level, double from, double to) {
	Vec3 direction = this.getBeamDirection();
	Vec3 start = this.position().add(direction.scale(from));
	Vec3 end = this.position().add(direction.scale(to));

	BlockHitResult hit = level.clip(
			new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

	if (hit.getType() == HitResult.Type.MISS) {
		return to;
	}

	double travelled = hit.getLocation().subtract(start).dot(direction);
	return Mth.clamp(from + travelled - BLOCK_MARGIN, from, to);
}
```

要点：

- **每 tick 只对「新走的一小段」做射线**（`from` → `to`，长 1.5 格），而不是每次都从发射点重射 30 格——既省性能，也避免柱子长出来后又被自己经过的地形挡住。
- **`ClipContext.Block.COLLIDER`** 只考虑有碰撞箱的方块，所以草、花、水、火把都拦不住柱子（`Fluid.NONE` 表示忽略流体）。
- **`hit.getLocation()` 投影到柱子轴线**上得到距离（用点乘），再扣掉 `BLOCK_MARGIN` 余量，避免把方块面本身当作可占用的空间。
- 命中后把 `frontBlocked` 置位，后续 tick 走「反向伸长」分支，不再做射线。

## 3. 关键修正：前端与长度必须同步给客户端

06 之前我们说过「时间线是确定性的，不用同步」。**加了撞墙判定后这个结论不成立了**：撞不撞墙取决于世界地形，客户端无法自行推算。

26.3 用 `SynchedEntityData` 同步两个 float 即可：

```java
private static final EntityDataAccessor<Float> DATA_FRONT = SynchedEntityData.defineId(
		BlackBlockPillarEntity.class, EntityDataSerializers.FLOAT);
private static final EntityDataAccessor<Float> DATA_BASE = SynchedEntityData.defineId(
		BlackBlockPillarEntity.class, EntityDataSerializers.FLOAT);

@Override
protected void defineSynchedData(SynchedEntityData.Builder entityData) {
	entityData.define(DATA_FRONT, 0.0F);
	entityData.define(DATA_BASE, 0.0F);
}
```

渲染器读的就是这两个值（`getBaseDistance()` / `getTipDistance()`），所以客户端画面与服务端判定天然一致，也不会再有「客户端晚 1–2 刻启动」的漂移。

> 教训：**「纯视觉实体可以两端各算各的」只在行为完全确定时才成立**。一旦逻辑依赖世界（撞墙、随机、其他实体），就必须同步真实状态，客户端只做插值/展示。

## 4. 玩家后坐：碰到柱子才被推开「剩余距离」

后坐**不是**「一反向伸长就自动推」，而是**接触触发**：发射者的身体真的碰到柱子时才开始，
推动的总距离等于**柱子还差多少长度**（`getMaxLength() - length`）。

```java
private void updateOwnerRecoil() {
	Entity player = this.owner;
	if (player == null || !player.isAlive()) { this.ownerRecoilActive = false; return; }

	if (!this.ownerRecoilActive) {
		// 只有反向伸长（前端被顶住、后端在后退）时后端才会扫向玩家；玩家一碰到柱子就开始后坐。
		if (!this.growing || !this.frontBlocked || !this.isTouching(player)) return;

		this.ownerRecoilActive = true;
		this.ownerRecoilPushed = 0.0D;
		this.ownerRecoilDistance = Math.max(0.0D, this.getMaxLength() - this.getLength());
	}

	// 推满剩余距离（或伸长阶段结束）就收手，并把速度按比例衰减
	if (this.ownerRecoilPushed >= this.ownerRecoilDistance || !this.growing) {
		this.endOwnerRecoil(player);
		return;
	}

	this.pushOwnerBackward(player);
	this.ownerRecoilPushed += SPEED_PER_TICK;
}
```

四个设计点：

1. **触发条件是「接触」**。`isTouching(player)` 把玩家身体中心投影到柱子线段上，落在玩家包围盒外扩 0.5 格内才算碰到——与生物命中共用同一套判定。空地上柱子不会扫向玩家，因此不会误触发（这里还额外限定了只在「反向伸长」阶段判定）。
2. **推的距离 = 剩余长度**。接触那一刻记下 `getMaxLength() - getLength()`；推的速度（1.5 格/tick）与后端后退速度完全相同，所以推满这段距离时柱子也刚好补满最大长度，玩家与后端同进退。
3. **结束即衰减**。`endOwnerRecoil` 里把速度乘以 **0.1**（`RECOIL_END_VELOCITY_SCALE`）并置 `syncVelocity = true`，玩家几乎立刻停下，但不会出现「瞬间归零」的突兀感；想要彻底停住就把这个比例改成 `0.0`。
4. **重力与伤害**。每 tick 用 `+0.08` 补偿重力（原版重力加速度就是 0.08 格/tick²，所以后坐期间玩家不会被往下拉），并 `resetFallDistance()` 让这段位移不产生坠落伤害。之所以不去动 `setNoGravity(true)`，是因为那是个会被写进玩家存档的字段，万一中途掉线就可能留下永久失重的坏数据；速度补偿没有这种副作用。

```java
private void pushOwnerBackward(Entity player) {
	Vec3 direction = this.getBeamDirection();
	if (direction.lengthSqr() < 1.0E-6D) return;

	player.setDeltaMovement(
			-direction.x * SPEED_PER_TICK,
			-direction.y * SPEED_PER_TICK + GRAVITY_COMPENSATION,
			-direction.z * SPEED_PER_TICK);
	player.syncVelocity = true;      // 客户端才看得到自己被打退
	player.resetFallDistance();      // 「不造成伤害」：后坐不产生坠落伤害
}

private void endOwnerRecoil(Entity player) {
	this.ownerRecoilActive = false;
	player.setDeltaMovement(player.getDeltaMovement().scale(0.1D));   // 结束速度 ×0.1
	player.syncVelocity = true;
	player.resetFallDistance();
}
```

> 后坐只在「反向伸长」期间可能触发。消散阶段后端改为向前收，不再判定接触，因此不会把人往墙里拖。

## 5. 击退手感：频率拉满、单次位移拉小

「调高击退频率、调低击退距离、让生物击退速度与伸长速度一致」在 Minecraft 里有一个硬上限：**实体移动是按 tick 结算的，20 Hz 就是最高频率**。所以正确做法是：

| 做法 | 结果 |
| --- | --- |
| 一次性大冲量（例如 `dir × 3.0`） | 目标被弹飞，随后被阻力与重力拉走，落点不可控 |
| **每 tick 重设速度 = 伸长速度（本项目）** | 目标与柱子同步被推着走，肉眼看上去就是「被柱子顶着前进」 |

```java
// 击退 / 后坐 / 伸长 用的是同一个速度：ModEntities.PillarSpec.speedPerTick
// 常规类型 1.5 格/tick（30 格/秒），长黑方块柱 3.0（60 格/秒）
```

配合「走过前端终点就不再推」的判断，目标最终会停在前端终点附近：

```java
double travelled = target.position().subtract(origin).dot(direction);
if (travelled < this.getMaxLength()) { /* 继续推 */ }
```

## 6. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 柱子总长 | `ModEntities.PillarSpec.maxLength`（撞墙后也保证这么长） |
| 伸长/后退速度 | `SPEED_PER_TICK` |
| 后坐更快 / 更慢 | `pushOwnerBackward` 里的速度（默认与后端同速 `SPEED_PER_TICK`） |
| 更容易 / 更难被后坐 | `updateOwnerRecoil` 里的 `isTouching` 判定范围（默认外扩 0.5 格） |
| 后坐结束保留惯性 | 删掉 `endOwnerRecoil` 里的 `setDeltaMovement(Vec3.ZERO)` |
| 后坐也带点高度 | `GRAVITY_COMPENSATION`（调大就往上飘） |
| 后坐期间允许摔伤 | 删掉 `player.resetFallDistance()` |
| 柱子能穿过草/花 | 已经是（`ClipContext.Block.COLLIDER`）；若想被所有方块挡住，改成 `ClipContext.Block.OUTLINE` 之类 |
| 撞墙后不反向、直接停 | 删掉 `frontBlocked` 分支里的 `length` 增长与 `pushOwnerBackward` 调用 |

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 客户端看到的柱子长度和服务端不一致 | 忘了同步 `front`/`length`（撞墙这类依赖世界的状态不能靠两端各算） |
| 柱子撞墙后总长变短 | 只处理了「前端停住」，没有把剩余长度交给后端 |
| 柱子卡进方块里 | 射线命中后没扣除 `BLOCK_MARGIN` |
| 玩家刚被推完就继续滑出去 | 结束衰减比例太大；`RECOIL_END_VELOCITY_SCALE` 调小（0 = 立刻停住） |
| 空地开火时玩家莫名被推 | 没做接触判定，或没把后坐限定在反向伸长阶段 |
| 玩家后坐时掉下悬崖摔伤 | 没调 `resetFallDistance()` |
| 用了 `setNoGravity(true)` 后玩家永久失重 | 该字段会写进玩家存档；中途掉线/崩溃就可能残留，建议用速度补偿代替 |
| 每 tick 只从发射点射线一次 | 性能浪费，而且柱子会被自己已越过的地形「重新挡住」 |

## 8. 验证方式

```bash
./gradlew build
./gradlew runDatagen   # 无界面启动，验证同步字段与注册不报错
./gradlew runClient    # 进游戏：贴着墙开火，观察柱子顶墙后从自己身后长出来、自己被顶退
```

进游戏后的三个观察点：

1. **空地对墙**：柱子 30 格全在前方；
2. **贴墙（前方 5 格就是墙）**：柱子前端停在墙面，后端退到自己身后 25 格，玩家被向后推约 25 格；
3. **草/花/水**：不被挡住，柱子正常穿过去。

## 9. 小结

- 柱子用「前端距离 + 总长度」两个量描述，`base = front − length` 自然支持「反向伸长」。
- 撞墙靠 `ClipContext` 射线逐 tick 检测，只认有碰撞箱的方块。
- **依赖世界的几何必须同步**；纯视觉才可以两端各算各的。
- 玩家后坐是**接触触发**的：碰到柱子才开始，推开「剩余长度」，与后端等速；**结束时速度 ×0.1**；全程用速度补偿抵消重力、清空坠落距离，因此不造成伤害。
- 实体位移是 tick 级的，20 Hz 已是上限；「提高击退频率、降低单次距离」的正确解法是把每 tick 的速度调到与伸长同速。
