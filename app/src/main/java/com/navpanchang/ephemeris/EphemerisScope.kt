package com.navpanchang.ephemeris

/**
 * `AutoCloseable` wrapper around an [EphemerisEngine] for **GC hygiene** during bulk
 * lookaheads.
 *
 * The Thomas Mack Swiss Ephemeris Java port holds native file handles to `.se1` data
 * files and allocates large transient scratch buffers per call. Ballooning memory during
 * a 24-month Tier 1 lookahead (700+ day-by-day panchang computations) is the single most
 * common source of OOM crashes on 4 GB devices. LeakCanary is configured to watch
 * `RefreshWorker` specifically so a scope that escapes its `use { }` block is flagged
 * immediately.
 *
 * The Meeus engine doesn't hold any native state, so for it [close] is a no-op. But we
 * still require callers to use `EphemerisScope().use { ... }` so the code pattern is
 * consistent and the swap to [SwissEphemerisEngine] in Phase 1b is a one-line change in
 * the Hilt provider.
 *
 * See TECH_DESIGN.md §Ephemeris integration.
 */
class EphemerisScope(val engine: EphemerisEngine) : AutoCloseable {

    private var closed = false

    /** Executes [block] with this scope's engine, guarding against post-close reuse. */
    inline fun <R> use(block: (EphemerisEngine) -> R): R {
        try {
            return block(engine)
        } finally {
            close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // SwissEphemerisEngine wraps native file handles and scratch pools that need
        // explicit cleanup. MeeusEphemerisEngine is pure-Kotlin — its close() is a no-op.
        if (engine is SwissEphemerisEngine) {
            engine.close()
        }
    }
}
