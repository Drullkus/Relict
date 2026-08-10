package us.drullk.relict.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import us.drullk.relict.Relict;

@EventBusSubscriber(modid = Relict.MODID)
public class RelictDatagen {

    @SubscribeEvent
    public static void generateData(GatherDataEvent.Server event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = event.getGenerator().getPackOutput();


    }

}
