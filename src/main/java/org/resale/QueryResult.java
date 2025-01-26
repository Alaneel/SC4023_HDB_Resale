package org.resale;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * QueryResult.java
 *
 * Class representing the result of a statistical query.
 * Encapsulates the result value and provides methods to access it.
 */
@AllArgsConstructor
@Getter
class QueryResult {
    private final String value;
}