package us.drullk.relict.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class VizardItem extends Item {

    private static final int SAFE = 0x36D399;
    private static final int WARNING = 0xE8C547;
    private static final int DANGER = 0xE8813A;
    private static final int CRITICAL = 0xE23C3C;

    public VizardItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        double remaining = 1.0D - (double) stack.getDamageValue() / stack.getMaxDamage();
        if (remaining > 0.5D) {
            return SAFE;
        } else if (remaining > 0.25D) {
            return WARNING;
        } else if (remaining > 0.125D) {
            return DANGER;
        } else {
            return CRITICAL;
        }
    }

}
