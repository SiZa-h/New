# 06. 自定义伤害类型与「击退到终点」

本篇给黑方块柱加上命中结算：

> 柱子**碰到的生物**会吃到**无视护甲的伤害**，并被沿伸长方向一路**击退到尖端终点**。

这条改动看起来只是「打一下 + 推一下」，但它牵出 26.3 里三块容易踩坑的知识：

1. **伤害类型是数据包内容**，不是代码里的枚举；
2. **「无视护甲」由标签决定**，而不是某个方法参数；
3. **服务端改了速度，客户端不一定看得见**，要显式同步。

## 1. 伤害类型（DamageType）在 26.3 是什么

从 1.20 起，伤害类型变成了**动态注册表**，内容放在数据包里：

```
data/<命名空间>/damage_type/<名字>.json
```

代码只拿一个 `ResourceKey<DamageType>` 去引用它。本项目新增：

`src/main/resources/data/new-mod/damage_type/black_block_pillar.json`

```json
{
  "exhaustion": 0.0,
  "message_id": "blackBlockPillar",
  "scaling": "when_caused_by_living_non_player"
}
```

| 字段 | 作用 |
| --- | --- |
| `message_id` | 死亡消息的语言键后缀：`death.attack.<message_id>`（有凶手时是 `death.attack.<message_id>.player`） |
| `exhaustion` | 受到该伤害时增加的饥饿消耗 |
| `scaling` | 是否随难度缩放：`never` / `when_caused_by_living_non_player` / `always` |

对应的死亡消息（两个语言文件都要加）：

```json
"death.attack.blackBlockPillar": "%1$s 被黑方块柱碾碎",
"death.attack.blackBlockPillar.player": "%1$s 在逃离 %2$s 时被黑方块柱碾碎"
```

### 1.1 代码侧只需要一个 Key

```java
public final class ModDamageTypes {
	public static final ResourceKey<DamageType> BLACK_BLOCK_PILLAR = ResourceKey.create(
			Registries.DAMAGE_TYPE,
			NewMod.id("black_block_pillar")
	);

	private ModDamageTypes() {
	}
}
```

> 不用注册！动态注册表的内容由数据包提供，代码里写 `ResourceKey` 即可。写错名字的后果是运行时
> `getOrThrow` 抛异常（`NoSuchElementException`），所以**JSON 文件名、命名空间、Key 三者必须一致**。

## 2. 「无视护甲」靠标签

伤害类型 JSON 里**没有** `bypasses_armor` 这个字段——它是**伤害类型标签**：

`src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json`

```json
{
  "replace": false,
  "values": [
    "new-mod:black_block_pillar"
  ]
}
```

要点：

- 路径写在 **`data/minecraft/...`**（扩展原版标签），而不是自己的命名空间；`replace: false` 表示**追加**而不是覆盖，原版那 19 个条目仍然有效。
- 原版的 `magic`、`generic`、`fall` 等都在这个标签里，所以「魔法伤害无视护甲」并不是魔法特殊，而是它挂了这个标签。
- **反过来的用法同样重要**：本模组的第二把武器「黑曜石方」用的是另一个伤害类型 `new-mod:obsidian_cube`，它**故意不写进这个标签**，于是它的伤害照常被护甲减免——「不能无视护甲」就是这么实现的（一行标签的有无）。
- 同类标签还有 `bypasses_invulnerability`、`bypasses_effects`、`is_projectile`、`no_knockback` 等，都可以用同样的方式追加。

### 2.1 构造伤害源

```java
DamageSource source = level.damageSources().source(
		ModDamageTypes.BLACK_BLOCK_PILLAR,   // 伤害类型 Key
		this,                                // 直接来源实体（柱子）
		this.owner);                         // 造成者（发射这颗星的玩家，可为 null）
```

三个参数的含义：**类型 → 直接实体 → 归因实体**。归因实体决定死亡消息里的「凶手」、生物仇恨与统计归属。
把发射者记下来是在生成实体时做的：

```java
BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.typeFor(weapon.kind()), level);
pillar.setOwner(player);
```

## 3. 触碰检测：先用 AABB 粗筛，再做点到线段精筛

柱子是一个**线段**（后端 → 尖端，最长 30 格）。斜着的柱子如果用包围盒直接判定，会把线段旁边的生物也一起打中，所以分两步：

```java
Vec3 origin = this.position();
Vec3 basePos = origin.add(direction.scale(base));
Vec3 tipPos = origin.add(direction.scale(tip));

AABB search = new AABB(basePos, tipPos).inflate(1.0D);            // ① 粗筛
for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search)) {
	if (target == this.owner || !target.isAlive()) continue;

	Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
	Vec3 closest = closestPointOnSegment(basePos, tipPos, center);  // ② 精筛

	if (!target.getBoundingBox().inflate(0.5D).contains(closest)) continue;
	...
}
```

