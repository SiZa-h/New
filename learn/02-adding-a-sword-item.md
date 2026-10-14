# 02. 添加并注册一个剑类武器（黑方块之星）

本教程讲解如何在 Fabric 1.21.1+（MC 26.3）下添加自定义剑类武器。重点是理解 MC 26.3 中工具材料（ToolMaterial）的写法，以及剑的属性如何通过 `Item.Properties.sword(...)` 设置，而不是直接使用 `SwordItem` 构造函数的某些旧写法。

## 1. 武器的组成部分

一个自定义剑通常需要：

1. **ToolMaterial（工具材质）** —— 定义基础属性：耐久度（durability）、挖掘速度（speed）、攻击伤害加成（attackDamageBonus）、附魔等级（enchantmentValue）、修复材料（repairItems）。
2. **Item（武器物品）** —— 实际的物品实例。MC 26.3 中剑可以通过 `Item.Properties.sword(material, attackDamage, attackSpeed)` 来配置，而不是必须继承 `SwordItem` 并传参。
3. **注册** —— 使用 ResourceKey + `setId`，在 `ModItems.register()` 注册到 `BuiltInRegistries.ITEM`。
4. **创造物品栏** —— 加入 `CreativeModeTabs.COMBAT`。
5. **资源文件** —— 模型、贴图、物品 JSON、语言文件。

## 2. 定义 ToolMaterial（ModToolMaterials）

在 MC 26.3，`ToolMaterial` 是一个 `record` 类。其构造函数参数顺序如下（从反编译/源码可见）：

```java
new ToolMaterial(
    incorrectBlocksForDrops,  // TagKey<Block> — 不正确的挖掘方块标签（决定是否能正确掉落）
    durability,               // int — 耐久度
    speed,                    // float — 挖掘速度
    attackDamageBonus,        // float — 攻击伤害加成
    enchantmentValue,         // int — 附魔价值（越高越容易获得好附魔）
    repairItems               // TagKey<Item> — 修复材料标签
)
```

示例：`ModToolMaterials.BLACK_BLOCK_STAR`

```java
package com.sizah.item;

import com.sizah.NewMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public final class ModToolMaterials {
    public static final ToolMaterial BLACK_BLOCK_STAR = new ToolMaterial(
            BlockTags.INCORRECT_FOR_IRON_TOOL, // incorrectBlocksForDrops
            1561,                             // durability（比钻石略高）
            8.0F,                             // mining speed
            3.0F,                             // attack damage bonus
            10,                               // enchantment value
            createRepairTag(NewMod.id("black_block"))
    );

    private ModToolMaterials() {}

    private static TagKey<Item> createRepairTag(net.minecraft.resources.Identifier id) {
        return TagKey.create(Registries.ITEM, id);
    }
}
```

### 2.1 参数说明

- `incorrectBlocksForDrops`：使用原版标签控制哪些方块用这个工具挖掘时是否符合“正确工具”。这里用 `BlockTags.INCORRECT_FOR_IRON_TOOL` 作为参考，表示对大部分需要铁级及以上工具的方块，这套标签的判定方式遵循原版逻辑。
- `durability`：1561 —— 耐久度值（使用次数）。钻石剑 1561 左右？钻石工具耐久 1561（约），这里取 1561 作为示例。
- `speed`：8.0F —— 挖掘速度。剑主要用于战斗，这个值对剑的战斗属性影响不大，但 ToolMaterial 是通用的。
- `attackDamageBonus`：3.0F —— 攻击伤害加成。最终攻击伤害 = 基础剑伤害 + 这个加成（具体计算由游戏内部处理）。
- `enchantmentValue`：10 —— 附魔亲和度，类似于铁/钻石之间的水平。
- `repairItems`：修复标签。这里指向 `new-mod:black_block`（我们的黑方块物品）。意味着用黑方块可以在铁砧修复这把剑。

> 原理：`TagKey<Item>` 是物品标签（Item Tag），用于标识一组可用于修复的物品。通过 `TagKey.create(Registries.ITEM, id)` 创建自定义物品标签引用（不需要单独写 JSON 文件也能作为引用使用，游戏在配方/修复判断时会按注册的物品匹配）。

## 3. 注册剑类物品（ModItems）

在 MC 26.3，原版并不强制你新建一个 `SwordItem` 子类。更常见的做法是直接用 `Item` + `Item.Properties.sword(...)` 来构建剑属性。

```java
public static final ResourceKey<Item> BLACK_BLOCK_STAR_KEY = ResourceKey.create(
        Registries.ITEM,
        NewMod.id("black_block_star")
);

public static final Item BLACK_BLOCK_STAR = new Item(
        new Item.Properties()
                .sword(ModToolMaterials.BLACK_BLOCK_STAR, 3.0F, -2.4F)
                .setId(BLACK_BLOCK_STAR_KEY)
);
```

### 3.1 sword() 方法签名

`Item.Properties.sword(ToolMaterial material, float attackDamage, float attackSpeed)`

