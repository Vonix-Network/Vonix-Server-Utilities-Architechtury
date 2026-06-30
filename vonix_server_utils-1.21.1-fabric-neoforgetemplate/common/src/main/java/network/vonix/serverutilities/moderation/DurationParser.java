package network.vonix.serverutilities.moderation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses moderation duration strings per the VSU v1.6.0 spec.
 *
 * Accepts:
 *   30s, 5m, 2h, 7d, 4w, 1mo, 1y
 *   perm / permanent / never  (returns Optional.empty -- caller treats as permanent)
 *   Composed:  1d12h, 7d6h30m  (no spaces)
 *
 * Rejects:
 *   any token without a unit (e.g. "30")
 *   negative numbers ("-5h")
 *   total > 100 years
 *   empty / null
 *
 * Returns {@code Optional<Long>} of milliseconds-to-expire from now,
 * or {@code Optional.empty()} for permanent.
 *
 * Result semantics:
 *   {@link #parse(String)} returns:
 *     - non-empty Optional with millis on valid finite duration
 *     - empty Optional when the input means "permanent"
 *     - throws {@link IllegalArgumentException} on reject
 *
 * Callers that want a tri-state result (valid finite / permanent / invalid)
 * should use {@link #parseSafe(String)} which returns a sentinel.
 */
public final class DurationParser {

    private DurationParser() {}

    /** Sentinel for "input parsed but the duration is permanent". */
    public static final long PERMANENT = -1L;
    /** Sentinel for "input was rejected". */
    public static final long INVALID = -2L;

    private static final long MS_SECOND = 1000L;
    private static final long MS_MINUTE = 60L * MS_SECOND;
    private static final long MS_HOUR   = 60L * MS_MINUTE;
    private static final long MS_DAY    = 24L * MS_HOUR;
    private static final long MS_WEEK   = 7L  * MS_DAY;
    // Calendar units approximated to fixed lengths (industry-standard for ban durations).
    private static final long MS_MONTH  = 30L  * MS_DAY;
    private static final long MS_YEAR   = 365L * MS_DAY;

    private static final long MAX_MS = 100L * MS_YEAR;

    // Token = digits + unit. Units (ordered longest-first so "mo" wins over "m"): mo, s, m, h, d, w, y.
    private static final Pattern TOKEN = Pattern.compile("(\\d+)(mo|s|m|h|d|w|y)");

    /**
     * Returns {@code Optional.empty()} for permanent, an Optional of millis-from-now for
     * a finite duration, or throws {@link IllegalArgumentException} for any reject case.
     */
    public static Optional<Long> parse(String input) {
        if (input == null) throw new IllegalArgumentException("duration is null");
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) throw new IllegalArgumentException("duration is empty");

        if (s.equals("perm") || s.equals("permanent") || s.equals("never")) {
            return Optional.empty();
        }
        if (s.startsWith("-")) {
            throw new IllegalArgumentException("negative duration: " + input);
        }

        Matcher m = TOKEN.matcher(s);
        long total = 0L;
        int consumed = 0;
        while (m.find()) {
            if (m.start() != consumed) {
                throw new IllegalArgumentException("unparsable segment: " + input);
            }
            long n;
            try { n = Long.parseLong(m.group(1)); }
            catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("number overflow: " + input);
            }
            String unit = m.group(2);
            long mul = switch (unit) {
                case "s"  -> MS_SECOND;
                case "m"  -> MS_MINUTE;
                case "h"  -> MS_HOUR;
                case "d"  -> MS_DAY;
                case "w"  -> MS_WEEK;
                case "mo" -> MS_MONTH;
                case "y"  -> MS_YEAR;
                default   -> throw new IllegalArgumentException("unknown unit: " + unit);
            };
            try {
                total = Math.addExact(total, Math.multiplyExact(n, mul));
            } catch (ArithmeticException ae) {
                throw new IllegalArgumentException("duration overflow: " + input);
            }
            consumed = m.end();
        }
        if (consumed == 0 || consumed != s.length()) {
            throw new IllegalArgumentException("unrecognised duration: " + input);
        }
        if (total <= 0) {
            throw new IllegalArgumentException("duration must be positive: " + input);
        }
        if (total > MAX_MS) {
            throw new IllegalArgumentException("duration exceeds 100 years: " + input);
        }
        return Optional.of(total);
    }

    /** Tri-state convenience: returns millis, {@link #PERMANENT}, or {@link #INVALID}. */
    public static long parseSafe(String input) {
        try {
            Optional<Long> r = parse(input);
            return r.orElse(PERMANENT);
        } catch (IllegalArgumentException e) {
            return INVALID;
        }
    }

    /** Pretty-print a millis-from-now value as "Xd Yh Zm" etc. for chat messages. */
    public static String format(long ms) {
        if (ms <= 0) return "0s";
        long days  = ms / MS_DAY;          ms %= MS_DAY;
        long hours = ms / MS_HOUR;         ms %= MS_HOUR;
        long mins  = ms / MS_MINUTE;       ms %= MS_MINUTE;
        long secs  = ms / MS_SECOND;
        StringBuilder sb = new StringBuilder();
        if (days  > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins  > 0) sb.append(mins).append("m ");
        if (secs  > 0 && days == 0 && hours == 0) sb.append(secs).append("s");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0s" : out;
    }
}
