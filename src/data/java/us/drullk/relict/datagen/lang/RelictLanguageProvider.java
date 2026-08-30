package us.drullk.relict.datagen.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredBlock;
import us.drullk.relict.Relict;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;
import us.drullk.relict.init.worldgen.RelictBiomes;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class RelictLanguageProvider extends LanguageProvider {

    private final Map<String, Component> entries = new LinkedHashMap<>();

    private final PackOutput output;

    public RelictLanguageProvider(PackOutput output) {
        super(output, Relict.MODID, "en_us");
        this.output = output;
    }

    public Map<String, Component> entries() {
        return entries;
    }

    @Override
    public void add(String key, Component value) {
        super.add(key, value);
        entries.put(key, value);
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.relict", "Relict");

        addItem(RelictItems.VITAL_VIZARD, "Vital Vizard");
        addItem(RelictItems.SPENT_VIZARD, "Spent Vizard");
        add("relict.vizard.warning", "%s%% air remaining");
        add("relict.vizard.inert", "Air depleted");

        addItem(RelictItems.RANGING_CAISSON, "Ranging Caisson");
        add("item.relict.stored_charges.charge", "Charge: %s / %s");

        addItem(RelictItems.RESTLESS_STRIDERS, "Restless Striders");
        addItem(RelictItems.GROUNDING_TREADS, "Grounding Treads");

        addItem(RelictItems.SEISMIC_LOCATOR, "Seismic Locator");
        add("item.relict.seismic_locator.no_signal", "FIXME no seismic signal in range");

        addItem(RelictItems.WEATHERGLASS, "Patient Weatherglass");

        addItem(RelictItems.RUBBING, "Keypad Rubbing");
        add("item.relict.rubbing.tooltip", "A map for a code");

        addItem(RelictItems.BURNING_GLASS, "Burning Glass");
        add("item.relict.burning_glass.tooltip", "Smelts blocks in place, held under open daylight");

        addBlock(RelictBlocks.MARS_PORTAL, "Mars Portal");
        addBlock(RelictBlocks.CIPHER_CHEST, "Cipher Chest");
        addBlock(RelictBlocks.LAB_BLOCK, "Lab Block");
        addBlock(RelictBlocks.LAB_SLAB, "Lab Slab");
        addBlock(RelictBlocks.LAB_STAIRS, "Lab Stairs");
        addBlock(RelictBlocks.LAB_SHAFT, "Lab Shaft");
        addBlock(RelictBlocks.LAB_MAST, "Lab Mast");

        addStoneFamily("Ochre", RelictBlocks.OCHRE, RelictBlocks.OCHRE_SLAB, RelictBlocks.OCHRE_STAIRS, RelictBlocks.OCHRE_WALL);
        addStoneFamily("Polished Ochre", RelictBlocks.POLISHED_OCHRE, RelictBlocks.POLISHED_OCHRE_SLAB, RelictBlocks.POLISHED_OCHRE_STAIRS, RelictBlocks.POLISHED_OCHRE_WALL);
        addStoneFamily("Serpentine", RelictBlocks.SERPENTINE, RelictBlocks.SERPENTINE_SLAB, RelictBlocks.SERPENTINE_STAIRS, RelictBlocks.SERPENTINE_WALL);
        addStoneFamily("Polished Serpentine", RelictBlocks.POLISHED_SERPENTINE, RelictBlocks.POLISHED_SERPENTINE_SLAB, RelictBlocks.POLISHED_SERPENTINE_STAIRS, RelictBlocks.POLISHED_SERPENTINE_WALL);

        addBlock(RelictBlocks.ROVER_WHEEL, "Rover Wheel");
        addBlock(RelictBlocks.SOLAR_PANEL, "Solar Panel");
        addBlock(RelictBlocks.SOLAR_PANEL_SPRINKLED, "Sprinkled Solar Panel");
        addBlock(RelictBlocks.SOLAR_PANEL_DUSTED, "Dusted Solar Panel");
        addBlock(RelictBlocks.SOLAR_PANEL_SANDED, "Sanded Solar Panel");
        addBlock(RelictBlocks.DUST_LAYER, "Red Sand Layer");
        addBlock(RelictBlocks.DRY_SNOW, "Dry Snow");
        addBlock(RelictBlocks.DRY_SNOW_LAYER, "Dry Snow Layer");
        addBlock(RelictBlocks.BASALT_SAND, "Basalt Sand");

        addBiome(RelictBiomes.WRINKLE_PLAINS, "Wrinkle Plains");
        addBiome(RelictBiomes.RUSTED_DUNES, "Rusted Dunes");
        addBiome(RelictBiomes.FRETTED_MESAS, "Fretted Mesas");
        addBiome(RelictBiomes.SHATTERED_HIGHLANDS, "Shattered Highlands");
        addBiome(RelictBiomes.BASALT_CAVES, "Basalt Caves");
        addBiome(RelictBiomes.CALCITE_CAVES, "Calcite Caves");
        addBiome(RelictBiomes.ICE_CAVES, "Ice Caves");
        addBiome(RelictBiomes.SULFUR_CAVES, "Sulfur Caves");

        add("attribute.name.relict.mars_life_support", "Mars Life Support");
        add("attribute.name.relict.nausea_immunity", "Nausea Immunity");
        add("attribute.name.relict.electric_damage", "Electric Damage");

        add("death.attack.relict.mars_unbreathable", "%1$s tried to breathe Mars");
        add("death.attack.relict.air_depleted", "%1$s's bubble popped on Mars");
        add("death.attack.relict.storm_discharge", "%1$s was shocked by a dust storm");

        add("relict.configuration.title", "Relict Configs");
        add("relict.configuration.section.relict.common.toml", "Relict Configs");
        add("relict.configuration.section.relict.common.toml.title", "Relict Mod Configs");
        add("relict.configuration.items", "Item List");
        add("relict.configuration.logDirtBlock", "Log Dirt Block");
        add("relict.configuration.magicNumberIntroduction", "Magic Number Text");
        add("relict.configuration.magicNumber", "Magic Number");

        add("gamerule.category.relict.atmosphere", "Atmosphere");
        add("gamerule.relict.atmosphere_cycle_tenth_sols", "Atmosphere cycle length (tenths of a sol)");
        add("gamerule.relict.storm_frequency_percent", "Storm chance per stay (percent)");
        add("gamerule.relict.storm_damage", "Storm discharge damage");
    }

    private void addStoneFamily(String displayName, DeferredBlock<? extends Block> base,
            DeferredBlock<? extends Block> slab, DeferredBlock<? extends Block> stairs, DeferredBlock<? extends Block> wall) {
        addBlock(base, displayName);
        addBlock(slab, displayName + " Slab");
        addBlock(stairs, displayName + " Stairs");
        addBlock(wall, displayName + " Wall");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        CompletableFuture<?> run = super.run(cache);

        CompletableFuture<?> upsideDown = saveUpsideDown(cache, this.output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(Relict.MODID).resolve("lang").resolve("en_ud.json"));

        return CompletableFuture.allOf(run, upsideDown);
    }

    // Copied from NeoForge's LanguageProvider source, as ATs do not apply to NF
    private CompletableFuture<?> saveUpsideDown(CachedOutput cache, Path target) {
        Map<String, Component> upsideDownEntries = new HashMap<>(this.entries);
        upsideDownEntries.replaceAll((_, localized) -> Component.literal(UpsideDownText.flip(localized.getString())));
        final JsonElement json = Codec.unboundedMap(Codec.STRING, ComponentSerialization.CODEC).encode(upsideDownEntries, JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        return DataProvider.saveStable(cache, json, target);
    }
}
