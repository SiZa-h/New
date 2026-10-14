# 20. 自绘贴图、3D 物品模型与「手写 PNG」

需求三件：

1. **黑方块之星的新模型**：纯白配色、充满梦幻感；
2. **新的模组图标**：要奇异；
3. （追加）模型改成「**一个小球，悬在手上，周边有微小黑方块环绕**」；「动态」部分试过粒子方案后**已按需求撤掉**（见第 6 节与第 12 节）。

最后交出来的东西，全部由 `tools/gen-assets.js` **一个脚本生成**（可重复、可微调）：

| 文件 | 尺寸 | 内容 |
| --- | --- | --- |
| `textures/item/black_block_star.png` | 16×16 | 纯白发光小球（球面高光 + 极淡四角星芒），贴在球体的 5 片上 |
| `textures/item/black_block_star_block.png` | 16×16 | 环绕方块用的**微黑方块**（必须放 item 图集，见第 12 节复盘） |
| `models/item/black_block_star.json` | — | 3D 模型：**体素球 5 片 + 环绕的微小黑方块 8 个**（13 个部件） |
| `icon.png` | 128×128 | 模组图标：黑方块 + 白星 + 竖瞳 + 光环（奇异风） |

## 1. 为什么「纯白」用 alpha 表达

需求是**纯白配色**。那就不该用「灰一点 / 蓝一点」去表现层次 —— 那会破坏纯白。
正确的做法是：**RGB 永远是 (255,255,255)，只让 alpha 变化**。

```js
// 白色柔光球：中心实、边缘平滑消失；power 越大衰减越急
glow(cx, cy, radius, alpha, power = 2.2) {
	this.shade(cx, cy, radius, alpha, (t) => (t > 1 ? 0 : Math.pow(1 - t, power)));
}
```

于是「梦幻」全部来自柔和的透明度渐变：贴图本身是白的，叠在一起是层层发光。

## 2. 手写 PNG：不用任何图像库

PNG 的结构其实很简单，Node 自带 `zlib` 就够：

```
89 50 4E 47 0D 0A 1A 0A      ← 8 字节签名
IHDR                          ← 宽、高、位深(8)、颜色类型(6 = RGBA)
IDAT                          ← zlib 压缩的扫描线；每行前面加 1 字节 filter(0 = None)
IEND
```

每个 chunk 的格式都一样：**长度(4 字节, 大端) + 类型(4 字节 ASCII) + 数据 + CRC32(4 字节)**。

```js
function writePNG(file, width, height, rgba) {
	const header = Buffer.alloc(13);
	header.writeUInt32BE(width, 0);
	header.writeUInt32BE(height, 4);
	header[8] = 8;      // 位深
	header[9] = 6;      // RGBA

	const stride = width * 4;
	const raw = Buffer.alloc((stride + 1) * height);

	for (let y = 0; y < height; y++) {
		raw[y * (stride + 1)] = 0;      // filter：0 = None
		rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
	}

	const png = Buffer.concat([
		Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
		chunk('IHDR', header),
		chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
		chunk('IEND', Buffer.alloc(0)),
	]);

	fs.writeFileSync(file, png);
}
```

`chunk()` 负责加长度与 CRC32（CRC32 用一个 256 项的查表实现，见脚本里的 `CRC_TABLE`）。

> 好处：改颜色/形状 = 改几个数字，`node tools/gen-assets.js` 重跑一遍即可，
> 不会出现「这张图是谁用什么软件画的」这种断档。

## 3. 把「形状」写成数学

**星形**：半径随角度起伏的边界函数 —— 尖角数 `points`、锐度 `sharpness`：

```js
const wave = Math.pow(Math.abs(Math.cos(points * angle / 2)), sharpness);
const boundary = (inner + (outer - inner) * wave) / outer;
return t <= boundary ? 1 : 0;
```

`points = 4, sharpness = 8` 就是四道细长星芒；`points = 8, sharpness = 2.4` 是柔和的副星。

