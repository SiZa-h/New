# 开发笔记（learn）

这里是本模组的开发笔记。每篇都遵循同一套结构：**概念与数据流 → 可运行代码 → 为什么这么写（26.3 API 要点）→ 参数速查 → 常见坑 → 验证方式 → 小结**。

## 约定

> **每次功能改动，都在本目录增补一篇「通俗且深入」的教程。**

- **通俗**：先讲「这东西是什么、数据怎么流动」，再落到代码；不假设读者已经熟悉该模块。
- **深入**：写清 API 的真实签名与调用顺序（例如 `hurtEnemy` 在伤害结算前、`ClientPreAttackCallback` 的 `clickCount` 语义），并解释「为什么不用另一种写法」。
- 文件名用 `NN-english-kebab-case.md` 递增编号；代码块直接引用本仓库的真实代码，尽量保持一致。
- 至少包含一节**验证方式**（`gradlew build` / `runDatagen` / `runClient` 该看什么）与一节**常见坑**。

## 索引

| 编号 | 主题 | 对应代码 |
| --- | --- | --- |
| [01](01-adding-and-registering-an-item.md) | 添加并注册一个新物品（黑方块） | `item/ModItems.java` |
| [02](02-adding-a-sword-item.md) | 添加并注册一个剑类武器（`ToolMaterial` + `Properties.sword`） | `item/ModToolMaterials.java` |
| [03](03-adding-a-placeable-block-and-a-custom-creative-tab.md) | 可放置方块（Block + BlockItem + 掉落表）与自定义物品页 NEW | `block/ModBlocks.java`、`item/ModItemGroups.java` |
| [04](04-creating-a-custom-weapon-class.md) | 自定义武器类与命中特效 | `item/PillarWeaponItem.java` |
| [05](05-left-click-ability-packet-and-custom-entity.md) | 左键技能：客户端事件 → 网络包 → 自定义实体 | `client/`、`network/`、`entity/` |
| [06](06-custom-damage-type-and-knockback.md) | 自定义伤害类型（无视护甲）与「击退到终点」 | `damage/ModDamageTypes.java`、`data/**/damage_type/` |
| [07](07-reverse-extension-and-recoil.md) | 撞墙反向伸长（保证 30 格）与玩家后坐 | `entity/BlackBlockPillarEntity.java` |
| [08](08-breaking-the-weaker-blocked-end.md) | 两头被顶住时按硬度破坏一侧（1 号行为保留基岩保护） | `entity/BlackBlockPillarEntity.java` |
| [09](09-weapon-variants-by-enum.md) | 多武器变体：一个枚举派生出手持/实体外观、伤害、是否破甲 | `item/PillarKind.java`、`item/ModItems.java` |
| [10](10-entity-types-carry-the-differences.md) | 四种柱子实体类型：用 `PillarSpec` 承载差异，免去同步字段 | `entity/ModEntities.java` |
| [11](11-right-click-long-pillar.md) | 右键技能：生成即满长的「长黑方块柱」（尖端向底端填充） | `network/ModNetworking.java` |
| [12](12-independent-skill-cooldowns.md) | 技能冷却的独立计算（不依赖原版物品冷却） | `item/WeaponCooldowns.java` |
| [13](13-pillar-collision-explosion-blackhole.md) | 两柱相撞：爆炸与黑洞禁锢 | `entity/BlackHoleEntity.java` |
| [15](15-black-hole-axes-pair-pillars-and-mouse-gestures.md) | 黑洞伴生轴、对撞柱与鼠标手势 | `entity/BlankAxisEntity.java`、`client/GestureInput.java` |
| [16](16-six-cooldown-slots-and-mark-barrage.md) | 六格独立冷却 + 集齐三层标记触发弹幕 | `entity/PillarBarrageEntity.java` |
| [17](17-settings-screen-for-world-breaking-toggles.md) | 设置页：基岩 / 爆炸破坏开关（含 26.3 按键与界面 API 变化） | `client/ModSettingsScreen.java` |
| [18](18-explosion-knockback-barrage-and-sneak-skills.md) | 不给速度的爆炸、击退冲突、吸向玩家的弹幕与两个潜行技能 | `effect/Explosions.java`、`effect/Corruption.java` |
| [19](19-view-angle-gate.md) | 触发门禁的历史：一次「视角移动量」尝试，以及它为什么被**双击 + 待命窗口**取代 | 现由 `client/GestureInput.java` 承担 |
| [20](20-drawing-textures-and-3d-item-models.md) | 自绘贴图、3D 物品模型与「手写 PNG」（纯白梦幻星 + 奇异图标） | `tools/gen-assets.js` |
| [21](21-inverted-block-white-block.md) | 反色方块：白方块（严格取反的贴图 + 一行生成模型） | `content/block/ModBlocks.java`、`tools/gen-block-textures.js` |
| [22](22-mirror-weapon-white-block-star.md) | 镜像武器：白方块之星（一份画法两个极性）与 **26.3 物品模型定义**这个坑 | `content/item/ModItems.java`、`tools/gen-assets.js` |
| [23](23-flying-pillar-white-block-pillar.md) | 飞行型柱子：白方块柱（0.1 格粗细、碰墙即停、残留一秒） | `content/weapon/entity/WhitePillarEntity.java` |
| [24](24-marks-and-decay.md) | 黑白标记与「消散」：跨武器状态 + 一个自带时钟的 buff | `content/effect/Marks.java`、`Decay.java`、`enchant/ModEffects.java` |
| [25](25-renaming-packages-and-content-skeleton.md) | 目录重组与内容骨架（含批量改包时 BOM / 乱码 / 正则误伤三个坑） | `common/SimpleRegistry.java`、`content/**` |
| [26](26-data-generation-and-build-pipeline.md) | 数据生成与构建流水线：让模型/掉落/标签/语言都由代码产出 | `datagen/**`、`build.gradle`、`.github/workflows/build.yml` |
| [27](27-white-star-input-hold-and-offhand.md) | 白星的输入层：按住技能、潜行优先、把生物钉在视线上、副手连击 | `client/input/WhiteStarInput.java`、`content/weapon/{WhiteStarFire,PinTargets,OffhandWhiteStar}.java` |

