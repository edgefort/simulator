package com.edgefort.simulator.provider.onafriq.billpayment;

import com.edgefort.simulator.core.provider.ProviderOperation;
import com.edgefort.simulator.core.provider.SimulatedProvider;
import com.edgefort.simulator.core.scenario.SimulationOutcome;
import com.edgefort.simulator.core.state.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Onafriq (Baxi) Biller Aggregation Platform bill-payment module.
 *
 * <p>Purchases answer {@code 200 success}, {@code 200 pending} (EXC00114), or {@code BX0022} failure, and the
 * transaction query/requery reports the same three states. The specification has no reversed representation, so
 * REVERSED is not declared. Name validations fail with {@code BX0025}; bundle and bouquet lookups have no documented
 * failure code and only support success and timeout.</p>
 */
@Component
public class OnafriqBillPaymentProvider implements SimulatedProvider {

    public static final String ID = "onafriq-bill-payment";

    public static final String AIRTIME = "airtime";
    public static final String DATA_BUNDLE = "data-bundle";
    public static final String CABLE_TV = "cable-tv";
    public static final String ELECTRICITY = "electricity";
    public static final String EPIN = "epin";
    public static final String JAMB = "jamb";
    public static final String BETTING = "betting";
    public static final String VEHICLE_INSURANCE = "vehicle-insurance";

    public static final String NAME_FINDER = "name-finder";
    public static final String ELECTRICITY_VERIFY = "electricity-verify";
    public static final String BETTING_VALIDATE = "betting-validate";
    public static final String DATA_BUNDLES = "data-bundles";
    public static final String EPIN_BUNDLES = "epin-bundles";
    public static final String CABLE_TV_BOUQUETS = "cable-tv-bouquets";
    public static final String CABLE_TV_ADDONS = "cable-tv-addons";

    private static final Set<SimulationOutcome> PURCHASE_OUTCOMES = Set.of(
            SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.PENDING, SimulationOutcome.TIMEOUT
    );
    private static final Set<TransactionStatus> QUERY_STATUSES = Set.of(
            TransactionStatus.PENDING, TransactionStatus.SUCCESS, TransactionStatus.FAILED
    );

    private static final List<ProviderOperation> OPERATIONS = List.of(
            purchase(AIRTIME, "service_type", "plan", "phone", "amount", "agentReference"),
            purchase(DATA_BUNDLE, "service_type", "datacode", "phone", "amount", "agentReference"),
            purchase(CABLE_TV, "service_type", "smartcard_number", "product_code", "total_amount", "agentReference"),
            purchase(ELECTRICITY, "service_type", "account_number", "phone", "amount", "agentReference"),
            purchase(EPIN, "service_type", "amount", "pinValue", "numberOfPins", "agentReference"),
            purchase(JAMB, "service_type", "account_number", "product_code", "phone", "amount", "agentReference"),
            purchase(BETTING, "service_type", "account_number", "action", "amount", "agentReference"),
            purchase(VEHICLE_INSURANCE, "service_type", "client.phone", "vehicle.registrationNumber",
                    "order.amount", "agentReference"),
            validation(NAME_FINDER, "service_type", "account_number", "product_code"),
            validation(ELECTRICITY_VERIFY, "service_type", "account_number"),
            validation(BETTING_VALIDATE, "service_type", "account_number"),
            lookup(DATA_BUNDLES, "service_type", "account_number"),
            lookup(EPIN_BUNDLES, "service_type"),
            lookup(CABLE_TV_BOUQUETS, "service_type"),
            lookup(CABLE_TV_ADDONS, "service_type", "product_code")
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<ProviderOperation> operations() {
        return OPERATIONS;
    }

    private static ProviderOperation purchase(String name, String... matchFields) {
        return ProviderOperation.transactional(name, Set.of(matchFields), PURCHASE_OUTCOMES, QUERY_STATUSES);
    }

    private static ProviderOperation validation(String name, String... matchFields) {
        return ProviderOperation.stateless(
                name, Set.of(matchFields),
                SimulationOutcome.SUCCESS, SimulationOutcome.FAILURE, SimulationOutcome.TIMEOUT
        );
    }

    private static ProviderOperation lookup(String name, String... matchFields) {
        return ProviderOperation.stateless(name, Set.of(matchFields), SimulationOutcome.SUCCESS, SimulationOutcome.TIMEOUT);
    }
}
