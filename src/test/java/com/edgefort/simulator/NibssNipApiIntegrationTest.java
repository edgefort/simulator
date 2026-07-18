package com.edgefort.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

@SpringBootTest(properties = "simulator.profiles.timeout.delay=1ms")
@AutoConfigureMockMvc
class NibssNipApiIntegrationTest {

    private static final String BASE_PATH = "/nip/v9.4";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void implementsTheDocumentedNameEnquiryXmlContract() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(nameEnquiryRequest()))
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
    void storesDirectCreditBySessionIdAndSupportsTheDocumentedStatusQuery() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(creditRequest("000001100913103301000000000002")))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/SessionID")
                        .string("000001100913103301000000000002"))
                .andExpect(xpath("/FTSingleCreditResponse/Amount").string("1000.00"))
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("00"));

        mockMvc.perform(post(BASE_PATH + "/txnstatusquerysingleitem")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(statusQueryRequest("000001100913103301000000000002")))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/SourceInstitutionCode").string("000002"))
                .andExpect(xpath("/TSQuerySingleResponse/SessionID")
                        .string("000001100913103301000000000002"))
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("00"));
    }

    @Test
    void usesNipResponseCodesForFailureTimeoutAndDuplicateScenarios() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .header("X-Simulation-Scenario", "failure")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(creditRequest("000001100913103301000000000003")))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("96"));

        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .header("X-Simulation-Scenario", "timeout")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(creditRequest("000001100913103301000000000004")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("97"));

        String duplicateRequest = creditRequest("000001100913103301000000000005");
        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(duplicateRequest))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(duplicateRequest))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("94"));
    }

    @Test
    void rejectsMalformedXmlAndTheRemovedProvisionalJsonEndpoint() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<NESingleRequest><SessionID>broken"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/simulators/nibss/nip/name-enquiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void implementsDirectDebitBalanceAndBothAdviceMessagePairs() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dd")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(debitRequest("debit-session-001")))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleDebitResponse/MandateReferenceNumber").string("mandate-001"))
                .andExpect(xpath("/FTSingleDebitResponse/TransactionFee").string("0.00"))
                .andExpect(xpath("/FTSingleDebitResponse/ResponseCode").string("00"));

        mockMvc.perform(post(BASE_PATH + "/balanceenquiry")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(balanceRequest("balance-session-001")))
                .andExpect(status().isOk())
                .andExpect(xpath("/BalanceEnquiryResponse/AvailableBalance").string("1000.00"))
                .andExpect(xpath("/BalanceEnquiryResponse/ResponseCode").string("00"));

        mockMvc.perform(post(BASE_PATH + "/fundtransferAdvice_dc")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(creditRequest("credit-advice-001")
                                .replace("FTSingleCreditRequest", "FTAdviceCreditRequest")))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTAdviceCreditResponse/SessionID").string("credit-advice-001"))
                .andExpect(xpath("/FTAdviceCreditResponse/ResponseCode").string("00"));

        mockMvc.perform(post(BASE_PATH + "/fundtransferAdvice_dd")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(debitRequest("debit-advice-001")
                                .replace("FTSingleDebitRequest", "FTAdviceDebitRequest")))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTAdviceDebitResponse/SessionID").string("debit-advice-001"))
                .andExpect(xpath("/FTAdviceDebitResponse/ResponseCode").string("00"));
    }

    @Test
    void reportsPendingStatusAndValidatesDocumentedRequiredFields() throws Exception {
        String sessionId = "pending-session-001";
        mockMvc.perform(post(BASE_PATH + "/fundtransfersingleitem_dc")
                        .header("X-Simulation-Scenario", "event-timeout")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(creditRequest(sessionId)))
                .andExpect(status().isOk())
                .andExpect(xpath("/FTSingleCreditResponse/ResponseCode").string("09"));

        mockMvc.perform(post(BASE_PATH + "/txnstatusquerysingleitem")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(statusQueryRequest(sessionId)))
                .andExpect(status().isOk())
                .andExpect(xpath("/TSQuerySingleResponse/ResponseCode").string("09"));

        mockMvc.perform(post(BASE_PATH + "/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<NESingleRequest/>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsXmlDocumentsContainingExternalEntities() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/nameenquirysingle")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("""
                                <?xml version="1.0"?>
                                <!DOCTYPE request [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                                <NESingleRequest>
                                  <SessionID>&xxe;</SessionID>
                                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                                  <ChannelCode>1</ChannelCode>
                                  <AccountNumber>2222000000012345</AccountNumber>
                                </NESingleRequest>
                                """))
                .andExpect(status().isBadRequest());
    }

    private String nameEnquiryRequest() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <NESingleRequest>
                  <SessionID>000001100913103301000000000001</SessionID>
                  <DestinationInstitutionCode>000002</DestinationInstitutionCode>
                  <ChannelCode>1</ChannelCode>
                  <AccountNumber>2222000000012345</AccountNumber>
                </NESingleRequest>
                """;
    }

    private String creditRequest(String sessionId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
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