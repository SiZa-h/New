# New Mod

> English version: [`README.md`](README.md)

一个 Fabric 模组（Minecraft 26.3），添加可放置的「黑方块」与它的剑类武器「黑方块之星」，并自带一个名为 **NEW** 的创造模式物品页。

## 内容

| ID | 名称 | 说明 |
| --- | --- | --- |
| `new-mod:black_block` | 黑方块 | 可放置方块（物品形态为 `BlockItem`），硬度 1.5、爆炸抗性 6.0、石头音效，徒手破坏也会掉落自身 |
| `new-mod:black_block_star` | 黑方块之星 | 柱子武器，共用武器类 [`PillarWeaponItem`](src/main/java/com/sizah/item/PillarWeaponItem.java)：**左键**发射短黑方块柱、**右键**发射长黑方块柱（与短柱同一生成逻辑，生成即满长、尖端向底端填充）；命中使目标陷入黑暗 5 秒，对黑暗中的目标额外造成 2 点伤害 |
| `new-mod:obsidian_cube` | 黑曜石方 | 同上的柱子武器，**只在外观与伤害上不同**：手持与柱子都是黑曜石材质，触碰伤害 4 点且**受护甲减免** |
| `new-mod:new` | NEW 物品页 | 自定义创造模式物品页，承载本模组的全部物品 |
| `new-mod:short_black_block_pillar` / `new-mod:obsidian_pillar` | 短黑方块柱 / 黑曜石柱（实体） | 两种武器各自发射的柱子，**行为完全相同**：沿发射方向以 30 格/秒伸长，**总长保证 30 格**：前端撞到实体方块就反向伸长（后端后退），**两头都被顶住时在两端面中点 0.75 格半径的球内瞬间破坏最软的方块**（两端硬度相同则破尖端侧；基岩等不可破坏方块永不破坏；破坏的方块**正常掉落**）；**发射者碰到柱子时被推开「剩余距离」**（后坐期间无重力、结束速度 ×0.1、不造成伤害）；长度满 30 后尖端固定、后端前移消散。不受重力、不是弹射物。碰到的生物会受到伤害（**黑方块之星 6 点无视护甲 / 黑曜石方 4 点受护甲减免**）并被以**与伸长同速**的击退推向前端终点 |
| `new-mod:long_black_block_pillar` | 长黑方块柱（实体） | 黑方块之星**右键**发射：**与短柱同一条生成逻辑**（同起点、同朝向），但**生成即满长 60 格**（先是「透明、无功能」的实体），随后黑方块与功能一起由**尖端向底端**以 **60 格/秒**填充（填到哪破坏到哪、打到哪，**连基岩一起破**），填满后再由**尖端向底端**同速消散；触碰生物造成无视护甲伤害并**向着玩家（底端）**击退，**连玩家（含发射者）一起击退但不伤害发射者** |
| `new-mod:eternal_black_block_pillar` | 永黑方块柱（实体） | **备用类型**（暂无武器发射，可用 `/summon` 试）：长满 30 格后**不消散** |
| `new-mod:black_hole` | 黑洞（实体） | **只有 1 号 + 2 号行为**（左键短柱 × 右键长柱）的爆炸会在碰撞点生成它：把爆炸波及的生物**按回碰撞点**（速度清零 + 每 tick 定位）禁锢 **1 秒**；对撞柱互撞、弹幕收尾的爆炸都**不生** |
| `new-mod:mark_lock` | 标记禁锢（实体） | **标记 1** 的附带效果：跟着被标记的生物 **5 秒**，每 tick **只清除它的所有方向速度**（不固定位置，所以仍会被爆炸/柱子推动），到点自动解除 |
| `new-mod:horizontal_axis` / `new-mod:vertical_axis` | 水平 / 竖直空白轴（实体） | 在**被打上标记 1 的生物身上**生成（以其身体中心为中点），各 **20 格**、垂直于玩家视线（水平轴取玩家左手方向，竖直轴取垂直于视线的上方向），并**跟随这只生物移动**；本身**不可见、无任何功能**，只给鼠标手势提供端点，10 秒后消失 |
| `new-mod:pillar_barrage` | 黑方块柱弹幕（实体） | 生物**集齐三层标记**（1+2+3）时触发：跟着目标 **40 秒**，每 **0.15 秒**从**四面八方**（球面随机，三根一循环，1↔2 ≥ 60°、1↔3 ≥ 100°）放一根 **60 格 / 240 格每秒**的穿透柱（**伤害减半**）；**期间目标会被打得乱飞**，**最后 1 秒收尾**——把目标**按回弹幕触发点**上方、周边**同时生成 28 根**等距柱子（同距离 → 同时命中），然后**一记大爆炸**（威力 6，未减）收场 |
| `new-mod:barrage_pillar` | 弹幕柱（实体） | 由弹幕生成：在目标外侧 **8 格**处生成、朝向目标、长 **60 格**、**240 格/秒**，因此**穿过**目标再冲出去（穿透造成伤害与击退）；**沿途清路（可破基岩）**；**伤害与击退（推力+距离）都减半**；触发者不受它伤害也不被它推；不参与任何柱间相撞 |
| `new-mod:dash_pillar` | 冲刺柱（实体） | **潜行右键**在玩家**身后 3 格**生成、朝**视线方向**长 60 格（60 格/秒），把玩家一路推出去；生成前先清掉玩家周边 3 格方块；不参与任何柱间相撞 |
| `new-mod:horizontal_axis_pillar` / `new-mod:vertical_axis_pillar` | 水平 / 竖直对撞柱（实体） | 由鼠标手势在空白轴端点生成的 **20 格**黑方块柱，**沿途清路（连基岩一起破）**；**同组且方向相反**的两根互撞时**只爆炸**（不生黑洞）、双方停止伸长，并给范围内生物打**标记 2 / 3** |

