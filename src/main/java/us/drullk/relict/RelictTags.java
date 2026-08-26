package us.drullk.relict;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.Structure;

public class RelictTags {

    public static final TagKey<DamageType> IS_ELECTRIC = TagKey.create(Registries.DAMAGE_TYPE, Relict.id("is_electric"));

    public static final TagKey<DimensionType> IS_MARS = TagKey.create(Registries.DIMENSION_TYPE, Relict.id("is_mars"));

    public static final TagKey<DimensionType> HAS_MARS_ATMOSPHERE = TagKey.create(Registries.DIMENSION_TYPE, Relict.id("has_mars_atmosphere"));

    public static final TagKey<Item> REPAIRS_SERVICE_ARMOR = TagKey.create(Registries.ITEM, Relict.id("repairs_service_armor"));

    public static final TagKey<Block> SPELEOTHEM_REPLACEABLE = TagKey.create(Registries.BLOCK, Relict.id("speleothem_replaceable"));

    public static final TagKey<Biome> HAS_STRUCTURE_PORTAL_RUIN = TagKey.create(Registries.BIOME, Relict.id("has_structure/portal_ruin"));

    public static final TagKey<Biome> HAS_STRUCTURE_UNMANNED_WRECK = TagKey.create(Registries.BIOME, Relict.id("has_structure/unmanned_wreck"));

    public static final TagKey<Biome> HAS_STRUCTURE_RUIN_A = TagKey.create(Registries.BIOME, Relict.id("has_structure/ruin_a"));

    public static final TagKey<Structure> SEISMIC_LOCATED = TagKey.create(Registries.STRUCTURE, Relict.id("locators/seismic"));

    public static final TagKey<Block> DRIPSTONE_REPLACEABLE = TagKey.create(Registries.BLOCK, Relict.id("dripstone_replaceable"));

    public static final TagKey<Block> BASE_STONE_MARS = TagKey.create(Registries.BLOCK, Relict.id("base_stone_mars"));

    public static final TagKey<Block> SANDS_BASALT = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "sands/basalt"));

}
