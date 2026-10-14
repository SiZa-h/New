package com.sizah.datagen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.sizah.common.NewMod;
import com.sizah.content.block.ModBlocks;
import com.sizah.content.item.ModItems;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The two language files ({@code zh_cn} and {@code en_us}).
 *
 * <p>Both files describe **the same key set**, so the two languages can never drift apart, but each side gets its
 * text from where it belongs:
 *
 * <ul>
 *     <li><b>English</b>: every item, block and entity type registered under this mod id is discovered from the
 *         registries and named after its id ({@code ruby_pickaxe} → "Ruby Pickaxe"), so **new content needs no
 *         English line at all**;</li>
 *     <li><b>Chinese</b>: the same auto-discovered default is used until {@link #CHINESE} overrides a key, which
 *         is where the names that should not be a transliterated id live ("黑方块之星", "短黑方块柱", ...).</li>
 * </ul>
 *
 * <p>Keys the game cannot derive at all (creative tabs, key bindings, screens, death messages) are listed in
 * {@link #CHINESE} as well and get their English from {@link #ENGLISH}.
 *
 * <p>To name a new block or item in Chinese, add one line to {@link #CHINESE}. To add a GUI or death message
 * string, add it to both maps.
 */
public final class ModLanguageProvider {
	private ModLanguageProvider() {
	}

	/**
	 * Keys the game cannot derive at all, as {@code key -> english}.
	 *
	 * <p>An item named after its block shares the block's key ({@code useBlockDescriptionPrefix()}), so there is
	 * only ever one line per piece of content.
	 */
	private static final Map<String, String> ENGLISH = Map.ofEntries(
			Map.entry("itemGroup.new-mod.new", "NEW"),
			Map.entry("key.categories.new-mod.main", "New Mod"),
			Map.entry("key.new-mod.settings", "Open settings"),
			Map.entry("screen.new-mod.settings.title", "New Mod Settings"),
			Map.entry("option.new-mod.break_bedrock", "Bedrock: %s"),
			Map.entry("option.new-mod.break_bedrock.on", "Breakable"),
			Map.entry("option.new-mod.break_bedrock.off", "Ignored"),
			Map.entry("option.new-mod.explosion_breaks", "Explosions break blocks: %s"),
			Map.entry("option.new-mod.explosion_breaks.on", "Yes"),
			Map.entry("option.new-mod.explosion_breaks.off", "No"),
			Map.entry("death.attack.blackBlockPillar", "%1$s was crushed by a black block pillar"),
			Map.entry("death.attack.blackBlockPillar.player", "%1$s was crushed by a black block pillar while escaping %2$s"),
			Map.entry("death.attack.obsidianCube", "%1$s was crushed by the obsidian cube"),
			Map.entry("death.attack.obsidianCube.player", "%1$s was crushed by the obsidian cube while escaping %2$s"),
			Map.entry("death.attack.whiteBlockStar", "%1$s was crushed by the white block star"),
			Map.entry("death.attack.whiteBlockStar.player", "%1$s was crushed by the white block star while escaping %2$s"),
			Map.entry("death.attack.decay", "%1$s decayed away"),
			Map.entry("death.attack.decay.player", "%1$s decayed away while escaping %2$s"),
			Map.entry("effect.new-mod.mark_pillar_clash", "Black Mark 1"),
			Map.entry("effect.new-mod.mark_horizontal_pair", "Black Mark 2"),
			Map.entry("effect.new-mod.mark_vertical_pair", "Black Mark 3"),
			Map.entry("effect.new-mod.mark_black", "Black Mark B"),
			Map.entry("effect.new-mod.mark_white", "White Mark W"),
			Map.entry("effect.new-mod.decay", "Decay")
	);

	/**
	 * The Chinese column: {@code key -> 中文}.
	 *
	 * <p>Anything missing here (a brand new block, for instance) falls back to the prettified id, so the file
	 * never shows a raw translation key even before the Chinese name has been written. Adding a Chinese name for
	 * a new block or item is therefore a single line, added whenever convenient.
	 */
	private static final Map<String, String> CHINESE = Map.ofEntries(
			// Items
			Map.entry(ModItems.BLACK_BLOCK_STAR.getDescriptionId(), "黑方块之星"),
			Map.entry(ModItems.WHITE_BLOCK_STAR.getDescriptionId(), "白方块之星"),
			Map.entry(ModItems.OBSIDIAN_CUBE.getDescriptionId(), "黑曜石方"),

			// Blocks
			Map.entry(ModBlocks.BLACK_BLOCK.getDescriptionId(), "黑方块"),
			Map.entry(ModBlocks.WHITE_BLOCK.getDescriptionId(), "白方块"),

			// Entity types (the pillars, the black hole and the helpers)
			Map.entry("entity.new-mod.short_black_block_pillar", "短黑方块柱"),
			Map.entry("entity.new-mod.obsidian_pillar", "黑曜石柱"),
			Map.entry("entity.new-mod.long_black_block_pillar", "长黑方块柱"),
			Map.entry("entity.new-mod.eternal_black_block_pillar", "永黑方块柱"),
			Map.entry("entity.new-mod.black_hole", "黑洞"),
			Map.entry("entity.new-mod.horizontal_axis", "水平空白轴"),
			Map.entry("entity.new-mod.vertical_axis", "竖直空白轴"),
			Map.entry("entity.new-mod.horizontal_axis_pillar", "水平对撞柱"),
			Map.entry("entity.new-mod.vertical_axis_pillar", "竖直对撞柱"),
			Map.entry("entity.new-mod.barrage_pillar", "弹幕柱"),
			Map.entry("entity.new-mod.dash_pillar", "冲刺柱"),
			Map.entry("entity.new-mod.pillar_barrage", "黑方块柱弹幕"),
			Map.entry("entity.new-mod.mark_lock", "标记禁锢"),
			Map.entry("entity.new-mod.white_pillar", "白方块柱"),

			// Status effects (the mark icons and the decay effect)
			Map.entry("effect.new-mod.mark_pillar_clash", "黑标记 1"),
			Map.entry("effect.new-mod.mark_horizontal_pair", "黑标记 2"),
			Map.entry("effect.new-mod.mark_vertical_pair", "黑标记 3"),
			Map.entry("effect.new-mod.mark_black", "黑标记 B"),
			Map.entry("effect.new-mod.mark_white", "白标记 W"),
			Map.entry("effect.new-mod.decay", "消散"),

			// Creative tab, key bindings, settings screen
			Map.entry("itemGroup.new-mod.new", "NEW"),
			Map.entry("key.categories.new-mod.main", "New Mod"),
			Map.entry("key.new-mod.settings", "打开设置页"),
			Map.entry("screen.new-mod.settings.title", "New Mod 设置"),
			Map.entry("option.new-mod.break_bedrock", "基岩：%s"),
			Map.entry("option.new-mod.break_bedrock.on", "破坏"),
			Map.entry("option.new-mod.break_bedrock.off", "无视"),
			Map.entry("option.new-mod.explosion_breaks", "爆炸破坏方块：%s"),
			Map.entry("option.new-mod.explosion_breaks.on", "是"),
			Map.entry("option.new-mod.explosion_breaks.off", "否"),

			// Death messages
			Map.entry("death.attack.blackBlockPillar", "%1$s 被黑方块柱碾碎"),
			Map.entry("death.attack.blackBlockPillar.player", "%1$s 在逃离 %2$s 时被黑方块柱碾碎"),
			Map.entry("death.attack.obsidianCube", "%1$s 被黑曜石方碾碎"),
			Map.entry("death.attack.obsidianCube.player", "%1$s 在逃离 %2$s 时被黑曜石方碾碎"),
			Map.entry("death.attack.whiteBlockStar", "%1$s 被白方块之星碾碎"),
			Map.entry("death.attack.whiteBlockStar.player", "%1$s 在逃离 %2$s 时被白方块之星碾碎"),
			Map.entry("death.attack.decay", "%1$s 消散了"),
			Map.entry("death.attack.decay.player", "%1$s 在逃离 %2$s 时消散了")
	);

	/** English file. */
	public static FabricLanguageProvider english(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		return new Provider(output, "en_us", registries, false);
	}

	/** Chinese file: the same key set, with the {@link #CHINESE} column on top. */
	public static FabricLanguageProvider chinese(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		return new Provider(output, "zh_cn", registries, true);
	}

	/**
	 * Every key this mod names, with its English text.
	 *
	 * <p>Block, item and entity type names come straight from the registers, which is what makes a new block
	 * need no language line: {@code new-mod:white_block} is "White Block" here automatically.
	 */
	private static Map<String, String> englishNames() {
		Map<String, String> names = new LinkedHashMap<>();

		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);

			if (NewMod.MOD_ID.equals(id.getNamespace())) {
				names.put(item.getDescriptionId(), humanise(id.getPath()));
			}
		}

		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);

			if (NewMod.MOD_ID.equals(id.getNamespace())) {
				names.put(block.getDescriptionId(), humanise(id.getPath()));
			}
		}

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);

			if (NewMod.MOD_ID.equals(id.getNamespace())) {
				names.put(type.getDescriptionId(), humanise(id.getPath()));
			}
		}

		names.putAll(ENGLISH);
		return names;
	}

	/** {@code long_black_block_pillar} -> {@code Long Black Block Pillar}; the fallback for content with no line yet. */
	private static String humanise(String path) {
		StringBuilder name = new StringBuilder();

		for (String word : path.split("_")) {
			if (word.isEmpty()) {
				continue;
			}

			if (!name.isEmpty()) {
				name.append(' ');
			}

			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}

		return name.toString();
	}

	/** One language file: the English column is the base, and the Chinese file overlays {@link #CHINESE} on it. */
	private static final class Provider extends FabricLanguageProvider {
		private final boolean chinese;

		private Provider(FabricPackOutput output, String languageCode, CompletableFuture<HolderLookup.Provider> registries, boolean chinese) {
			super(output, languageCode, registries);
			this.chinese = chinese;
		}

		@Override
		public void generateTranslations(HolderLookup.Provider registries, TranslationBuilder builder) {
			Map<String, String> names = englishNames();

			for (Map.Entry<String, String> entry : names.entrySet()) {
				builder.add(entry.getKey(), this.chinese ? CHINESE.getOrDefault(entry.getKey(), entry.getValue()) : entry.getValue());
			}
		}
	}
}
