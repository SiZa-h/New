# 19. 触发门禁：一次「视角移动量」尝试，以及它为什么被双击取代

> **修订（本次）**：**这条视角门禁已经整体删除**（`effect/ViewMotion.java` 与 `ModNetworking.viewStill` 都不在了）。
> 触发器现在是「**单击开窗口 + 窗口内定性**」，见 §3 与 [learn/15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md)：
>
> - **1 号（左键短柱）**：双击左键（`PillarPayload.SHORT`）；
> - **2 号（右键长柱）**：双击右键（`PillarPayload.LONG`）；
> - **3~6 号手势**：单击之后 4 tick 内滑动（右键看 yaw、左键看 pitch）；
> - 单击既不滑动也没有第二击 → **什么都不发生**；
> - 视角怎么转都不再影响能不能放，只影响朝向。
>
> 保留本篇是因为它记的技术点仍然有用（每 tick 朝向变化怎么算、yaw 环绕、两侧都要判、
> 以及 §4 的爆炸/标记范围 8 格），只是"门禁"这一层不再是当前实现。

需求原文：给 **1 号（左键短柱，含黑曜石方）** 与 **2 号（右键长柱）** 加一条判定 ——
**视角在水平或竖直方向的移动角度小于 1 度才触发，大于 1 不触发**；顺带把爆炸与标记范围从 4 格扩到 8 格。

> 一句话理解：**停住不动才能放，一边转视角一边点是没有反应的。**

> **它为什么被删掉**：门禁只能"偶尔"挡住误触——手势本身要求双击+滑动，而第一击早就把柱子放出去了；
> 真正的问题（触发 3~6 号时连带触发 1/2 号）必须靠"**把柱子的发射推迟到手势判定之后**"来解决，
> 也就是现在的待命窗口模型。

## 1. 「移动量」= 一个 tick 内朝向变化的绝对值

MC 把鼠标位移逐帧加到玩家朝向（yaw / pitch）上，所以「移动量」最自然的度量就是**每 tick 的变化量**：

```java
float deltaYaw   = Math.abs(Mth.wrapDegrees(yaw - previousYaw));   // 水平
float deltaPitch = Math.abs(pitch - previousPitch);                // 竖直

boolean still = deltaYaw < STILL_LIMIT_DEGREES && deltaPitch < STILL_LIMIT_DEGREES;   // 1.0 度
```

- **两个方向都要满足**：水平在转、或者竖直在转，都算「在移动」，都不触发；
- **yaw 必须 `wrapDegrees`**：朝向是从 -180 到 180 的循环量，从 179° 转到 -179° 实际只动了 2°，
  直接相减会得到 -358°（于是「明明没怎么动」却被判成剧烈移动）；
- pitch 在 -90…90 之间、不会环绕，直接相减即可。

## 2. 两侧共用一个小工具

门禁要在客户端（不发包、不给冷却动画）和服务端（权威）各判一次，但**公共代码不能引用客户端类**
（那是典型的「服务端加载客户端类」崩溃源）。所以把两侧的采样都塞进公共类 `effect/ViewMotion`：

| 侧 | 采样 | 查询 |
| --- | --- | --- |
| 服务端 | 每服务端 tick `ViewMotion.sample(player)`（在 `NewMod` 的 `END_SERVER_TICK` 里） | `ViewMotion.isStill(player)` |
| 客户端 | 每客户端 tick `ViewMotion.sampleLocal(yaw, pitch)`（由 `GestureInput` 顺手喂） | `ViewMotion.isLocalStill()` |

`ModNetworking` 再包一层，让调用点统一：

```java
public static boolean viewStill(Player player) {
	if (player instanceof ServerPlayer serverPlayer) {
		return ViewMotion.isStill(serverPlayer);
	}

	return ViewMotion.isLocalStill();      // 客户端分支：用本地采样
}
```

> 第一次采样（刚上线/刚进世界）当作「静止」，否则玩家会被凭空拦一次。

## 3. 门禁的顺序与返回值（历史实现，已删除）

