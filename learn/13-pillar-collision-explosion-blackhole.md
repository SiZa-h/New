# 13. 两柱相撞：爆炸与标记 1

黑方块之星的两个技能现在能联动：

> **长黑方块柱**与**短黑方块柱**相撞时，二者**停止伸长**，在碰撞处**发生爆炸**，
> 并给爆炸范围内的生物打**标记 1**。

> **2024 修正（见需求第 26 / 27 / 28 条）**：
> 相撞后会发生三件事 ——
> ① **1 号 + 2 号行为**（左键短柱 × 右键长柱）的爆炸在碰撞点生成**黑洞实体**，
> 持续 **1 秒**并把波及的生物**按回碰撞点**（禁锢）；**对撞柱、弹幕收尾的爆炸都不生黑洞**；
> ② 给爆炸波及且仍存活的生物打**标记 1**，并在**每只生物身上**生成两条垂直于玩家视线的 20 格空白透明轴
> （水平 / 竖直，**跟随它移动**）；
> ③ 标记 1 附带 **5 秒只清速度**（`MarkLockEntity`，**不固定位置**）。

长柱是从 60 格外朝玩家扫回来的，短柱是玩家身前 1 格朝前长的——两者**相向而行**，
只要同时放，就会在中间撞上。

## 1. 两根柱子共用同一条中轴

要让它们必定相遇，前提是两条轴线共线。做法很直接：**两个技能用同一条生成逻辑**——中轴都取玩家的视线方向轴。

- 短柱：从这条轴上的第一个点（`shortPillarOrigin`，玩家眼前 1 格）出发，沿视线方向长 30 格；
- 长柱：**同一个起点、同一个朝向**，只是生成即满长 60 格，随后由尖端向底端填充。

| | 起点（实体位置） | 朝向 | 满长覆盖区间 | 实际先出现的部分 |
| --- | --- | --- | --- | --- |
| 短柱 | `S` | `d` | `[S, S + 30·d]` | 从 `S` 沿 `d` 长出来 |
| 长柱 | `S` | `d` | `[S, S + 60·d]` | 由 `S + 60·d`（尖端）向 `S`（底端）填充 |

于是两根柱子是同一条直线上的两段、短柱整根落在长柱里（细节见
[learn/11](11-right-click-long-pillar.md) §2）。顺带一提：玩家被柱子击退时位移是**沿着这条轴**的，
轴线本身不会平移，所以连续施法仍然落在同一条线上。

> **相撞的时机**：长柱一开始是「满长但透明、无功能」的，相撞判定读的是它的**有效段**
> （＝已经填出来的那一截）。所以填充前沿扫到短柱身上时才会撞上——大约在填充进行到一半的时候。

## 2. 相撞判定：线段与线段的最近距离

两根柱子都是「后端 → 前端」的线段，所以碰撞检测就是**两条线段的最近距离**：

```java
private void checkPillarCollision(ServerLevel level) {
	if (!this.explodesOnCollision() || this.isRemoved()) return;      // 只有长柱主动检查

	Vec3 from = ..., to = ...;                                        // 本柱线段
	AABB search = new AABB(from, to).inflate(COLLISION_DISTANCE + 1.0D);

	for (BlackBlockPillarEntity other : level.getEntitiesOfClass(BlackBlockPillarEntity.class, search)) {
		if (other == this || other.getType() == this.getType() || other.isRemoved()) continue;

		Vec3 impact = this.findCollisionPoint(from, to, otherFrom, otherTo);
		if (impact != null) { this.collideWith(level, other, impact); return; }
	}
}
```

找到最近点用的是**采样 + 点到线段投影**，而不是精确的线段—线段最近点算法：

