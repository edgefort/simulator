package com.edgefort.simulator.provider.interswitch.common;

import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.provider.interswitch.InterswitchApiException;
import com.edgefort.simulator.provider.interswitch.InterswitchProvider;
import com.edgefort.simulator.provider.interswitch.billpayment.application.InterswitchBillPaymentSimulationAdapter;
import com.edgefort.simulator.provider.interswitch.transfer.application.InterswitchTransferSimulationAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single Quickteller v5 Query Transaction endpoint shared by Send Money and Bills Payment.
 */
@RestController
@RequestMapping("/quicktellerservice/api/v5")
@Tag(name = "Interswitch Query Transaction v5", description = "Quickteller Service v5 transaction query")
@SecurityRequirement(name = "InterswitchBearerToken")
public class InterswitchTransactionQueryController {

    private final TransactionSimulationService transactionService;
    private final InterswitchTransferSimulationAdapter transfers;
    private final InterswitchBillPaymentSimulationAdapter billPayments;

    public InterswitchTransactionQueryController(
            TransactionSimulationService transactionService,
            InterswitchTransferSimulationAdapter transfers,
            InterswitchBillPaymentSimulationAdapter billPayments
    ) {
        this.transactionService = transactionService;
        this.transfers = transfers;
        this.billPayments = billPayments;
    }

    @Operation(summary = "Query transaction by request reference")
    @GetMapping("/Transactions")
    Object query(@RequestParam("requestRef") String requestReference) {
        SimulatedTransaction transaction = transactionService.find(InterswitchProvider.ID, requestReference)
                .orElseThrow(() -> new InterswitchApiException(
                        HttpStatus.BAD_REQUEST, "70018", "Transaction Reference not found"
                ));
        return switch (transaction.operation()) {
            case InterswitchProvider.TRANSFER -> transfers.query(transaction);
            case InterswitchProvider.BILL_PAYMENT -> billPayments.query(transaction);
            default -> throw new IllegalStateException(
                    "Operation %s does not record Interswitch transactions".formatted(transaction.operation())
            );
        };
    }
}
