package network.vonix.serverutilities.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeIncludeGraphTest {

    @Test
    void requestedCellsResolveAsIndependentProjects() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String settings = Files.readString(root.resolve("settings.gradle"));
        String build = Files.readString(root.resolve("build.gradle"));

        assertTrue(settings.contains("include 'core'"));
        assertTrue(settings.contains("include 'common', 'fabric', 'neoforge'"));
        assertTrue(settings.contains("mc-1.21.1:common"));
        assertTrue(settings.contains("mc-1.21.1:fabric"));
        assertTrue(settings.contains("mc-1.21.1:neoforge"));
        assertTrue(settings.contains("mc-26.1.2:neoforge"));
        assertTrue(settings.contains("nativeIncludeGraph"));

        assertTrue(Files.isDirectory(root.resolve("core")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.21.1-fabric-neoforgetemplate/common")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.21.1-fabric-neoforgetemplate/fabric")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.21.1-fabric-neoforgetemplate/neoforge")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-26.1.2-neoforge-template")));
        assertTrue(Files.isRegularFile(root.resolve("vonix_server_utils-26.1.2-neoforge-template/settings.gradle")));
        assertTrue(Files.isRegularFile(root.resolve("vonix_server_utils-26.1.2-neoforge-template/gradlew")));

        // Historical templates remain; S0 must not delete them.
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.18.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.19.2-fabric-forge-template")));
        assertTrue(Files.isDirectory(root.resolve("vonix_server_utils-1.20.1-fabric-forge-template")));

        assertTrue(build.contains("verifyNativeIncludeGraph"));
        assertTrue(build.contains("architecturyCells"));
        assertFalse(build.contains("subprojects.findAll { it.name != 'core' }"));
    }
}
