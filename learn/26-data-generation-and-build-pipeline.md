# 26. 数据生成（datagen）与构建流水线：让 JSON 由代码产出

需求：**项目要长期加内容，别再手写 JSON 了**，顺带把打包/CI 理顺。

这一篇是 `com.sizah.datagen` 的说明书，也是三个"会浪费一整晚"的坑的现场记录。

## 1. 六个 Provider，各管一段

```
datagen/
├── NewModDataGenerator        入口：./gradlew runDatagen
├── ModModelProvider           方块状态 + 方块模型 + 物品模型/定义
├── ModLanguageProvider        en_us.json + zh_cn.json
├── ModBlockLootProvider       掉落表
├── ModRecipeProvider          配方
└── ModTagProvider             标签（物品 / 方块）
```

注册方式（`FabricDataGenerator.Pack.addProvider`，构造器引用即可）：

```java
FabricDataGenerator.Pack pack = generator.createPack();

pack.addProvider(ModModelProvider::new);                        // 需要 PackOutput
pack.addProvider(ModLanguageProvider::english);                 // 需要 PackOutput + registries
pack.addProvider(ModLanguageProvider::chinese);
pack.addProvider(ModBlockLootProvider::new);
pack.addProvider(ModRecipeProvider::new);
pack.addProvider(ModTagProvider::items);
pack.addProvider(ModTagProvider::blocks);
```

产物落在 **`src/main/generated`**，Loom 会把它当资源目录一起打进 jar。

> 关键认识：**datagen 只是在服务端/客户端起一个无头 Minecraft 跑一圈 Provider**。所以它跑的是**编译后的模组代码**——
> 这解释了两件事：为什么 `runDatagen` 依赖 `classes`；以及为什么它必须在"代码能编译"之后才能跑。

## 2. 语言文件：英文自动、中文覆盖

最省事的一条：**方块/物品/实体的英文名从注册表自动推导**，新内容不用写英文：

```java
for (Item item : BuiltInRegistries.ITEM) {
	Identifier id = BuiltInRegistries.ITEM.getKey(item);
	if (NewMod.MOD_ID.equals(id.getNamespace())) names.put(item.getDescriptionId(), humanise(id.getPath()));
}
// humanise("long_black_block_pillar") -> "Long Black Block Pillar"
```

中文是一个**覆盖表**（`CHINESE`），缺了也不会露出翻译键，最多显示英文式的"美化 id"：

```java
Map.entry(ModBlocks.WHITE_BLOCK.getDescriptionId(), "白方块"),
Map.entry(ModItems.WHITE_BLOCK_STAR.getDescriptionId(), "白方块之星"),
Map.entry("effect.new-mod.decay", "消散"),
Map.entry("death.attack.decay", "%1$s 消散了"),
```

而"游戏无法推导的键"（按键、界面标题、死亡消息、创造页标题）两边都要写：`ENGLISH` 提供英文，`CHINESE` 提供中文。

## 3. 两条硬性规矩

1. **`src/main/generated` 不要手改**：下次 `runDatagen` 直接覆盖。要改就改 Provider。
2. **`src/main/generated` 与 `src/main/resources` 不能有同名文件**：两个目录都会进 jar，谁覆盖谁取决于顺序。
   所以"手写副本 → 改由生成器产出"时必须**删掉原来那份**（黑方块的 blockstate/模型/掉落表、两颗星的物品定义都是这么转过来的）。

另外把生成结果**提交进版本库**：这样干净检出后 `./gradlew build` 也能打出完整的 jar，CI 不必依赖"先跑 datagen"。

## 4. 三个坑（都真实踩过）

### 坑 1：`FabricDataOutput` 已改名为 `FabricPackOutput`

26.3 的 Fabric datagen API 里没有 `FabricDataOutput` 这个类（旧教程到处都是它）。正确的类型与导入是：

```java
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
```

同类改名要一起记住的还有：`RecipeProvider` 现在是**实例对象**（由 `createRecipeProvider(registries, recipes, advancements)` 返回），
它的 `buildRecipes()` **不带参数**、通过自己的 `output` 字段写出；`BootstrapContext` 在 `net.minecraft.data.worldgen` 下。

