package org.resale;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * BenchmarkReporter.java
 *
 * A unified benchmarking framework for HDB resale data analysis.
 * This class provides standardized timing, reporting, and logging
 * functionality to ensure consistent benchmark measurements across
 * different implementations.
 */
public class BenchmarkReporter {

    // Benchmark stages
    public enum Stage {
        INITIALIZATION("Initialization"),
        DATA_LOADING("Data Loading"),
        INDEX_BUILDING("Index Building"),
        QUERY_EXECUTION("Query Execution"),
        STATISTICS_CALCULATION("Statistics Calculation"),
        TOTAL_EXECUTION("Total Execution");

        private final String displayName;

        Stage(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Timing data
    private final Map<String, Long> startTimes = new HashMap<>();
    private final Map<String, Long> elapsedTimes = new HashMap<>();

    // Performance metrics
    private final Map<String, Object> metrics = new HashMap<>();

    // Implementation info
    private final String implementationName;
    private final boolean enableDetailedLogging;
    private final boolean writeToFile;
    private final String outputFilePath;

    /**
     * Constructor with default settings for benchmark reporter
     *
     * @param implementationName Name of the implementation being benchmarked
     */
    public BenchmarkReporter(String implementationName) {
        this(implementationName, true, false, null);
    }

    /**
     * Constructor with custom settings for benchmark reporter
     *
     * @param implementationName Name of the implementation being benchmarked
     * @param enableDetailedLogging Whether to print detailed logs to console
     * @param writeToFile Whether to write benchmark results to a file
     * @param outputFilePath Path to write benchmark results if writeToFile is true
     */
    public BenchmarkReporter(String implementationName, boolean enableDetailedLogging,
                             boolean writeToFile, String outputFilePath) {
        this.implementationName = implementationName;
        this.enableDetailedLogging = enableDetailedLogging;
        this.writeToFile = writeToFile;
        this.outputFilePath = outputFilePath;

        // Record benchmark start time
        this.startTiming(Stage.TOTAL_EXECUTION.name());

        // Print benchmark header
        System.out.println("============================================================");
        System.out.println("  BENCHMARK: " + implementationName);
        System.out.println("  Started at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        System.out.println("============================================================");
    }

    /**
     * Start timing a specific stage or operation
     *
     * @param key Identifier for the operation being timed
     */
    public void startTiming(String key) {
        startTimes.put(key, System.nanoTime());

        if (enableDetailedLogging) {
            System.out.println("[BENCHMARK] Starting: " + key);
        }
    }

    /**
     * Start timing a predefined benchmark stage
     *
     * @param stage The benchmark stage to time
     */
    public void startTiming(Stage stage) {
        startTiming(stage.name());
    }

    /**
     * Stop timing a specific operation and record elapsed time
     *
     * @param key Identifier for the operation being timed
     * @return Elapsed time in milliseconds
     */
    public long stopTiming(String key) {
        if (!startTimes.containsKey(key)) {
            throw new IllegalArgumentException("No timing started for key: " + key);
        }

        long endTime = System.nanoTime();
        long startTime = startTimes.get(key);
        long elapsedTimeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        elapsedTimes.put(key, elapsedTimeMs);

        if (enableDetailedLogging) {
            System.out.println("[BENCHMARK] Completed: " + key + " in " + elapsedTimeMs + " ms");
        }

        return elapsedTimeMs;
    }

    /**
     * Stop timing a predefined benchmark stage
     *
     * @param stage The benchmark stage to stop timing
     * @return Elapsed time in milliseconds
     */
    public long stopTiming(Stage stage) {
        return stopTiming(stage.name());
    }

    /**
     * Record a numeric metric
     *
     * @param key Metric name
     * @param value Metric value
     */
    public void recordMetric(String key, Object value) {
        metrics.put(key, value);

        if (enableDetailedLogging) {
            System.out.println("[BENCHMARK] Metric: " + key + " = " + value);
        }
    }

    /**
     * Print section heading for a benchmark stage
     *
     * @param title Section title
     */
    public void printSectionHeading(String title) {
        System.out.println("\n---- " + title + " ----");
    }

    /**
     * Generate and print the final benchmark report
     */
    public void generateReport() {
        // Stop overall timing if still running
        if (startTimes.containsKey(Stage.TOTAL_EXECUTION.name()) &&
                !elapsedTimes.containsKey(Stage.TOTAL_EXECUTION.name())) {
            stopTiming(Stage.TOTAL_EXECUTION);
        }

        StringBuilder report = new StringBuilder();

        // Build report header
        report.append("============================================================\n");
        report.append("  BENCHMARK RESULTS: ").append(implementationName).append("\n");
        report.append("  Completed at: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        report.append("============================================================\n\n");

        // Add timing results
        report.append("TIMING RESULTS:\n");
        report.append("------------------------------------------------------------\n");

        // First add predefined stages in order
        for (Stage stage : Stage.values()) {
            if (elapsedTimes.containsKey(stage.name())) {
                report.append(String.format("%-30s: %8d ms\n",
                        stage.getDisplayName(), elapsedTimes.get(stage.name())));
            }
        }

        // Then add any custom timing metrics
        for (Map.Entry<String, Long> entry : elapsedTimes.entrySet()) {
            if (!isStageEnum(entry.getKey())) {
                report.append(String.format("%-30s: %8d ms\n", entry.getKey(), entry.getValue()));
            }
        }

        // Add other metrics
        if (!metrics.isEmpty()) {
            report.append("\nPERFORMANCE METRICS:\n");
            report.append("------------------------------------------------------------\n");

            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                report.append(String.format("%-30s: %s\n", entry.getKey(), entry.getValue()));
            }
        }

        // Print report to console
        System.out.println(report.toString());

        // Write report to file if enabled
        if (writeToFile && outputFilePath != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFilePath))) {
                writer.print(report.toString());
                System.out.println("Benchmark report written to: " + outputFilePath);
            } catch (IOException e) {
                System.err.println("Error writing benchmark report to file: " + e.getMessage());
            }
        }
    }

    /**
     * Check if a key corresponds to a predefined Stage enum value
     */
    private boolean isStageEnum(String key) {
        for (Stage stage : Stage.values()) {
            if (stage.name().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the elapsed time for a specific operation
     *
     * @param key Identifier for the operation
     * @return Elapsed time in milliseconds, or -1 if not found
     */
    public long getElapsedTime(String key) {
        return elapsedTimes.getOrDefault(key, -1L);
    }

    /**
     * Get the elapsed time for a predefined benchmark stage
     *
     * @param stage The benchmark stage
     * @return Elapsed time in milliseconds, or -1 if not found
     */
    public long getElapsedTime(Stage stage) {
        return getElapsedTime(stage.name());
    }
}