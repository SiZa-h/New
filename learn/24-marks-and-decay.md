# 24. 黑白标记与「消散」：跨武器的状态 + 一个自带时钟的 buff

需求（原文）：

> 被所有黑方块柱命中时添加一个黑标记 B（发出攻击者免疫）
> 被所有白方块柱命中添加白标记 W（发出者免疫）
> 实体同时获得黑标记 B 与白标记 W，则 3 秒赋予消散 buff。
> 消散 buff：每 0.5 秒受到随生效时间 t*2 点的伤害（例如生效 0.5 秒，则受到 1 的伤害，生效 1 秒则受 2 点，1.5 则受 3 点伤害），
> 每次被赋予状态时不重置时长，而是直接加时长（例如在剩余 0.23 秒时，再次获得 3 秒 buff，则时长为 0.23+3.00）。

这一篇有三件事值得记：标记用什么存、图标怎么跟上、以及**为什么这个 buff 不能只用 `MobEffectInstance`**。

## 1. 标记继续用原版实体标签

标记（B / W，以及原有的 1/2/3）就是**原版实体标签**：

```java
public static final int BLACK = 4;
public static final int WHITE = 5;

public static String tag(int mark) {
	return mark == BLACK ? "new-mod:markB" : mark == WHITE ? "new-mod:markW" : "new-mod:mark" + mark;
}
```

好处前面几篇已经写过：`/tag @e list` 可见、命令方块与其他模组能读、跟着实体走、存档往返不丢、**零同步代码**。

**关键设计：标签是唯一事实来源。** 图标、计时器都从标签**派生**，而不是各存一份状态：

```java
public static void applyAndSync(Entity entity, int mark) {
	apply(entity, mark);

	if (!(entity instanceof LivingEntity living)) return;

	// 两个标记都在 → 开始/续期；否则停止
	if (has(living, BLACK) && has(living, WHITE)) Decay.start(living);
	else Decay.stop(living);

	ModEffects.syncMarks(living, has(1), has(2), has(3), has(BLACK), has(WHITE));
}
```

于是"谁给谁上了标记、中途标记没了怎么办"都不用额外记账——**下一 tick 从标签重新算**即可。

## 2. 图标：五个纯显示用的状态效果

要让玩家一眼看出身上有哪几个标记，用状态效果做图标最省事（原版 HUD 自带渲染、自带排序）：

```java
public static final Holder<MobEffect> MARK_BLACK = register("mark_black", MobEffectCategory.HARMFUL, 0x0A0A0F);
...
private static void set(LivingEntity living, Holder<MobEffect> effect, boolean wanted) {
	MobEffectInstance current = living.getEffect(effect);
	if (wanted && current == null) {
		living.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false));
	} else if (!wanted && current != null) {
		living.removeEffect(effect);
	}
}
```

- 时长 `INFINITE_DURATION` + **不显示粒子**（`false, false`）→ 只当一个"徽章"；
- 加/删都由 `syncMarks` 依据标签决定 → **图标不可能和标记不一致**；
- 颜色取黑阶/白阶（`0x0A0A0F` / `0xEDEDEB`），与两颗星的外观呼应。

## 3. 消散：为什么不能只用 `MobEffectInstance`

需求里有两条是原版 buff **做不到**的：

1. **累加时长而不是重置**：剩余 0.23 s 时再给 3 s → 3.23 s；
2. **伤害随"已生效时间"增长**：每 0.5 s 一次，伤害 = `2 × 已生效秒数`。

`MobEffectInstance` 只能"设定时长"，也不会告诉你"已经跑了多久"。所以拆成两半：

| 角色 | 实现 | 职责 |
| --- | --- | --- |
| 效果本体 | `DecayEffect extends MobEffect` | 名字、分类、图标颜色、粒子 |
| 计时与伤害 | `Decay`（静态状态 + 服务端每 tick 推进） | 剩余时长、已生效时长、每 0.5 s 结算一次 |

