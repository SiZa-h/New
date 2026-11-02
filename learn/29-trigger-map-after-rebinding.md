# 29. 触发映射表：两颗星的按键与行为（一次大改版后的最终形态）

这一篇是"查表用"的：所有输入 → 行为 → 代码位置。触发方式在这一轮被整体重排过，之前的笔记（
[18](18-explosion-knockback-barrage-and-sneak-skills.md)、[23](23-flying-pillar-white-block-pillar.md)、
[27](27-white-star-input-hold-and-offhand.md)）里关于"潜行左键 / 按住右键"的描述以本篇为准。

## 1. 总表

### 黑方块之星（`PillarKind.BLACK_BLOCK`）

| 输入 | 行为 | 冷却槽 | 实现 |
| --- | --- | --- | --- |
| 双击左键 | 短柱（30 格） | `LEFT` 60t | `GestureInput` → `PillarPayload.SHORT` |
| 双击右键 | 长柱（60 格，尖端向底端填充） | `RIGHT` 20t | `GestureInput` → `PillarPayload.LONG` |
| 单击右键 + 左右滑 | 水平对撞柱（标记 2） | `HORIZONTAL_*` 10t | `GestureInput` → `AxisStrikePayload` |
| 单击左键 + 上下滑 | 竖直对撞柱（标记 3） | `VERTICAL_*` 10t | `GestureInput` → `AxisStrikePayload` |
| **潜行 + 双击右键** | **冲刺柱（7 号）** | `SNEAK_DASH` 20t | `BlackStarSneakInput` → `BlackSneakPayload.DASH` → `sneakDash` |
| **潜行 + 单击右键** | **感染成黑方块（8 号）** | `SNEAK_INFECT` 20t | `BlackStarSneakInput` → `BlackSneakPayload.INFECT` → `corruptTo(BLACK_BLOCK)` |
| 潜行 + 左键 | **什么都不做**（左键在潜行层不复用） | — | 回调里直接 `return true` 吞掉 |

### 白方块之星（`ModItems.WHITE_BLOCK_STAR`）

| 输入 | 行为 | 实现 |
| --- | --- | --- |
| 按住左键 | 从**身后一圈**持续放出白柱，朝准心汇聚（每 2 tick 一根） | `WhiteStarInput` → `FIRE_START/STOP` → `WhiteStarFire` |
| **双击右键** | **切换抓取**：把 30 格内准心指向的生物钉在视线上；再双击一次松开 | `WhiteStarInput` → `PULL_TOGGLE` → `PinTargets` |
| **潜行 + 右键** | **感染成白方块** | `WhiteStarInput` → `CORRUPT` → `corruptTo(WHITE_BLOCK)` |
| 潜行 + 左键 | 什么都不做（且按住左键的发射在潜行时被强制停止） | `WhiteStarInput` 里 `!sneaking` 才允许发射 |
| 副手持有时攻击 | 任何造成伤害的行为额外召唤 2~5 根白柱（2 tick 限流） | `OffhandWhiteStar`（`ServerLivingEntityEvents.AFTER_DAMAGE`） |
| 白柱累计命中 30 次后 | 每次命中额外召唤一根刺白方块柱 | `WhitePillarStrikes` + `ThornPillarEntity` |

**刺白方块柱**：0.1 × 5 格、无视方块、命中后不消失；绕目标做圆周运动（圆盘垂直于玩家视线，方向随机），
每 0.5 秒穿刺一次；**不计入连击**，且只由"玩家打出的白柱命中"续命，停火 2 秒后消散。

## 2. 为什么要"等一个窗口"：单击 / 双击共存

黑星的潜行层和白星的右键都出现"同一个键、单击与双击含义不同"，所以都需要**延迟判定**：

| 场景 | 第一击 | 第二击（≤4 tick） | 窗口到期 |
| --- | --- | --- | --- |
| 黑星 潜行+右键 | 记下时间 | 冲刺（`DASH`），取消感染 | 感染（`INFECT`） |
| 白星 右键（非潜行） | 记下时间 | 切换抓取（`PULL_TOGGLE`） | 什么都不做 |
| 黑星 非潜行+右键 | 记下时间（柱子的双击窗口） | 长柱 | 什么都 不做（交给手势） |

