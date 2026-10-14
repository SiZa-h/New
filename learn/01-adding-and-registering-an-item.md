# 01. 添加并注册一个新物品（黑方块）

本教程基于本次实践，通俗地讲解在 Fabric 1.21.1+（MC 26.3）下如何添加并注册一个自定义物品。它不是简单地复制代码，而是说明每一步的目的、原理和为什么要这么做。

## 1. 概念：什么是“注册（Registration）”

在 Minecraft（以及大部分模组加载器）里，游戏的内容（方块、物品、生物群系、创造物品栏等）都是通过注册表（Registry）统一管理的。

简单来说：注册 = 告诉游戏：“我有一个叫 `black_block` 的物品，请把它登记到物品注册表里，以后可以通过这个 ID 使用它”。

Fabric 使用 Minecraft 的原版注册表系统（`BuiltInRegistries`），配合模组自己的命名空间（Namespace）来避免 ID 冲突。

## 2. 确定 Mod ID 与包名

本项目的 Mod ID 定义在 `fabric.mod.json` 和 `NewMod` 中：

- Mod ID：`new-mod`
- 包名：`com.sizah`

所以我们的物品 ID 将是 `new-mod:black_block`。在代码里，我们使用 `NewMod.id("black_block")` 来生成 `Identifier`（资源/注册 ID）。

```java
// NewMod.java
public static final String MOD_ID = "new-mod";
public static Identifier id(String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
}
```

> 原理：命名空间（modid） + 路径（path） = 唯一 ID，避免和其他模组或原版冲突。

## 3. 创建物品注册类（ModItems）

为了保持代码结构清晰，我们通常把所有物品的常量和注册逻辑放在单独的类里（`com.sizah.item.ModItems`）。

### 3.1 定义 ResourceKey（物品 ID 的“钥匙”）

从 1.21.2+ 开始，注册表条目越来越倾向于先定义 `ResourceKey<T>` 来明确这个条目的 ID。对于 Item 来说：

```java
public static final ResourceKey<Item> BLACK_BLOCK_KEY = ResourceKey.create(
        Registries.ITEM,        // 注册表类型
        NewMod.id("black_block") // Identifier
);
```

**为什么要用 ResourceKey？**

- 它是 ID 的类型安全表示，而不是单纯的字符串。
- 在 `Item.Properties.setId(...)` 中显式绑定 ID，是 1.21.2+（MC 26.3）较新的要求。部分版本/运行时检查会因为 ID 未绑定而导致初始化失败（本次遇到的“物品 ID 未被正确设置，导致 ModItems 初始化失败”就是这个原因）。
- 把 Key 和实例分开，有助于控制注册时机和避免静态初始化顺序问题。

### 3.2 创建 Item 实例

```java
public static final Item BLACK_BLOCK = new Item(
        new Item.Properties().setId(BLACK_BLOCK_KEY)
);
```

这里用的是最简单的 `Item`。如果你想做工具、食物、装备等，可以在 `Properties` 上链式调用对应方法（例如 `stacksTo(64)`、`food(...)` 等）。MC 26.3 中 `Item.Settings` 已重命名为 `Item.Properties`。

### 3.3 实际注册到注册表

注册操作本身要放到 `register()` 方法里，在模组初始化阶段调用，而不是只靠静态字段的赋值来“触发”。

```java
public static void register() {
    Registry.register(BuiltInRegistries.ITEM, BLACK_BLOCK_KEY, BLACK_BLOCK);
}
```

参数说明：

1. `BuiltInRegistries.ITEM` —— 物品注册表（原版/通用的注册表实例）。注意：以前常见的写法是传 `Registries.ITEM`（ResourceKey），但 `Registry.register` 的重载需要传入 `Registry<V>` 实例，这里要用 `BuiltInRegistries.ITEM`。
2. `BLACK_BLOCK_KEY` —— 要注册的条目的 ID（ResourceKey）
3. `BLACK_BLOCK` —— 要注册的物品实例

> 教程要点：**ID 通过 `setId(KEY)` 绑定到 Properties，注册时再把 (KEY, INSTANCE) 注册到 `BuiltInRegistries.ITEM`。** 这种写法既符合 1.21.2+ 的要求，也能避免静态初始化时序导致的失败。

## 4. 在 ModInitializer 中调用注册

`fabric.mod.json` 指定了入口点 `main: com.sizah.NewMod`，因此我们在 `NewMod.onInitialize()` 中按顺序调用注册方法。

```java
@Override
public void onInitialize() {
    LOGGER.info("Hello Fabric world!");
    ModItems.register();
    ModItemGroups.register();
}
```

**为什么要显式调用，而不是依赖静态初始化？**

Java 的静态字段初始化顺序在跨类引用时有时会变得复杂。将“创建实例”（静态字段）和“实际登记到游戏注册表”（register() 调用）分开，可以更清晰地控制时机，减少启动阶段的隐含依赖，避免“入口无法执行/初始化失败”。

## 5. 添加到创造模式物品栏（ModItemGroups）

单纯注册物品还不够，它默认不会自动出现在创造模式物品栏。Fabric 提供了 `fabric-creative-tab-api-v1` 来方便地向原版物品栏添加物品。

