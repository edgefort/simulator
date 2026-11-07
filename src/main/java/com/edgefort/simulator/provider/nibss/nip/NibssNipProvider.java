package com.edgefort.simulator.provider.nibss.nip;

import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.SimulatedProvider;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * NIBSS NIP operations and what their documented response codes can express.
 *
 * <p>Transfers are transactional and observed through the transaction status query (016/017), which reports
 * {@code 00}, {@code 09}, or {@code 96}. NIP has no documented status-query code for a reversed transaction, so
 * {@link TransactionStatus#REVERSED} is not declared. Enquiries and advices keep no state and have no processing
 * code, so they cannot be pending.</p>
 *
 * <p>{@value #INWARD_DIRECT_CREDIT_TRANSFER} is the direct credit the simulator, playing NIBSS, sends to an
 * institution. Its behavior decides what the institution's later status query observes: success ({@code 00}),
 * failure ({@code 96}: NIBSS did not accept the institution's response, so the institution must not credit), or
 * pending ({@code 09}, then the profile's final status). The admin call is never dropped, so it has no timeout.</p>
 */
@Component
public class NibssNipProvider implements SimulatedProvider {

    public static final String ID = "nibss-nip";

    public static final String NAME_ENQUIRY = "name-enquiry";
    public static final String DIRECT_CREDIT_TRANSFER = "fund-transfer-direct-credit";
    public static final String DIRECT_DEBIT_TRANSFER = "fund-transfer-direct-debit";
    public static final String BALANCE_ENQUIRY = "balance-enquiry";
    public static final String DIRECT_CREDIT_ADVICE = "fund-transfer-advice-direct-credit";
    public static final String DIRECT_DEBIT_ADVICE = "fund-transfer-advice-direct-debit";
    public static final String INWARD_DIRECT_CREDIT_TRANSFER = "inward-fund-transfer-direct-credit";

    private static final Set<String> CREDIT_MATCH_FIELDS = Set.of(
            "DestinationInstitutionCode",
            "ChannelCode",
            "BeneficiaryAccountNumber",
            "OriginatorAccountNumber",
            "Amount"
    );

    private static final Set<String> DEBIT_MATCH_FIELDS = Set.of(
            "DestinationInstitutionCode",
            "ChannelCode",
            "DebitAccountNumber",
            "BeneficiaryAccountNumber",
            "MandateReferenceNumber",
            "Amount"
    );

    private static final Set<SimulationOutcome> TRANSFER_OUTCOMES = Set.of(
            SimulationOutcome.SUCCESS,
            SimulationOutcome.FAILURE,
            SimulationOutcome.PENDING,
            SimulationOutcome.TIMEOUT
    );

    private static final Set<TransactionStatus> STATUS_QUERY_STATUSES = Set.of(
            TransactionStatus.PENDING,
            TransactionStatus.SUCCESS,
            TransactionStatus.FAILED
    );

    private static final List<ProviderOperation> OPERATIONS = List.of(
            ProviderOperation.stateless(
                    NAME_ENQUIRY,
                    Set.of("DestinationInstitutionCode", "ChannelCode", "AccountNumber"),
                    SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
            ),
            ProviderOperation.transactional(
                    DIRECT_CREDIT_TRANSFER, CREDIT_MATCH_FIELDS, TRANSFER_OUTCOMES, STATUS_QUERY_STATUSES
            ),
            ProviderOperation.transactional(
                    DIRECT_DEBIT_TRANSFER, DEBIT_MATCH_FIELDS, TRANSFER_OUTCOMES, STATUS_QUERY_STATUSES
            ),
            ProviderOperation.stateless(
                    BALANCE_ENQUIRY,
                    Set.of("DestinationInstitutionCode", "ChannelCode", "TargetAccountNumber"),
                    SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
            ),
            ProviderOperation.stateless(
                    DIRECT_CREDIT_ADVICE,
                    CREDIT_MATCH_FIELDS,
                    SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
            ),
            ProviderOperation.stateless(
                    DIRECT_DEBIT_ADVICE,
                    DEBIT_MATCH_FIELDS,
                    SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
            ),
            ProviderOperation.transactional(
                    INWARD_DIRECT_CREDIT_TRANSFER,
                    CREDIT_MATCH_FIELDS,
                    Set.of(SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.PENDING),
                    STATUS_QUERY_STATUSES
            )
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ProviderOperation> operations() {
        return OPERATIONS;
    }
}
