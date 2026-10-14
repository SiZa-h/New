# 内容开发约定（content/）

这个目录按**内容类型**分家。以后加方块、物品、武器、工具、盔甲、食物、生物、投掷物、机器、世界生成，
都只在自己那一格里动，不要去改别的格子。

## 目录地图

| 目录 | 放什么 | 注册入口 |
| --- | --- | --- |
| `block/` | 方块；`OreFamilies` 是"一种材料十个方块"的属性工厂 | `ModBlocks.register()` |
| `item/` | 普通物品、材料、锭/宝石等 | `ModItems.register()` |
| `weapon/` | 武器本体（`PillarWeaponItem`）、武器变体表（`PillarKind`）、独立冷却（`WeaponCooldowns`）、以及武器产生的实体（`weapon/entity/`） | `ModItems` + `ModEntities` |
| `tool/` | 镐/斧/锹/锄；材料表 `ModToolMaterials` | `ModTools.register()` |
| `armor/` | 盔甲与饰品 | `ModArmor.register()` |
| `food/` | 食物与消耗品 | `ModFoods.register()` |
| `mob/` | 生物（含刷怪蛋、刷怪规则、战利品） | `ModMobs.register()` |
| `projectile/` | 投掷物、法术弹；参考 `weapon/entity/BlackBlockPillarEntity` 的写法 | `ModProjectiles.register()` |
| `machine/` | 机器与多方块（方块 + 方块实体 + tick） | `ModMachines.register()` |
| `world/` | 矿脉、地形、结构（大部分是 datagen 出的 JSON） | `ModWorldGen.register()` |
| `enchant/` | 附魔、状态效果、药水 | `ModEnchantments.register()` |
| `effect/` | **行为**层：标记、爆炸、击退冲突、感染、黑洞/弹幕/禁锢这些效果型实体 | 由使用方调用 |
| `ModCreativeTabs.java` | 本模组的创造模式标签页 | `ModCreativeTabs.register()` |

> 上面这些 `register()` 目前都是**空方法**，`NewMod.onInitialize` 已经按正确顺序调用它们
> （方块 → 物品 → 各类内容 → 创造标签页 → 实体类型 → 网络）。加内容时把代码填进去即可，不用动入口。

## 加一把武器：先想清楚"柱子长什么样"

武器（`weapon/`）和别的方块物品不一样：**黑方块之星那套技能是"造柱子"，而柱子长什么样是由实体类型携带的**
（`ModEntities.PillarSpec → PillarKind → 方块`）。所以

> **一根新外观的柱子 = 一个新的实体类型。** 想加"白方块柱"，就必须有 `short_white_block_pillar` 这类类型，
> 仅靠换贴图是做不到的。

### 两颗星现在的分工

| | 黑方块之星 | 白方块之星 |
| --- | --- | --- |
| 触发方式 | 双击左/右键出柱、单击+滑动出手势、潜行右键冲刺、潜行左键感染 | **按住左键**持续放出白柱、**按住右键**把准心生物钉在视线上、潜行左键感染成白方块 || 柱子实体 | `BlackBlockPillarEntity` 的 8 个类型（生长型光束） | `WhitePillarEntity`（**飞行型**：0.1 格粗、5 格长、256 格/秒、128 格射程） |
| 命中标记 | 黑标记 **B**（`Marks.BLACK`） | 白标记 **W**（`Marks.WHITE`） |
| 副手 | — | 副手持白星时，**任何造成伤害的攻击**都会生成 2~5 根白柱打击受害目标 |

> **两个标记同时存在就触发「消散」**（`content/effect/Decay`）：每 0.5 秒受到 `2 × 已生效秒数` 的伤害，
> 重复获得时**累加时长而不是重置**。

> **潜行优先于白星的两个按住技能**：潜行时左键是感染、右键无效，客户端与服务端都按这条判定
> （服务端也判，所以改过的客户端无法用"按住"绕过）。这也是为什么"潜行左键"不会顺带射出一根白柱。

### 给它加技能时要注意的结构

1. `weapon/PillarKind.java` 是"黑星/黑曜石方"的变体表——白星的柱子**不属于**它，因为飞行行为和生长行为
   不是同一套字段（这也是 `WhitePillarEntity` 单独成类的原因）；
2. `weapon/entity/ModEntities.java` 里，黑柱与白柱的类型、以及各自的 `PillarSpec`（只有黑柱有）都在这；
3. 触发层：`client/input/GestureInput`（黑星手势）、`client/input/WhiteStarInput`（白星按住）、
   `common/network/ModNetworking`（服务端权威实现）、`weapon/OffhandWhiteStar`（副手伤害事件）；
4. 想给白星加"生长型"柱子的话，照 `ModEntities` 里黑柱的写法加类型 + `PillarSpec` 即可，不用动实体类。

## 加一个方块/物品的最短路径

```java
// 1) block/ModBlocks.java —— 一行注册（SimpleRegistry 会建 key、注册、把实例还给你）
public static final Block RUBY_BLOCK = SimpleRegistry.block("ruby_block",
        BlockBehaviour.Properties.of().strength(5.0F, 6.0F).requiresCorrectToolForDrops());

public static void register() {
    Registry.register(BuiltInRegistries.BLOCK, RUBY_BLOCK_KEY, RUBY_BLOCK);
    // 物品形态：名字与方块共用一条语言键
    RUBY_BLOCK_ITEM = SimpleRegistry.blockItem("ruby_block", RUBY_BLOCK);
}
```

