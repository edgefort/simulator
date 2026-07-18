package com.edgefort.simulator.core.application;

import java.math.BigDecimal;
import java.net.URI;

public record TransferCommand(
        String provider,
        String operation,
        String reference,
        BigDecimal amount,
        URI callbackUrl
) {
}