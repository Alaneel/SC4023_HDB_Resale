# HDB Resale Price Analysis System

## Background

This project implements a column-oriented data management system for analyzing Housing and Development Board (HDB) resale flat transactions in Singapore from 2014 to 2023. The system processes historical transaction records to compute various statistics including minimum price, average price, standard deviation of price, and minimum price per square meter for flats meeting specific criteria.

The analysis is performed based on individual matriculation numbers, where different digits in the matriculation number determine the parameters for data analysis:
- Last digit: Determines the year (2010 + last digit)
- Second last digit: Determines the starting month (0 represents October)
- Third last digit: Determines the target town according to a predefined mapping

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
                    ├── ColumnStore.java       # Column-oriented storage implementation
                    ├── Main.java              # Program entry point
                    ├── QueryResult.java       # Query result handling
                    └── StatisticType.java     # Statistical calculation types
```

## Technologies Used

The project utilizes several key technologies:

1. Java 17: The primary programming language
2. Maven: Build automation and dependency management
3. Lombok: Reduces boilerplate code through annotations
    - @Getter: Automatically generates getter methods
    - @AllArgsConstructor: Generates a constructor with all fields
    - Additional Lombok annotations as needed for clean code

## Prerequisites

- Java Development Kit (JDK) 17 or higher
- Apache Maven 3.6 or higher
- Lombok plugin installed in your IDE
- Minimum 4GB RAM recommended for processing large datasets

## Dependencies

The project uses Maven for dependency management. Key dependencies include:

```xml
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>1.18.36</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

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

## Development Notes

The project uses Lombok to reduce boilerplate code and improve maintainability:
- The `QueryResult` class uses `@AllArgsConstructor` and `@Getter` to automatically generate constructors and getter methods
- The `StatisticType` enum similarly uses Lombok annotations to reduce verbosity
- IDE configuration may be required to properly recognize Lombok annotations

## Error Handling

The program includes robust error handling for common scenarios:
- Invalid matriculation number format
- Missing or inaccessible input file
- Malformed data in the CSV file
- Insufficient system memory

Error messages will be displayed in the console with appropriate context and suggested remediation steps.

## IDE Setup

To work with this project in an IDE, ensure you have the Lombok plugin installed:

For IntelliJ IDEA:
1. Go to Settings/Preferences
2. Navigate to Plugins
3. Search for "Lombok"
4. Install the plugin and restart the IDE

For Eclipse:
1. Download lombok.jar from projectlombok.org
2. Run the jar file and follow the installation instructions
3. Restart Eclipse

This setup ensures proper code completion and compilation with Lombok annotations.