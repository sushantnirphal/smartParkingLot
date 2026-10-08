package com.parking.lot.cache;

import com.parking.lot.web.dto.AvailabilityDto;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Read-through cache for {@code GET /availability} (architecture D4 / §7 layer 4).
 *
 * <p>DB remains the source of truth: a miss (or an invalidation) simply recomputes from the
 * spot rows. Entries are stamped with a monotonic {@code lotVersion} that only moves forward,
 * so a stale read can always be detected and self-heals on the next event.
 */
@Component
public class AvailabilityCache {

    private final AtomicLong lotVersion = new AtomicLong(0L);
    private volatile AvailabilityDto cached;

    /**
     * @return the cached snapshot, or a freshly computed one on miss.
     *
     * <p>The value is only stored when no spot change committed while it was being computed -
     * otherwise a concurrent entry/exit could pin a stale snapshot until the next event.
     */
    public AvailabilityDto getOrCompute(Supplier<AvailabilityDto> loader) {
        AvailabilityDto local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            long before = lotVersion.get();
            AvailabilityDto fresh = loader.get();
            if (lotVersion.get() == before) {
                cached = fresh;
            }
            return fresh;
        }
    }

    /** Called from the after-commit listener; never from inside a transaction. */
    public void invalidate() {
        cached = null;
        lotVersion.incrementAndGet();
    }

    public long currentVersion() {
        return lotVersion.get();
    }

    public void reset() {
        cached = null;
        lotVersion.set(0L);
    }
}
