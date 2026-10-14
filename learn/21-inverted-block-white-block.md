# 21. 反色方块：白方块（严格取反的贴图 + 一行生成模型）

需求一句话：**做一个白方块，颜色正好和黑方块相反。**

听上去只是"换个颜色"，但这里有两个值得写下来的决定：什么叫"正好相反"，以及"方块加进来之后，模型/掉落/名字从哪来"。

## 1. 先把黑方块量化，再取反

黑方块的贴图只有**两个颜色**，各占一片区域（16×16）：

| 区域 | 像素数 | 黑方块 | 白方块（取反） |
| --- | --- | --- | --- |
| 内部 14×14 | 196 | `#1C1C20` = (28,28,32) | `#E3E3DF` = (227,227,223) |
| 外框 1 像素 | 60 | `#121214` = (18,18,20) | `#EDEDEB` = (237,237,235) |

所以"正好相反"= **每个颜色通道做 `255 - x`**。

```js
/** The inverse of a colour: each channel mirrored around 128, which is exactly `255 - value`. */
function inverse(rgb) {
	return rgb.map((channel) => 255 - channel);
}
```

**为什么不是"白底黑框"？** 那样只是"另一个颜色"，不是相反。取反之后有个意外的收获：黑方块偏蓝（蓝通道比红绿高 4），取反后白方块**蓝通道比红绿低 4**，两者仍然是一对中性色，白方块不会发黄。

> 注意：`255 - x` 是"数值相反"，不是"视觉反色滤镜"（那会连色相一起翻）。对只有灰阶的图案来说两者一致，所以这里不用纠结。

## 2. 贴图交给脚本，不手动画

新增 `tools/gen-block-textures.js`：纯 Node 手写 PNG（与 `gen-assets.js` 同一套 `crc32/chunk/writePNG`），把"纯色方块 = 内部色 + 1 像素边框"这个图案描述一次：

```js
const BLACK_INTERIOR = [28, 28, 32];
const BLACK_BORDER = [18, 18, 20];

const TILES = {
	black_block: borderedTile(BLACK_INTERIOR, BLACK_BORDER),
	white_block: borderedTile(inverse(BLACK_INTERIOR), inverse(BLACK_BORDER))
};
```

好处有两个：以后加纯色方块只改一份 `TILES`；黑方块重画之后，取反**自动**跟着变（不会出现"白方块还是旧色调"）。

> 脚本重跑会重写黑方块 PNG。字节可能不同（deflate 压缩级别），所以**验证方式是比对像素**，不是比对文件：
> 本项目实测重写后逐像素完全一致（140 → 90 字节只是压得更紧）。

## 3. 方块与物品：一行各一处

```java
// content/block/ModBlocks.java
public static final Block WHITE_BLOCK = new Block(
		BlockBehaviour.Properties.of()
				.setId(WHITE_BLOCK_KEY)
				.mapColor(MapColor.SNOW)        // 黑方块是 COLOR_BLACK，这里换到色阶另一端
				.strength(1.5F, 6.0F)           // 与黑方块完全一致
				.sound(SoundType.STONE));
```

```java
// content/item/ModItems.java —— useBlockDescriptionPrefix() 让它复用方块的语言键
public static final Item WHITE_BLOCK = new BlockItem(ModBlocks.WHITE_BLOCK,
		new Item.Properties().useBlockDescriptionPrefix().setId(WHITE_BLOCK_KEY));
```

**注册名一旦发布就不能改**（存档里写的是它），所以 `white_block` 这个 id 定下来就别再动。

## 4. 剩下的全自动

| 需要的东西 | 谁产出 |
| --- | --- |
| 方块状态 + 方块模型 + 物品模型 | `ModModelProvider`：`generator.createTrivialCube(ModBlocks.WHITE_BLOCK)` |
| 掉落（掉自己） | `ModBlockLootProvider`：`this.dropSelf(ModBlocks.WHITE_BLOCK)` |
| 中英名字 | `ModLanguageProvider`：英文自动得到 "White Block"，中文在 `CHINESE` 表加一行 `白方块` |
| 创造标签页 | `ModCreativeTabs` 加一行 `output.accept(...)` |

也就是说：**新方块没有手写任何 JSON**，`./gradlew runDatagen` 之后 `src/main/generated` 里就齐了。

`createTrivialCube` 是"一次调用 = 方块状态 + 方块模型 + 物品模型"的那个方法，贴图按 id 去找 `textures/block/<id>.png`。

## 5. 常见坑

| 现象 | 原因 |
| --- | --- |
| 白方块比黑方块"亮得脏" | 没取反，只是随手选了白色；应当**从黑方块的常量取反**，两边永远成对 |
| 游戏里叫 `block.new-mod.white_block` | 忘了跑 `runDatagen`（或生成文件没提交） |
| 紫黑格 | 贴图路径不对（方块必须在 `textures/block/`，物品在 `textures/item/`） |
| 挖了不掉落 | `ModBlockLootProvider` 里没有 `dropSelf` |
| id 冲突崩溃 | 同一个 `white_block` 被注册了两次（例如脚本与代码各注册一次） |

## 6. 小结

- **"相反"要先量化再取反**：把黑方块的常量写下来，用 `255 - x` 派生白方块，而不是凭感觉选色；
- 图案（内部 + 边框）与颜色分离：图案一份、颜色两套，重画时两边同步；
- 方块进注册表之后，**模型/掉落/名字/标签页都应该由生成器产出**，手写 JSON 越少越好；
- 纯色方块贴图交给脚本，比在图像编辑器里点出来更可复现、也更容易批量扩展。
