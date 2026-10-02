# spigot-metric-exporter

![Java](https://img.shields.io/badge/Java_21-ED8B00?logo=openjdk&logoColor=white) ![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?logo=prometheus&logoColor=white) ![Format](https://img.shields.io/badge/exposition-v0.0.4_validated-success) ![License](https://img.shields.io/badge/License-MIT-green)

> Prometheus exporter for TPS, MSPT, heap and chunk counts. No client library,
> ~20KB jar.

## Thread discipline

This is the part a metrics exporter has to get right, because getting it wrong
produces intermittent crashes rather than clean failures.

```
Main thread          : sample Bukkit state -> immutable MetricSnapshot
HTTP thread (daemon) : read snapshot reference -> render text
```

The HTTP handler **never** touches the Bukkit API. Reading `world.getEntities()`
from a foreign thread is the standard way plugins like this cause
`ConcurrentModificationException` crashes under load.

`MetricSnapshot` is a `record`, so the HTTP thread gets a self-consistent set of
values with no locking — swapping an immutable reference is atomic.

## No client library

The JDK's built-in `HttpServer` keeps the jar at ~20KB and avoids the classpath
conflicts that arise when several plugins each shade a different version of the
Prometheus client.

Output was validated against the exposition spec:

```
metrics: 14  samples: 16  HELP: 14  TYPE: 14
trailing newline: True
ERRORS: none — valid Prometheus exposition format v0.0.4
```

## Metrics

| Metric | Type | Notes |
|---|---|---|
| `minecraft_tps{window}` | gauge | Labelled `1m`, `5m`, `15m` |
| `minecraft_mspt_mean` | gauge | Against the 50ms tick budget |
| `minecraft_heap_used_bytes` | gauge | Plus `_max_`, `_committed_`, `_used_ratio` |
| `minecraft_players_online` | gauge | Plus `_max` |
| `minecraft_chunks_loaded` | gauge | All worlds |
| `minecraft_entities_total` | gauge | All worlds |
| `minecraft_tile_entities_total` | gauge | Omitted when disabled |
| `minecraft_sample_age_seconds` | gauge | **Detects a blocked main thread** |

`minecraft_sample_age_seconds` is the most useful one for alerting. If sampling
stops, the main thread is stuck — and the other metrics would otherwise go
stale-but-plausible, which is how a hung server looks healthy on a dashboard.

## Cost control

```yaml
exporter:
  sample-interval-ticks: 100          # 5s
  count-tile-entities: true
  tile-entity-refresh-multiplier: 6   # tiles every 6th sample
```

Tile-entity counting walks every loaded chunk and is by far the most expensive
metric here, so it is sampled on a slower cadence. An exporter that measurably
degrades the thing it measures is not useful.

## Security

**The endpoint is unauthenticated by default and binds to loopback.**

It exposes player counts, heap sizing and uptime. Two safe deployments:

```yaml
# 1. Prometheus scrapes locally (preferred)
exporter: { bind: '127.0.0.1' }

# 2. Remote scrape — token AND firewall, not one or the other
exporter: { bind: '0.0.0.0', bearer-token: 'generate-a-long-random-value' }
```

The plugin logs a warning when bound to a non-loopback address.

## Prometheus setup

```yaml
scrape_configs:
  - job_name: 'minecraft'
    scrape_interval: 15s        # must exceed sample-interval-ticks
    static_configs:
      - targets: ['10.0.0.11:9225']
```

Scrape interval must be **longer** than the sampling interval, or consecutive
scrapes return the same sample and graphs show artificial plateaus.

`prometheus/alerts.yml` ships seven rules. Each holds `for:` several minutes —
alerting on an instantaneous dip pages on every chunk-save burst, while
sustained degradation is what needs a human:

| Alert | Threshold | Severity |
|---|---|---|
| `MinecraftLowTPS` | 5m TPS < 18 for 5m | warning |
| `MinecraftCriticalTPS` | 1m TPS < 12 for 2m | critical |
| `MinecraftTickBudgetExceeded` | MSPT > 45 for 5m | warning |
| `MinecraftHeapPressure` | heap > 90% for 10m | warning |
| `MinecraftExporterStale` | sample age > 60s | critical |

## Build

```bash
mvn clean package
# target/spigot-metric-exporter-1.0.0.jar
```

`/metrics` shows the endpoint and current readings in-game; `/healthz` returns
`ok` for orchestrator probes.

## License

MIT — see [LICENSE](LICENSE).
