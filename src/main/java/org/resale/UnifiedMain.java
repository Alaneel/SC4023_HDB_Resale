package org.resale;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * UnifiedMain.java
 *
 * Unified main program for analyzing HDB resale flat data with standardized benchmarking.
 * This program can run both the basic ColumnStore and the EnhancedColumnStore
 * implementations with consistent benchmark reporting for direct comparison.
 */
@Slf4j
public class UnifiedMain {
    // Configuration constants
    private static final String DEFAULT_FILEPATH = "data/ResalePricesSingapore.csv";
    private static final long MIN_MEMORY_FOR_STANDARD = 2L * 1024 * 1024 * 1024; // 2GB

    public static void main(String[] args) {
        // Parse command line arguments
        CommandLineOptions options = parseCommandLineArgs(args);

        if (options == null) {
            // Display usage if parsing failed
            displayUsage();
            return;
        }

        // Create benchmark report directory if it doesn't exist
        File benchmarkDir = new File("benchmark");
        if (!benchmarkDir.exists()) {
            benchmarkDir.mkdirs();
        }

        // Create benchmark reporter
        String benchmarkFilePath = "benchmark/benchmark_" + options.implementationType + "_" +
                options.matricNumber + ".txt";

        BenchmarkReporter benchmark = new BenchmarkReporter(
                options.implementationType,  // Name of implementation
                true,                        // Enable detailed logging
                true,                        // Write to file
                benchmarkFilePath           // File path
        );

        try {
            // Format dates for query
            String startDate = String.format("%d-%02d", options.year, options.startMonth);
            String endDate;

            if (options.startMonth == 12) {
                endDate = String.format("%d-01", options.year + 1);
            } else {
                endDate = String.format("%d-%02d", options.year, options.startMonth + 1);
            }

            // Record configuration in benchmark
            benchmark.recordMetric("Implementation Type", options.implementationType);
            benchmark.recordMetric("Input File", options.filepath);
            benchmark.recordMetric("Matric Number", options.matricNumber);
            benchmark.recordMetric("Target Town", options.targetTown);
            benchmark.recordMetric("Year", options.year);
            benchmark.recordMetric("Month", options.startMonth);
            benchmark.recordMetric("Min Area", options.minArea);
            benchmark.recordMetric("Start Date", startDate);
            benchmark.recordMetric("End Date", endDate);
            benchmark.recordMetric("Available Processors", Runtime.getRuntime().availableProcessors());
            benchmark.recordMetric("Max Memory (MB)", Runtime.getRuntime().maxMemory() / (1024 * 1024));

            // Run appropriate implementation based on command line option
            if (options.implementationType.equalsIgnoreCase("columnstore")) {
                runColumnStore(options, benchmark, startDate, endDate);
            } else if (options.implementationType.equalsIgnoreCase("enhancedcolumnstore")) {
                runEnhancedColumnStore(options, benchmark, startDate, endDate);
            } else {
                log.error("Error: Unknown implementation type - {}", options.implementationType);
                return;
            }

            // Generate the final benchmark report
            benchmark.generateReport();

        } catch (IOException e) {
            log.error("Error processing file: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }
    }

    /**
     * Run the basic ColumnStore implementation with benchmarking
     */
    private static void runColumnStore(CommandLineOptions options, BenchmarkReporter benchmark,
                                       String startDate, String endDate) throws IOException {
        // Initialize ColumnStore with the benchmark instance
        ColumnStore store = new ColumnStore(benchmark);

        // Load data
        benchmark.printSectionHeading("Loading Data");
        store.loadData(options.filepath);

        // Run query and calculate statistics
        benchmark.printSectionHeading("Running Query");
        runQuery(options, benchmark, startDate, endDate, store);
    }

    /**
     * Run the EnhancedColumnStore implementation with benchmarking
     */
    private static void runEnhancedColumnStore(CommandLineOptions options, BenchmarkReporter benchmark,
                                               String startDate, String endDate) throws IOException {
        // Determine if we should use memory mapping based on available memory
        boolean useMemoryMapping = shouldUseMemoryMapping();
        benchmark.recordMetric("Memory Mapping", useMemoryMapping ? "Enabled" : "Disabled");

        // Initialize EnhancedColumnStore with the benchmark instance
        EnhancedColumnStore store = new EnhancedColumnStore(benchmark);

        // Load data
        benchmark.printSectionHeading("Loading Data");
        store.loadData(options.filepath, useMemoryMapping);

        // Compress data if requested
        if (options.useCompression) {
            benchmark.printSectionHeading("Compressing Data");
            store.compressData();
        }

        // Run query and calculate statistics
        benchmark.printSectionHeading("Running Query");
        runQuery(options, benchmark, startDate, endDate, store);
    }

    /**
     * Run the query and calculate statistics for both implementations
     */
    private static void runQuery(CommandLineOptions options, BenchmarkReporter benchmark,
                                 String startDate, String endDate, Object storeObject) throws IOException {

        // Create output directory if it doesn't exist
        File resultDir = new File("result");
        if (!resultDir.exists() && !resultDir.mkdirs()) {
            log.error("Failed to create result directory: {}", resultDir.getAbsolutePath());
        }

        // Create output file based on implementation type
        String implName = storeObject instanceof ColumnStore ? "ColumnStore" : "EnhancedColumnStore";
        String outputFile = "result/ScanResult_" + implName + "_" + options.matricNumber + ".csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            // Write CSV header
            writer.println("Year,Month,Town,Category,Value");

            List<Integer> matchingIndices;

            // Execute query based on implementation type
            if (storeObject instanceof ColumnStore store) {
                matchingIndices = store.findMatchingIndices(
                    options.targetTown, startDate, endDate, options.minArea);

                // Process each statistic type
                benchmark.printSectionHeading("Calculating Statistics");
                for (StatisticType type : StatisticType.values()) {
                    QueryResult result = store.calculateStatistics(matchingIndices, type);
                    writer.printf("%d,%02d,%s,%s,%s%n",
                            options.year, options.startMonth, options.targetTown,
                            type.getDisplayName(), result.getValue());
                }
            } else {
                EnhancedColumnStore store = (EnhancedColumnStore) storeObject;
                matchingIndices = store.findMatchingIndices(
                        options.targetTown, startDate, endDate, options.minArea);

                // Process each statistic type
                benchmark.printSectionHeading("Calculating Statistics");
                for (StatisticType type : StatisticType.values()) {
                    QueryResult result = store.calculateStatistics(matchingIndices, type);
                    writer.printf("%d,%02d,%s,%s,%s%n",
                            options.year, options.startMonth, options.targetTown,
                            type.getDisplayName(), result.getValue());
                }
            }
        }

        log.info("\nAnalysis complete. Results written to {}", outputFile);
    }

    /**
     * Determine if we should use memory mapping based on available memory
     */
    private static boolean shouldUseMemoryMapping() {
        long maxMemory = Runtime.getRuntime().maxMemory();
        // Use memory mapping if available memory is limited
        return maxMemory < MIN_MEMORY_FOR_STANDARD;
    }

    /**
     * Parse command line arguments into options
     */
    private static CommandLineOptions parseCommandLineArgs(String[] args) {
        CommandLineOptions options = new CommandLineOptions();

        // Default values
        options.filepath = DEFAULT_FILEPATH;
        options.useCompression = false;
        options.minArea = 80.0;
        options.implementationType = "ColumnStore"; // Default implementation

        if (args.length < 2) {
            log.error("Error: Implementation type and matriculation number are required");
            return null;
        }

        options.implementationType = args[0];
        options.matricNumber = args[1];

        // Parse matriculation number for query parameters
        try {
            int lastDigit = Character.getNumericValue(options.matricNumber.charAt(options.matricNumber.length() - 2));
            int secondLastDigit = Character.getNumericValue(options.matricNumber.charAt(options.matricNumber.length() - 3));
            int thirdLastDigit = Character.getNumericValue(options.matricNumber.charAt(options.matricNumber.length() - 4));

            // Determine year with corrected mapping
            if (lastDigit >= 0 && lastDigit <= 3) {
                options.year = 2020 + lastDigit; // Maps 0,1,2,3 to 2020,2021,2022,2023
            } else {
                options.year = 2010 + lastDigit; // Maps 4,5,6,7,8,9 to 2014,2015,2016,2017,2018,2019
            }

            options.startMonth = secondLastDigit == 0 ? 10 : secondLastDigit;

            // Determine town based on third last digit
            String[] towns = {
                    "BEDOK", "BUKIT PANJANG", "CLEMENTI", "CHOA CHU KANG", "HOUGANG",
                    "JURONG WEST", "PASIR RIS", "TAMPINES", "WOODLANDS", "YISHUN"
            };
            options.targetTown = towns[thirdLastDigit];

        } catch (Exception e) {
            log.error("Error parsing matriculation number: {}", e.getMessage());
            return null;
        }

        // Parse additional options if provided
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--file") && i + 1 < args.length) {
                options.filepath = args[++i];
            } else if (arg.equals("--compress")) {
                options.useCompression = true;
            } else if (arg.equals("--min-area") && i + 1 < args.length) {
                try {
                    options.minArea = Double.parseDouble(args[++i]);
                } catch (NumberFormatException e) {
                    log.error("Error: Invalid minimum area value");
                    return null;
                }
            } else {
                log.error("Error: Unknown option: {}", arg);
                return null;
            }
        }

        return options;
    }

    /**
     * Display usage information
     */
    private static void displayUsage() {
        log.info("Usage: java UnifiedMain <implementation_type> <matriculation_number> [options]");
        log.info("");
        log.info("Implementation Types:");
        log.info("  ColumnStore           Use the basic column store implementation");
        log.info("  EnhancedColumnStore   Use the enhanced column store implementation");
        log.info("");
        log.info("Options:");
        log.info("  --file <filepath>    Specify the input CSV file path");
        log.info("  --compress           Enable data compression (EnhancedColumnStore only)");
        log.info("  --min-area <value>   Specify the minimum floor area (default: 80.0)");
        log.info("");
        log.info("Examples:");
        log.info("  java UnifiedMain ColumnStore U2211641C");
        log.info("  java UnifiedMain EnhancedColumnStore U2211641C --compress");
    }

    /**
     * Class to hold command line options
     */
    private static class CommandLineOptions {
        String implementationType;
        String matricNumber;
        String filepath;
        boolean useCompression;
        String targetTown;
        int year;
        int startMonth;
        double minArea;
    }
}