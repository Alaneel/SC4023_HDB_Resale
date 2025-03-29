# HDB Resale Price Analysis System

## Background

This project implements a column-oriented data management system for analyzing Housing and Development Board (HDB) resale flat transactions in Singapore from 2014 to 2023. The system processes historical transaction records to compute various statistics including minimum price, average price, standard deviation of price, and minimum price per square meter for flats meeting specific criteria.

The analysis is performed based on individual matriculation numbers, where different digits in the matriculation number determine the parameters for data analysis:
- Last digit: Determines the year (2010 + last digit or 2020 + last digit for digits 0-3)
- Second last digit: Determines the starting month (0 represents October)
- Third last digit: Determines the target town according to a predefined mapping

## Project Features

This implementation offers several key features:

1. **Column-Oriented Storage**
   - Data stored by columns rather than rows for efficient queries
   - Improved query performance by only scanning relevant columns
   - Better cache locality with similar values stored together

2. **Statistical Analysis**
   - Calculates minimum price of matching flats
   - Computes average price across filtered data
   - Determines standard deviation of prices
   - Identifies minimum price per square meter

3. **Query Processing**
   - Efficiently filters data based on town, date range, and minimum area
   - Processes large datasets through optimized column scanning
   - Handles edge cases such as empty result sets

## Project Structure

The project follows a standard Maven directory structure:

```
.
├── data                        # Input data directory
│   └── ResalePricesSingapore.csv
├── pom.xml                     # Maven configuration file
├── result                      # Output directory for analysis results
│   └── ScanResult_*.csv
└── src                         # Source code directory
    └── main
        └── java
            └── org
                └── resale
                    ├── ColumnStore.java        # Column-oriented storage implementation
                    ├── Main.java               # Entry point
                    ├── QueryResult.java        # Query result handling
                    └── StatisticType.java      # Statistical calculation types
```

## Technologies Used

The project utilizes several key technologies:

1. Java 17: The primary programming language
2. Maven: Build automation and dependency management
3. Lombok: Reduces boilerplate code through annotations
4. Java Stream API: For concise statistical calculations

## Prerequisites

- Java Development Kit (JDK) 17 or higher
- Apache Maven 3.6 or higher
- Lombok plugin installed in your IDE

## Building the Project

To build the project, execute the following command in the project root directory:

```bash
mvn clean package
```

## Running the Analysis

To run the analysis with your matriculation number, use the following Maven command:

```bash
mvn exec:java -Dexec.mainClass="org.resale.Main" -Dexec.arguments="YOUR_MATRIC_NUMBER"
```

For example:
```bash
mvn exec:java -Dexec.mainClass="org.resale.Main" -Dexec.arguments="U2211641C"
```

## Output

The program generates a CSV file named `ScanResult_<MatricNum>.csv` in the `result` directory. The output file contains the following columns:
- Year
- Month
- Town
- Category (type of statistical analysis)
- Value (calculated result)

All numerical results are rounded to two decimal places. If no data matches the specified criteria, the result will be marked as "No result".

## Town Mapping

The third last digit of the matriculation number maps to towns as follows:
- 0: BEDOK
- 1: BUKIT PANJANG
- 2: CLEMENTI
- 3: CHOA CHU KANG
- 4: HOUGANG
- 5: JURONG WEST
- 6: PASIR RIS
- 7: TAMPINES
- 8: WOODLANDS
- 9: YISHUN

## Error Handling

The program includes basic error handling for common scenarios:
- Invalid input file path
- Malformed data in CSV
- Invalid matriculation number format

Error messages are logged to help diagnose issues during execution.