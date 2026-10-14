# 09. 多武器变体：一枚枚举派生出一整套差异

现在有两把武器：

| 武器 | 柱子外观 | 触碰伤害 | 是否无视护甲 |
| --- | --- | --- | --- |
| 黑方块之星 `new-mod:black_block_star` | 黑方块 | 6.0 | **是** |
| 黑曜石方 `new-mod:obsidian_cube` | 黑曜石 | 4.0 | 否（受护甲减免） |

它们**共用全部逻辑**：同一个实体、同一个网络包、同一套几何（伸长 / 反向伸长 / 两头破坏 / 消散）、
同一套击退与后坐、同样的命中特效，而且**都不使用普通攻击键**（左键即技能键）。

差别只有三处，全部塞进一枚枚举 `PillarKind`。本篇讲这种「一套逻辑 + 一枚枚举派生多件武器」的写法。

## 1. 把差异集中到枚举里

```java
public enum PillarKind {
	/** 黑方块之星：黑方块外观，6 点无视护甲的伤害。 */
	BLACK_BLOCK(ModBlocks.BLACK_BLOCK, 6.0F, ModDamageTypes.BLACK_BLOCK_PILLAR),

	/** 黑曜石方：黑曜石外观，4 点受护甲减免的伤害。 */
	OBSIDIAN(Blocks.OBSIDIAN, 4.0F, ModDamageTypes.OBSIDIAN_CUBE);

	private final Block block;
	private final float touchDamage;
	private final ResourceKey<DamageType> damageType;
	// 构造器与 getter 略

	public static PillarKind byId(int id) {
		PillarKind[] values = values();
		return id >= 0 && id < values.length ? values[id] : BLACK_BLOCK;
	}
}
```

为什么是枚举而不是三个常量：

- **一处声明、处处可用**：物品注册、实体渲染、伤害结算都从这里取值，新增武器只要加一行枚举；
- **顺序稳定**：`ordinal()` 可以安全地当作同步用的 id（下方 `byId` 做了越界保护）；
- **编译期穷尽**：以后要用 `switch` 分支处理，漏掉一枚会直接编译不过。

> 枚举里引用了 `ModBlocks.BLACK_BLOCK` 与 **原版** `Blocks.OBSIDIAN`——两者都是通用代码，不在客户端专属包里，所以枚举可以放在 common 侧，客户端渲染器与服务端逻辑都能用。

## 2. 物品侧：一个类，两次实例化

```java
public class PillarWeaponItem extends Item {
	private final PillarKind kind;

	public PillarWeaponItem(PillarKind kind, Item.Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public PillarKind kind() {
		return this.kind;
	}

	// hurtEnemy / getAttackDamageBonus：两把武器完全一致
}
```

```java
public static final Item BLACK_BLOCK_STAR = new PillarWeaponItem(
		PillarKind.BLACK_BLOCK,
		new Item.Properties().sword(ModToolMaterials.BLACK_BLOCK_STAR, 3.0F, -2.4F).setId(BLACK_BLOCK_STAR_KEY)
);

public static final Item OBSIDIAN_CUBE = new PillarWeaponItem(
		PillarKind.OBSIDIAN,
		new Item.Properties().sword(ModToolMaterials.OBSIDIAN_CUBE, 2.0F, -2.4F).setId(OBSIDIAN_CUBE_KEY)
);
```

- **不要再为每把武器写一个子类**。子类只适合「行为不同」的情况；这两把武器行为完全相同，差异是**数据**，用构造参数表达更贴切。
- 「减低伤害」在这里有两层：物品自身用另一个 `ToolMaterial`（攻击加成 2.0 < 3.0），柱子触碰伤害则来自枚举（4.0 < 6.0）。
- 修复材料也随之不同：黑方块之星用物品标签 `new-mod:black_block`，黑曜石方用 `new-mod:obsidian`（`data/new-mod/tags/item/obsidian.json` 里指向 `minecraft:obsidian`）。

## 3. 实体侧怎么知道「是哪种」：靠实体类型，不靠同步

最初的做法是往实体同步一个 int（`ordinal`），后来改成**让实体类型本身携带差异**：两把武器发射的
实体类型不同，实体从自己的类型里查出 `PillarSpec`，于是**一个同步字段都不用加**。

```java
// ModEntities：武器种类 → 实体类型
public static EntityType<BlackBlockPillarEntity> typeFor(PillarKind kind) {
	return kind == PillarKind.OBSIDIAN ? OBSIDIAN_PILLAR : SHORT_BLACK_BLOCK_PILLAR;
}

// ModEntities：实体类型 → 差异表（外观方块 / 伤害 / 最大长度 / 是否消散）
public static PillarSpec specOf(EntityType<?> type) { ... }
```

```java
// 实体、渲染器、伤害结算都从同一个真相来源取值
public PillarKind getKind() { return ModEntities.specOf(this.getType()).kind(); }
template.blockState = entity.getKind().block().defaultBlockState();
```

- **客户端本来就知道同步过来的实体是什么类型**，所以外观可以直接算，不需要同步字段；
- 服务端取伤害与伤害类型，客户端取外观方块，两边看的是同一张表；
- 新增一把武器 / 一种柱子，只要在 `ModEntities` 注册一个类型 + 一条 `PillarSpec`。

