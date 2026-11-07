package com.edgefort.simulator.provider.onafriq.billpayment.api;

/**
 * Rejected Onafriq credentials, carrying the documented error code and message.
 */
class OnafriqAuthenticationException extends RuntimeException {

    private final String code;

    OnafriqAuthenticationException(String code, String message) {
        super(message, null, false, false);
        this.code = code;
    }

    String code() {
        return code;
    }
}
