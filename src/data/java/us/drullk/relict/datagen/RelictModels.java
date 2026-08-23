package us.drullk.relict.datagen;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.Item;
import us.drullk.relict.Relict;
import us.drullk.relict.client.item.WeatherglassCountdownProperty;
import us.drullk.relict.client.item.WeatherglassFace;
import us.drullk.relict.client.item.WeatherglassFaceProperty;
import us.drullk.relict.init.RelictBlocks;
import us.drullk.relict.init.RelictItems;

import java.util.ArrayList;
import java.util.List;

public class RelictModels extends ModelProvider {

    // Weatherglass tint palette: the orb shows where you are, the meter is forecast
    private static final int TINT_OVERWORLD_CLEAR = 0x4B95D8;
    private static final int TINT_MARS_CLEAR = 0xD87E4B;
    private static final int TINT_RAIN = 0x1F3D59;
    private static final int TINT_THUNDER = 0x9A50E5;
    private static final int TINT_DUST_STORM = 0xE5506B;
    private static final int TINT_ATMO_VACUUM = 0x555555;

    private static final TextureSlot WEATHERGLASS_METER_SLOT = TextureSlot.create("layer3");
    private static final ModelTemplate WEATHERGLASS_METER_TEMPLATE = ModelTemplates.createItem("generated", TextureSlot.LAYER0, TextureSlot.LAYER1, TextureSlot.LAYER2, WEATHERGLASS_METER_SLOT);

    private final float[] weatherGlassCountdownThresholds;

