# 04. 写一个自定义武器类（柱子武器）

02 里我们用 `Item.Properties.sword(...)` 做出了「一把剑」。但那还只是一个**普通 Item**——只是属性像剑而已。

本篇把它升级成**自定义武器类 `PillarWeaponItem`**，加上真正属于自己的战斗特性：

- 命中目标时让目标**陷入黑暗**（5 秒）；
- 对**已经处于黑暗中**的目标额外造成 2 点伤害。

> 这个类现在同时服务两把武器（黑方块之星 / 黑曜石方），差异由一个枚举描述，见 [learn/09](09-weapon-variants-by-enum.md)。

顺带讲清楚 MC 26.3 里「武器的属性」和「武器的行为」分别由谁负责。

## 1. 先分清：属性 vs 行为

| 部分 | 由谁决定 | 例子 |
| --- | --- | --- |
| 属性（数据） | `Item.Properties` + `ToolMaterial` | 耐久 1561、攻击 +3、攻速 -2.4、可附魔、修复材料 |
| 行为（代码） | `Item` 子类里覆写的方法 | 命中施加效果、条件加伤、特殊挖掘逻辑 |

也就是说：**继承 `Item` 不会自动获得剑的属性**，属性仍然要在 `Properties.sword(...)` 里给；反过来，**只写 `Properties.sword(...)` 也拿不到自定义行为**。两者要配合。

```java
public static final Item BLACK_BLOCK_STAR = new PillarWeaponItem(
		PillarKind.BLACK_BLOCK,                                          // 武器种类（外观/伤害/是否破甲）
		new Item.Properties()
				.sword(ModToolMaterials.BLACK_BLOCK_STAR, 3.0F, -2.4F)  // 属性：材质、攻击基线、攻速基线
				.setId(BLACK_BLOCK_STAR_KEY)
);
```

`Properties.sword(...)` 内部会做这些事（可以理解为「剑的标准配置」）：

```
durability(material.durability())      // 耐久
repairable(material.repairItems())     // 修复材料
enchantable(material.enchantmentValue())// 附魔亲和
.component(TOOL, ...)                  // 挖掘相关（蛛网 15 倍速等）
.attributes(...)                       // 主手攻击力 / 攻速
.component(WEAPON, new Weapon(1))      // 声明这是武器（每击掉 1 点耐久）
```

**关键点**：`WEAPON` 组件决定了「攻击命中后会不会掉耐久」——`ItemStack#postHurtEnemy` 里读的就是它。所以即使你自定义了武器类，也别忘了让属性里带上 `sword(...)`（或手工加 `WEAPON` 组件）。

## 2. 可用（可覆写）的武器钩子

MC 26.3 的 `Item` 上，和战斗相关的公开方法有这些：

| 方法 | 触发时机 | 常见用途 |
| --- | --- | --- |
| `float getAttackDamageBonus(Entity victim, float damage, DamageSource source)` | 玩家攻击**计算伤害时** | 条件增伤（对某类目标打得更疼） |
| `void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)` | 命中判定成立、**伤害尚未结算** | 给目标上负面效果、吸血前置逻辑 |
| `void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)` | 伤害结算**之后** | 击杀奖励、额外消耗 |
| `boolean mineBlock(...)` | 用物品挖掉一个方块后 | 特殊耐久消耗 |
| `boolean isCorrectToolForDrops(ItemStack, BlockState)` | 判定「是否算正确工具」 | 特殊工具判定 |

调用链（来自 26.3 源码，`Player#attack`）：

```java
baseDamage += weapon.getAttackDamageBonus(target, baseDamage, damageSource); // ① 先算伤害
...
if (itemHurtEnemy) {                      // ② ItemStack#hurtEnemy 返回 true 表示这是武器
	target.hurtServer(...);               //    真正扣血
	weapon.postHurtEnemy(target, player); // ③ 之后再扣耐久
}
```

`ItemStack#hurtEnemy` 的实现很短，值得记住：

```java
public boolean hurtEnemy(LivingEntity mob, LivingEntity attacker) {
	Item usedItem = this.getItem();
	usedItem.hurtEnemy(this, mob, attacker);   // ← 你覆写的方法在这里被调用
	if (this.has(DataComponents.WEAPON)) {     // 有 WEAPON 组件才继续
		if (attacker instanceof Player player) player.awardStat(...);
		return true;
	}
	return false;
}
```

**结论：`hurtEnemy` 在伤害结算之前执行**，所以在里面加「黑暗」，同一击就能吃到「对黑暗目标增伤」的加成——这正是我们要的效果，而且顺序是天然的，不需要额外计时器。

## 3. 实现 PillarWeaponItem

