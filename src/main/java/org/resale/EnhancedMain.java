package org.resale;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Enhanced Main.java
 *
 * Enhanced main program for analyzing HDB resale flat data with performance optimizations.
 * This version includes:
 * - Automatic memory usage detection
 * - Memory-mapped file support for large datasets
 * - Performance monitoring
 * - Multi-threaded execution
 * - Command line options for customization
 * - Corrected year mapping for matriculation numbers
 */
@Slf4j
public class EnhancedMain {
    // Configuration constants
    private static final long MIN_MEMORY_FOR_STANDARD = 2L * 1024 * 1024 * 1024; // 2GB
    private static final String DEFAULT_FILEPATH = "data/ResalePricesSingapore.csv";

    public static void main(String[] args) {
        // Parse command line arguments
        CommandLineOptions options = parseCommandLineArgs(args);

        if (options == null) {
            // Display usage if parsing failed
            displayUsage();
            return;
        }

        try {
            // Measure execution time
            long startTime = System.nanoTime();

            // Initialize storage
            EnhancedColumnStore store = new EnhancedColumnStore();

            // Determine if we should use memory mapping based on available memory
            boolean useMemoryMapping = shouldUseMemoryMapping();

            // Log configuration options
            log.info("Configuration:");
            log.info("  Input file: {}", options.filepath);
            log.info("  Memory mapping: {}", (useMemoryMapping ? "Enabled" : "Disabled"));
            log.info("  Available processors: {}", Runtime.getRuntime().availableProcessors());
            log.info("  Max memory: {} MB",
                    (Runtime.getRuntime().maxMemory() / (1024 * 1024)));

            log.info("\nLoading data...");
            store.loadData(options.filepath, useMemoryMapping);

            // Compress data if requested
            if (options.useCompression) {
                log.info("Compressing data...");
                store.compressData();
            }

            log.info("Data loaded: {} records", store.getTotalRows());

            // Format dates for query
            String startDate = String.format("%d-%02d", options.year, options.startMonth);
            String endDate;

            if (options.startMonth == 12) {
                endDate = String.format("%d-01", options.year + 1);
            } else {
                endDate = String.format("%d-%02d", options.year, options.startMonth + 1);
            }

            // Create output directory if it doesn't exist
            File resultDir = new File("result");
            if (!resultDir.exists() && !resultDir.mkdirs()) {
                log.error("Failed to create result directory: {}", resultDir.getAbsolutePath());
                return;
            }

            // Create output file
            String outputFile = "result/ScanResult_" + options.matricNumber + ".csv";
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write CSV header
                writer.println("Year,Month,Town,Category,Value");

                log.info("\nPerforming query for:");
                log.info("  Town: {}", options.targetTown);
                log.info("  Date range: {} to {}", startDate, endDate);
                log.info("  Minimum area: {} sqm", options.minArea);

                // Find matching indices for the given criteria
                long queryStartTime = System.nanoTime();
                List<Integer> matchingIndices = store.findMatchingIndices(
                        options.targetTown,
                        startDate,
                        endDate,
                        options.minArea);
                long queryEndTime = System.nanoTime();

                log.info("Query completed in {} ms",
                        TimeUnit.NANOSECONDS.toMillis(queryEndTime - queryStartTime));
                log.info("Found {} matching records", matchingIndices.size());

                // Process each statistic type
                log.info("\nCalculating statistics...");
                for (StatisticType type : StatisticType.values()) {
                    long statStartTime = System.nanoTime();
                    QueryResult result = store.calculateStatistics(matchingIndices, type);
                    long statEndTime = System.nanoTime();

                    writer.printf("%d,%02d,%s,%s,%s%n",
                            options.year,
                            options.startMonth,
                            options.targetTown,
                            type.getDisplayName(),
                            result.getValue());

                    log.info("  {}: {} (calculated in {} ms)",
                            type.getDisplayName(),
                            result.getValue(),
                            TimeUnit.NANOSECONDS.toMillis(statEndTime - statStartTime));
                }
            }

            long endTime = System.nanoTime();
            log.info("\nAnalysis complete. Results written to {}", outputFile);
            log.info("Total execution time: {} ms",
                    TimeUnit.NANOSECONDS.toMillis(endTime - startTime));

        } catch (IOException e) {
            log.error("Error processing file: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
        }
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

        if (args.length < 1) {
            log.error("Error: Matriculation number is required");
            return null;
        }

        options.matricNumber = args[0];

        // Parse matriculation number for query parameters
        try {
            int lastDigit = Character.getNumericValue(options.matricNumber.charAt(options.matricNumber.length() - 2));
            int secondLastDigit = Character.getNumericValue(options.matricNumber.charAt(options.matricNumber.length() - 3));
            int thirdLastDigit = Character.getNumericValue(options.matricNumber.charAt(options.matricNumber.length() - 4));

            // Print the extracted digits for verification
            log.info("Matriculation number: {}", options.matricNumber);
            log.info("Extracted digits:");
            log.info("  Last digit: {}", lastDigit);
            log.info("  Second last digit: {}", secondLastDigit);
            log.info("  Third last digit: {}", thirdLastDigit);

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

            log.info("\nQuery parameters:");
            log.info("  Year: {}", options.year);
            log.info("  Month: {}", options.startMonth);
            log.info("  Town: {}", options.targetTown);

        } catch (Exception e) {
            log.error("Error parsing matriculation number: {}", e.getMessage());
            return null;
        }

        // Parse additional options if provided
        for (int i = 1; i < args.length; i++) {
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
        log.info("Usage: java EnhancedMain <matriculation_number> [options]");
        log.info("");
        log.info("Options:");
        log.info("  --file <filepath>    Specify the input CSV file path");
        log.info("  --compress           Enable data compression");
        log.info("  --min-area <value>   Specify the minimum floor area (default: 80.0)");
        log.info("");
        log.info("Example:");
        log.info("  java EnhancedMain U2211641C --compress");
    }

    /**
     * Class to hold command line options
     */
    private static class CommandLineOptions {
        String matricNumber;
        String filepath;
        boolean useCompression;
        String targetTown;
        int year;
        int startMonth;
        double minArea;
    }
}