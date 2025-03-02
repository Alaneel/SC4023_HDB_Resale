package org.resale;

import java.io.*;
import java.util.*;
import java.time.YearMonth;

import lombok.extern.slf4j.Slf4j;

/**
 * ColumnStore.java
 *
 * This class implements a column-oriented storage system for HDB resale flat data.
 * It provides functionality to load, store, and analyze HDB resale transactions
 * in a column-oriented manner for efficient querying and analysis.
 * This version uses the unified benchmarking framework.
 */
@Slf4j
class ColumnStore {
    // Column arrays for storing different attributes of the HDB data
    private final List<String> months;      // Format: YYYY-MM
    private final List<String> towns;       // Town names
    private final List<Double> floorAreas;  // Floor areas in square meters
    private final List<Double> resalePrices;// Resale prices in SGD

    // Benchmarking instance
    private final BenchmarkReporter benchmark;

    // Metadata
    private int totalRows = 0;
    private final Map<String, Integer> townCounts = new HashMap<>();
    private final Map<String, Integer> monthCounts = new HashMap<>();

    /**
     * Constructor initializes empty column arrays for data storage.
     */
    public ColumnStore() {
        this(new BenchmarkReporter("ColumnStore"));
    }

    /**
     * Constructor with custom benchmark reporter
     */
    public ColumnStore(BenchmarkReporter benchmark) {
        this.benchmark = benchmark;
        benchmark.startTiming(BenchmarkReporter.Stage.INITIALIZATION);

        this.months = new ArrayList<>();
        this.towns = new ArrayList<>();
        this.floorAreas = new ArrayList<>();
        this.resalePrices = new ArrayList<>();

        benchmark.stopTiming(BenchmarkReporter.Stage.INITIALIZATION);
    }

