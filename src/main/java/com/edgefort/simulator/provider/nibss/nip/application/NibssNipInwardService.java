package com.edgefort.simulator.provider.nibss.nip.application;

import com.edgefort.simulator.core.application.TransactionCommand;
import com.edgefort.simulator.core.application.TransactionSimulationResult;
import com.edgefort.simulator.core.application.TransactionSimulationService;
import com.edgefort.simulator.core.state.SimulatedTransaction;
import com.edgefort.simulator.core.state.TransactionKey;
import com.edgefort.simulator.core.state.TransactionStatus;
import com.edgefort.simulator.core.state.TransactionStore;
import com.edgefort.simulator.provider.nibss.nip.config.NibssNipProperties;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.CreditTransferResponse;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryRequest;
import com.edgefort.simulator.provider.nibss.nip.contract.NibssNipContracts.NameEnquiryResponse;
import com.edgefort.simulator.provider.nibss.nip.security.NibssNipKeyStore;
import com.edgefort.simulator.provider.nibss.nip.security.SsmCodec;
import com.edgefort.simulator.provider.nibss.nip.security.SsmException;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoap;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoapFault;
import com.edgefort.simulator.provider.nibss.nip.soap.NibssNipSoapMethod;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;

import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.ID;
import static com.edgefort.simulator.provider.nibss.nip.NibssNipProvider.INWARD_DIRECT_CREDIT_TRANSFER;
import static com.edgefort.simulator.provider.nibss.nip.contract.NibssNipResponseCodes.APPROVED;

/**
 * The simulator playing NIBSS: sends NIP requests to an institution's inward web service, SSM-encrypted with the
 * institution's public key, and decrypts the answers with the simulator private key.
 *
 * <p>An approved inward direct credit is recorded under its {@code SessionID} through the normal behavior
 * resolution for {@value com.edgefort.simulator.provider.nibss.nip.NibssNipProvider#INWARD_DIRECT_CREDIT_TRANSFER},
 * so the institution's later status query observes success, failure, or pending as selected through the control
 * plane. A credit the institution did not approve is recorded as failed. Nothing is recorded when the call fails.</p>
 */
@Service
public class NibssNipInwardService {

    private static final Logger logger = LoggerFactory.getLogger(NibssNipInwardService.class);

    /** NIBSS operates in West Africa Time; session IDs carry its local date and time. */
    private static final ZoneId NIBSS_ZONE = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter SESSION_TIMESTAMP = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final long SESSION_SERIAL_BOUND = 1_000_000_000_000L;
    private static final String DEFAULT_CHANNEL_CODE = "1";

    private final NibssNipProperties.Inward settings;
    private final NibssNipKeyStore keys;
    private final XmlMapper xmlMapper;
    private final XmlMapper requestWriter;
    private final TransactionSimulationService transactionService;
    private final TransactionStore transactionStore;
    private final Clock clock;
    private final HttpClient httpClient;
    private final SecureRandom random = new SecureRandom();

