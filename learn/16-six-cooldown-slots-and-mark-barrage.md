# 16. 六格独立冷却 + 集齐三层标记触发「黑方块柱弹幕」

两件事：

1. **六种行为各自独立冷却**（左键短柱、右键长柱、四个手势对撞柱），互不冲突；
2. 实体**集齐三层标记**（标记 1 + 2 + 3）后触发终招：**黑方块柱从四面八方穿透目标**，
   三根一循环（1↔2 ≥ 60°、1↔3 ≥ 100°），持续 **20 秒**，最后 1 秒是**收尾**——
   把目标**禁锢在半空**、周边**同时生成一圈柱子同时命中**，最后**一记大爆炸**收场；
3. 除 1 号行为（左键短柱 / 黑曜石方）外，其余行为的柱子**连基岩一起破坏**（清路到底）。

## 1. 六格独立冷却：把 Slot 枚举从 2 个扩到 6 个

`WeaponCooldowns` 的记账键本来就是「**玩家 + 物品 + 技能槽**」，所以扩展只是一件事：
把 `Slot` 枚举写全。

```java
public enum Slot {
	LEFT,             // 左键短柱
	RIGHT,            // 右键长柱
	HORIZONTAL_LEFT,  // 单击右键 + 右滑
	HORIZONTAL_RIGHT, // 单击右键 + 左滑
	VERTICAL_BOTTOM,  // 单击左键 + 上滑
	VERTICAL_TOP      // 单击左键 + 下滑
}
```

每个行为在自己的调用点判定 + 记账：

```java
// 手势：把包里的端点直接映射成槽位
WeaponCooldowns.Slot slot = switch (endpoint) {
	case AxisStrikePayload.HORIZONTAL_LEFT -> WeaponCooldowns.Slot.HORIZONTAL_LEFT;
	case AxisStrikePayload.HORIZONTAL_RIGHT -> WeaponCooldowns.Slot.HORIZONTAL_RIGHT;
	case AxisStrikePayload.VERTICAL_BOTTOM -> WeaponCooldowns.Slot.VERTICAL_BOTTOM;
	default -> WeaponCooldowns.Slot.VERTICAL_TOP;
};

if (!WeaponCooldowns.isReady(player, stack, slot)) {
	return;                                   // 这一格还在冷却
}

... 生成对撞柱 ...

WeaponCooldowns.apply(player, stack, slot, GESTURE_COOLDOWN_TICKS);
```

为什么不直接用原版的 `ItemCooldowns`：它是「一物一冷却」，六个行为会互相顶掉（详见
[learn/12](12-independent-skill-cooldowns.md)）；而且它的公开 API 没有「按冷却组查询」的口子。

> 顺带一提：**单击右键是 3/4 号手势的起手，双击右键才是 2 号长柱**，两者用的是**不同槽位**，
> 而且由客户端在单击后的 4 tick 窗口里定性（滑了 → 手势，出现第二击 → 长柱，都没有 → 什么都不放），
> 所以它们不会互相顶掉，也不会在同一次操作里同时触发。
> 见 [learn/15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md)。

| 常量 | 默认 | 说明 |
| --- | --- | --- |
| `ModNetworking.FIRE_COOLDOWN_TICKS` | **60 tick（3 秒）** | 左键短柱 |
| `ModNetworking.LONG_FIRE_COOLDOWN_TICKS` | 20 tick | 右键长柱 |
| `ModNetworking.GESTURE_COOLDOWN_TICKS` | 10 tick | 四个手势对撞柱各自一格 |

## 2. 触发条件：`Marks.hasAll`

三层标记 = 标记 1 + 2 + 3（三种相撞各一层）：

```java
public static boolean hasAll(Entity entity) {
	return has(entity, PILLAR_CLASH) && has(entity, HORIZONTAL_PAIR) && has(entity, VERTICAL_PAIR);
}
```

判定放在**打标记的同一个循环**里，所以「哪一次爆炸补上第三层，就在那一刻触发」：

```java
for (LivingEntity victim : victims) {
	Marks.apply(victim, mark);
	this.tryTriggerBarrage(level, victim);
}
```

