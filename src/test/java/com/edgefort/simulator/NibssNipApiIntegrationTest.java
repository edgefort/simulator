package com.edgefort.simulator;

import com.edgefort.simulator.support.NoResponseAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "simulator.admin-token=local-admin",
                "simulator.profiles.pending.transition-delay=300ms",
                "simulator.profiles.pending-then-failed.transition-delay=300ms",
                "simulator.profiles.timeout.delay=50ms",
                "simulator.profiles.timeout-then-failed.delay=50ms",
                "simulator.profiles.timeout-not-received.delay=50ms"
        }
)
@AutoConfigureMockMvc
class NibssNipApiIntegrationTest {

    private static final String BASE_PATH = "/nip/v9.4";
    private static final String ADMIN_TOKEN = "local-admin";

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @AfterEach
    void resetSimulator() throws Exception {
        mockMvc.perform(post("/admin/state/reset").header("X-Admin-Token", ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    void implementsTheDocumentedNameEnquiryXmlContract() throws Exception {
        nip("/nameenquirysingle", nameEnquiryRequest("000001100913103301000000000001"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/NESingleResponse/SessionID").string("000001100913103301000000000001"))
                .andExpect(xpath("/NESingleResponse/DestinationInstitutionCode").string("000002"))
                .andExpect(xpath("/NESingleResponse/ChannelCode").string("1"))
                .andExpect(xpath("/NESingleResponse/AccountNumber").string("2222000000012345"))
                .andExpect(xpath("/NESingleResponse/AccountName").string("SIMULATED ACCOUNT"))
                .andExpect(xpath("/NESingleResponse/BankVerificationNumber").string("00000000000"))
                .andExpect(xpath("/NESingleResponse/KYCLevel").string("1"))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("00"));
    }

    @Test
    void nameEnquiryFailureIsSelectedBySessionIdOverride() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","reference":"ne-failure-001","profile":"failure"}
                """).andExpect(status().isCreated());

        nip("/nameenquirysingle", nameEnquiryRequest("ne-failure-001"))
                .andExpect(status().isOk())
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("96"));

        nip("/nameenquirysingle", nameEnquiryRequest("ne-success-001"))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("00"));
    }

    @Test
    void storesDirectCreditBySessionIdAndSupportsTheDocumentedStatusQuery() throws Exception {
        nip("/fundtransfersingleitem_dc", creditRequest("000001100913103301000000000002"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/SessionID").string("000001100913103301000000000002"))
                .andExpect(xpath("/FTSingleCreditResponse/Amount").string("1000.00"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));

        nip("/txnstatusquerysingleitem", statusQueryRequest("000001100913103301000000000002"))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/SourceInstitutionCode").string("000002"))
                .andExpect(xpath("/TSQuerySingleResponse/ChannelCode").string("1"))
                .andExpect(xpath("/TSQuerySingleResponse/SessionID").string("000001100913103301000000000002"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("00"));
    }

    @Test
    void reportsFailureFromTheOperationProfileAndInTheStatusQuery() throws Exception {
        mockMvc.perform(put("/admin/providers/nibss-nip/operations/fund-transfer-direct-credit/profile")
                        .header("X-Admin-Token", ADMIN_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profile":"failure"}
                                """))
                .andExpect(status().isNoContent());

        nip("/fundtransfersingleitem_dc", creditRequest("credit-failure-001"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("96"));
        nip("/txnstatusquerysingleitem", statusQueryRequest("credit-failure-001"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("96"));

        nip("/fundtransfersingleitem_dd", debitRequest("debit-unaffected-001"))
                .andExpect(xpath("/FTSingleDebitResponse/ResponseCode").string("00"));
    }

    @Test
    void returnsDuplicateTransactionForARepeatedSessionId() throws Exception {
        String request = creditRequest("000001100913103301000000000005");
        nip("/fundtransfersingleitem_dc", request)
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));
        nip("/fundtransfersingleitem_dc", request)
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/SessionID").string("000001100913103301000000000005"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("94"));
    }

    @Test
    void pendingTransferIsProcessingAndCompletesAsSuccess() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","reference":"pending-success-001","profile":"pending"}
                """).andExpect(status().isCreated());

        nip("/fundtransfersingleitem_dc", creditRequest("pending-success-001"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("09"));
        nip("/txnstatusquerysingleitem", statusQueryRequest("pending-success-001"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("09"));

        awaitStatus("pending-success-001", "00");
    }

    @Test
    void pendingDirectDebitCompletesAsFailed() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","operation":"fund-transfer-direct-debit","profile":"pending-then-failed"}
                """).andExpect(status().isCreated());