```java
private @Nullable Vec3 findCollisionPoint(Vec3 from, Vec3 to, Vec3 otherFrom, Vec3 otherTo) {
	double length = from.distanceTo(to);
	int steps = Math.max(1, (int) Math.ceil(length));       // 按 1 格步长采样

	Vec3 best = null;
	double bestDistance = COLLISION_DISTANCE;

	for (int i = 0; i <= steps; i++) {
		Vec3 point = from.lerp(to, (double) i / steps);
		Vec3 closest = closestPointOnSegment(otherFrom, otherTo, point);
		double distance = point.distanceTo(closest);

		if (distance <= bestDistance) {
			bestDistance = distance;
			best = point.add(closest).scale(0.5D);           // 碰撞点取两最近点的中点
		}
	}

	return best;
}
```

- 柱子截面是 1×1，所以**中心线距离 1 格以内就算贴住**（`COLLISION_DISTANCE = 1.0`）；
- 采样点最多偏半格，阈值是 1 格，**不会漏判**；比精确算法好懂很多；
- **只处理「不同类型的柱子」**：长柱 × 短柱、长柱 × 黑曜石柱、长柱 × 永黑柱都会触发；
  两根同类型的柱子擦肩而过不会有任何事（需求只说了「长柱与短柱」）。

## 3. 撞上之后：停止伸长 → 爆炸 → 打标记

```java
private void collideWith(ServerLevel level, BlackBlockPillarEntity other, Vec3 impact) {
	// 二者都停止伸长：常规柱子停下「生长」，填充型长柱连「填充」一起停（sweepFinished）
	this.stopGrowing();
	other.stopGrowing();

	// 爆炸：真实爆炸（破坏方块 + 造成伤害），口径由设置页决定
	level.explode(this, impact.x, impact.y, impact.z,
			(float) EXPLOSION_RADIUS, Settings.explosionInteraction());

	// 爆炸波及的生物
	List<LivingEntity> victims = new ArrayList<>(level.getEntitiesOfClass(
			LivingEntity.class, new AABB(impact, impact).inflate(EXPLOSION_RADIUS)));

	// 黑洞：只有组 0（1 号 + 2 号行为）的爆炸才生成，持续 1 秒、把波及者按回碰撞点
	if (this.collisionGroup() == ModEntities.GROUP_LONG
			|| other.collisionGroup() == ModEntities.GROUP_LONG) {
		BlackHoleEntity blackHole = new BlackHoleEntity(ModEntities.BLACK_HOLE, level);
		blackHole.setPos(impact.x, impact.y, impact.z);
		blackHole.capture(victims);
		level.addFreshEntity(blackHole);
	}

	int mark = this.collisionGroup() == GROUP_LONG || other.collisionGroup() == GROUP_LONG
			? Marks.PILLAR_CLASH                                   // 标记 1
			: Math.max(this.collisionMark(), other.collisionMark()); // 标记 2 / 3

	for (LivingEntity victim : victims) {
		Marks.apply(victim, mark);

		if (mark == Marks.PILLAR_CLASH) {
			this.applyMarkOne(level, victim);      // 标记 1：在它身上生两条空白轴 + 禁锢 5 秒
		}

		this.tryTriggerBarrage(level, victim);     // 集齐三层 → 弹幕终结技
	}
}
```

**爆炸是真实爆炸**：默认用 `Level.ExplosionInteraction.TNT` —— **会破坏方块、会造成伤害**，
和 TNT 的爆炸一样（不点火）；威力 / 波及半径是 `EXPLOSION_RADIUS = 4`。
是否破坏方块现在由**设置页**决定（见 [learn/17](17-settings-screen-for-world-breaking-toggles.md)）：

| 设置 | 实际用的口径 | 效果 |
| --- | --- | --- |
| 爆炸破坏方块：**是**（默认） | `Level.ExplosionInteraction.TNT` | 破坏地形 + 造成伤害 |
| 爆炸破坏方块：**否** | `Level.ExplosionInteraction.MOB` | 只伤生物、不炸地形 |

> 另外三种口径也能手动换：`BLOCK`（只破坏地形）、`NONE`（都不），
> 或者干脆删掉 `level.explode(...)` 这一句。

停止之后双方各自消散：**长黑方块柱是「尖端 → 底端」**（前端往回退），常规柱子是「底端 → 尖端」（后端前收）。

长柱那一下（标记 1）的产物是**在被打上标记的那只生物身上**：

