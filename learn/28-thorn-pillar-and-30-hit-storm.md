# 28. 刺白方块柱：30 连击后的随机游走与每秒穿刺

需求（原文）：

> 创建：刺白方块柱，属性套用白方块柱，但攻击后不会消失，也不会被方块交互。
> 添加，当一个实体被白方块柱连续攻击 30 次后，每次白方块柱攻击额外从亚空间召唤（刺白方块柱）对生物进行穿刺，
> 然后以玩家视线为切线进行随机方向的圆周运动，期间每次穿刺生物都造成伤害，
> 当生物不受到白方块柱的伤害，则在 2 秒后消散。

这一篇讲三件事：**如何用继承复用「白柱」**、**连击计数为什么要独立于实体**、**随机游走 + 定时穿刺的状态机怎么写**。

## 1. 复用：`ThornPillarEntity extends WhitePillarEntity`

"属性套用白方块柱"最省事的做法就是继承——形状（0.1 × 5）、伤害、白标记、渲染器全部直接复用，
只覆盖 `tick()` 换掉运动方式：

```java
public class ThornPillarEntity extends WhitePillarEntity {
	public ThornPillarEntity(EntityType<? extends ThornPillarEntity> type, Level level) { super(type, level); }

	@Override
	public void tick() { ... }    // 不再沿直线飞：随机游走 + 每秒一次穿透
}
```

代价是父类要把 thorn 需要的东西从 `private` 放宽到 `protected`：

```java
protected @Nullable Entity owner;                          // thorn 的伤害来源
protected final Set<UUID> hitTargets = new HashSet<>();    // 复用"每目标只打一次"（thorn 用不上，但无害）
```

而"伤害 + 打标记"被提成一个静态方法，供两条路径共用；**连击计数只在真正的飞行柱命中时调用**：

```java
// WhitePillarEntity：只负责伤害与标记
protected static void damage(ServerLevel level, LivingEntity target, @Nullable Entity owner) {
	DamageSource damageSource = level.damageSources().source(ModDamageTypes.WHITE_BLOCK_STAR, owner);
	target.hurtServer(level, damageSource, TOUCH_DAMAGE);
	Marks.applyAndSync(target, Marks.WHITE);
}

// WhitePillarEntity#strike（飞行柱命中）：额外记一次连击
damage(level, target, this.owner);
WhitePillarStrikes.onWhitePillarHit(level, target, this.owner);

// ThornPillarEntity#pierce（穿刺）：只造成伤害，不计入连击
WhitePillarEntity.damage(level, victim, this.owner);
```

> 设计点 1：**伤害只有一个入口**。飞行柱命中、thorn 穿刺、副手齐射全都走 `damage(...)`，
> 于是"伤害数值与标记"不会再出现"某条路径忘了加"的情况。
>
> 设计点 2：**计数与伤害分开**。需求明确"刺柱不计入连击"——否则 thorn 的穿刺会继续喂计数器，
> 召唤出更多 thorn，风暴会自我维持到服务器卡死。拆开之后，只有**玩家射出的白柱**能推进连击。

## 2. 与方块的交互：覆盖 `tick` 就自然免疫

白柱的 `blocked()` 是在 `tick()` 里调的。thorn **整个覆盖了 `tick()`**，于是：

- 不检查方块 → **不会被方块挡住**（需求的"不会被方块交互"）；
- 不检查射程/寿命 → 不会飞满 128 格消散；
- 不调用 `rest()` → 不会残留。

它只受两个条件约束：目标还在，以及"目标最近有没有挨白柱打"。

## 3. 连击计数为什么不能放在实体上

`WhitePillarStrikes` 用一个 `Map<UUID, Record>` 记每只生物的连击：

```java
private static final class Record {
	private int hits;
	private int staleTicks;
	private final List<ThornPillarEntity> thorns = new ArrayList<>();
}
```

原因很直接：**同一次"连击"来自不同的柱子实体**（一根柱子打一下就消散）。计数必须挂在**受害者**身上，
不能挂在任何一根柱子上。

