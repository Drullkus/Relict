package us.drullk.relict.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.RelictTags;
import us.drullk.relict.atmosphere.AtmosphereCurve;
import us.drullk.relict.atmosphere.AtmosphereCurve.CycleGeometry;
import us.drullk.relict.atmosphere.RelictAtmosphereData;
import us.drullk.relict.atmosphere.RelictAtmosphereServer;
import us.drullk.relict.atmosphere.StormPhase;
import us.drullk.relict.block.wreck.SolarPanelDecay;
import us.drullk.relict.init.RelictGameRules;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A solar concentrator: aimed and held on a target under open sky in daylight, it smelts blocks where they
 * lie, or cooks/burns a dropped item stack. Bow/food-style continuous use rather than an instant right-click
 * -- {@link #getUseAnimation} and {@link #getUseDuration} drive vanilla's own hold-to-charge loop
 * ({@code LivingEntity#updateUsingItem}), so an early release naturally falls through to the no-op default
 * {@link Item#releaseUsing} and only a completed hold reaches {@link #finishUsingItem}.
 * <p>
 * The target is never locked at the start of the hold: every tick -- including the completing one --
 * re-aims fresh off the player's current look direction, the same way vanilla's own {@code BrushItem} redoes
 * its hit test each tick rather than remembering the first one. Looking away mid-charge does not cancel the
 * hold; it only skips that tick's smoke and, if still off-target when the duration runs out, the charge
 * completes with no effect.
 * <p>
 * The sun gate is checked twice: once up front in {@link #use}, where a closed gate plays the quiet inert
 * cue and refuses to start the charge at all (so a doomed hold is never wasted), and again at
 * {@link #finishUsingItem}, in case the sky or storm state changed during the hold -- that second check is
 * silent, matching how an off-target completion is silent, since the loud "gated" feedback belongs to the
 * moment the player commits, not to a hold that drifted out from under them.
 */
public class BurningGlassItem extends Item {

    /** 20 seconds at 20 ticks/sol-independent real-time tick rate. Fire Aspect halves this per level (see {@link #getUseDuration}). */
    private static final int BASE_USE_DURATION_TICKS = 20 * 20;

    /** Cadence for the charging smoke -- every tick would flood the particle log for no visible gain. */
    private static final int SMOKE_INTERVAL_TICKS = 4;

    private static final String TOOLTIP_KEY = "item.relict.burning_glass.tooltip";

    private static final Predicate<Entity> DROPPED_ITEMS = entity -> entity instanceof ItemEntity && entity.isAlive();

    public BurningGlassItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------------------------------- use lifecycle

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }

        BlockPos targetPos = targetPos(aim(player));
        if (targetPos == null) {
            return InteractionResult.PASS;
        }

        if (!isSunGateOpen(serverLevel, targetPos)) {
            playInertFeedback(serverLevel, targetPos);
            return InteractionResult.FAIL;
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        int fireAspectLevel = Mth.clamp(fireAspectLevel(stack, user.level()), 0, 2);
        return BASE_USE_DURATION_TICKS >> fireAspectLevel;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int ticksRemaining) {
        if (ticksRemaining % SMOKE_INTERVAL_TICKS != 0 || !(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof Player player)) {
            return;
        }

        HitResult hit = aim(player);
        if (targetPos(hit) == null) {
            return;
        }

        Vec3 clip = hit.getLocation();
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, clip.x, clip.y, clip.z, 1, 0.02, 0.05, 0.02, 0.01);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof Player player)) {
            return stack;
        }

        HitResult hit = aim(player);
        BlockPos targetPos = targetPos(hit);
        if (targetPos == null || !isSunGateOpen(serverLevel, targetPos)) {
            return stack;
        }

        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemEntity itemEntity) {
            cookOrBurnDroppedItem(serverLevel, itemEntity);
        } else if (hit instanceof BlockHitResult blockHit) {
            smeltBlockInPlace(serverLevel, blockHit);
        }

        return stack;
    }

    // ---------------------------------------------------------------------------------------------------- aim

    /**
     * Combined block-or-item raycast along the player's view, out to their own block interaction range.
     * {@link ProjectileUtil#getHitResultOnViewVector} already gives an entity hit priority over a farther
     * block hit -- the same "item before block" rule the dropped-item path needs -- so no separate priority
     * check is written here.
     */
    private static HitResult aim(Player player) {
        return ProjectileUtil.getHitResultOnViewVector(player, DROPPED_ITEMS, player.blockInteractionRange());
    }

    /** The position the sun gate and the smelt/cook target both key off; {@code null} for a clean miss. */
    @Nullable
    private static BlockPos targetPos(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ItemEntity itemEntity) {
            return itemEntity.blockPosition();
        }
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            // Targeted block pos from blockHit technically cannot see the sun, so the solution is to get the blockpos that's facing the block face being cooked.
            return blockHit.getBlockPos().relative(blockHit.getDirection());
        }
        return null;
    }

    // --------------------------------------------------------------------------------------------- sun gate

    /**
     * Open sky and daylight, everywhere; additionally not storm-dimmed on Mars.
     */
    private static boolean isSunGateOpen(ServerLevel level, BlockPos targetPos) {
        if (!level.canSeeSky(targetPos) || !level.isBrightOutside()) {
            return false;
        }

        return !level.dimensionTypeRegistration().is(RelictTags.HAS_MARS_ATMOSPHERE) || !isMarsStormDimmed(level);
    }

    private static boolean isMarsStormDimmed(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long totalTicks = RelictAtmosphereServer.marsTotalTicks(server);
        GameRules rules = server.getGlobalGameRules();
        int cycleTenthSols = rules.get(RelictGameRules.ATMOSPHERE_CYCLE_TENTH_SOLS.get());
        CycleGeometry geo = AtmosphereCurve.geometryAt(totalTicks, RelictDimension.SOL_TICKS, cycleTenthSols);
        RelictAtmosphereData data = server.getDataStorage().computeIfAbsent(RelictAtmosphereData.TYPE);
        StormPhase phase = AtmosphereCurve.stormAt(totalTicks, geo, data.schedule()).phase();
        return SolarPanelDecay.isStormDepositingDust(phase);
    }

    private static int fireAspectLevel(ItemStack stack, Level level) {
        Holder<Enchantment> fireAspect = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT);
        return EnchantmentHelper.getItemEnchantmentLevel(fireAspect, stack);
    }

    // ------------------------------------------------------------------------------------------------ smelting

    /**
     * Hardcoded rather than a live {@code RecipeManager} query -- unlike the dropped-item path below, most
     * smelting results here (charcoal, ingots) are not blocks, so "the block's item form has a smelting
     * recipe" would still need a second rule for what to do with a non-block result. Listing the pairs
     * directly keeps both cases in one place and reads as a trivial swap for a datapack recipe type later:
     * one row per {@code (input predicate, output ItemLike)}.
     */
    private record Conversion(Predicate<BlockState> matches, Supplier<ItemStack> result) {

        static Conversion of(Block input, ItemLike output) {
            return new Conversion(state -> state.is(input), () -> new ItemStack(output));
        }

        static Conversion ofTag(net.minecraft.tags.TagKey<Block> input, ItemLike output) {
            return new Conversion(state -> state.is(input), () -> new ItemStack(output));
        }

    }

    private static final List<Conversion> CONVERSIONS = List.of(
            Conversion.of(Blocks.SAND, Blocks.GLASS),
            Conversion.of(Blocks.RED_SAND, Blocks.GLASS),
            Conversion.of(Blocks.CLAY, Blocks.TERRACOTTA),
            Conversion.ofTag(BlockTags.LOGS, Items.CHARCOAL),
            Conversion.of(Blocks.IRON_ORE, Items.IRON_INGOT),
            Conversion.of(Blocks.DEEPSLATE_IRON_ORE, Items.IRON_INGOT),
            Conversion.of(Blocks.GOLD_ORE, Items.GOLD_INGOT),
            Conversion.of(Blocks.DEEPSLATE_GOLD_ORE, Items.GOLD_INGOT),
            Conversion.of(Blocks.NETHER_GOLD_ORE, Items.GOLD_INGOT),
            Conversion.of(Blocks.COPPER_ORE, Items.COPPER_INGOT),
            Conversion.of(Blocks.DEEPSLATE_COPPER_ORE, Items.COPPER_INGOT)
    );

    /**
     * A block outside {@link #CONVERSIONS} is left untouched -- no consumption, no feedback beyond whatever
     * the sun gate already gave at the start of the hold.
     */
    private static void smeltBlockInPlace(ServerLevel level, BlockHitResult blockHit) {
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        Optional<ItemStack> result = CONVERSIONS.stream()
                .filter(conversion -> conversion.matches().test(state))
                .findFirst()
                .map(conversion -> conversion.result().get());
        if (result.isEmpty()) {
            return;
        }

        ItemStack stack = result.get();
        if (stack.getItem() instanceof BlockItem blockItem) {
            level.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
        } else {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            Block.popResource(level, pos, stack);
        }

        playSmeltFeedback(level, pos);
    }

    /**
     * The whole stack converts in one stroke -- the entity's own stack is replaced (cook) or the entity is
     * discarded outright (burn), never split into per-item events.
     */
    private static void cookOrBurnDroppedItem(ServerLevel level, ItemEntity itemEntity) {
        ItemStack original = itemEntity.getItem();
        SingleRecipeInput input = new SingleRecipeInput(original.copyWithCount(1));
        Optional<ItemStack> smeltedUnit = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, input, level)
                .map(recipe -> recipe.value().assemble(input))
                .filter(unit -> !unit.isEmpty());

        BlockPos pos = itemEntity.blockPosition();
        if (smeltedUnit.isPresent()) {
            ItemStack whole = smeltedUnit.get().copy();
            whole.setCount(whole.getCount() * original.getCount());
            itemEntity.setItem(whole);
            playSmeltFeedback(level, pos);
        } else {
            itemEntity.discard();
            playBurnFeedback(level, pos);
        }
    }

    // -------------------------------------------------------------------------------------------- feedback

    private static void playInertFeedback(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 0.6F);
    }

    private static void playSmeltFeedback(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.02);
    }

    private static void playBurnFeedback(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GENERIC_BURN, SoundSource.BLOCKS, 0.7F, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
    }

    // -------------------------------------------------------------------------------------------- tooltip

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag flag) {
        tooltipAdder.accept(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }

}
