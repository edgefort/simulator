package com.edgefort.simulator.provider.onafriq.billpayment.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wire contract of the Onafriq (Baxi) Biller Aggregation Platform v1.0.0. Field names, required fields, and
 * constraints follow the exported OpenAPI schemas of the B2B VAS project; see ONAFRIQ_BILL_PAYMENT_V1.md.
 */
public final class OnafriqBillPaymentContracts {

    private OnafriqBillPaymentContracts() {
    }

    /**
     * Documented response object: {@code status} and {@code code} are mandatory, {@code message}, {@code data}, and
     * {@code errors} are optional. Successful responses carry the numeric code {@code 200}; errors carry a string
     * code from the documented code table.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"status", "message", "code", "data", "errors"})
    public record Response(
            String status,
            String message,
            Object code,
            Object data,
            List<String> errors
    ) {

        public static Response success(Object data) {
            return new Response(STATUS_SUCCESS, MESSAGE_SUCCESSFUL, SUCCESS_CODE, data, null);
        }

        public static Response pending(Object data) {
            return new Response(STATUS_PENDING, MESSAGE_PENDING, CODE_PENDING, data, null);
        }

        public static Response error(String code, String message) {
            return new Response(STATUS_ERROR, message, code, null, null);
        }

        public static Response error(String code, String message, List<String> errors) {
            return new Response(STATUS_ERROR, message, code, null, errors);
        }
    }

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ERROR = "error";
    public static final int SUCCESS_CODE = 200;
    public static final String MESSAGE_SUCCESSFUL = "Successful";

    /** The exchange is still pending. */
    public static final String CODE_PENDING = "EXC00114";
    public static final String MESSAGE_PENDING = "The exchange is still pending.";
    /** Failed Transaction from the service provider. */
    public static final String CODE_FAILED = "BX0022";
    public static final String MESSAGE_FAILED = "Failed Transaction from the service provider";
    /** Duplicate Agent Transaction Reference. */
    public static final String CODE_DUPLICATE = "BX0023";
    public static final String MESSAGE_DUPLICATE = "Duplicate Agent Transaction Reference";
    /** Transaction does not exist. */
    public static final String CODE_TRANSACTION_NOT_FOUND = "BX0002";
    public static final String MESSAGE_TRANSACTION_NOT_FOUND = "Transaction does not exist";
    /** Error encountered during name finder validation. */
    public static final String CODE_NAME_FINDER_FAILED = "BX0025";
    public static final String MESSAGE_NAME_FINDER_FAILED = "Error encountered during name finder validation";
    /** Invalid request parameters data. */
    public static final String CODE_INVALID_REQUEST = "BX0003";
    public static final String MESSAGE_INVALID_REQUEST = "Invalid request parameters data";
    /** Bearer Token not provided. */
    public static final String CODE_CREDENTIALS_MISSING = "BX0013";
    public static final String MESSAGE_CREDENTIALS_MISSING = "Bearer Token not provided";
    /** Bad credentials. */
    public static final String CODE_BAD_CREDENTIALS = "SEC00001";
    public static final String MESSAGE_BAD_CREDENTIALS = "Bad credentials";

    /** A purchase creating a transaction that {@code /superagent/transaction/requery} can report. */
    public interface PurchaseRequest {

        String agentReference();

        /** Amount debited, or {@code null} when the request carries none. */
        BigDecimal amount();

        /** Request values keyed by their documented field names. */
        Map<String, String> attributes();
    }