    public RelictModels(PackOutput output) {
        super(output, Relict.MODID);

        float[] floats = new float[8];
        float step = 1f / floats.length;

        for (int i = 0; i < floats.length; i++) {
            floats[i] = step * i;
        }

        this.weatherGlassCountdownThresholds = floats;
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // FIXME replace nether_portal placeholder for custom
        Identifier portalModel = ModelTemplates.CUBE_ALL.create(RelictBlocks.MARS_PORTAL.get(),
                TextureMapping.cube(new Material(Identifier.withDefaultNamespace("block/nether_portal"))), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(RelictBlocks.MARS_PORTAL.get(),
                new MultiVariant(WeightedList.of(new Variant(portalModel)))));

        itemModels.generateDynamicTrimmableItem(RelictItems.VITAL_VIZARD.get(), itemModels.createFlatItemModel(RelictItems.VITAL_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);

        itemModels.generateFlatItem(RelictItems.RANGING_CAISSON.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(RelictItems.RESTLESS_STRIDERS.get(), ModelTemplates.FLAT_ITEM);

        itemModels.generateDynamicTrimmableItem(RelictItems.GROUNDING_TREADS.get(), itemModels.createFlatItemModel(RelictItems.GROUNDING_TREADS.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_BOOTS);

        itemModels.generateDynamicTrimmableItem(RelictItems.SPENT_VIZARD.get(), itemModels.createFlatItemModel(RelictItems.SPENT_VIZARD.get(), ModelTemplates.FLAT_ITEM), ItemModelGenerators.TRIM_PREFIX_HELMET);

        itemModels.generateFlatItem(RelictItems.SEISMIC_LOCATOR.get(), ModelTemplates.FLAT_ITEM);

        this.generateWeatherglassItem(itemModels);
    }

    private void generateWeatherglassItem(ItemModelGenerators itemModels) {
        Item weatherglass = RelictItems.WEATHERGLASS.get();

        Material foot = weatherglassMaterial("weatherglass_foot");
        Material orb = weatherglassMaterial("weatherglass_orb");
        Material streaks = weatherglassMaterial("weatherglass_streaks");

        Identifier clearModel = Relict.id("item/weatherglass/clear");
        itemModels.generateLayeredItem(clearModel, foot, orb, streaks);

        Identifier[] meterModels = new Identifier[this.weatherGlassCountdownThresholds.length];
        for (int frame = 0; frame < meterModels.length; frame++) {
            Identifier meterModel = Relict.id("item/weatherglass/meter_" + (frame + 1));
            Material meter = weatherglassMaterial("weatherglass_meter_" + (frame + 1));
            TextureMapping mapping = new TextureMapping()
                    .put(TextureSlot.LAYER0, foot)
                    .put(TextureSlot.LAYER1, orb)
                    .put(TextureSlot.LAYER2, streaks)
                    .put(WEATHERGLASS_METER_SLOT, meter);
            WEATHERGLASS_METER_TEMPLATE.create(meterModel, mapping, itemModels.modelOutput);
            meterModels[frame] = meterModel;
        }

        ItemModel.Unbaked marsClear = tintedClear(clearModel, TINT_MARS_CLEAR);
        ItemModel.Unbaked clearDefault = tintedClear(clearModel, TINT_OVERWORLD_CLEAR);

        itemModels.itemModelOutput.accept(weatherglass, ItemModelUtils.select(new WeatherglassFaceProperty(), clearDefault, List.of(
                ItemModelUtils.when(WeatherglassFace.MARS_CLEAR, marsClear),
                ItemModelUtils.when(WeatherglassFace.CLEAR_DEFAULT, clearDefault),
                ItemModelUtils.when(WeatherglassFace.RAIN_INTO, this.weatherglassCountdown(meterModels, TINT_OVERWORLD_CLEAR, ItemModelUtils.constantTint(TINT_RAIN))),
                ItemModelUtils.when(WeatherglassFace.THUNDER_INTO, this.weatherglassCountdown(meterModels, TINT_OVERWORLD_CLEAR, ItemModelUtils.constantTint(TINT_THUNDER))),
                ItemModelUtils.when(WeatherglassFace.MARS_STORM_INTO, this.weatherglassCountdown(meterModels, TINT_MARS_CLEAR, ItemModelUtils.constantTint(TINT_DUST_STORM))),
                ItemModelUtils.when(WeatherglassFace.RAIN_EXIT, this.weatherglassCountdown(meterModels, TINT_RAIN, ItemModelUtils.constantTint(TINT_OVERWORLD_CLEAR))),
                ItemModelUtils.when(WeatherglassFace.THUNDER_EXIT, this.weatherglassCountdown(meterModels, TINT_THUNDER, ItemModelUtils.constantTint(TINT_OVERWORLD_CLEAR))),
                ItemModelUtils.when(WeatherglassFace.MARS_STORM_EXIT, this.weatherglassCountdown(meterModels, TINT_DUST_STORM, ItemModelUtils.constantTint(TINT_MARS_CLEAR))),
                ItemModelUtils.when(WeatherglassFace.ATMO_THINNING, this.weatherglassCountdown(meterModels, TINT_MARS_CLEAR, ItemModelUtils.constantTint(TINT_ATMO_VACUUM))),
                ItemModelUtils.when(WeatherglassFace.ATMO_VACUUM, this.weatherglassCountdown(meterModels, TINT_ATMO_VACUUM, ItemModelGenerators.BLANK_LAYER)),
                ItemModelUtils.when(WeatherglassFace.ATMO_FILLING, this.weatherglassCountdown(meterModels, TINT_ATMO_VACUUM, ItemModelUtils.constantTint(TINT_MARS_CLEAR)))
        )));
    }

    private ItemModel.Unbaked weatherglassCountdown(Identifier[] meterModels, int orbTint, ItemTintSource meterTint) {
        List<RangeSelectItemModel.Entry> frames = new ArrayList<>();
        for (int i = 0; i < this.weatherGlassCountdownThresholds.length; i++) {
            ItemModel.Unbaked frame = ItemModelUtils.tintedModel(meterModels[i], ItemModelGenerators.BLANK_LAYER,
                    ItemModelUtils.constantTint(orbTint), ItemModelGenerators.BLANK_LAYER, meterTint);
            frames.add(ItemModelUtils.override(frame, this.weatherGlassCountdownThresholds[i]));
        }

        return ItemModelUtils.rangeSelect(new WeatherglassCountdownProperty(), frames);
    }

    private static ItemModel.Unbaked tintedClear(Identifier clearModel, int orbTint) {
        return ItemModelUtils.tintedModel(clearModel, ItemModelGenerators.BLANK_LAYER, ItemModelUtils.constantTint(orbTint), ItemModelGenerators.BLANK_LAYER);
    }

    private static Material weatherglassMaterial(String textureName) {
        return new Material(Relict.id("item/weatherglass/" + textureName));
    }

}