```java
public static void onWhitePillarHit(ServerLevel level, LivingEntity victim, Entity owner) {
	Record record = RECORDS.computeIfAbsent(victim.getUUID(), id -> new Record());

	record.hits++;
	record.staleTicks = 0;
	refresh(record);                       // 已存在的 thorn 续命（这就是"2 秒无伤害才消散"）

	if (record.hits > HITS_TO_SUMMON) {    // 第 31 次起，每次命中都额外召唤一根
		summon(level, victim, owner, record);
	}
}
```

- `HITS_TO_SUMMON = 30`：**超过** 30 才开始召唤，正好是"连续攻击 30 次后，每次攻击额外召唤"；
- `staleTicks` 超过 40（2 秒）没挨**白柱**打 → 记录删除，连击重置，下一次要重新攒 30 次；
- 目标死亡/移除 → 记录删除（每 tick 检查），避免长期运行的服务器越积越多。

**刺柱不计入连击**：`onWhitePillarHit` 只被飞行柱的 `strike()` 调用，thorn 的 `pierce()` 不调用它。
所以 thorn 既不会推进计数器（否则风暴自我增殖），**也不会给自己续命**——续命只来自玩家真正打出的白柱。

> 这也意味着：一旦停火，thorn 最多再活 2 秒；要维持风暴就必须持续命中。

## 4. 轨迹：随机游走 + 每秒一次穿刺

> **轨迹改过一次**。最初是"绕视线做圆周运动"；现在是需求里的"**随机·游走**，1 秒后再次穿透目标"。
> 下面写的是**当前实现**（圆周那套 `ORBIT_RADIUS` / `ANGULAR_SPEED` 已经删掉）。

两个阶段交替，状态机只有 `wandering` / `piercing` 两个取值：

**阶段 1：随机游走**（`WANDER_RADIUS = 2.5` 格内）

```java
if (--this.pierceTicks <= 0) { beginPierce(victim, center); return; }   // 1 秒到了

// 偶尔换方向（平滑随机游走，不是每 tick 乱抖）
if (dir 未定 || random < WANDER_TURN_CHANCE || 离中心超过半径) dir = randomDirection();
if (离中心超过半径) dir = center.subtract(position).normalize();        // 想往外走就拐回来
Vec3 next = position().add(dir.scale(WANDER_STEP));                     // 0.22 格/tick
if (next 离中心 > 半径) next = center + (next-center).normalize() * 半径; // 硬夹紧
```

- `WANDER_TURN_CHANCE = 0.07`：平均约 14 tick 换一次方向，看起来像"游荡"而不是"抽搐"；
- 半径内还有**硬夹紧**，这样目标跑得快也不会把刺柱落在身后。

**阶段 2：穿透**（`PIERCE_SPEED = 2.2` 格/tick，全长 `PIERCE_LENGTH = 4.0` 格）

```java
beginPierce():  pierceDir = 随机单位向量;
                position = center - pierceDir * (PIERCE_LENGTH / 2);    // 从身体一侧起步
tickPierce():   position += pierceDir * PIERCE_SPEED;
                命中判定（一次/趟）：离中心 ≤ 0.6，或进入受影响体略放大的包围盒
                走满 PIERCE_LENGTH → 回到游走，pierceTicks = 20（1 秒后再来）
```

- 起点在身体**一侧**、走满 4 格，所以视觉上是**穿过去**而不是"在旁边抖一下"；
- `pierceTicks = 20` 就是需求里的"**1 秒后再次穿透**"；`setTarget` 时也初始化为 20，所以召唤后先游走 1 秒；
- **兜底**：万一这一趟因为目标瞬移没命中，走满长度时**强制结算一次**，保证"每秒必有一次伤害"。

> "以玩家视线为切线"的那套几何（圆盘垂直于视线）已经随轨迹一起移除了；
> 现在刺柱的朝向 = 它的**运动方向**（渲染的 5 格长条指向它要去的地方）。