    public NibssNipInwardService(
            NibssNipProperties properties,
            NibssNipKeyStore keys,
            XmlMapper xmlMapper,
            TransactionSimulationService transactionService,
            TransactionStore transactionStore,
            Clock clock
    ) {
        this.settings = properties.getInward();
        this.keys = keys;
        this.xmlMapper = xmlMapper;
        this.requestWriter = xmlMapper.rebuild()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        this.transactionService = transactionService;
        this.transactionStore = transactionStore;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(settings.getTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Sends a name enquiry (010) to the institution and returns its response (011).
     */
    public InwardResult nameEnquiry(InwardNameEnquiry command) {
        PGPPublicKey institutionKey = requireConfigured();
        String sessionId = sessionId();
        NameEnquiryRequest request = new NameEnquiryRequest(
                sessionId,
                command.destinationInstitutionCode(),
                channelCode(command.channelCode()),
                command.accountNumber()
        );
        Call call = call(NibssNipSoapMethod.NAME_ENQUIRY, sessionId, requestWriter.writeValueAsString(request),
                institutionKey);
        String responseCode = responseCode(call, NameEnquiryResponse.class, NameEnquiryResponse::responseCode);
        return call.result(responseCode, null, null);
    }

    /**
     * Sends a direct credit (012) to the institution, records the outcome for its later status query, and returns
     * its response (013).
     */
    public InwardResult credit(InwardCredit command) {
        PGPPublicKey institutionKey = requireConfigured();
        String sessionId = sessionId();
        String channelCode = channelCode(command.channelCode());
        BigDecimal amount = command.amount().setScale(2);
        CreditTransferRequest request = new CreditTransferRequest(
                sessionId,
                blankToNull(command.nameEnquiryRef()),
                command.destinationInstitutionCode(),
                channelCode,
                command.beneficiaryAccountName(),
                command.beneficiaryAccountNumber(),
                command.beneficiaryBankVerificationNumber(),
                blankToNull(command.beneficiaryKycLevel()),
                command.originatorAccountName(),
                command.originatorAccountNumber(),
                command.originatorBankVerificationNumber(),
                blankToNull(command.originatorKycLevel()),
                blankToNull(command.transactionLocation()),
                blankToNull(command.narration()),
                blankToNull(command.paymentReference()),
                amount
        );
        Call call = call(NibssNipSoapMethod.DIRECT_CREDIT_TRANSFER, sessionId,
                requestWriter.writeValueAsString(request), institutionKey);
        String responseCode = responseCode(call, CreditTransferResponse.class, CreditTransferResponse::responseCode);

        Map<String, String> attributes = Map.of(
                "DestinationInstitutionCode", command.destinationInstitutionCode(),
                "ChannelCode", channelCode,
                "BeneficiaryAccountNumber", command.beneficiaryAccountNumber(),
                "OriginatorAccountNumber", command.originatorAccountNumber(),
                "Amount", amount.toPlainString()
        );
        TransactionStatus status;
        if (APPROVED.equals(responseCode)) {
            TransactionSimulationResult result = transactionService.simulate(new TransactionCommand(
                    ID, INWARD_DIRECT_CREDIT_TRANSFER, sessionId, amount, attributes
            ));
            status = result.transaction().status();
        }
        else {
            Instant now = clock.instant();
            SimulatedTransaction failed = new SimulatedTransaction(
                    new TransactionKey(ID, sessionId), INWARD_DIRECT_CREDIT_TRANSFER, amount, attributes,
                    TransactionStatus.FAILED, now, now
            );
            status = transactionStore.saveIfAbsent(failed).orElse(failed).status();
        }
        return call.result(responseCode, status, NibssNipSimulationAdapter.statusResponseCode(status));
    }

    private PGPPublicKey requireConfigured() {
        PGPPublicKey institutionKey = keys.clientEncryptionKey().orElseThrow(() -> new NibssNipInwardException(
                NibssNipInwardException.Reason.NOT_CONFIGURED,
                "The institution public key is not configured; upload it to "
                        + "/admin/providers/nibss-nip/keys/client-public or set NIBSS_NIP_CLIENT_PUBLIC_KEY",
                null
        ));
        if (settings.getUrl().isBlank()) {
            throw new NibssNipInwardException(
                    NibssNipInwardException.Reason.NOT_CONFIGURED,
                    "The institution inward URL is not configured; set NIBSS_NIP_INWARD_URL",
                    null
            );
        }
        return institutionKey;
    }

    private Call call(NibssNipSoapMethod method, String sessionId, String requestXml, PGPPublicKey institutionKey) {
        String url = settings.getUrl();
        NibssNipInwardException.Exchange sent = new NibssNipInwardException.Exchange(
                sessionId, method.wireName(), url, requestXml, null
        );
        String envelope;
        try {
            envelope = NibssNipSoap.request(
                    settings.getNamespace(),
                    method.wireName(),
                    settings.getRequestElement(),
                    SsmCodec.encrypt(requestXml, institutionKey)
            );
        }
        catch (SsmException exception) {
            throw failed("Unable to encrypt the request: " + exception.getMessage(), sent);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(settings.getTimeout())
                .header("Content-Type", NibssNipSoap.CONTENT_TYPE)
                .header("SOAPAction", "\"" + settings.getSoapAction() + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
        catch (IOException exception) {
            logger.info("NIBSS NIP inward {} to {} failed: {}", method.wireName(), url, exception.toString());
            throw failed("The institution could not be reached: " + exception, sent);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failed("Interrupted while calling the institution", sent);
        }
        NibssNipInwardException.Exchange received = new NibssNipInwardException.Exchange(
                sessionId, method.wireName(), url, requestXml, response.statusCode()
        );
        String payload;
        try {
            payload = NibssNipSoap.parseResponsePayload(response.body());
        }
        catch (NibssNipSoapFault fault) {
            throw failed("HTTP %d from the institution: %s".formatted(response.statusCode(), fault.getMessage()),
                    received);
        }
        if (response.statusCode() != 200) {
            throw failed("HTTP %d from the institution".formatted(response.statusCode()), received);
        }
        String responseXml;
        try {
            responseXml = SsmCodec.decrypt(payload, keys.simulatorKeys());
        }
        catch (SsmException exception) {
            throw failed("Unable to decrypt the institution response: " + exception.getMessage(), received);
        }
        return new Call(received, responseXml);
    }

    private <T> String responseCode(Call call, Class<T> responseType, Function<T, String> responseCode) {
        T response;
        try {
            response = xmlMapper.readValue(call.responseXml(), responseType);
        }
        catch (JacksonException exception) {
            throw failed("The institution response is not a NIP %s message".formatted(responseType.getSimpleName()),
                    call.exchange());
        }
        String code = response == null ? null : responseCode.apply(response);
        if (code == null || code.isBlank()) {
            throw failed("The institution response has no ResponseCode", call.exchange());
        }
        return code;
    }

    /**
     * NIP session ID: sender code (6), {@code yyMMddHHmmss} (12), and a 12-digit random number.
     */
    private String sessionId() {
        return settings.getSourceInstitutionCode()
                + SESSION_TIMESTAMP.format(clock.instant().atZone(NIBSS_ZONE))
                + "%012d".formatted(random.nextLong(SESSION_SERIAL_BOUND));
    }

    private static String channelCode(String channelCode) {
        return channelCode == null || channelCode.isBlank() ? DEFAULT_CHANNEL_CODE : channelCode;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static NibssNipInwardException failed(String message, NibssNipInwardException.Exchange exchange) {
        return new NibssNipInwardException(NibssNipInwardException.Reason.CALL_FAILED, message, exchange);
    }

    private record Call(NibssNipInwardException.Exchange exchange, String responseXml) {

        InwardResult result(String responseCode, TransactionStatus status, String statusQueryResponseCode) {
            return new InwardResult(
                    exchange.sessionId(),
                    exchange.method(),
                    exchange.url(),
                    exchange.requestXml(),
                    responseXml,
                    responseCode,
                    exchange.httpStatus(),
                    status,
                    statusQueryResponseCode
            );
        }
    }

    public record InwardNameEnquiry(
            @NotBlank @Pattern(regexp = "\\d{6}") String destinationInstitutionCode,
            @NotBlank @Size(max = 20) String accountNumber,
            @Pattern(regexp = "\\d+") String channelCode
    ) {
    }

    /**
     * Business fields of the direct credit request (012). {@code SessionID} is generated.
     */
    public record InwardCredit(
            @NotBlank @Pattern(regexp = "\\d{6}") String destinationInstitutionCode,
            @Pattern(regexp = "\\d+") String channelCode,
            @Size(max = 30) String nameEnquiryRef,
            @NotBlank @Size(max = 100) String beneficiaryAccountName,
            @NotBlank @Size(max = 20) String beneficiaryAccountNumber,
            @NotBlank @Size(max = 20) String beneficiaryBankVerificationNumber,
            @Size(max = 1) String beneficiaryKycLevel,
            @NotBlank @Size(max = 100) String originatorAccountName,
            @NotBlank @Size(max = 20) String originatorAccountNumber,
            @NotBlank @Size(max = 20) String originatorBankVerificationNumber,
            @Size(max = 1) String originatorKycLevel,
            String transactionLocation,
            @Size(max = 100) String narration,
            @Size(max = 100) String paymentReference,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
    ) {
    }

    /**
     * @param httpStatus                institution HTTP status
     * @param transactionStatus         recorded status of an inward credit; {@code null} for a name enquiry
     * @param statusQueryResponseCode   what the institution's status query for {@code sessionId} answers now
     */
    public record InwardResult(
            String sessionId,
            String method,
            String url,
            String requestXml,
            String responseXml,
            String responseCode,
            Integer httpStatus,
            TransactionStatus transactionStatus,
            String statusQueryResponseCode
    ) {
    }
}
