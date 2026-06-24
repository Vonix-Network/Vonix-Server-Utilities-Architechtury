package network.vonix.serverutilities.venary;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.vonix.serverutilities.VonixServerUtilities;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Singleton HTTP client for the Venary site-link API.
 *
 * <p>Fail-open contract: every public method returns a CompletableFuture that
 * resolves to {@code null} on ANY failure (kill switch off, missing key, IO
 * error, non-2xx, malformed JSON). Callers MUST treat {@code null} as
 * "site unreachable, try again later" and never crash.
 *
 * <p>Threading: all I/O is off the main tick thread on a small dedicated
 * executor. Connect timeout 5s; per-request response timeout 10s.
 *
 * <p>Secret hygiene: the API key is sent only as the {@code x-api-key} header
 * and is NEVER written to logs.
 *
 * <p>PORT-NOTE: java.net.http is JDK 11+, identical on 1.18.2 / 1.19.2 /
 * 1.20.1 / 1.21.1 — this class is portable as-is. Gson is shipped with
 * Minecraft on every target.
 */
public final class VenaryClient {

    private static VenaryClient instance;

    private final HttpClient http;
    private final ExecutorService ioExecutor;
    private volatile VenaryConfig config;

    private VenaryClient(VenaryConfig config) {
        this.config = config;
        this.ioExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "VonixSU-Venary");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)  // Force HTTP/1.1: Express/Node does not handle java HttpClient h2c upgrade probe — see VSU PR
                .connectTimeout(Duration.ofSeconds(5))
                .executor(ioExecutor)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static synchronized void init(VenaryConfig config) {
        if (instance == null) {
            instance = new VenaryClient(config);
        } else {
            instance.config = config;
        }
    }

    public static VenaryClient get() {
        return instance;
    }

    public VenaryConfig getConfig() {
        return config;
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** POST /minecraft/link/generate. Returns body or null on failure. */
    public CompletableFuture<JsonObject> generateLinkCode(UUID uuid, String username) {
        return post("/minecraft/link/generate", playerBody(uuid, username));
    }

    /** POST /minecraft/verify-player. Returns body or null on failure. */
    public CompletableFuture<JsonObject> verifyPlayer(UUID uuid, String username) {
        return post("/minecraft/verify-player", playerBody(uuid, username));
    }

    /** POST /minecraft/login (JWT mint). Disabled unless config flag set. */
    public CompletableFuture<JsonObject> loginJwt(UUID uuid, String username) {
        if (!config.isLoginJwtEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        return post("/minecraft/login", playerBody(uuid, username));
    }

    /** POST /minecraft/players/sync. Disabled unless stats sync flag set. */
    public CompletableFuture<JsonObject> syncPlayer(UUID uuid, String username, Map<String, Long> stats) {
        if (!config.isStatsSyncEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        JsonObject body = playerBody(uuid, username);
        JsonObject statsObj = new JsonObject();
        if (stats != null) {
            for (Map.Entry<String, Long> e : stats.entrySet()) {
                statsObj.addProperty(e.getKey(), e.getValue());
            }
        }
        body.add("stats", statsObj);
        return post("/minecraft/players/sync", body);
    }

    // ── Feature-flag / donation-rank / audit endpoints ──────────────────────

    /**
     * GET /minecraft/server-config — returns the canonical feature-flag,
     * feature-settings, and donation-rank table. Fail-open: null on any error.
     */
    public CompletableFuture<JsonObject> getServerConfig() {
        return get("/minecraft/server-config");
    }

    /**
     * GET /minecraft/players/&lt;uuid&gt;/ranks — returns {linked, ranks:[...]}.
     * Fail-open: null on any error (treat as "no ranks known", do nothing).
     */
    public CompletableFuture<JsonObject> getPlayerRanks(UUID uuid) {
        return get("/minecraft/players/" + uuid + "/ranks");
    }

    /**
     * POST /minecraft/audit. {@code eventType} MUST be on the backend allowlist
     * (feature_toggle, rank_apply, rank_remove, link_generate, link_consume,
     * sync, lp_group_create, lp_group_update, error). Fail-open.
     */
    public CompletableFuture<JsonObject> postAudit(String eventType,
                                                   String actorType,
                                                   String actorId,
                                                   UUID playerUuid,
                                                   JsonObject payload) {
        JsonObject body = new JsonObject();
        body.addProperty("event_type", eventType);
        body.addProperty("actor_type", actorType);
        if (actorId    != null) body.addProperty("actor_id",    actorId);
        if (playerUuid != null) body.addProperty("player_uuid", playerUuid.toString());
        if (payload    != null) body.add("payload", payload);
        return post("/minecraft/audit", body);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static JsonObject playerBody(UUID uuid, String username) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", uuid.toString());
        o.addProperty("username", username);
        return o;
    }

    /**
     * Returns a future that ALWAYS resolves (never completes exceptionally),
     * yielding the parsed JSON body on 2xx, or null otherwise.
     */
    private CompletableFuture<JsonObject> post(String path, JsonObject body) {
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!config.hasApiKey()) {
            // No key configured — silently no-op. Logged at debug only, no key value.
            VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] Skipping {} — no api key configured", path);
            return CompletableFuture.completedFuture(null);
        }

        final HttpRequest request;
        try {
            URI uri = URI.create(config.getApiBase() + path + "?protocol_version=1");
            request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type",  "application/json")
                    .header("Accept",        "application/json")
                    .header("x-api-key",     config.getApiKey())
                    .header("User-Agent",    userAgent())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.warn("[VonixSU/Venary] Failed to build request for {}: {}", path, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, err) -> {
                    if (err != null) {
                        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] {} failed: {}", path, err.getMessage());
                        return null;
                    }
                    int status = resp.statusCode();
                    if (status / 100 != 2) {
                        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] {} returned HTTP {}", path, status);
                        return null;
                    }
                    try {
                        return JsonParser.parseString(resp.body()).getAsJsonObject();
                    } catch (Exception parseErr) {
                        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] {} returned malformed JSON", path);
                        return null;
                    }
                });
    }

    /**
     * GET counterpart to {@link #post}. Same fail-open semantics, same
     * masking-by-omission for headers. Used by feature-flag + rank pulls.
     */
    private CompletableFuture<JsonObject> get(String path) {
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!config.hasApiKey()) {
            VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] Skipping GET {} — no api key", path);
            return CompletableFuture.completedFuture(null);
        }
        final HttpRequest request;
        try {
            URI uri = URI.create(config.getApiBase() + path + "?protocol_version=1");
            request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept",     "application/json")
                    .header("x-api-key",  config.getApiKey())
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
        } catch (Exception e) {
            VonixServerUtilities.LOGGER.warn("[VonixSU/Venary] Failed to build GET request for {}: {}", path, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((resp, err) -> {
                    if (err != null) {
                        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] GET {} failed: {}", path, err.getMessage());
                        return null;
                    }
                    int status = resp.statusCode();
                    if (status / 100 != 2) {
                        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] GET {} returned HTTP {}", path, status);
                        return null;
                    }
                    try {
                        return JsonParser.parseString(resp.body()).getAsJsonObject();
                    } catch (Exception parseErr) {
                        VonixServerUtilities.LOGGER.debug("[VonixSU/Venary] GET {} returned malformed JSON", path);
                        return null;
                    }
                });
    }

    private static String userAgent() {
        return "VonixServerUtilities/" + VonixServerUtilities.VERSION + " MC/1.20.1";
    }
}
