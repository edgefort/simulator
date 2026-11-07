package com.edgefort.simulator;

import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import com.edgefort.simulator.support.NipSoapMessages;
import com.edgefort.simulator.support.NoResponseAssertions;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.edgefort.simulator.support.NipSoapMessages.decryptReturn;
import static com.edgefort.simulator.support.NipSoapMessages.encryptedEnvelope;
import static com.edgefort.simulator.support.NipSoapMessages.envelope;
import static com.edgefort.simulator.support.NipSoapMessages.faultCode;
import static com.edgefort.simulator.support.NipSoapMessages.xpath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "simulator.admin-token=soap-admin",
                "simulator.profiles.timeout.delay=50ms"
        }
)
@AutoConfigureMockMvc
class NibssNipSoapIntegrationTest {

    private static final String ADMIN_TOKEN = "soap-admin";
    private static final PgpSecretKeys INSTITUTION_KEYS = PgpSecretKeys.generate("Test Institution");

    private static final String NIP_INTERFACE = "/NIPWS/NIPInterface";
    private static final String NIP_TSQ_INTERFACE = "/NIPWS/NIPTSQInterface";
    private static final String NIP_NE_INTERFACE = "/NIPWS/NIPNEInterface";

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    private PGPPublicKeyRing simulatorKey;

    @BeforeEach
    void exchangeKeys() throws Exception {
        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(PgpKeys.binary(INSTITUTION_KEYS.publicKeyRing())))
                .andExpect(status().isNoContent());

