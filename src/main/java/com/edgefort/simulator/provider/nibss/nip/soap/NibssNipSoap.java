package com.edgefort.simulator.provider.nibss.nip.soap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SOAP 1.1 envelopes of the NIP web service.
 *
 * <p>Requests follow the NIBSS sample: the body holds {@code <core:{method}>} in namespace
 * {@value #SERVICE_NAMESPACE}, whose single unqualified child {@value #REQUEST_ELEMENT} carries the SSM payload.</p>
 *
 * <p><strong>Unverified:</strong> the response shape. It follows the JAX-WS document/literal-wrapped default,
 * {@code <ns2:{method}Response><return>payload</return></ns2:{method}Response>}; confirm the
 * {@link #RESPONSE_ELEMENT} name against NIBSS's WSDL ({@code http://{IP}/NIPWS/NIPInterface?wsdl}) or a captured
 * response.</p>
 *
 * <p>Parsing never resolves DTDs or external entities.</p>
 */
public final class NibssNipSoap {

    public static final String ENVELOPE_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String SERVICE_NAMESPACE = "http://core.nip.nibss/";
    public static final String REQUEST_ELEMENT = "request";
    /** Unverified response payload element; see the class description. */
    public static final String RESPONSE_ELEMENT = "return";
    public static final String CONTENT_TYPE = "text/xml; charset=utf-8";

    private NibssNipSoap() {
    }

    /**
     * Reads a NIP request envelope. Throws {@link NibssNipSoapFault} with a {@code Client} or
     * {@code VersionMismatch} fault code when the envelope cannot be dispatched.
     */
    public static Request parseRequest(byte[] body) {
        Element bodyElement = body(parse(body));
        Element operation = singleChild(bodyElement)
                .orElseThrow(() -> NibssNipSoapFault.client("SOAP Body must contain exactly one operation element"));
        if (!SERVICE_NAMESPACE.equals(operation.getNamespaceURI())) {
            throw NibssNipSoapFault.client("Cannot find dispatch method for {%s}%s"
                    .formatted(nullToEmpty(operation.getNamespaceURI()), operation.getLocalName()));
        }
        NibssNipSoapMethod method = NibssNipSoapMethod.fromWireName(operation.getLocalName())
                .orElseThrow(() -> NibssNipSoapFault.client("Cannot find dispatch method for {%s}%s"
                        .formatted(SERVICE_NAMESPACE, operation.getLocalName())));
        Element request = childElements(operation).stream()
                .filter(child -> child.getNamespaceURI() == null && REQUEST_ELEMENT.equals(child.getLocalName()))
                .findFirst()
                .orElseThrow(() -> NibssNipSoapFault.client(
                        "Operation %s has no %s element".formatted(method.wireName(), REQUEST_ELEMENT)
                ));
        String payload = request.getTextContent();
        if (payload == null || payload.isBlank()) {
            throw NibssNipSoapFault.client("Element %s is empty".formatted(REQUEST_ELEMENT));
        }
        return new Request(method, payload.strip());
    }

    /**
     * Response envelope carrying the encrypted response message.
     */
    public static String response(NibssNipSoapMethod method, String payload) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>\
                <S:Envelope xmlns:S="%s">\
                <S:Body>\
                <ns2:%s xmlns:ns2="%s">\
                <%s>%s</%s>\
                </ns2:%s>\
                </S:Body>\
                </S:Envelope>""".formatted(
                ENVELOPE_NAMESPACE,
                method.responseWireName(), SERVICE_NAMESPACE,
                RESPONSE_ELEMENT, escape(payload), RESPONSE_ELEMENT,
                method.responseWireName()
        );
    }

    public static String fault(NibssNipSoapFault fault) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>\
                <S:Envelope xmlns:S="%s">\
                <S:Body>\
                <S:Fault>\
                <faultcode>S:%s</faultcode>\
                <faultstring>%s</faultstring>\
                </S:Fault>\
                </S:Body>\
                </S:Envelope>""".formatted(ENVELOPE_NAMESPACE, fault.code().wireName(), escape(fault.getMessage()));
    }

    /**
     * Request envelope sent to an institution's inward web service, shaped like the NIBSS sample.
     */
    public static String request(String namespace, String method, String requestElement, String payload) {
        return """
                <soapenv:Envelope xmlns:soapenv="%s" xmlns:core="%s">\
                <soapenv:Header/>\
                <soapenv:Body>\
                <core:%s>\
                <%s>%s</%s>\
                </core:%s>\
                </soapenv:Body>\
                </soapenv:Envelope>""".formatted(
                ENVELOPE_NAMESPACE, escape(namespace),
                method,
                requestElement, escape(payload), requestElement,
                method
        );
    }

    /**
     * Reads the payload of an operation response: the text of the first child element of the body's operation
     * element, whatever its name, because institution WSDLs name it differently ({@code return},
     * {@code {method}Result}, ...). A SOAP fault is thrown as {@link NibssNipSoapFault}.
     */
    public static String parseResponsePayload(byte[] body) {
        Element bodyElement = body(parse(body));
        Element operation = singleChild(bodyElement)
                .orElseThrow(() -> NibssNipSoapFault.client("SOAP Body must contain exactly one element"));
        if (ENVELOPE_NAMESPACE.equals(operation.getNamespaceURI()) && "Fault".equals(operation.getLocalName())) {
            String code = childText(operation, "faultcode").orElse("");
            String message = childText(operation, "faultstring").orElse("");
            throw new NibssNipSoapFault(NibssNipSoapFault.Code.SERVER, "SOAP fault %s: %s".formatted(code, message));
        }
        List<Element> children = childElements(operation);
        if (children.isEmpty()) {
            throw NibssNipSoapFault.client("Operation response %s has no payload element"
                    .formatted(operation.getLocalName()));
        }
        String payload = children.getFirst().getTextContent();
        if (payload == null || payload.isBlank()) {
            throw NibssNipSoapFault.client("Operation response payload is empty");
        }
        return payload.strip();
    }

    private static Document parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw NibssNipSoapFault.client("Request body is empty");
        }
        try {
            return documentBuilder().parse(new ByteArrayInputStream(body));
        }
        catch (SAXException | IOException exception) {
            throw NibssNipSoapFault.client("Malformed SOAP message");
        }
    }

    private static Element body(Document document) {
        Element envelope = document.getDocumentElement();
        if (!"Envelope".equals(envelope.getLocalName())) {
            throw NibssNipSoapFault.client("Root element must be a SOAP Envelope");
        }
        if (!ENVELOPE_NAMESPACE.equals(envelope.getNamespaceURI())) {
            throw new NibssNipSoapFault(NibssNipSoapFault.Code.VERSION_MISMATCH,
                    "Expected a SOAP 1.1 envelope in namespace " + ENVELOPE_NAMESPACE);
        }
        return childElements(envelope).stream()
                .filter(child -> ENVELOPE_NAMESPACE.equals(child.getNamespaceURI())
                        && "Body".equals(child.getLocalName()))
                .findFirst()
                .orElseThrow(() -> NibssNipSoapFault.client("SOAP Envelope has no Body"));
    }

    private static Optional<Element> singleChild(Element parent) {
        List<Element> children = childElements(parent);
        return children.size() == 1 ? Optional.of(children.getFirst()) : Optional.empty();
    }

    private static Optional<String> childText(Element parent, String localName) {
        return childElements(parent).stream()
                .filter(child -> localName.equals(child.getLocalName()))
                .map(Element::getTextContent)
                .findFirst();
    }

    private static List<Element> childElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static DocumentBuilder documentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder;
        }
        catch (ParserConfigurationException exception) {
            throw new IllegalStateException("Secure XML parsing is not supported by this runtime", exception);
        }
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * A dispatchable NIP request: the method and its SSM-encrypted payload.
     */
    public record Request(NibssNipSoapMethod method, String payload) {
    }
}
