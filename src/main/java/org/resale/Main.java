package org.resale;

import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Main.java
 *
 * Main program for analyzing HDB resale flat data.
 * Processes command line arguments, coordinates data loading and analysis,
 * and generates output files with results.
 */
@Slf4j
public class Main {
    public static void main(String[] args) {
        // Validate command line arguments
        if (args.length != 1) {
            log.info("Usage: java Main <matriculation_number>");
            return;
        }

        final String FILEPATH = "data/ResalePricesSingapore.csv";
        String matricNumber = args[0];

        // Parse matriculation number for query parameters
        int lastDigit = Character.getNumericValue(matricNumber.charAt(matricNumber.length() - 2));
        int secondLastDigit = Character.getNumericValue(matricNumber.charAt(matricNumber.length() - 3));
        int thirdLastDigit = Character.getNumericValue(matricNumber.charAt(matricNumber.length() - 4));

        // Determine year and months
        int year;
        if (lastDigit >= 0 && lastDigit <= 3) {
            year = 2020 + lastDigit; // Maps 0,1,2,3 to 2020,2021,2022,2023
        } else {
            year = 2010 + lastDigit; // Maps 4,5,6,7,8,9 to 2014,2015,2016,2017,2018,2019
        }
        int startMonth = secondLastDigit == 0 ? 10 : secondLastDigit;

        // Determine town based on third last digit
        String[] towns = {
                "BEDOK", "BUKIT PANJANG", "CLEMENTI", "CHOA CHU KANG", "HOUGANG",
                "JURONG WEST", "PASIR RIS", "TAMPINES", "WOODLANDS", "YISHUN"
        };
        String targetTown = towns[thirdLastDigit];

        try {
            // Initialize and load data
            ColumnStore store = new ColumnStore();
            store.loadData(FILEPATH);

            // Format dates for query
            String startDate = String.format("%d-%02d", year, startMonth);
            String endDate = String.format("%d-%02d", year, startMonth == 12 ? 1 : startMonth + 1);
            if (startMonth == 12) {
                endDate = String.format("%d-01", year + 1);
            }

            // Create output file
            String outputFile = "result/ScanResult_" + matricNumber + ".csv";
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write CSV header
                writer.println("Year,Month,Town,Category,Value");

                // Find matching indices for the given criteria
                List<Integer> matchingIndices = store.findMatchingIndices(targetTown, startDate, endDate, 80.0);

                // Process each statistic type
                for (StatisticType type : StatisticType.values()) {
                    QueryResult result = store.calculateStatistics(matchingIndices, type);
                    writer.printf("%d,%02d,%s,%s,%s%n",
                            year, startMonth, targetTown, type.getDisplayName(), result.getValue());
                }
            }

            log.info("Analysis complete. Results written to {}", outputFile);

        } catch (IOException e) {
            log.error("Error processing file: {}", e.getMessage());
        }
    }
}