# Contributing

Contributions are welcome. Please open an issue before making a large or behavior-changing proposal.

## Before submitting

- Keep changes focused and follow the existing package and naming conventions.
- Do not hand-edit files under `src/main/generated`; update the data providers and run `./gradlew runDatagen`.
- Run `node tools/check-assets.js` when changing generated textures or models, and run `./gradlew build` before opening a pull request.
- Update relevant English and Chinese documentation when behavior or project instructions change.
- In pull requests, explain the change, how it was tested, and any user-visible behavior changes.

## Rights and attribution

By submitting a contribution, you agree to make your original contribution available under the project's MIT license. The project also began from the [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod) template, whose template material is dedicated under [CC0-1.0](https://github.com/FabricMC/fabric-example-mod/blob/26.3/LICENSE); this existing grant remains available. Disclose the source and license of third-party code, text, fonts, or artwork, and retain required notices. Do not submit Minecraft/Mojang assets or material that you do not have permission to redistribute. The project's license does not replace the licenses of dependencies or other third-party material.

# 贡献指南

欢迎贡献。对于较大或会改变现有行为的提议，请先开 issue 讨论。

## 提交前

- 保持改动聚焦，并遵循现有的包结构与命名习惯。
- 不要手工编辑 `src/main/generated` 下的文件；应修改数据提供器并运行 `./gradlew runDatagen`。
- 修改生成贴图或模型时运行 `node tools/check-assets.js`；提交拉取请求前运行 `./gradlew build`。
- 行为或项目说明有变化时，同步更新相关的中英文文档。
- 拉取请求中请说明改动内容、验证方式及对用户可见的行为变化。

## 权利与署名

提交贡献即表示你同意将自己原创的贡献按本项目 MIT 许可证提供。本项目起始于 [Fabric Example Mod](https://github.com/FabricMC/fabric-example-mod) 示例模板，其模板材料以 [CC0-1.0](https://github.com/FabricMC/fabric-example-mod/blob/26.3/LICENSE) 放弃版权并授权使用；既有授权继续有效。第三方代码、文字、字体或美术素材须注明来源及许可，并保留其要求的声明。不得提交 Minecraft/Mojang 游戏素材，或任何未经授权可再分发的材料。本项目许可证不会取代依赖项或其他第三方材料各自的许可证。
