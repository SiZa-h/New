# 03. 添加可放置方块与自定义创造模式物品页

本篇做两件事：

1. 把「黑方块」从**纯物品**升级成**真正能放置的方块**（Block + BlockItem + 掉落表）；
2. 单开一个名为 **NEW** 的创造模式物品页，承载本模组的全部物品。

难度比 01/02 略高，因为方块的东西比物品多一圈：注册表、掉落表、语言键前缀、以及**注册顺序**都会踩坑。下面按「先理解概念，再逐段写代码」的顺序来。

## 1. 先搞清楚：物品和方块是两套注册表

Minecraft 里「方块」和「物品」是两个独立的注册表：

| 概念 | 注册表 | 资源目录 | 语言键前缀 |
| --- | --- | --- | --- |
| 方块 Block | `BuiltInRegistries.BLOCK` | `assets/<ns>/blockstates/`、`assets/<ns>/models/block/` | `block.<ns>.<id>` |
| 物品 Item | `BuiltInRegistries.ITEM` | `assets/<ns>/items/`、`assets/<ns>/models/item/`、`assets/<ns>/textures/item/` | `item.<ns>.<id>` |
| 方块物品 BlockItem | 同上（Item 表） | 复用方块的模型 | 用 `useBlockDescriptionPrefix()` 后变成 `block.<ns>.<id>` |

想「能放置」，你需要**同一个 ID 同时存在于两张表里**：`BLOCK` 表里是方块本体，`ITEM` 表里是它的 `BlockItem`（拿着它右键能放下）。

> 因此 ID 一般保持一致：`new-mod:black_block` 既是方块也是物品。这样玩家、配方、标签、掉落表都只用一个名字。

## 2. 注册方块（ModBlocks）

```java
package com.sizah.block;

import com.sizah.NewMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	public static final ResourceKey<Block> BLACK_BLOCK_KEY = ResourceKey.create(
			Registries.BLOCK,
			NewMod.id("black_block")
	);

	public static final Block BLACK_BLOCK = new Block(
			BlockBehaviour.Properties.of()
					.setId(BLACK_BLOCK_KEY)          // 26.3 必须：方块也要有 ID
					.mapColor(MapColor.COLOR_BLACK)  // 地图上的颜色
					.strength(1.5F, 6.0F)            // 硬度、爆炸抗性
					.sound(SoundType.STONE)          // 音效
	);

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, BLACK_BLOCK_KEY, BLACK_BLOCK);
	}
}
```

要点：

- **`setId(...)` 是硬性要求**。MC 26.3 里方块/物品都要把 ID 写进属性；漏了会在类初始化时抛 `NullPointerException: Block id not set`（物品那边是 `Item id not set`，01/02 里我们已经踩过一次）。
- `ResourceKey<Block>` 是「这个名字在 BLOCK 表里的钥匙」，注册时必须给，游戏内部很多查找都依赖它。
- **没有调用 `requiresCorrectToolForDrops()`**：不要求正确工具，所以徒手也能挖下来。若写成要求工具，还要额外提供 `data/minecraft/tags/block/mineable/pickaxe.json` 把方块加进镐子标签，否则**任何工具都挖不掉**（这是新手最常见的「方块挖了不掉东西」原因）。

## 3. 物品形态：BlockItem（ModItems）

```java
public static final Item BLACK_BLOCK = new BlockItem(
		ModBlocks.BLACK_BLOCK,
		new Item.Properties()
				.useBlockDescriptionPrefix()
				.setId(BLACK_BLOCK_KEY)
);
```

- `BlockItem` 是「拿着能放置」的关键，它把右键 `useOn` 转成 `BlockPlaceContext` 并调用 `Level#setBlock`。
- `useBlockDescriptionPrefix()` 让物品名沿用**方块名**（语言键 `block.new-mod.black_block`）。不加的话它会去找 `item.new-mod.black_block`——你的 `zh_cn.json` 里如果只有 block 键，物品名就会显示成一串原始键名。
- 方块必须**先注册**、再构造 `BlockItem`：

```java
// NewMod.onInitialize()
ModBlocks.register();   // 先方块
ModItems.register();    // 再物品（BlockItem 需要已构造的 Block 实例）
```

## 4. 掉落表：不写就什么都不掉

方块被挖掉时，游戏查的是数据包里的**掉落表**，不是代码：

