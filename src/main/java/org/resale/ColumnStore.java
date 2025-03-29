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
 */
@Slf4j
class ColumnStore {
    // Column arrays for storing different attributes of the HDB data
    private final List<String> months;      // Format: YYYY-MM
    private final List<String> towns;       // Town names
    private final List<Double> floorAreas;  // Floor areas in square meters
    private final List<Double> resalePrices;// Resale prices in SGD

    // Metadata
    private int totalRows = 0;
    private final Map<String, Integer> townCounts = new HashMap<>();
    private final Map<String, Integer> monthCounts = new HashMap<>();

    /**
     * Constructor initializes empty column arrays for data storage.
     */
    public ColumnStore() {
        this.months = new ArrayList<>();
        this.towns = new ArrayList<>();
        this.floorAreas = new ArrayList<>();
        this.resalePrices = new ArrayList<>();
    }

    /**
     * Loads data from a CSV file into the column store.
     * Each column of data is stored in a separate array for efficient access.
     *
     * @param filepath Path to the input CSV file
     * @throws IOException If there are issues reading the file
     */
    public void loadData(String filepath) throws IOException {
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

                // Update metadata for analysis
                totalRows++;
                townCounts.put(values[1].trim(), townCounts.getOrDefault(values[1].trim(), 0) + 1);
                monthCounts.put(values[0].trim(), monthCounts.getOrDefault(values[0].trim(), 0) + 1);

                // Print progress for large files
                lineCount++;
                if (lineCount % batchSize == 0) {
                    log.info("Processed {} rows", lineCount);
                }
            }
        }

        log.info("Data loading completed. Total rows: {}", totalRows);
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

        // Log query parameters
        log.info("Query parameters:");
        log.info("  Target town: {}", targetTown);
        log.info("  Date range: {} to {}", startMonth, endMonth);
        log.info("  Minimum area: {} sqm", minArea);

        // Parse date range for comparison
        YearMonth start = YearMonth.parse(startMonth);
        YearMonth end = YearMonth.parse(endMonth);

        // Scan through all records
        for (int i = 0; i < months.size(); i++) {
            // Apply town filter
            if (towns.get(i).equals(targetTown)) {
                // Apply date range filter
                YearMonth current = YearMonth.parse(months.get(i));
                if (!current.isBefore(start) && !current.isAfter(end)) {
                    // Apply floor area filter
                    if (floorAreas.get(i) >= minArea) {
                        matchingIndices.add(i);
                    }
                }
            }
        }

        log.info("Found {} matching records", matchingIndices.size());
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

        // Format result to 2 decimal places
        return new QueryResult(String.format("%.2f", result));
    }
}