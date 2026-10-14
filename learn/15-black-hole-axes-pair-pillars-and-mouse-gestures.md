# 15. 黑洞伴生轴、对撞柱与鼠标手势

> **修订（本次）**：触发方式定稿为
>
> | 行为 | 触发 |
> | --- | --- |
> | 1 号 左键短柱 | **双击左键** |
> | 2 号 右键长柱 | **双击右键** |
> | 3 / 4 号 水平对撞柱 | **单击右键 + 视角左/右滑** |
> | 5 / 6 号 竖直对撞柱 | **单击左键 + 视角上/下滑** |
> | 7 / 8 号 冲刺 / 感染 | 潜行 + 右键 / 左键（即时） |
>
> 也就是说：**单击是手势的起手，双击才是柱子**。因为"单击"要同时充当"手势起手"和"双击的第一击"，
> 单击本身**什么都不放**，只开一个 4 tick 的窗口，由窗口内发生的事来定性（滑动→手势，第二次点击→柱子，
> 都没有→什么都不发生）。1/2 号的请求走 `network/PillarPayload`，右键的 use 事件在服务端只负责**潜行冲刺**。
> `learn/19` 里那条"视角静止门禁"已删除。

黑方块之星的连招这一层：

> 长柱与短柱相撞 → **不再生成黑洞**，而是在**被打上标记 1 的生物身上**各生成**水平 / 竖直两条垂直于玩家视线的 20 格空白透明实体**，
> 并把这些生物**清除所有方向速度 5 秒**；之后用鼠标手势在轴的端点放出**对撞柱**，两柱相撞**只爆炸**并打**标记 2 / 3**。

| 手势 | 做什么 | 相撞后果 |
| --- | --- | --- |
| 单击（右键）+ **右**滑 | 水平轴**左**端生成 20 格柱 → 把敌人向**右**击退 | 只爆炸 + **标记 2** |
| 单击（右键）+ **左**滑 | 水平轴**右**端生成 → 向**左**击退 | 只爆炸 + **标记 2** |
| 单击（左键）+ **上**滑 | 竖直轴**下**端生成 → 把敌人**击飞** | 只爆炸 + **标记 3** |
| 单击（左键）+ **下**滑 | 竖直轴**上**端生成 → 把敌人**击落** | 只爆炸 + **标记 3** |

对撞柱**沿途清路**（`destroysPath = true`），所以哪怕撞点在实心岩层里，两根柱子也能一路清到中间撞上。

## 1. 「空白透明轴」：一个不可见的坐标系

两条轴本身**没有任何功能**（不可见、不伤害、不破坏），存在的意义只是给手势提供**两个端点**。
所以它就是一个普通实体：位置 = **被标记 1 的生物的身体中心**，朝向 = 原版实体的 yRot/xRot，长度 20 格，
并且**每 tick 跟随这只生物移动**（`setTarget`），于是它走位时端点也跟着走。

```java
/** 端点坐标。 */
public Vec3 endpoint(boolean positive) {
	double offset = positive ? LENGTH * 0.5D : -LENGTH * 0.5D;
	return this.position().add(this.getBeamDirection().scale(offset));
}
```

**朝向怎么取**（`view` = 玩家视线单位向量，`up` = (0,1,0)）：

```java
Vec3 horizontal = up.cross(view).normalize();       // 玩家的左手方向（始终水平）
Vec3 vertical = view.cross(horizontal).normalize(); // 垂直于视线的「上」
```

- `up.cross(view)`：对 `view = +Z`（朝南）得到 `+X`（东）——朝南时东在左手边，所以这是**左手**方向；
  于是水平轴的**正端点天然就是「左端」**，负端点就是「右端」；
- `view.cross(horizontal)`：它等于 `up - view·(up·view)`，也就是**垂直于视线的上方向**
  （平视时就是正上方，抬头/低头时随之倾斜），于是竖直轴的正端点是「上端」。

> **为什么把轴做成实体，而不是让客户端自己按视线算？**
> ① 生成时机在服务端（相撞那一刻），客户端未必知道；② 轴有生命期与归属（谁撞出来的谁能用）；
> ③ 端点/生长方向由服务端说了算，客户端只发「哪一端」，坐标无法伪造。

