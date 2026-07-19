package com.edgefort.simulator.provider.onafriq.billpayment.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class OnafriqBillPaymentContracts {

    private OnafriqBillPaymentContracts() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response<T>(
            String status,
            Object code,
            String message,
            T data,
            List<String> errors
    ) {
    }

    public record AccountValidationRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("account_number") String accountNumber
    ) {
    }

    public record ServiceRequest(
            @NotBlank @JsonProperty("service_type") String serviceType
    ) {
    }

    public record AirtimeRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank String plan,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public record DataRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("data_code") String dataCode,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public record CableTvRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("smartcard_number") String smartcardNumber,
            @NotBlank @JsonProperty("product_code") String productCode,
            @Positive @JsonProperty("product_monthsPaidFor") int productMonthsPaidFor,
            @NotNull @DecimalMin("1.00") @JsonProperty("total_amount") BigDecimal totalAmount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {

        @Override
        public BigDecimal amount() {
            return totalAmount;
        }
    }

    public record ElectricityRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("account_number") String accountNumber,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public record EpinRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotNull @DecimalMin("1.00") BigDecimal pinValue,
            @Positive int pinCount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public record JambRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank String profileId,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public record BettingRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("account_number") String accountNumber,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public record VehicleInsuranceRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("customer_name") String customerName,
            @NotBlank @JsonProperty("chassis_number") String chassisNumber,
            @NotBlank @JsonProperty("engine_number") String engineNumber,
            @NotBlank @JsonProperty("plate_number") String plateNumber,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            @NotBlank @Size(min = 7, max = 15) String phone,
            @NotBlank String agentId,
            @NotBlank @Size(max = 40) String agentReference
    ) implements PurchaseRequest {
    }

    public interface PurchaseRequest {

        String serviceType();

        BigDecimal amount();

        String phone();

        String agentId();

        String agentReference();
    }
}