```java
// 2) 画贴图：src/main/resources/assets/new-mod/textures/block/ruby_block.png
// 3) 跑一次数据生成，模型/掉落/标签/双语名字全部自动产出
./gradlew runDatagen
// 4) 进游戏看
./gradlew runClient
```

**语言文件不用手写**：`datagen/ModLanguageProvider` 会遍历注册表，把 `new-mod:` 下的每个方块/物品/实体
自动命名（`ruby_block` → `Ruby Block`）；中文名不想要"美化 id"时，才在 `CHINESE` 表里加一行（例如白方块）。

### 已经走完这条路的例子：白方块

白方块（黑方块的反色）就是按上面四步加进来的，可以照着抄：

| 步骤 | 文件 |
| --- | --- |
| 贴图 | `tools/gen-block-textures.js` 生成 `textures/block/white_block.png`（黑方块每通道取反：内部 `#E3E3DF`、边框 `#EDEDEB`） |
| 方块 | `block/ModBlocks.java` 的 `WHITE_BLOCK`（`MapColor.SNOW`，其余属性与黑方块一致） |
| 物品 | `item/ModItems.java` 的 `WHITE_BLOCK`（`SimpleRegistry.blockItem`）+ 创造标签页里加一行 |
| 产出 | `./gradlew runDatagen` 生成 blockstate／模型／掉落表／中英名字，**没有手写任何 JSON** |

## datagen 产什么、放哪

| 产物 | 生成者 | 手写副本 |
| --- | --- | --- |
| `assets/<ns>/blockstates/*.json` | `ModModelProvider` | 已删（原来是手写的） |
| `assets/<ns>/models/block/*.json`、`models/item/<ns>.json`、`items/<ns>.json` | `ModModelProvider` | 已删 |
| `assets/<ns>/items/<id>.json`（物品模型定义） | `ModModelProvider` | 已删 |
| `assets/<ns>/lang/en_us.json`、`zh_cn.json` | `ModLanguageProvider` | 已删 |
| `data/<ns>/loot_table/blocks/*.json` | `ModBlockLootProvider` | 已删 |
| `data/<ns>/tags/...` | `ModTagProvider` | 已删 |
| 配方 `data/<ns>/recipe/*.json` | `ModRecipeProvider`（目前是骨架） | — |
| 贴图 PNG、3D 武器模型、伤害类型 JSON | **手写**，留在 `src/main/resources` | 不生成 |

生成结果落在 `src/main/generated`，Loom 会把它当资源目录一起打进 jar。

> **规矩**：`src/main/generated` 里的文件**不要手改**，下次 `runDatagen` 会覆盖；要改就改对应的 Provider。
> 每加完内容跑一次 `runDatagen` 并提交生成结果——`src/main/resources` 与 `src/main/generated` 里
> **不能有同名文件**，否则两个目录会互相覆盖，模型/贴图对不对就看运气了。

### 手写模型的物品：必须声明 `items/<id>.json`

26.3 里物品**必须**有一份**物品模型定义** `assets/<ns>/items/<id>.json`（内容是"这个物品用哪个模型"）。
它有三种来源，别漏：

| 情况 | 谁生成 | 怎么做 |
| --- | --- | --- |
| 普通物品 / 手持工具 | `generateFlatItem(...)` | 一行搞定，定义与模型一起产出 |
| 方块物品 | `createTrivialCube(block)` | 同上，自动指向方块模型 |
| **手写模型**（如两颗星、黑曜石方的 3D 模型） | `declareCustomModelItem(item)` | **必须显式声明**，否则物品没有任何模型定义 → 游戏里显示**黑紫块** |

> 白方块之星就踩过这个坑：模型与贴图都对，但没人声明 `items/white_block_star.json`，于是渲染成黑紫块。
> 现在三个自定义模型物品都在 `ModModelProvider.generateItemModels` 里声明了。

## 命名与注册约定

- **id 一律小写下划线**：`ruby_ore`、`deepslate_ruby_ore`，注册名一旦发布就不能改（存档里写的是它）。
- **一个材料一套**：金属块/矿石/生料块用 `OreFamilies.metal/ore/rawBlock/decorative/plant`，
  属性写在一处，楼梯/台阶/墙用 `OreFamilies.copyOf(base)` 复制属性，避免"改了硬度忘了改台阶"。
- **方块先于物品**：`BlockItem` 需要方块实例已经构造好，`NewMod` 的顺序别动。
- **行为放 `effect/`，定义放自己那一格**：比如"给实体打标记"是 `effect/Marks`，而"中毒效果的定义"属于 `enchant/`。

## 常见坑

| 现象 | 原因 |
| --- | --- |
| 进游戏物品叫 `item.new-mod.xxx` | 忘了跑 `runDatagen`，或生成文件没提交 |
| 模型紫黑/缺失 | 贴图路径不对（方块在 `textures/block/`，物品在 `textures/item/`），或模型没生成 |
| 挖了不掉落 | `ModBlockLootProvider` 里既没有 `dropSelf` 也没有 `add(...)` |
| 每个内容类型注册两次报 id 冲突 | 同一个 id 在两处注册（例如 `ModBlocks` 与 `OreFamilies` 各建了一个） |
| 改了 `SimpleRegistry` 的 id 后存档报错 | 注册名已经写进存档，不能改；要改就当作新内容加 |
