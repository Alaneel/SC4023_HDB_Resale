package org.resale;

import lombok.Getter;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * EnhancedColumnStore.java
 *
 * An optimized column-oriented storage system for HDB resale flat data.
 * This implementation includes:
 * - Dictionary encoding for string columns
 * - Bitmap indexing for efficient filtering
 * - Chunked data processing for handling large datasets
 * - Caching mechanism for query results
 * - Parallel processing capabilities
 * - Memory-mapped file support for large datasets
 *
 * This version uses the unified benchmarking framework.
 */
public class EnhancedColumnStore {
    // Configuration parameters
    private static final int CHUNK_SIZE = 100_000; // Number of rows per chunk
    private static final int CACHE_SIZE = 20; // Number of query results to cache
    private static final int PARALLEL_THRESHOLD = 10_000; // Threshold for parallel processing

    // Column data structures
    private List<List<Integer>> monthChunks; // Dictionary-encoded months (YYYY-MM)
    private List<List<Integer>> townChunks;  // Dictionary-encoded towns
    private List<List<Double>> floorAreaChunks; // Floor areas in square meters
    private List<List<Double>> resalePriceChunks; // Resale prices in SGD

    // Benchmarking instance
    private final BenchmarkReporter benchmark;

    // Dictionaries for encoding/decoding
    @Getter private final Map<String, Integer> monthDictionary = new HashMap<>();
    @Getter private final Map<Integer, String> reverseMonthDictionary = new HashMap<>();
    @Getter private final Map<String, Integer> townDictionary = new HashMap<>();
    @Getter private final Map<Integer, String> reverseTownDictionary = new HashMap<>();

    // Indexes for efficient querying
    private Map<String, BitSet> townBitmapIndex; // Bitmap index for towns
    private TreeMap<String, BitSet> monthRangeIndex; // Range index for months
    private TreeMap<Double, BitSet> floorAreaRangeIndex; // Range index for floor areas

    // Query result cache
    private final Map<String, QueryResult> queryCache = new ConcurrentHashMap<>();
    private final List<String> cacheKeys = new ArrayList<>();

    // Metadata
    private int totalRows = 0;
    private boolean isCompressed = false;
    private boolean usesMemoryMapping = false;

    /**
     * Constructor initializes data structures for column storage
     */
    public EnhancedColumnStore() {
        this(new BenchmarkReporter("EnhancedColumnStore"));
    }

    /**
     * Constructor with custom benchmark reporter
     */
    public EnhancedColumnStore(BenchmarkReporter benchmark) {
        this.benchmark = benchmark;
        benchmark.startTiming(BenchmarkReporter.Stage.INITIALIZATION);

        this.monthChunks = new ArrayList<>();
        this.townChunks = new ArrayList<>();
        this.floorAreaChunks = new ArrayList<>();
        this.resalePriceChunks = new ArrayList<>();
        this.townBitmapIndex = new HashMap<>();
        this.monthRangeIndex = new TreeMap<>();
        this.floorAreaRangeIndex = new TreeMap<>();

        benchmark.stopTiming(BenchmarkReporter.Stage.INITIALIZATION);
    }

    /**
     * Loads data from a CSV file into the column store with optimizations
     * for handling large datasets.
     *
     * @param filepath Path to the input CSV file
     * @param useMemoryMapping Whether to use memory-mapped files for large datasets
     * @throws IOException If there are issues reading the file
     */
    public void loadData(String filepath, boolean useMemoryMapping) throws IOException {
        benchmark.startTiming(BenchmarkReporter.Stage.DATA_LOADING);

        this.usesMemoryMapping = useMemoryMapping;
        benchmark.recordMetric("Memory Mapping", useMemoryMapping ? "Enabled" : "Disabled");

        if (useMemoryMapping) {
            benchmark.startTiming("Memory-Mapped Loading");
            loadWithMemoryMapping(filepath);
            benchmark.stopTiming("Memory-Mapped Loading");
        } else {
            benchmark.startTiming("Buffered Loading");
            loadWithBufferedReader(filepath);
            benchmark.stopTiming("Buffered Loading");
        }

        // Build indexes after loading data
        benchmark.startTiming("Index Building");
        buildIndexes();
        benchmark.stopTiming("Index Building");

        // Record data loading metrics
        benchmark.recordMetric("Total Rows", totalRows);
        benchmark.recordMetric("Unique Towns", townDictionary.size());
        benchmark.recordMetric("Unique Months", monthDictionary.size());
        benchmark.recordMetric("Chunks Created", monthChunks.size());
        benchmark.recordMetric("Avg Rows Per Chunk", totalRows / Math.max(1, monthChunks.size()));

        long loadingTime = benchmark.stopTiming(BenchmarkReporter.Stage.DATA_LOADING);
        System.out.println("Data loading completed in " + loadingTime + " ms");
    }