**抗锯齿**：每个像素取 4×4 个子样本求命中比例，边缘自然柔和 —— 16×16 的贴图没有抗锯齿会像刀切的一样硬。

```js
for (let sy = 0; sy < 4; sy++) {
	for (let sx = 0; sx < 4; sx++) { /* 采样 */ }
}
this.blend(x, y, r, g, b, alpha * hits / 16);
```

## 4. 一个真实踩过的坑：白底看白图

第一版预览我直接把贴图放大 16 倍输出，结果**什么都看不出来** —— 白色 alpha 叠在查看器的白色背景上，
和「整个方块都是白的」长得一模一样，我还因此误判贴图是「一个方疙瘩」。

正确做法是**合成到深色棋盘上**再放大：

```js
const bg = ((Math.floor(x / cell) + Math.floor(y / cell)) % 2 === 0) ? 46 : 78;
out.blend(x, y, bg, bg, bg + 12, 1);      // 先铺底
out.blend(x, y, r, g, b, a / 255);        // 再叠贴图
```

透明区域显示出棋盘、不透明区域是白色 —— 形状一目了然。**检查带 alpha 的贴图，必须给个底色。**

## 5. 3D 物品模型：一颗悬在手上的小球 + 一圈环绕的小黑方块

造型需求：**一个小球，悬在手上，周边有微小黑方块环绕**（需求原文含「动态」，那部分试过粒子方案后已撤掉，见第 6、12 节）。模型 JSON 由脚本里的两个数组描述：

```js
/** 体素球：5 片，[from, to, 额外露出的面]。 */
const BALL_SLICES = [
	{ from: [6.5, 4.5, 6.5], to: [9.5, 5.5, 9.5], extra: ['down'] },   // 3 像素宽
	{ from: [5.5, 5.5, 5.5], to: [10.5, 6.5, 10.5], extra: [] },       // 5 像素宽
	{ from: [4.5, 6.5, 4.5], to: [11.5, 9.5, 11.5], extra: [] },       // 7 像素宽（中间 3 像素高，合并成一片）
	{ from: [5.5, 9.5, 5.5], to: [10.5, 10.5, 10.5], extra: [] },
	{ from: [6.5, 10.5, 6.5], to: [9.5, 11.5, 9.5], extra: ['up'] },
];
```

四个要点：

1. **球用体素球拼**（下→上 3/5/7/5/3 像素宽），而且**只写露在外面的面**：
   相邻两片的接触面互相遮挡，写出来只会共面闪烁 —— 尤其是我们的贴图带 alpha，能看进球体内部。
   同宽的连续几片**合并成一片**（这里是中间 3 像素高的那层），同样是为了消灭共面；
2. **`shade: false`**：原版按面朝向压暗（朝上的面最亮、侧面次之），对「纯白发光」是毁灭性的，
   关掉之后所有面都是贴图原本的亮度；
3. **旋转只能绕单轴、角度必须是 22.5 的倍数且 |角度| ≤ 45**：环绕的 8 个小方块就是各自绕 X/Y/Z 转 ±22.5°，
   看起来像正在翻滚的碎块；
4. **别继承 `item/generated` / `item/handheld`**：那两个父模型会触发原版的「平面贴图生成器」，
   于是 3D 模型上会再叠一张平面贴图，而且会叠在正中间。代价是 `display`（手持/掉落/GUI 的变换）得自己写全。

「**悬在手上**」是靠 `display` 的 `translation` 做的：把 `+Y` 抬起来（这里 5.6 ~ 7）、再缩小（0.52 ~ 0.62），
球就浮在掌心上方一段距离，而不是贴着手：

```json
"firstperson_righthand": { "rotation": [0, -90, 15], "translation": [1.13, 5.6, 1.13], "scale": [0.52, 0.52, 0.52] }
```

模型也是脚本生成的：改形状就改 `BALL_SLICES` / `ORBIT_*` 数组，重跑脚本。

## 6. 怎么让物品「动」起来（26.3 的现实）

需求里的「**动态**环绕」踩到了 26.3 的一个硬边界，值得单独记一笔（这一节的调查结论**现在仍然有效**）。

