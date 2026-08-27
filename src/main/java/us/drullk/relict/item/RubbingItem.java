package us.drullk.relict.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * A charcoal-rubbing image of the Agrippa square's 25 numbers, can be held and displayed like a map
 */
public class RubbingItem extends MapItem {

    public RubbingItem(Properties properties) {
        super(properties);
    }

    @Override
    protected MapItemSavedData getCustomMapData(ItemStack itemStack, Level level) {
        return RubbingMapData.get();
    }

    @Override
    public void inventoryTick(ItemStack itemStack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        // NO-OP map item logic
    }

    @Override
    public void update(Level level, Entity player, MapItemSavedData data) {
        // NO-OP map item logic, should any mods use this too
    }
}
