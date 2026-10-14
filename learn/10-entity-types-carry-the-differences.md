# 10. 四种柱子实体类型：用 `PillarSpec` 承载差异

现在柱子在游戏里是**四个实体类型**，名字各不相同：

| ID | 名称 | 状态 | 外观 | 最大长度 | 长满后 |
| --- | --- | --- | --- | --- | --- |
| `new-mod:short_black_block_pillar` | 短黑方块柱 | 黑方块之星发射 | 黑方块 | 30 格 | 1 秒后消散 |
| `new-mod:obsidian_pillar` | 黑曜石柱 | 黑曜石方发射 | 黑曜石 | 30 格 | 1 秒后消散 |
| `new-mod:long_black_block_pillar` | 长黑方块柱 | 黑方块之星**右键**发射 | 黑方块 | 60 格 | 1 秒后消散 |
| `new-mod:eternal_black_block_pillar` | 永黑方块柱 | **备用** | 黑方块 | 30 格 | **不消散** |

四个类型**共用同一个实体类** `BlackBlockPillarEntity`，差异全部由 `ModEntities.PillarSpec` 描述。

## 1. 一张表装下所有差异

```java
public record PillarSpec(
		PillarKind kind,
		double maxLength,
		double speedPerTick,          // 伸长 / 消散 / 击退速度
		boolean dissipates,
		boolean destroysPath,         // 沿途瞬间破坏方块（长黑方块柱）
		boolean knocksBackOwner,      // 发射者也能被击退（长黑方块柱）
		boolean explodesOnCollision,  // 与别的柱子相撞时停下并炸出黑洞（长黑方块柱）
		boolean fillsFromTip          // 生成即满长，再由尖端向底端填充渲染与功能（长黑方块柱）
) {
}
```

- `kind`：外观方块、触碰伤害、是否无视护甲（见 [learn/09](09-weapon-variants-by-enum.md)）；
- `maxLength`：这一种柱子要长到多少格（伸长、反向伸长、击退上限、后坐距离都以它为准）；
- `speedPerTick`：速度，常规 1.5（30 格/秒），长黑方块柱 3.0（60 格/秒）；
- `dissipates`：长满之后是收回去消散，还是**原地留着**；
- 后四项只有长黑方块柱打开，见 [learn/11](11-right-click-long-pillar.md) 与 [learn/13](13-pillar-collision-explosion-blackhole.md)。

```java
private static final Map<EntityType<?>, PillarSpec> SPECS = Map.of(
		SHORT_BLACK_BLOCK_PILLAR,
		new PillarSpec(PillarKind.BLACK_BLOCK, SHORT_LENGTH, SPEED_PER_TICK, true, false, false, false, false),
		OBSIDIAN_PILLAR,
		new PillarSpec(PillarKind.OBSIDIAN, SHORT_LENGTH, SPEED_PER_TICK, true, false, false, false, false),
		LONG_BLACK_BLOCK_PILLAR,
		new PillarSpec(PillarKind.BLACK_BLOCK, LONG_LENGTH, LONG_SPEED_PER_TICK, true, true, true, true, true),
		ETERNAL_BLACK_BLOCK_PILLAR,
		new PillarSpec(PillarKind.BLACK_BLOCK, SHORT_LENGTH, SPEED_PER_TICK, false, false, false, false, false)
);
```

实体与渲染器都只问这一张表：

```java
// 实体：外观、伤害、长度、生命周期全从这里取
private ModEntities.PillarSpec spec() { return ModEntities.specOf(this.getType()); }
public PillarKind getKind()   { return this.spec().kind(); }
public double getMaxLength()  { return this.spec().maxLength(); }
public boolean dissipates()   { return this.spec().dissipates(); }
```

## 2. 为什么把差异放在「实体类型」上

因为**客户端本来就知道同步过来的实体是什么类型**——`Entity#getType()` 两端都可用。于是：

- **不需要任何自定义同步字段**（早期版本同步过一个 `ordinal`，现在删掉了）；
- **只有一个真相来源**：服务端拿它算伤害，客户端拿它画方块，配错也只可能错在一处；
- **名字天然区分**：语言键 `entity.new-mod.<id>` 直接给出「短黑方块柱 / 黑曜石柱 …」，
  也不需要用 `setCustomName(...)` 去改显示名（那样还可能顶出一个漂浮名牌）。

## 3. 类型怎么建、怎么注册