```java
package com.sizah.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;

public final class ModItemGroups {
    private ModItemGroups() {}

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> entries.accept(ModItems.BLACK_BLOCK));
    }
}
```

这里把 `BLACK_BLOCK` 加入了 `INGREDIENTS`（材料/配方材料）栏。你也可以选择其他栏位，比如 `BUILDING_BLOCKS`（建筑方块）、`NATURAL_BLOCKS`、`FUNCTIONAL_BLOCKS`、`TOOLS_AND_UTILITIES`、`COMBAT`、`FOOD_AND_DRINKS` 等，根据物品用途决定。

> 原理：`CreativeModeTabEvents.modifyOutputEvent(tabKey)` 在游戏构建该物品栏的显示列表时触发回调，我们在回调里调用 `entries.accept(item)` 把自定义物品追加进去。

## 6. 添加资源文件（Models、Textures、Blockstates、Items、Lang）

Minecraft 是基于资源包（assets）的，物品要在游戏里正确显示，需要提供模型、贴图等资源文件。我们的 `black_block` 在这里被当作一个**物品（Item）**使用，如果后续想做成可放置的方块，还需要额外的 block 资源，这里按“物品”形式处理。

### 6.1 贴图（Texture）

路径：`src/main/resources/assets/new-mod/textures/block/black_block.png`

我们用简单的程序生成了 16×16 的深色网格贴图，用于方块/物品的外观。实际做模组时建议使用图片编辑器绘制。

### 6.2 方块状态（BlockState）—— 可选但完整

路径：`assets/new-mod/blockstates/black_block.json`

```json
{
  "variants": {
    "": {
      "model": "new-mod:block/black_block"
    }
  }
}
```

即使当前是纯物品，也保留 blockstates/models/block 是有好处的（如果未来把它变成 Block，或者某些物品模型继承自方块模型时需要）。

### 6.3 方块模型（Block Model）

路径：`assets/new-mod/models/block/black_block.json`

```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "new-mod:block/black_block"
  }
}
```

继承原版的 `cube_all`（全六面相同贴图），把 `all` 指向我们的贴图。

### 6.4 物品模型（Item Model）

路径：`assets/new-mod/models/item/black_block.json`

```json
{
  "parent": "new-mod:block/black_block"
}
```

直接继承方块模型，这样物品在手上/背包里就显示成方块的外观（很常见，比如原版的混凝土块就是这种做法）。

### 6.5 物品模型定义（Items JSON，1.21+）

路径：`assets/new-mod/items/black_block.json`

在 1.21+，Fabric/Loom 推荐为物品单独定义 `assets/<modid>/items/<id>.json`，指明使用哪个模型：

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "new-mod:block/black_block"
  }
}
```

这是 1.21 对物品模型加载方式的一部分改动，配合 Loom/vanilla 模型系统使用。

### 6.6 本地化（Language）

路径：`assets/new-mod/lang/en_us.json`、`assets/new-mod/lang/zh_cn.json`

```json
{
  "item.new-mod.black_block": "Black Block"
}
```

```json
{
  "item.new-mod.black_block": "黑方块"
}
```

键名格式是 `item.<modid>.<item_id>`。游戏会根据语言设置自动选择对应文件。

## 7. 为什么是这个写法（MC 26.3 / Fabric API 要点）

- **使用 `Item.Properties().setId(KEY)`**：MC 1.21.2+ 对注册表条目的 ID 绑定要求更严格。提前把 ID 绑定到物品的属性里，可以避免“物品 ID 未被正确设置”导致的注册/初始化失败。
- **`BuiltInRegistries.ITEM` vs `Registries.ITEM`**：`Registries.ITEM` 是 `ResourceKey<Registry<Item>>`，用于标识注册表本身；`BuiltInRegistries.ITEM` 是实际的 `Registry<Item>` 实例。`Registry.register` 需要的是 Registry 实例。
- **延迟到 `register()` 调用**：将实际注册从静态字段初始化推迟到模组 `onInitialize()`，降低了类加载时序导致的问题风险。
- **Creative Tab 用 Events API**：Fabric 的 Creative Tab API（v1）使用事件方式添加物品，避免直接修改原版注册表，兼容性更好。

## 8. 构建验证

```bash
./gradlew.bat build
```

成功后会在 `build/libs/` 生成：

- `new-mod-1.0.0.jar` —— 模组本体
- `new-mod-1.0.0-sources.jar` —— 源码包（可选，用于调试/发布）

## 9. 小结

1. 用 `ResourceKey<Item>` 定义物品 ID
2. 用 `new Item(new Item.Properties().setId(KEY))` 创建实例（显式绑定 ID）
3. 在 `ModItems.register()` 里调用 `Registry.register(BuiltInRegistries.ITEM, KEY, INSTANCE)` 完成注册
4. 在 `NewMod.onInitialize()` 调用 `ModItems.register()` 和 `ModItemGroups.register()`
5. 补全 assets（models/items/blockstates/textures/lang）让物品能正确显示和本地化
6. 编译构建验证

按照这个流程添加物品，既符合当前 MC 26.3 的 API，也能避免大部分启动阶段的注册时序问题。
