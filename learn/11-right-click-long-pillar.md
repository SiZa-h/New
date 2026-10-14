# 11. 右键技能：生成即满长的「长黑方块柱」

黑方块之星现在有两个技能：

| 输入 | 柱子 | 行为 |
| --- | --- | --- |
| **左键** | 短黑方块柱 | 身前 1 格生成，朝视线方向以 30 格/秒长到 30 格，1 秒后消散 |
| **右键** | **长黑方块柱** | **与短柱同一条生成逻辑**（同起点、同朝向），但生成即满长 60 格；随后黑方块与功能由**尖端向底端**以 **60 格/秒**填充，填满后再由**尖端向底端**同速消散 |

右键这根柱子还更凶：

- 对触碰的生物造成**无视护甲**的伤害，并**向着玩家**（朝底端）击退；
- **连玩家一起击退**（包括发射者自己），但**不伤害发射者**；
- **沿途瞬间破坏路径上的方块**（**连基岩也一起破**：长柱是 2 号行为，按需求只有 1 号行为保留基岩保护）。

内容对应 [learn/10](10-entity-types-carry-the-differences.md) 里预留的 `long_black_block_pillar`——这次正式启用了它。

## 1. 右键事件：不需要网络包

左键技能是自己发的包（[learn/05](05-left-click-ability-packet-and-custom-entity.md)），因为左键必须先「拦下原版攻击」。
右键不用这么麻烦：Fabric 的交互事件**在服务端也会触发**。

查看 Fabric API 源码可以看到 `UseItemCallback` 有两处注入：

```java
// fabric-events-interaction-v0 → Mixin(MultiPlayerGameMode.class)   —— 客户端
@Inject(... method = "useItem" ...) public void interactItem(...) { UseItemCallback.EVENT.invoker()... }

// fabric-events-interaction-v0 → Mixin(ServerPlayerGameMode.class)  —— 服务端
@Inject(... method = "useItem" ...) public void interactItem(...) { UseItemCallback.EVENT.invoker()... }
```

也就是说：客户端右键 → 原版照常把「使用物品」包发给服务端 → **服务端再触发一次同一个事件**。
所以只要「只在服务端处理」，就能一次点击只生成一根柱子，完全不需要自定义包：

```java
public static void register() {
	UseItemCallback.EVENT.register((player, level, hand) -> fireLongPillar(player, level, hand));
	UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> fireLongPillar(player, level, hand));
}

private static InteractionResult fireLongPillar(Player player, Level level, InteractionHand hand) {
	if (level.isClientSide() || hand != InteractionHand.MAIN_HAND
			|| !(player instanceof ServerPlayer serverPlayer)) {
		return InteractionResult.PASS;      // 客户端那次直接放行，让原版把包发给服务端
	}
	...
	return InteractionResult.SUCCESS;       // 服务端这次：取消原版处理
}
```

三个要点：

1. **`isClientSide()` 守卫是必须的**。事件两端都触发，不加守卫就会「客户端算一次、服务端再生一次」。
2. **两个事件都要注册**。右键空气/物品走 `UseItemCallback`，右键**方块**走 `UseBlockCallback`
   （方块交互失败时原版才会回退到 `useItem`，而我们在 `UseBlockCallback` 里返回 `SUCCESS` 就把回退掐掉了，
   所以不会重复生成）。
3. **返回 `SUCCESS` 会取消原版处理**（不掉进方块交互、不触发物品使用）；冷却中也要返回 `SUCCESS`，
   这样右键在冷却里「什么都不做」而不是下传到别的逻辑。

## 2. 生成逻辑：与短柱**完全相同**，生成即满长

```java
// 与左键短柱完全同一条生成逻辑：中轴 = 玩家视线方向轴（含俯仰），起点 = 短柱起点
Vec3 direction = serverPlayer.getViewVector(1.0F).normalize();
Vec3 start = shortPillarOrigin(serverPlayer, direction);

BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.LONG_BLACK_BLOCK_PILLAR, serverLevel);
pillar.setOwner(serverPlayer);
applyRotation(pillar, direction);                                // 与左键共用同一套旋转换算
pillar.setPos(start.x, start.y, start.z);
```

