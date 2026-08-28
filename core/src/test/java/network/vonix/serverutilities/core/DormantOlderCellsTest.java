package network.vonix.serverutilities.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Older VSU templates remain on disk for history. S1 does not repair them and
 * must not claim 1.18.2 / 1.19.2 / 1.20.1 parity, including 1.18.2 chat mixins.
 */
class DormantOlderCellsTest {

    @Test
    void olderTemplatesRemainOnDiskAndAreNotInTheNativeIncludeGraph() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.18.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.19.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.20.1-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("Vonix Build Menu")));

        String settings = Files.readString(root.resolve("settings.gradle"));
        assertFalse(settings.contains("1.18.2-fabric-forge-template"));
        assertFalse(settings.contains("1.19.2-fabric-forge-template"));
        assertFalse(settings.contains("1.20.1-fabric-forge-template"));
        assertTrue(settings.contains("mc-1.21.1:fabric"));
        assertTrue(settings.contains("mc-1.21.1:neoforge"));
        assertTrue(settings.contains("mc-26.1.2:neoforge"));
    }

    @Test
    void workingCellDoesNotShipChatFormatterOrMixinClasses() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        assertFalse(Files.exists(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/chat/ChatFormatter.java")),
                "1.21.1 working cell dropped ChatFormatter; do not copy 1.18.2 mixin bytecode forward");
        assertFalse(Files.exists(root.resolve(
                "vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/chat/ChatFormatter.java")));
        String mixin1211 = Files.readString(root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/resources/vonix_server_utilities.mixins.json"));
        assertFalse(mixin1211.contains("ServerGamePacketListenerImplMixin"));
    }
}
