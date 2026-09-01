package us.drullk.relict.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import us.drullk.relict.init.worldgen.RelictDimension;

import java.util.Map;

/**
 * [VANILLACOPY] {@code NetherPortalBlock}'s shape, axis handling and particle/sound flavour; the
 * destination search is new (see {@link RelictPortalNetwork}, {@link RelictPortalForcer}) since there is
 * no frame to rediscover an existing portal from.
 */
public class RelictPortalBlock extends Block implements Portal {

    public static final MapCodec<RelictPortalBlock> CODEC = simpleCodec(RelictPortalBlock::new);

    public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final Map<Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));

    private static final int SEARCH_RADIUS = 128;

    private static final DustParticleOptions OCHRE_DUST = new DustParticleOptions(0xCC7722, 1.0F);

    public RelictPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Axis.X));
    }

    @Override
    public MapCodec<RelictPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    // [VANILLACOPY] NetherPortalBlock#updateShape, retargeted at RelictPortalShape
    @Override
    protected BlockState updateShape(
            BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random
    ) {
        Axis updateAxis = directionToNeighbour.getAxis();
        Axis axis = state.getValue(AXIS);
        boolean wrongAxis = axis != updateAxis && updateAxis.isHorizontal();
        return !wrongAxis && !neighbourState.is(this) && !RelictPortalShape.isComplete(level, pos, axis)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    // [VANILLACOPY]
    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return entity instanceof Player player
                ? Math.max(0, level.getGameRules().get(player.getAbilities().invulnerable
                        ? GameRules.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY
                        : GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY))
                : 0;
    }

    @Nullable
    @Override
    public TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        ResourceKey<Level> targetDimension = currentLevel.dimension() == RelictDimension.MARS_LEVEL ? Level.OVERWORLD : RelictDimension.MARS_LEVEL;
        ServerLevel targetLevel = currentLevel.getServer().getLevel(targetDimension);
        if (targetLevel == null) {
            return null;
        }

        RelictPortalNetwork network = RelictPortalNetwork.get(currentLevel);
        network.remember(GlobalPos.of(currentLevel.dimension(), portalEntryPos));

        double teleportationScale = DimensionType.getTeleportationScale(currentLevel.dimensionType(), targetLevel.dimensionType());
        BlockPos approximateExitPos = targetLevel.getWorldBorder()
                .clampToBounds(entity.getX() * teleportationScale, entity.getY(), entity.getZ() * teleportationScale);

        Axis sourceAxis = currentLevel.getBlockState(portalEntryPos).getOptionalValue(AXIS).orElse(Axis.X);

        BlockPos exitPos = network.findNearest(targetDimension, approximateExitPos, SEARCH_RADIUS,
                        candidate -> targetLevel.getBlockState(candidate.pos()).is(this))
                .map(GlobalPos::pos)
                .orElseGet(() -> {
                    BlockPos created = RelictPortalForcer.createLandingPortal(targetLevel, approximateExitPos, sourceAxis);
                    network.remember(GlobalPos.of(targetDimension, created));
                    return created;
                });

        return new TeleportTransition(
                targetLevel,
                Vec3.atBottomCenterOf(exitPos),
                Vec3.ZERO,
                0.0F,
                0.0F,
                Relative.union(Relative.DELTA, Relative.ROTATION),
                TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)
        );
    }

    @Override
    public Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double xa = (random.nextFloat() - 0.5) * 0.5;
            double ya = (random.nextFloat() - 0.5) * 0.5;
            double za = (random.nextFloat() - 0.5) * 0.5;
            int flip = random.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this)) {
                x = pos.getX() + 0.5 + 0.25 * flip;
                xa = random.nextFloat() * 2.0F * flip;
            } else {
                z = pos.getZ() + 0.5 + 0.25 * flip;
                za = random.nextFloat() * 2.0F * flip;
            }

            level.addParticle(OCHRE_DUST, x, y, z, xa, ya, za);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> switch (state.getValue(AXIS)) {
                case X -> state.setValue(AXIS, Axis.Z);
                case Z -> state.setValue(AXIS, Axis.X);
                default -> state;
            };
            default -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

}
