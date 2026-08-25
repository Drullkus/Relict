package us.drullk.relict.datagen.models;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.RangeSelectItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import us.drullk.relict.Relict;
import us.drullk.relict.client.item.WeatherglassCountdownProperty;
import us.drullk.relict.client.item.WeatherglassFace;
import us.drullk.relict.client.item.WeatherglassFaceProperty;
import us.drullk.relict.init.RelictItems;

import java.util.ArrayList;
import java.util.List;

/** Item model for the Patient Weatherglass: a clear-sky orb layered item plus a range-selected countdown meter overlay. */
public final class WeatherglassModels {

    // Tint palette: the orb shows where you are, the meter is forecast.
    private static final int TINT_OVERWORLD_CLEAR = 0x4B95D8;
    private static final int TINT_MARS_CLEAR = 0xD87E4B;
    private static final int TINT_RAIN = 0x1F3D59;
    private static final int TINT_THUNDER = 0x9A50E5;
    private static final int TINT_DUST_STORM = 0xE5506B;
    private static final int TINT_ATMO_VACUUM = 0x555555;

    private static final TextureSlot METER_SLOT = TextureSlot.create("layer3");
    private static final ModelTemplate METER_TEMPLATE = ModelTemplates.createItem("generated", TextureSlot.LAYER0, TextureSlot.LAYER1, TextureSlot.LAYER2, METER_SLOT);

    private static final float[] COUNTDOWN_THRESHOLDS = countdownThresholds(8);

    private WeatherglassModels() {
    }

    public static void register(ItemModelGenerators itemModels) {
        Item weatherglass = RelictItems.WEATHERGLASS.get();

        Material foot = material("weatherglass_foot");
        Material orb = material("weatherglass_orb");
        Material streaks = material("weatherglass_streaks");

        Identifier clearModel = Relict.id("item/weatherglass/clear");
        itemModels.generateLayeredItem(clearModel, foot, orb, streaks);

        Identifier[] meterModels = new Identifier[COUNTDOWN_THRESHOLDS.length];
        for (int frame = 0; frame < meterModels.length; frame++) {
            Identifier meterModel = Relict.id("item/weatherglass/meter_" + (frame + 1));
            Material meter = material("weatherglass_meter_" + (frame + 1));
            TextureMapping mapping = new TextureMapping()
                    .put(TextureSlot.LAYER0, foot)
                    .put(TextureSlot.LAYER1, orb)
                    .put(TextureSlot.LAYER2, streaks)
                    .put(METER_SLOT, meter);
            METER_TEMPLATE.create(meterModel, mapping, itemModels.modelOutput);
            meterModels[frame] = meterModel;
        }

        ItemModel.Unbaked marsClear = tintedClear(clearModel, TINT_MARS_CLEAR);
        ItemModel.Unbaked clearDefault = tintedClear(clearModel, TINT_OVERWORLD_CLEAR);

        itemModels.itemModelOutput.accept(weatherglass, ItemModelUtils.select(new WeatherglassFaceProperty(), clearDefault, List.of(
                ItemModelUtils.when(WeatherglassFace.MARS_CLEAR, marsClear),
                ItemModelUtils.when(WeatherglassFace.CLEAR_DEFAULT, clearDefault),
                ItemModelUtils.when(WeatherglassFace.RAIN_INTO, countdown(meterModels, TINT_OVERWORLD_CLEAR, ItemModelUtils.constantTint(TINT_RAIN))),
                ItemModelUtils.when(WeatherglassFace.THUNDER_INTO, countdown(meterModels, TINT_OVERWORLD_CLEAR, ItemModelUtils.constantTint(TINT_THUNDER))),
                ItemModelUtils.when(WeatherglassFace.MARS_STORM_INTO, countdown(meterModels, TINT_MARS_CLEAR, ItemModelUtils.constantTint(TINT_DUST_STORM))),
                ItemModelUtils.when(WeatherglassFace.RAIN_EXIT, countdown(meterModels, TINT_RAIN, ItemModelUtils.constantTint(TINT_OVERWORLD_CLEAR))),
                ItemModelUtils.when(WeatherglassFace.THUNDER_EXIT, countdown(meterModels, TINT_THUNDER, ItemModelUtils.constantTint(TINT_OVERWORLD_CLEAR))),
                ItemModelUtils.when(WeatherglassFace.MARS_STORM_EXIT, countdown(meterModels, TINT_DUST_STORM, ItemModelUtils.constantTint(TINT_MARS_CLEAR))),
                ItemModelUtils.when(WeatherglassFace.ATMO_THINNING, countdown(meterModels, TINT_MARS_CLEAR, ItemModelUtils.constantTint(TINT_ATMO_VACUUM))),
                ItemModelUtils.when(WeatherglassFace.ATMO_VACUUM, countdown(meterModels, TINT_ATMO_VACUUM, ItemModelGenerators.BLANK_LAYER)),
                ItemModelUtils.when(WeatherglassFace.ATMO_FILLING, countdown(meterModels, TINT_ATMO_VACUUM, ItemModelUtils.constantTint(TINT_MARS_CLEAR)))
        )));
    }

    private static ItemModel.Unbaked countdown(Identifier[] meterModels, int orbTint, ItemTintSource meterTint) {
        List<RangeSelectItemModel.Entry> frames = new ArrayList<>();
        for (int i = 0; i < COUNTDOWN_THRESHOLDS.length; i++) {
            ItemModel.Unbaked frame = ItemModelUtils.tintedModel(meterModels[i], ItemModelGenerators.BLANK_LAYER,
                    ItemModelUtils.constantTint(orbTint), ItemModelGenerators.BLANK_LAYER, meterTint);
            frames.add(ItemModelUtils.override(frame, COUNTDOWN_THRESHOLDS[i]));
        }

        return ItemModelUtils.rangeSelect(new WeatherglassCountdownProperty(), frames);
    }

    private static ItemModel.Unbaked tintedClear(Identifier clearModel, int orbTint) {
        return ItemModelUtils.tintedModel(clearModel, ItemModelGenerators.BLANK_LAYER, ItemModelUtils.constantTint(orbTint), ItemModelGenerators.BLANK_LAYER);
    }

    private static Material material(String textureName) {
        return new Material(Relict.id("item/weatherglass/" + textureName));
    }

    private static float[] countdownThresholds(int frameCount) {
        float[] thresholds = new float[frameCount];
        float step = 1f / frameCount;
        for (int i = 0; i < frameCount; i++) {
            thresholds[i] = step * i;
        }
        return thresholds;
    }

}
