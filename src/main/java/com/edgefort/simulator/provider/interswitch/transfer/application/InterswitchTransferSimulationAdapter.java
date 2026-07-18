package com.edgefort.simulator.provider.interswitch.transfer.application;

import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransferCommand;
import com.edgefort.simulator.core.application.TransferSimulationResult;
import com.edgefort.simulator.core.application.TransferSimulationService;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationScenario;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferRequest;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class InterswitchTransferSimulationAdapter {

    public static final String PROVIDER_ID = "interswitch-transfer";

    private final ProviderOperationSimulator operationSimulator;
    private final TransferSimulationService transferService;

    public InterswitchTransferSimulationAdapter(
            ProviderOperationSimulator operationSimulator,
            TransferSimulationService transferService
    ) {
        this.operationSimulator = operationSimulator;
        this.transferService = transferService;
    }

    public NameEnquiryResponse nameEnquiry(
            String bankCode,
            String accountId,
            String requestedScenario
    ) {
        SimulationBehavior behavior = operationSimulator.simulate(
                PROVIDER_ID, "name-enquiry", bankCode + ":" + accountId, requestedScenario
        );
        if (behavior.scenario() == SimulationScenario.TIMEOUT) {
            return new NameEnquiryResponse(null, "SIMULATOR_TIMEOUT", "Timed out");
        }
        boolean success = behavior.scenario() == SimulationScenario.SUCCESS
                || behavior.scenario() == SimulationScenario.DELAYED_SUCCESS;
        return new NameEnquiryResponse(
                success ? "SIMULATED ACCOUNT" : null,
                success ? "90000" : "90012",
                success ? "Approved by Financial Institution" : "Invalid transaction"
        );
    }

    public TransferResponse transfer(TransferRequest request, String requestedScenario) {
        verifyMac(request);
        TransferSimulationResult result = transferService.simulate(new TransferCommand(
                PROVIDER_ID,
                "transfer",
                request.TransferCode(),
                request.Initiation().Amount(),
                null
        ), requestedScenario);
        return toResponse(result);
    }

    public Optional<TransferResponse> requery(String reference) {
        return transferService.find(PROVIDER_ID, reference).map(this::toResponse);
    }

    private TransferResponse toResponse(TransferSimulationResult result) {
        if (result.duplicate()) {
            return new TransferResponse("90094", "Duplicate Transaction", result.reference(), null);
        }
        if (result.timeout()) {
            return new TransferResponse("SIMULATOR_TIMEOUT", "Timed out", result.reference(), null);
        }
        return switch (result.status()) {
            case SUCCESS -> new TransferResponse(
                    "90000", "Approved by Financial Institution", result.reference(), null
            );
            case FAILED -> new TransferResponse("90096", "System Error", result.reference(), null);
            case PENDING -> new TransferResponse("90009", "Request In Progress", result.reference(), null);
        };
    }

    private TransferResponse toResponse(SimulatedTransaction transaction) {
        return toResponse(new TransferSimulationResult(
                transaction.key().reference(), transaction.status(), SimulationScenario.SUCCESS, false, false
        ));
    }

    private void verifyMac(TransferRequest request) {
        String secureData = request.Initiation().Amount().toPlainString()
                + request.Initiation().CurrencyCode()
                + request.Initiation().PaymentMethodCode()
                + request.Termination().Amount().toPlainString()
                + request.Termination().CurrencyCode()
                + request.Termination().PaymentMethodCode()
                + request.Termination().CountryCode();
        String expectedMac = sha512(secureData);
        if (!MessageDigest.isEqual(
                expectedMac.getBytes(StandardCharsets.US_ASCII),
                request.MAC().toLowerCase().getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new IllegalArgumentException("MAC does not match the Interswitch secure-data version 12 fields");
        }
    }

    private String sha512(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-512")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-512 is unavailable", exception);
        }
    }
}