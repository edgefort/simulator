package com.edgefort.simulator.provider.interswitch.transfer.application;

import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransactionCommand;
import com.edgefort.simulator.core.application.TransactionSimulationResult;
import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.provider.interswitch.InterswitchApiException;
import com.edgefort.simulator.provider.interswitch.InterswitchProvider;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferQueryResponse;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferRequest;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
public class InterswitchTransferSimulationAdapter {

    private static final String SUCCESS_CODE = "90000";
    private static final String NAME_ENQUIRY_FAILED_CODE = "40100";
    private static final String TRANSFER_FAILED_CODE = "90096";
    private static final String IN_PROGRESS_CODE = "90009";
    private static final String DUPLICATE_TRANSFER_CODE = "30020";
    private static final String SUCCESSFUL = "SUCCESSFUL";
    private static final String FAILED = "FAILED";
    private static final String PENDING = "PENDING";

    private static final String MAC = "mac";
    private static final String INITIATION_AMOUNT = "initiation.amount";
    private static final String INITIATION_CURRENCY = "initiation.currencyCode";
    private static final String ACCOUNT_NUMBER = "termination.accountReceivable.accountNumber";
    private static final String SENDER_EMAIL = "sender.email";
    private static final String SENDER_PHONE = "sender.phone";

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter TRANSACTION_DATE =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy HH:mm:ss", Locale.ENGLISH).withZone(LAGOS);
    private static final DateTimeFormatter PAYMENT_DATE =
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.ENGLISH).withZone(LAGOS);

    private final ProviderOperationSimulator operationSimulator;
    private final TransactionSimulationService transactionService;

    public InterswitchTransferSimulationAdapter(
            ProviderOperationSimulator operationSimulator,
            TransactionSimulationService transactionService
    ) {
        this.operationSimulator = operationSimulator;
        this.transactionService = transactionService;
    }

    public NameEnquiryResponse nameEnquiry(String bankCode, String accountId) {
        SimulationOutcome outcome = operationSimulator.simulate(
                InterswitchProvider.ID,
                InterswitchProvider.NAME_ENQUIRY,
                null,
                Map.of("bankCode", bankCode, "accountId", accountId)
        );
        return outcome == SimulationOutcome.SUCCESS
                ? new NameEnquiryResponse("SIMULATED ACCOUNT", SUCCESS_CODE, SUCCESSFUL)
                : new NameEnquiryResponse(null, NAME_ENQUIRY_FAILED_CODE, FAILED);
    }

    public TransferResponse transfer(TransferRequest request) {
        verifyMac(request);
        TransactionSimulationResult result = transactionService.simulate(new TransactionCommand(
                InterswitchProvider.ID,
                InterswitchProvider.TRANSFER,
                request.transferCode(),
                request.initiation().amount(),
                attributes(request)
        ));
        SimulatedTransaction transaction = result.transaction();
        if (result.duplicate()) {
            return response(transaction, request.mac(), DUPLICATE_TRANSFER_CODE, FAILED);
        }
        return switch (transaction.status()) {
            case SUCCESS -> response(transaction, request.mac(), SUCCESS_CODE, SUCCESSFUL);
            case FAILED -> response(transaction, request.mac(), TRANSFER_FAILED_CODE, FAILED);
            case PENDING -> response(transaction, request.mac(), IN_PROGRESS_CODE, PENDING);
            case REVERSED -> throw new IllegalStateException("A new transfer cannot be reversed when it is received");
        };
    }

    /**
     * Renders a recorded transfer in the published Query Transaction response.
     */
    public TransferQueryResponse query(SimulatedTransaction transaction) {
        Map<String, String> attributes = transaction.attributes();
        String reference = transaction.key().reference();
        String status = switch (transaction.status()) {
            case SUCCESS -> "Completed";
            case PENDING -> "Pending";
            case FAILED -> "Failed";
            case REVERSED -> "Reversed";
        };
        String responseCode = switch (transaction.status()) {
            case SUCCESS -> SUCCESS_CODE;
            case PENDING -> IN_PROGRESS_CODE;
            case FAILED -> TRANSFER_FAILED_CODE;
            case REVERSED -> null;
        };
        return new TransferQueryResponse(
                attributes.get(INITIATION_AMOUNT),
                attributes.get(INITIATION_CURRENCY),
                attributes.get(ACCOUNT_NUMBER),
                attributes.get(SENDER_EMAIL),
                attributes.get(SENDER_PHONE),
                PAYMENT_DATE.format(transaction.createdAt()),
                reference,
                status,
                reference,
                responseCode
        );
    }

    private TransferResponse response(SimulatedTransaction transaction, String mac, String code, String grouping) {
        return new TransferResponse(
                mac,
                TRANSACTION_DATE.format(transaction.createdAt()),
                transaction.key().reference(),
                null,
                null,
                code,
                grouping
        );
    }

    private Map<String, String> attributes(TransferRequest request) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("initiatingEntityCode", request.initiatingEntityCode());
        attributes.put(MAC, request.mac());
        attributes.put(INITIATION_AMOUNT, request.initiation().amount().toPlainString());
        attributes.put(INITIATION_CURRENCY, request.initiation().currencyCode());
        attributes.put("termination.amount", request.termination().amount().toPlainString());
        attributes.put("termination.entityCode", request.termination().entityCode());
        attributes.put(ACCOUNT_NUMBER, request.termination().accountReceivable().accountNumber());
        if (request.sender().email() != null) {
            attributes.put(SENDER_EMAIL, request.sender().email());
        }
        if (request.sender().phone() != null) {
            attributes.put(SENDER_PHONE, request.sender().phone());
        }
        return attributes;
    }

    private void verifyMac(TransferRequest request) {
        String secureData = request.initiation().amount().toPlainString()
                + request.initiation().currencyCode()
                + request.initiation().paymentMethodCode()
                + request.termination().amount().toPlainString()
                + request.termination().currencyCode()
                + request.termination().paymentMethodCode()
                + request.termination().countryCode();
        if (!MessageDigest.isEqual(
                sha512(secureData).getBytes(StandardCharsets.US_ASCII),
                request.mac().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new InterswitchApiException(
                    HttpStatus.BAD_REQUEST, "20050", "Message integrity validation failed"
            );
        }
    }

    private static String sha512(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-512").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 is unavailable", exception);
        }
    }
}