## 2. 相撞分组：为什么不能再用「类型不同」

原来的规则是「**不同类型**的柱子相撞」，对长柱够用；但水平对撞柱的左右两根**是同一个类型**
（方向由生成时的旋转携带），于是需要新的判据：

```java
/** 谁能和谁撞：长柱（组 0）撞任何别组的柱子；对撞柱只在同组、且方向相反时才互撞。 */
private boolean canCollideWith(BlackBlockPillarEntity other) {
	int mine = this.collisionGroup();
	int theirs = other.collisionGroup();

	if (mine == 0) {
		return theirs != 0;
	}

	return mine == theirs && this.getBeamDirection().dot(other.getBeamDirection()) < 0.0D;
}
```

- `collisionGroup`：`-1` 不参与 / `0` 长柱 / `2` 水平对 / `3` 竖直对（放在 `PillarSpec` 里，随类型走）；
- `dot(...) < 0` 要求两根柱**朝相反方向**长：左右两根互相奔赴 → 撞；若玩家连放两根「左端柱」，
  它们同向、不会互撞（否则两根完全重合的柱子会自爆）。

**只结算一次**——同一 tick 里两根柱子会互相检测，用 `collided` 标志让先跑的那根「认领」这次相撞：

```java
private void collideWith(ServerLevel level, BlackBlockPillarEntity other, Vec3 impact) {
	this.collided = true;
	other.collided = true;      // 对方这一 tick 稍后也会跑，看到自己的标志就跳过
	this.stopGrowing();
	other.stopGrowing();
	...
}
```

实体 tick 是顺序执行的，所以这足以保证**只爆炸一次、只生成一个黑洞**（对应需求里那句「只生成一个黑洞实体」）。

## 3. 标记：用记分板标签

标记不自己同步字段，直接用原版**实体标签**（26.3 里是 `entityTags()`）：

```java
public static void apply(Entity entity, int mark) {
	if (mark > 0) {
		entity.addTag(tag(mark));       // 标签名 "new-mod:mark1" / mark2 / mark3
	}
}

public static boolean has(Entity entity, int mark) {
	return mark > 0 && entity.entityTags().contains(tag(mark));
}
```

好处：

- `/tag @e list` 直接能看到，命令方块/其它模组也能读；
- 跟着实体走，存档往返不丢（不需要额外写 `ValueOutput`）；
- 想「消耗标记」做下一段连招，调用 `Marks.clear(entity, mark)` 即可。

爆炸之后的打标记就一行：

```java
int mark = this.collisionGroup() == 0 || other.collisionGroup() == 0
		? Marks.PILLAR_CLASH                                   // 标记 1
		: Math.max(this.collisionMark(), other.collisionMark()); // 标记 2 / 3

for (LivingEntity victim : victims) {
	Marks.apply(victim, mark);
}
```

> 这三层标记不是白打的：**集齐 1 + 2 + 3** 会触发终结技「黑方块柱弹幕」（四面八方穿透目标 10 秒），
> 触发时标记被消耗。见 [learn/16](16-six-cooldown-slots-and-mark-barrage.md)。

## 4. 手势识别：单击 + 视角转动（窗口定性模型）

**不写 Mixin**：鼠标滑动在游戏里本来就表现为**视角转动**，所以直接累计 `yaw / pitch` 的变化量即可。

```java
float deltaYaw = Mth.wrapDegrees(yaw - lastYaw);   // 必须 wrap：±180 环绕时直接相减会得到 -358
float deltaPitch = pitch - lastPitch;
```

四个状态 + 四个阈值：

| 常量 | 默认 | 含义 |
| --- | --- | --- |
| `DOUBLE_CLICK_TICKS` | 4 | **双击窗口**（从第一击算起）：第二击距它不超过此值才算双击 |
| `GESTURE_WINDOW_TICKS` | 4 | **单击之后的定性窗口**：这段时间内滑动算手势，过期就什么都不发生 |
| `HORIZONTAL_SWIPE_DEGREES` | 5° | 判定左右滑需要的 yaw 累计变化 |
| `VERTICAL_SWIPE_DEGREES` | 3° | 判定上下滑需要的 pitch 累计变化 |

**核心：单击不是"顺手记一笔"，而是一次延迟定性。** `GestureInput` 是唯一的决策点：

