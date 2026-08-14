package us.drullk.relict.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.BooleanAttribute;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import us.drullk.relict.Relict;

public class RelictAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, Relict.MODID);

    public static final DeferredHolder<Attribute, Attribute> MARS_LIFE_SUPPORT = ATTRIBUTES.register("mars_life_support", () -> new BooleanAttribute("attribute.name.relict.mars_life_support", false).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> NAUSEA_IMMUNITY = ATTRIBUTES.register("nausea_immunity", () -> new BooleanAttribute("attribute.name.relict.nausea_immunity", false).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> ELECTRIC_DAMAGE = ATTRIBUTES.register("electric_damage", () -> new PercentageAttribute("attribute.name.relict.electric_damage", 1.0D, 0.0D, 1024.0D).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));

    public static AttributeModifier enable(Identifier id) {
        return new AttributeModifier(id, 1.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(RelictAttributes::addToEntities);
    }

    private static void addToEntities(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            event.add(type, MARS_LIFE_SUPPORT);
            event.add(type, NAUSEA_IMMUNITY);
            event.add(type, ELECTRIC_DAMAGE);
        }
    }

}