`closestPointOnSegment` 就是标准的「点到线段最近点」投影：

```java
private static Vec3 closestPointOnSegment(Vec3 from, Vec3 to, Vec3 point) {
	Vec3 segment = to.subtract(from);
	double lengthSqr = segment.lengthSqr();
	if (lengthSqr < 1.0E-8D) return from;                          // 退化成一个点

	double t = Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
	return from.add(segment.scale(t));
}
```

> `new AABB(basePos, tipPos)` 在柱子完全竖直时宽高为 0，`inflate(1.0)` 顺便解决了这个退化情况。

## 4. 伤害只结算一次

柱子每 tick 都在动，很容易变成「每 tick 打一下」。这里用两个手段：

```java
// damage 与是否无视护甲都取自武器种类（PillarKind）
if (!this.damagedTargets.contains(target.getUUID())
		&& target.hurtServer(level, source, kind.touchDamage())) {
	this.damagedTargets.add(target.getUUID());
}
```

- `hurtServer(...)` **返回 boolean**：目标处于无敌帧、免疫该伤害等原因会让它返回 `false`。**只有真的打中了才记账**，这样「第一下因为无敌帧没打进去」的情况会在下一 tick 自动重试。
- 记账用 `Set<UUID>`，因此一根柱子对同一个目标最多造成一次伤害。

## 5. 击退到尖端终点

需求是「向伸长方向的击退，击退至尖端终点」。注意两个实现选项：

| 方案 | 结果 |
| --- | --- |
| 一次性冲量 `setDeltaMovement(dir * 大数)` | 受空气阻力与重力影响，落点不可控，基本到不了 30 格外 |
| **每 tick 重设速度（本项目）** | 目标被柱子一路「推着走」，稳定到达终点 |

```java
double knockback = this.getSpeed();                                    // = 该类型的伸长速度
double travelled = target.position().subtract(origin).dot(direction);  // 沿线已前进的距离

if (travelled < this.getMaxLength()) {                                 // 还没到前端终点才继续推
	target.setDeltaMovement(
			direction.x * knockback,
			direction.y * knockback + GRAVITY_COMPENSATION,                // 抵消重力，别让目标一路下沉
			direction.z * knockback);
	target.syncVelocity = true;
}
```

- **点乘投影**：`(target - origin) · direction` 就是目标沿柱子方向走了多远，用它判断「是否已到前端终点（`getMaxLength()`）」。
- **击退速度 = 该类型的伸长速度**（`this.getSpeed()`，常规 1.5 格/tick、长黑方块柱 3.0）：目标是被柱子「推着走」而不是被弹飞；每 tick 推一次（20 Hz 是实体位移的频率上限），单次位移就等于伸长速度——这就是「调高频率、调低单次距离」的正确做法，详见 [learn/07](07-reverse-extension-and-recoil.md)。
- **`GRAVITY_COMPENSATION = 0.08`** 正好等于原版重力加速度，水平发射时目标高度基本不变；柱子朝下发射时它只是稍微削弱下压速度，不会把目标往反方向拽。
- **击退倍率**：单次位移 = `getSpeed() × PillarSpec.knockbackScale`。弹幕柱的倍率是 **0.5**
  （需求「弹幕击退效果减半」），其余类型是 `1.0`。
- **脱离柱子时的收尾**：上一 tick 还在柱子里、这一 tick 已经不在了的生物，速度再 **×0.01**
  （`BlackBlockPillarEntity.RELEASE_VELOCITY_SCALE`）——于是它是「被推着走、离开时几乎停住」，
  不会带着满速飞出去。发射者自己不算（他有专门的后坐收尾 ×0.1，见 [learn/07](07-reverse-extension-and-recoil.md)）。

  ```java
  private void updateDetachedTargets() {
      for (Entity previous : this.touchedTargets) {
          if (this.touchedThisTick.contains(previous) || previous.isRemoved() || previous == this.owner) {
              continue;
          }

          previous.setDeltaMovement(previous.getDeltaMovement().scale(RELEASE_VELOCITY_SCALE));
          previous.syncVelocity = true;
      }

      this.touchedTargets.clear();
      this.touchedTargets.addAll(this.touchedThisTick);
  }
  ```

  实现要点：`pushTargets` 每 tick **开头**清空 `touchedThisTick`、碰到谁就把谁加进去，
  于是「没被加进去」天然等于「已脱离」——连「柱子这一 tick 提前 return（还没长出来 / 已经收完）」也算脱离。
- **两种柱子同时击退 → 速度清零**：受伤期间（`hurtTime > 0`）若本 tick 已有**另一种**黑方块实体在推它，
  这一 tick 就把速度清零、不再叠加（`effect/PushConflicts`，见 [learn/18](18-explosion-knockback-barrage-and-sneak-skills.md)）。