        nip("/fundtransfersingleitem_dd", debitRequest("pending-failed-001"))
                .andExpect(xpath("/FTSingleDebitResponse/ResponseCode").string("09"));

        awaitStatus("pending-failed-001", "96");
    }

    @Test
    void rejectsProfilesTheNipContractCannotExpress() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","operation":"fund-transfer-direct-credit","profile":"pending-then-reversed"}
                """).andExpect(status().isBadRequest());

        admin("/admin/overrides", """
                {"provider":"nibss-nip","operation":"name-enquiry","profile":"pending"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void selectsBehaviorByRuleOnASpecificationField() throws Exception {
        admin("/admin/rules", """
                {"provider":"nibss-nip","operation":"fund-transfer-direct-credit",
                 "field":"BeneficiaryAccountNumber","equals":"9999000001","profile":"failure"}
                """).andExpect(status().isCreated());
        admin("/admin/rules", """
                {"provider":"nibss-nip","operation":"name-enquiry",
                 "field":"AccountNumber","equals":"9999000002","profile":"failure"}
                """).andExpect(status().isCreated());

        nip("/fundtransfersingleitem_dc", creditRequest("rule-match-001", "9999000001"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("96"));
        nip("/fundtransfersingleitem_dc", creditRequest("rule-miss-001", "2222002345"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));
        nip("/nameenquirysingle", nameEnquiryRequest("rule-ne-001").replace("2222000000012345", "9999000002"))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("96"));

        admin("/admin/rules", """
                {"provider":"nibss-nip","operation":"name-enquiry","field":"X-Simulator","equals":"1","profile":"failure"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void timeoutClosesTheConnectionAndTheStatusQueryReportsTheRecordedTransfer() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","reference":"timeout-success-001","profile":"timeout"}
                """).andExpect(status().isCreated());
        assertNoResponse("/fundtransfersingleitem_dc", creditRequest("timeout-success-001"));
        awaitStatus("timeout-success-001", "00");

        admin("/admin/overrides", """
                {"provider":"nibss-nip","reference":"timeout-failed-001","profile":"timeout-then-failed"}
                """).andExpect(status().isCreated());
        assertNoResponse("/fundtransfersingleitem_dd", debitRequest("timeout-failed-001"));
        awaitStatus("timeout-failed-001", "96");
    }

    @Test
    void timeoutNotReceivedLeavesNoRecordToLocate() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","reference":"timeout-lost-001","profile":"timeout-not-received"}
                """).andExpect(status().isCreated());
        assertNoResponse("/fundtransfersingleitem_dc", creditRequest("timeout-lost-001"));

        nip("/txnstatusquerysingleitem", statusQueryRequest("timeout-lost-001"))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("25"));
    }

    @Test
    void timeoutOnAnEnquiryClosesTheConnection() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","operation":"name-enquiry","profile":"timeout","count":1}
                """).andExpect(status().isCreated());
        assertNoResponse("/nameenquirysingle", nameEnquiryRequest("timeout-ne-001"));

        nip("/nameenquirysingle", nameEnquiryRequest("timeout-ne-002"))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("00"));
    }

    @Test
    void statusQueryForAnUnknownSessionIsUnableToLocateRecord() throws Exception {
        nip("/txnstatusquerysingleitem", statusQueryRequest("never-sent-001"))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/SessionID").string("never-sent-001"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("25"));
    }

    @Test
    void implementsDirectDebitBalanceAndBothAdviceMessagePairs() throws Exception {
        nip("/fundtransfersingleitem_dd", debitRequest("debit-session-001"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleDebitResponse/MandateReferenceNumber").string("mandate-001"))
                .andExpect(xpath("/FTSingleDebitResponse/TransactionFee").string("0.00"))
                .andExpect(xpath("/FTSingleDebitResponse/ResponseCode").string("00"));

        nip("/balanceenquiry", balanceRequest("balance-session-001"))
                .andExpect(status().isOk())
                .andExpect(xpath("/BalanceEnquiryResponse/AvailableBalance").string("1000.00"))
                .andExpect(xpath("/BalanceEnquiryResponse/ResponseCode").string("00"));

        nip("/fundtransferAdvice_dc", creditRequest("credit-advice-001")
                .replace("FTSingleCreditRequest", "FTAdviceCreditRequest"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTAdviceCreditResponse/SessionID").string("credit-advice-001"))
                .andExpect(xpath("/FTAdviceCreditResponse/ResponseCode").string("00"));

        nip("/fundtransferAdvice_dd", debitRequest("debit-advice-001")
                .replace("FTSingleDebitRequest", "FTAdviceDebitRequest"))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTAdviceDebitResponse/SessionID").string("debit-advice-001"))
                .andExpect(xpath("/FTAdviceDebitResponse/ResponseCode").string("00"));
    }

    @Test
    void balanceAndAdviceFailuresUseTheNipFailureCode() throws Exception {
        admin("/admin/overrides", """
                {"provider":"nibss-nip","reference":"balance-failure-001","profile":"failure"}
                """).andExpect(status().isCreated());
        admin("/admin/overrides", """
                {"provider":"nibss-nip","operation":"fund-transfer-advice-direct-debit","profile":"failure"}
                """).andExpect(status().isCreated());

        nip("/balanceenquiry", balanceRequest("balance-failure-001"))
                .andExpect(xpath("/BalanceEnquiryResponse/ResponseCode").string("96"));
        nip("/fundtransferAdvice_dd", debitRequest("debit-advice-failure-001")
                .replace("FTSingleDebitRequest", "FTAdviceDebitRequest"))
                .andExpect(xpath("/FTAdviceDebitResponse/ResponseCode").string("96"));
    }

    @Test
    void missingRequiredElementsAreAnsweredWithTheNipFormatErrorResponse() throws Exception {
        nip("/nameenquirysingle", "<NESingleRequest/>")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("30"));

        nip("/fundtransfersingleitem_dc", creditRequest("invalid-credit-001")
                .replace("<BeneficiaryAccountName>Ajibade Oluwasegun</BeneficiaryAccountName>", ""))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/SessionID").string("invalid-credit-001"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("30"));

        nip("/txnstatusquerysingleitem", statusQueryRequest("invalid-credit-001"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("25"));
    }

    @Test
    void malformedXmlAndNonXmlMediaTypesAreAnsweredInNipForm() throws Exception {
        nip("/nameenquirysingle", "<NESingleRequest><SessionID>broken")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("30"));

        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"SessionID\":\"json-credit-001\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("30"));

        mockMvc.perform(post(BASE_PATH + "/balanceenquiry").content(balanceRequest("no-content-type-001")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("/BalanceEnquiryResponse/ResponseCode").string("30"));

        nip("/txnstatusquerysingleitem", statusQueryRequest("json-credit-001"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("25"));
    }

    @Test
    void rejectsXmlDocumentsContainingExternalEntities() throws Exception {
        nip("/nameenquirysingle", """
                <?xml version="1.0"?>
                <!DOCTYPE request [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <NESingleRequest>
                  <SessionID>&xxe;</SessionID>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <AccountNumber>2222000000012345</AccountNumber>
                </NESingleRequest>
                """)
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("root:"))))
                .andExpect(xpath("/NESingleResponse/ResponseCode").string("30"));
    }

    @Test
    void removedProvisionalJsonEndpointIsNotMapped() throws Exception {
        mockMvc.perform(post("/simulators/nibss/nip/name-enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private ResultActions nip(String path, String xml) throws Exception {
        return mockMvc.perform(post(BASE_PATH + path)
                .contentType(MediaType.APPLICATION_XML)
                .content(xml));
    }

    private ResultActions admin(String path, String json) throws Exception {
        return mockMvc.perform(post(path)
                .header("X-Admin-Token", ADMIN_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void assertNoResponse(String path, String xml) {
        NoResponseAssertions.assertNoResponse(
                "POST",
                URI.create("http://localhost:" + port + BASE_PATH + path),
                Map.of("Content-Type", MediaType.APPLICATION_XML_VALUE),
                xml
        );
    }

    private void awaitStatus(String sessionId, String responseCode) {
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                nip("/txnstatusquerysingleitem", statusQueryRequest(sessionId))
                        .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string(responseCode))
        );
    }

    private String nameEnquiryRequest(String sessionId) {
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

    private String creditRequest(String sessionId) {
        return creditRequest(sessionId, "2222002345");
    }

    private String creditRequest(String sessionId, String beneficiaryAccountNumber) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <FTSingleCreditRequest>
                  <SessionID>%s</SessionID>
                  <NameEnquiryRef>000001100913103301000000000001</NameEnquiryRef>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <BeneficiaryAccountName>Ajibade Oluwasegun</BeneficiaryAccountName>
                  <BeneficiaryAccountNumber>%s</BeneficiaryAccountNumber>
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
                """.formatted(sessionId, beneficiaryAccountNumber);
    }

    private String statusQueryRequest(String sessionId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <TSQuerySingleRequest>
                  <SourceInstitutionCode>000002</SourceInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <SessionID>%s</SessionID>
                </TSQuerySingleRequest>
                """.formatted(sessionId);
    }

    private String debitRequest(String sessionId) {
        return """
                <FTSingleDebitRequest>
                  <SessionID>%s</SessionID>
                  <NameEnquiryRef>name-enquiry-001</NameEnquiryRef>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <DebitAccountName>Ajibade Oluwasegun</DebitAccountName>
                  <DebitAccountNumber>2222000000012345</DebitAccountNumber>
                  <DebitBankVerificationNumber>1033000442</DebitBankVerificationNumber>
                  <DebitKYCLevel>1</DebitKYCLevel>
                  <BeneficiaryAccountName>Sarah Hassan Emeka</BeneficiaryAccountName>
                  <BeneficiaryAccountNumber>2222002345</BeneficiaryAccountNumber>
                  <BeneficiaryBankVerificationNumber>1033000442</BeneficiaryBankVerificationNumber>
                  <BeneficiaryKYCLevel>1</BeneficiaryKYCLevel>
                  <TransactionLocation>6.4300747,3.4110715</TransactionLocation>
                  <Narration>Transfer from 000002</Narration>
                  <PaymentReference>1000000001</PaymentReference>
                  <MandateReferenceNumber>mandate-001</MandateReferenceNumber>
                  <TransactionFee>0.00</TransactionFee>
                  <Amount>1000.00</Amount>
                </FTSingleDebitRequest>
                """.formatted(sessionId);
    }

    private String balanceRequest(String sessionId) {
        return """
                <BalanceEnquiryRequest>
                  <SessionID>%s</SessionID>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>7</ChannelCode>
                  <AuthorizationCode>authorization-001</AuthorizationCode>
                  <TargetAccountName>Ajibade Oluwasegun</TargetAccountName>
                  <TargetBankVerificationNumber>1033000442</TargetBankVerificationNumber>
                  <TargetAccountNumber>2222002345</TargetAccountNumber>
                </BalanceEnquiryRequest>
                """.formatted(sessionId);
    }
}
