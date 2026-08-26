package us.drullk.relict.gametest;

import com.mojang.serialization.MapCodec;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Consumer;

/**
 * A {@link GameTestInstance} registered straight from Java code via NeoForge's
 * {@code RegisterGameTestsEvent#registerTest(Identifier, GameTestInstance)}, rather than deserialized from
 * a {@code data/<ns>/test_instance/*.json} file. Vanilla's own {@code FunctionGameTestInstance} needs a
 * {@code ResourceKey} into the {@code minecraft:test_function} registry, which exists only to let JSON test
 * definitions reference a Java method by name -- pointless indirection for a mod that's registering the
 * function from code in the first place, so this wraps the {@link Consumer} directly instead.
 */
public final class RelictFunctionGameTestInstance extends GameTestInstance {

    private final MapCodec<RelictFunctionGameTestInstance> CODEC = MapCodec.unit(() -> this);

    private final Consumer<GameTestHelper> function;
    private final Component description;

    public RelictFunctionGameTestInstance(Consumer<GameTestHelper> function, Component description,
            TestData<Holder<TestEnvironmentDefinition<?>>> info) {
        super(info);
        this.function = function;
        this.description = description;
    }

    @Override
    public void run(GameTestHelper helper) {
        this.function.accept(helper);
    }

    @Override
    public MapCodec<? extends GameTestInstance> codec() {
        return CODEC;
    }

    @Override
    protected MutableComponent typeDescription() {
        return this.description.copy();
    }

}
