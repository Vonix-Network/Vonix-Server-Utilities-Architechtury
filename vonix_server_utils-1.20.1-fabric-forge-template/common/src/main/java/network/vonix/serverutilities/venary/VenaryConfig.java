package network.vonix.serverutilities.venary;

/**
 * Strongly-typed accessor for Venary-related settings stored in
 * {@link network.vonix.serverutilities.config.ModConfig}.
 *
 * <p>This is a thin view object — actual persistence lives in
 * {@code vonix_server_utilities.properties}. All defaults are conservative
 * (kill switch OFF) so a freshly-installed mod never reaches out to the
 * network until an operator opts in.
 *
 * <p>Threat-model invariants enforced here:
 * <ul>
 *   <li>{@link #toString()} MUST mask the API key.</li>
 *   <li>Defaults must keep the HTTP layer fully disabled.</li>
 * </ul>
 */
public final class VenaryConfig {

    public static final String DEFAULT_API_BASE = "https://api.vonix.network";

    private final boolean enabled;
    private final String  apiBase;
    private final String  apiKey;
    private final boolean loginJwtEnabled;
    private final boolean statsSyncEnabled;
    private final int     statsSyncIntervalMinutes;
    private final int     linkCooldownSeconds;

    public VenaryConfig(boolean enabled,
                        String apiBase,
                        String apiKey,
                        boolean loginJwtEnabled,
                        boolean statsSyncEnabled,
                        int statsSyncIntervalMinutes,
                        int linkCooldownSeconds) {
        this.enabled                  = enabled;
        this.apiBase                  = (apiBase == null || apiBase.isBlank()) ? DEFAULT_API_BASE : stripTrailingSlash(apiBase);
        this.apiKey                   = apiKey == null ? "" : apiKey.trim();
        this.loginJwtEnabled          = loginJwtEnabled;
        this.statsSyncEnabled         = statsSyncEnabled;
        this.statsSyncIntervalMinutes = Math.max(1, statsSyncIntervalMinutes);
        this.linkCooldownSeconds      = Math.max(0, linkCooldownSeconds);
    }

    public boolean isEnabled()                 { return enabled; }
    public String  getApiBase()                { return apiBase; }
    public String  getApiKey()                 { return apiKey; }
    public boolean isLoginJwtEnabled()         { return loginJwtEnabled; }
    public boolean isStatsSyncEnabled()        { return statsSyncEnabled; }
    public int     getStatsSyncIntervalMinutes() { return statsSyncIntervalMinutes; }
    public int     getLinkCooldownSeconds()    { return linkCooldownSeconds; }

    /** True if the API key looks usable. Does NOT validate it remotely. */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** "****" + last 4 chars of api key, safe to print to operators. */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.isEmpty()) return "<unset>";
        if (apiKey.length() <= 4)               return "****";
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Override
    public String toString() {
        // NEVER print the raw api key.
        return "VenaryConfig{enabled=" + enabled
                + ", apiBase=" + apiBase
                + ", apiKey=" + getMaskedApiKey()
                + ", loginJwt=" + loginJwtEnabled
                + ", statsSync=" + statsSyncEnabled
                + ", statsSyncIntervalMinutes=" + statsSyncIntervalMinutes
                + ", linkCooldownSeconds=" + linkCooldownSeconds
                + '}';
    }
}
