package com.fivemcodehub.metrics.http;

import com.fivemcodehub.metrics.collect.MetricSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the rendered output against Prometheus text exposition format
 * v0.0.4.
 *
 * <p>A malformed body does not throw — Prometheus simply drops the target and
 * the dashboard goes quietly blank, so the format has to be asserted.
 */
class PrometheusFormatterTest {

    private static final Pattern SAMPLE =
            Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{[^}]*})?\\s+(-?[0-9.eE+]+)$");

    private static MetricSnapshot sample() {
        return new MetricSnapshot(
                19.87, 19.92, 19.95, 32.47,
                3_221_225_472L, 8_589_934_592L, 4_294_967_296L,
                87, 150, 1842, 9431, 2104, 3, 86_400,
                System.currentTimeMillis());
    }

    @Test
    @DisplayName("every sample line parses and carries HELP and TYPE metadata")
    void everySampleIsWellFormed() {
        String body = PrometheusFormatter.render(sample());

        Set<String> helps = new HashSet<>();
        Set<String> types = new HashSet<>();
        List<String> samples = new ArrayList<>();

        for (String line : body.split("\n")) {
            if (line.isEmpty()) continue;
            if (line.startsWith("# HELP ")) {
                helps.add(line.split(" ")[2]);
            } else if (line.startsWith("# TYPE ")) {
                String[] parts = line.split(" ");
                types.add(parts[2]);
                assertEquals("gauge", parts[3], "only gauges are emitted");
            } else if (!line.startsWith("#")) {
                Matcher matcher = SAMPLE.matcher(line);
                assertTrue(matcher.matches(), () -> "malformed sample line: " + line);
                samples.add(matcher.group(1));
                assertDoesNotThrow(() -> Double.parseDouble(matcher.group(3)));
            }
        }

        assertFalse(samples.isEmpty(), "expected at least one sample");
        for (String name : samples) {
            assertTrue(helps.contains(name), () -> name + " is missing a HELP line");
            assertTrue(types.contains(name), () -> name + " is missing a TYPE line");
        }
    }

    @Test
    @DisplayName("body ends with a newline")
    void endsWithNewline() {
        // Prometheus rejects a body whose final line is unterminated.
        assertTrue(PrometheusFormatter.render(sample()).endsWith("\n"));
    }

    @Test
    @DisplayName("TPS is emitted once per window label")
    void tpsIsLabelledPerWindow() {
        String body = PrometheusFormatter.render(sample());
        assertTrue(body.contains("minecraft_tps{window=\"1m\"} 19.87"));
        assertTrue(body.contains("minecraft_tps{window=\"5m\"} 19.92"));
        assertTrue(body.contains("minecraft_tps{window=\"15m\"} 19.95"));
    }

    @Test
    @DisplayName("tile-entity metric is omitted when collection is disabled")
    void tileEntitiesOmittedWhenDisabled() {
        // -1 is the sentinel for "not collected". Emitting -1 would be read as
        // a real measurement and plotted.
        MetricSnapshot disabled = new MetricSnapshot(
                20, 20, 20, 10, 1L, 2L, 1L, 0, 20, 10, 10, -1, 1, 5,
                System.currentTimeMillis());
        assertFalse(PrometheusFormatter.render(disabled)
                .contains("minecraft_tile_entities_total"));
        assertTrue(PrometheusFormatter.render(sample())
                .contains("minecraft_tile_entities_total 2104"));
    }

    @Test
    @DisplayName("a zero maximum heap does not divide by zero")
    void zeroHeapMaxIsSafe() {
        MetricSnapshot zeroHeap = new MetricSnapshot(
                20, 20, 20, 0, 0L, 0L, 0L, 0, 0, 0, 0, 0, 0, 0,
                System.currentTimeMillis());
        assertEquals(0.0, zeroHeap.heapUsedRatio());
        assertDoesNotThrow(() -> PrometheusFormatter.render(zeroHeap));
    }

    @Test
    @DisplayName("heap ratio is computed correctly")
    void heapRatio() {
        assertEquals(0.375, sample().heapUsedRatio(), 0.0001);
    }

    @Test
    @DisplayName("sample age grows with wall-clock time")
    void sampleAgeReflectsStaleness() {
        // minecraft_sample_age_seconds is how a blocked main thread is
        // detected, so it has to track real time rather than stay at zero.
        MetricSnapshot old = new MetricSnapshot(
                20, 20, 20, 0, 1L, 2L, 1L, 0, 20, 0, 0, 0, 1, 5,
                System.currentTimeMillis() - 90_000L);
        assertTrue(old.ageMillis() >= 90_000L);
        assertTrue(PrometheusFormatter.render(old).contains("minecraft_sample_age_seconds"));
    }

    @Test
    @DisplayName("no metric name is emitted twice")
    void noDuplicateMetricNames() {
        // A repeated name makes Prometheus discard the whole scrape.
        List<String> names = new ArrayList<>();
        for (String line : PrometheusFormatter.render(sample()).split("\n")) {
            if (line.startsWith("# TYPE ")) names.add(line.split(" ")[2]);
        }
        assertEquals(new HashSet<>(names).size(), names.size(), "duplicate TYPE declaration");
    }
}