        byte[] download = mockMvc.perform(get("/admin/providers/nibss-nip/keys/simulator-public")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pgp-keys"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nibss_public.key\""))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(download[0] & 0x80).as("binary OpenPGP packet, not ASCII armor").isNotZero();
        simulatorKey = PgpKeys.parsePublicKeyRing(download);
    }

    @AfterEach
    void resetSimulator() throws Exception {
        mockMvc.perform(post("/admin/state/reset").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void answersNameEnquiryOnEveryServicePathWithAnEncryptedReturn() throws Exception {
        for (String path : List.of(NIP_INTERFACE, NIP_NE_INTERFACE, NIP_TSQ_INTERFACE)) {
            String response = soap(path, "nameenquirysingleitem",
                    nameEnquiryRequest("soap-ne-" + path.substring(path.lastIndexOf('/') + 1)));

            String message = decryptReturn(response, "nameenquirysingleitem", INSTITUTION_KEYS);
            assertThat(xpath(message, "/NESingleResponse/SessionID"))
                    .isEqualTo("soap-ne-" + path.substring(path.lastIndexOf('/') + 1));
            assertThat(xpath(message, "/NESingleResponse/AccountName")).isEqualTo("SIMULATED ACCOUNT");
            assertThat(xpath(message, "/NESingleResponse/ResponseCode")).isEqualTo("00");
        }
    }

    @Test
    void creditTransferIsRecordedAndReportedByTheStatusQueryService() throws Exception {
        String credit = decryptReturn(
                soap(NIP_INTERFACE, "fundtransfersingleitem_dc", creditRequest("soap-credit-001")),
                "fundtransfersingleitem_dc", INSTITUTION_KEYS
        );
        assertThat(xpath(credit, "/FTSingleCreditResponse/SessionID")).isEqualTo("soap-credit-001");
        assertThat(xpath(credit, "/FTSingleCreditResponse/Amount")).isEqualTo("1000.00");
        assertThat(xpath(credit, "/FTSingleCreditResponse/ResponseCode")).isEqualTo("00");

        assertThat(statusQuery("soap-credit-001")).isEqualTo("00");
        assertThat(statusQuery("soap-never-sent-001")).isEqualTo("25");
    }

    @Test
    void repeatedSessionIdIsADuplicateTransaction() throws Exception {
        soap(NIP_INTERFACE, "fundtransfersingleitem_dc", creditRequest("soap-duplicate-001"));

        String duplicate = decryptReturn(
                soap(NIP_INTERFACE, "fundtransfersingleitem_dc", creditRequest("soap-duplicate-001")),
                "fundtransfersingleitem_dc", INSTITUTION_KEYS
        );
        assertThat(xpath(duplicate, "/FTSingleCreditResponse/ResponseCode")).isEqualTo("94");
    }

    @Test
    void failureProfileSelectedThroughTheAdminApiIsReported() throws Exception {
        mockMvc.perform(put("/admin/providers/nibss-nip/operations/fund-transfer-direct-credit/profile")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":\"failure\"}"))
                .andExpect(status().isNoContent());

        String credit = decryptReturn(
                soap(NIP_INTERFACE, "fundtransfersingleitem_dc", creditRequest("soap-failure-001")),
                "fundtransfersingleitem_dc", INSTITUTION_KEYS
        );
        assertThat(xpath(credit, "/FTSingleCreditResponse/ResponseCode")).isEqualTo("96");
        assertThat(statusQuery("soap-failure-001")).isEqualTo("96");
    }

    @Test
    void invalidInnerMessagesAreAnsweredWithAnEncryptedFormatError() throws Exception {
        String missingElements = decryptReturn(
                soap(NIP_INTERFACE, "nameenquirysingleitem", "<NESingleRequest/>"),
                "nameenquirysingleitem", INSTITUTION_KEYS
        );
        assertThat(xpath(missingElements, "/NESingleResponse/ResponseCode")).isEqualTo("30");

        String invalidCredit = decryptReturn(
                soap(NIP_INTERFACE, "fundtransfersingleitem_dc", creditRequest("soap-invalid-001")
                        .replace("<BeneficiaryAccountName>Ajibade Oluwasegun</BeneficiaryAccountName>", "")),
                "fundtransfersingleitem_dc", INSTITUTION_KEYS
        );
        assertThat(xpath(invalidCredit, "/FTSingleCreditResponse/SessionID")).isEqualTo("soap-invalid-001");
        assertThat(xpath(invalidCredit, "/FTSingleCreditResponse/ResponseCode")).isEqualTo("30");
        assertThat(statusQuery("soap-invalid-001")).isEqualTo("25");

        String malformed = decryptReturn(
                soap(NIP_INTERFACE, "nameenquirysingleitem", "<NESingleRequest><SessionID>broken"),
                "nameenquirysingleitem", INSTITUTION_KEYS
        );
        assertThat(xpath(malformed, "/NESingleResponse/ResponseCode")).isEqualTo("30");

        String externalEntity = decryptReturn(soap(NIP_INTERFACE, "nameenquirysingleitem", """
                <?xml version="1.0"?>
                <!DOCTYPE request [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <NESingleRequest><SessionID>&xxe;</SessionID></NESingleRequest>
                """), "nameenquirysingleitem", INSTITUTION_KEYS);
        assertThat(externalEntity).doesNotContain("root:");
        assertThat(xpath(externalEntity, "/NESingleResponse/ResponseCode")).isEqualTo("30");
    }

    @Test
    void undispatchableOrUndecryptableRequestsAreSoapFaults() throws Exception {
        assertThat(faultCode(fault(NIP_INTERFACE, envelope("nameenquirysingleitem", "ZZZZ;"))))
                .isEqualTo("S:Client");
        assertThat(faultCode(fault(NIP_INTERFACE, envelope("nameenquirysingleitem", "0A0B0C;"))))
                .isEqualTo("S:Client");
        assertThat(faultCode(fault(NIP_INTERFACE,
                encryptedEnvelope("unknownmethod", nameEnquiryRequest("soap-fault-001"), simulatorKey))))
                .isEqualTo("S:Client");
        assertThat(faultCode(fault(NIP_INTERFACE, envelope("nameenquirysingleitem", "")
                .replace("<request></request>", ""))))
                .isEqualTo("S:Client");
        assertThat(faultCode(fault(NIP_INTERFACE, "<soapenv:Envelope")))
                .isEqualTo("S:Client");
        assertThat(faultCode(fault(NIP_INTERFACE, """
                <?xml version="1.0"?>
                <!DOCTYPE soapenv:Envelope [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"><soapenv:Body/></soapenv:Envelope>
                """)))
                .isEqualTo("S:Client");
        assertThat(faultCode(fault(NIP_INTERFACE, envelope("nameenquirysingleitem", "00;")
                .replace("http://schemas.xmlsoap.org/soap/envelope/", "http://www.w3.org/2003/05/soap-envelope"))))
                .isEqualTo("S:VersionMismatch");
    }

    @Test
    void timeoutClosesTheSoapConnectionWithoutAResponse() throws Exception {
        mockMvc.perform(post("/admin/overrides")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"nibss-nip","reference":"soap-timeout-001","profile":"timeout"}
                                """))
                .andExpect(status().isCreated());

        NoResponseAssertions.assertNoResponse(
                "POST",
                URI.create("http://localhost:" + port + NIP_INTERFACE),
                Map.of("Content-Type", "text/xml; charset=utf-8", "SOAPAction", "\"\""),
                encryptedEnvelope("fundtransfersingleitem_dc", creditRequest("soap-timeout-001"), simulatorKey)
        );

        assertThat(statusQuery("soap-timeout-001")).isEqualTo("00");
    }

    @Test
    void publishesAWsdlForEveryServicePath() throws Exception {
        for (String path : List.of(NIP_INTERFACE, NIP_TSQ_INTERFACE, NIP_NE_INTERFACE)) {
            String wsdl = mockMvc.perform(get(path + "?wsdl"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            Document document = NipSoapMessages.parse(wsdl);
            String wsdlNamespace = "http://schemas.xmlsoap.org/wsdl/";
            String schemaNamespace = "http://www.w3.org/2001/XMLSchema";
            Element definitions = document.getDocumentElement();
            assertThat(definitions.getAttribute("targetNamespace")).isEqualTo("http://core.nip.nibss/");

            Element portType = (Element) document.getElementsByTagNameNS(wsdlNamespace, "portType").item(0);
            List<String> operations = new ArrayList<>();
            NodeList operationNodes = portType.getElementsByTagNameNS(wsdlNamespace, "operation");
            for (int index = 0; index < operationNodes.getLength(); index++) {
                operations.add(((Element) operationNodes.item(index)).getAttribute("name"));
            }
            assertThat(operations).containsExactlyInAnyOrder(
                    "nameenquirysingleitem", "fundtransfersingleitem_dc", "fundtransfersingleitem_dd",
                    "txnstatusquerysingleitem", "balanceenquiry", "fundtransferAdvice_dc", "fundtransferAdvice_dd"
            );

            Element schema = (Element) document.getElementsByTagNameNS(schemaNamespace, "schema").item(0);
            assertThat(schema.getAttribute("elementFormDefault")).isEqualTo("unqualified");
            assertThat(wsdl)
                    .contains("<xs:element name=\"request\" type=\"xs:string\" minOccurs=\"0\"/>")
                    .contains("<xs:element name=\"return\" type=\"xs:string\" minOccurs=\"0\"/>")
                    .contains("<part name=\"parameters\" element=\"tns:txnstatusquerysingleitemResponse\"/>");
            assertThat(document.getElementsByTagNameNS(wsdlNamespace, "message").getLength()).isEqualTo(14);

            Element address = (Element) document
                    .getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "address").item(0);
            assertThat(address.getAttribute("location")).isEqualTo("http://localhost" + path);
        }
    }

    @Test
    void keyAdministrationExposesNoKeyMaterialAndRejectsUnusableKeys() throws Exception {
        String fingerprint = PgpKeys.describe(INSTITUTION_KEYS.publicKeyRing()).fingerprint();
        mockMvc.perform(get("/admin/providers/nibss-nip/keys").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulator.fingerprint").value(PgpKeys.describe(simulatorKey).fingerprint()))
                .andExpect(jsonPath("$.simulator.userIds[0]").value("NIBSS NIP Simulator"))
                .andExpect(jsonPath("$.client.fingerprint").value(fingerprint))
                .andExpect(content().string(not(containsString("BEGIN PGP"))));

        mockMvc.perform(get("/admin/providers/nibss-nip/keys/simulator-public")
                        .param("format", "armored")
                        .header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("-----BEGIN PGP PUBLIC KEY BLOCK-----")));

        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(PgpKeys.armored(INSTITUTION_KEYS.publicKeyRing())))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(PgpKeys.binary(INSTITUTION_KEYS.publicKeyRing())))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(PgpKeys.armored(INSTITUTION_KEYS.publicKeyRing())))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/admin/providers/nibss-nip/keys/client-public")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not a key"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_KEY"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/admin/providers/nibss-nip/keys"))
                .andExpect(status().isUnauthorized());
    }

    private String soap(String path, String method, String nipMessage) throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post(path)
                        .contentType("text/xml;charset=UTF-8")
                        .header("SOAPAction", "\"\"")
                        .content(encryptedEnvelope(method, nipMessage, simulatorKey)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andReturn().getResponse();
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private String fault(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType("text/xml;charset=UTF-8").content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String statusQuery(String sessionId) throws Exception {
        String response = decryptReturn(
                soap(NIP_TSQ_INTERFACE, "txnstatusquerysingleitem", """
                        <TSQuerySingleRequest>
                          <SourceInstitutionCode>000002</SourceInstitutionCode>
                          <ChannelCode>1</ChannelCode>
                          <SessionID>%s</SessionID>
                        </TSQuerySingleRequest>
                        """.formatted(sessionId)),
                "txnstatusquerysingleitem", INSTITUTION_KEYS
        );
        assertThat(xpath(response, "/TSQuerySingleResponse/SessionID")).isEqualTo(sessionId);
        return xpath(response, "/TSQuerySingleResponse/ResponseCode");
    }

    private static String nameEnquiryRequest(String sessionId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <NESingleRequest>
                  <SessionID>%s</SessionID>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <AccountNumber>2222000000012345</AccountNumber>
                </NESingleRequest>
                """.formatted(sessionId);
    }

    private static String creditRequest(String sessionId) {
        return """
                <FTSingleCreditRequest>
                  <SessionID>%s</SessionID>
                  <NameEnquiryRef>000001100913103301000000000001</NameEnquiryRef>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <BeneficiaryAccountName>Ajibade Oluwasegun</BeneficiaryAccountName>
                  <BeneficiaryAccountNumber>2222002345</BeneficiaryAccountNumber>
                  <BeneficiaryBankVerificationNumber>1033000442</BeneficiaryBankVerificationNumber>
                  <BeneficiaryKYCLevel>1</BeneficiaryKYCLevel>
                  <OriginatorAccountName>Adewale Hassan</OriginatorAccountName>
                  <OriginatorAccountNumber>3333002345</OriginatorAccountNumber>
                  <OriginatorBankVerificationNumber>1033000441</OriginatorBankVerificationNumber>
                  <OriginatorKYCLevel>1</OriginatorKYCLevel>
                  <TransactionLocation>6.4300747,3.4110715</TransactionLocation>
                  <Narration>1000000001</Narration>
                  <PaymentReference>yyyyyyyyyyyyyy</PaymentReference>
                  <Amount>1000.00</Amount>
                </FTSingleCreditRequest>
                """.formatted(sessionId);
    }
}
