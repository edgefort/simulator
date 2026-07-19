package com.edgefort.simulator.provider.onafriq.billpayment.api;

import com.edgefort.simulator.provider.onafriq.billpayment.application.OnafriqBillPaymentSimulationAdapter;
import com.edgefort.simulator.provider.onafriq.billpayment.application.OnafriqBillPaymentSimulationAdapter.Result;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.AccountValidationRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.AirtimeRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.BettingRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.CableTvRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.DataRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.ElectricityRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.EpinRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.JambRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.PurchaseRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.Response;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.ServiceRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.VehicleInsuranceRequest;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/services")
@Tag(name = "Onafriq B2B VAS", description = "Onafriq Biller Aggregation Platform bill-payment operations")
@SecurityRequirement(name = "OnafriqApiKey")
public class OnafriqBillPaymentController {

    private static final String SCENARIO_HEADER = "X-Simulation-Scenario";
    private static final List<Map<String, Object>> CATEGORIES = List.of(
            Map.of("name", "Airtime", "service_type", "airtime"),
            Map.of("name", "Data", "service_type", "data"),
            Map.of("name", "Cable TV", "service_type", "cabletv"),
            Map.of("name", "Electricity", "service_type", "electricity"),
            Map.of("name", "E-Pin", "service_type", "epin"),
            Map.of("name", "Betting", "service_type", "betting"),
            Map.of("name", "Vehicle Insurance", "service_type", "vehicle-insurance")
    );
    private static final List<Map<String, Object>> AIRTIME_PROVIDERS = List.of(
            provider("MTN", "mtn"), provider("Airtel", "airtel"),
            provider("Glo", "glo"), provider("9mobile", "9mobile")
    );
    private static final List<Map<String, Object>> DATA_PROVIDERS = List.of(
            provider("MTN Data", "mtn_data"), provider("Airtel Data", "airtel_data"),
            provider("Glo Data", "glo_data"), provider("9mobile Data", "9mobile_data")
    );
    private static final List<Map<String, Object>> CABLE_TV_PROVIDERS = List.of(
            provider("DStv", "dstv"), provider("GOtv", "gotv"), provider("StarTimes", "startimes")
    );
    private static final List<Map<String, Object>> ELECTRICITY_PROVIDERS = List.of(
            provider("Ikeja Electric Prepaid", "ikeja_electric_prepaid"),
            provider("Ikeja Electric Postpaid", "ikeja_electric_postpaid"),
            provider("Eko Electric Prepaid", "eko_electric_prepaid"),
            provider("Eko Electric Postpaid", "eko_electric_postpaid")
    );
    private static final List<Map<String, Object>> EPIN_PROVIDERS = List.of(
            provider("WAEC Result Checker", "waec"), provider("JAMB", "jamb")
    );
    private static final List<Map<String, Object>> BETTING_PROVIDERS = List.of(
            provider("Bet9ja", "bet9ja"), provider("SportyBet", "sportybet"),
            provider("BetKing", "betking")
    );
    private static final List<Map<String, Object>> VEHICLE_PROVIDERS = List.of(
            provider("Third Party Motor Insurance", "third_party_motor_insurance")
    );

    private final OnafriqBillPaymentSimulationAdapter adapter;

    public OnafriqBillPaymentController(OnafriqBillPaymentSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @GetMapping("/superagent/balance")
    Response<?> balance() {
        return success(Map.of("balance", new BigDecimal("1000000.00"), "currency", "NGN"));
    }

    @GetMapping("/superagent/commission-balance")
    Response<?> commissionBalance() {
        return success(Map.of("balance", new BigDecimal("25000.00"), "currency", "NGN"));
    }

    @GetMapping("/superagent/transaction")
    ResponseEntity<Response<Map<String, Object>>> transaction(
            @RequestParam("reference") String reference
    ) {
        return response(adapter.requery(reference));
    }

    @GetMapping("/superagent/transaction/requery")
    ResponseEntity<Response<Map<String, Object>>> requery(
            @RequestParam("agentReference") String agentReference
    ) {
        return response(adapter.requery(agentReference));
    }

    @GetMapping("/billers/providers/all")
    Response<?> allProviders() {
        return success(List.of(
                AIRTIME_PROVIDERS, DATA_PROVIDERS, CABLE_TV_PROVIDERS,
                ELECTRICITY_PROVIDERS, EPIN_PROVIDERS, BETTING_PROVIDERS, VEHICLE_PROVIDERS
        ).stream().flatMap(List::stream).toList());
    }

    @GetMapping("/billers/category/all")
    Response<?> categories() {
        return success(CATEGORIES);
    }

    @GetMapping("/billers/services/category/{category}")
    Response<?> servicesByCategory(@PathVariable String category) {
        return success(providersFor(category));
    }

    @GetMapping("/billers/services/provider/{provider}")
    Response<?> servicesByProvider(@PathVariable String provider) {
        return success(allProviderEntries().stream()
                .filter(entry -> provider.equals(entry.get("service_type")))
                .toList());
    }

    @GetMapping("/airtime/providers")
    Response<?> airtimeProviders() {
        return success(AIRTIME_PROVIDERS);
    }

    @PostMapping("/airtime/request")
    ResponseEntity<Response<Map<String, Object>>> airtime(
            @Valid @RequestBody AirtimeRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "airtime", scenario);
    }

    @GetMapping("/data/providers")
    Response<?> dataProviders() {
        return success(DATA_PROVIDERS);
    }

    @PostMapping("/data/bundles")
    Response<?> dataBundles(@Valid @RequestBody ServiceRequest request) {
        return success(List.of(
                Map.of("service_type", request.serviceType(), "data_code", "DAILY-100MB", "amount", 100),
                Map.of("service_type", request.serviceType(), "data_code", "MONTHLY-1GB", "amount", 1000)
        ));
    }