实现上都是同一套：

```java
if (down && !rightWasDown) {
	if (tick - lastPress <= DOUBLE_CLICK_TICKS) {
		lastPress = -1000;      // 用掉这次双击
		doDoubleClickAction();
	} else {
		lastPress = tick;       // 第一击：等
		pendingUntil = tick + DOUBLE_CLICK_TICKS;
	}
}
if (pendingUntil >= 0 && tick > pendingUntil) {
	pendingUntil = -1;
	doSingleClickAction();      // 窗口到期，才是单击
}
```

两个细节：

1. **潜行时白星的右键不做双击判定**：潜行+右键明确就是感染，立刻发包，没有 4 tick 的延迟感；
2. **判定在客户端，校验在服务端**：服务端重查"是不是这颗星、是不是真的在潜行、冷却是否就绪"，
   所以伪造包既不能绕过潜行要求，也不能把感染目标改成别的方块。

## 3. 所有"开关"集中在一处

| 想要的效果 | 改哪里 |
| --- | --- |
| 黑星双击窗口 | `GestureInput.DOUBLE_CLICK_TICKS`、`BlackStarSneakInput.DOUBLE_CLICK_TICKS` |
| 白星右键双击窗口 | `WhiteStarInput.DOUBLE_CLICK_TICKS` |
| 白柱出生位置（身后距离 / 环形半径） | `WhiteStarFire.SPAWN_BACK` / `SPAWN_RADIUS` |
| 白柱发射频率 | `WhiteStarFire.INTERVAL_TICKS` |
| 白柱伤害 | `WhitePillarEntity.TOUCH_DAMAGE` |
| 抓取距离 | `PinTargets.GRAB_RANGE` |
| 连击门槛 / 断连重置 | `WhitePillarStrikes.HITS_TO_SUMMON` / `COUNTER_TIMEOUT_TICKS` |
| 刺柱存活 / 绕圈 / 穿刺节奏 | `ThornPillarEntity.IDLE_TIMEOUT_TICKS` / `ANGULAR_SPEED` / `PIERCE_INTERVAL_TICKS` |
| 各技能冷却 | `ModNetworking.FIRE/LONG_FIRE/GESTURE/SNEAK_COOLDOWN_TICKS` |

## 4. 常见坑

| 现象 | 原因 |
| --- | --- |
| 潜行右键没反应 | `BlackStarSneakInput.active()` 要求主手是黑星且正在潜行；或服务端 `handleBlackSneak` 的校验没过 |
| 潜行单击右键变成冲刺 | 两次点击间隔超过窗口（4 tick），被当成两次单击 |
| 潜行左键还会放柱子 | `GestureInput.usable()` 没有排除潜行；或旧版 `ClientPreAttackCallback` 里还留着感染分支 |
| 白星双击右键抓不到 | 双击判定写成了两次都立即执行；或 `PinTargets.isHolding` 的判断放在了客户端 |
| 潜行右键既感染又抓取 | 客户端没有对"潜行"分支短路 |
| 感染变成黑方块 | `corruptTo` 的方块参数写错（白星应传 `WHITE_BLOCK`） |
| 刺柱越打越多停不下来 | thorn 的穿刺被算进了连击（本轮修的就是这个） |

## 5. 小结

- 触发方式重排时，**先写一张"输入 → 行为 → 冷却槽"的总表**，再动代码；否则很容易漏掉某个旧分支；
- 同一个键承载单击与双击时，**单击必须等窗口**，且窗口到期才执行；
- 客户端只做"哪个动作"的判定，**是否合法全部由服务端重查**；
- 删除某条旧触发路径时，连同它的 payload 一起删（本轮删掉了 `CorruptPayload`），否则会留下"能发包但不该生效"的暗门。