```java
private void tryTriggerBarrage(ServerLevel level, LivingEntity victim) {
	if (!Marks.hasAll(victim)) {
		return;
	}

	Marks.clearAll(victim);      // 消耗掉三层标记：想再来一次必须重新打一套连招

	PillarBarrageEntity barrage = new PillarBarrageEntity(ModEntities.PILLAR_BARRAGE, level);
	barrage.setTarget(victim);
	barrage.setPos(victim.getX(), victim.getY(), victim.getZ());
	level.addFreshEntity(barrage);
}
```

**为什么消耗标记**：标记是原版实体标签、不会自己过期，如果留着，那「只要身上有 3 层标记，
之后每一次爆炸都会再触发一次弹幕」——连环触发，很快就会失控。消耗掉之后语义也干净：
三层标记 = 一套连招的能量，用掉就没了。

## 3. 弹幕做成一个「控制器实体」

弹幕不是粒子效果也不是全局计时器，而是一个**不可见的实体**：位置跟着目标走，活 10 秒。

```java
public void tick() {
	super.tick();
	this.lifeTicks++;

	if (!(this.level() instanceof ServerLevel serverLevel)) return;

	Entity victim = this.target;

	if (victim == null || victim.isRemoved() || !victim.isAlive() || victim.level() != serverLevel
			|| this.lifeTicks > DURATION_TICKS) {
		this.discard();      // 目标没了/时间到 → 收工
		return;
	}

	this.setPos(victim.getX(), victim.getY(), victim.getZ());   // 跟着目标

	// 收尾阶段
	if (this.lifeTicks >= DURATION_TICKS - FINALE_TICKS) {
		this.tickFinale(serverLevel, victim);
		return;
	}

	if ((this.lifeTicks - 1) % INTERVAL_TICKS == 0) {
		this.fire(serverLevel, victim);
	}
}
```

### 3.1 收尾：禁锢在半空 → 同时生成 → 同时命中 → 爆炸

```java
private void tickFinale(ServerLevel level, Entity victim) {
	if (this.holdPoint == null) {                                        // 收尾第一 tick
		this.holdPoint = victim.position().add(0.0D, HOLD_HEIGHT, 0.0D); // 抬到半空
		this.fireVolley(level, victim);                                  // 周边同时生成一圈
	}

	this.holdVictim(victim);                                             // 每 tick 按住

	if (this.lifeTicks >= DURATION_TICKS) {                              // 最后
		level.explode(this, center.x, center.y, center.z,
				FINALE_EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
		this.discard();
	}
}
```

三个细节：

- **「同时命中」怎么保证**：齐射的每根柱子都在**同一个距离**（`FINALE_DISTANCE = 12`）生成、
  朝向同一个中心点、速度相同，所以它们**同一 tick 抵达**目标——不需要任何同步机制。
- **方向怎么铺得好看**：用**斐波那契球面**把 14 个方向均匀铺开，
  比随机方向整齐，也比「只绕水平一圈」更「四面八方」：

  ```java
  public static Vec3 fibonacciDirection(int index, int count) {
      double y = 1.0D - 2.0D * (index + 0.5D) / count;
      double radius = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
      double theta = Math.PI * (1.0D + Math.sqrt(5.0D)) * index;
      return new Vec3(Math.cos(theta) * radius, y, Math.sin(theta) * radius);
  }
  ```

- **禁锢**和黑洞用的是同一套手法（速度清零 + 每 tick 定位 + 清坠落距离；玩家必须走
  `ServerPlayer#teleportTo`），差别只是这里把位置**抬高 `HOLD_HEIGHT = 2.5` 格**，所以是「禁锢在半空」。
  > 注意：**标记 1 的禁锢只清速度、不固定位置**（见 [learn/13](13-pillar-collision-explosion-blackhole.md)）；
  > 真正「按住不放」的只有这里——弹幕收尾。两者的区别就是 `holdVictim` 里那两行传送。

做实体而不是「挂在目标身上的一个 Map」的三个好处：

1. **生命期自带**：`noSave()` + `discard()`，不需要自己写超时清理，也不会被写进存档；
2. **天然跟随**：每 tick 把位置贴到目标身上，柱子永远围着目标打；
3. **可见可调试**：`/kill @e[type=new-mod:pillar_barrage]` 就能停掉，`/summon` 也能单独试。

