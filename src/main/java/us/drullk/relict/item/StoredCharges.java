package us.drullk.relict.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import us.drullk.relict.init.RelictDataComponents;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Consumer;

// Stored power at fixed-point tenths
public record StoredCharges(int tenths) implements TooltipProvider {

    public static final int MAX = 100;

    private static final int DIVISOR = 10;

    public static final StoredCharges EMPTY = new StoredCharges(0);

    public static final Codec<StoredCharges> CODEC = Codec.intRange(0, MAX).xmap(StoredCharges::new, StoredCharges::tenths);

    public static final StreamCodec<ByteBuf, StoredCharges> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(StoredCharges::new, StoredCharges::tenths);

    private static final DecimalFormat FORMAT = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public StoredCharges {
        tenths = Mth.clamp(tenths, 0, MAX);
    }

    public static StoredCharges getStoredCharge(ItemStack stack) {
        return stack.getOrDefault(RelictDataComponents.STORED_CHARGE.get(), EMPTY);
    }

    public static void addCharge(ItemStack stack, int charge) {
        if (charge <= 0 || !stack.has(RelictDataComponents.STORED_CHARGE.get())) {
            return;
        }

        StoredCharges storedCharges = getStoredCharge(stack);
        if (storedCharges.isFull()) {
            return;
        }

        stack.set(RelictDataComponents.STORED_CHARGE.get(), storedCharges.addCharge(charge));
    }

    public StoredCharges addCharge(int charge) {
        return charge <= 0 ? this : new StoredCharges(this.tenths + charge);
    }

    public boolean isFull() {
        return this.tenths >= MAX;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        consumer.accept(Component.translatable("item.relict.stored_charges.charge",
                FORMAT.format(this.tenths / (double) DIVISOR),
                MAX / DIVISOR).withStyle(ChatFormatting.GRAY));
    }

}
