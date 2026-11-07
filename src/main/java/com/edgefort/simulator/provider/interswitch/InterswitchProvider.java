package com.edgefort.simulator.provider.interswitch;

import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.SimulatedProvider;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Interswitch Quickteller Service v5: one API host with shared OAuth, {@code TerminalId}, and one
 * {@code GET /Transactions?requestRef=} query, so Send Money and Bills Payment share one transaction keyspace.
 *
 * <p>Transfers and bill payments answer {@code 90000} (SUCCESSFUL), a failure code (FAILED), or {@code 90009}
 * "Request in progress" (PENDING). The shared query reports {@code Completed}, {@code Pending}, {@code Failed}, and
 * {@code Reversed}, so every transaction status is reportable. Name enquiry and customer validation keep no state and
 * have no pending code.</p>
 */
@Component
public class InterswitchProvider implements SimulatedProvider {

    public static final String ID = "interswitch";

    public static final String NAME_ENQUIRY = "name-enquiry";
    public static final String TRANSFER = "transfer";
    public static final String CUSTOMER_VALIDATION = "customer-validation";
    public static final String BILL_PAYMENT = "bill-payment";

    private static final Set<SimulationOutcome> TRANSACTION_OUTCOMES = Set.of(
            SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.PENDING, SimulationOutcome.TIMEOUT
    );
    private static final Set<TransactionStatus> QUERY_STATUSES = Set.of(
            TransactionStatus.PENDING, TransactionStatus.SUCCESS, TransactionStatus.FAILED, TransactionStatus.REVERSED
    );

    private static final List<ProviderOperation> OPERATIONS = List.of(
            ProviderOperation.stateless(
                    NAME_ENQUIRY,
                    Set.of("bankCode", "accountId"),
                    SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
            ),
            ProviderOperation.transactional(
                    TRANSFER,
                    Set.of(
                            "initiatingEntityCode",
                            "initiation.amount",
                            "termination.amount",
                            "termination.entityCode",
                            "termination.accountReceivable.accountNumber"
                    ),
                    TRANSACTION_OUTCOMES,
                    QUERY_STATUSES
            ),
            ProviderOperation.stateless(
                    CUSTOMER_VALIDATION,
                    Set.of("PaymentCode", "CustomerId"),
                    SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
            ),
            ProviderOperation.transactional(
                    BILL_PAYMENT,
                    Set.of("paymentCode", "customerId", "customerMobile", "amount"),
                    TRANSACTION_OUTCOMES,
                    QUERY_STATUSES
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