`src/main/resources/data/new-mod/loot_table/blocks/black_block.json`

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "condition": { "type": "minecraft:survives_explosion" },
      "entries": [
        { "type": "minecraft:item", "name": "new-mod:black_block" }
      ],
      "rolls": 1
    }
  ],
  "random_sequence": "new-mod:blocks/black_block"
}
```

- 路径是 **`loot_table`（单数）**，MC 1.21 起数据包目录全部改成单数（`loot_table`、`recipe`、`tags/item`、`advancement`），写成复数会被忽略。
- 最省事的做法是照抄一个「掉落自身」的原版文件（例如 `iron_block.json`），把名字换成自己的。
- `random_sequence` 是随机数种子来源，照抄格式即可。

## 5. 资源文件与语言键（容易漏的一步）

方块需要的资源（本模组已具备）：

| 文件 | 作用 |
| --- | --- |
| `assets/new-mod/blockstates/black_block.json` | 方块状态 → 模型映射。无属性方块用空字符串 `""` 作为变体名 |
| `assets/new-mod/models/block/black_block.json` | 方块模型（这里 `parent: minecraft:block/cube_all`） |
| `assets/new-mod/models/item/black_block.json` | 物品形态的模型（继承方块模型即可） |
| `assets/new-mod/items/black_block.json` | 1.21+ 的物品定义（`type: minecraft:model`） |
| `assets/new-mod/lang/*.json` | **语言键从 `item.` 改成 `block.`** |

```json
{
	"block.new-mod.black_block": "黑方块",
	"item.new-mod.black_block_star": "黑方块之星",
	"itemGroup.new-mod.new": "NEW"
}
```

## 6. 单开一个物品页：NEW

创造模式物品页（CreativeModeTab）本身也是一个注册表 `BuiltInRegistries.CREATIVE_MODE_TAB`。Fabric API 提供了更好用的建造器：

```java
package com.sizah.item;

import com.sizah.NewMod;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroups {
	public static final ResourceKey<CreativeModeTab> NEW_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			NewMod.id("new")
	);

	public static void register() {
		CreativeModeTab newTab = FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.new-mod.new"))   // 必填，否则 build() 抛异常
				.icon(() -> new ItemStack(ModItems.BLACK_BLOCK_STAR))      // 页签图标
				.displayItems((parameters, output) -> {                     // 页内物品
					output.accept(ModItems.BLACK_BLOCK);
					output.accept(ModItems.BLACK_BLOCK_STAR);
				})
				.build();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, NEW_TAB, newTab);
	}
}
```

### 6.1 为什么用 FabricCreativeModeTab 而不是 `CreativeModeTab.builder(row, column)`

原版建造器要求你指定**行号与列号**（`Row.TOP` + 具体列），多个模组很容易撞车。Fabric 的 `FabricCreativeModeTab.builder()` 用 `(row = null, column = -1)` 占位，由 Fabric 在客户端把模组页签**自动追加到原版页签之后**，不会覆盖别人。

### 6.2 页内物品的两种写法

- `output.accept(Item)`：加一个物品（最常用）。
- `FabricCreativeModeTabOutput` 还提供带 `TabVisibility` 的重载，可只加进「搜索页」而不进主页面。

> `displayItems` 的 lambda 是在**打开创造模式背包时**执行的，不是注册时。所以里面引用 `ModItems` 的字段是安全的，哪怕此时物品还没注册完。

### 6.3 和原版页签的关系

如果只是想把物品塞进原版页签，用 `CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)` 就行。本模组改成**独立的 NEW 页**，所以原版页签的注入被移除了——如果你希望物品「既在 NEW 页也在原版页」，两种方式完全可以并存。

## 7. 注册顺序总览

```java
// NewMod.onInitialize()
ModBlocks.register();       // 1. 方块
ModItems.register();        // 2. 物品（含 BlockItem）
ModItemGroups.register();   // 3. 物品页（内容延后生成，顺序不敏感）
ModEntities.register();     // 4. 实体类型（见 05）
ModNetworking.register();   // 5. 网络包（见 05）
```

## 8. 常见坑速查

| 现象 | 原因 |
| --- | --- |
| 启动崩溃 `NullPointerException: Block id not set` / `Item id not set` | 忘了 `.setId(ResourceKey)` |
| 方块能放，但挖掉什么都不掉 | 缺 `data/<ns>/loot_table/blocks/<id>.json` |
| 物品名显示成 `item.new-mod.black_block` 这样的原始键 | 少了 `useBlockDescriptionPrefix()` 或语言键前缀写错（`item.` vs `block.`） |
| 方块挖得极慢 / 挖了没掉落 | 加了 `requiresCorrectToolForDrops()` 却没配 `mineable` 标签 |
| 物品页不显示 | 忘了注册到 `CREATIVE_MODE_TAB`，或 `title(...)` 没设（Fabric 建造器会直接抛异常） |
| 方块贴图紫黑格 | `blockstates` / `models/block` / 贴图路径三者对不上（注意 `textures/block/` 与 `textures/item/`） |

## 9. 验证方式

```bash
./gradlew build        # 编译 + 打包，能抓出 setId、注册顺序等编译期问题
./gradlew runDatagen   # 无界面启动一次，真正执行 onInitialize 的注册逻辑
./gradlew runClient    # 进游戏：拿到黑方块 → 放置 → 挖掉 → 看是否掉落；打开创造背包看 NEW 页
```

`runDatagen` 是「低成本运行期验证」的好工具：它会完整走一遍注册流程，但不需要进世界，能抓到 `NullPointerException`、重复注册这类只在运行期暴露的错误。

## 10. 小结

- 方块 = **BLOCK 表 + ITEM 表（BlockItem）+ 掉落表 + 资源文件**，四样缺一不可。
- 26.3 里方块和物品都必须 `setId(...)`。
- 语言键前缀会随形态变化：方块用 `block.`，物品用 `item.`，BlockItem 用 `useBlockDescriptionPrefix()` 走方块键。
- 自定义物品页用 `FabricCreativeModeTab.builder()`，只写标题、图标、内容；位置交给 Fabric。
- **方块先注册，物品后注册**；掉落表放在数据包的 `loot_table/blocks/`（单数）目录。
