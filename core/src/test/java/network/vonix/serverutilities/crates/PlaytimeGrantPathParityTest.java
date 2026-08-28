package network.vonix.serverutilities.crates;

import network.vonix.serverutilities.core.ImportBoundaryTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeGrantPathParityTest {

    @Test
    void grantPathsCallCorePlaytimeIntervals() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        Path cell1211 = root.resolve(
                "vonix_server_utils-1.21.1-fabric-neoforgetemplate/common/src/main/java/network/vonix/serverutilities/crates/CratePlaytimeTask.java");
        Path cell2612 = root.resolve(
                "vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/crates/CratePlaytimeTask.java");
        assertTrue(Files.isRegularFile(cell1211), "1.21.1 CratePlaytimeTask missing");
        assertTrue(Files.isRegularFile(cell2612), "26.1.2 CratePlaytimeTask missing");

        assertGrantCallsCore(Files.readString(cell1211), "1.21.1");
        assertGrantCallsCore(Files.readString(cell2612), "26.1.2");
    }

    @Test
    void twentySixDoesNotVendorPlaytimeIntervalsWhenCoreIsWired() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        Path vendor = root.resolve(
                "vonix_server_utils-26.1.2-neoforge-template/src/main/java/network/vonix/serverutilities/crates/PlaytimeIntervals.java");
        assertTrue(!Files.exists(vendor),
                "26.1.2 must not keep a duplicate PlaytimeIntervals once core sources are on the compile path");
        String build = Files.readString(root.resolve("vonix_server_utils-26.1.2-neoforge-template/build.gradle"));
        assertTrue(!build.contains("../../core"),
                "26.1.2 srcDir ../../core escapes the repo (candidates-r4j/core); the template must use ../core");
        assertTrue(build.contains("../core/src/main/java"),
                "26.1.2 build.gradle must compile core PlaytimeIntervals from the S0 core source tree");
        assertTrue(Files.isDirectory(root.resolve("core/src/main/java")),
                "S0 core source tree must exist so the independent 26.1.2 srcDir can compile PlaytimeIntervals");
    }

    @Test
    void grantPathsDoNotMoveMinecraftStatsIntoCore() throws IOException {
        Path root = ImportBoundaryTest.repoRoot();
        String core = Files.readString(root.resolve(
                "core/src/main/java/network/vonix/serverutilities/crates/PlaytimeIntervals.java"));
        assertTrue(!core.contains("net.minecraft"), "core PlaytimeIntervals must stay Stats-free");
        assertTrue(!core.contains("Stats.PLAY_TIME"), "Minecraft Stats access must stay in version cells");
    }

    private static void assertGrantCallsCore(String source, String cell) {
        assertTrue(source.contains("PlaytimeIntervals.completed("),
                cell + " CratePlaytimeTask must call PlaytimeIntervals.completed");
        assertTrue(source.contains("Stats.CUSTOM") && source.contains("Stats.PLAY_TIME"),
                cell + " must still read Minecraft playtime Stats locally");
        assertTrue(!source.contains("minutes * 60L * 20L"),
                cell + " must not inline intervalTicks math beside the core call");
    }
}
