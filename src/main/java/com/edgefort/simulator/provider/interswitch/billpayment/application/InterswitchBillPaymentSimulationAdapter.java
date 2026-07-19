package com.edgefort.simulator.provider.interswitch.billpayment.application;

import com.edgefort.simulator.config.SimulationProperties;
import com.edgefort.simulator.core.application.TransferCommand;
import com.edgefort.simulator.core.application.TransferSimulationResult;
import com.edgefort.simulator.core.application.TransferSimulationService;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.Biller;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.BillerCategory;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.BillerList;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.BillersResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CategoriesResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.Category;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerToValidate;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerValidationRequest;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerValidationResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentItem;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentItemsResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentRequest;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.TransactionQueryResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.ValidatedCustomer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InterswitchBillPaymentSimulationAdapter {

    public static final String PROVIDER_ID = "interswitch-bill-payment";

    private static final String SUCCESS_CODE = "90000";
    private static final String SUCCESS_GROUP = "SUCCESSFUL";
    private static final String FAILED_GROUP = "FAILED";
    private static final String PENDING_GROUP = "PENDING";
    private static final List<Category> CATEGORIES = List.of(
            new Category(1, "Utility Bills", "Electricity and other utility payments"),
            new Category(4, "Mobile/Recharge", "Mobile airtime and recharge services")
    );
    private static final Map<String, PaymentProduct> PRODUCTS = Map.of(
            "051758901", new PaymentProduct(
                    17589, 1, "Abuja Disco Buy Power Prepaid", "AbjBuyPw",
                    "Meter Number", null, 2, "Biller requires minimum amount: NGN 350.00.",
                    new BigDecimal("35000"), null, true, null, null, null
            ),
            "628051043", new PaymentProduct(
                    109, 4, "MTN e-Charge Prepaid", "MTNVTU1",
                    "Phone Number", null, 0, "Biller accepts any amount.",
                    new BigDecimal("5000"), new BigDecimal("1000000"), false,
                    "MTN", "628051043", "MO"
            ),
            "6280510490", new PaymentProduct(
                    120, 4, "Etisalat Recharge Top-Up", "ETILAT",
                    "Phone No", null, 0, "Biller accepts any amount.",
                    new BigDecimal("5000"), new BigDecimal("1000000"), false,
                    "9mobile", "6280510425", "MO"
            ),
            "628051045", new PaymentProduct(
                    402, 4, "Glo QuickCharge", "GLOQCK",
                    "Mobile number", null, 0, "Biller accepts any amount.",
                    new BigDecimal("5000"), new BigDecimal("1000000"), false,
                    "GLO", "628051045", "MO"
            )
    );

    private final TransferSimulationService transferService;
    private final Clock clock;
    private final int maxTransactions;
    private final Duration transactionTtl;
    private final Map<String, PaymentSnapshot> snapshots = new LinkedHashMap<>();

    public InterswitchBillPaymentSimulationAdapter(
            TransferSimulationService transferService,
            SimulationProperties properties,
            Clock clock
    ) {
        this.transferService = transferService;
        this.clock = clock;
        this.maxTransactions = properties.getLimits().getMaxTransactions();
        this.transactionTtl = properties.getLimits().getTransactionTtl();
    }

    public CategoriesResponse categories() {
        return new CategoriesResponse(CATEGORIES, SUCCESS_CODE, SUCCESS_GROUP);
    }

    public BillersResponse billers(int categoryId) {
        Optional<Category> category = CATEGORIES.stream().filter(item -> item.Id() == categoryId).findFirst();
        List<BillerCategory> categoryList = category.map(value -> List.of(new BillerCategory(
                value.Id(), value.Name(), PRODUCTS.entrySet().stream()
                        .filter(entry -> entry.getValue().categoryId() == categoryId)
                        .map(entry -> toBiller(entry.getKey(), entry.getValue()))
                        .toList()
        ))).orElseGet(List::of);
        int count = categoryList.stream().mapToInt(item -> item.Billers().size()).sum();
        return new BillersResponse(new BillerList(count, categoryList), SUCCESS_CODE, SUCCESS_GROUP);
    }

    public PaymentItemsResponse paymentItems(int serviceId) {
        List<PaymentItem> items = PRODUCTS.entrySet().stream()
                .filter(entry -> entry.getValue().billerId() == serviceId)
                .map(entry -> toPaymentItem(entry.getKey(), entry.getValue()))
                .toList();
        return new PaymentItemsResponse(items, SUCCESS_CODE, SUCCESS_GROUP);
    }

    public CustomerValidationResponse validateCustomers(CustomerValidationRequest request) {
        List<ValidatedCustomer> customers = request.customers().stream()
                .map(customer -> validateCustomer(request.TerminalId(), customer))
                .toList();
        boolean allValid = customers.stream().allMatch(customer -> SUCCESS_CODE.equals(customer.ResponseCode()));
        return new CustomerValidationResponse(
                customers,
                allValid ? SUCCESS_CODE : "90008",
                allValid ? SUCCESS_GROUP : FAILED_GROUP
        );
    }

    public PaymentResponse pay(PaymentRequest request, String terminalId, String scenario) {
        PaymentProduct product = PRODUCTS.get(request.paymentCode());
        if (product == null) {
            return response(null, null, request.amount(), "90007", "Invalid payment code", FAILED_GROUP);
        }
        if (request.amount().compareTo(product.minimumAmount()) < 0
                || product.maximumAmount() != null && request.amount().compareTo(product.maximumAmount()) > 0) {
            return response(null, null, request.amount(), "90003", "Invalid amount", FAILED_GROUP);
        }

        TransferSimulationResult result = transferService.simulate(new TransferCommand(
                PROVIDER_ID,
                product.categoryId() == 4 ? "airtime-recharge" : "bill-payment",
                request.requestReference(),
                request.amount(),
                null
        ), scenario);

        String transactionReference = transactionReference(terminalId, product, request.requestReference());
        String rechargePin = product.pinBased() ? rechargePin(request.requestReference()) : null;
        if (!result.duplicate()) {
            saveSnapshot(new PaymentSnapshot(
                    request.requestReference(), transactionReference, request.paymentCode(), request.customerId(),
                    request.amount(), rechargePin, result.status(), clock.instant()
            ));
        }
        if (result.duplicate()) {
            return response(transactionReference, null, request.amount(), "90005", "Duplicate transaction", FAILED_GROUP);
        }
        if (result.timeout()) {
            return response(transactionReference, null, request.amount(), "SIMULATOR_TIMEOUT", "Timed out", PENDING_GROUP);
        }
        return switch (result.status()) {
            case SUCCESS -> successResponse(transactionReference, rechargePin, request, product);
            case FAILED -> response(transactionReference, null, request.amount(), "90006", "Service unavailable", FAILED_GROUP);
            case PENDING -> response(transactionReference, null, request.amount(), "90009", "Request is being processed", PENDING_GROUP);
        };
    }

    public Optional<TransactionQueryResponse> query(String requestReference) {
        PaymentSnapshot snapshot = findSnapshot(requestReference);
        if (snapshot == null) {
            return Optional.empty();
        }
        Optional<TransactionStatus> currentStatus = transferService.find(PROVIDER_ID, requestReference)
                .map(transaction -> transaction.status());
        if (currentStatus.isEmpty()) {
            return Optional.empty();
        }
        TransactionStatus status = currentStatus.get();
        String responseCode = switch (status) {
            case SUCCESS -> SUCCESS_CODE;
            case FAILED -> "90006";
            case PENDING -> "90009";
        };
        String responseMessage = switch (status) {
            case SUCCESS -> "Success";
            case FAILED -> "Service unavailable";
            case PENDING -> "Request is being processed";
        };
        String statusName = switch (status) {
            case SUCCESS -> "Completed";
            case FAILED -> "Failed";
            case PENDING -> "Pending";
        };
        return Optional.of(new TransactionQueryResponse(
                snapshot.requestReference(), snapshot.transactionReference(), snapshot.serviceCode(),
                snapshot.customerId(), amount(snapshot.amount()), statusName, responseCode, responseMessage,
                "BillPayment", status == TransactionStatus.SUCCESS ? snapshot.rechargePin() : null
        ));
    }

    private Biller toBiller(String paymentCode, PaymentProduct product) {
        return new Biller(
                product.billerId(), product.name(), product.shortName(), product.name(),
                product.customerField1(), product.customerField2(), null, paymentCode,
                product.amountType(), product.amountTypeDescription(), "566", "NGN",
                product.categoryId(), categoryName(product.categoryId()), product.networkId(), paymentCode,
                product.type()
        );
    }

    private PaymentItem toPaymentItem(String paymentCode, PaymentProduct product) {
        return new PaymentItem(
                "01", product.name(), product.name(), "CustomerRef", "01", "PHV", "0", "0",
                Integer.toString(product.billerId()), Integer.toString(product.categoryId()), "566", "NGN",
                "566", "NGN", List.of(), product.amountType() != 0, 0, 0,
                paymentCode, product.amountType(), "051760201"
        );
    }

    private ValidatedCustomer validateCustomer(String terminalId, CustomerToValidate customer) {
        PaymentProduct product = PRODUCTS.get(customer.PaymentCode());
        if (product == null) {
            return new ValidatedCustomer(
                    terminalId, 0, customer.PaymentCode(), customer.CustomerId(), null, "90007", null,
                    BigDecimal.ZERO, 0, null, BigDecimal.ZERO, null, null, null, null, null, null, null, null, null
            );
        }
        boolean details = customer.WithDetails();
        return new ValidatedCustomer(
                terminalId, product.billerId(), customer.PaymentCode(), customer.CustomerId(),
                details ? "true" : null, SUCCESS_CODE, "SIMULATED CUSTOMER", product.minimumAmount(),
                product.amountType(), product.amountTypeDescription(), BigDecimal.ZERO,
                details ? "1 Simulator Avenue" : null, details ? "01-01-2000" : null,
                details ? "SIMULATED" : null, details ? "CUSTOMER" : null,
                details ? "" : null, details ? "" : null, details ? customer.CustomerId() : null,
                details ? Map.of("MinAmount", amount(product.minimumAmount()), "AmountDue", "0") : null,
                details ? "Simulated customer details" : null
        );
    }

    private PaymentResponse successResponse(
            String transactionReference,
            String rechargePin,
            PaymentRequest request,
            PaymentProduct product
    ) {
        Map<String, String> additionalInfo = product.categoryId() == 4
                ? Map.of(
                        "network", product.networkName(),
                        "phoneNumber", request.customerId(),
                        "amountCredited", request.amount().movePointLeft(2).setScale(2).toPlainString()
                )
                : Map.of("Pin", rechargePin, "Token", rechargePin, "Units", "55.9");
        String tokenDetails = rechargePin == null ? null : "Pin:" + rechargePin + ";Units:55.9;Token:" + rechargePin;
        return new PaymentResponse(
                transactionReference, rechargePin, tokenDetails, amount(request.amount()), tokenDetails,
                additionalInfo, SUCCESS_CODE, "Success", SUCCESS_GROUP
        );
    }

    private String categoryName(int categoryId) {
        return CATEGORIES.stream()
                .filter(category -> category.Id() == categoryId)
                .map(Category::Name)
                .findFirst()
                .orElse("");
    }

    private PaymentResponse response(
            String transactionReference,
            String rechargePin,
            BigDecimal approvedAmount,
            String code,
            String description,
            String grouping
    ) {
        return new PaymentResponse(
                transactionReference, rechargePin, null, amount(approvedAmount), null,
                Map.of(), code, description, grouping
        );
    }

    private String transactionReference(String terminalId, PaymentProduct product, String requestReference) {
        return "PBL|Web|" + terminalId + "|" + product.shortName() + "|" + requestReference;
    }

    private String rechargePin(String requestReference) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestReference.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        return String.format("%020d", new BigInteger(1, digest).mod(BigInteger.TEN.pow(20)));
    }

    private String amount(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private synchronized void saveSnapshot(PaymentSnapshot snapshot) {
        removeExpired();
        while (snapshots.size() >= maxTransactions) {
            String oldestReference = snapshots.keySet().iterator().next();
            snapshots.remove(oldestReference);
        }
        snapshots.put(snapshot.requestReference(), snapshot);
    }

    private synchronized PaymentSnapshot findSnapshot(String requestReference) {
        removeExpired();
        return snapshots.get(requestReference);
    }

    private void removeExpired() {
        Instant cutoff = clock.instant().minus(transactionTtl);
        snapshots.values().removeIf(snapshot -> snapshot.createdAt().isBefore(cutoff));
    }

    private record PaymentProduct(
            int billerId,
            int categoryId,
            String name,
            String shortName,
            String customerField1,
            String customerField2,
            int amountType,
            String amountTypeDescription,
            BigDecimal minimumAmount,
            BigDecimal maximumAmount,
            boolean pinBased,
            String networkName,
            String networkId,
            String type
    ) {
    }

    private record PaymentSnapshot(
            String requestReference,
            String transactionReference,
            String serviceCode,
            String customerId,
            BigDecimal amount,
            String rechargePin,
            TransactionStatus initialStatus,
            Instant createdAt
    ) {
    }
}