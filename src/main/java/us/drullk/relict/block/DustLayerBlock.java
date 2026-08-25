package us.drullk.relict.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class DustLayerBlock extends AbstractRelictLayerBlock {

    public static final MapCodec<DustLayerBlock> CODEC = simpleCodec(DustLayerBlock::new);

    public static final BooleanProperty TRODDEN = BooleanProperty.create("trodden");

    public DustLayerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1).setValue(TRODDEN, false));
    }

    @Override
    public MapCodec<DustLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockState stack(BlockState existing, int newLayers) {
        // Burial resets the block's disturbance
        return existing.setValue(LAYERS, newLayers).setValue(TRODDEN, false);
    }

    /* FIXME Keep or remove. This feature wasn't as cool as I pictured it to be
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
        if (!level.isClientSide() && !onState.getValue(TRODDEN)) {
            level.setBlock(pos, onState.setValue(TRODDEN, true), Block.UPDATE_CLIENTS);
        }

        super.stepOn(level, pos, onState, entity);
    }*/

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        DustLayerWeather.tick(this, state, level, pos, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TRODDEN);
    }

}
