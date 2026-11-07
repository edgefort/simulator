package com.edgefort.simulator.provider.interswitch.billpayment.contract;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class InterswitchBillPaymentContracts {

    private InterswitchBillPaymentContracts() {
    }

    public record Category(int Id, String Name, String Description) {
    }

    public record CategoriesResponse(
            List<Category> BillerCategories,
            String ResponseCode,
            String ResponseCodeGrouping
    ) {
    }

    public record Biller(
            int Id,
            String Name,
            String ShortName,
            String Narration,
            String CustomerField1,
            String CustomerField2,
            String LogoUrl,
            String PaymentCode,
            int AmountType,
            String AmountTypeDescription,
            String CurrencyCode,
            String CurrencySymbol,
            int CategoryId,
            String CategoryName,
            String NetworkId,
            String ProductCode,
            String Type
    ) {
    }

    public record BillerCategory(int Id, String Name, List<Biller> Billers) {
    }

    public record BillerList(int Count, List<BillerCategory> Category) {
    }

    public record BillersResponse(
            BillerList BillerList,
            String ResponseCode,
            String ResponseCodeGrouping
    ) {
    }

    public record PaymentItem(
            String Id,
            String Name,
            String BillerName,
            String ConsumerIdField,
            String Code,
            String BillerType,
            String ItemFee,
            String Amount,
            String BillerId,
            String BillerCategoryId,
            String CurrencyCode,
            String CurrencySymbol,
            String ItemCurrencyCode,
            String ItemCurrencySymbol,
            List<String> Children,
            boolean IsAmountFixed,
            int SortOrder,
            int PictureId,
            String PaymentCode,
            int AmountType,
            String PaydirectItemCode
    ) {
    }

    public record PaymentItemsResponse(
            List<PaymentItem> PaymentItems,
            String ResponseCode,
            String ResponseCodeGrouping
    ) {
    }

    public record CustomerValidationRequest(
            @NotEmpty List<@Valid CustomerToValidate> customers,
            @NotBlank String TerminalId
    ) {
    }

    public record CustomerToValidate(
            @NotBlank String PaymentCode,
            @NotBlank String CustomerId,
            String Amount,
            boolean WithDetails
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ValidatedCustomer(
            String TerminalId,
            int BillerId,
            String PaymentCode,
            String CustomerId,
            String WithDetails,
            String ResponseCode,
            String FullName,
            BigDecimal Amount,
            int AmountType,
            String AmountTypeDescription,
            BigDecimal Surcharge,
            String Address,
            String DateOfBirth,
            String Lastname,
            String Othernames,
            String Title,
            String SecConsumerId,
            String PriConsumerId,
            Map<String, String> AdditionalInfoParameters,
            String AdditionalInfo
    ) {
    }

    public record CustomerValidationResponse(
            List<ValidatedCustomer> Customers,
            String ResponseCode,
            String ResponseCodeGrouping
    ) {
    }

    /**
     * Make Payment request. The Bills Payment guide publishes the fields in camelCase in its main example and in
     * PascalCase in its "Passing Additional Information" example, so property names bind case-insensitively.
     * {@code TerminalId} appears in the Airtime guide body only and is therefore optional.
     */
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    public record PaymentRequest(
            String TerminalId,
            @NotBlank String paymentCode,
            @NotBlank String customerId,
            @NotBlank String customerMobile,
            String customerEmail,
            String customerName,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String additionalInfo,
            @NotBlank @Size(max = 20) String requestReference
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentResponse(
            String TransactionRef,
            String RechargePIN,
            String PhcnTokenDetails,
            String ApprovedAmount,
            String MiscData,
            Map<String, String> AdditionalInfo,
            String ResponseCode,
            String ResponseDescription,
            String ResponseCodeGrouping
    ) {
    }

    /**
     * Query Transaction response as published by the Bills Payment and Airtime guides.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionQueryResponse(
            BillPaymentDetails BillPayment,
            String amount,
            String currencyCode,
            String customer,
            String customerEmail,
            String customerMobile,
            String paymentDate,
            String requestReference,
            String serviceCode,
            String serviceName,
            String serviceProviderId,
            String status,
            String surcharge,
            String transactionRef,
            String transactionResponseCode,
            String transactionSet
    ) {
    }

    public record BillPaymentDetails(
            String biller,
            String customerId1,
            String customerId2,
            String paymentTypeName,
            String paymentTypeCode,
            String billerId
    ) {
    }
}