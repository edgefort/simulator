package com.edgefort.simulator.provider.nibss.nip.soap;

import static com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoap.REQUEST_ELEMENT;
import static com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoap.RESPONSE_ELEMENT;
import static com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoap.SERVICE_NAMESPACE;
import static com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoap.escape;

/**
 * Document/literal-wrapped WSDL 1.1 for one NIP service path. Every operation wraps a single unqualified
 * {@code request} string and answers with a single unqualified {@code return} string, the SSM-encrypted messages.
 * The SOAP action of every operation is empty because no other value was supplied.
 */
public final class NibssNipWsdl {

    private NibssNipWsdl() {
    }

    /**
     * @param serviceName service and port type name, for example {@code NIPInterface}
     * @param address     URL the client used to reach the service, including any base path
     */
    public static String describe(String serviceName, String address) {
        StringBuilder schema = new StringBuilder();
        StringBuilder messages = new StringBuilder();
        StringBuilder portType = new StringBuilder();
        StringBuilder binding = new StringBuilder();
        for (NibssNipSoapMethod method : NibssNipSoapMethod.values()) {
            String input = method.wireName();
            String output = method.responseWireName();
            schema.append("""
                          <xs:element name="%1$s" type="tns:%1$s"/>
                          <xs:element name="%2$s" type="tns:%2$s"/>
                          <xs:complexType name="%1$s">
                            <xs:sequence>
                              <xs:element name="%3$s" type="xs:string" minOccurs="0"/>
                            </xs:sequence>
                          </xs:complexType>
                          <xs:complexType name="%2$s">
                            <xs:sequence>
                              <xs:element name="%4$s" type="xs:string" minOccurs="0"/>
                            </xs:sequence>
                          </xs:complexType>
                    """.formatted(input, output, REQUEST_ELEMENT, RESPONSE_ELEMENT));
            messages.append("""
                      <message name="%1$s">
                        <part name="parameters" element="tns:%1$s"/>
                      </message>
                      <message name="%2$s">
                        <part name="parameters" element="tns:%2$s"/>
                      </message>
                    """.formatted(input, output));
            portType.append("""
                        <operation name="%1$s">
                          <input message="tns:%1$s"/>
                          <output message="tns:%2$s"/>
                        </operation>
                    """.formatted(input, output));
            binding.append("""
                        <operation name="%s">
                          <soap:operation soapAction=""/>
                          <input>
                            <soap:body use="literal"/>
                          </input>
                          <output>
                            <soap:body use="literal"/>
                          </output>
                        </operation>
                    """.formatted(input));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
                             xmlns:xs="http://www.w3.org/2001/XMLSchema"
                             xmlns:tns="%1$s"
                             targetNamespace="%1$s"
                             name="%2$s">
                  <types>
                    <xs:schema targetNamespace="%1$s" elementFormDefault="unqualified" version="1.0">
                %3$s    </xs:schema>
                  </types>
                %4$s  <portType name="%2$s">
                %5$s  </portType>
                  <binding name="%2$sPortBinding" type="tns:%2$s">
                    <soap:binding transport="http://schemas.xmlsoap.org/soap/http" style="document"/>
                %6$s  </binding>
                  <service name="%2$s">
                    <port name="%2$sPort" binding="tns:%2$sPortBinding">
                      <soap:address location="%7$s"/>
                    </port>
                  </service>
                </definitions>
                """.formatted(
                SERVICE_NAMESPACE, serviceName, schema, messages, portType, binding, escape(address)
        );
    }
}
