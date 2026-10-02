package com.fivemcodehub.metrics.collect;

/**
 * Immutable sample of server state.
 *
 * <p>A record, not a mutable holder: the HTTP thread must be able to read a
 * self-consistent set of values without locking, and swapping an immutable
 * reference gives that for free.
 */
public record MetricSnapshot(
        double tps1m,
        double tps5m,
        double tps15m,
        double msptMean,
        long heapUsedBytes,
        long heapMaxBytes,
        long heapCommittedBytes,
        int onlinePlayers,
        int maxPlayers,
        int loadedChunks,
        int entityCount,
        int tileEntityCount,
        int worldCount,
        long uptimeSeconds,
        long capturedAtMillis
) {

    public static MetricSnapshot empty() {
        return new MetricSnapshot(20, 20, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                System.currentTimeMillis());
    }

    public double heapUsedRatio() {
        return heapMaxBytes == 0 ? 0 : (double) heapUsedBytes / heapMaxBytes;
    }

    /** Age of this sample; a scrape reading a stale snapshot is worth knowing about. */
    public long ageMillis() {
        return System.currentTimeMillis() - capturedAtMillis;
    }
}