**先说结论**：想让**模型自身**逐帧旋转，只有两条路 —— 上 Mixin 改原版的映射表，
或者用 Fabric 的 `ModelModifier.AfterBakeItem` 自己产出逐帧几何。

我第一版走的是第三条路：**模型静态造型 + 粒子真实运动**（手持时每 tick 沿圆周生成黑方块粒子）。
它确实能转，但**进游戏试过之后不符合预期，已按需求整体撤掉**（代码与注册都删了）——
粒子终究是另一套系统，位置与观感很难和手上那颗球严丝合缝。现在模型只保留静态的一圈方块。
下面是当时那轮调查的记录，想做「真·模型动画」时照着来：

调查过程（都可以用 `javap` 在反混淆 jar 上复现）：

```powershell
javap -p -classpath minecraft-merged-deobf-26.3.jar net.minecraft.client.renderer.item.ItemModels
#   private static final ExtraCodecs$LateBoundIdMapper<...> ID_MAPPER;   ← 硬编码，不是 Registry

javap -c -p ... net.minecraft.client.renderer.special.SpecialModelRenderers
#   静态块里一串 ID_MAPPER.put(minecraft:bell/banner/book/conduit/chest/...)，没有注册入口
```

- **物品模型类型**（`empty` / `model` / `range_dispatch` / `special` / `composite` / `select` / `condition` …）
  与 **special 渲染器类型**（`bell` / `banner` / `chest` / `trident` …）都是
  `LateBoundIdMapper`，在静态初始化里写死；`SpecialModelRenderers` 只暴露一个 `CODEC` 与 `bootstrap()`，
  **没有** `Registry` 那样的注册入口；Fabric API 里也没有对应 hook（我翻了 47 个 API jar，
  与物品模型相关的只有 `CuboidItemModelWrapperMixin`，那是别的事）。
- 那用**数据驱动**呢？`range_dispatch` + `minecraft:time` 确实能按时间切换模型（原版钟表就是这么做），
  但 `time` 的三个来源是 `random` / `daytime` / `moon_phase` —— `daytime` 是 **24000 tick（20 分钟）**一个循环，
  做「转一圈」太慢；`random` 又会让模型逐帧乱跳。`cooldown` / `use_cycle` / `crossbow/pull` 只在特定状态下才有值。

所以现状是：**模型只负责静态造型**（球 5 片 + 环绕 8 块，见第 5 节），
真想让它转，得走上面那两条路。

## 7. 不跑游戏也能检查模型：等轴测预览

改完模型最怕「进游戏才发现摆错了」。我的办法是**自己画一张等轴测投影图**：
把每个 box 的 8 个角点按 `sx = (x - z) * cos30, sy = (x + z) * sin30 - y` 投到 2D，
按深度排序（远的先画），再填出顶面 / 南面 / 东面三个可见面：

```js
boxes.sort((a, b) => a.depth - b.depth);      // 远的先画，近的盖上去
canvas.polygon([corner(x0, y1, z0), corner(x1, y1, z0), corner(x1, y1, z1), corner(x0, y1, z1)], 1, base, base, base);
```

配合 `--preview` 一起输出，一眼就能看出「球在中间、方块是否均匀绕了一圈」。这一版的预览图确实帮我确认了布局。

## 8. 奇异风的图标怎么画的

128×128，纯程序化，从下往上叠八层：

| 层 | 做法 |
| --- | --- |
| 1 | 深空底：中心微亮的黑 + 90 颗极淡星尘 |
| 2 | 两条**细长的斜光环**（半径带正弦扰动，所以「不太对劲」） |
| 3 | 中央黑方块：点内测试多边形填充 + 1 像素白描边，只留 3 像素的歪斜 |
| 4 | 方块正面一颗大白星（四角、锐度 8）+ 一层薄副星 + 炽白星心 |
| 5 | **竖瞳**：把星心挖成一条中间粗两头细的黑缝 —— 奇异感的关键（星像一只眼） |
| 6 | 六条短裂纹：从星芒外端随机游走出折线，越远越淡 |
| 7 | 22 个漂浮碎片：绕中心散开的小白点与小方块 |
| 8 | 暗角：把视线收回中央 |

