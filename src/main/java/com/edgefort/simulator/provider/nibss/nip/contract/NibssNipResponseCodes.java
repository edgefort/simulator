package com.edgefort.simulator.provider.nibss.nip.contract;

/**
 * NIP response codes returned by this module. See NIBSS_NIP.md for the source of each code.
 */
public final class NibssNipResponseCodes {

    public static final String APPROVED = "00";
    public static final String REQUEST_PROCESSING_IN_PROGRESS = "09";
    public static final String UNABLE_TO_LOCATE_RECORD = "25";
    public static final String FORMAT_ERROR = "30";
    public static final String DUPLICATE_TRANSACTION = "94";
    public static final String SYSTEM_MALFUNCTION = "96";

    private NibssNipResponseCodes() {
    }
}
