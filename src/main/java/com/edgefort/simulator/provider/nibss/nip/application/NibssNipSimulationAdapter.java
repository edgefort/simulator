package com.edgefort.simulator.provider.nibss.nip.application;

import com.edgefort.simulator.core.application.ProviderOperationSimulator;
import com.edgefort.simulator.core.application.TransactionCommand;
import com.edgefort.simulator.core.application.TransactionSimulationResult;
import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.TransactionStatus;
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
import java.util.Map;

import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.BALANCE_ENQUIRY;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.DIRECT_CREDIT_ADVICE;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.DIRECT_CREDIT_TRANSFER;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.DIRECT_DEBIT_ADVICE;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.DIRECT_DEBIT_TRANSFER;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.ID;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.NAME_ENQUIRY;
import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.APPROVED;
import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.DUPLICATE_TRANSACTION;
import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.REQUEST_PROCESSING_IN_PROGRESS;
import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.SYSTEM_MALFUNCTION;
import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.UNABLE_TO_LOCATE_RECORD;

/**
 * Maps NIP v9.4 messages to the provider-neutral simulation core. {@code SessionID} is the transaction reference;
 * simulation attributes use the NIP element names.
 */
@Service
public class NibssNipSimulationAdapter {

    private static final String SIMULATED_ACCOUNT_NAME = "SIMULATED ACCOUNT";
    private static final String SIMULATED_BANK_VERIFICATION_NUMBER = "00000000000";
    private static final String SIMULATED_KYC_LEVEL = "1";
    private static final BigDecimal SIMULATED_AVAILABLE_BALANCE = new BigDecimal("1000.00");

    private final ProviderOperationSimulator operationSimulator;
    private final TransactionSimulationService transactionService;

    public NibssNipSimulationAdapter(
            ProviderOperationSimulator operationSimulator,
            TransactionSimulationService transactionService
    ) {
        this.operationSimulator = operationSimulator;
        this.transactionService = transactionService;
    }

    public NameEnquiryResponse nameEnquiry(NameEnquiryRequest request) {
        String responseCode = simulateOperation(NAME_ENQUIRY, request.sessionId(), Map.of(
                "DestinationInstitutionCode", request.destinationInstitutionCode(),
                "ChannelCode", request.channelCode(),
                "AccountNumber", request.accountNumber()
        ));
        boolean approved = APPROVED.equals(responseCode);
        return new NameEnquiryResponse(
                request.sessionId(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.accountNumber(),
                approved ? SIMULATED_ACCOUNT_NAME : null,
                approved ? SIMULATED_BANK_VERIFICATION_NUMBER : null,
                approved ? SIMULATED_KYC_LEVEL : null,
                responseCode
        );
    }

    public CreditTransferResponse creditTransfer(CreditTransferRequest request) {
        TransactionSimulationResult result = transactionService.simulate(new TransactionCommand(
                ID, DIRECT_CREDIT_TRANSFER, request.sessionId(), request.amount(), Map.of(
                        "DestinationInstitutionCode", request.destinationInstitutionCode(),
                        "ChannelCode", request.channelCode(),
                        "BeneficiaryAccountNumber", request.beneficiaryAccountNumber(),
                        "OriginatorAccountNumber", request.originatorAccountNumber(),
                        "Amount", request.amount().toPlainString()
                )
        ));
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

    public DebitTransferResponse debitTransfer(DebitTransferRequest request) {
        TransactionSimulationResult result = transactionService.simulate(new TransactionCommand(
                ID, DIRECT_DEBIT_TRANSFER, request.sessionId(), request.amount(), Map.of(
                        "DestinationInstitutionCode", request.destinationInstitutionCode(),
                        "ChannelCode", request.channelCode(),
                        "DebitAccountNumber", request.debitAccountNumber(),
                        "BeneficiaryAccountNumber", request.beneficiaryAccountNumber(),
                        "MandateReferenceNumber", request.mandateReferenceNumber(),
                        "Amount", request.amount().toPlainString()
                )
        ));
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
        String responseCode = transactionService.find(ID, request.sessionId())
                .map(transaction -> statusResponseCode(transaction.status()))
                .orElse(UNABLE_TO_LOCATE_RECORD);
        return new TransactionStatusQueryResponse(
                request.sourceInstitutionCode(), request.channelCode(), request.sessionId(), responseCode
        );
    }

    public BalanceEnquiryResponse balanceEnquiry(BalanceEnquiryRequest request) {
        String responseCode = simulateOperation(BALANCE_ENQUIRY, request.sessionId(), Map.of(
                "DestinationInstitutionCode", request.destinationInstitutionCode(),
                "ChannelCode", request.channelCode(),
                "TargetAccountNumber", request.targetAccountNumber()
        ));
        return new BalanceEnquiryResponse(
                request.sessionId(),
                request.destinationInstitutionCode(),
                request.channelCode(),
                request.authorizationCode(),
                request.targetAccountName(),
                request.targetBankVerificationNumber(),
                request.targetAccountNumber(),
                APPROVED.equals(responseCode) ? SIMULATED_AVAILABLE_BALANCE : null,
                responseCode
        );
    }

    public CreditAdviceResponse creditAdvice(CreditAdviceRequest request) {
        String responseCode = simulateOperation(DIRECT_CREDIT_ADVICE, request.sessionId(), Map.of(
                "DestinationInstitutionCode", request.destinationInstitutionCode(),
                "ChannelCode", request.channelCode(),
                "BeneficiaryAccountNumber", request.beneficiaryAccountNumber(),
                "OriginatorAccountNumber", request.originatorAccountNumber(),
                "Amount", request.amount().toPlainString()
        ));
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
                responseCode
        );
    }

    public DebitAdviceResponse debitAdvice(DebitAdviceRequest request) {
        String responseCode = simulateOperation(DIRECT_DEBIT_ADVICE, request.sessionId(), Map.of(
                "DestinationInstitutionCode", request.destinationInstitutionCode(),
                "ChannelCode", request.channelCode(),
                "DebitAccountNumber", request.debitAccountNumber(),
                "BeneficiaryAccountNumber", request.beneficiaryAccountNumber(),
                "MandateReferenceNumber", request.mandateReferenceNumber(),
                "Amount", request.amount().toPlainString()
        ));
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
                responseCode
        );
    }

    private String simulateOperation(String operation, String sessionId, Map<String, String> attributes) {
        SimulationOutcome outcome = operationSimulator.simulate(ID, operation, sessionId, attributes);
        return switch (outcome) {
            case SUCCESS -> APPROVED;
            case FAILURE -> SYSTEM_MALFUNCTION;
            case PENDING, TIMEOUT -> throw new IllegalStateException(
                    "Stateless NIP operation %s cannot complete as %s".formatted(operation, outcome)
            );
        };
    }

    private String transferResponseCode(TransactionSimulationResult result) {
        if (result.duplicate()) {
            return DUPLICATE_TRANSACTION;
        }
        return statusResponseCode(result.transaction().status());
    }

    private String statusResponseCode(TransactionStatus status) {
        return switch (status) {
            case SUCCESS -> APPROVED;
            case PENDING -> REQUEST_PROCESSING_IN_PROGRESS;
            case FAILED -> SYSTEM_MALFUNCTION;
            case REVERSED -> throw new IllegalStateException(
                    "NIP declares no reversed status; " + ID + " must not be assigned a reversing profile"
            );
        };
    }
}