> 经验：图标最终会缩到 32×32 甚至更小显示，**主体必须一眼可辨**。
> 第一版我把光环画得很粗、裂纹又多又长，缩略图就是一团白噪声；
> 把光环调细调暗、裂纹减到 6 条变短之后，「黑方块 + 白星 + 竖瞳」才立得住。

## 9. 常见坑

| 现象 | 原因 |
| --- | --- |
| 贴图在游戏里是黑块 | PNG 颜色类型/位深写错（要用 8 位、类型 6 = RGBA） |
| 文件打不开 | 忘了给每个 chunk 写 CRC32，或长度没写大端 |
| 白贴图看不出形状 | 预览没铺底色（见第 4 节）；游戏里则要看是否被 `shade` 压暗 |
| 3D 模型上多了一张平面图 | 继承了 `item/generated` / `item/handheld` |
| 模型旋转不生效/报错 | 角度不是 22.5 的倍数，或一次绕了两根轴 |
| 球体内部一闪一闪 | 写了被遮挡的内部面（相邻体素共面）→ 只留露在外面的面 |
| 16×16 贴图边缘很硬 | 没做超采样抗锯齿 |
| 想给物品模型做逐帧动画却找不到接口 | 26.3 的物品模型类型与 special 渲染器都是硬编码 `LateBoundIdMapper`，Fabric 也没 hook → 上 Mixin 或 `ModelModifier.AfterBakeItem`（见第 6 节） |
| **物品在游戏里是个黑紫色的方块** | 模型烘焙失败（回退成 missing model）。**十有八九是图集混用**：见第 12 节的复盘 —— 物品模型只允许用一张图集，别引用 `textures/block/*` |
| 手绘图与脚本生成的不一致 | 资产要**只认脚本**：改了就在脚本里改，别手改产物 |

## 10. 调参速查

| 想要的效果 | 改哪里 |
| --- | --- |
| 小球更亮 / 高光更硬 | `starOrb()` 里的 `glow(...)` 的 alpha 与 `power` |
| 球心那层四角星芒更明显 | `starOrb()` 末行的 `star(c, c, 5.4, 0.8, 4, 9.0, 0.15)` |
| 球的片数与直径 | `tools/gen-assets.js` 的 `BALL_SLICES` 数组（下→上 3/5/7/5/3） |
| 环绕方块的数量 / 半径 / 高低 / 自转 | `ORBIT_COUNT` / `ORBIT_RADIUS` / `ORBIT_HIGH_Y`、`ORBIT_LOW_Y` / 生成循环里的 `22.5` |
| 悬浮高度与大小（悬在手上） | 模型 `display` 里的 `translation[1]` 与 `scale` |
| 重新生成全部资产 | `node tools/gen-assets.js`；加 `--preview <目录>` 还会输出贴图放大预览与**等轴测模型预览**（默认写 `tools/preview/`，已在 .gitignore 里） |
| 生成后自检 | `node tools/check-assets.js` —— 查模型部件/旋转/贴图引用/球与环绕方块数量、PNG 尺寸与颜色类型、「纯白贴图里有没有非白像素」，以及**物品模型的图集安全**（第 12 节那条硬规则） |

## 11. 小结

- **纯白 = RGB 不动、只动 alpha**；梦幻感来自柔和的透明度层次。
- **PNG 可以手写**：签名 + IHDR + IDAT(zlib) + IEND，每个 chunk 带 CRC32 —— Node 自带 zlib 就够。
- **形状写成数学**：半径随角度起伏的边界函数能画出任意角数的星；4×4 超采样负责柔边。
- **检查 alpha 贴图一定要铺底色**，否则白底白图等于没看。
- **3D 物品模型**：体素球只留外露面、`shade: false` 保纯白、旋转只能单轴 ±45、别继承 `item/generated`、
  用 `display` 的 `translation` +Y 做出「悬在手上」。
