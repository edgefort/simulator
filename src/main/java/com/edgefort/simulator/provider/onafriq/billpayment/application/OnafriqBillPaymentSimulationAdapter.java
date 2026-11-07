package com.edgefort.simulator.provider.onafriq.billpayment.application;

import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransactionCommand;
import com.edgefort.simulator.core.application.TransactionSimulationResult;
import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.PurchaseRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider.AIRTIME;
import static com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider.CABLE_TV;
import static com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider.DATA_BUNDLE;
import static com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider.ELECTRICITY;
import static com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider.EPIN;

/**
 * Maps Onafriq requests to the simulator core and core results to documented Onafriq responses. Purchase responses
 * are rebuilt from the stored transaction, so a requery returns the same data as the original purchase response.
 */
@Component
public class OnafriqBillPaymentSimulationAdapter {

    private static final String PROVIDER = OnafriqBillPaymentProvider.ID;
    private static final String PIN_INSTRUCTIONS = "Dial *203*3*YOURPIN# to recharge.";

    private final TransactionSimulationService transactions;
    private final ProviderOperationSimulator operations;

    public OnafriqBillPaymentSimulationAdapter(
            TransactionSimulationService transactions,
            ProviderOperationSimulator operations
    ) {
        this.transactions = transactions;
        this.operations = operations;
    }

    /**
     * Simulates a purchase keyed by {@code agentReference}. A simulated timeout propagates and closes the connection.
     */
    public Result purchase(String operation, PurchaseRequest request) {
        TransactionSimulationResult result = transactions.simulate(new TransactionCommand(
                PROVIDER, operation, request.agentReference(), request.amount(), request.attributes()
        ));
        if (result.duplicate()) {
            return new Result(HttpStatus.BAD_REQUEST, Response.error(
                    OnafriqBillPaymentContracts.CODE_DUPLICATE, OnafriqBillPaymentContracts.MESSAGE_DUPLICATE
            ));
        }
        return transactionResult(result.transaction());
    }

    public Result requery(String agentReference) {
        return transactions.find(PROVIDER, agentReference)
                .map(this::transactionResult)
                .orElseGet(() -> new Result(HttpStatus.NOT_FOUND, Response.error(
                        OnafriqBillPaymentContracts.CODE_TRANSACTION_NOT_FOUND,
                        OnafriqBillPaymentContracts.MESSAGE_TRANSACTION_NOT_FOUND
                )));
    }

    /**
     * Simulates an account or name validation that fails with the documented name-finder code.
     */
    public Result validation(String operation, Map<String, String> attributes, Supplier<Object> data) {
        SimulationOutcome outcome = operations.simulate(PROVIDER, operation, null, attributes);
        return switch (outcome) {
            case SUCCESS -> new Result(HttpStatus.OK, Response.success(data.get()));
            case FAILURE -> new Result(HttpStatus.INTERNAL_SERVER_ERROR, Response.error(
                    OnafriqBillPaymentContracts.CODE_NAME_FINDER_FAILED,
                    OnafriqBillPaymentContracts.MESSAGE_NAME_FINDER_FAILED
            ));
            case PENDING, TIMEOUT -> throw new IllegalStateException(
                    "Core returned " + outcome + " for stateless operation " + operation
            );
        };
    }

    /**
     * Simulates a catalog lookup answered by the upstream biller. Only success and timeout are declared.
     */
    public Result lookup(String operation, Map<String, String> attributes, Supplier<Object> data) {
        SimulationOutcome outcome = operations.simulate(PROVIDER, operation, null, attributes);
        return switch (outcome) {
            case SUCCESS -> new Result(HttpStatus.OK, Response.success(data.get()));
            case FAILURE, PENDING, TIMEOUT -> throw new IllegalStateException(
                    "Core returned undeclared outcome " + outcome + " for " + operation
            );
        };
    }

    private Result transactionResult(SimulatedTransaction transaction) {
        return switch (transaction.status()) {
            case SUCCESS -> new Result(HttpStatus.OK, Response.success(successData(transaction)));
            case PENDING -> new Result(HttpStatus.OK, Response.pending(pendingData(transaction)));
            case FAILED -> new Result(HttpStatus.INTERNAL_SERVER_ERROR, Response.error(
                    OnafriqBillPaymentContracts.CODE_FAILED, OnafriqBillPaymentContracts.MESSAGE_FAILED
            ));
            case REVERSED -> throw new IllegalStateException(
                    "Onafriq declares no reversed status; transaction " + transaction.key() + " is REVERSED"
            );
        };
    }

    private Map<String, Object> pendingData(SimulatedTransaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transactionStatus", "pending");
        data.put("transactionReference", transactionReference(transaction));
        return data;
    }

    private Map<String, Object> successData(SimulatedTransaction transaction) {
        Map<String, String> attributes = transaction.attributes();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statusCode", "0");
        data.put("transactionStatus", "success");
        data.put("transactionReference", transactionReference(transaction));
        switch (transaction.operation()) {
            case AIRTIME -> data.put("transactionMessage", "Airtime Topup successful on " + attributes.get("phone"));
            case DATA_BUNDLE -> data.put("transactionMessage", "Data bundle successful on " + attributes.get("phone"));
            case CABLE_TV -> {
                data.put("transactionMessage", "Cable TV Subcription successful");
                data.put("baxiReference", number("baxiReference:" + transaction.key().reference()));
                data.put("provider_message", "true");
            }
            case ELECTRICITY -> {
                data.put("transactionMessage", "Payment Successful");
                if (attributes.getOrDefault("service_type", "").endsWith("_prepaid")) {
                    BigDecimal amount = transaction.amount();
                    data.put("tokenCode", digits("tokenCode:" + transaction.key().reference(), 20));
                    data.put("tokenAmount", amount);
                    data.put("amountOfPower", amount.toPlainString() + " kWh");
                }
            }
            case EPIN -> {
                data.put("transactionMessage", "Successful pin purchase");
                data.put("pins", pins(transaction));
            }
            default -> data.put("transactionMessage", "Transaction successful");
        }
        return data;
    }

    private List<Map<String, Object>> pins(SimulatedTransaction transaction) {
        int count = Integer.parseInt(transaction.attributes().get("numberOfPins"));
        String expiresOn = transaction.createdAt().atZone(ZoneOffset.UTC).toLocalDate().plusYears(1).toString();
        List<Map<String, Object>> pins = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String seed = transaction.key().reference() + ":" + index;
            Map<String, Object> pin = new LinkedHashMap<>();
            pin.put("instructions", PIN_INSTRUCTIONS);
            pin.put("serialNumber", digits("serialNumber:" + seed, 13));
            pin.put("pin", digits("pin:" + seed, 16));
            pin.put("expiresOn", expiresOn);
            pins.add(pin);
        }
        return pins;
    }

    /**
     * The platform's numeric transaction reference, derived from {@code agentReference} so it is stable across
     * purchase and requery.
     */
    private static long transactionReference(SimulatedTransaction transaction) {
        return number("transactionReference:" + transaction.key().reference());
    }

    private static long number(String seed) {
        return new BigInteger(1, Arrays.copyOf(sha256(seed), 5)).longValue();
    }

    private static String digits(String seed, int length) {
        byte[] hash = sha256(seed);
        StringBuilder digits = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            digits.append((hash[index % hash.length] & 0xff) % 10);
        }
        return digits.toString();
    }

    private static byte[] sha256(String seed) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Result(HttpStatusCode status, Response body) {
    }
}
