package com.edgefort.simulator.provider.nibss.nip.soap;

/**
 * A SOAP 1.1 fault. The NIP specification does not define faults; the simulator answers requests it cannot
 * dispatch, decrypt, or encrypt for with a standard SOAP 1.1 fault (HTTP 500).
 */
public class NibssNipSoapFault extends RuntimeException {

    private final Code code;

    public NibssNipSoapFault(Code code, String message) {
        super(message);
        this.code = code;
    }

    public static NibssNipSoapFault client(String message) {
        return new NibssNipSoapFault(Code.CLIENT, message);
    }

    public static NibssNipSoapFault server(String message) {
        return new NibssNipSoapFault(Code.SERVER, message);
    }

    public Code code() {
        return code;
    }

    public enum Code {
        VERSION_MISMATCH("VersionMismatch"),
        CLIENT("Client"),
        SERVER("Server");

        private final String wireName;

        Code(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }
}