**联动**：两根柱子**共用同一条生成逻辑**（同起点、同朝向，都在玩家视线轴上，天然共线、互相套住），长柱的**有效段**随填充推进，因此填充前沿扫到短柱身上时两者相撞 → **双方立刻停止伸长/填充**、在碰撞处**真实爆炸**（破坏与否由设置页决定），并给爆炸波及且仍存活的生物打上**标记 1**（原版实体标签 `new-mod:mark1/2/3`，`/tag @e list` 可见）。**标记 1 的效果**：在**这只生物身上**生成水平 / 竖直两条 20 格空白透明轴（垂直于玩家视线，**并跟随它移动**），并由 [`MarkLockEntity`](src/main/java/com/sizah/entity/MarkLockEntity.java) **只清除它的所有方向速度 5 秒**（不固定位置）。**黑洞**则由**1 号 + 2 号行为的爆炸**在碰撞点生成，持续 **1 秒**并把波及者按回碰撞点。

**击退细节**：柱子每 tick 推一次，单次位移 = 类型速度 × `PillarSpec.knockbackScale`（**弹幕柱 0.5 = 推力与距离都减半**；弹幕柱的伤害同样 ×0.5）；生物**脱离柱子**（上一 tick 还在、这一 tick 不在）时速度再 **×0.01**，几乎停住（发射者例外，他有专门的后坐收尾 ×0.1）。另外三条规则：

- **爆炸不给速度**：黑方块实体产生的爆炸（相撞、弹幕收尾）先记下附近生物的速度、炸完立刻还原 —— 只有伤害与破坏，没有那「一脚」（[`Explosions`](src/main/java/com/sizah/effect/Explosions.java)）；
- **击退冲突清零**：**受伤期间**（`hurtTime > 0`）被**两种不同的黑方块实体**在同一 tick 推到时，速度直接清零，不再叠加（[`PushConflicts`](src/main/java/com/sizah/effect/PushConflicts.java)）；
- **发出行为的玩家免疫**：**不被黑洞吸、不被爆炸打标记、不受弹幕击退**（相撞时先把 owner 从 `victims` 里剔除，弹幕柱则带上 owner）。

详见 `learn/18`。

**潜行技能**（手持黑方块之星时）：

