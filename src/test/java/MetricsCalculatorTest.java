import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.ifbest.MetricsCalculator;

public class MetricsCalculatorTest {
    @Test
    public void testPercentileEmptyList() {
        List<Long> empty = Collections.emptyList();
        assertEquals(0, MetricsCalculator.calculatePercentile(empty, 50));
        assertEquals(0, MetricsCalculator.calculatePercentile(empty, 95));
    }

    @Test
    public void testPercentileSingleElement() {
        List<Long> single = Collections.singletonList(100L);
        assertEquals(100, MetricsCalculator.calculatePercentile(single, 50));
        assertEquals(100, MetricsCalculator.calculatePercentile(single, 99));
    }

    @Test
    public void testPercentileP50() {
        List<Long> times = Arrays.asList(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
        double p50 = MetricsCalculator.calculatePercentile(times, 50);
        assertEquals(50, p50);
    }

    @Test
    public void testPercentileP95() {
        List<Long> times = Arrays.asList(
            10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L,
            110L, 120L, 130L, 140L, 150L, 160L, 170L, 180L, 190L, 200L
        );
        double p95 = MetricsCalculator.calculatePercentile(times, 95);
        assertTrue(p95 >= 190 && p95 <= 200);
    }

    @Test
    public void testRpsCalculation() {
        double rps = MetricsCalculator.calculateRps(100, 2000);
        assertEquals(50.0, rps, 0.01);
    }

    @Test
    public void testRpsZeroTime() {
        double rps = MetricsCalculator.calculateRps(100, 0);
        assertEquals(0, rps);
    }

    @Test
    public void testSlaCheckPassed() {
        assertTrue(MetricsCalculator.checkSla(1500, 2000));
        assertTrue(MetricsCalculator.checkSla(2000, 2000));
    }

    @Test
    public void testSlaCheckFailed() {
        assertFalse(MetricsCalculator.checkSla(2500, 2000));
    }

    @Test
    public void testSlaCompliancePercent() {
        List<Long> times = Arrays.asList(100L, 200L, 300L, 400L, 500L);
        double percent = MetricsCalculator.calculateSlaCompliancePercent(times, 300);
        assertEquals(60.0, percent, 0.01);
    }
}