    /**
     * Loads data from a CSV file into the column store.
     * Each column of data is stored in a separate array for efficient access.
     *
     * @param filepath Path to the input CSV file
     * @throws IOException If there are issues reading the file
     */
    public void loadData(String filepath) throws IOException {
        benchmark.startTiming(BenchmarkReporter.Stage.DATA_LOADING);
        int lineCount = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line = br.readLine(); // Skip header row

            log.info("Header: {}", line);
            log.info("Starting data loading...");

            int batchSize = 100000;

            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                // Store each column value in its respective array
                months.add(values[0].trim());
                towns.add(values[1].trim());
                floorAreas.add(Double.parseDouble(values[6].trim()));
                resalePrices.add(Double.parseDouble(values[9].trim()));

                // Update metadata for benchmarking
                totalRows++;
                townCounts.put(values[1].trim(), townCounts.getOrDefault(values[1].trim(), 0) + 1);
                monthCounts.put(values[0].trim(), monthCounts.getOrDefault(values[0].trim(), 0) + 1);

                // Print progress for large files
                lineCount++;
                if (lineCount % batchSize == 0) {
                    benchmark.recordMetric("Rows Processed", lineCount);
                }
            }
        }

        // Record data loading metrics
        benchmark.recordMetric("Total Rows", totalRows);
        benchmark.recordMetric("Unique Towns", townCounts.size());
        benchmark.recordMetric("Unique Months", monthCounts.size());

        long loadingTime = benchmark.stopTiming(BenchmarkReporter.Stage.DATA_LOADING);
        log.info("Data loading completed in {} ms", loadingTime);

        // Print distribution statistics for debugging
        benchmark.printSectionHeading("Town Distribution (Top 5)");
        townCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> log.info("  {}: {} records", e.getKey(), e.getValue()));

        benchmark.printSectionHeading("Year Distribution");
        Map<String, Integer> yearCounts = new HashMap<>();
        for (Map.Entry<String, Integer> entry : monthCounts.entrySet()) {
            String year = entry.getKey().substring(0, 4);
            yearCounts.put(year, yearCounts.getOrDefault(year, 0) + entry.getValue());
        }

        yearCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> log.info("  {}: {} records", e.getKey(), e.getValue()));
    }

    /**
     * Finds indices of records matching the given criteria.
     *
     * @param targetTown Town to search for
     * @param startMonth Start month in YYYY-MM format
     * @param endMonth End month in YYYY-MM format
     * @param minArea Minimum floor area in square meters
     * @return List of indices of matching records
     */
    public List<Integer> findMatchingIndices(String targetTown, String startMonth, String endMonth, double minArea) {
        List<Integer> matchingIndices = new ArrayList<>();

        benchmark.startTiming(BenchmarkReporter.Stage.QUERY_EXECUTION);
        benchmark.startTiming("Town Filter");

        // Log query parameters for benchmarking
        log.info("Query parameters:");
        log.info("  Target town: {}", targetTown);
        log.info("  Date range: {} to {}", startMonth, endMonth);
        log.info("  Minimum area: {} sqm", minArea);

        // Parse date range for comparison
        YearMonth start = YearMonth.parse(startMonth);
        YearMonth end = YearMonth.parse(endMonth);

        // Track filter performance
        int townMatches = 0;
        int dateRangeMatches = 0;
        int areaMatches = 0;

        // Scan through all records
        for (int i = 0; i < months.size(); i++) {
            // Apply town filter
            if (towns.get(i).equals(targetTown)) {
                townMatches++;

                // Apply date range filter
                YearMonth current = YearMonth.parse(months.get(i));
                if (!current.isBefore(start) && !current.isAfter(end)) {
                    dateRangeMatches++;

                    // Apply floor area filter
                    if (floorAreas.get(i) >= minArea) {
                        areaMatches++;
                        matchingIndices.add(i);
                    }
                }
            }
        }


        // Record filter performance metrics
        final String PERCENTAGE_FORMAT = "%.2f%%";
        benchmark.recordMetric("Records Matching Town", townMatches);
        benchmark.recordMetric("Records Matching Town+Date", dateRangeMatches);
        benchmark.recordMetric("Records Matching All Criteria", areaMatches);
        benchmark.recordMetric("Town Filter Selectivity",
                String.format(PERCENTAGE_FORMAT, (townMatches * 100.0 / totalRows)));
        benchmark.recordMetric("Date Range Selectivity",
                String.format(PERCENTAGE_FORMAT, (dateRangeMatches * 100.0 / townMatches)));
        benchmark.recordMetric("Area Filter Selectivity",
                String.format(PERCENTAGE_FORMAT, (areaMatches * 100.0 / dateRangeMatches)));

        return matchingIndices;
    }

    /**
     * Calculates statistics for the given indices based on the specified statistic type.
     *
     * @param indices List of indices to calculate statistics for
     * @param type Type of statistic to calculate
     * @return QueryResult containing the calculated statistic
     */
    public QueryResult calculateStatistics(List<Integer> indices, StatisticType type) {
        // Return "No result" if no matching records found
        if (indices.isEmpty()) {
            return new QueryResult("No result");
        }

        String opName = "Calculate " + type.getDisplayName();
        benchmark.startTiming(opName);
        benchmark.startTiming(BenchmarkReporter.Stage.STATISTICS_CALCULATION);

        double result;
        switch (type) {
            case MINIMUM_PRICE:
                // Calculate minimum resale price
                result = indices.stream()
                        .mapToDouble(resalePrices::get)
                        .min()
                        .orElse(0.0);
                break;

            case AVERAGE_PRICE:
                // Calculate average resale price
                result = indices.stream()
                        .mapToDouble(resalePrices::get)
                        .average()
                        .orElse(0.0);
                break;

            case STANDARD_DEVIATION:
                // Calculate mean first
                double mean = indices.stream()
                        .mapToDouble(resalePrices::get)
                        .average()
                        .orElse(0.0);

                // Calculate standard deviation
                result = Math.sqrt(indices.stream()
                        .mapToDouble(i -> {
                            double diff = resalePrices.get(i) - mean;
                            return diff * diff;
                        })
                        .average()
                        .orElse(0.0));
                break;

            case MIN_PRICE_PER_SQM:
                // Calculate minimum price per square meter
                result = indices.stream()
                        .mapToDouble(i -> resalePrices.get(i) / floorAreas.get(i))
                        .min()
                        .orElse(0.0);
                break;

            default:
                throw new IllegalArgumentException("Unknown statistic type");
        }

        benchmark.stopTiming(BenchmarkReporter.Stage.STATISTICS_CALCULATION);
        long calcTime = benchmark.stopTiming(opName);

        // Record statistics calculation metrics
        benchmark.recordMetric(type.getDisplayName() + " Calculation Time", calcTime + " ms");
        benchmark.recordMetric(type.getDisplayName() + " Records Processed", indices.size());

        // Format result to 2 decimal places
        return new QueryResult(String.format("%.2f", result));
    }
}