### 坑 2：手写模型的物品必须有 `items/<id>.json`

详见 [learn/22](22-mirror-weapon-white-block-star.md)：26.3 的物品模型是两段式的，手写 3D 模型必须
`generator.declareCustomModelItem(item)` 才会生成 `assets/<ns>/items/<id>.json`，否则游戏里是**黑紫格**。

### 坑 3：`runDatagen` 与 `build` **不能放在同一次 Gradle 调用里**

```bash
./gradlew runDatagen build      # ❌ 报 "implicit dependency" 校验失败
```

原因链条：

- `runDatagen` 依赖 `classes`（生成器跑的是编译后的模组）；
- `sourcesJar` 的输入包含 `src/main/generated`（Loom 把生成目录加进了主源集）；
- 于是"谁先谁后"无法表达：让 `sourcesJar` 依赖 `runDatagen` 会成环（`runDatagen → classes → sourcesJar`）。

处理方式（本项目采用）：

```groovy
// 生成物是数据、不是源码：把它从 sources jar 里排除
tasks.named("sourcesJar") {
	exclude("src/main/generated/**")
}
```

```yaml
# CI 分两步调用（也顺带让"生成的 JSON 与代码不同步"立刻暴露）
./gradlew runDatagen
./gradlew build
```

## 5. 构建流水线里另外两处

| 问题 | 处理 |
| --- | --- |
| `fabric-data-generation-api-v1` 不在编译类路径 | 它是 Fabric API **bundle 里的嵌套模块**，需在 `build.gradle` 显式声明依赖（版本写进 `gradle.properties`） |
| 加内容后忘了跑 datagen | `runDatagen` 必须在 `build` 之前；CI 两步跑就是这条的自动化版本 |

## 6. 产出的清单（本项目现状）

| 产物 | 生成者 |
| --- | --- |
| `assets/<ns>/blockstates/*.json`、`models/block/*.json` | `ModModelProvider` |
| `assets/<ns>/items/*.json`（物品模型定义）、`models/item/*.json`（平面物品） | `ModModelProvider` |
| `assets/<ns>/lang/en_us.json`、`zh_cn.json` | `ModLanguageProvider` |
| `data/<ns>/loot_table/blocks/*.json` | `ModBlockLootProvider` |
| `data/<ns>/tags/item/*.json`、`tags/block/*.json`（含写入原版标签） | `ModTagProvider` |
| 配方（骨架，待第一条配方） | `ModRecipeProvider` |
| **仍为手写**：贴图 PNG、3D 武器模型、伤害类型 JSON、`fabric.mod.json` | — |

## 7. 常见坑

| 现象 | 原因 |
| --- | --- |
| 编译报找不到 `FabricDataOutput` | 该类型在 26.3 已改名为 `FabricPackOutput` |
| 物品黑紫格 | 手写模型缺 `declareCustomModelItem`（→ 没有 `items/<id>.json`） |
| `runDatagen build` 报 implicit dependency | 二者不能同批调用；`sourcesJar` 要排除生成目录，CI 分两步 |
| 模型/贴图时好时坏 | `resources` 与 `generated` 里有同名文件，互相覆盖 |
| 干净检出后 jar 缺语言/模型 | 生成结果没提交 |
| 改了生成文件但没生效 | 改的是 `src/main/generated`（会被覆盖），应改 Provider |

## 8. 小结

- 能推导的东西就别写第二遍：**模型、掉落、标签、语言**都交给生成器，手写只留"真正是艺术/数据"的部分（贴图、3D 模型、伤害类型）；
- 语言文件用"**英文自动 + 中文覆盖**"，新内容零成本接入；
- 生成目录与手写目录**互斥**，转换时要删旧文件；
- 记住三条环境事实：`FabricPackOutput`（改名）、`declareCustomModelItem`（物品定义）、`runDatagen` 与 `build` 分两步（任务依赖无法成立）。