完整的四个类型（短黑方块柱 / 黑曜石柱 / 长黑方块柱 / 永黑方块柱）与这套架构见
[learn/10](10-entity-types-carry-the-differences.md)。

## 4. 「不能无视护甲」其实就是不加标签

两把武器用的是**两个不同的伤害类型**：

```
data/new-mod/damage_type/black_block_pillar.json   →  message_id: blackBlockPillar
data/new-mod/damage_type/obsidian_cube.json        →  message_id: obsidianCube
```

```
data/minecraft/tags/damage_type/bypasses_armor.json:
  values: [ "new-mod:black_block_pillar" ]      ← 只有黑方块之星在里面
```

于是「黑曜石方不能无视护甲」不需要任何 if 判断——**标签里没有它，护甲自然生效**。
死亡消息也顺带分开了：`death.attack.obsidianCube`。

## 5. 资源清单（新增一把武器要动的地方）

| 文件 | 作用 |
| --- | --- |
| `assets/new-mod/items/obsidian_cube.json` | 1.21+ 物品定义，指向下面的模型 |
| `assets/new-mod/models/item/obsidian_cube.json` | `{"parent": "minecraft:block/obsidian"}` —— **手持模型直接用原版黑曜石模型** |
| `assets/new-mod/lang/{en_us,zh_cn}.json` | `item.new-mod.obsidian_cube` 与两条死亡消息 |
| `data/new-mod/damage_type/obsidian_cube.json` | 新伤害类型（**不**进 bypasses_armor） |
| `data/new-mod/tags/item/obsidian.json` | 修复材料标签（`minecraft:obsidian`） |
| `item/ModItems.java`、`item/ModItemGroups.java` | 注册物品并加入 NEW 物品页 |

> 手持模型与实体模型要「同材质」是这里的核心诉求：物品侧靠 `parent: minecraft:block/obsidian`，
> 实体侧靠枚举里的 `Blocks.OBSIDIAN`，两边指向同一个原版模型/贴图，天然一致。

## 6. 「不需要普通攻击键」落在哪

客户端拦截左键的地方只判断基类，于是两把武器行为自动一致：

```java
ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
	if (clickCount == 0) return false;                       // 长按不连发
	if (!(player.getMainHandItem().getItem() instanceof PillarWeaponItem)) return false;

	// ……发包、冷却、挥手
	return true;                                             // 拦截原版左键处理（攻击/挖掘/挥手）
});
```

服务端也按同一个基类校验并读出种类：

```java
if (!(stack.getItem() instanceof PillarWeaponItem weapon)) return false;
BlackBlockPillarEntity pillar = new BlackBlockPillarEntity(ModEntities.typeFor(weapon.kind()), level);
```

**只要以后新增的武器也 `extends PillarWeaponItem`，客户端、网络、实体三处代码都不用改。**

## 7. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 新武器（沿用现有逻辑） | `PillarKind` 加一枚值 + `ModItems` 注册 + 资源文件 + 语言键 |
| 改某把武器的柱子伤害 | 对应枚举值的 `touchDamage` |
| 让某把武器无视护甲 | 把它的伤害类型加进 `bypasses_armor` 标签 |
| 改某把武器的手持/实体外观 | 枚举里的 `block`（物品侧同时改 `models/item/*.json` 的 parent） |
| 改武器自身攻击力 | `ModToolMaterials` 里的 `attackDamageBonus` 与 `sword(...)` 的基线 |
| 恢复普通攻击键 | `NewModClient.FIRE_ONLY_WHEN_AIMING_AT_NOTHING = true`（只在准心空指时发射） |

## 8. 常见坑

| 现象 | 原因 |
| --- | --- |
| 黑曜石方的柱子画成黑方块 | `ModEntities.specOf(...)` 里该类型的 `kind` 配错，或 `typeFor(...)` 映射错 |
| 实体报「未注册的实体类型」 | 新增类型忘了在 `ModEntities.register()` 里注册，或客户端渲染器没注册 |
| 黑曜石方的伤害仍然穿甲 | 它的伤害类型被误加进了 `bypasses_armor` 标签 |
| 死亡消息显示成 `death.attack.obsidianCube` | 语言键没加，或 `message_id` 与语言键对不上 |
| 物品栏里显示紫黑格 | `items/*.json` 与 `models/item/*.json` 的名字/路径不一致 |
| 新增武器点了左键没反应 | 新类没有 `extends PillarWeaponItem`，两处 `instanceof` 都不成立 |

## 9. 验证方式

```bash
./gradlew build
./gradlew runDatagen
./gradlew runClient   # 分别手持两把武器开火：一个黑方块柱、一个黑曜石柱
```

量化验证「是否无视护甲」：给僵尸穿全套铁甲，两把武器各打一次对比掉血；黑方块之星应明显更高。

## 10. 小结

- **行为相同、数据不同 → 用枚举 + 构造参数**，不要为每把武器复制一个子类。
- 三个差异点（外观方块、触碰伤害、伤害类型）集中在 `PillarKind`，新增武器只加一行枚举。
- 客户端只需外观 → **同步一个 `ordinal` 就够**，伤害留在服务端。
- 「是否无视护甲」是**标签的有无**，不是代码分支；死亡消息由 `message_id` 决定。
- 客户端与服务端都只判断基类 `PillarWeaponItem`，所以新增武器时**三处代码零改动**。
