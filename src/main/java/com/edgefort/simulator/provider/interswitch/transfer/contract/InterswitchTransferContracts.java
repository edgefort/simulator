package com.edgefort.simulator.provider.interswitch.transfer.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public final class InterswitchTransferContracts {

    private InterswitchTransferContracts() {
    }

    public record NameEnquiryResponse(
            String accountName,
            String ResponseCode,
            String ResponseDescription
    ) {
    }

    public record FundTransferBank(
            String BankName,
            String CBNCode,
            String BankCode
    ) {
    }

    public record TransferRequest(
            @NotBlank String InitiatingEntityCode,
            @Valid @NotNull Initiation Initiation,
            @Valid @NotNull Termination Termination,
            @Valid @NotNull Sender sender,
            @Valid @NotNull Beneficiary Beneficiary,
            @NotBlank String TransferCode,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{128}") String MAC
    ) {
    }

    public record Initiation(
            @NotNull @DecimalMin("0.01") BigDecimal Amount,
            @NotBlank String CurrencyCode,
            @NotBlank String PaymentMethodCode,
            @NotBlank String Channel
    ) {
    }

    public record Termination(
            @NotNull @DecimalMin("0.01") BigDecimal Amount,
            @NotBlank String CurrencyCode,
            @NotBlank String PaymentMethodCode,
            @NotBlank String CountryCode,
            @Valid @NotNull AccountReceivable AccountReceivable,
            @NotBlank String EntityCode
    ) {
    }

    public record AccountReceivable(
            @NotBlank @Pattern(regexp = "\\d{10}") String AccountNumber,
            @NotBlank String AccountType
    ) {
    }

    public record Sender(
            @NotBlank String lastname,
            @NotBlank String othernames,
            String email,
            String phone
    ) {
    }

    public record Beneficiary(
            @NotBlank String lastname,
            @NotBlank String othernames
    ) {
    }

    public record TransferResponse(
            String ResponseCode,
            String ResponseDescription,
            String TransactionReference,
            String TransferCode
    ) {
    }
}