```java
// 一次点击 = 开一个窗口，并记住"它是哪根柱子的第一击"
private static void arm(boolean right) {
	horizontalArmed = right;                     // 右键 → 看 yaw，左键 → 看 pitch
	pressed = right ? PillarPayload.LONG : PillarPayload.SHORT;
	windowEnd = tick + GESTURE_WINDOW_TICKS;
	yawSum = 0.0F;
	pitchSum = 0.0F;
}

// 同一按钮的第二击落在双击窗口内 → 定性为"柱子"：立刻发包，并把这个窗口关掉
if (tick - last <= DOUBLE_CLICK_TICKS) {
	disarm();                                    // 双击不可能是手势
	send(right ? PillarPayload.LONG : PillarPayload.SHORT);
	...
}

// 每 tick：先累计滑动；够门槛就出手势；窗口过期还没动静 → 什么都不放
if (endpoint >= 0) { disarm(); send(new AxisStrikePayload(endpoint)); }
```

几个关键决定：

1. **单击什么都不做**。它只开窗口：`滑动 → 手势`、`同键第二击 → 柱子`、`都没有 → 静默过期`。
   这就是"触发手势时不会连带放柱子"的根本原因。
2. **顺序很重要**：`tick()` 里先判"窗口过期"再做右键轮询，这样**正好踩在截止 tick 上的那一击**会被
   当成"新的一次点击"，而不会先把旧的单击结算掉。
3. **两个手势互斥**：窗口里要么看 yaw、要么看 pitch（`horizontalArmed`），并要求它明显大于另一个轴的累计量
   （`|yawSum| > |pitchSum|`），避免斜着划一下同时触发。
4. **方向与 yaw 符号**：yaw 0 朝南、90 朝西，所以**向右转 = yaw 变大**。
   按需求这里是「反着」的：**右滑 → 水平轴左端**（把人往右推）、**左滑 → 水平轴右端**（往左推）；
   竖直方向则是**上滑 → 下端（击飞）**、**下滑 → 上端（击落）**。
5. **右键要自己"吞掉"原版使用**：`UseItemCallback` / `UseBlockCallback` **两侧都会触发**，
   所以服务端那条路现在只做**潜行冲刺**；客户端在非潜行时返回 `InteractionResult.FAIL`
   （`FAIL` 不会发出 `ServerboundUseItemPacket`），手势与柱子的包完全由 `GestureInput` 发。
   左键则由 `ClientPreAttackCallback` 拦截，并由它顺手 `player.swing(...)` 补上挥臂动画。
6. **不可用时清零**：打开 GUI、没拿黑方块之星、玩家为空时立刻 `disarm()`，避免"待定"跨界面残留。

## 5. 服务端怎么落地手势

```java
private static void strikeAxis(ServerPlayer player, int endpoint) {
	boolean vertical = AxisStrikePayload.isVertical(endpoint);
	BlankAxisEntity axis = findLatestAxis(player, vertical ? ModEntities.VERTICAL_AXIS : ModEntities.HORIZONTAL_AXIS);

	if (axis == null) {
		return;      // 还没撞出黑洞（没有轴）就什么都不做
	}

	boolean positive = endpoint == AxisStrikePayload.HORIZONTAL_LEFT
			|| endpoint == AxisStrikePayload.VERTICAL_TOP;
	Vec3 start = axis.endpoint(positive);
	Vec3 direction = axis.getBeamDirection().scale(positive ? -1.0D : 1.0D);   // 从这一端指向另一端
	...
}
```

- 对撞柱的**朝向就是击退方向**（`knockbackDirection()` 对常规柱子取自身朝向），
  所以「在左端生成、朝右长」＝「把敌人向右击退」，不需要额外的参数；
- 轴按「同类型 + 自己的归属 + 实体 id 最大」挑选，所以多人时不会抢别人的轴；
- 手势**不设冷却**：滑动本身有 5° / 3° 的门槛，天然限速；轴也有 10 秒生命期。

