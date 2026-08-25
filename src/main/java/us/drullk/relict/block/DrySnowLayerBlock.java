package us.drullk.relict.block;

import com.mojang.serialization.MapCodec;

// TODO Increase layers when atmosphere is thinning and lose layers when atmosphere is filling
public class DrySnowLayerBlock extends AbstractRelictLayerBlock {

    public static final MapCodec<DrySnowLayerBlock> CODEC = simpleCodec(DrySnowLayerBlock::new);

    public DrySnowLayerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<DrySnowLayerBlock> codec() {
        return CODEC;
    }

}