    @PostMapping("/data/request")
    ResponseEntity<Response<Map<String, Object>>> data(
            @Valid @RequestBody DataRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "data", scenario);
    }

    @GetMapping("/cabletv/providers")
    Response<?> cableTvProviders() {
        return success(CABLE_TV_PROVIDERS);
    }

    @PostMapping("/cabletv/check-addons")
    Response<?> cableTvAddons(@Valid @RequestBody AccountValidationRequest request) {
        return success(Map.of(
                "serviceType", request.serviceType(),
                "accountNumber", request.accountNumber(),
                "customerName", "SIMULATED CUSTOMER",
                "addons", List.of()
        ));
    }

    @PostMapping("/cabletv/request")
    ResponseEntity<Response<Map<String, Object>>> cableTv(
            @Valid @RequestBody CableTvRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "cable-tv", scenario);
    }

    @GetMapping("/electricity/providers")
    Response<?> electricityProviders() {
        return success(ELECTRICITY_PROVIDERS);
    }

    @PostMapping("/electricity/verify")
    Response<?> verifyElectricity(@Valid @RequestBody AccountValidationRequest request) {
        return account(request);
    }

    @PostMapping("/electricity/request")
    ResponseEntity<Response<Map<String, Object>>> electricity(
            @Valid @RequestBody ElectricityRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "electricity", scenario);
    }

    @GetMapping("/epin/providers")
    Response<?> epinProviders() {
        return success(EPIN_PROVIDERS);
    }

    @PostMapping("/epin/bundles")
    Response<?> epinBundles(@Valid @RequestBody ServiceRequest request) {
        return success(List.of(Map.of(
                "service_type", request.serviceType(), "pinValue", 3500, "pinCount", 1
        )));
    }

    @PostMapping("/epin/request")
    ResponseEntity<Response<Map<String, Object>>> epin(
            @Valid @RequestBody EpinRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "epin", scenario);
    }

    @GetMapping("/epin/jamb-profiles")
    Response<?> jambProfiles() {
        return success(List.of(Map.of(
                "profileId", "UTME", "description", "JAMB UTME profile", "amount", 4700
        )));
    }

    @PostMapping("/epin/jamb-request")
    ResponseEntity<Response<Map<String, Object>>> jamb(
            @Valid @RequestBody JambRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "jamb", scenario);
    }

    @GetMapping("/betting/providers")
    Response<?> bettingProviders() {
        return success(BETTING_PROVIDERS);
    }

    @PostMapping("/betting/verify")
    Response<?> verifyBetting(@Valid @RequestBody AccountValidationRequest request) {
        return account(request);
    }

    @PostMapping("/betting/request")
    ResponseEntity<Response<Map<String, Object>>> betting(
            @Valid @RequestBody BettingRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "betting", scenario);
    }

    @PostMapping("/namefinder/query")
    Response<?> nameFinder(@Valid @RequestBody AccountValidationRequest request) {
        return account(request);
    }

    @GetMapping("/vehicle-insurance/providers")
    Response<?> vehicleInsuranceProviders() {
        return success(VEHICLE_PROVIDERS);
    }

    @PostMapping("/vehicle-insurance/request")
    ResponseEntity<Response<Map<String, Object>>> vehicleInsurance(
            @Valid @RequestBody VehicleInsuranceRequest request,
            @ScenarioHeader @RequestHeader(value = SCENARIO_HEADER, defaultValue = "success") String scenario
    ) {
        return purchase(request, "vehicle-insurance", scenario);
    }

    private ResponseEntity<Response<Map<String, Object>>> purchase(
            PurchaseRequest request,
            String operation,
            String scenario
    ) {
        return response(adapter.purchase(request, operation, scenario));
    }

    private ResponseEntity<Response<Map<String, Object>>> response(Result result) {
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private Response<?> account(AccountValidationRequest request) {
        return success(Map.of(
                "serviceType", request.serviceType(),
                "accountNumber", request.accountNumber(),
                "accountName", "SIMULATED CUSTOMER"
        ));
    }

    private static Response<Object> success(Object data) {
        return new Response<>("success", 200, "Successful", data, List.of());
    }

    private static Map<String, Object> provider(String name, String serviceType) {
        return Map.of("name", name, "service_type", serviceType);
    }

    private List<Map<String, Object>> providersFor(String category) {
        return switch (category) {
            case "airtime" -> AIRTIME_PROVIDERS;
            case "data" -> DATA_PROVIDERS;
            case "cabletv" -> CABLE_TV_PROVIDERS;
            case "electricity" -> ELECTRICITY_PROVIDERS;
            case "epin" -> EPIN_PROVIDERS;
            case "betting" -> BETTING_PROVIDERS;
            case "vehicle-insurance" -> VEHICLE_PROVIDERS;
            default -> List.of();
        };
    }

    private List<Map<String, Object>> allProviderEntries() {
        return List.of(
                AIRTIME_PROVIDERS, DATA_PROVIDERS, CABLE_TV_PROVIDERS,
                ELECTRICITY_PROVIDERS, EPIN_PROVIDERS, BETTING_PROVIDERS, VEHICLE_PROVIDERS
        ).stream().flatMap(List::stream).toList();
    }

    @Parameter(
            description = "Simulation behavior for this request",
            schema = @Schema(
                    defaultValue = "success",
                    allowableValues = {
                            "success", "failure", "pending", "delayed-success",
                            "timeout", "event-timeout", "late-event"
                    }
            )
    )
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ScenarioHeader {
    }
}