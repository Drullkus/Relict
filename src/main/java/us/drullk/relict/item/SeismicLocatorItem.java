package us.drullk.relict.item;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.RelictTags;

import java.util.Optional;

public class SeismicLocatorItem extends CompassItem {

    private static final int SEARCH_RADIUS_CHUNKS = 10;

    public SeismicLocatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || stack.has(DataComponents.LODESTONE_TRACKER)) {
            return InteractionResult.PASS;
        }

        HolderSet<Structure> leads = serverLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(RelictTags.SEISMIC_LOCATED);
        @Nullable Pair<BlockPos, net.minecraft.core.Holder<Structure>> found = serverLevel.getChunkSource().getGenerator()
                .findNearestMapStructure(serverLevel, leads, player.blockPosition(), SEARCH_RADIUS_CHUNKS, false);

        if (found == null) {
            player.sendOverlayMessage(Component.translatable("item.relict.seismic_locator.no_signal"));
            return InteractionResult.FAIL;
        }

        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(serverLevel.dimension(), found.getFirst())), false));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /**
     * [VANILLACOPY] net.minecraft.world.item.Item#getName. {@link CompassItem#getName} renames any stack
     * holding {@code LODESTONE_TRACKER} to "Lodestone Compass"; this keeps the item's own name instead.
     */
    @Override
    public Component getName(ItemStack stack) {
        return stack.getComponents().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
    }

    // Do not foil when coordinates are loaded
    public boolean isFoil(ItemStack itemStack) {
        return itemStack.isEnchanted();
    }

}
