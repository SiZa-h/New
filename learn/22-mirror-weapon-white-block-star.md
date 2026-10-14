# 22. 镜像武器：白方块之星（外观取反 + 26.3 的「物品模型定义」大坑）

需求：**加一把武器，白方块之星。**

当时的要求是"先不添加技能，创建空模板"。所以这一篇记录两件事：怎样用**一份绘制代码**产出反色的孪生外观，以及 26.3 里让物品不变成黑紫块的那个**必须步骤**。

## 1. 外观：一份画法、两个极性

黑方块之星是"一颗纯白小球 + 8 个环绕的微型黑方块"。白方块之星作为镜像，就是**每个通道取反**：

| | 黑方块之星 | 白方块之星 |
| --- | --- | --- |
| 球体 | 纯白发光（中心亮、边缘淡出） | **黑核 + 白色轮廓** |
| 环绕体 | 微型黑方块 | 微型白方块 |
| 贴图 | `textures/item/black_block_star.png`、`..._block.png` | `textures/item/white_block_star.png`、`..._block.png` |
| 模型 | `models/item/black_block_star.json` | `models/item/white_block_star.json`（**除贴图名外逐字节同构**） |

实现方式是在生成器里加一个 `invert` 开关，而不是复制一份画法：

```js
function starOrb(invert = false) {
	const tone = (value) => (invert ? 255 - value : value);
	canvas.glow(c, c, 8.0, 0.88, 2.3, tone(255), tone(255), tone(255));
	...
}
```

> 取反会**连 alpha 一起翻**，于是"亮球"自然变成"暗核 + 亮边"——这正是想要的效果，不用另写一套光照逻辑。

模型则用一个名字参数复用：

```js
function itemModel(name = 'black_block_star') {
	...
	textures: {
		orb: `new-mod:item/${name}`,
		block: `new-mod:item/${name}_block`,
		particle: `new-mod:item/${name}`,
	},
}
```

**验证方式**：把两个模型的文件名统一替换后逐字节比较，确认结构一致：

```powershell
$a = (Get-Content black_block_star.json -Raw) -replace 'black_block_star','STAR'
$b = (Get-Content white_block_star.json -Raw) -replace 'white_block_star','STAR'
$a -eq $b      # True
```

## 2. 物品数值与修复材料

白星与黑星同数值（耐久 1561 / 挖掘速度 8.0 / 攻击加成 3.0 / 附魔值 10），只有两处不同：

```java
/** 白方块之星：与黑星同数值，但用白方块修复。 */
public static final ToolMaterial WHITE_BLOCK_STAR = new ToolMaterial(
		BlockTags.INCORRECT_FOR_IRON_TOOL, 1561, 8.0F, 3.0F, 10,
		createRepairTag(NewMod.id("white_block")));
```

修复材料是一个**标签**（`new-mod:white_block`），由 `ModTagProvider` 生成。**这个标签必须存在**，哪怕里面只有一项——否则物品加载时会因为未知标签报错。同理还有 `ModDamageTypes.WHITE_BLOCK_STAR` 与它对应 `damage_type/white_block_star.json`（死亡消息在语言文件里）。

## 3. 26.3 的大坑：手写模型的物品**必须**有 `items/<id>.json`

26.3 把物品模型拆成了两层：

| 文件 | 作用 |
| --- | --- |
| `assets/<ns>/items/<id>.json` | **物品模型定义**：这个物品用哪个模型（或什么条件模型） |
| `assets/<ns>/models/item/<id>.json` | 模型本体（几何、贴图、显示变换） |

`items/<id>.json` 有三种来源：

| 情况 | 生成方式 |
| --- | --- |
| 普通物品 / 手持工具 | `generator.generateFlatItem(item, ModelTemplates.FLAT_ITEM)`，定义与模型一起产出 |
| 方块物品 | `createTrivialCube(block)`，自动指向方块模型 |
| **手写模型**（两颗星、黑曜石方的 3D 模型） | **必须 `generator.declareCustomModelItem(item)`** |

```java
generator.declareCustomModelItem(ModItems.BLACK_BLOCK_STAR);
generator.declareCustomModelItem(ModItems.WHITE_BLOCK_STAR);
generator.declareCustomModelItem(ModItems.OBSIDIAN_CUBE);
```

**踩坑记录**：白星最初只在 `generateItemModels` 里"跳过"（注释写着"它有手写模型"），结果**没有任何物品模型定义** → 游戏里显示成**黑紫格**（missing model）。

- 模型 JSON 正确 ✅
- 贴图存在且合法 ✅
- `check-assets.js` 全绿 ✅
- **但物品没有定义** ❌ → 黑紫格

而黑星当年是**手写**了一份 `resources/assets/new-mod/items/black_block_star.json`（初始提交里就有），所以它没事——这类"历史手写文件"最容易被忽略，排查时要专门去 `resources` 里找同名的 `items/*.json`。

修完之后，三份定义都改成由 datagen 生成，并删掉手写副本（两个目录**不能有同名文件**）。

## 4. 空模板怎么留

技能没设计时，物品就是个普通剑：

```java
public static final Item WHITE_BLOCK_STAR = new Item(
		new Item.Properties().sword(ModToolMaterials.WHITE_BLOCK_STAR, 3.0F, -2.4F).setId(WHITE_BLOCK_STAR_KEY));
```

> 26.3 里**没有 `SwordItem` 类**了：剑就是 `Item.Properties.sword(...)`（挖矿/攻击行为由属性里的组件决定）。这一点在旧教程里几乎都是错的。

后续要加技能时的顺序（见 [learn/15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md) 与 `content/README.md`）：
**`PillarKind` 变体 → 每个行为的实体类型 → 触发层判定"是哪颗星"**。

## 5. 常见坑

| 现象 | 原因 |
| --- | --- |
| 物品是**黑紫格** | 缺少 `assets/<ns>/items/<id>.json`（手写模型时忘了 `declareCustomModelItem`） |
| 物品是**平面贴图**而不是 3D | datagen 用 `generateFlatItem` 覆盖了手写模型，或模型定义指向了 `models/item` 之外的地方 |
| 模型烘焙失败、控制台报 atlas | 物品模型引用了**方块图集**的贴图；物品模型只能引用 `textures/item/**`（item 图集） |
| 物品加载报未知标签 | 修复材料标签没有生成（`ModTagProvider` 少一行） |
| 名字显示翻译键 | 忘了跑 `runDatagen`（语言文件由生成器产出） |

## 6. 小结

- **镜像外观用"一份画法 + 极性参数"**，不要复制两份代码；取反连 alpha 一起翻，正好得到"暗核亮边"；
- 26.3 的物品模型是**两段式**：`items/<id>.json`（定义）+ `models/item/<id>.json`（本体）。手写模型必须显式声明，否则就是黑紫格；
- 排查黑紫格时，先确认"物品定义文件存在"，再怀疑模型与贴图——顺序反了会白查很久；
- 样板（空模板）阶段就把**注册、贴图、模型定义、材料、标签、伤害类型、语言**一次配齐，之后加技能只需动触发与实体。