## 4. 「穿透」怎么实现

一根柱子的**生成点离目标 8 格**，而柱长是 **60 格**（速度快到 120 格/秒），朝向是「从生成点指向目标」：

```java
Vec3 spawn = center.add(direction.scale(SPAWN_DISTANCE));   // 目标外侧 8 格
BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.BARRAGE_PILLAR, level);
pillar.setBeamDirection(direction.scale(-1.0D));            // 朝向目标
pillar.setPos(spawn.x, spawn.y, spawn.z);
level.addFreshEntity(pillar);
```

于是柱子从 8 格外长过来，**穿过目标**再往外冲 12 格 —— 这就是「穿透实体」：
柱子不在实体处停下（我们的柱子本来就只被**方块**挡住），而命中伤害照常结算。

> 生成距离必须**小于**柱长，否则柱子还没碰到目标就长满了。两个参数都在
> `PillarBarrageEntity.SPAWN_DISTANCE` 与 `ModEntities.BARRAGE_LENGTH`。

## 5. 四面八方 + 夹角约束

方向在**球面**上均匀取，而不是只绕水平一圈：

```java
private Vec3 randomUnitVector() {
	double z = this.random.nextDouble() * 2.0D - 1.0D;                 // z 均匀
	double azimuth = this.random.nextDouble() * Math.PI * 2.0D;       // 方位角均匀
	double radius = Math.sqrt(Math.max(0.0D, 1.0D - z * z));          // 保证是单位向量
	return new Vec3(radius * Math.cos(azimuth), z, radius * Math.sin(azimuth));
}
```

三根一循环，用**拒绝采样**满足夹角：

| 第几根 | 约束 |
| --- | --- |
| 1 | 完全随机（同时作为这一轮的基准） |
| 2 | 与第 1 根夹角 ≥ 60° |
| 3 | 与第 1 根夹角 ≥ 100°，并且与第 2 根也拉开 ≥ 60° |

```java
double minDot = Math.cos(Math.toRadians(minDegrees));

for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
	Vec3 candidate = this.randomUnitVector();

	if (candidate.dot(from) <= minDot && (alsoSpread == null || candidate.dot(alsoSpread) <= spreadDot)) {
		return candidate;      // 夹角够大：用点积判断，dot ≤ cos(最小夹角)
	}
}

return this.randomUnitVector();   // 兜底：宁可偶尔重叠，也不能不放柱子
```

为什么用点积：两根方向的夹角 θ 满足 `dot = cosθ`，所以「夹角 ≥ 100°」等价于 `dot ≤ cos(100°) ≈ -0.17`。
判断一次乘法即可，不用算 `acos`。

## 6. 弹幕柱不参与相撞

新分组常量：

```java
public static final int GROUP_NONE = -1;      // 不主动检测，但会被长柱撞到（常规柱子）
public static final int GROUP_LONG = 0;       // 长柱：撞任何「可撞」的柱子 → 黑洞 + 标记 1
public static final int GROUP_HORIZONTAL = 2; // 水平对
public static final int GROUP_VERTICAL = 3;   // 竖直对
public static final int GROUP_IMMUNE = -2;    // 完全不参与相撞（弹幕柱）
```

判定里先看对方是不是免疫组：

```java
if (theirs == ModEntities.GROUP_IMMUNE) {
	return false;      // 弹幕柱不参与任何相撞
}
```

否则 20 根弹幕柱会在目标身上互相撞爆，炸出一堆黑洞 —— 那就不是「弹幕」而是「烟花」了。

