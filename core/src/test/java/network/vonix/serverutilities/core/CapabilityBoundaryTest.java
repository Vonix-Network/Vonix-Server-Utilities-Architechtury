package network.vonix.serverutilities.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CapabilityBoundaryTest {

    @Test
    void requestedCommonAndSharedSourcesDoNotClassForNameLoaderApis() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        List<Path> roots = List.of(
                root.resolve("vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java"),
                root.resolve("vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/inventory"),
                root.resolve("vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/api"),
                root.resolve("vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/inventory/providers"));
        List<String> hits = new ArrayList<>();
        for (Path dir : roots) {
            hits.addAll(classForNameHits(dir));
        }
        if (!hits.isEmpty()) {
            fail("shared/common code Class.forName'd a loader API:\n" + String.join("\n", hits));
        }
    }

    @Test
    void capabilityProviderIsInstalledByLoaderAdapters() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String commonBridge = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/inventory/CapabilityInventoryBridge.java"));
        assertTrue(commonBridge.contains("ItemHandlerAccess"),
                "common capability facade must delegate to a loader-owned ItemHandlerAccess");
        assertFalse(commonBridge.contains("Class.forName"));

        String neo1211 = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/neoforge/src/main/java/network/vonix/serverutilities/neoforge/VonixServerUtilitiesNeoForge.java"));
        assertTrue(neo1211.contains("ItemHandlerAccess.Holder.install"),
                "1.21.1 NeoForge entrypoint must install the native capability adapter");

        String neo2612 = Files.readString(root.resolve(
                "vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/neoforge/VonixServerUtilitiesNeoForge.java"));
        assertTrue(neo2612.contains("ItemHandlerAccess.Holder.install"),
                "26.1.2 NeoForge entrypoint must install the native capability adapter");
    }

    @Test
    void requestedCellAdaptersUseClasspathNativeCapabilityApis() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String neo1211 = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/neoforge/src/main/java/network/vonix/serverutilities/inventory/neoforge/NeoForgeItemHandlerAccess.java"));
        assertTrue(neo1211.contains("Capabilities.ItemHandler.ITEM"),
                "1.21.1 NeoForge still exposes Capabilities.ItemHandler.ITEM");
        assertTrue(!neo1211.contains("Class.forName"));

        String neo2612 = Files.readString(root.resolve(
                "vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/inventory/neoforge/NeoForgeItemHandlerAccess.java"));
        assertTrue(neo2612.contains("Capabilities.Item.ITEM"),
                "26.1.2 replaced Capabilities.ItemHandler with Capabilities.Item; adapters must use the classpath API");
        assertTrue(!neo2612.contains("Capabilities.ItemHandler"),
                "26.1.2 Capabilities.ItemHandler.ITEM does not exist on the target classpath");
        assertTrue(neo2612.contains("ItemAccess"),
                "26.1.2 item capabilities take ItemAccess context, not Void");
        assertTrue(neo2612.contains("ResourceHandler"),
                "26.1.2 item capability type is ResourceHandler<ItemResource>");
        assertTrue(!neo2612.contains("Class.forName"));
    }

    private static List<String> classForNameHits(Path root) throws IOException {
        List<String> hits = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return hits;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                try {
                    int lineNo = 0;
                    for (String line : Files.readAllLines(file)) {
                        lineNo++;
                        String trimmed = line.trim();
                        if (!trimmed.contains("Class.forName")) continue;
                        if (trimmed.contains("net.minecraftforge") || trimmed.contains("net.neoforged")) {
                            hits.add(root.relativize(file) + ":" + lineNo + " " + trimmed);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return hits;
    }
}
