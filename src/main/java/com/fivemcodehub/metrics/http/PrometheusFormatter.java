package com.fivemcodehub.metrics.http;

import com.fivemcodehub.metrics.collect.MetricSnapshot;

/**
 * Renders a snapshot as Prometheus text exposition format (v0.0.4).
 *
 * <p>Format rules that are easy to get wrong and that break scraping:
 * every metric needs its HELP and TYPE lines before its samples, gauge
 * names should carry a unit suffix, and the body must end with a newline.
 */
public final class PrometheusFormatter {

    private PrometheusFormatter() {
    }

    public static String render(MetricSnapshot snapshot) {
        StringBuilder out = new StringBuilder(2048);

        gauge(out, "minecraft_tps", "Ticks per second averaged over a window",
                new String[][]{
                        {"window=\"1m\"", String.valueOf(round(snapshot.tps1m()))},
                        {"window=\"5m\"", String.valueOf(round(snapshot.tps5m()))},
                        {"window=\"15m\"", String.valueOf(round(snapshot.tps15m()))},
                });

        simple(out, "minecraft_mspt_mean", "Mean milliseconds per tick",
                round(snapshot.msptMean()));

        simple(out, "minecraft_heap_used_bytes", "Used JVM heap in bytes",
                snapshot.heapUsedBytes());
        simple(out, "minecraft_heap_max_bytes", "Maximum JVM heap in bytes",
                snapshot.heapMaxBytes());
        simple(out, "minecraft_heap_committed_bytes", "Committed JVM heap in bytes",
                snapshot.heapCommittedBytes());
        simple(out, "minecraft_heap_used_ratio", "Used heap as a fraction of maximum",
                round(snapshot.heapUsedRatio()));

        simple(out, "minecraft_players_online", "Players currently connected",
                snapshot.onlinePlayers());
        simple(out, "minecraft_players_max", "Configured player slots",
                snapshot.maxPlayers());

        simple(out, "minecraft_chunks_loaded", "Loaded chunks across all worlds",
                snapshot.loadedChunks());
        simple(out, "minecraft_entities_total", "Entities across all worlds",
                snapshot.entityCount());

        if (snapshot.tileEntityCount() >= 0) {
            simple(out, "minecraft_tile_entities_total",
                    "Tile entities across all loaded chunks", snapshot.tileEntityCount());
        }

        simple(out, "minecraft_worlds_loaded", "Loaded worlds", snapshot.worldCount());
        simple(out, "minecraft_uptime_seconds", "Seconds since the exporter started",
                snapshot.uptimeSeconds());
        simple(out, "minecraft_sample_age_seconds",
                "Age of the most recent sample, in seconds",
                round(snapshot.ageMillis() / 1000.0));

        return out.toString();
    }

    private static void simple(StringBuilder out, String name, String help, Number value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
        out.append(name).append(' ').append(value).append('\n');
    }

    private static void gauge(StringBuilder out, String name, String help,
                              String[][] labelledValues) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
        for (String[] pair : labelledValues) {
            out.append(name).append('{').append(pair[0]).append("} ")
                    .append(pair[1]).append('\n');
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
