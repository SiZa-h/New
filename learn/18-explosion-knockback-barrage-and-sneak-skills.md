# 18. 力量收尾：不给速度的爆炸、击退冲突、吸向玩家的弹幕与两个潜行技能

这一批是「手感与规则」的收尾，一共五件事：

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 生物受伤期间被**两种黑方块实体**同时击退 → 速度清零 | `effect/PushConflicts` |
| 2 | 黑方块实体产生的**爆炸不提供速度** | `effect/Explosions` |
| 3 | 弹幕**速度、数量翻倍，击退距离减半，可穿透实体，落点尽量靠近玩家** | `PillarBarrageEntity` + `PillarSpec.knockbackScale` + `knockbackTarget` |
| 4 | **潜行右键**：身后生成冲刺柱，把玩家朝视线推出去，破坏周边方块 | `ModNetworking.sneakDash`（`dash_pillar`） |
| 5 | **潜行左键**：把面前一片方块感染成黑方块，结束潜行后掉落 | `effect/Corruption` |

## 1. 爆炸不提供速度：记速度 → 炸 → 还原

原版爆炸会顺手给附近每个生物一脚（改它们的速度）。要「不提供速度」，最省事的做法不是自己重写爆炸，
而是**把那一脚抹掉**：

```java
public static void explodeWithoutKnockback(ServerLevel level, Entity source, Vec3 center,
		float power, Level.ExplosionInteraction interaction) {
	// 稍微多圈一点范围：爆炸影响半径比 power 大，漏掉谁谁就还会被踢一脚
	List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
			new AABB(center, center).inflate(power + 2.0D));

	Map<Entity, Vec3> velocities = new HashMap<>();
	for (LivingEntity entity : nearby) {
		velocities.put(entity, entity.getDeltaMovement());
	}

	level.explode(source, center.x, center.y, center.z, power, interaction);

	for (Map.Entry<Entity, Vec3> entry : velocities.entrySet()) {
		Entity entity = entry.getKey();
		if (entity.isRemoved()) continue;

		entity.setDeltaMovement(entry.getValue());
		entity.syncVelocity = true;
	}
}
```

两个要点：

- **还原而不是清零**：目标可能正被柱子推着走，清零会把别的速度来源一起抹掉；
- 之所以能这么干，是因为爆炸只**设置速度**、位移发生在下一 tick —— 所以炸完立刻还原，等于它没被推过。
  范围多圈 2 格，避免「站在边缘、被踢了但没进我的记录」的漏网之鱼。

## 2. 两种柱子同时击退 → 速度清零

判定需要跨实体协作：**本 tick 有没有另一种黑方块实体也在推它**。用一张按 UUID 记的短命表即可：

```java
public static boolean isConflicting(ServerLevel level, Entity target, EntityType<?> type) {
	long tick = level.getGameTime();
	Push previous = LAST_PUSH.get(target.getUUID());
	boolean conflicting = previous != null && previous.tick() == tick && previous.type() != type;

	LAST_PUSH.put(target.getUUID(), new Push(tick, type));
	return conflicting;
}
```

调用点在 `pushTargets` 里、真正推之前：

```java
if (target.hurtTime > 0 && PushConflicts.isConflicting(level, target, this.getType())) {
	target.setDeltaMovement(Vec3.ZERO);
	target.syncVelocity = true;
	continue;      // 这一 tick 不再叠加任何击退
}
```

- `hurtTime > 0` 就是需求里的「**受伤期间**」（原版受击后的红闪计时）；
- 表在 `ServerTickEvents.END_SERVER_TICK` 里每 tick 清一次，不会积累；
- 用 `level.getGameTime()` 当「同一 tick」的凭据，比自己在实体上存字段干净得多。

## 3. 弹幕：更快、更多、更长、伤害更低，但结尾把人送回触发点

| 需求 | 改动 |
| --- | --- |
| 速度翻倍 | `BARRAGE_SPEED_PER_TICK` 6.0 → **12.0**（240 格/秒） |
| 数量翻倍 | `INTERVAL_TICKS` 6 → **3**（每 0.15 秒一根）、收尾齐射 14 → **28** 根 |
| 时间翻倍 | `DURATION_TICKS` 400 → **800**（40 秒） |
| 伤害减低 | `PillarSpec.damageScale`（弹幕柱 **0.5**）—— 收尾爆炸威力**不变**（仍是 6.0） |
| 击退距离减半 | `travelled < getMaxLength() * knockbackScale()` —— 倍率同时缩**推力与距离** |
| 可穿透实体 | 柱子本来就只被方块挡住，命中实体不减速（无需改动，属既有行为） |
| 期间可乱飞、结尾回触发点 | 主阶段**不做**任何吸附；收尾时把目标按回**弹幕触发点**上方 |