```java
private void applyMarkOne(ServerLevel level, LivingEntity victim) {
	Vec3 center = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
	Vec3 view = this.owner != null ? this.owner.getViewVector(1.0F).normalize() : this.getBeamDirection();
	Vec3 horizontal = new Vec3(0.0D, 1.0D, 0.0D).cross(view).normalize();   // 玩家左手方向

	if (horizontal.lengthSqr() > 1.0E-6D) {
		Vec3 vertical = view.cross(horizontal).normalize();                  // 垂直于视线的「上」
		this.spawnAxis(ModEntities.HORIZONTAL_AXIS, level, center, horizontal);
		this.spawnAxis(ModEntities.VERTICAL_AXIS, level, center, vertical);
	}

	MarkLockEntity lock = new MarkLockEntity(ModEntities.MARK_LOCK, level);
	lock.setTarget(victim);
	lock.setPos(victim.getX(), victim.getY(), victim.getZ());
	level.addFreshEntity(lock);
}
```

- 两条轴以生物**身体中心**为中点、各 20 格（两端各 ±10），朝向分别取**玩家左手**与**垂直于视线的上**，
  并且**每 tick 跟着这只生物移动**（`BlankAxisEntity.setTarget`），所以它走位/被推走时端点也跟着走；
- **禁锢 5 秒**由 `MarkLockEntity` 负责：每 tick 只把**速度清零**（并 `syncVelocity`）与清坠落距离，
  **不固定位置** —— 按需求「只清速度」，所以它虽然自己走不动，但仍可能被外力（爆炸、柱子击退）推动；
- 两组对撞柱互撞时**只爆炸**，分别打标记 2 / 3（不生成轴、也不禁锢）。

## 4. 黑洞实体：1 号 + 2 号行为专用，持续 1 秒

> 它只由**左键短柱 × 右键长柱**（组 0）的爆炸生成；对撞柱互撞、弹幕收尾都不生。
> 禁锢方式是**按回位置**（速度清零 + 每 tick 传送回碰撞点）。标记 1 的禁锢则是**只清速度**，
> 两者不同，别混。

```java
public class BlackHoleEntity extends Entity {
	public static final int LIFETIME_TICKS = 100;         // 5 秒后消散，松手
	public static final double CAPTURE_RADIUS = 4.0D;     // 爆炸波及 / 禁锢半径

	private final List<Entity> captured = new ArrayList<>();

	@Override
	public void tick() {
		super.tick();
		this.lifeTicks++;
		if (!(this.level() instanceof ServerLevel serverLevel)) return;
		if (this.lifeTicks >= LIFETIME_TICKS) { this.discard(); return; }

		this.emitParticles(serverLevel);     // 用粒子显现（没有做模型）
		this.holdCaptured();
	}

	private void holdCaptured() {
		this.captured.removeIf(entity -> entity == null || entity.isRemoved()
				|| !entity.isAlive() || entity.level() != this.level());

		for (Entity entity : this.captured) {
			entity.setDeltaMovement(Vec3.ZERO);
			entity.syncVelocity = true;      // 客户端也看到它被定住
			entity.fallDistance = 0.0D;      // 别因为禁锢而摔伤

			if (entity instanceof ServerPlayer serverPlayer) {
				serverPlayer.teleportTo(this.getX(), this.getY(), this.getZ());   // 玩家必须走这个重载
			} else {
				entity.teleportTo(this.getX(), this.getY(), this.getZ());
			}
		}
	}
}
```

四个细节：

1. **玩家的传送必须用 `ServerPlayer#teleportTo`**。`Entity#teleportTo(x,y,z)` 内部只是 `snapTo(...)`，
   **不会给客户端发传送包**，玩家会被橡皮筋拉回去（服务端以为他在中心，客户端还在原地）。
2. **禁锢 = 每 tick 清零速度 + 直接定位**，不用改 AI、也不用禁止移动输入：
   位置每 tick 被拉回中心，生物自然动弹不得。
