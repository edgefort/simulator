package com.edgefort.simulator.provider.interswitch.billpayment.application;

import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransactionCommand;
import com.edgefort.simulator.core.application.TransactionSimulationResult;
import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.provider.interswitch.InterswitchApiException;
import com.edgefort.simulator.provider.interswitch.InterswitchProvider;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.BillPaymentDetails;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InterswitchBillPaymentSimulationAdapter {

    private static final String SUCCESS_CODE = "90000";
    private static final String CUSTOMER_NOT_FOUND_CODE = "90002";
    private static final String INVALID_AMOUNT_CODE = "90003";
    private static final String DUPLICATE_CODE = "90005";
    private static final String SERVICE_UNAVAILABLE_CODE = "90006";
    private static final String INVALID_PAYMENT_CODE = "90007";
    private static final String VALIDATION_FAILED_CODE = "90008";
    private static final String IN_PROGRESS_CODE = "90009";
    private static final String SUCCESS_GROUP = "SUCCESSFUL";
    private static final String FAILED_GROUP = "FAILED";
    private static final String PENDING_GROUP = "PENDING";

    private static final String PAYMENT_CODE = "paymentCode";
    private static final String CUSTOMER_ID = "customerId";
    private static final String CUSTOMER_MOBILE = "customerMobile";
    private static final String CUSTOMER_EMAIL = "customerEmail";
    private static final String TRANSACTION_REF = "TransactionRef";
    private static final String RECHARGE_PIN = "RechargePIN";

    private static final DateTimeFormatter PAYMENT_DATE = DateTimeFormatter
            .ofPattern("M/d/yyyy h:mm:ss a", Locale.ENGLISH)
            .withZone(ZoneId.of("Africa/Lagos"));

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

    private final ProviderOperationSimulator operationSimulator;
    private final TransactionSimulationService transactionService;

    public InterswitchBillPaymentSimulationAdapter(
            ProviderOperationSimulator operationSimulator,
            TransactionSimulationService transactionService
    ) {
        this.operationSimulator = operationSimulator;
        this.transactionService = transactionService;
    }

    public CategoriesResponse categories() {
        return new CategoriesResponse(CATEGORIES, SUCCESS_CODE, SUCCESS_GROUP);
    }

    /**
     * Billers of one category, or of every category when {@code categoryId} is absent.
     */
    public BillersResponse billers(Integer categoryId) {
        List<BillerCategory> categoryList = CATEGORIES.stream()
                .filter(category -> categoryId == null || category.Id() == categoryId)
                .map(category -> new BillerCategory(
                        category.Id(), category.Name(), PRODUCTS.entrySet().stream()
                        .filter(entry -> entry.getValue().categoryId() == category.Id())
                        .map(entry -> toBiller(entry.getKey(), entry.getValue()))
                        .toList()
                ))
                .toList();
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

    /**
     * Validates each customer entry. Each entry with a known payment code is one {@code customer-validation}
     * simulation.
     *
     * @param terminalId the {@code TerminalId} header, already verified against the configured terminal
     */
    public CustomerValidationResponse validateCustomers(CustomerValidationRequest request, String terminalId) {
        requireSameTerminal(request.TerminalId(), terminalId);
        List<ValidatedCustomer> customers = request.customers().stream()
                .map(customer -> validateCustomer(terminalId, customer))
                .toList();
        boolean allValid = customers.stream().allMatch(customer -> SUCCESS_CODE.equals(customer.ResponseCode()));
        return new CustomerValidationResponse(
                customers,
                allValid ? SUCCESS_CODE : VALIDATION_FAILED_CODE,
                allValid ? SUCCESS_GROUP : FAILED_GROUP
        );
    }

    /**
     * @param terminalId the {@code TerminalId} header, already verified against the configured terminal
     */
    public PaymentResponse pay(PaymentRequest request, String terminalId) {
        if (request.TerminalId() != null) {
            requireSameTerminal(request.TerminalId(), terminalId);
        }
        PaymentProduct product = PRODUCTS.get(request.paymentCode());
        if (product == null) {
            return response(null, request.amount(), INVALID_PAYMENT_CODE, "Invalid payment code", FAILED_GROUP);
        }
        if (request.amount().compareTo(product.minimumAmount()) < 0
                || product.maximumAmount() != null && request.amount().compareTo(product.maximumAmount()) > 0) {
            return response(null, request.amount(), INVALID_AMOUNT_CODE, "Invalid amount", FAILED_GROUP);
        }

        TransactionSimulationResult result = transactionService.simulate(new TransactionCommand(
                InterswitchProvider.ID,
                InterswitchProvider.BILL_PAYMENT,
                request.requestReference(),
                request.amount(),
                attributes(request, terminalId, product)
        ));
        SimulatedTransaction transaction = result.transaction();
        String transactionRef = transaction.attributes().get(TRANSACTION_REF);
        if (result.duplicate()) {
            return response(transactionRef, request.amount(), DUPLICATE_CODE, "Duplicate transaction", FAILED_GROUP);
        }
        return switch (transaction.status()) {
            case SUCCESS -> successResponse(transaction, product);
            case FAILED -> response(
                    transactionRef, request.amount(), SERVICE_UNAVAILABLE_CODE, "Service unavailable", FAILED_GROUP
            );
            case PENDING -> response(
                    transactionRef, request.amount(), IN_PROGRESS_CODE, "Request in progress", PENDING_GROUP
            );
            case REVERSED -> throw new IllegalStateException("A new payment cannot be reversed when it is received");
        };
    }

    /**
     * Renders a recorded bill payment in the published Query Transaction response.
     */
    public TransactionQueryResponse query(SimulatedTransaction transaction) {
        Map<String, String> attributes = transaction.attributes();
        String paymentCode = attributes.get(PAYMENT_CODE);
        PaymentProduct product = PRODUCTS.get(paymentCode);
        String billerId = Integer.toString(product.billerId());
        String status = switch (transaction.status()) {
            case SUCCESS -> "Completed";
            case PENDING -> "Pending";
            case FAILED -> "Failed";
            case REVERSED -> "Reversed";
        };
        String responseCode = switch (transaction.status()) {
            case SUCCESS -> SUCCESS_CODE;
            case PENDING -> IN_PROGRESS_CODE;
            case FAILED -> SERVICE_UNAVAILABLE_CODE;
            case REVERSED -> null;
        };
        return new TransactionQueryResponse(
                new BillPaymentDetails(
                        product.shortName(), attributes.get(CUSTOMER_ID), null, product.name(), paymentCode, billerId
                ),
                amount(transaction.amount()),
                "566",
                attributes.get(CUSTOMER_ID),
                attributes.get(CUSTOMER_EMAIL),
                attributes.get(CUSTOMER_MOBILE),
                PAYMENT_DATE.format(transaction.createdAt()),
                transaction.key().reference(),
                paymentCode,
                product.name(),
                billerId,
                status,
                "0",
                attributes.get(TRANSACTION_REF),
                responseCode,
                "BillPayment"
        );
    }

    private Map<String, String> attributes(PaymentRequest request, String terminalId, PaymentProduct product) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(PAYMENT_CODE, request.paymentCode());
        attributes.put(CUSTOMER_ID, request.customerId());
        attributes.put(CUSTOMER_MOBILE, request.customerMobile());
        if (request.customerEmail() != null) {
            attributes.put(CUSTOMER_EMAIL, request.customerEmail());
        }
        attributes.put("amount", request.amount().toPlainString());
        attributes.put("TerminalId", terminalId);
        attributes.put(TRANSACTION_REF, transactionReference(terminalId, product, request.requestReference()));
        if (product.pinBased()) {
            attributes.put(RECHARGE_PIN, rechargePin(request.requestReference()));
        }
        return attributes;
    }

    private void requireSameTerminal(String bodyTerminalId, String headerTerminalId) {
        if (!headerTerminalId.equals(bodyTerminalId)) {
            throw new InterswitchApiException(HttpStatus.BAD_REQUEST, "70012", "Unrecognized terminal owner");
        }
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
            return invalidCustomer(terminalId, 0, customer, INVALID_PAYMENT_CODE);
        }
        SimulationOutcome outcome = operationSimulator.simulate(
                InterswitchProvider.ID,
                InterswitchProvider.CUSTOMER_VALIDATION,
                null,
                Map.of("PaymentCode", customer.PaymentCode(), "CustomerId", customer.CustomerId())
        );
        if (outcome != SimulationOutcome.SUCCESS) {
            return invalidCustomer(terminalId, product.billerId(), customer, CUSTOMER_NOT_FOUND_CODE);
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

    private ValidatedCustomer invalidCustomer(
            String terminalId,
            int billerId,
            CustomerToValidate customer,
            String responseCode
    ) {
        return new ValidatedCustomer(
                terminalId, billerId, customer.PaymentCode(), customer.CustomerId(), null, responseCode, null,
                BigDecimal.ZERO, 0, null, BigDecimal.ZERO, null, null, null, null, null, null, null, null, null
        );
    }

    private PaymentResponse successResponse(SimulatedTransaction transaction, PaymentProduct product) {
        Map<String, String> attributes = transaction.attributes();
        String rechargePin = attributes.get(RECHARGE_PIN);
        Map<String, String> additionalInfo = product.categoryId() == 4
                ? Map.of(
                        "network", product.networkName(),
                        "phoneNumber", attributes.get(CUSTOMER_ID),
                        "amountCredited", transaction.amount().movePointLeft(2).setScale(2).toPlainString()
                )
                : Map.of("Pin", rechargePin, "Token", rechargePin, "Units", "55.9");
        String tokenDetails = rechargePin == null ? null : "Pin:" + rechargePin + ";Units:55.9;Token:" + rechargePin;
        return new PaymentResponse(
                attributes.get(TRANSACTION_REF), rechargePin, tokenDetails, amount(transaction.amount()), tokenDetails,
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
            BigDecimal approvedAmount,
            String code,
            String description,
            String grouping
    ) {
        return new PaymentResponse(
                transactionReference, null, null, amount(approvedAmount), null,
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
}
