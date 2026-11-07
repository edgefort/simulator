package com.edgefort.simulator.provider.onafriq.billpayment.api;

import com.edgefort.simulator.provider.onafriq.billpayment.OnafriqBillPaymentProvider;
import com.edgefort.simulator.provider.onafriq.billpayment.application.OnafriqBillPaymentSimulationAdapter;
import com.edgefort.simulator.provider.onafriq.billpayment.application.OnafriqBillPaymentSimulationAdapter.Result;
import com.edgefort.simulator.provider.onafriq.billpayment.application.OnafriqSampleData;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.AddonsRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.AirtimeRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.BettingRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.BettingValidateRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.BouquetListRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.CableTvRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.DataBundleRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.DataBundlesRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.ElectricityRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.ElectricityVerifyRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.EpinRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.ExamPinPurchaseRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.NameFinderRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.Response;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.ServiceTypeRequest;
import com.edgefort.simulator.provider.onafriq.billpayment.contract.OnafriqBillPaymentContracts.VehicleInsuranceRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Onafriq Biller Aggregation Platform routes, at the paths of the exported OpenAPI document (server
 * {@code https://api.staging.baxibap.com/services}).
 */
@RestController
@RequestMapping("/services")
@Tag(name = "Onafriq B2B VAS", description = "Onafriq Biller Aggregation Platform bill-payment operations")
@SecurityRequirement(name = "OnafriqApiKey")
public class OnafriqBillPaymentController {

    private final OnafriqBillPaymentSimulationAdapter adapter;

    public OnafriqBillPaymentController(OnafriqBillPaymentSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @GetMapping("/superagent/account/balance")
    Response balance() {
        return Response.success(OnafriqSampleData.balance());
    }

    @GetMapping("/superagent/transaction/query")
    ResponseEntity<Response> query(@RequestParam("agentReference") String agentReference) {
        return response(adapter.requery(agentReference));
    }

    @GetMapping("/superagent/transaction/requery")
    ResponseEntity<Response> requery(@RequestParam("agentReference") String agentReference) {
        return response(adapter.requery(agentReference));
    }

    @GetMapping("/billers/category/all")
    Response categories() {
        return Response.success(OnafriqSampleData.categories());
    }

    @GetMapping("/billers/services/list")
    Response services() {
        return Response.success(OnafriqSampleData.servicesByCategory());
    }

    @PostMapping("/billers/services/category")
    Response servicesByCategory(@Valid @RequestBody ServiceTypeRequest request) {
        return Response.success(OnafriqSampleData.services(request.serviceType()));
    }

    @GetMapping("/airtime/providers")
    Response airtimeProviders() {
        return Response.success(OnafriqSampleData.airtimeProviders());
    }

    @PostMapping("/airtime/request")
    ResponseEntity<Response> airtime(@Valid @RequestBody AirtimeRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.AIRTIME, request));
    }

    @GetMapping("/databundle/providers")
    Response dataBundleProviders() {
        return Response.success(OnafriqSampleData.dataBundleProviders());
    }

    @PostMapping("/databundle/bundles")
    ResponseEntity<Response> dataBundles(@Valid @RequestBody DataBundlesRequest request) {
        return response(adapter.lookup(
                OnafriqBillPaymentProvider.DATA_BUNDLES, request.attributes(), OnafriqSampleData::dataBundles
        ));
    }

    @PostMapping("/databundle/request")
    ResponseEntity<Response> dataBundle(@Valid @RequestBody DataBundleRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.DATA_BUNDLE, request));
    }

    @GetMapping("/cabletv/providers")
    Response cableTvProviders() {
        return Response.success(OnafriqSampleData.cableTvProviders());
    }

    @PostMapping("/multichoice/list")
    ResponseEntity<Response> bouquets(@Valid @RequestBody(required = false) BouquetListRequest request) {
        Map<String, String> attributes = request == null ? Map.of() : request.attributes();
        return response(adapter.lookup(
                OnafriqBillPaymentProvider.CABLE_TV_BOUQUETS, attributes, OnafriqSampleData::bouquets
        ));
    }

    @PostMapping("/multichoice/addons")
    ResponseEntity<Response> addons(@Valid @RequestBody AddonsRequest request) {
        return response(adapter.lookup(
                OnafriqBillPaymentProvider.CABLE_TV_ADDONS, request.attributes(), OnafriqSampleData::addons
        ));
    }

    @PostMapping("/multichoice/request")
    ResponseEntity<Response> cableTv(@Valid @RequestBody CableTvRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.CABLE_TV, request));
    }

    @GetMapping("/electricity/billers")
    Response electricityBillers() {
        return Response.success(OnafriqSampleData.electricityBillers());
    }

    @PostMapping("/electricity/verify")
    ResponseEntity<Response> verifyElectricity(@Valid @RequestBody ElectricityVerifyRequest request) {
        return response(adapter.validation(
                OnafriqBillPaymentProvider.ELECTRICITY_VERIFY,
                request.attributes(),
                () -> OnafriqSampleData.meter(request.accountNumber())
        ));
    }

    @PostMapping("/electricity/request")
    ResponseEntity<Response> electricity(@Valid @RequestBody ElectricityRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.ELECTRICITY, request));
    }

    @GetMapping("/epin/providers")
    Response epinProviders() {
        return Response.success(OnafriqSampleData.epinProviders());
    }

    @PostMapping("/epin/bundles")
    ResponseEntity<Response> epinBundles(@Valid @RequestBody ServiceTypeRequest request) {
        return response(adapter.lookup(
                OnafriqBillPaymentProvider.EPIN_BUNDLES, request.attributes(), OnafriqSampleData::epinBundles
        ));
    }

    @PostMapping("/epin/request")
    ResponseEntity<Response> epin(@Valid @RequestBody EpinRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.EPIN, request));
    }

    @PostMapping("/exampin/purchase")
    ResponseEntity<Response> examPin(@Valid @RequestBody ExamPinPurchaseRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.JAMB, request));
    }

    @GetMapping("/betting/billers")
    Response bettingBillers() {
        return Response.success(OnafriqSampleData.bettingBillers());
    }

    @PostMapping("/betting/validate")
    ResponseEntity<Response> validateBetting(@Valid @RequestBody BettingValidateRequest request) {
        return response(adapter.validation(
                OnafriqBillPaymentProvider.BETTING_VALIDATE,
                request.attributes(),
                () -> OnafriqSampleData.bettingAccount(request.accountNumber())
        ));
    }

    @PostMapping("/betting/request")
    ResponseEntity<Response> betting(@Valid @RequestBody BettingRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.BETTING, request));
    }

    @PostMapping("/namefinder/query")
    ResponseEntity<Response> nameFinder(@Valid @RequestBody NameFinderRequest request) {
        return response(adapter.validation(
                OnafriqBillPaymentProvider.NAME_FINDER,
                request.attributes(),
                () -> OnafriqSampleData.nameFinder(request.serviceType(), request.accountNumber())
        ));
    }

    @PostMapping("/carinsurance/request")
    ResponseEntity<Response> vehicleInsurance(@Valid @RequestBody VehicleInsuranceRequest request) {
        return response(adapter.purchase(OnafriqBillPaymentProvider.VEHICLE_INSURANCE, request));
    }

    private static ResponseEntity<Response> response(Result result) {
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
