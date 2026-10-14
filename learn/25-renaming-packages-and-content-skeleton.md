# 25. 目录重组与「加内容」的骨架（common / content / client / datagen）

需求：**把这个项目整理一下，后面还要加很多方块、物品、武器、工具、生物，可以建空文件夹备用。**

这一篇记录重组后的结构、两个"一行注册"的助手，以及**差点把整个项目搞坏的那次批量改包**——它比结构本身更值得写下来。

## 1. 为什么按内容类型分家

重组前是"按技术职责"分：`block/ item/ entity/ effect/ network/ client/`。问题在于**加内容时要在四个目录之间跳**：
一个"红宝石矿石"要动 `block/`（方块）、`item/`（物品形态）、`entity/`（如果它是机器就有方块实体）、
`client/`（渲染）、还有数据文件。

重组后：

```
com.sizah
├── common/       跨内容的设施：入口 NewMod、注册助手、ModTags、config、damage、network
├── content/      按内容类型分家
│   ├── ModCreativeTabs
│   ├── block/    ModBlocks、OreFamilies（"一种材料十个方块"的属性工厂）
│   ├── item/     普通物品
│   ├── weapon/   武器本体、变体表（PillarKind）、冷却（WeaponCooldowns）、weapon/entity/（柱子、ModEntities）
│   ├── tool/ armor/ food/ mob/ projectile/ machine/ world/ enchant/   ← 骨架，register() 待填
│   └── effect/   行为层（Marks / Explosions / PushConflicts / Corruption / Decay）+ effect/entity/
├── client/       客户端：NewModClient、input/（手势、白星输入）、render/、settings/
└── datagen/      数据生成：6 个 Provider + 入口
```

两条规则：

1. **每类内容一个 `register()`**，全部由 `NewMod.onInitialize` 按依赖顺序调用：
   `方块 → 物品 → 各类内容 → 状态效果 → 创造标签页（要能看到所有物品，所以最后）→ 实体类型 → 网络`；
2. **"行为"与"定义"分家**：例如"打标记"是 `effect/Marks`（行为），而"状态效果的定义"是 `enchant/ModEffects`（定义）。

## 2. 空包不能是空目录

Git 不跟踪空目录，Java 包名又必须对应目录，所以"预留位置"要放东西。这里放的是**骨架注册类**：

```java
public final class ModMobs {
	private ModMobs() {}

	/** Registers every mob entity type and spawn egg. Called from NewMod#onInitialize. */
	public static void register() {
		// TODO: register the first mob entity type (and its spawn egg) here.
	}
}
```

每个类的**类注释就是那一类内容的"清单"**：加生物要准备哪些东西（实体类、实体类型、渲染器、刷怪蛋、战利品、语言），
加机器要注意什么（方块实体、ticker 只在服务端跑、容器能力）……比单独写一份文档更不容易过期。

## 3. 两个"一行注册"助手

`common/SimpleRegistry` 把每个内容都要做的三件事（建 `ResourceKey`、注册、返回实例）合成一行：

```java
public static final Block RUBY_ORE = SimpleRegistry.block("ruby_ore", oreProperties());
public static final Item RUBY_BLOCK_ITEM = SimpleRegistry.blockItem("ruby_block", ModBlocks.RUBY_BLOCK);
```

`content/block/OreFamilies` 把"一种材料的常见属性"写好（金属块 / 矿石 / 生料块 / 装饰块 / 植物），
并提供 `copyOf(base)` 给楼梯台阶墙复用属性——避免"改了硬度忘了改台阶"。

> 约定：**注册名一旦发布就不能改**（存档里写的是它）。`SimpleRegistry` 只帮你省事，不帮你改名。

## 4. 那次批量改包：三个坑

重组本身不复杂（33 个文件、一张映射表），但我用 PowerShell 批量改文件时踩了三连坑，值得记住：

### 坑 1：PowerShell 5 的 `-Encoding utf8` 会写 BOM

```powershell
Set-Content -Path Foo.java -Value $text -Encoding utf8   # ← 写入了 EF BB BF
```

症状：`javac` 报 `非法字符: '\ufeff'`，所有文件第一行都编译不过。

正确做法是用 .NET 的无 BOM UTF-8：

```powershell
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($path, $text, $utf8NoBom)
```

### 坑 2：同一操作还会**把中文注释变成乱码**

`Get-Content -Raw` 读进来的中文，再 `Set-Content -Encoding utf8` 写出时按 GBK 解释 → `鈫?`、`榛戞柟鍧?`。
这次 20 个文件的中文注释全部损坏，最后靠 `git show HEAD:<path>` **从 git 恢复原文**才救回来。

教训：**批量文本处理一律用 `[IO.File]::ReadAllText/WriteAllText` + 显式 UTF8**，或者干脆用带编码保证的工具（Python、Node）。

### 坑 3：用正则"按包名替换"会连环误伤

我最初按包名做替换（`com.sizah.client.` → `com.sizah.client.`），结果：

- `com.sizah.client.GestureInput` → `com.sizah.client`（**类名被吃掉**）；
- `com.sizah.NewMod` 规则又把 `com.sizah.NewModClient` 截成 `com.sizah.client`；
- 重跑又二次改写，`effect/entity/*` 一度被标成 `weapon/entity`。

**更稳的做法：按"目录 → 期望包名"强制写回**，再按"类名 → 所在包"生成 import：

```powershell
# 1) 包名永远由文件所在目录决定，不做替换、直接写
$expected = "package " + ((Split-Path $rel -Parent) -replace '\\','.') + ";"

# 2) import 用「类名 → 包」的映射表修正（表由遍历源码生成），而不是拿旧包名去 replace
```

改完立刻编译（`gradlew compileJava`），让编译器兜住剩下的漏改——比肉眼检查可靠得多。

## 5. 别忘入口点

包一挪，`fabric.mod.json` 里的全限定名就失效了：

```json
"entrypoints": {
	"main": ["com.sizah.common.NewMod"],
	"client": ["com.sizah.client.NewModClient"],
	"fabric-datagen": ["com.sizah.datagen.NewModDataGenerator"]
}
```

**漏改这一处的表现是"启动即崩"**，而不是编译错误，所以改包之后要专门去检查它。

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 编译报 `非法字符: '\ufeff'` | 文件被写入了 BOM |
| 中文注释变乱码 | 读写时编码不一致（PowerShell 5 的 `-Encoding utf8`） |
| import 指向不存在的包 | 用包名做正则替换的连锁误伤；改为"按类名映射 + 目录决定包名" |
| 启动崩溃、日志说找不到主类 | `fabric.mod.json` 的入口没同步 |
| 新内容注册两次、id 冲突 | 两个地方都建了同一个 id（例如 `ModBlocks` 与 `OreFamilies` 各一份） |

## 7. 小结

- 组织方式跟着**工作流**走：按内容类型分家，加内容只动一个格子；空包用"骨架类 + 类注释清单"占位；
- 注册的重复劳动用 `SimpleRegistry` / `OreFamilies` 这类助手消掉，但**留给"命名"的人工决定**（id 不能改）；
- 批量重构的三条铁律：**无 BOM 的 UTF-8 读写**、**按目录推导包名（不做替换）**、**类名映射修 import**；
- 每次批量改动之后**立刻编译**，再检查入口点配置——这两步能兜住绝大多数低级错误。
