package com.edgefort.simulator.provider.nibss.nip.soap;

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

import java.util.Arrays;
import java.util.Optional;

/**
 * NIP web-service methods (specification section 6) implemented by the simulator, with the XML messages carried
 * in their encrypted {@code request} and {@code return} elements. Section 6 lists only
 * {@code nameenquirysingleitem}, {@code fundtransfersingleitem_dc}, and {@code txnstatusquerysingleitem} as exposed
 * by NIBSS; the others are institution-side methods the simulator also answers.
 */
public enum NibssNipSoapMethod {

    NAME_ENQUIRY("nameenquirysingleitem", NameEnquiryRequest.class, NameEnquiryResponse.class),
    DIRECT_CREDIT_TRANSFER("fundtransfersingleitem_dc", CreditTransferRequest.class, CreditTransferResponse.class),
    DIRECT_DEBIT_TRANSFER("fundtransfersingleitem_dd", DebitTransferRequest.class, DebitTransferResponse.class),
    TRANSACTION_STATUS_QUERY(
            "txnstatusquerysingleitem", TransactionStatusQueryRequest.class, TransactionStatusQueryResponse.class
    ),
    BALANCE_ENQUIRY("balanceenquiry", BalanceEnquiryRequest.class, BalanceEnquiryResponse.class),
    DIRECT_CREDIT_ADVICE("fundtransferAdvice_dc", CreditAdviceRequest.class, CreditAdviceResponse.class),
    DIRECT_DEBIT_ADVICE("fundtransferAdvice_dd", DebitAdviceRequest.class, DebitAdviceResponse.class);

    private final String wireName;
    private final Class<? extends Record> requestType;
    private final Class<? extends Record> responseType;

    NibssNipSoapMethod(String wireName, Class<? extends Record> requestType, Class<? extends Record> responseType) {
        this.wireName = wireName;
        this.requestType = requestType;
        this.responseType = responseType;
    }

    public static Optional<NibssNipSoapMethod> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(method -> method.wireName.equals(wireName)).findFirst();
    }

    /** Operation and request wrapper element name, for example {@code nameenquirysingleitem}. */
    public String wireName() {
        return wireName;
    }

    public String responseWireName() {
        return wireName + "Response";
    }

    public Class<? extends Record> requestType() {
        return requestType;
    }

    public Class<? extends Record> responseType() {
        return responseType;
    }
}
