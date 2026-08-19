package us.drullk.relict;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;

public class RelictTags {

    public static final TagKey<DamageType> IS_ELECTRIC = TagKey.create(Registries.DAMAGE_TYPE, Relict.id("is_electric"));

    public static final TagKey<DimensionType> REQUIRES_MARS_LIFE_SUPPORT = TagKey.create(Registries.DIMENSION_TYPE, Relict.id("requires_mars_life_support"));

    public static final TagKey<Item> REPAIRS_SERVICE_ARMOR = TagKey.create(Registries.ITEM, Relict.id("repairs_service_armor"));

    public static final TagKey<Block> SPELEOTHEM_REPLACEABLE = TagKey.create(Registries.BLOCK, Relict.id("speleothem_replaceable"));

}
