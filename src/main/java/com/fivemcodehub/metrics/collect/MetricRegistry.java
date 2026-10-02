package com.fivemcodehub.metrics.collect;

import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Samples server state on the main thread at a fixed interval.
 *
 * <p>Chunk and entity counts are the expensive part. Counting tile entities
 * means walking every loaded chunk, so it is gated behind a config flag and
 * sampled on a slower cadence than the cheap gauges. A metrics exporter that
 * measurably degrades the thing it measures is not useful.
 */
public final class MetricRegistry {

    private final JavaPlugin plugin;
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final AtomicReference<MetricSnapshot> current =
            new AtomicReference<>(MetricSnapshot.empty());

    private final long startedAt = System.currentTimeMillis();

    private BukkitTask task;
    private int tickCounter;
    private int cachedTileEntities;

    public MetricRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = Math.max(20L, plugin.getConfig()
                .getLong("exporter.sample-interval-ticks", 100L));
        this.task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::sample, interval, interval);
    }

    private void sample() {
        var server = plugin.getServer();
        double[] tps = server.getTPS();

        int chunks = 0;
        int entities = 0;
        for (World world : server.getWorlds()) {
            chunks += world.getLoadedChunks().length;
            entities += world.getEntities().size();
        }

        // Tile entities require a full chunk walk, so refresh them less often.
        boolean countTiles = plugin.getConfig()
                .getBoolean("exporter.count-tile-entities", true);
        int tileRefreshEvery = Math.max(1, plugin.getConfig()
                .getInt("exporter.tile-entity-refresh-multiplier", 6));

        if (countTiles && tickCounter % tileRefreshEvery == 0) {
            int tiles = 0;
            for (World world : server.getWorlds()) {
                for (var chunk : world.getLoadedChunks()) {
                    BlockState[] states = chunk.getTileEntities(false);
                    tiles += states.length;
                }
            }
            cachedTileEntities = tiles;
        }
        tickCounter++;

        var heap = memory.getHeapMemoryUsage();

        current.set(new MetricSnapshot(
                tps.length > 0 ? tps[0] : 20,
                tps.length > 1 ? tps[1] : 20,
                tps.length > 2 ? tps[2] : 20,
                server.getAverageTickTime(),
                heap.getUsed(),
                heap.getMax(),
                heap.getCommitted(),
                server.getOnlinePlayers().size(),
                server.getMaxPlayers(),
                chunks,
                entities,
                countTiles ? cachedTileEntities : -1,
                server.getWorlds().size(),
                (System.currentTimeMillis() - startedAt) / 1000L,
                System.currentTimeMillis()));
    }

    public MetricSnapshot snapshot() {
        return current.get();
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }
}
