package com.fivemcodehub.metrics.http;

import com.fivemcodehub.metrics.collect.MetricRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP endpoint serving /metrics.
 *
 * <p>Runs on its own single-threaded executor. The handler only reads an
 * immutable snapshot, so it never touches the Bukkit API from a foreign
 * thread — doing so is the standard way plugins like this cause
 * ConcurrentModificationException crashes.
 */
public final class ExporterServer {

    private static final String CONTENT_TYPE =
            "text/plain; version=0.0.4; charset=utf-8";

    private final JavaPlugin plugin;
    private final MetricRegistry registry;
    private final String bind;
    private final int port;

    private HttpServer server;

    public ExporterServer(JavaPlugin plugin, MetricRegistry registry,
                          String bind, int port) throws IOException {
        this.plugin = plugin;
        this.registry = registry;
        this.bind = bind;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
    }

    public void start() {
        String path = plugin.getConfig().getString("exporter.path", "/metrics");
        String token = plugin.getConfig().getString("exporter.bearer-token", "");

        server.createContext(path == null ? "/metrics" : path, exchange -> {
            try {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                // Optional shared-secret check for exporters that must listen
                // on a routable address.
                if (token != null && !token.isEmpty()) {
                    String provided = exchange.getRequestHeaders()
                            .getFirst("Authorization");
                    if (provided == null || !provided.equals("Bearer " + token)) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                }

                byte[] body = PrometheusFormatter.render(registry.snapshot())
                        .getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", CONTENT_TYPE);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (IOException ex) {
                plugin.getLogger().fine("Scrape failed: " + ex.getMessage());
            } finally {
                exchange.close();
            }
        });

        // Health endpoint, so an orchestrator can probe without parsing metrics.
        server.createContext("/healthz", (HttpExchange exchange) -> {
            byte[] body = "ok\n".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });

        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "metrics-exporter");
            thread.setDaemon(true);
            return thread;
        }));

        server.start();
    }

    public String endpoint() {
        return "http://" + bind + ":" + port
                + plugin.getConfig().getString("exporter.path", "/metrics");
    }

    public void stop() {
        if (server != null) {
            // Small delay lets an in-flight scrape finish rather than
            // returning a truncated body.
            server.stop(1);
            server = null;
        }
    }
}