    /**
     * Loads data using standard BufferedReader approach
     */
    private void loadWithBufferedReader(String filepath) throws IOException {
        // Initialize current chunk lists
        List<Integer> monthChunk = new ArrayList<>(CHUNK_SIZE);
        List<Integer> townChunk = new ArrayList<>(CHUNK_SIZE);
        List<Double> floorAreaChunk = new ArrayList<>(CHUNK_SIZE);
        List<Double> resalePriceChunk = new ArrayList<>(CHUNK_SIZE);

        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line = br.readLine(); // Skip header row
            System.out.println("Header: " + line);

            int rowCount = 0;
            int batchSize = 100000;

            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");

                // Dictionary encode month and town
                String month = values[0].trim();
                String town = values[1].trim();

                // Encode months (add to dictionary if not present)
                int monthCode = monthDictionary.computeIfAbsent(month, k -> {
                    int code = monthDictionary.size();
                    reverseMonthDictionary.put(code, k);
                    return code;
                });

                // Encode towns (add to dictionary if not present)
                int townCode = townDictionary.computeIfAbsent(town, k -> {
                    int code = townDictionary.size();
                    reverseTownDictionary.put(code, k);
                    return code;
                });

                // Add to current chunk
                monthChunk.add(monthCode);
                townChunk.add(townCode);
                floorAreaChunk.add(Double.parseDouble(values[6].trim()));
                resalePriceChunk.add(Double.parseDouble(values[9].trim()));

                rowCount++;

                // If chunk is full, add to chunks list and create new chunk
                if (rowCount % CHUNK_SIZE == 0) {
                    monthChunks.add(monthChunk);
                    townChunks.add(townChunk);
                    floorAreaChunks.add(floorAreaChunk);
                    resalePriceChunks.add(resalePriceChunk);

                    // Create new chunks
                    monthChunk = new ArrayList<>(CHUNK_SIZE);
                    townChunk = new ArrayList<>(CHUNK_SIZE);
                    floorAreaChunk = new ArrayList<>(CHUNK_SIZE);
                    resalePriceChunk = new ArrayList<>(CHUNK_SIZE);
                }

                // Print progress for large files
                if (rowCount % batchSize == 0) {
                    benchmark.recordMetric("Rows Processed", rowCount);
                }
            }

            // Add the last partial chunk if it's not empty
            if (!monthChunk.isEmpty()) {
                monthChunks.add(monthChunk);
                townChunks.add(townChunk);
                floorAreaChunks.add(floorAreaChunk);
                resalePriceChunks.add(resalePriceChunk);
            }

