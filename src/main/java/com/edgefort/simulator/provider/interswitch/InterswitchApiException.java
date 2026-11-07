package com.edgefort.simulator.provider.interswitch;

import org.springframework.http.HttpStatus;

/**
 * A request rejected with a documented Interswitch response code. Rendered by the provider's error advice as
 * {@code ResponseCode}, {@code ResponseDescription}, and {@code ResponseCodeGrouping} {@code FAILED}.
 */
public class InterswitchApiException extends RuntimeException {

    private final HttpStatus status;
    private final String responseCode;

    public InterswitchApiException(HttpStatus status, String responseCode, String responseDescription) {
        super(responseDescription, null, false, false);
        this.status = status;
        this.responseCode = responseCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String responseCode() {
        return responseCode;
    }

    public String responseDescription() {
        return getMessage();
    }
}
