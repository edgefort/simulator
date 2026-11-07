package com.edgefort.simulator.core.application;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param reference  provider-unique transaction reference supplied by the client
 * @param attributes request values named as in the provider specification; used by test-data rules and stored with
 *                   the transaction for later queries
 */
public record TransactionCommand(
        String provider,
        String operation,
        String reference,
        BigDecimal amount,
        Map<String, String> attributes
) {

    public TransactionCommand {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