**「结尾回到触发点」的实现**：弹幕在第一次 tick 记下目标的位置，收尾阶段就用它当禁锢点：

```java
// 第一次 tick：记下弹幕触发点
if (this.triggerPoint == null) {
	this.triggerPoint = victim.position();
}

// 收尾：把目标按回触发点上方，而不是「它现在在哪就在哪」
Vec3 anchor = this.triggerPoint != null ? this.triggerPoint : victim.position();
this.holdPoint = anchor.add(0.0D, HOLD_HEIGHT, 0.0D);
```

> 上一版曾用「每根弹幕柱都把目标推向发射者」来保证落点，需求改成「期间可乱飞、只要结尾回到触发点附近」
> 之后，这套吸附就**整个删掉**了 —— 主阶段让弹幕把它打得满天飞，最后由 `holdVictim` 把它按回起点。

## 4. 潜行右键：冲刺柱

```java
Vec3 view = player.getViewVector(1.0F).normalize();
Vec3 start = origin.subtract(view.scale(DASH_BACK_OFFSET));     // 身后 3 格

clearAround(level, player.blockPosition(), DASH_CLEAR_RADIUS);  // 先破坏玩家周边方块

BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.DASH_PILLAR, level);
pillar.setOwner(player);
pillar.setBeamDirection(view);                                  // 朝视线方向长 → 把玩家往前推
pillar.setPos(start.x, start.y, start.z);
level.addFreshEntity(pillar);
```

- 柱子**在身后、朝视线方向长**，所以它的击退方向（柱子朝向）就是玩家面朝方向，玩家被一路推出去；
- 冲刺柱是独立的实体类型（`dash_pillar`）：60 格、60 格/秒、**允许击退发射者**（否则推不动玩家）、
  沿途清路、**不参与任何柱间相撞**（`GROUP_IMMUNE`）；
- 先 `clearAround` 清掉玩家周围 3 格，免得一冲就顶在墙上；
- 它与普通右键（长柱）**各占一个冷却槽**（`SNEAK_RIGHT`），互不影响。

> 重写右键逻辑时要先判潜行：`fireLongPillar` 一进来就 `if (player.isShiftKeyDown()) return sneakDash(...);`，
> 否则潜行右键会同时触发两个技能。

## 5. 潜行左键：感染（按下瞬间随机感染 64 块，松手都掉落）

按下那一刻**一次性**完成：从准心指向的第一个方块出发做**随机游走**，一路感染相邻方块，
直到**感染满 64 块**为止：

```java
for (int step = 0; step < MAX_STEPS && infection.blocks.size() < BLOCKS_PER_CAST; step++) {
	infectAt(level, infection, infection.current);
	spread(player, infection);      // 随机走一个相邻方块
}
```

- **一次性**而不是按时间蔓延：需求从「以可观测速度蔓延」**回滚**成「直接随机感染 64 个方块」，
  于是速度公式（`V=(1-0.1n)×5`）、每 tick 攒进度的字段全部删掉，`tick` 只剩「松开就结算」；
- **随机游走**让感染是一团从第一个方块长出去的区域（形状每次不同），**半径 3 格的球**是它的边界，
  出界就退回起点（比裁剪坐标短、也不会卡在角落）；
- **步数上限 = 64 × 10**：随机游走会反复踩到已感染的方块，所以「走 64 步」凑不满 64 块；
  上限只用于防止在周围几乎没方块可感染的角落空转（那种情况就少于 64 块）；
- 结算仍然是**都掉落**：

  ```java
  level.removeBlock(pos, false);                                              // 方块本体消失
  Block.dropResources(original, level, pos);                                  // 原本掉落物
  Block.popResource(level, pos, new ItemStack(ModItems.BLACK_BLOCK));         // 再加一个黑方块
  ```

- 状态存在 `Map<UUID, Infection>` 里（每个玩家一份，含起点、当前游走位置、已感染表），
  由 `ServerTickEvents.END_SERVER_TICK` 每 tick 检查 `player.isShiftKeyDown()`，
  **一旦松开就结算**；玩家退出、再次施法时也会先结算旧的。

