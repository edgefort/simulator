package com.edgefort.simulator.provider.interswitch.billpayment.api;

import com.edgefort.simulator.provider.interswitch.billpayment.application.InterswitchBillPaymentSimulationAdapter;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.BillersResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CategoriesResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerValidationRequest;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.CustomerValidationResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentItemsResponse;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentRequest;
import com.edgefort.simulator.provider.interswitch.billpayment.contract.InterswitchBillPaymentContracts.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quickteller Service v5 Bills Payment and Airtime. Bearer authorization and {@code TerminalId} are enforced by the
 * provider interceptor; transaction query is served by the shared {@code GET /Transactions} handler.
 */
@RestController
@RequestMapping("/quicktellerservice/api/v5")
@Tag(name = "Interswitch Bills Payment v5", description = "Quickteller bills payment and airtime recharge operations")
@SecurityRequirement(name = "InterswitchBearerToken")
public class InterswitchBillPaymentController {

    private static final String TERMINAL_ID = "TerminalId";

    private final InterswitchBillPaymentSimulationAdapter adapter;

    public InterswitchBillPaymentController(InterswitchBillPaymentSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @Operation(summary = "Get categories")
    @GetMapping("/services/categories")
    CategoriesResponse categories() {
        return adapter.categories();
    }

    @Operation(summary = "Get billers, optionally for one category")
    @GetMapping("/services")
    BillersResponse billers(@RequestParam(value = "categoryId", required = false) Integer categoryId) {
        return adapter.billers(categoryId);
    }

    @Operation(summary = "Get payment items")
    @GetMapping("/services/options")
    PaymentItemsResponse paymentItems(@RequestParam("serviceid") int serviceId) {
        return adapter.paymentItems(serviceId);
    }

    @Operation(summary = "Validate customers")
    @PostMapping("/Transactions/validatecustomers")
    CustomerValidationResponse validateCustomers(
            @RequestHeader(TERMINAL_ID) String terminalId,
            @Valid @RequestBody CustomerValidationRequest request
    ) {
        return adapter.validateCustomers(request, terminalId);
    }

    @Operation(summary = "Make payment (bills payment or airtime recharge)")
    @PostMapping({"/Transactions", "/transactions"})
    PaymentResponse pay(
            @RequestHeader(TERMINAL_ID) String terminalId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return adapter.pay(request, terminalId);
    }
}