> **编号说明**：没有 `14` —— 那一篇对应的功能后来被整体回滚，文档一并删除；编号不复用，免得外部链接指错。

> **暂未单独成篇的一块**：打包/发布流程（PowerShell 打 zip、可分发 jar、CI 两步构建）写在
> [26](26-data-generation-and-build-pipeline.md) 的构建流水线一节里。

## 代码地图（整理后的目录）

```
src/main/java/com/sizah/
├── common/                  跨内容的公共设施
│   ├── NewMod.java          模组入口：注册 + 每 tick 结算（击退冲突 / 消散计时 / 白星按住技能 / 感染）
│   ├── SimpleRegistry.java  「加一个方块/物品」的两个一行式注册助手
│   ├── ModTags.java         本模组自己的标签（修复材料、矿石组）
│   ├── config/Settings.java 服务端开关（基岩 / 爆炸破坏）
│   ├── damage/              自定义伤害类型（柱子 / 黑曜石 / 白星 / 消散）
│   └── network/             C→S 包与全部技能的服务端权威实现（ModNetworking + 5 个 payload）
├── content/                 按内容类型分家（详见 content/README.md）
│   ├── ModCreativeTabs.java 本模组的创造标签页
│   ├── block/               方块 + OreFamilies（"一种材料十个方块"的属性工厂）
│   ├── item/                普通物品与材料（含白方块的物品形态）
│   ├── weapon/              武器本体、变体表、独立冷却、白星的按住/副手实现
│   │                        （WhiteStarFire / PinTargets / OffhandWhiteStar）
│   │   └── entity/          柱子（黑柱 BlackBlockPillarEntity、白柱 WhitePillarEntity）、
│   │                        空白轴、ModEntities 的 PillarSpec 表
│   ├── tool/ armor/ food/   工具 / 盔甲 / 食物（骨架，register() 待填）
│   ├── mob/ projectile/ machine/  生物 / 投掷物 / 机器（骨架）
│   ├── world/ enchant/      世界生成（骨架）/ 状态效果 ModEffects（标记图标 + 消散）
│   └── effect/              行为层：Marks、Decay、Explosions、PushConflicts、Corruption
│       └── entity/          效果型实体：黑洞、弹幕、标记禁锢
├── client/                  客户端：入口 NewModClient、input/（GestureInput、WhiteStarInput）、
│                            render/（黑柱/白柱渲染器、不可见渲染器）、settings/
└── datagen/                 数据生成：模型与方块状态、双语 lang、掉落表、配方、标签
    └── NewModDataGenerator  `./gradlew runDatagen` 的入口

src/main/generated/          datagen 产物（模型/掉落/标签/语言/物品定义），不要手改
src/main/resources/          手写资产：贴图、3D 武器模型、伤害类型 JSON、fabric.mod.json

tools/
├── gen-assets.js            美术资产生成器：纯 Node 手写 PNG + 生成 3D 物品模型（黑/白星两个极性）
├── gen-block-textures.js    纯色方块贴图生成器（白方块 = 黑方块逐通道取反）
└── check-assets.js          资产自检：模型/贴图/图集规则/纯白校验
                             `node tools/gen-assets.js`（加 `--preview <目录>` 出放大预览）
```

## 版本基线

本笔记对应 Minecraft **26.3** + Fabric Loader 0.19.5 + Fabric API 0.161.0+26.3 + Java 25。26.3 的若干签名与旧教程不同，照抄网上的 1.20/1.21 例子容易编译不过，以本仓库代码与源码反编译结果为准。

**已确认的 26.3 API 事实**（本项目逐个踩过）：

| 主题 | 26.3 的写法 |
| --- | --- |
| 资源标识 | `Identifier`（不再是 `ResourceLocation`） |
| 方块 / 物品属性 | 必须 `.setId(ResourceKey)`（注册名写进组件） |
| 剑与工具 | **没有 `SwordItem` 类**：`Item.Properties.sword(material, damage, speed)` / `.pickaxe(...)` |
| 伤害 | `LivingEntity#hurtServer(ServerLevel, DamageSource, float)`；速度同步用 `entity.syncVelocity = true`（没有 `hurtMarked`） |
| 实体标签 | `entity.entityTags()`（不是 `getTags()`） |
| 方块属性 | `noCollision()`（不是 `noCollission`）；`PushReaction` 只有 `PUSH_PULL/PUSH/POPPED/IMMOVEABLE/IGNORE_ENTITY` |
| 渲染 | render state 模式（`EntityRenderer<T, S>` + `createRenderState` / `extractRenderState` / `submit`），`PoseStack#mulPose` 只接受矩阵或 `Transformation` |
| 物品模型 | **两段式**：`assets/<ns>/items/<id>.json`（定义）+ `models/item/<id>.json`（本体）；手写模型必须 `declareCustomModelItem` |
| 数据生成 | `FabricPackOutput`（不再是 `FabricDataOutput`）；`RecipeProvider` 是实例对象，`buildRecipes()` 无参 |
| 屏幕 / 界面 | `client.gui.screen()`；按键用 `KeyMappingHelper` 注册 |
| 换弹匣式的属性组件 | 物品行为（食物、工具、护甲）通过 `Item.Properties` 上的组件表达，而不是各自的 Item 子类 |
