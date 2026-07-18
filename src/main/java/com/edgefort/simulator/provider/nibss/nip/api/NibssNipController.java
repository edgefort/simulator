package com.edgefort.simulator.provider.nibss.nip.api;

import com.edgefort.simulator.provider.nibss.nip.application.NibssNipSimulationAdapter;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.BalanceEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.BalanceEnquiryResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditAdviceRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditAdviceResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitAdviceRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitAdviceResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitTransferResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.TransactionStatusQueryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.TransactionStatusQueryResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/nip/v9.4", produces = MediaType.APPLICATION_XML_VALUE)
@Tag(name = "NIBSS NIP v9.4", description = "NIP v9.4 XML message operations documented in the supplied specification")
public class NibssNipController {

    private final NibssNipSimulationAdapter adapter;

    public NibssNipController(NibssNipSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping(path = "/nameenquirysingle", consumes = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<NameEnquiryResponse> nameEnquiry(
            @Valid @RequestBody NameEnquiryRequest request,
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
        NameEnquiryResponse response = adapter.nameEnquiry(request, scenario);
        return response(response.responseCode(), response);
    }

    @PostMapping(path = "/fundtransfersingleitem_dc", consumes = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<CreditTransferResponse> creditTransfer(
            @Valid @RequestBody CreditTransferRequest request,
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
        CreditTransferResponse response = adapter.creditTransfer(request, scenario);
        return response(response.responseCode(), response);
    }

    @PostMapping(path = "/fundtransfersingleitem_dd", consumes = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<DebitTransferResponse> debitTransfer(
            @Valid @RequestBody DebitTransferRequest request,
            @RequestHeader(value = "X-Simulation-Scenario", defaultValue = "success") String scenario
    ) {
        DebitTransferResponse response = adapter.debitTransfer(request, scenario);
        return response(response.responseCode(), response);
    }

    @PostMapping(path = "/txnstatusquerysingleitem", consumes = MediaType.APPLICATION_XML_VALUE)
    TransactionStatusQueryResponse transactionStatus(@Valid @RequestBody TransactionStatusQueryRequest request) {
        return adapter.transactionStatus(request);
    }

    @PostMapping(path = "/balanceenquiry", consumes = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<BalanceEnquiryResponse> balanceEnquiry(
            @Valid @RequestBody BalanceEnquiryRequest request,
            @RequestHeader(value = "X-Simulation-Scenario", defaultValue = "success") String scenario
    ) {
        BalanceEnquiryResponse response = adapter.balanceEnquiry(request, scenario);
        return response(response.responseCode(), response);
    }

    @PostMapping(path = "/fundtransferAdvice_dc", consumes = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<CreditAdviceResponse> creditAdvice(
            @Valid @RequestBody CreditAdviceRequest request,
            @RequestHeader(value = "X-Simulation-Scenario", defaultValue = "success") String scenario
    ) {
        CreditAdviceResponse response = adapter.creditAdvice(request, scenario);
        return response(response.responseCode(), response);
    }

    @PostMapping(path = "/fundtransferAdvice_dd", consumes = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<DebitAdviceResponse> debitAdvice(
            @Valid @RequestBody DebitAdviceRequest request,
            @RequestHeader(value = "X-Simulation-Scenario", defaultValue = "success") String scenario
    ) {
        DebitAdviceResponse response = adapter.debitAdvice(request, scenario);
        return response(response.responseCode(), response);
    }

    private <T> ResponseEntity<T> response(String responseCode, T body) {
        HttpStatus status = "97".equals(responseCode) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.OK;
        return ResponseEntity.status(status).body(body);
    }
}