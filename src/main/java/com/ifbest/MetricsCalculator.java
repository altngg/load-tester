package com.ifbest;

import java.util.List;

public class MetricsCalculator {
    public static double calculatePercentile(List<Long> sortedTimes, double percentile) {
        if (sortedTimes == null || sortedTimes.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedTimes.size()) - 1;
        index = Math.max(0, Math.min(index, sortedTimes.size() - 1));
        return sortedTimes.get(index);
    }

    public static double calculateRps(int totalRequests, long totalTestTimeMs) {
        if (totalTestTimeMs <= 0) {
            return 0;
        }
        return (totalRequests * 1000.0) / totalTestTimeMs;
    }

    public static boolean checkSla(double p95, double slaThresholdMs) {
        return p95 <= slaThresholdMs;
    }

    public static double calculateSlaCompliancePercent(List<Long> sortedTimes, double slaThresholdMs) {
        if (sortedTimes == null || sortedTimes.isEmpty()) {
            return 0;
        }
        long compliantCount = sortedTimes.stream()
                .filter(time -> time <= slaThresholdMs)
                .count();
        return (compliantCount * 100.0) / sortedTimes.size();
    }
}