> 客户端这一侧要在 `ClientPreAttackCallback` 里**先判潜行**：潜行时发 `corrupt` 包并 `return true` 拦截原版左键，
> 否则短柱会和感染一起放出去。

## 6. 发出行为的玩家免疫三件事

需求：「发出行为的玩家**不受黑洞吸引**，**不被爆炸标记**，也**不受弹幕击退**」。三处各加一个排除：

| 免疫 | 位置 | 做法 |
| --- | --- | --- |
| 不被黑洞吸 | 相撞结算 | `victims.removeIf(v -> v == this.owner \|\| v == other.owner)` —— 黑洞的捕获名单就是这个表 |
| 不被爆炸打标记 | 同上 | 标记、生轴、禁锢、弹幕触发**都在同一个循环**里遍历 `victims`，所以过滤一次就全免了 |
| 不受弹幕击退（也不被弹幕打） | `PillarBarrageEntity.spawnPillar` | 弹幕柱 `setOwner(owner)`：`pushTargets` 里 `isOwner && !knocksBackOwner` 会跳过发射者 |

> 注意：**爆炸伤害本身照旧会落到在场所有人身上**（需求只点名了「不被标记」）。要连伤害一起免掉，
> 得在爆炸前把 owner 挪出影响范围，或者改用不伤及 owner 的爆炸口径。

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 爆炸还是把人炸飞 | 没包 `Explosions`，或还原范围圈得太小（爆炸实际影响半径大于 power） |
| 目标被震得贴着墙抖 | 冲突判定只看「受伤期间」；没受伤时两股推力本来就该叠加 |
| 弹幕把生物推到天边 | 忘了给弹幕柱设 `knockbackTarget`（发射者），或玩家自己站太远 |
| 潜行右键放出了长柱 | `fireLongPillar` 里没先判 `isShiftKeyDown()` |
| 潜行左键又开火又感染 | 客户端没在潜行时 `return true` 拦截原版左键 |
| 感染后松手什么都不掉 | `ServerTickEvents` 没注册，或玩家退出时没结算 |
| 一次感染把地图刷成黑方块 | 没在退出/松手时 `finish`，地图会留着感染结果 |

## 8. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 弹幕速度 / 密度 / 时长 / 齐射数 | `BARRAGE_SPEED_PER_TICK`（12.0）/ `INTERVAL_TICKS`（3）/ `DURATION_TICKS`（800 = 40 秒）/ `FINALE_PILLARS`（28） |
| 弹幕伤害与击退（推力+距离） | `PillarSpec.damageScale`（0.5）/ `knockbackScale`（0.5）；收尾爆炸看 `FINALE_EXPLOSION_POWER`（6.0，未改） |
| 结尾回到哪里 | `PillarBarrageEntity.triggerPoint`（第一次 tick 记下的目标位置）+ `HOLD_HEIGHT` |
| 击退冲突清零的条件 | `pushTargets` 里 `target.hurtTime > 0` 那一条 |
| 冲刺柱长度 / 速度 / 后撤距离 | `DASH_LENGTH`（60）/ `DASH_SPEED_PER_TICK`（3.0）/ `ModNetworking.DASH_BACK_OFFSET`（3） |
| 冲刺前清场半径 | `ModNetworking.DASH_CLEAR_RADIUS`（3） |
| 感染射线 / 半径 / 蔓延步数 | `ModNetworking.CORRUPT_REACH`（10）/ `CORRUPT_RADIUS`（3）/ `CORRUPT_STEPS`（60） |

## 9. 小结

- 「爆炸不提供速度」不必重写爆炸：**记下速度、炸、还原**，三行就够，连伤害与破坏都保持原版口径。
- 跨实体的「同一 tick」判定，用**游戏刻 + 一张每 tick 清空的短表**最省事，不用在实体上加同步字段。
- 「击退落点」这种意图，既可以用**吸附某个实体**实现（每 tick 推向它），也可以只在**结尾**把目标按回一个记录点
  —— 后者更省事、也更符合「过程随便飞、结果要可控」的设计。
- 潜行键是很好的「第二技能层」：同一个按键 + 潜行 = 另一套行为，各自占一个冷却槽即可。
- 会改写世界的临时状态（感染）一定要有**明确的结算时机**（松手、退出），否则会留下烂摊子。
- 「谁不受影响」这件事，在最上游把名单过滤一次（`victims.removeIf(...)`）比在下游每个功能里各判一遍清爽得多。