## 7. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 六个行为各自的冷却时长 | `FIRE_COOLDOWN_TICKS` / `LONG_FIRE_COOLDOWN_TICKS` / `GESTURE_COOLDOWN_TICKS` |
| 弹幕持续时长 / 密度 | `PillarBarrageEntity.DURATION_TICKS`（800 = 40 秒）/ `INTERVAL_TICKS`（3 = 0.15 秒一根） |
| 柱子离目标多远生成（穿透感） | `PillarBarrageEntity.SPAWN_DISTANCE`（必须小于柱长） |
| 单根柱子多长 / 多快 | `ModEntities.BARRAGE_LENGTH`（60 格）/ `BARRAGE_SPEED_PER_TICK`（12.0 = 240 格/秒） |
| 弹幕伤害 / 击退强度（推力与距离） | `PillarSpec.damageScale`（0.5）/ `knockbackScale`（0.5）；收尾爆炸威力不变（`FINALE_EXPLOSION_POWER` = 6.0） |
| 收尾把目标按回哪里 | `triggerPoint`（弹幕第一次 tick 记下的位置）+ `HOLD_HEIGHT`，见 [learn/18](18-explosion-knockback-barrage-and-sneak-skills.md) |
| 夹角约束 | `MIN_ANGLE_1_2` / `MIN_ANGLE_1_3` / `MIN_ANGLE_2_3` |
| 收尾时长 / 齐射数量 / 举多高 | `FINALE_TICKS`（20）/ `FINALE_PILLARS`（28）/ `HOLD_HEIGHT`（2.5） |
| 收尾爆炸威力 | `FINALE_EXPLOSION_POWER`（6.0，TNT 是 4） |
| 触发需要几层标记 / 是否消耗 | `Marks.hasAll` / `tryTriggerBarrage` 里的 `Marks.clearAll` |
| 哪些行为能破坏基岩 | `PillarSpec.breaksUnbreakable`（只有 1 号行为是 `false`）**并且**设置页里的「基岩」开关开着（见 [learn/17](17-settings-screen-for-world-breaking-toggles.md)） |
| 弹幕柱要不要清路 | `ModEntities` 里 `BARRAGE_PILLAR` 的 `destroysPath`（当前开启） |

## 8. 常见坑

| 现象 | 原因 |
| --- | --- |
| 放完一种技能其它全进冷却 | 还在用原版 `ItemCooldowns`；应各自占一个 `WeaponCooldowns.Slot` |
| 集齐标记后弹幕连发好几次 | 触发时没消耗标记（或没用「本次已触发」标志） |
| 柱子没碰到目标就消失 | `SPAWN_DISTANCE` 大于柱长 |
| 弹幕柱互相撞爆、炸出一堆黑洞 | 弹幕柱没有用 `GROUP_IMMUNE` |
| 柱子全从水平方向来 | 只在圆周上取角度；应在球面上取（z 均匀） |
| 夹角偶尔不满足 | 拒绝采样到上限后的兜底分支——要么放宽约束，要么加大 `MAX_ATTEMPTS` |
| 目标死后弹幕还在放 | `tick` 里没检查 `target.isAlive()` / `isRemoved()` |
| 齐射的柱子没有同时命中 | 生成距离不一致（改成同一个 `FINALE_DISTANCE`），或速度不同 |
| 收尾把目标按不住 | 忘了每 tick 重设速度；或玩家用了 `Entity#teleportTo`（要 `ServerPlayer` 的重载） |
| 想留基岩却被打穿了 | 该类型的 `breaksUnbreakable` 没设成 `false`（只有 1 号行为该是 `false`） |

## 9. 小结

- 「独立冷却」的关键从来不是数值，而是**记账的维度**：玩家 + 物品 + 行为槽；扩行为只需扩枚举。
- 连招触发用**边沿条件**（集齐三层 + 消耗），比「持有状态就触发」可控得多。
- 周期性的持续效果做成**控制器实体**最省事：生命期、跟随、清理、调试全都白送。
- 「穿透」= 生成距离 < 柱长 + 朝向目标，两行代码；不需要给柱子加什么穿透模式。
- 「四面八方」用球面均匀采样；夹角约束用**点积 + 拒绝采样**，比解析构造简单得多。
- **收尾的「同时」不需要同步**：让所有柱子的生成距离、方向、速度一致，它们自然同 tick 抵达。
- **禁锢**就是「每 tick 清零速度 + 定位」，抬高一点就是「禁锢在半空」。
- 破坏基岩这件事只差一个判断：`destroyBlock` 对基岩本来就有效，`getDestroySpeed < 0` 只挡玩家挖掘，
  所以我们用一个 `breaksUnbreakable` 开关决定「要不要跳过它」。