```java
private static final class State {
	private int remaining;     // 剩余 tick
	private int elapsed;       // 已生效 tick（伤害用它算）
	private int sinceLastBite; // 距离上次结算过了多少 tick
}

public static void start(LivingEntity living) {
	State state = STATES.computeIfAbsent(living.getUUID(), id -> new State());
	state.remaining += APPLICATION_TICKS;   // ★ 累加，不重置
	state.sinceLastBite = 0;
	living.addEffect(new MobEffectInstance(effect, state.remaining, 0, false, true));
}
```

每 tick（从 `NewMod` 的 `END_SERVER_TICK` 推进）：

```java
state.remaining--; state.elapsed++; state.sinceLastBite++;

if (state.sinceLastBite >= INTERVAL_TICKS) {          // 10 tick = 0.5 s
	state.sinceLastBite = 0;
	hurt(level, living, state.elapsed / 20.0D);        // 伤害 = 2 × 已生效秒数
}
```

参数：`INTERVAL_TICKS = 10`、`DAMAGE_PER_SECOND = 2.0F`、`APPLICATION_TICKS = 60`（一次给 3 秒）。

**停止条件也来自标记**，而不是自己判断：

```java
if (!Marks.has(living, Marks.BLACK) || !Marks.has(living, Marks.WHITE)) {
	living.removeEffect(effect);
	iterator.remove();
	continue;
}
```

顺带处理三种"人没了"的情况：实体被移除、死亡、或者已经不在任何已加载的维度里（`find(server, uuid)` 返回 null）→ 清掉记录，避免长跑服务器越积越多。

## 4. 伤害走自定义伤害类型

```java
victim.hurtServer(level, level.damageSources().source(ModDamageTypes.DECAY, null), damage);
victim.setDeltaMovement(Vec3.ZERO);
victim.syncVelocity = true;
```

`data/new-mod/damage_type/decay.json` 的 `message_id` 决定死亡消息，语言文件里写：

```json
"death.attack.decay": "%1$s 消散了",
"death.attack.decay.player": "%1$s 在逃离 %2$s 时消散了"
```

> 伤害类型**不进** `bypasses_armor` 标签 → 护甲照常减免（与"无视护甲"的黑柱形成对比）。

## 5. 跨武器状态是怎么落地的

两个标记分别由两把武器给，这是同一套机制第一次被**跨武器**使用：

| 来源 | 位置 | 备注 |
| --- | --- | --- |
| 黑标记 B | `BlackBlockPillarEntity.pushTargets`：伤害**真正生效**（`hurtServer` 返回 true）时 | 所有黑方块柱共用这条路径（短柱/长柱/对撞柱/弹幕柱/冲刺柱） |
| 白标记 W | `WhitePillarEntity.strike`：命中并结算伤害后 | 白柱自己的命中逻辑 |

两处都天然满足"**发出攻击者免疫**"——owner 在那之前就被 `continue`/`skip` 掉了。

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 图标和标记不一致 | 图标单独记账；应当**只由标签派生** |
| 重复获得时被重置成 3 秒 | 用了 `addEffect(new MobEffectInstance(...))` 而没有累加自己的时钟 |
| 伤害不增长（一直是 1） | 用 `amplifier` 或固定值算伤害；应当用**已生效时长** |
| 标记没了但还在掉血 | 停止条件没跟标记联动（只在死亡时清） |
| 长跑服务器内存增长 | 实体离开/死亡时没有移除 `Map` 里的记录 |
| 打了标记却看不到图标 | `addEffect` 的 `visible` 参数写成 `false`（那样连图标也不显示） |

## 7. 小结

- **标记 = 标签**：可见、可存档、零同步；一切派生状态（图标、计时）都从它算出来；
- 需要一个"能累加、能记时"的 buff 时，把**效果本体**与**时钟**拆开：效果只负责表现，时钟负责数值；
- 跨武器/跨系统的状态，最好收敛成一个**公共入口**（`Marks.applyAndSync`），所有来源都调用它，规则只有一份；
- 停止条件要写全：失去标记、死亡、实体移除、离开世界、时间耗尽。
