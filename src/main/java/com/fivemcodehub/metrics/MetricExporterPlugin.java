package com.fivemcodehub.metrics;

import com.fivemcodehub.metrics.collect.MetricRegistry;
import com.fivemcodehub.metrics.command.MetricsCommand;
import com.fivemcodehub.metrics.http.ExporterServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

/**
 * Prometheus exporter.
 *
 * <p>Uses the JDK's built-in {@code HttpServer} rather than the Prometheus
 * client library, which keeps the jar at a few kilobytes and avoids the
 * classpath conflicts that arise when several plugins each shade a different
 * version of the same library.
 *
 * <p>Collection and serving are separated on purpose: the HTTP handler runs on
 * its own thread and must never read Bukkit state directly. Metrics are
 * sampled on the main thread into a snapshot, and scrapes read that snapshot.
 */
public final class MetricExporterPlugin extends JavaPlugin {

    private MetricRegistry registry;
    private ExporterServer server;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.registry = new MetricRegistry(this);
        this.registry.start();

        int port = getConfig().getInt("exporter.port", 9225);
        String bind = getConfig().getString("exporter.bind", "127.0.0.1");

        try {
            this.server = new ExporterServer(this, registry, bind, port);
            this.server.start();
        } catch (IOException ex) {
            getLogger().severe("Could not bind " + bind + ":" + port + " — " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        var cmd = getCommand("metrics");
        if (cmd != null) cmd.setExecutor(new MetricsCommand(this, registry, bind, port));

        getLogger().info("Metrics exposed on http://" + bind + ":" + port + "/metrics");
        if (!"127.0.0.1".equals(bind)) {
            getLogger().warning("Exporter is bound to a non-loopback address. "
                    + "Restrict access with a firewall; the endpoint is unauthenticated.");
        }
    }

    @Override
    public void onDisable() {
        if (server != null) server.stop();
        if (registry != null) registry.shutdown();
    }
}