            totalRows = rowCount;
        }
    }

    /**
     * Loads data using memory-mapped files for very large datasets
     */
    private void loadWithMemoryMapping(String filepath) throws IOException {
        File file = new File(filepath);
        long fileSize = file.length();
        benchmark.recordMetric("File Size", fileSize + " bytes");

        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             FileChannel channel = raf.getChannel()) {

            // Map the entire file
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);

            // Initialize current chunk lists
            List<Integer> monthChunk = new ArrayList<>(CHUNK_SIZE);
            List<Integer> townChunk = new ArrayList<>(CHUNK_SIZE);
            List<Double> floorAreaChunk = new ArrayList<>(CHUNK_SIZE);
            List<Double> resalePriceChunk = new ArrayList<>(CHUNK_SIZE);

            // Read the file line by line
            StringBuilder line = new StringBuilder();
            boolean isFirstLine = true;
            int rowCount = 0;
            int batchSize = 100000;

            while (buffer.hasRemaining()) {
                char c = (char) buffer.get();

                if (c == '\n') {
                    // Skip header
                    if (isFirstLine) {
                        isFirstLine = false;
                        System.out.println("Header: " + line.toString());
                        line.setLength(0);
                        continue;
                    }

                    String[] values = line.toString().split(",");

                    // Dictionary encode month and town
                    String month = values[0].trim();
                    String town = values[1].trim();

                    // Encode months (add to dictionary if not present)
                    int monthCode = monthDictionary.computeIfAbsent(month, k -> {
                        int code = monthDictionary.size();
                        reverseMonthDictionary.put(code, k);
                        return code;
                    });

                    // Encode towns (add to dictionary if not present)
                    int townCode = townDictionary.computeIfAbsent(town, k -> {
                        int code = townDictionary.size();
                        reverseTownDictionary.put(code, k);
                        return code;
                    });

                    // Add to current chunk
                    monthChunk.add(monthCode);
                    townChunk.add(townCode);
                    floorAreaChunk.add(Double.parseDouble(values[6].trim()));
                    resalePriceChunk.add(Double.parseDouble(values[9].trim()));

                    rowCount++;

                    // If chunk is full, add to chunks list and create new chunk
                    if (rowCount % CHUNK_SIZE == 0) {
                        monthChunks.add(monthChunk);
                        townChunks.add(townChunk);
                        floorAreaChunks.add(floorAreaChunk);
                        resalePriceChunks.add(resalePriceChunk);

                        // Create new chunks
                        monthChunk = new ArrayList<>(CHUNK_SIZE);
                        townChunk = new ArrayList<>(CHUNK_SIZE);
                        floorAreaChunk = new ArrayList<>(CHUNK_SIZE);
                        resalePriceChunk = new ArrayList<>(CHUNK_SIZE);
                    }

                    // Print progress for large files
                    if (rowCount % batchSize == 0) {
                        benchmark.recordMetric("Rows Processed", rowCount);
                    }

                    line.setLength(0);
                } else {
                    line.append(c);
                }
            }

            // Add the last partial chunk if it's not empty
            if (!monthChunk.isEmpty()) {
                monthChunks.add(monthChunk);
                townChunks.add(townChunk);
                floorAreaChunks.add(floorAreaChunk);
                resalePriceChunks.add(resalePriceChunk);
            }

            totalRows = rowCount;
        }
    }

    /**
     * Builds bitmap and range indexes for efficient querying
     */
    private void buildIndexes() {
        // Track index building time
        benchmark.startTiming(BenchmarkReporter.Stage.INDEX_BUILDING);

        // Build town bitmap index
        benchmark.startTiming("Town Bitmap Index");
        for (String town : townDictionary.keySet()) {
            BitSet bitSet = new BitSet(totalRows);
            int townCode = townDictionary.get(town);

            int globalRowIndex = 0;
            for (int chunkIndex = 0; chunkIndex < townChunks.size(); chunkIndex++) {
                List<Integer> chunk = townChunks.get(chunkIndex);
                for (int i = 0; i < chunk.size(); i++) {
                    if (chunk.get(i) == townCode) {
                        bitSet.set(globalRowIndex);
                    }
                    globalRowIndex++;
                }
            }

            townBitmapIndex.put(town, bitSet);
        }
        benchmark.stopTiming("Town Bitmap Index");
        benchmark.recordMetric("Town Bitmap Index Size", townBitmapIndex.size());

        // Build month range index
        benchmark.startTiming("Month Range Index");
        Set<String> uniqueMonths = new HashSet<>(monthDictionary.keySet());
        for (String month : uniqueMonths) {
            BitSet bitSet = new BitSet(totalRows);
            int monthCode = monthDictionary.get(month);

            int globalRowIndex = 0;
            for (int chunkIndex = 0; chunkIndex < monthChunks.size(); chunkIndex++) {
                List<Integer> chunk = monthChunks.get(chunkIndex);
                for (int i = 0; i < chunk.size(); i++) {
                    if (chunk.get(i) == monthCode) {
                        bitSet.set(globalRowIndex);
                    }
                    globalRowIndex++;
                }
            }

            monthRangeIndex.put(month, bitSet);
        }
        benchmark.stopTiming("Month Range Index");
        benchmark.recordMetric("Month Range Index Size", monthRangeIndex.size());

        // Build floor area range index
        benchmark.startTiming("Floor Area Range Index");
        double[] thresholds = {60.0, 70.0, 80.0, 90.0, 100.0, 120.0, 150.0};

        for (double threshold : thresholds) {
            BitSet bitSet = new BitSet(totalRows);

            int globalRowIndex = 0;
            for (int chunkIndex = 0; chunkIndex < floorAreaChunks.size(); chunkIndex++) {
                List<Double> chunk = floorAreaChunks.get(chunkIndex);
                for (int i = 0; i < chunk.size(); i++) {
                    if (chunk.get(i) >= threshold) {
                        bitSet.set(globalRowIndex);
                    }
                    globalRowIndex++;
                }
            }

            floorAreaRangeIndex.put(threshold, bitSet);
        }
        benchmark.stopTiming("Floor Area Range Index");
        benchmark.recordMetric("Floor Area Range Index Size", floorAreaRangeIndex.size());

        // Record overall index building time
        long indexTime = benchmark.stopTiming(BenchmarkReporter.Stage.INDEX_BUILDING);
        System.out.println("Index building completed in " + indexTime + " ms");
    }

    /**
     * Compresses the data to reduce memory footprint
     */
    public void compressData() {
        if (isCompressed) {
            return; // Already compressed
        }

        benchmark.startTiming("Data Compression");
        try {
            // Compress each chunk for each column
            for (int i = 0; i < monthChunks.size(); i++) {
                monthChunks.set(i, compressIntegerList(monthChunks.get(i)));
                townChunks.set(i, compressIntegerList(townChunks.get(i)));
                // Note: We don't compress numeric columns as the overhead might outweigh benefits
            }

            isCompressed = true;
            benchmark.recordMetric("Data Compression", "Enabled");
        } catch (IOException e) {
            System.err.println("Error compressing data: " + e.getMessage());
            benchmark.recordMetric("Data Compression Error", e.getMessage());
        }
        benchmark.stopTiming("Data Compression");
    }

    /**
     * Helper method to compress a list of integers
     */
    private List<Integer> compressIntegerList(List<Integer> list) throws IOException {
        // Dictionary encoding is already applied, so we'll just return the original list
        // In a real implementation, we might use run-length encoding or other techniques
        return list;
    }

    /**
     * Finds indices of records matching the given criteria using bitmap indexes
     * for improved performance.
     *
     * @param targetTown Town to search for
     * @param startMonth Start month in YYYY-MM format
     * @param endMonth End month in YYYY-MM format
     * @param minArea Minimum floor area in square meters
     * @return List of indices of matching records
     */
    public List<Integer> findMatchingIndices(String targetTown, String startMonth, String endMonth, double minArea) {
        benchmark.startTiming(BenchmarkReporter.Stage.QUERY_EXECUTION);

        // Create a query key for caching
        String queryKey = targetTown + "|" + startMonth + "|" + endMonth + "|" + minArea;
        benchmark.recordMetric("Query Parameters", queryKey);

        // Log query parameters
        System.out.println("Query parameters:");
        System.out.println("  Target town: " + targetTown);
        System.out.println("  Date range: " + startMonth + " to " + endMonth);
        System.out.println("  Minimum area: " + minArea + " sqm");

        // Check cache first
        benchmark.startTiming("Cache Lookup");
        boolean cacheHit = queryCache.containsKey(queryKey);
        if (cacheHit) {
            // Update cache access order
            cacheKeys.remove(queryKey);
            cacheKeys.add(0, queryKey);
            benchmark.recordMetric("Cache Hit", "Yes");
        } else {
            benchmark.recordMetric("Cache Hit", "No");
        }
        benchmark.stopTiming("Cache Lookup");

        // Use bitmap indexes for efficient filtering
        benchmark.startTiming("Bitmap Filtering");
        BitSet resultBitSet = new BitSet(totalRows);

        // Start with town filter (usually most selective)
        benchmark.startTiming("Town Filter");
        if (townBitmapIndex.containsKey(targetTown)) {
            resultBitSet = (BitSet) townBitmapIndex.get(targetTown).clone();
            benchmark.recordMetric("Town Matches", resultBitSet.cardinality());
        } else {
            // Town not found, return empty result
            benchmark.recordMetric("Town Not Found", targetTown);
            benchmark.stopTiming("Town Filter");
            benchmark.stopTiming("Bitmap Filtering");
            benchmark.stopTiming(BenchmarkReporter.Stage.QUERY_EXECUTION);
            return Collections.emptyList();
        }
        benchmark.stopTiming("Town Filter");

        // Parse date range for comparison
        YearMonth startYM = YearMonth.parse(startMonth);
        YearMonth endYM = YearMonth.parse(endMonth);

        // Apply date range filter
        benchmark.startTiming("Date Range Filter");
        BitSet dateRangeBitSet = new BitSet(totalRows);
        dateRangeBitSet.set(0, totalRows); // Start with all bits set

        // Get all months in the range
        for (String month : monthRangeIndex.keySet()) {
            YearMonth current = YearMonth.parse(month);
            if (current.isBefore(startYM) || current.isAfter(endYM)) {
                // Remove months outside the range
                BitSet monthBitSet = monthRangeIndex.get(month);
                BitSet invertedMonthBitSet = (BitSet) monthBitSet.clone();
                invertedMonthBitSet.flip(0, totalRows);
                dateRangeBitSet.and(invertedMonthBitSet);
            }
        }

        // Combine town and date filters
        int beforeDateFilter = resultBitSet.cardinality();
        resultBitSet.and(dateRangeBitSet);
        int afterDateFilter = resultBitSet.cardinality();
        benchmark.recordMetric("Date Range Matches", afterDateFilter);
        benchmark.stopTiming("Date Range Filter");

        // Apply floor area filter
        benchmark.startTiming("Floor Area Filter");
        BitSet floorAreaBitSet;
        // Find the closest threshold less than or equal to the requested minArea
        Map.Entry<Double, BitSet> floorAreaEntry = floorAreaRangeIndex.floorEntry(minArea);

        if (floorAreaEntry != null) {
            floorAreaBitSet = floorAreaEntry.getValue();
            resultBitSet.and(floorAreaBitSet);
        } else {
            // No threshold found, need to scan all floor areas
            int globalRowIndex = 0;
            for (int chunkIndex = 0; chunkIndex < floorAreaChunks.size(); chunkIndex++) {
                List<Double> chunk = floorAreaChunks.get(chunkIndex);
                for (int i = 0; i < chunk.size(); i++) {
                    if (chunk.get(i) < minArea) {
                        resultBitSet.clear(globalRowIndex);
                    }
                    globalRowIndex++;
                }
            }
        }
        int afterAreaFilter = resultBitSet.cardinality();
        benchmark.recordMetric("Area Filter Matches", afterAreaFilter);
        benchmark.stopTiming("Floor Area Filter");

        benchmark.stopTiming("Bitmap Filtering");

        // Convert BitSet to a list of indices
        benchmark.startTiming("Collect Results");
        List<Integer> matchingIndices = new ArrayList<>();
        for (int i = resultBitSet.nextSetBit(0); i >= 0; i = resultBitSet.nextSetBit(i + 1)) {
            matchingIndices.add(i);
        }
        benchmark.stopTiming("Collect Results");

        // Calculate selectivity
        benchmark.recordMetric("Town Filter Selectivity",
                String.format("%.2f%%", (beforeDateFilter * 100.0 / totalRows)));
        benchmark.recordMetric("Date Range Filter Selectivity",
                String.format("%.2f%%", (afterDateFilter * 100.0 / beforeDateFilter)));
        benchmark.recordMetric("Area Filter Selectivity",
                String.format("%.2f%%", (afterAreaFilter * 100.0 / afterDateFilter)));
        benchmark.recordMetric("Final Result Count", matchingIndices.size());

        long queryTime = benchmark.stopTiming(BenchmarkReporter.Stage.QUERY_EXECUTION);
        System.out.println("Query execution completed in " + queryTime + " ms");
        System.out.println("Found " + matchingIndices.size() + " matching records");

        return matchingIndices;
    }

    /**
     * Calculates statistics for the given indices with parallel processing
     * for improved performance on large datasets.
     *
     * @param indices List of indices to calculate statistics for
     * @param type Type of statistic to calculate
     * @return QueryResult containing the calculated statistic
     */
    public QueryResult calculateStatistics(List<Integer> indices, StatisticType type) {
        String opName = "Calculate " + type.getDisplayName();
        benchmark.startTiming(opName);
        benchmark.startTiming(BenchmarkReporter.Stage.STATISTICS_CALCULATION);

        // Create a query key for caching
        String queryKey = type.name() + "|" + indices.hashCode();

        // Check cache first
        benchmark.startTiming("Statistics Cache Lookup");
        boolean cacheHit = queryCache.containsKey(queryKey);
        QueryResult result;

        if (cacheHit) {
            // Update cache access order
            cacheKeys.remove(queryKey);
            cacheKeys.add(0, queryKey);
            result = queryCache.get(queryKey);
            benchmark.recordMetric("Statistics Cache Hit", "Yes");
            benchmark.stopTiming("Statistics Cache Lookup");
            benchmark.stopTiming(BenchmarkReporter.Stage.STATISTICS_CALCULATION);
            benchmark.stopTiming(opName);
            return result;
        }
        benchmark.recordMetric("Statistics Cache Hit", "No");
        benchmark.stopTiming("Statistics Cache Lookup");

        // Return "No result" if no matching records found
        if (indices.isEmpty()) {
            result = new QueryResult("No result");
            cacheResult(queryKey, result);
            benchmark.stopTiming(BenchmarkReporter.Stage.STATISTICS_CALCULATION);
            benchmark.stopTiming(opName);
            return result;
        }

        // Decide whether to use parallel processing based on data size
        boolean useParallel = indices.size() > PARALLEL_THRESHOLD;
        benchmark.recordMetric("Parallel Processing", useParallel ? "Enabled" : "Disabled");
        benchmark.recordMetric("Records Processed", indices.size());

        // Convert global indices to chunk-local indices for efficient access
        benchmark.startTiming("Convert Indices");
        Map<Integer, List<Integer>> chunkIndices = convertToChunkIndices(indices);
        benchmark.stopTiming("Convert Indices");

        double value;
        benchmark.startTiming("Calculation");

        switch (type) {
            case MINIMUM_PRICE:
                // Calculate minimum resale price
                value = useParallel
                        ? calculateMinPriceParallel(chunkIndices)
                        : calculateMinPriceSequential(chunkIndices);
                break;

            case AVERAGE_PRICE:
                // Calculate average resale price
                value = useParallel
                        ? calculateAvgPriceParallel(chunkIndices)
                        : calculateAvgPriceSequential(chunkIndices);
                break;

            case STANDARD_DEVIATION:
                // Calculate standard deviation
                // First pass: calculate mean
                double mean = useParallel
                        ? calculateAvgPriceParallel(chunkIndices)
                        : calculateAvgPriceSequential(chunkIndices);

                // Second pass: calculate variance
                value = useParallel
                        ? calculateStdDevParallel(chunkIndices, mean)
                        : calculateStdDevSequential(chunkIndices, mean);
                break;

            case MIN_PRICE_PER_SQM:
                // Calculate minimum price per square meter
                value = useParallel
                        ? calculateMinPricePerSqmParallel(chunkIndices)
                        : calculateMinPricePerSqmSequential(chunkIndices);
                break;

            default:
                throw new IllegalArgumentException("Unknown statistic type");
        }
        benchmark.stopTiming("Calculation");

        // Format result to 2 decimal places
        result = new QueryResult(String.format("%.2f", value));

        // Cache the result
        benchmark.startTiming("Cache Result");
        cacheResult(queryKey, result);
        benchmark.stopTiming("Cache Result");

        benchmark.recordMetric(type.getDisplayName(), result.getValue());

        long calcTime = benchmark.stopTiming(BenchmarkReporter.Stage.STATISTICS_CALCULATION);
        benchmark.stopTiming(opName);

        System.out.println(type.getDisplayName() + " calculated in " + calcTime + " ms: " + result.getValue());

        return result;
    }

    /**
     * Converts global indices to chunk-local indices for efficient access
     */
    private Map<Integer, List<Integer>> convertToChunkIndices(List<Integer> globalIndices) {
        Map<Integer, List<Integer>> chunkIndices = new HashMap<>();

        for (int globalIndex : globalIndices) {
            int chunkIndex = globalIndex / CHUNK_SIZE;
            int localIndex = globalIndex % CHUNK_SIZE;

            chunkIndices.computeIfAbsent(chunkIndex, k -> new ArrayList<>()).add(localIndex);
        }

        return chunkIndices;
    }

    // Sequential implementation of statistical calculations

    private double calculateMinPriceSequential(Map<Integer, List<Integer>> chunkIndices) {
        double minPrice = Double.MAX_VALUE;

        for (Map.Entry<Integer, List<Integer>> entry : chunkIndices.entrySet()) {
            int chunkIndex = entry.getKey();
            List<Integer> localIndices = entry.getValue();
            List<Double> priceChunk = resalePriceChunks.get(chunkIndex);

            for (int localIndex : localIndices) {
                minPrice = Math.min(minPrice, priceChunk.get(localIndex));
            }
        }

        return minPrice;
    }

    private double calculateAvgPriceSequential(Map<Integer, List<Integer>> chunkIndices) {
        double sum = 0;
        int count = 0;

        for (Map.Entry<Integer, List<Integer>> entry : chunkIndices.entrySet()) {
            int chunkIndex = entry.getKey();
            List<Integer> localIndices = entry.getValue();
            List<Double> priceChunk = resalePriceChunks.get(chunkIndex);

            for (int localIndex : localIndices) {
                sum += priceChunk.get(localIndex);
                count++;
            }
        }

        return count > 0 ? sum / count : 0;
    }

    private double calculateStdDevSequential(Map<Integer, List<Integer>> chunkIndices, double mean) {
        double sumSquaredDiff = 0;
        int count = 0;

        for (Map.Entry<Integer, List<Integer>> entry : chunkIndices.entrySet()) {
            int chunkIndex = entry.getKey();
            List<Integer> localIndices = entry.getValue();
            List<Double> priceChunk = resalePriceChunks.get(chunkIndex);

            for (int localIndex : localIndices) {
                double price = priceChunk.get(localIndex);
                double diff = price - mean;
                sumSquaredDiff += diff * diff;
                count++;
            }
        }

        return count > 0 ? Math.sqrt(sumSquaredDiff / count) : 0;
    }

    private double calculateMinPricePerSqmSequential(Map<Integer, List<Integer>> chunkIndices) {
        double minPricePerSqm = Double.MAX_VALUE;

        for (Map.Entry<Integer, List<Integer>> entry : chunkIndices.entrySet()) {
            int chunkIndex = entry.getKey();
            List<Integer> localIndices = entry.getValue();
            List<Double> priceChunk = resalePriceChunks.get(chunkIndex);
            List<Double> areaChunk = floorAreaChunks.get(chunkIndex);

            for (int localIndex : localIndices) {
                double price = priceChunk.get(localIndex);
                double area = areaChunk.get(localIndex);
                double pricePerSqm = price / area;
                minPricePerSqm = Math.min(minPricePerSqm, pricePerSqm);
            }
        }

        return minPricePerSqm;
    }

    // Parallel implementation of statistical calculations

    private double calculateMinPriceParallel(Map<Integer, List<Integer>> chunkIndices) {
        return chunkIndices.entrySet().parallelStream()
                .map(entry -> {
                    int chunkIndex = entry.getKey();
                    List<Integer> localIndices = entry.getValue();
                    List<Double> priceChunk = resalePriceChunks.get(chunkIndex);

                    return localIndices.stream()
                            .mapToDouble(priceChunk::get)
                            .min()
                            .orElse(Double.MAX_VALUE);
                })
                .reduce(Double.MAX_VALUE, Math::min);
    }

    private double calculateAvgPriceParallel(Map<Integer, List<Integer>> chunkIndices) {
        // Count the total number of indices
        int totalCount = chunkIndices.values().stream().mapToInt(List::size).sum();

        double sum = chunkIndices.entrySet().parallelStream()
                .mapToDouble(entry -> {
                    int chunkIndex = entry.getKey();
                    List<Integer> localIndices = entry.getValue();
                    List<Double> priceChunk = resalePriceChunks.get(chunkIndex);

                    return localIndices.stream()
                            .mapToDouble(priceChunk::get)
                            .sum();
                })
                .sum();

        return totalCount > 0 ? sum / totalCount : 0;
    }

    private double calculateStdDevParallel(Map<Integer, List<Integer>> chunkIndices, double mean) {
        // Count the total number of indices
        int totalCount = chunkIndices.values().stream().mapToInt(List::size).sum();

        double sumSquaredDiff = chunkIndices.entrySet().parallelStream()
                .mapToDouble(entry -> {
                    int chunkIndex = entry.getKey();
                    List<Integer> localIndices = entry.getValue();
                    List<Double> priceChunk = resalePriceChunks.get(chunkIndex);

                    return localIndices.stream()
                            .mapToDouble(localIndex -> {
                                double diff = priceChunk.get(localIndex) - mean;
                                return diff * diff;
                            })
                            .sum();
                })
                .sum();

        return totalCount > 0 ? Math.sqrt(sumSquaredDiff / totalCount) : 0;
    }

    private double calculateMinPricePerSqmParallel(Map<Integer, List<Integer>> chunkIndices) {
        return chunkIndices.entrySet().parallelStream()
                .map(entry -> {
                    int chunkIndex = entry.getKey();
                    List<Integer> localIndices = entry.getValue();
                    List<Double> priceChunk = resalePriceChunks.get(chunkIndex);
                    List<Double> areaChunk = floorAreaChunks.get(chunkIndex);

                    return localIndices.stream()
                            .mapToDouble(localIndex -> {
                                double price = priceChunk.get(localIndex);
                                double area = areaChunk.get(localIndex);
                                return price / area;
                            })
                            .min()
                            .orElse(Double.MAX_VALUE);
                })
                .reduce(Double.MAX_VALUE, Math::min);
    }

    /**
     * Caches a query result and manages the cache size
     */
    private void cacheResult(String queryKey, QueryResult result) {
        // Add to cache
        queryCache.put(queryKey, result);
        cacheKeys.add(queryKey);

        // Manage cache size (LRU eviction)
        if (cacheKeys.size() > CACHE_SIZE) {
            String oldestKey = cacheKeys.remove(cacheKeys.size() - 1);
            queryCache.remove(oldestKey);
        }
    }

    /**
     * Returns the total number of rows in the dataset
     */
    public int getTotalRows() {
        return totalRows;
    }

    /**
     * Returns whether the data is compressed
     */
    public boolean isCompressed() {
        return isCompressed;
    }

    /**
     * Returns whether memory mapping is used
     */
    public boolean usesMemoryMapping() {
        return usesMemoryMapping;
    }

    /**
     * Gets the benchmark reporter instance
     */
    public BenchmarkReporter getBenchmark() {
        return benchmark;
    }
}