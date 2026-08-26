package us.drullk.relict.gametest;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.gametest.framework.TestEnvironmentDefinition.AllOf;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import us.drullk.relict.Relict;

import java.util.List;

/**
 * Central GameTest bootstrap. Lives in its own {@code gametest} source set -- wired in {@code build.gradle}
 * so only {@code runGameTestServer} ever compiles or loads it, mirroring the {@code data}/{@code reports}
 * source sets' own {@code @EventBusSubscriber(modid = Relict.MODID)} self-registration (see
 * {@code RelictDatagen}, {@code RelictReports}) rather than an explicit call from {@link Relict}: no class
 * in {@code main}/{@code client}/{@code data}/{@code reports} references anything in this package. Fires
 * once on the mod bus and registers the shared test environment, then hands off to each feature's own
 * test-pack class -- mirrors the Model Generators precedent ({@code RelictModels} composing
 * {@code CipherChestModelGenerator#bootstrap} and friends): every feature ships its own gametest class,
 * this file adds at most one line per feature.
 */
@EventBusSubscriber(modid = Relict.MODID)
public final class RelictGameTests {

    /** Bare stone platform, shared by every test pack that just needs somewhere to place a block. */
    public static final Identifier PLATFORM = Relict.id("gametest/platform");

    private RelictGameTests() {
    }

    @SubscribeEvent
    static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Relict.id("default"), new AllOf(List.of()));

        BasaltSandGameTests.register(event, environment);
    }

}
