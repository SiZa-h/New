# 30. 让变体彻底独立：把"黑方块之星的特征"从黑曜石柱里摘出来

需求一句话：

> 黑曜石方的柱的，**独立创建，不吃黑方块柱特点**。

这一篇讲"**特征泄漏**"这种 bug：两个变体共用一套实体代码时，某个变体的专属特性会悄悄作用到另一个身上。
它不会编译报错，也不会崩，只会让"独立创建"的武器变得不是自己。

## 1. 症状：共用几何，却连被动一起继承了

`OBSIDIAN_PILLAR` 与 `SHORT_BLACK_BLOCK_PILLAR` 用的是**同一个实体类**（`BlackBlockPillarEntity`），
只有 `PillarSpec` 不同。当时的外观、伤害、伤害类型、破甲都已经按变体分开了：

```java
// ModEntities.PillarSpec 表
new PillarSpec(PillarKind.OBSIDIAN, SHORT_LENGTH, SPEED_PER_TICK,
		true, false, false, GROUP_NONE, Marks.NONE, false, false, 1.0D, 1.0D)
```

但伤害结算里有两行是**无条件**的：

```java
float damage = PillarWeaponItem.applyPillarPassive(target, this.owner, kind.touchDamage());
//                                  ↑ 额外 +3 伤害，并挂上黑暗效果（黑星被动）
if (target.hurtServer(level, source, damage)) {
	Marks.applyAndSync(target, Marks.BLACK);      // ↑ 黑标记 B（黑星专属）
}
```

于是黑曜石柱：

- 实际伤害是 **4 + 3 = 7**（表里写的是 4）；
- 会留下**黑标记 B**——而 B 是"消散"的一半，等于黑曜石武器也能参与黑星的组合技；
- 会挂**黑暗**，那是黑星的视觉签名。

这正是"吃了黑方块柱特点"。

## 2. 修法：把"专属"变成一个有名字的判据

不要在调用点散落 `kind == PillarKind.BLACK_BLOCK`，而是让变体表自己回答"我是不是黑星"：

```java
// PillarKind
/**
 * Whether this kind carries the **black block star's** signature traits: the +3 passive with the darkness
 * effect, and the black mark **B**.
 *
 * <p>The obsidian cube's pillar is created independently: it only shares the growing-pillar geometry.
 */
public boolean isBlackBlockStar() {
	return this == BLACK_BLOCK;
}
```

然后每一处"黑星专属"都过这道门：

```java
// BlackBlockPillarEntity#pushTargets
boolean blackStar = kind.isBlackBlockStar();
float touchDamage = kind.touchDamage() * (float) this.damageScale();
float damage = blackStar ? PillarWeaponItem.applyPillarPassive(target, this.owner, touchDamage) : touchDamage;

if (target.hurtServer(level, source, damage)) {
	this.damagedTargets.add(target.getUUID());

	if (blackStar) {                       // 只有黑星留 B 标记
		Marks.applyAndSync(target, Marks.BLACK);
	}
}
```

近战那条路（`hurtEnemy` 挂黑暗）同样要门控——它虽然很少被走到（两颗星的左键都是技能键），
但"很少走到"不等于"不该分开"：

```java
// PillarWeaponItem#hurtEnemy
if (this.kind.isBlackBlockStar() && !attacker.level().isClientSide()) {
	target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS), attacker);
}
```

## 3. 怎么找全所有泄漏点（可复用的审计方法）

1. **列"专属特性清单"**：对黑星来说就是「+3 伤害」「黑暗效果」「黑标记 B」；
2. **按特性名全仓搜索**，而不是凭记忆：`Marks.BLACK`、`applyPillarPassive`、`DARKNESS`；
3. 对每个命中点问一句：**这里有没有变体判断？** 没有的就是泄漏点；
4. 修的时候**只加门控、不改几何**——共用的生长/破坏/击退逻辑一行都不用动。

本次审计结果（3 个命中点）：

| 位置 | 是否泄漏 | 处理 |
| --- | --- | --- |
| `BlackBlockPillarEntity#pushTargets` 的 +3/黑暗 | 是 | `isBlackBlockStar()` 门控 |
| `BlackBlockPillarEntity#pushTargets` 的 B 标记 | 是 | 同上 |
| `PillarWeaponItem#hurtEnemy` 的黑暗 | 是（近战路径） | 同上 |

## 4. 顺手确认：黑曜石柱现在"独立"在哪

| 维度 | 黑曜石柱 | 是否独立 |
| --- | --- | --- |
| 实体类型 | `obsidian_pillar`（自己的 `PillarSpec` 条目） | ✅ |
| 外观 | 黑曜石方块 | ✅ |
| 伤害 | 4.0（不再 +3） | ✅ 本次修 |
| 破甲 | 不破甲（`obsidian_cube` 不在 `bypasses_armor`） | ✅ |
| 黑标记 B | 不再施加 | ✅ 本次修 |
| 黑暗效果 | 不再施加 | ✅ 本次修 |
| 生长 / 反向伸长 / 两端破坏 / 击退 | 与黑星共用代码 | 共用（这是"同一类柱子"，不是特征泄漏） |

## 5. 常见坑

| 现象 | 原因 |
| --- | --- |
| 变体伤害比表里写的高 | 共用的 `applyPillarPassive` 在无门控地加 +3 |
| 变体也能触发另一把武器的组合技 | 标记无条件施加（B 是消散的一半） |
| 改了一个变体，另一个也跟着变 | 特性写在共用方法里，而不是写在变体声明里 |
| 只改了"主路径"，漏了近战/副手等次要路径 | 审计只搜了一种触发方式；要按**特性名**搜而不是按入口搜 |

## 6. 小结

- **共用几何 ≠ 共用特性**：变体之间可以共享实体类，但"专属特性"必须有一个**具名判据**（本节是
  `isBlackBlockStar()`），并在每个使用点显式门控；
- 找泄漏要用**特性名全仓搜索**（`Marks.BLACK` / `applyPillarPassive` / `DARKNESS`），而不是逐个入口回忆；
- 修复时**只加门控、不动共用逻辑**，风险最小；
- 次要路径（近战、副手、事件回调）也要一起门控，"很少触发"不是理由。
