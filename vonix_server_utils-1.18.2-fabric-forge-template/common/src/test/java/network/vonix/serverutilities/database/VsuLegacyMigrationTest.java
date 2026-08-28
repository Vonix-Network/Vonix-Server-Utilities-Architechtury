package network.vonix.serverutilities.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Regression test for retained VSU database recovery and separate back/death rows. */
public final class VsuLegacyMigrationTest {
    private VsuLegacyMigrationTest() {}

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path root = Files.createTempDirectory("vsu-migration-test-");
        Path source = root.resolve("data.db.bak");
        Path destination = root.resolve("destination.db");
        createSource(source);
        createDestination(destination);

        String before = VsuLegacyMigration.fingerprint(source);
        VsuLegacyMigration.ImportResult first;
        try (Connection dest = open(destination)) {
            first = VsuLegacyMigration.importInto(dest, source, before);
            require(first.recognized(), "source was not recognized");
            require(first.homesInserted() == 2, "expected two inserted homes");
            require(first.backLocationsInserted() == 2, "expected separate tp/death rows");
            require(count(dest, "vsu_homes") == 2, "destination home count mismatch");
            require(count(dest, "vsu_back_locations") == 2, "destination back count mismatch");

            VsuLegacyMigration.ImportResult second =
                    VsuLegacyMigration.importInto(dest, source, before);
            require(second.homesInserted() == 0, "rerun duplicated homes");
            require(second.backLocationsInserted() == 0, "rerun duplicated back rows");
        }
        require(before.equals(VsuLegacyMigration.fingerprint(source)), "source file changed");

        Path lookalike = root.resolve("lookalike.db");
        createLookalike(lookalike);
        try (Connection dest = open(destination)) {
            boolean rejected = false;
            try {
                VsuLegacyMigration.importInto(dest, lookalike,
                        VsuLegacyMigration.fingerprint(lookalike));
            } catch (SQLException expected) {
                rejected = true;
            }
            require(rejected, "lookalike schema was accepted");
            require(count(dest, "vsu_homes") == 2, "lookalike changed destination homes");
            require(count(dest, "vsu_back_locations") == 2, "lookalike changed destination back rows");
        }
        System.out.println("VsuLegacyMigrationTest: PASS");
    }

    private static void createSource(Path path) throws Exception {
        try (Connection connection = open(path); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE vsu_homes(uuid TEXT NOT NULL, name TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, PRIMARY KEY(uuid,name))");
            statement.execute("CREATE TABLE vsu_back_locations(uuid TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, kind TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(uuid,kind))");
            statement.execute("INSERT INTO vsu_homes VALUES('00000000-0000-0000-0000-000000000001','base','minecraft:overworld',1,64,2,0,0)");
            statement.execute("INSERT INTO vsu_homes VALUES('00000000-0000-0000-0000-000000000001','nether','minecraft:the_nether',3,70,4,90,5)");
            statement.execute("INSERT INTO vsu_back_locations VALUES('00000000-0000-0000-0000-000000000001','minecraft:overworld',5,65,6,0,0,'tp',100)");
            statement.execute("INSERT INTO vsu_back_locations VALUES('00000000-0000-0000-0000-000000000001','minecraft:overworld',7,66,8,180,0,'death',200)");
        }
    }

    private static void createDestination(Path path) throws Exception {
        try (Connection connection = open(path); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE vsu_homes(uuid TEXT NOT NULL, name TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, PRIMARY KEY(uuid,name))");
            statement.execute("CREATE TABLE vsu_back_locations(uuid TEXT NOT NULL, world TEXT NOT NULL, x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL, kind TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(uuid,kind))");
        }
    }

    private static void createLookalike(Path path) throws Exception {
        try (Connection connection = open(path); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE vsu_homes(uuid TEXT, name TEXT, world TEXT, x TEXT, y REAL, z REAL, yaw REAL, pitch REAL)");
        }
    }

    private static Connection open(Path path) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
    }

    private static int count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
