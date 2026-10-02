package com.fivemcodehub.metrics.command;

import com.fivemcodehub.metrics.collect.MetricRegistry;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class MetricsCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final MetricRegistry registry;
    private final String bind;
    private final int port;

    public MetricsCommand(JavaPlugin plugin, MetricRegistry registry, String bind, int port) {
        this.plugin = plugin;
        this.registry = registry;
        this.bind = bind;
        this.port = port;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        var snapshot = registry.snapshot();

        sender.sendMessage(MM.deserialize("<gray>──── SpigotMetricExporter ────</gray>"));
        sender.sendMessage(MM.deserialize("<gray>Endpoint:</gray> <white><url></white>",
                Placeholder.unparsed("url", "http://" + bind + ":" + port
                        + plugin.getConfig().getString("exporter.path", "/metrics"))));
        sender.sendMessage(MM.deserialize(
                "<gray>TPS:</gray> <white><t></white>  <gray>MSPT:</gray> <white><m></white>",
                Placeholder.unparsed("t", String.format("%.2f", snapshot.tps1m())),
                Placeholder.unparsed("m", String.format("%.2f", snapshot.msptMean()))));
        sender.sendMessage(MM.deserialize(
                "<gray>Heap:</gray> <white><u>/<x> MiB (<p>%)</white>",
                Placeholder.unparsed("u", String.valueOf(snapshot.heapUsedBytes() / 1048576)),
                Placeholder.unparsed("x", String.valueOf(snapshot.heapMaxBytes() / 1048576)),
                Placeholder.unparsed("p", String.format("%.0f", snapshot.heapUsedRatio() * 100))));
        sender.sendMessage(MM.deserialize(
                "<gray>Chunks:</gray> <white><c></white>  "
                        + "<gray>Entities:</gray> <white><e></white>  "
                        + "<gray>Tiles:</gray> <white><b></white>",
                Placeholder.unparsed("c", String.valueOf(snapshot.loadedChunks())),
                Placeholder.unparsed("e", String.valueOf(snapshot.entityCount())),
                Placeholder.unparsed("b", snapshot.tileEntityCount() < 0
                        ? "off" : String.valueOf(snapshot.tileEntityCount()))));
        return true;
    }
}