区别只在**实体类型**（`PillarSpec` 的最后一位 `fillsFromTip = true`）。生命周期的三个阶段：

| 阶段 | 长柱内部做什么 | 玩家看到什么 |
| --- | --- | --- |
| **生成瞬间** | 几何直接拉满：`front = 60`、`base = 0`、`fill = 60` | **什么都没有**：实体是「60 格长、透明、没有任何功能」的 |
| **之后 20 tick** | `fill` 以 3 格/tick（60 格/秒）由**尖端**向**底端**推进，推进到哪就破坏到哪 | 黑方块从**尖端**开始、一段段朝**底端**出现 |
| **填满后 20 tick** | `growing = false` → **`front` 往底端退**（`setGeometry(floor + length, base)`） | 从**尖端**开始消散、一路退到底端（同样 60 格/秒） |

关键是「**有效段**」这个概念：

```java
/** 有效后端距离：填充型柱子只有被填过的那一段才算数。 */
public double getEffectBaseDistance() {
	return this.fillsFromTip() ? Math.max(this.getBaseDistance(), this.getFillDistance()) : this.getBaseDistance();
}
```

渲染、触碰伤害与击退、路径破坏、与另一根柱子的相撞**全部**读这个有效段 ——
所以「还没填到的地方」既看不见也打不到，这正是需求里「透明、没有任何功能」的含义。

两个附带结论：

- 两根柱子的中轴都是玩家视线方向轴，所以它们天然共线；
- 长柱的朝向就是**视线方向**，但它的功能是「由尖端向底端推进」的，所以**击退方向取反 → 向着玩家**
  （见 §5）；消散也是同一方向（尖端 → 底端）。

## 3. 类型差异全部落在 `PillarSpec`

`long_black_block_pillar` 的记录比其他类型多了四项：

```java
public record PillarSpec(
		PillarKind kind,
		double maxLength,
		double speedPerTick,
		boolean dissipates,
		boolean destroysPath,        // 沿途瞬间破坏方块
		boolean knocksBackOwner,     // 发射者也能被击退
		boolean explodesOnCollision, // 与别的柱子相撞 → 停下 + 爆炸 + 黑洞
		boolean fillsFromTip         // 生成即满长，再由尖端向底端填充渲染与功能
) {
}
```

- **速度 3.0 格/tick = 60 格/秒**：填充、消散、击退全部用 `getSpeed()`，所以三者自动同步；
- 60 格 ÷ 60 格/秒 = **1 秒填满**，随后 1 秒消散，与需求的节奏一致。

## 4. 沿途破坏方块

填充型柱子是「填到哪、破坏到哪」——每 tick 只清掉**新填进来**的那一小段：

```java
double fill = this.getFillDistance();
double nextFill = Math.max(0.0D, fill - this.getSpeed());

if (this.destroysPath()) {
	this.destroyPathBlocks(level, nextFill, fill);   // 新填的那一段
}
```

普通柱子则是「前端每走一步先清路」：

```java
if (!this.frontBlocked) {
	if (this.destroysPath()) {
		this.destroyPathBlocks(level, front, front + need);   // 路径破坏
	}

	double limit = this.limitForward(level, front, front + need);   // 之后照常做射线（只可能被基岩挡住）
	...
}
```

```java
private void destroyPathBlocks(ServerLevel level, double from, double to) {
	Vec3 direction = this.getBeamDirection();
	Vec3 origin = this.position();
	double distance = Math.abs(to - from);
	int steps = Math.max(1, (int) Math.ceil(distance / PATH_SAMPLE_STEP));   // 0.25 格采样

	for (int i = 0; i <= steps; i++) {
		double at = from + (to - from) * i / steps;
		BlockPos pos = BlockPos.containing(origin.add(direction.scale(at)));
		BlockState state = level.getBlockState(pos);

		if (state.getCollisionShape(level, pos).isEmpty()) continue;   // 空气、草、花、水
		if (state.getDestroySpeed(level, pos) < 0.0F) continue;        // 基岩等不可破坏方块

		level.destroyBlock(pos, true);                                 // 正常掉落物品
	}
}
```

