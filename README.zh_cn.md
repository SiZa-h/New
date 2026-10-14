# New Mod

> English version: [`README.md`](README.md)

一个 Fabric 模组（Minecraft 26.3）

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

## 作者与许可

作者：siza-h。以 CC0-1.0 许可发布，详见 [`LICENSE`](LICENSE)；项目基于 Fabric 官方示例模板搭建。
