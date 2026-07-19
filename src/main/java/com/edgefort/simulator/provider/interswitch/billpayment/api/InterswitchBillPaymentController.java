package com.edgefort.simulator.provider.interswitch.billpayment.api;

import com.edgefort.simulator.provider.interswitch.billpayment.application.InterswitchBillPaymentSimulationAdapter;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.BillersResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CategoriesResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerValidationRequest;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerValidationResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentItemsResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentRequest;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/quicktellerservice/api/v5")
@Tag(name = "Interswitch Bills Payment v5", description = "Quickteller bills payment and airtime recharge operations")
@SecurityRequirement(name = "InterswitchBearerToken")
public class InterswitchBillPaymentController {

    private final InterswitchBillPaymentSimulationAdapter adapter;

    public InterswitchBillPaymentController(InterswitchBillPaymentSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @GetMapping("/services/categories")
    CategoriesResponse categories(@RequestHeader("TerminalId") String terminalId) {
        return adapter.categories();
    }

    @GetMapping("/services")
    BillersResponse billers(
            @RequestHeader("TerminalId") String terminalId,
            @RequestParam("categoryId") int categoryId
    ) {
        return adapter.billers(categoryId);
    }

    @GetMapping("/services/options")
    PaymentItemsResponse paymentItems(
            @RequestHeader("TerminalId") String terminalId,
            @RequestParam("serviceid") int serviceId
    ) {
        return adapter.paymentItems(serviceId);
    }

    @PostMapping("/Transactions/validatecustomers")
    CustomerValidationResponse validateCustomers(
            @RequestHeader("TerminalId") String terminalId,
            @Valid @RequestBody CustomerValidationRequest request
    ) {
        if (!terminalId.equals(request.TerminalId())) {
            throw new IllegalArgumentException("TerminalId header and request body must match");
        }
        return adapter.validateCustomers(request);
    }

    @PostMapping("/Transactions")
    ResponseEntity<PaymentResponse> pay(
            @RequestHeader("TerminalId") String terminalId,
            @Valid @RequestBody PaymentRequest request,
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
            @RequestHeader(value = "X-Simulation-Scenario", defaultValue = "success") String scenario
    ) {
        if (!terminalId.equals(request.TerminalId())) {
            throw new IllegalArgumentException("TerminalId header and request body must match");
        }
        PaymentResponse response = adapter.pay(request, terminalId, scenario);
        HttpStatus status = "SIMULATOR_TIMEOUT".equals(response.ResponseCode())
                ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}