- **爆炸不给速度**：黑方块实体的爆炸统一走 `effect/Explosions`——先记速度、炸完还原，
  于是只有伤害与破坏，没有那「一脚」（见 [learn/18](18-explosion-knockback-barrage-and-sneak-skills.md)）。

### 5.1 为什么必须写 `syncVelocity`

服务端改速度不会自动同步。原版 `Entity#markHurt()` 做的事就是：

```java
protected void markHurt() {
	this.syncVelocity = true;   // 26.3 实现
}
```

而 `markHurt()` 是 **protected**，我们没法对别的实体调用，所以直接写公开字段：

```java
target.syncVelocity = true;   // 服务端会在下一次 sendChanges 时发速度包（含给自己）
```

`ServerEntity#sendChanges()` 里对 `syncVelocity` 的处理是 `sendToTrackingPlayersAndSelf(...)`——**注意含自己**，否则发射者在自己的屏幕上会看到怪物「瞬移」而不是被推走。

> 相关字段还有 `needsSync`（还会补发位置更新）。想让客户端更平滑，可以两者都设。

## 6. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 伤害更高 / 更低 | `PillarKind` 里各武器的 `touchDamage`（黑方块之星 6.0 无视护甲 / 黑曜石方 4.0 受护甲减免） |
| 变成「正常受护甲减免」 | 把 `bypasses_armor` 标签里的 `new-mod:black_block_pillar` 删掉 |
| 击退更快 / 更慢 | `ModEntities.PillarSpec.speedPerTick`（常规 1.5 = 30 格/秒，长黑方块柱 3.0 = 60 格/秒） |
| 某一类的击退更强 / 更弱 | `ModEntities.PillarSpec.knockbackScale`（弹幕柱 0.5） |
| 脱离柱子时的刹车力度 | `BlackBlockPillarEntity.RELEASE_VELOCITY_SCALE`（默认 0.01） |
| 击退距离 | 与最大长度联动：目标是走到「前端终点」即停（`getMaxLength()`） |
| 推得更高 | `GRAVITY_COMPENSATION` |
| 允许打自己 | 删掉 `target == this.owner` 判断 |
| 每 tick 都掉血 | 去掉 `damagedTargets` 记账（建议同时调小对应武器的 `touchDamage`） |
| 改成别的死亡消息 | 数据包 `message_id` + 语言键 `death.attack.<message_id>` |

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 启动/受击时报 `NoSuchElementException`（找不到 damage_type） | JSON 路径或名字与 `ResourceKey` 不一致；注意目录是 `damage_type`（单数） |
| 伤害被护甲挡住了 | 忘了把伤害类型加进 `bypasses_armor` 标签，或标签文件写在了自己的命名空间下（应写 `data/minecraft/tags/damage_type/...`） |
| 覆盖掉了原版标签 | 标签文件写了 `"replace": true` |
| 死亡消息显示成 `death.attack.blackBlockPillar` | 语言键没加，或 `message_id` 与语言键对不上 |
| 服务端能看到击退，客户端看不到 | 忘了 `syncVelocity = true` |
| 生物离开柱子后带着满速飞出去 | 没做「脱离收尾」（`updateDetachedTargets`），或它在脱离那一 tick 被别的逻辑跳过了 |
| 一次柱子打掉半管血 | 没做「每根柱子只结算一次」的记账 |
| 斜着发射时旁边的生物也被打中 | 只用 AABB 判定，没做点到线段精筛 |
| 击退把怪物推过头摔下悬崖 | 这是设计使然——终点就是柱子前端，可以调小该类型的 `speedPerTick` 或改成一次性冲量 |

## 8. 验证方式

```bash
./gradlew build        # 编译
./gradlew runDatagen   # 无界面启动：会加载数据包，能暴露 damage_type JSON 的错误
./gradlew runClient    # 进游戏：朝一群怪开火，观察掉血、被推走、以及死亡消息
```

想单独验证「无视护甲」，可以给僵尸穿全套铁甲（`/item replace entity @e[...] armor.head with iron_helmet` 等）后对比普通剑与柱子的掉血差异；也可以用 `/damage` 命令直接测伤害类型：`/damage @e 6 new-mod:black_block_pillar`。

## 9. 小结

- 伤害类型是**数据包注册表**：代码只写 `ResourceKey`，性质（无视护甲等）由**标签**决定，标签要写进 `data/minecraft/tags/damage_type/` 且 `replace: false`。
- 死亡消息键 = `death.attack.<message_id>`（+ `.player`）。
- 线段型攻击的命中检测要「AABB 粗筛 + 点到线段精筛」，并用 `hurtServer` 的返回值判断是否真的打中。
- 「击退到某处」用**每 tick 重设速度**比一次性冲量可靠，配合点乘投影判断何时到达终点。
- 服务端改速度必须 `syncVelocity = true`（`markHurt()` 的等价写法），否则客户端看不到击退。