## 5. 消散：2 秒无白柱伤害

```java
if (this.idleTicks-- <= 0) { this.discard(); return; }
```

`idleTicks` 初值 `IDLE_TIMEOUT_TICKS = 40`，每次 `refresh()` 重置为 40。`refresh()` 由
`WhitePillarStrikes` 在"该生物又挨了一记白柱"时调用 → 于是**只要白柱还在命中，thorn 就一直在**；
白柱停了 2 秒，它们同时消散。

## 6. 参数一览

| 常量 | 位置 | 默认 | 含义 |
| --- | --- | --- | --- |
| `TOUCH_DAMAGE` | `WhitePillarEntity` | **8.0** | 白柱与 thorn 的单次伤害（本轮从 5.0 提高） |
| `HITS_TO_SUMMON` | `WhitePillarStrikes` | 30 | 连击多少次后开始召唤 |
| `COUNTER_TIMEOUT_TICKS` | `WhitePillarStrikes` | 40 | 断连多久后计数清零（2 秒） |
| `IDLE_TIMEOUT_TICKS` | `ThornPillarEntity` | 40 | thorn 多久没续命就消散（2 秒） |
| `WANDER_RADIUS` | `ThornPillarEntity` | 2.5 | 游走半径（离受害者中心） |
| `WANDER_STEP` | `ThornPillarEntity` | 0.22 | 游走速度（格/tick） |
| `WANDER_TURN_CHANCE` | `ThornPillarEntity` | 0.07 | 每 tick 换方向的概率 |
| `PIERCE_INTERVAL_TICKS` | `ThornPillarEntity` | 20 | 每 **1 秒**穿刺一次 |
| `PIERCE_SPEED` / `PIERCE_LENGTH` | `ThornPillarEntity` | 2.2 / 4.0 | 穿刺冲刺速度与贯穿长度 |
| `SPAWN_BACK` / `SPAWN_RADIUS` | `WhiteStarFire` | 1.0 / 1.2 | 白柱在玩家**身后** Distance/半径（身后一圈） |

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| thorn 一出来就被墙挡住 / 立刻消失 | 覆盖 `tick()` 时仍调用了父类的方块判定 |
| thorn 打一下就没了 | 穿刺后调用了 `discard()`（需求是"攻击后不会消失"） |
| 连击永远攒不满 | 计数放在了柱子实体上（每根都从 0 开始） |
| 连击数各自为政 | 用"每根柱子命中次数"而不是"每只生物被命中次数" |
| 白柱停了 thorn 还满天飞 | 没有 idle 计时，或 `refresh()` 没被每次命中调用 |
| 刺柱满地图乱飞 | 游走没有半径夹紧，目标跑动后没有"拐回来" |
| 刺柱只在旁边抖、不疼 | 没有冲刺阶段，只在圆周上算距离；穿刺要有一趟"穿过身体"的位移 |
| 每秒伤害偶尔漏一次 | 冲刺没做兜底结算（走满长度仍未判定到就强制结算一次） |
| 长期运行内存增长 | 目标死亡/离场时没有清理 `RECORDS` |

## 8. 小结

- **属性复用交给继承**，运动差异通过覆盖 `tick()` 实现；把公共的"伤害 + 标记"提成一个入口，避免漏加；
- **跨越多次命中的状态要挂在受害者身上**（`Map<UUID, …>`），而不是挂在会消散的实体上；
- **两种节奏就用状态机**：随机游走（平滑转向 + 半径夹紧）与穿刺冲刺（一次穿过 + 走满即回）分开写，比塞进一个公式好调得多；
- "刺柱不计入连击、也不续命"这类反自激规则，要落在**调用点**上（谁不调用计数入口），而不是靠注释说明；
- "X 秒没有受到某类伤害就消散"用 **idle 计时 + 每次命中刷新**，比在实体里自己判断伤害来源简单得多。