| 操作 | 动作 | 冷却 |
| --- | --- | --- |
| **潜行右键** | 在**身后 3 格**生成冲刺柱（朝视线方向长 60 格），把玩家推出去；生成前先清掉玩家周边 3 格方块 | 独立 20 tick |
| **潜行左键** | **按下瞬间一次性随机感染 64 个方块**：从准心指向的第一个方块出发随机游走，一路感染相邻方块直到满 64 块（半径 3 格内）；**松开潜行**时**都掉落** —— 方块本体消失，掉落「原本掉落物 + 一个黑方块」 | 独立 20 tick |

感染的记录与结算见 [`Corruption`](src/main/java/com/sizah/effect/Corruption.java)（玩家退出时也会立即结算）。

**视角移动量门禁（1、2 号行为）**：**一个 tick 内水平（yaw）或竖直（pitch）的转动角度都要小于 1 度**才会触发 —— 也就是**停住不动才能放**，一边转视角一边点没有反应。不满足时什么都不放、**也不消耗冷却**，并且左键仍可正常攻击/挖掘。潜行技能与四个手势**不受**这条限制。阈值与两侧采样在 [`ViewMotion`](src/main/java/com/sizah/effect/ViewMotion.java)（`STILL_LIMIT_DEGREES`），详见 `learn/19`。另外，相撞的**爆炸与标记范围已从 4 格扩到 8 格**（`BlackBlockPillarEntity.EXPLOSION_RADIUS`，同时也是黑洞的捕获范围）。

**连招手势**（需手持黑方块之星，且场上存在相应的空白轴）：

| 手势 | 动作 | 相撞后果 |
| --- | --- | --- |
| 右键单击 + **右**滑 | 水平轴**左**端生成 20 格柱 → 把敌人向**右**击退 | 只爆炸 + 标记 **2** |
| 右键单击 + **左**滑 | 水平轴**右**端生成 → 向**左**击退 | 只爆炸 + 标记 **2** |
| 左键单击 + **上**滑 | 竖直轴**下**端生成 → 把敌人**击飞** | 只爆炸 + 标记 **3** |
| 左键单击 + **下**滑 | 竖直轴**上**端生成 → 把敌人**击落** | 只爆炸 + 标记 **3** |

实现链路：「单击 + 视角转动」识别（[`GestureInput`](src/main/java/com/sizah/client/GestureInput.java)，无需 Mixin）→ `axis_strike` 自定义包 → 服务端在轴端点生成对撞柱（沿途清路），详见 `learn/15`。

**终结技（集齐三层标记）**：生物身上同时带着**标记 1 + 2 + 3** 时，立刻消耗这三层标记并触发 [`PillarBarrageEntity`](src/main/java/com/sizah/entity/PillarBarrageEntity.java)——跟着目标 **20 秒**、每 **0.3 秒**从**四面八方**（球面随机方向，三根一循环：1↔2 ≥ 60°、1↔3 ≥ 100°）放出 **60 格 / 120 格每秒的穿透柱**（在目标外侧 8 格生成、朝向目标，所以会穿过它）；**最后 1 秒收尾**：把目标**禁锢在半空**（抬升 2.5 格并按住），周边**同时生成 14 根等距柱子**（距离相同 → 同时命中），结尾一记**威力 6 的爆炸**。详见 `learn/16`。

**设置页**：游戏内默认按 **K** 打开（可在「选项 → 按键控制 → New Mod」改键），两个开关：

| 开关 | 关闭 | 打开（默认） |
| --- | --- | --- |
| 基岩：破坏 / 无视 | 所有柱子都拿基岩没办法 | 除 1 号行为外，柱子连基岩一起清 |
| 爆炸破坏方块：是 / 否 | 爆炸只伤生物、不炸地形 | 爆炸同 TNT，破坏地形 |

选择会存进 `config/new-mod.json`，并在改动时与每次进入世界时同步给服务端（服务端权威），详见 `learn/17`。

**八种行为各自独立冷却**（[`WeaponCooldowns`](src/main/java/com/sizah/item/WeaponCooldowns.java) 的 `Slot`）：**左键短柱 60 tick（3 秒）**、右键长柱 20 tick、四个手势对撞柱各 10 tick、潜行右键 / 潜行左键各 20 tick —— 一共 **八格**，互不影响。

