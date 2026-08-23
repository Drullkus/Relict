package us.drullk.relict.init;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;
import us.drullk.relict.item.StoredCharges;
import us.drullk.relict.item.WeatherglassReading;

public class RelictDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Relict.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StoredCharges>> STORED_CHARGE = DATA_COMPONENTS.registerComponentType("stored_charge", builder -> builder.persistent(StoredCharges.CODEC).networkSynchronized(StoredCharges.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> INERT = DATA_COMPONENTS.registerComponentType("inert", builder -> builder.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeatherglassReading>> WEATHERGLASS_READING = DATA_COMPONENTS.registerComponentType("weatherglass_reading", builder -> builder.persistent(WeatherglassReading.CODEC).networkSynchronized(WeatherglassReading.STREAM_CODEC));

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
        modEventBus.addListener(RelictDataComponents::registerTooltipAppenders);
    }

    private static void registerTooltipAppenders(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(STORED_CHARGE, TooltipAppender.createComponentAppender(STORED_CHARGE.get()));
    }

}
