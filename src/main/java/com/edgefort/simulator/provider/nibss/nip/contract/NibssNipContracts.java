package com.edgefort.simulator.provider.nibss.nip.contract;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.math.BigDecimal;

public final class NibssNipContracts {

    private NibssNipContracts() {
    }

    @JacksonXmlRootElement(localName = "NESingleRequest")
    public record NameEnquiryRequest(
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @NotBlank @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "AccountNumber") String accountNumber
    ) {
    }

    @JacksonXmlRootElement(localName = "NESingleResponse")
    public record NameEnquiryResponse(
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "AccountNumber") String accountNumber,
            @JacksonXmlProperty(localName = "AccountName") String accountName,
            @JacksonXmlProperty(localName = "BankVerificationNumber") String bankVerificationNumber,
            @JacksonXmlProperty(localName = "KYCLevel") String kycLevel,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }

    @JacksonXmlRootElement(localName = "FTSingleCreditRequest")
    public record CreditTransferRequest(
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @NotBlank @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @NotBlank @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorAccountName") String originatorAccountName,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorAccountNumber") String originatorAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorBankVerificationNumber") String originatorBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorKYCLevel") String originatorKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @NotBlank @Size(max = 100) @JacksonXmlProperty(localName = "Narration") String narration,
            @NotBlank @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @NotNull @DecimalMin("0.01") @JacksonXmlProperty(localName = "Amount") BigDecimal amount
    ) {
    }

    @JacksonXmlRootElement(localName = "FTSingleCreditResponse")
    public record CreditTransferResponse(
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @JacksonXmlProperty(localName = "OriginatorAccountName") String originatorAccountName,
            @JacksonXmlProperty(localName = "OriginatorAccountNumber") String originatorAccountNumber,
            @JacksonXmlProperty(localName = "OriginatorBankVerificationNumber") String originatorBankVerificationNumber,
            @JacksonXmlProperty(localName = "OriginatorKYCLevel") String originatorKycLevel,
            @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @JacksonXmlProperty(localName = "Narration") String narration,
            @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @JacksonXmlProperty(localName = "Amount") BigDecimal amount,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }

    @JacksonXmlRootElement(localName = "FTSingleDebitRequest")
    public record DebitTransferRequest(
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @NotBlank @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @NotBlank @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "DebitAccountName") String debitAccountName,
            @NotBlank @JacksonXmlProperty(localName = "DebitAccountNumber") String debitAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "DebitBankVerificationNumber") String debitBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "DebitKYCLevel") String debitKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @NotBlank @Size(max = 100) @JacksonXmlProperty(localName = "Narration") String narration,
            @NotBlank @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @NotBlank @JacksonXmlProperty(localName = "MandateReferenceNumber") String mandateReferenceNumber,
            @NotNull @DecimalMin("0.00") @JacksonXmlProperty(localName = "TransactionFee") BigDecimal transactionFee,
            @NotNull @DecimalMin("0.01") @JacksonXmlProperty(localName = "Amount") BigDecimal amount
    ) {
    }

    @JacksonXmlRootElement(localName = "FTSingleDebitResponse")
    public record DebitTransferResponse(
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "DebitAccountName") String debitAccountName,
            @JacksonXmlProperty(localName = "DebitAccountNumber") String debitAccountNumber,
            @JacksonXmlProperty(localName = "DebitBankVerificationNumber") String debitBankVerificationNumber,
            @JacksonXmlProperty(localName = "DebitKYCLevel") String debitKycLevel,
            @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @JacksonXmlProperty(localName = "Narration") String narration,
            @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @JacksonXmlProperty(localName = "MandateReferenceNumber") String mandateReferenceNumber,
            @JacksonXmlProperty(localName = "TransactionFee") BigDecimal transactionFee,
            @JacksonXmlProperty(localName = "Amount") BigDecimal amount,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }

    @JacksonXmlRootElement(localName = "TSQuerySingleRequest")
    public record TransactionStatusQueryRequest(
            @NotBlank @JacksonXmlProperty(localName = "SourceInstitutionCode") String sourceInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId
    ) {
    }

    @JacksonXmlRootElement(localName = "TSQuerySingleResponse")
    public record TransactionStatusQueryResponse(
            @JacksonXmlProperty(localName = "SourceInstitutionCode") String sourceInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }

    @JacksonXmlRootElement(localName = "BalanceEnquiryRequest")
    public record BalanceEnquiryRequest(
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @NotBlank @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "AuthorizationCode") String authorizationCode,
            @NotBlank @JacksonXmlProperty(localName = "TargetAccountName") String targetAccountName,
            @NotBlank @JacksonXmlProperty(localName = "TargetBankVerificationNumber") String targetBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "TargetAccountNumber") String targetAccountNumber
    ) {
    }

    @JacksonXmlRootElement(localName = "BalanceEnquiryResponse")
    public record BalanceEnquiryResponse(
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "AuthorizationCode") String authorizationCode,
            @JacksonXmlProperty(localName = "TargetAccountName") String targetAccountName,
            @JacksonXmlProperty(localName = "TargetBankVerificationNumber") String targetBankVerificationNumber,
            @JacksonXmlProperty(localName = "TargetAccountNumber") String targetAccountNumber,
            @JacksonXmlProperty(localName = "AvailableBalance") BigDecimal availableBalance,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }

    @JacksonXmlRootElement(localName = "FTAdviceCreditRequest")
    public record CreditAdviceRequest(
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @NotBlank @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @NotBlank @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorAccountName") String originatorAccountName,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorAccountNumber") String originatorAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorBankVerificationNumber") String originatorBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "OriginatorKYCLevel") String originatorKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @NotBlank @Size(max = 100) @JacksonXmlProperty(localName = "Narration") String narration,
            @NotBlank @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @NotNull @DecimalMin("0.01") @JacksonXmlProperty(localName = "Amount") BigDecimal amount
    ) {
    }

    @JacksonXmlRootElement(localName = "FTAdviceCreditResponse")
    public record CreditAdviceResponse(
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @JacksonXmlProperty(localName = "OriginatorAccountName") String originatorAccountName,
            @JacksonXmlProperty(localName = "OriginatorAccountNumber") String originatorAccountNumber,
            @JacksonXmlProperty(localName = "OriginatorBankVerificationNumber") String originatorBankVerificationNumber,
            @JacksonXmlProperty(localName = "OriginatorKYCLevel") String originatorKycLevel,
            @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @JacksonXmlProperty(localName = "Narration") String narration,
            @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @JacksonXmlProperty(localName = "Amount") BigDecimal amount,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }

    @JacksonXmlRootElement(localName = "FTAdviceDebitRequest")
    public record DebitAdviceRequest(
            @NotBlank @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @NotBlank @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @NotBlank @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @NotBlank @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @NotBlank @JacksonXmlProperty(localName = "DebitAccountName") String debitAccountName,
            @NotBlank @JacksonXmlProperty(localName = "DebitAccountNumber") String debitAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "DebitBankVerificationNumber") String debitBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "DebitKYCLevel") String debitKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @NotBlank @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @NotBlank @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @NotBlank @Size(max = 100) @JacksonXmlProperty(localName = "Narration") String narration,
            @NotBlank @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @NotBlank @JacksonXmlProperty(localName = "MandateReferenceNumber") String mandateReferenceNumber,
            @NotNull @DecimalMin("0.00") @JacksonXmlProperty(localName = "TransactionFee") BigDecimal transactionFee,
            @NotNull @DecimalMin("0.01") @JacksonXmlProperty(localName = "Amount") BigDecimal amount
    ) {
    }

    @JacksonXmlRootElement(localName = "FTAdviceDebitResponse")
    public record DebitAdviceResponse(
            @JacksonXmlProperty(localName = "SessionID") String sessionId,
            @JacksonXmlProperty(localName = "NameEnquiryRef") String nameEnquiryRef,
            @JacksonXmlProperty(localName = "DestinationInstitutionCode") String destinationInstitutionCode,
            @JacksonXmlProperty(localName = "ChannelCode") String channelCode,
            @JacksonXmlProperty(localName = "DebitAccountName") String debitAccountName,
            @JacksonXmlProperty(localName = "DebitAccountNumber") String debitAccountNumber,
            @JacksonXmlProperty(localName = "DebitBankVerificationNumber") String debitBankVerificationNumber,
            @JacksonXmlProperty(localName = "DebitKYCLevel") String debitKycLevel,
            @JacksonXmlProperty(localName = "BeneficiaryAccountName") String beneficiaryAccountName,
            @JacksonXmlProperty(localName = "BeneficiaryAccountNumber") String beneficiaryAccountNumber,
            @JacksonXmlProperty(localName = "BeneficiaryBankVerificationNumber") String beneficiaryBankVerificationNumber,
            @JacksonXmlProperty(localName = "BeneficiaryKYCLevel") String beneficiaryKycLevel,
            @JacksonXmlProperty(localName = "TransactionLocation") String transactionLocation,
            @JacksonXmlProperty(localName = "Narration") String narration,
            @JacksonXmlProperty(localName = "PaymentReference") String paymentReference,
            @JacksonXmlProperty(localName = "MandateReferenceNumber") String mandateReferenceNumber,
            @JacksonXmlProperty(localName = "TransactionFee") BigDecimal transactionFee,
            @JacksonXmlProperty(localName = "Amount") BigDecimal amount,
            @JacksonXmlProperty(localName = "ResponseCode") String responseCode
    ) {
    }
}