- **采样而不是整段遍历**：3 格/tick 的推进用 0.25 格步长采样 12 次就够，既不会漏格也不浪费；
- **破坏范围就是柱子 1×1 截面的扫过体积**（采样点所在方块）；
- **基岩也挡不住**：长柱的 `breaksUnbreakable` 是 `true`（1 号行为才是 `false`），
  并且设置页里的「基岩」开关默认开着，所以它会把基岩一并清掉；想恢复「基岩挡得住」，
  在设置页把基岩改成「无视」（或把该类型这一位改回 `false`）即可；
- 破坏的方块**正常掉落物品**（`destroyBlock(pos, true)`），与两端破坏的行为一致。

## 5. 连玩家一起击退

`PillarSpec.knocksBackOwner` 打开后，目标筛选里不再直接跳过发射者：

```java
boolean isOwner = target == this.owner;

// 常规类型完全不碰发射者；长黑方块柱会把他一起推走
if (isOwner && !knockOwner) continue;

// 伤害：发射者永远不受伤（他只可能被击退）
if (!isOwner && !this.damagedTargets.contains(target.getUUID())
		&& target.hurtServer(level, source, kind.touchDamage())) {
	this.damagedTargets.add(target.getUUID());
}

// 击退：包括发射者在内的所有被触碰目标
target.setDeltaMovement(direction.x * knockback, ..., direction.z * knockback);
target.syncVelocity = true;
```

- 其他玩家本来就会被击退（常规类型也只排除「发射者」这一个实体），所以「可击退触碰玩家」这条需求
  实际要表达的是**发射者自己也会被这根柱子推走**；
- **不伤害发射者**：自己放的大招把自己打死体验太差，因此伤害侧始终排除 owner；
- 击退方向是**向着玩家**的：长柱的功能由尖端向底端推进，所以代码里把它「推进方向」的反向当作击退方向 ——

  ```java
  /** 常规柱子朝尖端推；长黑方块柱由尖端向底端推进，所以朝底端（向着玩家）推。 */
  private Vec3 knockbackDirection() {
  	return this.fillsFromTip() ? this.getBeamDirection().scale(-1.0D) : this.getBeamDirection();
  }
  ```

- 填充型柱子的有效段是「已经填出来的那一部分」，所以击退与伤害也是**随着填充推进**才生效的。

