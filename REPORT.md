# HDB Resale Price Analysis System

## 1. Introduction

This project implements a column-oriented data management system for analyzing Housing and Development Board (HDB) resale flat transactions in Singapore from 2014 to 2023. The primary objective was to develop an efficient solution that stores data in columns rather than rows, enabling optimized queries and analysis on specific attributes without loading unnecessary data.

Our implementation follows the requirements of computing statistical metrics (minimum price, average price, standard deviation, and minimum price per square meter) for resale flats with area ≥ 80 square meters in specific towns over consecutive months.

### 1.1 Project Requirements

The system was designed to:
- Store HDB resale transaction data in a column-oriented manner
- Process queries based on parameters derived from matriculation numbers
- Compute four types of statistical analyses on filtered data
- Produce accurate results validated against supplementary tools

## 2. System Design

### 2.1 Column-Oriented Storage

Our implementation (`ColumnStore.java`) stores each attribute of the HDB data in separate arrays:

```java
private final List<String> months;      // Format: YYYY-MM
private final List<String> towns;       // Town names
private final List<Double> floorAreas;  // Floor areas in square meters
private final List<Double> resalePrices;// Resale prices in SGD
```

This column-oriented approach offers two key advantages:

1. **Improved Query Performance**: When querying specific attributes (e.g., town, floor area), we only need to scan relevant columns rather than entire rows.
2. **Better Cache Locality**: Similar values stored together improve CPU cache utilization.

### 2.2 Query Processing

Query processing follows three main steps:

1. **Filtering**: Scan columns to find records matching query criteria (town, date range, minimum area)
2. **Index Collection**: Gather indices of matching records
3. **Statistical Calculation**: Compute required statistics on the filtered dataset

```java
// Example of the filtering process (simplified)
for (int i = 0; i < months.size(); i++) {
    if (towns.get(i).equals(targetTown) &&
        isInDateRange(months.get(i), startMonth, endMonth) &&
        floorAreas.get(i) >= minArea) {
        matchingIndices.add(i);
    }
}
```

### 2.3 Statistical Calculations

The system implements four statistical calculations as required:

1. **Minimum Price**: Finding the smallest value in the filtered resale prices
2. **Average Price**: Computing the mean of all filtered resale prices
3. **Standard Deviation**: Measuring price dispersion around the mean
4. **Minimum Price per Square Meter**: Finding the minimum value of price divided by area

Each calculation is performed using Java Stream API for concise code:

```java
// Example: Calculate average price
result = indices.stream()
        .mapToDouble(i -> resalePrices.get(i))
        .average()
        .orElse(0.0);
```

## 3. Implementation Details

### 3.1 Data Loading

The data loading process reads the CSV file line by line, storing each column value in its respective array:

```java
// Store each column value in its respective array
months.add(values[0].trim());
towns.add(values[1].trim());
floorAreas.add(Double.parseDouble(values[6].trim()));
resalePrices.add(Double.parseDouble(values[9].trim()));
```

The system handles header rows and potential formatting issues by using appropriate data types and validation.

### 3.2 Date Range Filtering

To filter records within a specific date range, we parse the month strings into YearMonth objects for proper comparison:

```java
// Parse date range for comparison
YearMonth start = YearMonth.parse(startMonth);
YearMonth end = YearMonth.parse(endMonth);

// Apply date range filter
YearMonth current = YearMonth.parse(months.get(i));
if (!current.isBefore(start) && !current.isAfter(end)) {
    // Record is within date range
}
```

This approach correctly handles month boundaries and ensures accurate date filtering.

### 3.3 Exception Handling

The system includes comprehensive exception handling to manage common issues:

- File not found exceptions
- Number format exceptions for numeric data
- Date parsing errors
- Empty result handling

Each exception is appropriately logged to provide clear feedback to the user.

## 4. Results Validation

To ensure accuracy, we validated our results against Microsoft Excel calculations. The query for Average Price in TAMPINES from January 2022 to February 2022 for flats ≥80 square meters yielded 585243.18 SGD in both our system and Excel, confirming correctness.

The validation process included:
1. Filtering data in Excel using the same criteria
2. Applying equivalent statistical formulas
3. Comparing results with our system's output
4. Verifying precision (two decimal places)

## 5. Conclusion

This project successfully implemented a column-oriented data management system for analyzing HDB resale flat transactions. Our solution meets all the requirements specified in the project description, efficiently processing queries and calculating statistical metrics for filtered data.

The column-oriented approach proved effective for this specific use case, providing good performance for analytical queries over the dataset. The system correctly handles filtering based on town, date range, and minimum area criteria, producing accurate statistical results validated against external tools.

Future improvements could include additional indexing strategies to further improve query performance or more sophisticated error handling for edge cases.