    public record AirtimeRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank String plan,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String phone,
            String agentId,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "plan", plan, "amount", amount, "phone", phone,
                    "agentId", agentId, "agentReference", agentReference
            );
        }
    }

    public record DataBundleRequest(
            @JsonProperty("service_type") @JsonAlias("serviceType") String serviceType,
            @NotBlank String phone,
            @NotNull @DecimalMin("50") BigDecimal amount,
            @JsonProperty("package") @Pattern(regexp = "RENEW|CHANGE_IMMEDIATE|CHANGE_AFTER_EXPIRY") String dataPackage,
            String datacode,
            String agentId,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "phone", phone, "amount", amount, "package", dataPackage,
                    "datacode", datacode, "agentId", agentId, "agentReference", agentReference
            );
        }
    }

    public record CableTvRequest(
            @NotNull @Positive @JsonProperty("total_amount") BigDecimal totalAmount,
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("smartcard_number") String smartcardNumber,
            @NotBlank @JsonProperty("product_code") String productCode,
            @NotNull @Positive @JsonProperty("product_monthsPaidFor") Integer productMonthsPaidFor,
            @JsonProperty("isBoxoffice") @JsonAlias("isBoxOffice") Boolean isBoxoffice,
            @JsonProperty("addon_code") Object addonCode,
            String agentId,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public BigDecimal amount() {
            return totalAmount;
        }

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "total_amount", totalAmount, "service_type", serviceType, "smartcard_number", smartcardNumber,
                    "product_code", productCode, "product_monthsPaidFor", productMonthsPaidFor,
                    "agentId", agentId, "agentReference", agentReference
            );
        }
    }

    public record ElectricityRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("account_number") String accountNumber,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String phone,
            String agentId,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "account_number", accountNumber, "amount", amount, "phone", phone,
                    "agentId", agentId, "agentReference", agentReference
            );
        }
    }

    /** Upper bound of {@code numberOfPins}, so a simulated pin list stays bounded. */
    public static final int MAXIMUM_PINS = 100;

    public record EpinRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotNull @Positive BigDecimal amount,
            @NotNull @Positive BigDecimal pinValue,
            @NotNull @Min(1) @Max(MAXIMUM_PINS) Integer numberOfPins,
            String agentId,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "amount", amount, "pinValue", pinValue,
                    "numberOfPins", numberOfPins, "agentId", agentId, "agentReference", agentReference
            );
        }
    }

    public record ExamPinPurchaseRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("account_number") String accountNumber,
            @NotBlank @Pattern(regexp = "DE|UTME|UTME_MOCK|pin|token") @JsonProperty("product_code") String productCode,
            String phone,
            @NotNull BigDecimal amount,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "account_number", accountNumber, "product_code", productCode,
                    "phone", phone, "amount", amount, "agentReference", agentReference
            );
        }
    }

    public record BettingRequest(
            @NotBlank String action,
            @NotNull @DecimalMin("50") BigDecimal amount,
            @JsonProperty("account_number") String accountNumber,
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank String agentReference
    ) implements PurchaseRequest {

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "action", action, "amount", amount, "account_number", accountNumber,
                    "service_type", serviceType, "agentReference", agentReference
            );
        }
    }

    public record VehicleInsuranceRequest(
            @NotBlank @Pattern(regexp = "carpaddy|mutualbenefit|cornerstone") @JsonProperty("service_type")
            String serviceType,
            @NotBlank String agentReference,
            @NotNull @Valid Client client,
            @NotNull @Valid Vehicle vehicle,
            @NotNull @Valid Order order
    ) implements PurchaseRequest {

        @Override
        public BigDecimal amount() {
            return order.amount();
        }

        @Override
        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "agentReference", agentReference,
                    "client.phone", client.phone(), "vehicle.registrationNumber", vehicle.registrationNumber(),
                    "order.amount", order.amount()
            );
        }
    }

    public record Client(
            @NotBlank @Size(max = 25) String firstName,
            @NotBlank @Size(max = 25) String lastName,
            @NotBlank @Pattern(regexp = "^[0-9]{11}$") String phone,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 50) String address,
            @NotBlank @Size(max = 6) String gender,
            @NotBlank @Size(max = 25) String companyName,
            @NotBlank @Pattern(regexp = "^[1-9]{4}-[0-9]{2}-[0-9]{2}$") String dateOfBirth,
            String occupation
    ) {
    }

    public record Vehicle(
            @NotBlank @Size(max = 75) String vehicleType,
            @NotBlank @Size(max = 25) String vehicleManufacturer,
            @NotBlank @Size(max = 25) String vehicleModel,
            @NotBlank @Size(max = 25) String registrationNumber,
            @NotBlank @Size(max = 25) String engineNumber,
            @NotBlank @Size(max = 25) String chassisNumber,
            @NotNull @Min(1960) Integer yearOfManufacture,
            @Size(max = 25) String color,
            @Min(1960) Integer yearOfPurchase
    ) {
    }

    public record Order(
            @Min(0) BigDecimal vehicleTypeId,
            @Min(0) BigDecimal orderDescriptionId,
            @Size(max = 75) String orderDescriptionName,
            @Size(max = 25) String vehicleManufacturerId,
            @Size(max = 25) String vehicleModelId,
            @NotBlank @Size(max = 25) String city,
            @DecimalMin("0") BigDecimal amount,
            @Size(max = 25) String transactionCode,
            @Size(max = 25) String policyholder,
            @NotBlank @Size(max = 25) String action
    ) {
    }

    /** Name finder query; the optional fields are service-specific and documented on the account verification DTO. */
    public record NameFinderRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("account_number") String accountNumber,
            @DecimalMin("0") BigDecimal amount,
            String username,
            @JsonProperty("product_code") String productCode,
            @JsonProperty("pay_code") String payCode,
            @Pattern(regexp = "DEPOSIT|WITHDRAW") String action,
            @Pattern(regexp = "^[A-Za-z]{3}$") @JsonProperty("country_code") String countryCode
    ) {

        public Map<String, String> attributes() {
            return attributesOf(
                    "service_type", serviceType, "account_number", accountNumber, "product_code", productCode
            );
        }
    }

    public record ElectricityVerifyRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @JsonProperty("account_number") String accountNumber
    ) {

        public Map<String, String> attributes() {
            return attributesOf("service_type", serviceType, "account_number", accountNumber);
        }
    }

    public record BettingValidateRequest(
            @NotBlank @JsonProperty("account_number") String accountNumber,
            @NotBlank @JsonProperty("service_type") String serviceType
    ) {

        public Map<String, String> attributes() {
            return attributesOf("service_type", serviceType, "account_number", accountNumber);
        }
    }

    public record DataBundlesRequest(
            @NotBlank @JsonProperty("service_type") String serviceType,
            @JsonProperty("account_number") String accountNumber
    ) {

        public Map<String, String> attributes() {
            return attributesOf("service_type", serviceType, "account_number", accountNumber);
        }
    }

    public record ServiceTypeRequest(
            @NotBlank @JsonProperty("service_type") String serviceType
    ) {

        public Map<String, String> attributes() {
            return attributesOf("service_type", serviceType);
        }
    }

    public record BouquetListRequest(
            @JsonProperty("service_type") String serviceType
    ) {

        public Map<String, String> attributes() {
            return attributesOf("service_type", serviceType);
        }
    }

    public record AddonsRequest(
            @JsonProperty("service_type") String serviceType,
            @NotBlank @JsonProperty("product_code") String productCode
    ) {

        public Map<String, String> attributes() {
            return attributesOf("service_type", serviceType, "product_code", productCode);
        }
    }

    /**
     * Builds request attributes from alternating documented names and values, skipping absent values. Amounts use
     * {@link BigDecimal#toPlainString()}.
     */
    static Map<String, String> attributesOf(Object... namesAndValues) {
        Map<String, String> attributes = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            Object value = namesAndValues[index + 1];
            if (value == null) {
                continue;
            }
            String text = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
            attributes.put((String) namesAndValues[index], text);
        }
        return attributes;
    }
}