```java
package com.sizah.item;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class PillarWeaponItem extends Item {
	/** 命中时施加的黑暗持续时间（tick，20 tick = 1 秒）。 */
	private static final int DARKNESS_DURATION_TICKS = 100;

	/** 对已处于黑暗中的目标追加的伤害。 */
	private static final float DARKNESS_BONUS_DAMAGE = 2.0F;

	private final PillarKind kind;

	public PillarWeaponItem(PillarKind kind, Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public PillarKind kind() {
		return this.kind;
	}

	@Override
	public void hurtEnemy(ItemStack itemStack, LivingEntity target, LivingEntity attacker) {
		if (!attacker.level().isClientSide()) {
			target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS), attacker);
		}
	}

	@Override
	public float getAttackDamageBonus(Entity victim, float damage, DamageSource damageSource) {
		return victim instanceof LivingEntity living && living.hasEffect(MobEffects.DARKNESS)
				? DARKNESS_BONUS_DAMAGE
				: 0.0F;
	}
}
```

### 3.1 三个细节

1. **`!attacker.level().isClientSide()` 不能省。** 攻击逻辑在客户端和服务端都会跑（客户端跑是为了即时反馈），效果必须只在服务端施加，否则会出现「本地看着生效、实际没生效」的错位。
2. **`MobEffects.DARKNESS` 是 `Holder<MobEffect>`**，不是 `MobEffect` 本身。`MobEffectInstance` 的构造器正好接收 `Holder<MobEffect>`，所以直接传就行。
3. **`getAttackDamageBonus` 的 `victim` 是 `Entity`**，先 `instanceof LivingEntity` 再调用 `hasEffect(...)`——`hasEffect(Holder<MobEffect>)` 定义在 `LivingEntity` 上。

### 3.2 为什么不用 `hurtEnemy` 之外的方式加效果

也可以在 `postHurtEnemy` 里加，但那样「本击增伤」就会晚一拍（目标挨打时还没黑暗，第二击才吃到加成）。想做成「先上 debuff 再增伤」的手感，就必须在 `hurtEnemy`。

## 4. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 黑暗更久/更短 | `DARKNESS_DURATION_TICKS`（20 tick = 1 秒） |
| 增伤更高 | `DARKNESS_BONUS_DAMAGE` |
| 换成凋零/缓慢/中毒 | 把 `MobEffects.DARKNESS` 换掉（`MobEffects.WITHER`、`MobEffects.MOVEMENT_SLOWDOWN`…） |
| 命中时回血 / 击退 | 在 `hurtEnemy` 里对 `attacker` 操作，或改 `postHurtEnemy` |
| 基础攻击力、耐久、攻速 | `ModToolMaterials` 与 `Properties.sword(...)` 的两个数字 |

> 注意：`addEffect(instance, source)` 的第二个参数是**效果来源实体**，用于死亡消息与统计。传 `attacker` 比传 `null` 更规范。

## 5. 常见坑

| 现象 | 原因 |
| --- | --- |
| 效果只在自己屏幕上出现 | 忘了 `isClientSide()` 判断，或者反过来只在客户端加了 |
| 打怪不掉耐久 | `Properties` 里没带 `sword(...)`/`WEAPON` 组件 |
| 条件增伤永远不生效 | 效果加在 `postHurtEnemy`（比伤害结算晚），换到 `hurtEnemy` |
| 编译报错 `MobEffect` 与 `Holder<MobEffect>` 不匹配 | `MobEffects.XXX` 是 `Holder`，需要 `new MobEffectInstance(...)`，不要直接 `addEffect(MobEffects.XXX)` |

## 6. 验证方式

```bash
./gradlew build        # 编译通过
./gradlew runClient    # 进游戏：创造模式拿剑 → 打一只僵尸 → 观察黑暗粒子与血量差
```

想量化验证「增伤是否生效」，可以给目标挂 `/effect give @e darkness` 后再各打一次，对比伤害数字。

## 7. 小结

- **属性靠 `Properties`，行为靠子类**；写自定义武器类时两者都要给。
- `hurtEnemy` 在**伤害结算前**执行，是「上 debuff + 同击增伤」的正确位置；`postHurtEnemy` 在之后。
- 组合 `sword(...)` + 自定义子类，就能同时拥有「原版剑的手感」和「自己的战斗特性」。
- 同一个武器类可以通过构造参数/枚举派生出多把武器，见 [learn/09](09-weapon-variants-by-enum.md)。

> 注意：本模组的左键被「发射柱子」技能接管，所以这两把武器**不使用普通攻击键**，
> 上面这些命中特效只在其他途径触发攻击时才生效。