**柱子那一侧**（`PillarPayload`）：1/2 号不再从 use 事件里生成，而是由 `GestureInput` 在**双击判定成立的那一刻**
发包，服务端只回答"是否合法"：`canFireShortPillar` / `canFireLongPillar` 检查**手里是不是这把武器** +
**自己那一格冷却是否 ready**，然后生成柱子并记账（`WeaponCooldowns`）。

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 手势完全没反应 | 没拿黑方块之星；或还没撞出黑洞（没有空白轴）；或单击后超过 `GESTURE_WINDOW_TICKS`(4 tick) 才滑动 |
| 单击就放出了柱子 | 双击判定把两击合并了：`DOUBLE_CLICK_TICKS` 太大，或把"第二击"判到了别的按钮上 |
| 想放柱子却打出手势 | 那一击之后手抖划过了 5°（yaw）/ 3°（pitch）——窗口期内滑动优先判手势 |
| 单击+滑动经常被"吞掉" | 滑动起手太慢，4 tick 窗口已经过期；调大 `GESTURE_WINDOW_TICKS`（代价：双击放柱子更迟钝） |
| 一划就触发两次 | 发完手势没 `disarm()`，或同时满足两个轴的判据 |
| yaw 环绕时判反了方向 | 没对 yaw 差值用 `Mth.wrapDegrees` |
| 打开背包后手势乱触发 | 没有在 `gui.screen() != null` 时清零待命状态 |
| 右键点箱子/按钮没反应 | 手里拿着黑方块之星时客户端会吞掉右键（`FAIL`）；换成别的物品即可正常交互 |
| 左右两根对撞柱不互撞 | `canCollideWith` 少了「同组」或「方向相反」其中一条 |
| 一次相撞炸出两个黑洞 | 没用 `collided` 认领，双方在同一 tick 各结算了一次 |
| 右键连点/长按刷出多根长柱 | 忘了在发包处查 `WeaponCooldowns`（或把冷却判定挪到了客户端） |
| 连点两下第三下也放出柱子 | 双击成立后没有把 `lastLeftClick` / `lastRightClick` 重置成哨兵值 |
| 26.3 里 `entity.getTags()` 编译不过 | 方法改名为 `entityTags()`；屏幕要用 `client.gui.screen()` |

## 7. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 轴的长度 | `BlankAxisEntity.LENGTH`（默认 20 格，两端各 ±10） |
| 轴的存在时长 | `BlankAxisEntity.LIFETIME_TICKS`（默认 200 tick = 10 秒） |
| 对撞柱长度 / 速度 | `ModEntities.PAIR_PILLAR_LENGTH` / `PillarSpec.speedPerTick` |
| 对撞柱是否清路 | `PillarSpec.destroysPath`（现已打开：一路破坏到撞上为止） |
| **双击窗口**（两击间隔上限） | `GestureInput.DOUBLE_CLICK_TICKS`（默认 4 tick） |
| **手势窗口**（单击后等滑动的时间，也就是双击放柱的延迟） | `GestureInput.GESTURE_WINDOW_TICKS`（默认 4 tick） |
| 滑动门槛 | `GestureInput.HORIZONTAL_SWIPE_DEGREES`（5°）/ `VERTICAL_SWIPE_DEGREES`（3°） |
| 标记名 | `Marks.tag(int)`（默认 `new-mod:mark1/2/3`） |
| 对撞柱只爆炸、不要黑洞 | 已经如此：只有 `collisionGroup == 0`（长柱）才生成黑洞与两条轴 |

## 8. 小结

- 把「坐标参考系」做成**不可见实体**，比在客户端各自算更可靠：服务端权威、有归属、有生命期。
- 相撞判据从「类型不同」升级为 **分组 + 方向相反**，才能支持「同类型的左右两根柱互撞」。
- 一次相撞只结算一次，用**对方也置位的标志**在同 tick 内认领，比加锁/排队简单得多。
- **一键多义时，让"窗口"来定性，而不是先做再撤**：单击 → 4 tick 窗口 →
  滑动=手势 / 第二击=柱子 / 都没有=无事发生。既不用退冷却，也不用删实体。
- **判定所需的信息在哪一侧，决策就放在哪一侧**：点击与滑动只有客户端知道，所以 `GestureInput` 负责定性；
  服务端只保留"是否合法"的权威判定（武器 + 独立冷却）。
- 鼠标手势可以只用**视角转动**来识别——不必写 Mixin 去读鼠标私有位移，也顺手处理了 yaw 环绕。
