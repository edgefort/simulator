package com.edgefort.simulator.provider.nibss.nip.application;

import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransferCommand;
import com.edgefort.simulator.core.application.TransferSimulationResult;
import com.edgefort.simulator.core.application.TransferSimulationService;
import com.edgefort.simulator.core.scenario.SimulationBehavior;
import com.edgefort.simulator.core.scenario.SimulationScenario;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.BalanceEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.BalanceEnquiryResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditAdviceRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditAdviceResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitAdviceRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitAdviceResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitTransferResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.TransactionStatusQueryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.TransactionStatusQueryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class NibssNipSimulationAdapter {

    public static final String PROVIDER_ID = "nibss-nip";

    private final ProviderOperationSimulator operationSimulator;
    private final TransferSimulationService transferService;

    public NibssNipSimulationAdapter(
            ProviderOperationSimulator operationSimulator,
            TransferSimulationService transferService
    ) {
        this.operationSimulator = operationSimulator;
        this.transferService = transferService;
    }

    public NameEnquiryResponse nameEnquiry(NameEnquiryRequest request, String requestedScenario) {
        SimulationBehavior behavior = operationSimulator.simulate(
                PROVIDER_ID, "name-enquiry", request.sessionId(), requestedScenario
        );
        boolean success = isSuccess(behavior.scenario());
        return new NameEnquiryResponse(
                request.sessionId(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.accountNumber(),
                success ? "SIMULATED ACCOUNT" : null,
                success ? "00000000000" : null,
                success ? "1" : null,
                operationResponseCode(behavior.scenario())
        );
    }

    public CreditTransferResponse creditTransfer(CreditTransferRequest request, String requestedScenario) {
        TransferSimulationResult result = simulateTransfer(
                "fund-transfer-direct-credit", request.sessionId(), request.amount(), requestedScenario
        );
        return new CreditTransferResponse(
                request.sessionId(),
                request.nameEnquiryRef(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.beneficiaryAccountName(),
                request.beneficiaryAccountNumber(),
                request.beneficiaryKycLevel(),
                request.beneficiaryBankVerificationNumber(),
                request.originatorAccountName(),
                request.originatorAccountNumber(),
                request.originatorBankVerificationNumber(),
                request.originatorKycLevel(),
                request.transactionLocation(),
                request.narration(),
                request.paymentReference(),
                request.amount(),
                transferResponseCode(result)
        );
    }

    public DebitTransferResponse debitTransfer(DebitTransferRequest request, String requestedScenario) {
        TransferSimulationResult result = simulateTransfer(
                "fund-transfer-direct-debit", request.sessionId(), request.amount(), requestedScenario
        );
        return new DebitTransferResponse(
                request.sessionId(),
                request.nameEnquiryRef(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.debitAccountName(),
                request.debitAccountNumber(),
                request.debitBankVerificationNumber(),
                request.debitKycLevel(),
                request.beneficiaryAccountName(),
                request.beneficiaryAccountNumber(),
                request.beneficiaryBankVerificationNumber(),
                request.beneficiaryKycLevel(),
                request.transactionLocation(),
                request.narration(),
                request.paymentReference(),
                request.mandateReferenceNumber(),
                request.transactionFee(),
                request.amount(),
                transferResponseCode(result)
        );
    }

    public TransactionStatusQueryResponse transactionStatus(TransactionStatusQueryRequest request) {
        String responseCode = transferService.find(PROVIDER_ID, request.sessionId())
                .map(this::transactionResponseCode)
                .orElse("25");
        return new TransactionStatusQueryResponse(
                request.sourceInstitutionCode(), request.channelCode(), request.sessionId(), responseCode
        );
    }

    public BalanceEnquiryResponse balanceEnquiry(BalanceEnquiryRequest request, String requestedScenario) {
        SimulationBehavior behavior = operationSimulator.simulate(
                PROVIDER_ID, "balance-enquiry", request.sessionId(), requestedScenario
        );
        boolean success = isSuccess(behavior.scenario());
        return new BalanceEnquiryResponse(
                request.sessionId(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.authorizationCode(),
                request.targetAccountName(),
                request.targetBankVerificationNumber(),
                request.targetAccountNumber(),
                success ? new BigDecimal("1000.00") : null,
                operationResponseCode(behavior.scenario())
        );
    }

    public CreditAdviceResponse creditAdvice(CreditAdviceRequest request, String requestedScenario) {
        SimulationBehavior behavior = operationSimulator.simulate(
                PROVIDER_ID, "fund-transfer-advice-direct-credit", request.sessionId(), requestedScenario
        );
        return new CreditAdviceResponse(
                request.sessionId(),
                request.nameEnquiryRef(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.beneficiaryAccountName(),
                request.beneficiaryAccountNumber(),
                request.beneficiaryBankVerificationNumber(),
                request.beneficiaryKycLevel(),
                request.originatorAccountName(),
                request.originatorAccountNumber(),
                request.originatorBankVerificationNumber(),
                request.originatorKycLevel(),
                request.transactionLocation(),
                request.narration(),
                request.paymentReference(),
                request.amount(),
                operationResponseCode(behavior.scenario())
        );
    }

    public DebitAdviceResponse debitAdvice(DebitAdviceRequest request, String requestedScenario) {
        SimulationBehavior behavior = operationSimulator.simulate(
                PROVIDER_ID, "fund-transfer-advice-direct-debit", request.sessionId(), requestedScenario
        );
        return new DebitAdviceResponse(
                request.sessionId(),
                request.nameEnquiryRef(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.debitAccountName(),
                request.debitAccountNumber(),
                request.debitBankVerificationNumber(),
                request.debitKycLevel(),
                request.beneficiaryAccountName(),
                request.beneficiaryAccountNumber(),
                request.beneficiaryBankVerificationNumber(),
                request.beneficiaryKycLevel(),
                request.transactionLocation(),
                request.narration(),
                request.paymentReference(),
                request.mandateReferenceNumber(),
                request.transactionFee(),
                request.amount(),
                operationResponseCode(behavior.scenario())
        );
    }

    private TransferSimulationResult simulateTransfer(
            String operation,
            String sessionId,
            BigDecimal amount,
            String requestedScenario
    ) {
        return transferService.simulate(new TransferCommand(
                PROVIDER_ID,
                operation,
                sessionId,
                amount,
                null
        ), requestedScenario);
    }

    private boolean isSuccess(SimulationScenario scenario) {
        return scenario == SimulationScenario.SUCCESS || scenario == SimulationScenario.DELAYED_SUCCESS;
    }

    private String operationResponseCode(SimulationScenario scenario) {
        if (scenario == SimulationScenario.TIMEOUT) {
            return "97";
        }
        return isSuccess(scenario) ? "00" : "96";
    }

    private String transferResponseCode(TransferSimulationResult result) {
        if (result.duplicate()) {
            return "94";
        }
        if (result.timeout()) {
            return "97";
        }
        return switch (result.status()) {
            case SUCCESS -> "00";
            case FAILED -> "96";
            case PENDING -> "09";
        };
    }

    private String transactionResponseCode(SimulatedTransaction transaction) {
        return switch (transaction.status()) {
            case SUCCESS -> "00";
            case FAILED -> "96";
            case PENDING -> "09";
        };
    }
}