```java
/** @param noSummon 武器发射的类型禁止 /summon；备用类型允许，方便单独试效果 */
private static EntityType<BlackBlockPillarEntity> build(ResourceKey<EntityType<?>> key, boolean noSummon) {
	EntityType.Builder<BlackBlockPillarEntity> builder = EntityType.Builder
			.of(BlackBlockPillarEntity::new, MobCategory.MISC)
			.sized(1.0F, 1.0F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.noSave();                 // 短命实体（「永」类型也不进存档）

	if (noSummon) {
		builder = builder.noSummon();
	}

	return builder.build(key);     // 26.3 的 build 需要 ResourceKey
}

public static void register() {
	Registry.register(BuiltInRegistries.ENTITY_TYPE, SHORT_BLACK_BLOCK_PILLAR_KEY, SHORT_BLACK_BLOCK_PILLAR);
	Registry.register(BuiltInRegistries.ENTITY_TYPE, OBSIDIAN_PILLAR_KEY, OBSIDIAN_PILLAR);
	Registry.register(BuiltInRegistries.ENTITY_TYPE, LONG_BLACK_BLOCK_PILLAR_KEY, LONG_BLACK_BLOCK_PILLAR);
	Registry.register(BuiltInRegistries.ENTITY_TYPE, ETERNAL_BLACK_BLOCK_PILLAR_KEY, ETERNAL_BLACK_BLOCK_PILLAR);
}
```

客户端渲染器四个类型都要注册（共用一个渲染器实例）：

```java
EntityRenderers.register(ModEntities.SHORT_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
EntityRenderers.register(ModEntities.OBSIDIAN_PILLAR, BlackBlockPillarRenderer::new);
EntityRenderers.register(ModEntities.LONG_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
EntityRenderers.register(ModEntities.ETERNAL_BLACK_BLOCK_PILLAR, BlackBlockPillarRenderer::new);
```

## 4. 「备用」类型怎么用

`eternal_black_block_pillar` 目前没有武器发射（`typeFor(...)` 只映射到短黑方块柱和黑曜石柱；
长黑方块柱走**右键**那条独立路径，见 [learn/11](11-right-click-long-pillar.md)），但它**允许 `/summon`**，
可以先单独看效果：

```
/summon new-mod:eternal_black_block_pillar ~ ~ ~
/summon new-mod:long_black_block_pillar ~ ~ ~ {Rotation:[0f,0f]}
```

- 生成方向取自实体旋转（`xRot`/`yRot`），所以 `Rotation` 决定柱子朝哪边伸；
- 用指令生成时**没有发射者**（`owner` 为空），因此不会有后坐、也不会伤害到谁；
- `noSave()` 意味着它们不会被写进存档，重启即消失——「永」是指**存活期间不自行消散**，不是永久建筑。

> 名字里的「长 / 永」就是行为预期的占位：`LONG_LENGTH = 60`、`dissipates = false`。
> 将来做对应的武器时，只要把 `typeFor(...)` 接上即可，实体逻辑一行都不用改。

## 5. 新增一种柱子要动的地方

1. `ModEntities`：加一个 `ResourceKey` + 一个 `build(...)` 类型 + 一条 `SPECS` 记录；
2. `ModEntities.register()` 里注册它；
3. 客户端 `EntityRenderers.register(...)` 注册渲染器；
4. 语言文件加 `entity.new-mod.<id>`；
5. 若要能被武器发射，再把它接进 `typeFor(...)`（或新的映射规则）。

## 6. 常见坑

| 现象 | 原因 |
| --- | --- |
| 启动报 `IllegalArgumentException: Duplicate key` / 表里取不到 | `Map.of` 的 key 必须是**已注册的同一个 `EntityType` 实例**，不能是新建的等价对象 |
| `SPECS` 初始化时 `null` | 静态字段按声明顺序初始化：**`SPECS` 必须写在四个类型常量之后** |
| 客户端看不到柱子 / 空指针 | 新增类型忘了注册渲染器（原版渲染调度器对未注册类型会空指针） |
| 柱子名字显示成 `entity.new-mod.xxx` | 语言键漏了 |
| `/summon` 提示无法生成 | 该类型带了 `noSummon()`（武器发射的两个类型是故意的） |
| 「永」类型长满后仍在缩短 | `dissipates()` 判断没接进 `updateGeometry`，或 `PillarSpec` 配成了 `true` |

## 7. 验证方式

```bash
./gradlew build
./gradlew runDatagen         # 验证四个类型都能注册
./gradlew runClient          # 用两把武器各开一枪；再 /summon 两个备用类型看长度与是否消散
```

进游戏观察点：

1. 黑方块之星 → 柱体是黑方块，名字在 F3 里显示为「短黑方块柱」；
2. 黑曜石方 → 柱体是黑曜石，名字「黑曜石柱」；
3. `/summon new-mod:long_black_block_pillar` → 长度到 60 格才停止；
4. `/summon new-mod:eternal_black_block_pillar` → 长满 30 格后停在原地不消散。

## 8. 小结

- **多个实体类型共用一个实体类**，差异集中在一条 `PillarSpec` 记录里。
- 差异放在**类型**上而不是同步字段里：客户端本来就知道类型，于是零同步、单一真相来源、名字天然区分。
- 「备用」类型通过允许 `/summon` 保持可测试性；`noSave()` 让它们不会污染存档。
- 新增柱子类型 = 加一条 `SPECS` + 一次注册 + 一次渲染器注册 + 一个语言键。
