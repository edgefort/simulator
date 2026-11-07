package com.edgefort.simulator.support;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import com.edgefort.simulator.provider.nibss.nip.security.SsmCodec;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds NIP SOAP requests exactly like the NIBSS Postman sample and reads SOAP responses, playing the institution
 * side of SSM with throwaway test keys.
 */
public final class NipSoapMessages {

    public static final String ENVELOPE_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";
    public static final String SERVICE_NAMESPACE = "http://core.nip.nibss/";

    private NipSoapMessages() {
    }

    /**
     * The NIBSS Postman sample envelope with {@code request} holding the given text verbatim.
     */
    public static String envelope(String method, String requestText) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:core="http://core.nip.nibss/">
                   <soapenv:Header/>
                   <soapenv:Body>
                      <core:%s>
                         <request>%s</request>
                      </core:%s>
                   </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(method, requestText, method);
    }

    /**
     * Envelope carrying the NIP message SSM-encrypted for the simulator.
     */
    public static String encryptedEnvelope(String method, String nipMessage, PGPPublicKeyRing simulatorKey) {
        return envelope(method, SsmCodec.encrypt(nipMessage, PgpKeys.encryptionKey(simulatorKey)));
    }

    /**
     * Reads the {@code return} element of a {@code {method}Response} and decrypts it with the institution keys.
     */
    public static String decryptReturn(String soapResponse, String method, PgpSecretKeys institutionKeys) {
        Document document = parse(soapResponse);
        Element envelope = document.getDocumentElement();
        assertThat(envelope.getNamespaceURI()).isEqualTo(ENVELOPE_NAMESPACE);
        assertThat(envelope.getLocalName()).isEqualTo("Envelope");
        Element body = single(children(envelope, ENVELOPE_NAMESPACE, "Body"));
        Element response = single(children(body, SERVICE_NAMESPACE, method + "Response"));
        Element payload = single(children(response, null, "return"));
        String encrypted = payload.getTextContent();
        assertThat(encrypted).matches("([0-9A-F]+;)+");
        return SsmCodec.decrypt(encrypted, institutionKeys);
    }

    /**
     * Fault code of a SOAP 1.1 fault response, for example {@code S:Client}.
     */
    public static String faultCode(String soapResponse) {
        Document document = parse(soapResponse);
        Element body = single(children(document.getDocumentElement(), ENVELOPE_NAMESPACE, "Body"));
        Element fault = single(children(body, ENVELOPE_NAMESPACE, "Fault"));
        assertThat(single(children(fault, null, "faultstring")).getTextContent()).isNotBlank();
        return single(children(fault, null, "faultcode")).getTextContent();
    }

    public static String xpath(String xml, String expression) {
        try {
            return (String) XPathFactory.newInstance().newXPath()
                    .evaluate(expression, parse(xml), XPathConstants.STRING);
        }
        catch (Exception exception) {
            throw new AssertionError("Cannot evaluate " + expression, exception);
        }
    }

    public static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new AssertionError("Not well-formed XML: " + xml, exception);
        }
    }

    public static List<Element> children(Element parent, String namespace, String localName) {
        List<Element> elements = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && localName.equals(element.getLocalName())
                    && (namespace == null ? element.getNamespaceURI() == null
                    : namespace.equals(element.getNamespaceURI()))) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static Element single(List<Element> elements) {
        assertThat(elements).hasSize(1);
        return elements.getFirst();
    }
}
