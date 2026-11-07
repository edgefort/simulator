package com.edgefort.simulator.provider.interswitch.transfer.api;

import com.edgefort.simulator.provider.interswitch.transfer.application.InterswitchTransferSimulationAdapter;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.Bank;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.BanksResponse;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferRequest;
import com.edgefort.simulator.provider.interswitch.transfer.contract.InterswitchTransferContracts.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quickteller Service v5 Send Money. Bearer authorization and {@code TerminalId} are enforced by the provider
 * interceptor; transaction query is served by the shared {@code GET /Transactions} handler.
 */
@RestController
@RequestMapping("/quicktellerservice/api/v5")
@Tag(name = "Interswitch Send Money v5", description = "Quickteller Service Send Money v5 operations")
@SecurityRequirement(name = "InterswitchBearerToken")
public class InterswitchTransferController {

    private static final BanksResponse FUND_TRANSFER_BANKS = new BanksResponse(List.of(
            new Bank("31", "044", "Access Bank Nigeria Plc", "ABP"),
            new Bank("101", "056", "Ecobank Nigeria 2", "ECO2"),
            new Bank("128", "085", "First Inland Bank Plc", "FIB"),
            new Bank("10", "058", "Guaranty Trust Bank Plc", "GTB"),
            new Bank("121", "232", "Sterling Bank Plc", "SBP"),
            new Bank("117", "057", "Zenith Bank International", "ZIB")
    ));

    private final InterswitchTransferSimulationAdapter adapter;

    public InterswitchTransferController(InterswitchTransferSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @Operation(summary = "Name enquiry (Single Transfer guide: GET /transactions/DoAccountNameInquiry)")
    @GetMapping("/transactions/DoAccountNameInquiry")
    NameEnquiryResponse nameEnquiry(
            @RequestHeader("bankCode") String bankCode,
            @RequestHeader("accountId") String accountId
    ) {
        return adapter.nameEnquiry(bankCode, accountId);
    }

    @Operation(summary = "Name enquiry (API reference: POST /Transactions/DoAccountNameInquiry)")
    @PostMapping("/Transactions/DoAccountNameInquiry")
    NameEnquiryResponse nameEnquiryByReference(
            @RequestHeader("bankCode") String bankCode,
            @RequestHeader("accountId") String accountId
    ) {
        return adapter.nameEnquiry(bankCode, accountId);
    }

    @Operation(summary = "Get bank codes")
    @GetMapping("/configuration/fundstransferbanks")
    BanksResponse banks() {
        return FUND_TRANSFER_BANKS;
    }

    @Operation(summary = "Single transfer")
    @PostMapping("/transactions/TransferFunds")
    TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return adapter.transfer(request);
    }
}
