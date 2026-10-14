# New Mod

> English version: [`README.md`](README.md)

一个 Fabric 模组（Minecraft 26.3）：黑方块及其精确反色**白方块**、黑方块之星、黑曜石方，以及一整套"造柱子"的技能——
左键短柱 / 右键长柱、鼠标手势驱动的对撞柱、黑洞、基于标记的柱弹幕，外加潜行冲刺与感染。
**白方块之星**已作为一把可用之剑就位，技能待设计（见 [`content/README.md`](src/main/java/com/sizah/content/README.md)）。

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
./gradlew runClient     # 启动开发客户端
./gradlew runDatagen    # 重新生成模型/掉落/标签/语言（产物在 src/main/generated）
./gradlew build         # 构建，产物在 build/libs/
```

版本号、依赖版本统一在 [`gradle.properties`](gradle.properties) 中调整。

> **加完内容记得跑一次 `runDatagen`**：模型、方块状态、掉落表、标签、中英双语语言文件都由数据生成产出，
> 不跑就会在游戏里看到"未翻译的名字"和缺失的模型。生成目录不要手工编辑，规则见
> [`src/main/java/com/sizah/content/README.md`](src/main/java/com/sizah/content/README.md)。

## 代码结构

```
src/main/java/com/sizah/
├── common/     入口、注册助手（SimpleRegistry）、标签、配置、伤害类型、网络包
├── content/    按内容类型分家：block / item / weapon / tool / armor / food / mob /
│               projectile / machine / world / enchant / effect，以及创造标签页
├── client/     客户端入口、鼠标手势、渲染器、设置页
└── datagen/    数据生成提供器（模型、双语语言文件、掉落、配方、标签）

src/main/generated/   datagen 产物（勿手改）
src/main/resources/   手写资产：贴图、3D 武器模型、伤害类型 JSON
```

新增内容的最短路径见 [`content/README.md`](src/main/java/com/sizah/content/README.md)；
从零实现某个功能的详细笔记见 [`learn/`](learn/README.md)。

## 作者与许可

作者：siza-h。本项目原创代码、文档与素材以 [MIT 许可证](LICENSE) 发布。项目起始于 [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod) 示例模板；该模板以 [CC0-1.0](https://github.com/FabricMC/fabric-example-mod/blob/26.3/LICENSE) 放弃版权并授权使用，针对模板衍生内容的该授权不会因本仓库采用 MIT 而撤销。第三方依赖及其他上游材料仍遵循各自的许可。

本模组与 Mojang Studios 或 Microsoft 无关联。"Minecraft" 是 Mojang Synergies AB 的商标；本项目仅依赖官方游戏与 Fabric API，不包含或再分发 Mojang 的游戏素材。仓库规范见[贡献指南](CONTRIBUTING.md)、[行为准则](CODE_OF_CONDUCT.md)和[安全政策](SECURITY.md)。