- **让物品动起来要先查接口**：26.3 的物品模型类型/special 渲染器是硬编码映射表，数据驱动的 `time` 又是 20 分钟一圈；
  想逐帧动画只能上 Mixin / `ModelModifier.AfterBakeItem`（用粒子绕行的方案试过，观感不符合预期，已撤掉）。
- **不跑游戏也能验模型**：自己写个等轴测投影预览，摆位对不对一眼可见。
- **资产脚本化**：图与模型都由一个脚本产出，改参数重跑即可，避免「不知道这张图怎么来的」。

## 12. 复盘：一次真实的「黑紫块」排查

第一次把这套模型装上后，进游戏看到的是**一团黑紫色的方块** —— 这就是原版的「缺失模型 / 缺失贴图」花纹。
整个过程值得完整记一遍，因为**结论是一条必须记住的硬规则**。

### 12.1 先读日志，别猜

排查的第一站永远是**游戏日志**（`run/logs/latest.log`）。一搜就找到了确切异常：

```
[15:02:34] [Worker-Main-14/WARN] (Minecraft) Unable to bake item model: 'new-mod:black_block_star'
java.lang.IllegalStateException: Multiple atlases used in model,
  expected minecraft:textures/atlas/items.png, but also got minecraft:textures/atlas/blocks.png
	at net.minecraft.client.renderer.item.CuboidItemModelWrapper.validateAtlasUsage(CuboidItemModelWrapper.java:117)
	at net.minecraft.client.renderer.item.CuboidItemModelWrapper$Unbaked.bake(CuboidItemModelWrapper.java:154)
```

**一次就定位了**：模型烘焙时抛异常 → 物品回退成 missing model（黑紫）→ 而且日志里只有这一处失败，说明别的物品模型都是好的。

### 12.2 规则：一个物品模型只能用**一张图集**

26.3 的资源系统把贴图分成多张图集（atlas）：`blocks.png`、`items.png`、`particles.png`……，
而 `CuboidItemModelWrapper#validateAtlasUsage` 会校验「**这个物品模型用到的精灵是否只来自一张图集**」。

我犯的错是：环绕的小黑方块，我直接复用了方块的贴图 `new-mod:block/black_block` ——
它属于 **blocks 图集**；而球体用的 `new-mod:item/black_block_star` 属于 **items 图集**。
一个模型跨了两张图集 → 直接抛异常。

修法很简单：**给物品单独做一张贴图**，放在 `textures/item/` 下：

```js
textures: {
	orb: 'new-mod:item/black_block_star',            // 球
	block: 'new-mod:item/black_block_star_block',    // 环绕方块（新做的 item 图集贴图）
	particle: 'new-mod:item/black_block_star',
},
```

### 12.3 反过来说，「parent 指向方块模型」是允许的

同一个仓库里的黑方块物品用的是：

```json
{ "parent": "new-mod:block/black_block" }
```

它引用的当然是方块贴图，但**它没有报错** —— 因为「只用 parent 指向一个方块模型」的物品走的是
**方块模型那条渲染路径**（整张图都是 blocks 图集，单图集，天然合法）。
被校验的是那种「自带 `textures` + `elements`」的**立方体物品模型**。

### 12.4 顺手把这条规则变成自动检查

人的记性靠不住，所以 `tools/check-assets.js` 现在会：

- 扫 `models/item/**`，对「自带 textures + elements」的模型，**逐个校验它引用的贴图是否都在 `item/` 下**；
- 顺便确认这些贴图**文件真的存在**（防拼错路径）。

这条检查如果早就有，这次的黑紫块根本不会出现在游戏里。

### 12.5 教训

1. **「黑紫块」= 模型没烘出来**，第一站是日志，不是猜；
2. **物品模型只能用一张图集**：要用方块的外观，就为物品单独做一份 item 贴图，别去引用 `textures/block/*`；
3. **纯 parent 指向方块模型的物品不受这条限制**（它走方块路径）；
4. 把踩过的坑**写成自动检查**，比写进文档更可靠。
