# HDB Resale Price Analysis System

A high-performance, column-oriented data management system designed to analyze Singapore's Housing and Development Board (HDB) resale flat transactions from 2014 to 2023.

## Introduction

The HDB Resale Price Analysis System efficiently processes and analyzes historical HDB resale transaction records to derive valuable statistical insights. The system employs a column-oriented storage approach that significantly improves query performance for analytical operations by storing data in column-based arrays rather than traditional row-based structures.

The system performs four key statistical analyses on filtered transaction data:
- Minimum resale price
- Average resale price
- Standard deviation of prices
- Minimum price per square meter

Analysis parameters are dynamically determined based on the user's matriculation number, with different digits mapping to specific query parameters (year, month, and town).

## Data Source and Format

The system processes official HDB resale transaction data from 2014 to 2023, stored in CSV format. The input file (`ResalePricesSingapore.csv`) contains detailed transaction records with the following key columns:
- Transaction date (YYYY-MM format)
- Town name
- Flat type
- Floor area (square meters)
- Resale price (SGD)

Each record represents a single HDB flat resale transaction with its associated attributes.

## Key Features

### Column-Oriented Storage
- Data stored by columns rather than rows for efficient analytical queries
- Significant performance improvement by scanning only relevant columns
- Enhanced cache locality with similar values stored together
- Reduced I/O overhead for queries targeting specific attributes

### Comprehensive Statistical Analysis
- **Minimum Price:** Identifies the lowest transaction price among matching records
- **Average Price:** Calculates the mean transaction price across filtered data
- **Standard Deviation:** Measures price variation and market volatility
- **Minimum Price per Square Meter:** Determines the most cost-effective property in terms of price-to-area ratio

### Efficient Query Processing
- Optimized filtering based on town, date range, and minimum area requirements
- High-performance processing of large datasets through selective column scanning
- Robust handling of edge cases including empty result sets
- Memory-efficient implementation suitable for large transaction histories

## System Architecture

### Column-Oriented Design
Unlike traditional row-oriented databases, this system stores each data attribute (month, town, floor area, price) in separate column arrays. This approach significantly improves analytical query performance by:

1. Minimizing I/O by reading only required columns
2. Improving cache utilization through data locality
3. Enabling more efficient compression of similar values
4. Facilitating vectorized operations for statistical calculations

### Query Processing Flow
1. **Parameter Extraction:** Derive query parameters (year, month, town) from matriculation number
2. **Data Loading:** Load CSV data into column-oriented storage structure
3. **Filtering:** Apply town, date range, and minimum area filters to identify matching records
4. **Statistical Analysis:** Perform required calculations on filtered dataset
5. **Results Output:** Format and write results to CSV file

## Project Structure

```
.
├── data/                            # Input data directory
│   └── ResalePricesSingapore.csv    # HDB resale transaction data
├── result/                          # Output directory for analysis results
│   └── ScanResult_*.csv             # Generated results with matriculation number suffix
├── src/
│   └── main/
│       ├── java/
│       │   └── org/
│       │       └── resale/
│       │           ├── ColumnStore.java     # Column-oriented storage implementation
│       │           ├── Main.java            # Application entry point
│       │           ├── QueryResult.java     # Result encapsulation class
│       │           └── StatisticType.java   # Statistical operation types enum
│       └── resources/
│           └── logback.xml                  # Logging configuration
├── .gitignore                       # Git ignore configuration
├── pom.xml                          # Maven project configuration
└── README.md                        # Project documentation
```

### Key Files

- **ColumnStore.java:** Implements the column-oriented storage engine and query processing logic
- **Main.java:** Controls program flow, parameter parsing, and results output
- **QueryResult.java:** Encapsulates query results with appropriate formatting
- **StatisticType.java:** Defines the types of statistical analyses performed
- **logback.xml:** Configures application logging behavior and output formats

## Technologies Used

- **Java 17:** Core programming language with modern features
- **Maven:** Build automation and dependency management
- **Lombok:** Reduces boilerplate code through annotations
- **SLF4J & Logback:** Comprehensive logging framework
- **Java Stream API:** Facilitates concise statistical calculations

## Installation and Setup

### Prerequisites

- Java Development Kit (JDK) 17 or higher
- Apache Maven 3.6 or higher
- Lombok plugin installed in your IDE (IntelliJ IDEA, Eclipse, etc.)
- Minimum 2GB RAM (4GB recommended for larger datasets)

### Installation Steps

1. Clone the repository or download the source code:
   ```bash
   git clone https://github.com/Alaneel/SC4023_HDB_Resale.git
   cd SC4023_HDB_Resale
   ```

