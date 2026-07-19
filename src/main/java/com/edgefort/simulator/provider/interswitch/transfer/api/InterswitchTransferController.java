package com.edgefort.simulator.provider.interswitch.transfer.api;

import com.edgefort.simulator.provider.interswitch.billpayment.application.InterswitchBillPaymentSimulationAdapter;
import com.edgefort.simulator.provider.interswitch.transfer.application.InterswitchTransferSimulationAdapter;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.FundTransferBank;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferRequest;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferResponse;
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

import java.util.List;

@RestController
@RequestMapping("/quicktellerservice/api/v5")
@Tag(name = "Interswitch Send Money v5", description = "Quickteller Service Send Money v5 operations")
@SecurityRequirement(name = "InterswitchBearerToken")
public class InterswitchTransferController {

    private static final List<FundTransferBank> FUND_TRANSFER_BANKS = List.of(
            new FundTransferBank("ACCESS BANK", "044", "000014"),
            new FundTransferBank("CITIBANK", "023", "000009"),
            new FundTransferBank("DIAMOND BANK", "063", "000005"),
            new FundTransferBank("ECOBANK NIGERIA", "050", "000010"),
            new FundTransferBank("ENTERPRISE BANK", "084", "000019"),
            new FundTransferBank("FIDELITY BANK", "070", "000007")
    );

    private final InterswitchTransferSimulationAdapter adapter;
    private final InterswitchBillPaymentSimulationAdapter billPaymentAdapter;

    public InterswitchTransferController(
            InterswitchTransferSimulationAdapter adapter,
            InterswitchBillPaymentSimulationAdapter billPaymentAdapter
    ) {
        this.adapter = adapter;
        this.billPaymentAdapter = billPaymentAdapter;
    }

    @GetMapping("/transactions/DoAccountNameInquiry")
    ResponseEntity<NameEnquiryResponse> nameEnquiry(
            @RequestHeader("bankCode") String bankCode,
            @RequestHeader("accountId") String accountId,
            @RequestHeader("TerminalId") String terminalId,
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
        NameEnquiryResponse response = adapter.nameEnquiry(bankCode, accountId, scenario);
        HttpStatus status = "SIMULATOR_TIMEOUT".equals(response.ResponseCode())
                ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/configuration/fundstransferbanks")
    List<FundTransferBank> banks(@RequestHeader("TerminalId") String terminalId) {
        return FUND_TRANSFER_BANKS;
    }

    @PostMapping("/transactions/Transfer")
    ResponseEntity<TransferResponse> transfer(
            @RequestHeader("TerminalId") String terminalId,
            @Valid @RequestBody TransferRequest request,
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
        TransferResponse response = adapter.transfer(request, scenario);
        HttpStatus status = "SIMULATOR_TIMEOUT".equals(response.ResponseCode())
                ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/Transactions")
    ResponseEntity<?> requery(
            @RequestHeader("TerminalId") String terminalId,
            @RequestParam("requestRef") String requestReference
    ) {
        var billPaymentResponse = billPaymentAdapter.query(requestReference);
        if (billPaymentResponse.isPresent()) {
            return ResponseEntity.ok(billPaymentResponse.get());
        }
        return adapter.requery(requestReference)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}