```java
if (!(stack.getItem() instanceof PillarWeaponItem weapon) ...) return ...;   // ① 手里得是这把武器
if (serverPlayer.isShiftKeyDown()) return sneakDash(serverPlayer, stack);    // ② 潜行 = 另一个技能，先分流
if (!viewStill(serverPlayer)) return InteractionResult.SUCCESS;              // ③ 视角移动量门禁（已删）
if (!WeaponCooldowns.isReady(serverPlayer, stack, slot)) return ...;         // ④ 最后才查冷却
```

- 潜行技能（冲刺 / 感染）与四个手势**不受**这条限制，所以要在它之前分流；
- **门禁没过不消耗冷却**，否则玩家一转身点一下就被罚 1 秒；
- 返回值：左键不满足时返回 `false`（**放行原版左键**，仍能正常攻击/挖掘）；右键不满足时返回
  `InteractionResult.SUCCESS`（取消事件，连使用包都不发）。

**现在的顺序**（[learn/15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md)）在客户端就分完了：
`可用？→ 潜行分流 → 单击开窗口 → 窗口内（滑动 → 手势 / 同键第二击 → 柱子 / 都没有 → 无事发生）`，
服务端只保留"武器对不对 + 自己的冷却槽是否 ready"，**不再看视角**。

## 4. 顺带：爆炸与标记范围 4 → 8

```java
/** 相撞爆炸的威力 / 波及半径（格）：打伤害、打标记、黑洞捕获名单都以它为准。 */
private static final double EXPLOSION_RADIUS = 8.0D;
```

一处生效、四处受益：`level.explode` 的威力、伤害与**标记的判定球**、
以及黑洞的捕获名单（名单就取自这个球）。

## 5. 常见坑（当时踩过的，改双击模型后仍然值得注意）

| 现象 | 原因 |
| --- | --- |
| 「明明没动却放不出来」 | yaw 差值没做 `wrapDegrees`（跨 ±180 时会被算成几百度的移动）——手势的滑动累计同样要 `wrapDegrees` |
| 一直在转视角也能放 | 只判了一个方向；旧需求是**水平或竖直任一**移动量超标都不触发 |
| 门禁没过却进了冷却 | 冷却检查写在门禁**之前**（现在同理：**待命窗口没到期之前不要动冷却**） |
| 潜行技能放不出来 | 分流写在门禁**之前**；现在也一样，潜行分流必须在双击判定之前 |
| 转着视角连普通攻击都没了 | 左键回调里返回了 `true`（拦截原版）而不是 `false`（放行）：**只有准备接手技能时才拦截** |
| 右键被吞掉、方块交互也一起没了 | 客户端在 `useItemOn` 里返回了 `FAIL`：现在只在**手里拿着黑方块之星**时才这样，其它武器一律 `PASS` |

## 6. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 双击窗口 / 手势窗口 | `GestureInput.DOUBLE_CLICK_TICKS`（4）/ `GestureInput.GESTURE_WINDOW_TICKS`（4） |
| 滑动门槛 | `GestureInput.HORIZONTAL_SWIPE_DEGREES`（5°）/ `VERTICAL_SWIPE_DEGREES`（3°） |
| 爆炸与标记范围 | `BlackBlockPillarEntity.EXPLOSION_RADIUS`（默认 8 格） |

## 7. 小结

- 「移动量」用**每 tick 的朝向变化绝对值**度量最直接：`|Δyaw|`（要 `wrapDegrees`）与 `|Δpitch|`。
- 想「按住某个条件才允许放技能」，判定放在**客户端与服务端两侧**最稳；把两侧的采样收进一个**公共类**，
  公共代码就不必引用客户端类。
- **但"能不能放"不该由玩家的操作姿势（视角静止）决定**：这类门禁只是治标，一旦要支持"点击 + 滑动"，
  就必须把决策搬到一个能同时看到点击和滑动的层（现在的 `GestureInput`），
  否则第一击永远会漏出去。
