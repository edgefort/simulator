package com.edgefort.simulator.provider.nibss.nip.api;

import com.edgefort.simulator.provider.nibss.nip.application.NibssNipSimulationAdapter;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.BalanceEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditAdviceRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitAdviceRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.DebitTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.TransactionStatusQueryRequest;
import com.edgefort.simulator.provider.nibss.nip.security.NibssNipKeyStore;
import com.edgefort.simulator.provider.nibss.nip.security.SsmCodec;
import com.edgefort.simulator.provider.nibss.nip.security.SsmException;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoap;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoapFault;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoapMethod;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipWsdl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;

/**
 * NIP v9.5 SOAP web service. The body element names the method; its {@code request} element carries the
 * SSM-encrypted NIP request message, which is decrypted with the simulator private key, answered exactly like the
 * v9.4 XML routes, and returned SSM-encrypted with the institution's public key. Every path accepts every
 * implemented method. Simulated timeouts propagate so the connection is dropped without a response.
 */
@RestController
@Tag(name = "NIBSS NIP v9.5 SOAP", description = "NIP SOAP 1.1 web service with SSM-encrypted request and return "
        + "elements; append ?wsdl for the service description")
public class NibssNipSoapController {

    static final String NIP_INTERFACE = "/NIPWS/NIPInterface";
    static final String NIP_TSQ_INTERFACE = "/NIPWS/NIPTSQInterface";
    static final String NIP_NE_INTERFACE = "/NIPWS/NIPNEInterface";

    private static final MediaType SOAP_MEDIA_TYPE = MediaType.parseMediaType(NibssNipSoap.CONTENT_TYPE);

    private final NibssNipSimulationAdapter adapter;
    private final NibssNipKeyStore keys;
    private final XmlMapper xmlMapper;
    private final Validator validator;

    public NibssNipSoapController(
            NibssNipSimulationAdapter adapter,
            NibssNipKeyStore keys,
            XmlMapper xmlMapper,
            Validator validator
    ) {
        this.adapter = adapter;
        this.keys = keys;
        this.xmlMapper = xmlMapper;
        this.validator = validator;
    }

    @PostMapping({NIP_INTERFACE, NIP_TSQ_INTERFACE, NIP_NE_INTERFACE})
    @Operation(summary = "Invoke a NIP method (SOAP 1.1 envelope, SSM-encrypted request)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.TEXT_XML_VALUE, schema = @Schema(type = "string")
    ))
    ResponseEntity<String> invoke(@RequestBody(required = false) byte[] body) {
        NibssNipSoap.Request request = NibssNipSoap.parseRequest(body);
        PGPPublicKey clientKey = keys.clientEncryptionKey()
                .orElseThrow(() -> NibssNipSoapFault.server("Institution public key is not configured"));
        String message;
        try {
            message = SsmCodec.decrypt(request.payload(), keys.simulatorKeys());
        }
        catch (SsmException exception) {
            throw NibssNipSoapFault.client("Unable to decrypt request: " + exception.getMessage());
        }
        String response = answer(request.method(), message);
        String encrypted;
        try {
            encrypted = SsmCodec.encrypt(response, clientKey);
        }
        catch (SsmException exception) {
            throw NibssNipSoapFault.server("Unable to encrypt response: " + exception.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(SOAP_MEDIA_TYPE)
                .body(NibssNipSoap.response(request.method(), encrypted));
    }

    @GetMapping(path = {NIP_INTERFACE, NIP_TSQ_INTERFACE, NIP_NE_INTERFACE}, params = "wsdl")
    @Operation(summary = "WSDL of the NIP service at this path")
    ResponseEntity<String> wsdl(HttpServletRequest request) {
        String path = request.getRequestURI();
        String serviceName = path.substring(path.lastIndexOf('/') + 1);
        return ResponseEntity.ok()
                .contentType(SOAP_MEDIA_TYPE)
                .body(NibssNipWsdl.describe(serviceName, request.getRequestURL().toString()));
    }

    @ExceptionHandler(NibssNipSoapFault.class)
    ResponseEntity<String> fault(NibssNipSoapFault fault) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(SOAP_MEDIA_TYPE)
                .body(NibssNipSoap.fault(fault));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<String> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return fault(NibssNipSoapFault.client("Unsupported content type"));
    }

    /**
     * Answers the decrypted NIP message exactly like the v9.4 XML routes, including the format-error response
     * ({@code 30}) for unreadable or invalid messages.
     */
    private String answer(NibssNipSoapMethod method, String message) {
        Record request;
        try {
            request = xmlMapper.readValue(message, method.requestType());
        }
        catch (JacksonException exception) {
            return xml(NibssNipExceptionHandler.responseMessage(method.responseType(), null));
        }
        if (request == null) {
            return xml(NibssNipExceptionHandler.responseMessage(method.responseType(), null));
        }
        if (!validator.validate(request).isEmpty()) {
            return xml(NibssNipExceptionHandler.responseMessage(method.responseType(), request));
        }
        Object response = switch (method) {
            case NAME_ENQUIRY -> adapter.nameEnquiry((NameEnquiryRequest) request);
            case DIRECT_CREDIT_TRANSFER -> adapter.creditTransfer((CreditTransferRequest) request);
            case DIRECT_DEBIT_TRANSFER -> adapter.debitTransfer((DebitTransferRequest) request);
            case TRANSACTION_STATUS_QUERY -> adapter.transactionStatus((TransactionStatusQueryRequest) request);
            case BALANCE_ENQUIRY -> adapter.balanceEnquiry((BalanceEnquiryRequest) request);
            case DIRECT_CREDIT_ADVICE -> adapter.creditAdvice((CreditAdviceRequest) request);
            case DIRECT_DEBIT_ADVICE -> adapter.debitAdvice((DebitAdviceRequest) request);
        };
        return xml(response);
    }

    private String xml(Object message) {
        return xmlMapper.writeValueAsString(message);
    }
}
