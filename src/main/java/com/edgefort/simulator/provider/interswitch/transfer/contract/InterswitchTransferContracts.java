package com.edgefort.simulator.provider.interswitch.transfer.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quickteller Service v5 Send Money wire models, named exactly as in the public Single Transfer, Name Enquiry, Get
 * Bank Codes, and Query Transaction documentation.
 */
public final class InterswitchTransferContracts {

    private InterswitchTransferContracts() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NameEnquiryResponse(
            String AccountName,
            String ResponseCode,
            String ResponseCodeGrouping
    ) {
    }

    public record BanksResponse(List<Bank> banks) {
    }

    public record Bank(String id, String cbnCode, String bankName, String bankCode) {
    }

    public record TransferRequest(
            @NotBlank String transferCode,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{128}") String mac,
            @NotBlank String initiatingEntityCode,
            @Valid @NotNull Initiation initiation,
            @Valid @NotNull Termination termination,
            @Valid @NotNull Sender sender,
            @Valid @NotNull Beneficiary beneficiary
    ) {
    }

    public record Initiation(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String currencyCode,
            @NotBlank String paymentMethodCode,
            @NotBlank String channel
    ) {
    }

    public record Termination(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Valid @NotNull AccountReceivable accountReceivable,
            @NotBlank String entityCode,
            @NotBlank String currencyCode,
            @NotBlank String paymentMethodCode,
            @NotBlank String countryCode
    ) {
    }

    public record AccountReceivable(
            @NotBlank @Pattern(regexp = "\\d{10}") String accountNumber,
            @NotBlank String accountType
    ) {
    }

    public record Sender(
            String phone,
            String email,
            @NotBlank String lastname,
            @NotBlank String othernames
    ) {
    }

    public record Beneficiary(
            @NotBlank String lastname,
            @NotBlank String othernames
    ) {
    }

    /**
     * Single Transfer response. {@code Pin} and {@code TransferCode} are published as {@code null}, so nulls are
     * written.
     */
    public record TransferResponse(
            String MAC,
            String TransactionDate,
            String TransactionReference,
            String Pin,
            String TransferCode,
            String ResponseCode,
            String ResponseCodeGrouping
    ) {
    }

    /**
     * Fields of the published Query Transaction response that a transfer can populate. The bill-payment specific
     * fields ({@code BillPayment}, {@code serviceCode}, {@code serviceName}, {@code serviceProviderId},
     * {@code surcharge}, {@code transactionSet}) are not published for transfers and are omitted.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransferQueryResponse(
            String amount,
            String currencyCode,
            String customer,
            String customerEmail,
            String customerMobile,
            String paymentDate,
            String requestReference,
            String status,
            String transactionRef,
            String transactionResponseCode
    ) {
    }
}
