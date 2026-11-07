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
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NIP v9.4 XML messages. Mappings deliberately declare no {@code consumes}/{@code produces}: a mapping-level media
 * type mismatch would be rejected before a NIBSS handler is selected and could not be answered in NIP form.
 * {@link NibssNipWebConfiguration} enforces {@code application/xml} requests and every response is XML.
 */
@RestController
@RequestMapping("/nip/v9.4")
@Tag(name = "NIBSS NIP v9.4", description = "NIP v9.4 XML message operations documented in the supplied specification")
public class NibssNipController {

    private final NibssNipSimulationAdapter adapter;

    public NibssNipController(NibssNipSimulationAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping("/nameenquirysingle")
    ResponseEntity<NameEnquiryResponse> nameEnquiry(@Valid @RequestBody NameEnquiryRequest request) {
        return xml(adapter.nameEnquiry(request));
    }

    @PostMapping("/fundtransfersingleitem_dc")
    ResponseEntity<CreditTransferResponse> creditTransfer(@Valid @RequestBody CreditTransferRequest request) {
        return xml(adapter.creditTransfer(request));
    }

    @PostMapping("/fundtransfersingleitem_dd")
    ResponseEntity<DebitTransferResponse> debitTransfer(@Valid @RequestBody DebitTransferRequest request) {
        return xml(adapter.debitTransfer(request));
    }

    @PostMapping("/txnstatusquerysingleitem")
    ResponseEntity<TransactionStatusQueryResponse> transactionStatus(
            @Valid @RequestBody TransactionStatusQueryRequest request
    ) {
        return xml(adapter.transactionStatus(request));
    }

    @PostMapping("/balanceenquiry")
    ResponseEntity<BalanceEnquiryResponse> balanceEnquiry(@Valid @RequestBody BalanceEnquiryRequest request) {
        return xml(adapter.balanceEnquiry(request));
    }

    @PostMapping("/fundtransferAdvice_dc")
    ResponseEntity<CreditAdviceResponse> creditAdvice(@Valid @RequestBody CreditAdviceRequest request) {
        return xml(adapter.creditAdvice(request));
    }

    @PostMapping("/fundtransferAdvice_dd")
    ResponseEntity<DebitAdviceResponse> debitAdvice(@Valid @RequestBody DebitAdviceRequest request) {
        return xml(adapter.debitAdvice(request));
    }

    static <T> ResponseEntity<T> xml(T body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(body);
    }
}
