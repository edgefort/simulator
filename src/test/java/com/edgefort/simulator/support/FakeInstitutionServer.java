package com.edgefort.simulator.support;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import com.edgefort.simulator.provider.nibss.nip.security.SsmCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * An institution's inward NIP web service: decrypts the simulator's request with the institution private key and
 * answers with the institution's response message, encrypted with the simulator public key. The answer element is
 * named {@code {method}Result} to prove the simulator accepts any element name.
 */
public final class FakeInstitutionServer implements AutoCloseable {

    public static final String PATH = "/NIPWS/BankInwardService";

    private final HttpServer server;
    private final PgpSecretKeys institutionKeys;
    private volatile PGPPublicKeyRing simulatorKey;
    private volatile String responseCode = "00";
    private volatile Received lastRequest;

    public FakeInstitutionServer(PgpSecretKeys institutionKeys) {
        this.institutionKeys = institutionKeys;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext(PATH, this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
    }

    public void simulatorKey(PGPPublicKeyRing simulatorKey) {
        this.simulatorKey = simulatorKey;
    }

    public void respondWith(String responseCode) {
        this.responseCode = responseCode;
    }

    public Received lastRequest() {
        return lastRequest;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Element envelope = NipSoapMessages.parse(body).getDocumentElement();
            Element soapBody = NipSoapMessages.children(envelope, NipSoapMessages.ENVELOPE_NAMESPACE, "Body")
                    .getFirst();
            Element operation = firstElement(soapBody);
            Element request = NipSoapMessages.children(operation, null, "request").getFirst();
            String method = operation.getLocalName();
            String message = SsmCodec.decrypt(request.getTextContent(), institutionKeys);
            lastRequest = new Received(
                    method,
                    operation.getNamespaceURI(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("SOAPAction"),
                    message
            );

            String answer = answer(message);
            String encrypted = SsmCodec.encrypt(answer, PgpKeys.encryptionKey(simulatorKey));
            byte[] response = """
                    <S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/"><S:Body>\
                    <ns2:%1$sResponse xmlns:ns2="http://core.nip.nibss/"><%1$sResult>%2$s</%1$sResult>\
                    </ns2:%1$sResponse></S:Body></S:Envelope>""".formatted(method, encrypted)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }

    private String answer(String message) {
        for (String root : List.of("NESingleRequest", "FTSingleCreditRequest")) {
            if (message.contains("<" + root + ">")) {
                String responseRoot = root.replace("Request", "Response");
                String extra = root.equals("NESingleRequest")
                        ? "<AccountName>INSTITUTION CUSTOMER</AccountName><BankVerificationNumber>22222222222"
                        + "</BankVerificationNumber><KYCLevel>1</KYCLevel>"
                        : "";
                return message
                        .replace("<" + root + ">", "<" + responseRoot + ">")
                        .replace("</" + root + ">", extra + "<ResponseCode>" + responseCode + "</ResponseCode></"
                                + responseRoot + ">");
            }
        }
        throw new IllegalStateException("Unexpected message " + message);
    }

    private static Element firstElement(Element parent) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                return element;
            }
        }
        throw new IllegalStateException("Empty SOAP body");
    }

    /**
     * @param message decrypted NIP request message
     */
    public record Received(String method, String namespace, String contentType, String soapAction, String message) {
    }
}
