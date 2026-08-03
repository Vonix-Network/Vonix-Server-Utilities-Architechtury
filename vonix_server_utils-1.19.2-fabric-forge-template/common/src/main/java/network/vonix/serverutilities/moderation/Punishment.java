package network.vonix.serverutilities.moderation;

import java.util.UUID;

/**
 * DTO representing a single row in the {@code punishments} table.
 *
 * Schema source-of-truth lives in {@link PunishmentRepository#SCHEMA_SQL}.
 * Field types map 1:1 to the SQL columns.
 *
 * Immutable. Construct via the static factory methods to enforce
 * type-specific invariants (e.g. KICK / WARN never have an expiry).
 */
public final class Punishment {

    public enum Type {
        BAN,
        MUTE,
        KICK,
        WARN
    }

    private final long    id;
    private final Type    type;
    private final UUID    targetUuid;
    private final String  targetName;
    private final UUID    issuerUuid;   // null = console
    private final String  issuerName;   // 'CONSOLE' or player name
    private final String  reason;
    private final long    issuedAt;
    private final Long    expiresAt;    // null = permanent or N/A
    private final boolean active;
    private final String  revokedBy;
    private final Long    revokedAt;

    public Punishment(long id, Type type,
                      UUID targetUuid, String targetName,
                      UUID issuerUuid, String issuerName,
                      String reason, long issuedAt, Long expiresAt,
                      boolean active, String revokedBy, Long revokedAt) {
        this.id         = id;
        this.type       = type;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.issuerUuid = issuerUuid;
        this.issuerName = issuerName;
        this.reason     = reason;
        this.issuedAt   = issuedAt;
        this.expiresAt  = expiresAt;
        this.active     = active;
        this.revokedBy  = revokedBy;
        this.revokedAt  = revokedAt;
    }

    public static Punishment forInsert(Type type,
                                       UUID targetUuid, String targetName,
                                       UUID issuerUuid, String issuerName,
                                       String reason, Long expiresAt) {
        if ((type == Type.KICK || type == Type.WARN) && expiresAt != null) {
            throw new IllegalArgumentException(type + " punishments cannot have an expiry");
        }
        return new Punishment(0L, type, targetUuid, targetName,
                issuerUuid, issuerName, reason,
                System.currentTimeMillis(), expiresAt, true, null, null);
    }

    public long    id()         { return id; }
    public Type    type()       { return type; }
    public UUID    targetUuid() { return targetUuid; }
    public String  targetName() { return targetName; }
    public UUID    issuerUuid() { return issuerUuid; }
    public String  issuerName() { return issuerName; }
    public String  reason()     { return reason; }
    public long    issuedAt()   { return issuedAt; }
    public Long    expiresAt()  { return expiresAt; }
    public boolean active()     { return active; }
    public String  revokedBy()  { return revokedBy; }
    public Long    revokedAt()  { return revokedAt; }

    public boolean isPermanent() { return expiresAt == null; }

    /** True if expiresAt is set and has passed. Permanent rows are NEVER expired. */
    public boolean isExpired(long now) {
        return expiresAt != null && expiresAt <= now;
    }
}
