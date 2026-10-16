package app.listful.domain;

import java.time.Duration;
import java.time.Instant;

public final class RetentionPolicy {
    public static final Duration TRASH = Duration.ofDays(14);
    public static final Duration PUBLIC_LINK = Duration.ofDays(56);

    private RetentionPolicy() {}

    public static boolean isRestorable(Instant deletedAt, Instant now) {
        return deletedAt != null && now.isBefore(deletedAt.plus(TRASH));
    }
}