2. Ensure your JDK is properly configured:
   ```bash
   java -version  # Should show Java 17 or higher
   ```

3. Install Maven dependencies:
   ```bash
   mvn clean install
   ```

4. Verify the installation:
   ```bash
   mvn test
   ```

5. Place your input data file in the `data/` directory:
   ```bash
   cp /path/to/your/data/ResalePricesSingapore.csv data/
   ```

### Configuration (Optional)

You can adjust logging levels and output locations by modifying `src/main/resources/logback.xml`.

## Running the Analysis

### Command Syntax

To run the analysis with your matriculation number, use the following Maven command:

```bash
mvn exec:java -Dexec.mainClass="org.resale.Main" -Dexec.arguments="YOUR_MATRIC_NUMBER"
```

### Example

```bash
mvn exec:java -Dexec.mainClass="org.resale.Main" -Dexec.arguments="U2211641C"
```

### Parameter Derivation

The system derives analysis parameters from the matriculation number as follows:

1. **Last Digit (N):**
   - If N is 0-3: Year = 2020 + N
   - If N is 4-9: Year = 2010 + N

2. **Second Last Digit (M):**
   - Starting Month = M (where 0 represents October)

3. **Third Last Digit (T):**
   - Town = Mapping from digit to town name (see Town Mapping section)

### Date Range

The system automatically analyzes data for two consecutive months:
- Starting from the derived month
- Including the following month

## Output Format

### Output File

The system generates a CSV file named `ScanResult_<MatricNum>.csv` in the `result/` directory containing the analysis results.

### Column Structure

The output file contains the following columns:
- **Year:** Analysis year derived from matriculation number
- **Month:** Starting month of analysis
- **Town:** Target town for analysis
- **Category:** Type of statistical analysis performed
- **Value:** Calculated result (or "No result" if no matching data)

### Sample Output

```
Year,Month,Town,Category,Value
2022,01,TAMPINES,Minimum Price,460000.00
2022,01,TAMPINES,Average Price,585243.18
2022,01,TAMPINES,Standard Deviation of Price,98765.43
2022,01,TAMPINES,Minimum Price per Square Meter,4836.21
```

### Interpretation

- **Minimum Price:** The lowest resale price found for matching flats
- **Average Price:** The mean resale price across all matching flats
- **Standard Deviation:** Higher values indicate greater price variation
- **Minimum Price per Square Meter:** Lower values indicate more cost-effective properties

## Performance Considerations

- **Memory Usage:** The column-oriented approach typically requires more memory than row-based storage but provides superior query performance
- **Scaling:** The system efficiently handles the full Singapore HDB resale dataset (approximately 150,000 records)
- **Processing Time:** Typical analysis completes in under 5 seconds on modern hardware
- **Optimization:** For very large datasets, consider increasing JVM heap size with `-Xmx` parameter

## Town Mapping Reference

The third last digit of the matriculation number maps to towns as follows:

| Digit | Town           |
|-------|----------------|
| 0     | BEDOK          |
| 1     | BUKIT PANJANG  |
| 2     | CLEMENTI       |
| 3     | CHOA CHU KANG  |
| 4     | HOUGANG        |
| 5     | JURONG WEST    |
| 6     | PASIR RIS      |
| 7     | TAMPINES       |
| 8     | WOODLANDS      |
| 9     | YISHUN         |

## Troubleshooting

### Common Issues and Solutions

| Problem | Possible Cause | Solution |
|---------|----------------|----------|
| "File not found" error | Input CSV missing | Ensure `ResalePricesSingapore.csv` exists in the `data/` directory |
| "OutOfMemoryError" | Insufficient heap space | Increase JVM memory with `-Xmx2g` parameter |
| "No result" in output | No matching data | Verify town name and date range have transactions in dataset |
| Invalid town error | Incorrect matriculation format | Ensure matriculation number follows expected format |
| Build failure | Maven dependency issues | Run `mvn clean` and retry installation |

### Logging

The system logs detailed operation information to:
- Console output (basic information)
- `logs/application.log` (comprehensive diagnostic information)

Review these logs to diagnose any issues encountered during execution.

## Error Handling

The system includes comprehensive error handling for common scenarios:
- Invalid input file paths or formats
- Malformed data in the CSV file
- Invalid matriculation number format
- Empty result sets
- Date parsing errors

Error messages are logged with appropriate context to facilitate troubleshooting.

## Contributing

Contributions to improve the system are welcome. Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Implement your changes
4. Add appropriate tests
5. Commit your changes (`git commit -m 'Add your feature'`)
6. Push to the branch (`git push origin feature/your-feature`)
7. Create a Pull Request

Please maintain consistent code style and add appropriate documentation for new features.