**破坏力**：所有柱子都**清路**（沿途瞬间破坏并正常掉落）；其中**除 1 号行为（左键短柱 / 黑曜石方）外的全部行为——右键长柱、四个手势对撞柱、弹幕柱——连基岩也能破坏**（`PillarSpec.breaksUnbreakable` + 设置页总开关）。

**技能**：主手拿着**黑方块之星**时——**左键**沿视线方向发射短黑方块柱（1 秒长到 30 格，再 1 秒消散）；**右键**用同一条生成逻辑放出**长黑方块柱**（生成即满长 60 格，黑方块与功能由尖端向底端 60 格/秒填充，填满后由尖端向底端消散），沿途破坏方块并把目标**向着玩家**击退（含你自己）。**黑曜石方**只有左键，且**两者都不使用普通攻击键**（左键即技能键）；左键与右键的冷却**独立计算**（[`WeaponCooldowns`](src/main/java/com/sizah/item/WeaponCooldowns.java)）。实现链路为「客户端 `ClientPreAttackCallback` → 自定义网络包 → 服务端生成实体 → 客户端用方块模型渲染」，详见 `learn/05`；命中结算（自定义伤害类型、无视护甲标签、击退同步）见 `learn/06`；撞墙反向伸长与玩家后坐见 `learn/07`；两头被顶住时按硬度破坏方块见 `learn/08`；两种武器共用一套逻辑的做法见 `learn/09`；四种柱子类型与 `PillarSpec` 见 `learn/10`；右键长柱见 `learn/11`。

武器基础数值定义在 [`ModToolMaterials`](src/main/java/com/sizah/item/ModToolMaterials.java)：耐久 1561、挖掘速度 8.0、攻击加成 3.0、附魔值 10，修复材料为物品标签 `new-mod:black_block`（见 [`data/new-mod/tags/item/black_block.json`](src/main/resources/data/new-mod/tags/item/black_block.json)）。

## 环境要求

| 组件 | 版本 |
| --- | --- |
| Java | 25 |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18-SNAPSHOT |

## 构建与运行

```bash
./gradlew build      # 构建，产物在 build/libs/
./gradlew runClient  # 启动开发客户端
```

版本号、依赖版本统一在 [`gradle.properties`](gradle.properties) 中调整。

## 目录结构

