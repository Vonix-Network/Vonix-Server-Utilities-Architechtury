package network.vonix.serverutilities.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLoaderBoundaryTest {

    @Test
    void oneTwentyOneAdaptersUseNativeLoaderApis() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String fabric = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/fabric/src/main/java/network/vonix/serverutilities/fabric/VsuPlatformEvents.java"));
        String neo = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/neoforge/src/main/java/network/vonix/serverutilities/neoforge/VsuPlatformEvents.java"));

        assertFalse(fabric.contains("dev.architectury"),
                "1.21.1 Fabric PlatformEvents adapter must not use Architectury");
        assertFalse(neo.contains("dev.architectury"),
                "1.21.1 NeoForge PlatformEvents adapter must not use Architectury");
        assertTrue(fabric.contains("CommandRegistrationCallback"));
        assertTrue(fabric.contains("ServerLifecycleEvents"));
        assertTrue(fabric.contains("ServerTickEvents"));
        assertTrue(fabric.contains("ServerPlayConnectionEvents"));
        assertTrue(fabric.contains("FabricLoader"));
        assertTrue(neo.contains("RegisterCommandsEvent"));
        assertTrue(neo.contains("ServerStartingEvent"));
        assertTrue(neo.contains("ServerStartedEvent"));
        assertTrue(neo.contains("ServerStoppingEvent"));
        assertTrue(neo.contains("ServerStoppedEvent"));
        assertTrue(neo.contains("NeoForge.EVENT_BUS"));
    }

    @Test
    void oneTwentyOneCommonDoesNotDependOnArchitecturyApi() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String commonBuild = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/build.gradle"));
        String fabricBuild = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/fabric/build.gradle"));
        String neoBuild = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/neoforge/build.gradle"));
        assertFalse(commonBuild.contains("dev.architectury:architectury"));
        assertFalse(fabricBuild.contains("dev.architectury:architectury-fabric"));
        assertFalse(neoBuild.contains("dev.architectury:architectury-neoforge"));
    }

    @Test
    void loaderModulesOwnEventRegistration() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String commonHandler = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/listener/EventHandler.java"));
        assertTrue(commonHandler.contains("PlatformEvents.Holder.get().register"));
        assertFalse(commonHandler.contains("dev.architectury"));
        assertFalse(commonHandler.contains("net.fabricmc."));
        assertFalse(commonHandler.contains("net.neoforged."));
    }
}
