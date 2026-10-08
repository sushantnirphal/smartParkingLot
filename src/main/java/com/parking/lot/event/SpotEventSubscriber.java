package com.parking.lot.event;

import com.parking.lot.cache.AvailabilityCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The consumer side of the Observer pattern (architecture P4 / layer 4 of §7).
 *
 * <p>Runs strictly <b>after commit</b>: a rollback never invalidates the cache, and a cache that
 * was written before the commit cannot survive the change.
 */
@Component
public class SpotEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SpotEventSubscriber.class);

    private final AvailabilityCache cache;
    private final Counter occupied;
    private final Counter freed;

    public SpotEventSubscriber(AvailabilityCache cache, MeterRegistry meterRegistry) {
        this.cache = cache;
        this.occupied = meterRegistry.counter("parking.spot.occupied");
        this.freed = meterRegistry.counter("parking.spot.freed");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpotChanged(SpotChangeEvent event) {
        cache.invalidate();
        if (event.getKind() == SpotChangeEvent.Kind.OCCUPIED) {
            occupied.increment();
        } else {
            freed.increment();
        }
        log.debug("spot {} {} on floor {} -> {}",
                event.getLabel(), event.getKind(), event.getFloorNo(), event.getNewStatus());
    }
}
