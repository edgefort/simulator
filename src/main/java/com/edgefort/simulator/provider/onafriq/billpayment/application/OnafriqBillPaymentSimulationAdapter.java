package com.edgefort.simulator.provider.onafriq.billpayment.application;

import com.edgefort.simulator.core.application.TransferCommand;
import com.edgefort.simulator.core.application.TransferSimulationResult;
import com.edgefort.simulator.core.application.TransferSimulationService;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.PurchaseRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.Response;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OnafriqBillPaymentSimulationAdapter {

    public static final String PROVIDER_ID = "onafriq-bill-payment";

    private final TransferSimulationService simulationService;

    public OnafriqBillPaymentSimulationAdapter(TransferSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    public Result purchase(PurchaseRequest request, String operation, String scenario) {
        TransferSimulationResult result = simulationService.simulate(new TransferCommand(
                PROVIDER_ID,
                operation,
                request.agentReference(),
                request.amount(),
                null
        ), scenario);
        if (result.duplicate()) {
            return error(HttpStatus.BAD_REQUEST, "BX0023", "Duplicate agent reference");
        }
        if (result.timeout()) {
            return error(HttpStatus.GATEWAY_TIMEOUT, "BX0001", "Request timed out");
        }
        return transactionResponse(
                result.status(),
                request.agentReference(),
                request.serviceType(),
                request.amount()
        );
    }

    public Result requery(String agentReference) {
        Optional<SimulatedTransaction> transaction = simulationService.find(PROVIDER_ID, agentReference);
        if (transaction.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "BX0018", "Transaction not found");
        }
        SimulatedTransaction stored = transaction.get();
        return transactionResponse(stored.status(), agentReference, null, stored.amount());
    }

    private Result transactionResponse(
            TransactionStatus status,
            String agentReference,
            String serviceType,
            BigDecimal amount
    ) {
        Map<String, Object> data = transactionData(status, agentReference, serviceType, amount);
        return switch (status) {
            case SUCCESS -> new Result(HttpStatus.OK, new Response<>(
                    "success", 200, "Successful", data, List.of()
            ));
            case PENDING -> new Result(HttpStatus.OK, new Response<>(
                    "pending", "EXC00114", "Transaction is pending", data, List.of()
            ));
            case FAILED -> error(HttpStatus.SERVICE_UNAVAILABLE, "BX0022", "Provider service unavailable");
        };
    }

    private Map<String, Object> transactionData(
            TransactionStatus status,
            String agentReference,
            String serviceType,
            BigDecimal amount
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("statusCode", status == TransactionStatus.SUCCESS ? "0" : "1");
        data.put("transactionStatus", status.name().toLowerCase());
        data.put("transactionReference", "ONAFRIQ-%08X".formatted(agentReference.hashCode()));
        data.put("transactionMessage", status == TransactionStatus.SUCCESS
                ? "Transaction successful" : "Transaction pending");
        data.put("agentReference", agentReference);
        if (serviceType != null) {
            data.put("serviceType", serviceType);
        }
        data.put("amount", amount);
        return data;
    }

    private Result error(HttpStatus status, String code, String message) {
        return new Result(status, new Response<>(
                "error", code, message, Map.of(), List.of(message)
        ));
    }

    public record Result(HttpStatus status, Response<Map<String, Object>> body) {
    }
}