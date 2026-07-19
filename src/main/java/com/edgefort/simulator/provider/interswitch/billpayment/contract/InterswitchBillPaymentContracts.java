package com.edgefort.simulator.provider.interswitch.billpayment.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    public record PaymentRequest(
            @NotBlank String TerminalId,
            @NotBlank String paymentCode,
            @NotBlank String customerId,
            @NotBlank String customerMobile,
            String customerEmail,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransactionQueryResponse(
            String requestReference,
            @JsonProperty("transactionRef") String transactionReference,
            String serviceCode,
            String customerId,
            String amount,
            String status,
            String transactionResponseCode,
            String transactionResponseMessage,
            String transactionSet,
            String RechargePIN
    ) {
    }
}