- `material`：工具材质，决定耐久、修复、部分挖掘行为等
- `attackDamage`：基础攻击伤害增量（最终伤害 = 这个值 + material.attackDamageBonus()，具体由游戏计算）
- `attackSpeed`：攻击速度（攻击冷却相关，负值表示较慢的攻速，原版钻石剑约 -2.4F）

示例参数（3.0F, -2.4F）接近钻石剑的手感，搭配 `attackDamageBonus 3.0F` 会让这把剑的输出略有变化。

### 3.2 为什么不用 SwordItem 构造函数？

在早期版本中，`SwordItem` 构造函数通常是 `(ToolMaterial tier, int attackDamageModifier, float attackSpeedModifier, Item.Properties properties)`。但在不同 MC 版本里类名/构造器可能有变化。MC 26.3 的反编译里未直接暴露 `SwordItem` 类路径（混淆/映射结构变化），但 `Item.Properties` 提供了 `.sword(...)`、`.pickaxe(...)`、`.axe(...)`、`.shovel(...)`、`.hoe(...)` 等便捷方法。这些方法内部会应用对应的工具属性（攻击范围、挖掘标签、修正等），是 Fabric/Loom 推荐且更版本适配友好的写法。

> 教程要点：**优先使用 `Item.Properties.sword(material, damage, speed)` 来创建剑类物品。** 它和原版注册方式一致，也减少了对特定类路径的依赖。

### 3.3 注册到注册表

和物品一样，使用 `Registry.register(BuiltInRegistries.ITEM, KEY, INSTANCE)`：

```java
public static void register() {
    Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_KEY, BLACK_BLOCK);
    Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_STAR_KEY, BLACK_BLOCK_STAR);
}
```

别忘了在 `NewMod.onInitialize()` 已经调用了 `ModItems.register()`。

## 4. 加入创造模式物品栏

把武器加到战斗栏（COMBAT）：

```java
CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
        .register(entries -> entries.accept(ModItems.BLACK_BLOCK_STAR));
```

`COMBAT` 栏位于原版创造模式的“战斗”页，放置剑、弓、盔甲等武器装备最合适。

## 5. 添加资源文件

### 5.1 物品模型（Item Model）

路径：`assets/new-mod/models/item/black_block_star.json`

```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "new-mod:item/black_block_star"
  }
}
```

剑类物品使用 `minecraft:item/handheld` 父模型（原版手持物品的通用模型，第三人称/第一人称手持时会有正确的旋转/缩放）。

### 5.2 物品定义（Items JSON，1.21+）

路径：`assets/new-mod/items/black_block_star.json`

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "new-mod:item/black_block_star"
  }
}
```

和之前的物品写法一致，指向对应的 item model。

### 5.3 贴图（Texture）

路径：`assets/new-mod/textures/item/black_block_star.png`

可以复用黑色主题，简单绘制一个带星芒或亮点的剑刃贴图。示例中生成的是一个带中心方块+外圈光晕的简易 16×16 图标。

### 5.4 本地化（Lang）

在 `en_us.json` 和 `zh_cn.json` 添加：

```json
"item.new-mod.black_block_star": "Black Block Star"
"item.new-mod.black_block_star": "黑方块之星"
```

## 6. 常见问题与说明

### Q1：能不能直接用 `SwordItem` 类？

MC 26.3 的混淆映射结构下，直接引用 `net.minecraft.world.item.SwordItem` 在某些环境可能需要注意。但使用 `Item.Properties.sword(...)` 是最安全、最贴合原版注册方式的做法，推荐优先使用。

### Q2：攻击伤害和攻速怎么调？

- `attackDamage`（sword 方法的第2个参数）+ `attackDamageBonus`（ToolMaterial 的第4个参数）共同决定最终攻击力。
- `attackSpeed`（第3个参数）是攻击冷却，数值越小（更负）攻速越慢。原版参考：木剑（1.6, -2.4F）、钻石剑（3.0, -2.4F）、下界合金剑（3.0, -2.4F）。

### Q3：修复材料为什么用 TagKey 而不是直接物品？

用物品标签（TagKey）更灵活。如果未来想让多种物品都能修复这把剑，只需要往对应标签里加物品即可，不用修改代码。

## 7. 构建验证

```bash
./gradlew.bat build
```

构建成功后，`build/libs/new-mod-1.0.0.jar` 会包含新增的物品、材质和模型。

## 8. 小结

1. 新建 `ModToolMaterials`，用 `record ToolMaterial` 定义耐久、伤害加成、附魔值、修复标签等属性。
2. 在 `ModItems` 中用 `ResourceKey<Item>` 定义 ID，用 `new Item(new Item.Properties().sword(material, damage, speed).setId(KEY))` 创建剑。
3. 在 `ModItemGroups` 把物品加入 `CreativeModeTabs.COMBAT`。
4. 补全 `models/item`、`items/*.json`、`textures/item`、`lang/*.json` 四类资源。
5. 编译通过即可在游戏中获得“黑方块之星”。
