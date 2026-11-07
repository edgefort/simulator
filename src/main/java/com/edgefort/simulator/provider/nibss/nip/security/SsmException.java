package com.edgefort.simulator.provider.nibss.nip.security;

/**
 * An SSM payload or OpenPGP key could not be processed. Messages never contain key material or plaintext.
 */
public class SsmException extends RuntimeException {

    public SsmException(String message) {
        super(message);
    }

    public SsmException(String message, Throwable cause) {
        super(message, cause);
    }
}
