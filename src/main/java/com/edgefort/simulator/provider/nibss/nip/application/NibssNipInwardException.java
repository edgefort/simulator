package com.edgefort.simulator.provider.nibss.nip.application;

/**
 * An inward call (the simulator playing NIBSS) could not be made or did not produce a usable institution response.
 * Nothing is recorded in either case.
 */
public class NibssNipInwardException extends RuntimeException {

    private final Reason reason;
    private final Exchange exchange;

    public NibssNipInwardException(Reason reason, String message, Exchange exchange) {
        super(message);
        this.reason = reason;
        this.exchange = exchange;
    }

    public Reason reason() {
        return reason;
    }

    /**
     * What was sent, when the failure happened during or after the call; {@code null} when no call was attempted.
     */
    public Exchange exchange() {
        return exchange;
    }

    public enum Reason {
        /** Inward calls need the institution public key and the inward URL. */
        NOT_CONFIGURED,
        /** The institution could not be reached or answered with an error, a fault, or an unusable message. */
        CALL_FAILED
    }

    /**
     * @param httpStatus institution HTTP status, or {@code null} when no response was received
     */
    public record Exchange(String sessionId, String method, String url, String requestXml, Integer httpStatus) {
    }
}