- [`src/main/java/com/sizah`](src/main/java/com/sizah) — 模组入口 `NewMod`、Mixin 示例
  - [`block/ModBlocks.java`](src/main/java/com/sizah/block/ModBlocks.java) — 方块注册
  - [`item/ModItems.java`](src/main/java/com/sizah/item/ModItems.java) — 物品注册（含方块物品形态）
  - [`item/ModItemGroups.java`](src/main/java/com/sizah/item/ModItemGroups.java) — NEW 物品页
  - [`item/PillarWeaponItem.java`](src/main/java/com/sizah/item/PillarWeaponItem.java) — 柱子武器类（两种武器共用）
  - [`item/PillarKind.java`](src/main/java/com/sizah/item/PillarKind.java) — 武器种类：外观方块 / 触碰伤害 / 是否无视护甲
  - [`item/ModToolMaterials.java`](src/main/java/com/sizah/item/ModToolMaterials.java) — 武器数值
  - [`entity/`](src/main/java/com/sizah/entity) — 柱子实体（伸长/反向伸长/两头破坏/消散、触碰伤害与击退、玩家后坐、相撞爆炸）
  - [`entity/ModEntities.java`](src/main/java/com/sizah/entity/ModEntities.java) — 类型表（`PillarSpec`：外观/伤害/长度/速度/是否消散/是否沿途破坏/能否击退发射者/相撞分组与标记/是否尖端向底端填充）与黑洞、空白轴类型
  - [`entity/BlackHoleEntity.java`](src/main/java/com/sizah/entity/BlackHoleEntity.java) — 黑洞：1 号 + 2 号行为的爆炸生成，禁锢 1 秒
  - [`entity/MarkLockEntity.java`](src/main/java/com/sizah/entity/MarkLockEntity.java) — 标记 1 的禁锢：清除生物所有方向速度 5 秒
  - [`entity/BlankAxisEntity.java`](src/main/java/com/sizah/entity/BlankAxisEntity.java) — 水平 / 竖直空白透明轴：只给手势提供端点
  - [`effect/Marks.java`](src/main/java/com/sizah/effect/Marks.java) — 标记 1 / 2 / 3（原版实体标签实现）
  - [`effect/Explosions.java`](src/main/java/com/sizah/effect/Explosions.java) — 「不提供速度」的爆炸封装
  - [`effect/PushConflicts.java`](src/main/java/com/sizah/effect/PushConflicts.java) — 同 tick 两种实体击退 → 速度清零
  - [`effect/Corruption.java`](src/main/java/com/sizah/effect/Corruption.java) — 潜行左键的方块感染与松手结算
  - [`config/Settings.java`](src/main/java/com/sizah/config/Settings.java) — 服务端玩法开关（基岩 / 爆炸破坏）
  - [`damage/ModDamageTypes.java`](src/main/java/com/sizah/damage/ModDamageTypes.java) — 自定义伤害类型（无视护甲）
  - [`network/`](src/main/java/com/sizah/network) — 左键请求包、手势包与服务端处理、右键技能（服务端交互事件）
  - [`client/NewModClient.java`](src/main/java/com/sizah/client/NewModClient.java) — 客户端入口：左键拦截与实体渲染器注册
  - [`client/GestureInput.java`](src/main/java/com/sizah/client/GestureInput.java) — 鼠标手势识别（单击 + 视角转动，无 Mixin）
  - [`client/ModSettingsScreen.java`](src/main/java/com/sizah/client/ModSettingsScreen.java) — 设置页（基岩 / 爆炸破坏开关，默认 K 打开）
  - [`client/ClientSettings.java`](src/main/java/com/sizah/client/ClientSettings.java) — 客户端设置：写 `config/new-mod.json` 并发包同步
  - [`client/BlackBlockPillarRenderer.java`](src/main/java/com/sizah/client/BlackBlockPillarRenderer.java) — 用黑方块模型绘制柱子
  - [`client/InvisibleRenderer.java`](src/main/java/com/sizah/client/InvisibleRenderer.java) — 「什么都不画」：黑洞、空白轴、弹幕控制器、标记禁锢共用
- [`src/main/resources/assets/new-mod`](src/main/resources/assets/new-mod) — 物品定义、模型、贴图、**模组图标**与本地化（`en_us`、`zh_cn`）
- `tools/gen-assets.js` — **美术资产生成器**（纯 Node，零第三方库）：手写 PNG 画出纯白发光小球贴图与奇异风图标，并生成「体素球 + 环绕小黑方块」的 3D 物品模型；
  改完参数跑 `node tools/gen-assets.js` 即可重新生成（加 `--preview <目录>` 会额外输出贴图放大预览与**等轴测模型预览**，方便检查摆位与 alpha 形状）。原理见 `learn/20`
- `tools/check-assets.js` — **资产自检**：模型部件/旋转/贴图引用/球与环绕方块数量、PNG 尺寸与颜色类型、以及「纯白贴图里有没有混进非白像素」，`node tools/check-assets.js` 一键跑完
- [`src/main/resources/data/new-mod`](src/main/resources/data/new-mod) — 战利品表、物品标签与伤害类型
- [`src/main/resources/data/minecraft`](src/main/resources/data/minecraft) — 扩展原版标签（把自定义伤害类型加进 `damage_type/bypasses_armor`）
- `learn/` — 中文开发笔记，**仅在本地保留、不随仓库发布**（见下）

## 开发笔记（learn，本地保留）

**约定：每次功能改动都要在 `learn/` 增补一篇「通俗且深入」的教程**——先讲清概念与数据流，再给可运行的代码，最后附上参数速查、常见坑与验证方式。

这套笔记**仅在本地保留、不随仓库发布**（`learn/` 已写进 `.gitignore`），所以公开仓库里读不到它们；想看同样的知识，请以代码注释与 [`README.md`](README.md) 为准。

## 作者与许可

作者：siza-h。以 CC0-1.0 许可发布，详见 [`LICENSE`](LICENSE)；项目基于 Fabric 官方示例模板搭建。
