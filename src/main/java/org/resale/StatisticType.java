package org.resale;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * StatisticType.java
 *
 * Enum representing different types of statistics that can be calculated
 * for HDB resale transactions. Each type includes a display name for output.
 */
@AllArgsConstructor
@Getter
enum StatisticType {
    MINIMUM_PRICE("Minimum Price"),
    AVERAGE_PRICE("Average Price"),
    STANDARD_DEVIATION("Standard Deviation of Price"),
    MIN_PRICE_PER_SQM("Minimum Price per Square Meter");

    private final String displayName;
}