## 6. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 生成位置 | 与左键短柱共用 `ModNetworking.shortPillarOrigin`（视线轴上玩家眼前 1 格） |
| 填充速度 | `ModEntities.LONG_SPEED_PER_TICK`（3.0 = 60 格/秒） |
| 长度 | `ModEntities.LONG_LENGTH`（60 格） |
| 右键冷却 | `ModNetworking.LONG_FIRE_COOLDOWN_TICKS`（默认 20 tick，与左键**独立计算**，见 [learn/12](12-independent-skill-cooldowns.md)） |
| 沿途破坏是否掉落 | 已经会掉落（`destroyBlock(pos, true)`） |
| 是否连基岩一起破坏 | `PillarSpec.breaksUnbreakable`（长柱为 `true`，1 号行为为 `false`） |
| 右键也伤害发射者 | 在 `pushTargets` 里去掉 `!isOwner` 条件 |
| 让黑曜石方也有右键技能 | `fireLongPillar` 里的 `weapon.kind() != PillarKind.BLACK_BLOCK` 判断去掉 |
| 关掉「尖端向底端填充」 | 把 `PillarSpec` 最后一位置回 `false`（回到常规的「前端伸长」模型） |
| 击退方向 | `BlackBlockPillarEntity.knockbackDirection()`（填充型取反向 = 朝底端 = 向着玩家） |
| 消散方向 | 填充型在 `updateGeometry` 的消散分支里让 `front` 往底端退；常规类型是 `base` 往尖端收 |

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 一次右键生成两根柱子 | 没做 `level.isClientSide()` 守卫（事件两端都会触发） |
| 右键方块时技能不触发 | 只注册了 `UseItemCallback`；右键方块走的是 `UseBlockCallback` |
| 右键后意外触发了方块交互 | 处理完没返回 `InteractionResult.SUCCESS` |
| 柱子出现在玩家身后/朝反了 | 朝向应为**视线方向本身**（填充型长柱不再取反） |
| 客户端看不到 60 格外的柱子 | 类型的 `clientTrackingRange` 太小（当前 12 区块） |
| 柱子被普通方块卡住 | 该类型没打开 `destroysPath`，或采样步长大于 1 格导致漏格 |
| 把自己打死了 | 伤害侧没有排除 owner（击退可以给，伤害不要给） |
| 长柱一出现就是整根 60 格 | 没有读「有效段」：渲染与结算都该用 `getEffectBaseDistance()` |
| 长柱一开始就能打人 | 同上：填充前沿还是 60（有效段为空），功能应当随填充才生效 |
| 长柱把目标推向远处 | 击退方向没取反（应当用 `knockbackDirection()`，朝底端＝向着玩家） |
| 消散方向反了 | 填充型的消散是**前端往回退**（`front` 减小），不是「后端前收」 |
| 相撞后长柱还在继续填 | `stopGrowing()` 里没有把 `sweepFinished` 一起置位 |

## 8. 验证方式

```bash
./gradlew build
./gradlew runDatagen   # 无界面启动，验证事件注册与实体注册
./gradlew runClient    # 进游戏：手持黑方块之星朝一片树林右键
```

进游戏观察点：

1. 面朝树林按右键 → **先什么都不出现**（实体已是满长但透明、无功能）；
2. 紧接着黑方块从 **60 格外的尖端**开始出现，一段段朝你这边填过来，沿途树木被逐格破坏；
3. 填充扫到你身上时你会被**推离原位、朝着底端（你的身后／玩家一侧）**飞出去（但**不掉血**）；
4. 站在路径上的怪会被无视护甲打伤并被**推向玩家一侧**；
5. 填满后**从尖端开始**消散、一路退到底端；此时若左键短柱还在，两者相撞 →
   **双方都立刻停止伸长/填充** + 爆炸 + 黑洞（长柱从当前的填充位置往尖端方向消散）。

## 9. 小结

- **右键不需要自定义包**：Fabric 的 UseItem / UseBlock 事件在服务端也会触发，只要加 `isClientSide()` 守卫、
  并且两个事件都注册（覆盖右键方块的情况）、处理时返回 `SUCCESS`。
- 长柱与短柱**共用同一条生成逻辑**（同起点、同朝向）；差别只在实体类型上的一串 `PillarSpec` 开关。
- 「生成即满长 + 尖端向底端填充」把**实体几何**与**渲染/功能**解耦：实体立刻是 60 格，
  而黑方块与伤害随填充前沿推进 —— 靠一个 `getEffectBaseDistance()` 让渲染、命中、相撞、破坏统一读「有效段」。
- 沿途破坏用「采样 + 可选的 `getDestroySpeed < 0` 跳过」实现：既保证不漏格，
  又能用 `breaksUnbreakable` 一位开关决定「要不要把基岩也一起清掉」。
- **方向要从「功能在往哪边走」推**：长柱的几何朝视线方向，但功能由尖端向底端推进，
  于是**击退**（取反 → 向着玩家）与**消散**（前端回退）都跟着「推进方向」而不是实体朝向。
- 相撞时双方都由对方调用 `stopGrowing()`：常规柱子停的是「伸长」，填充型柱子停的是「填充」
  （`sweepFinished` 一起置位），随后各自按自己的方向消散。