3. **每 tick 顺手 `resetFallDistance`（`fallDistance = 0`）**：被按在半空中的生物落地时不该摔伤。
4. **有寿命**（5 秒）而不是永久禁锢：实体 `noSave()`，服务器重启就消失，不会留下「被永久粘住」的存档。

## 5. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 相撞判定更宽松 / 更严格 | `BlackBlockPillarEntity.COLLISION_DISTANCE`（默认 1.0 格） |
| 爆炸范围 / 标记范围 / 黑洞捕获范围 | `BlackBlockPillarEntity.EXPLOSION_RADIUS`（默认 **8 格**）—— 一处生效、四处受益 |
| 禁锢时长 | `BlackHoleEntity.LIFETIME_TICKS`（默认 20 tick = 1 秒） |
| 改成别的爆炸口径 | `collideWith` 里的 `Level.ExplosionInteraction.TNT` → `BLOCK`（只炸地形）/ `MOB`（只伤生物）/ 删掉整句（完全不炸） |
| 只允许长柱 × 短柱 | `checkPillarCollision` 里把 `other.getType() == this.getType()` 的判断换成「只认短柱类型」 |
| 让黑曜石柱也能引发相撞 | 给它的 `PillarSpec` 把最后一个参数改成 `true` |
| 黑洞要有模型 | 目前渲染器是空的（只有粒子）；可参照 `BlackBlockPillarRenderer` 用方块模型堆一个 |

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 两根柱子穿过去了没反应 | 只注册了「长柱主动检查」；若想让另一根也检查，把它的 `explodesOnCollision` 打开（注意会双向触发，要加去重） |
| 玩家被禁锢后橡皮筋抖动 | 用了 `Entity#teleportTo` 而不是 `ServerPlayer#teleportTo` |
| 被禁锢的生物仍能小幅移动 | 只清零了速度没做定位；位置每 tick 都要按回中心 |
| 禁锢结束后生物掉下去摔伤 | `MarkLockEntity` 里没有清 `fallDistance` |
| 同时炸出两个黑洞 | 双方都开了相撞检测且各自触发；用 `collided` 标志认领即可 |
| 两根柱子平行错开、撞不上 | 中轴没取视线方向轴（例如被人为改成按别的基准生成） |
| 爆炸把自己也炸死了 | 真爆炸的正常后果；换 `MOB` 或删掉爆炸那一句 |

## 7. 验证方式

```bash
./gradlew build
./gradlew runDatagen
./gradlew runClient    # 进游戏：先右键再左键（或反过来），让两根柱子对撞
```

观察点：

1. 两根柱子在中间相遇 → 立即双双停下（不再变长）并开始消散；
2. 碰撞点发生**真实爆炸**（破坏方块与否则看设置页）；
3. 碰撞点出现**黑洞**（只有 1 号 + 2 号行为会），把周围 4 格内**仍然存活**的生物**按在中心 1 秒**；
4. 同时这些生物被逐只打上**标记 1**、每只身上出现两条空白轴（看不见，但手势能感知），
   并且**5 秒内自己动不了**（速度被每 tick 清空；位置不固定，仍会被爆炸/柱子推动）；
5. 接着用「**单击 + 滑动**」在轴的端点放对撞柱 → 两柱互撞只爆炸、打标记 2 / 3；集齐三层标记触发弹幕。
   （柱子本身是**双击**触发，单击只会开一个 4 tick 的手势窗口——见 [learn/15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md)。）

## 8. 小结

- 先保证**两根柱子共用玩家视线方向轴**，相撞才是必然事件而不是巧合。
- 线段碰撞用「采样 + 点到线段投影」足够准，还不必写精确的线段—线段最近点算法。
- 相撞的后果拆成三步：**停止伸长 → 真实爆炸（1/2 号行为还生 1 秒黑洞）→ 打标记（标记 1 附带「生轴 + 只清速度 5 秒」）**；每步都能单独调。
- **玩家的传送必须走 `ServerPlayer#teleportTo`**，否则服务端与客户端会打架。
- 禁锢用「每 tick 清零速度 + 定位」实现，配上寿命与 `fallDistance` 清理，既有手感又不留隐患。
