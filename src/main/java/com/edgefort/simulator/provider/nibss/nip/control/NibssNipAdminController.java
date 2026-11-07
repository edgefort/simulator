package com.edgefort.simulator.provider.nibss.nip.control;

import com.edgefort.simulator.infrastructure.web.ControlPlaneEndpoint;
import com.edgefort.simulator.provider.nibss.nip.application.NibssNipInwardService;
import com.edgefort.simulator.provider.nibss.nip.application.NibssNipInwardService.InwardCredit;
import com.edgefort.simulator.provider.nibss.nip.application.NibssNipInwardService.InwardNameEnquiry;
import com.edgefort.simulator.provider.nibss.nip.application.NibssNipInwardService.InwardResult;
import com.edgefort.simulator.provider.nibss.nip.security.NibssNipKeyStore;
import com.edgefort.simulator.provider.nibss.nip.security.PgpKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * NIBSS NIP control plane: SSM key exchange and inward calls in which the simulator plays NIBSS. It lives with the
 * provider module but is mounted at {@code /admin/...} regardless of the provider base path, behind the admin token.
 */
@ControlPlaneEndpoint
@RestController
@RequestMapping("/admin/providers/nibss-nip")
@Tag(name = "Administration: NIBSS NIP", description = "SSM keys and inward NIP calls to the connected institution")
@SecurityRequirement(name = "AdminToken")
public class NibssNipAdminController {

    static final MediaType PGP_KEYS = MediaType.parseMediaType("application/pgp-keys");
    static final String PUBLIC_KEY_FILE_NAME = "nibss_public.key";

    private final NibssNipKeyStore keys;
    private final NibssNipInwardService inward;

    public NibssNipAdminController(NibssNipKeyStore keys, NibssNipInwardService inward) {
        this.keys = keys;
        this.inward = inward;
    }

    @GetMapping("/keys")
    @Operation(summary = "Identify the simulator and institution SSM keys (fingerprints and key IDs only)")
    KeysResponse keys() {
        return new KeysResponse(
                PgpKeys.describe(keys.simulatorPublicKey()),
                keys.clientKey().map(PgpKeys::describe).orElse(null)
        );
    }

    @GetMapping("/keys/simulator-public")
    @Operation(summary = "Download the simulator (NIBSS) public key: binary nibss_public.key, or ?format=armored")
    ResponseEntity<byte[]> simulatorPublicKey(@RequestParam(defaultValue = "binary") String format) {
        if ("armored".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.US_ASCII))
                    .body(PgpKeys.armored(keys.simulatorPublicKey()).getBytes(StandardCharsets.US_ASCII));
        }
        if (!"binary".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException("format must be binary or armored");
        }
        return ResponseEntity.ok()
                .contentType(PGP_KEYS)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(PUBLIC_KEY_FILE_NAME).build().toString())
                .body(PgpKeys.binary(keys.simulatorPublicKey()));
    }

    @PutMapping("/keys/client-public")
    @Operation(summary = "Set the institution public key (ASCII-armored or binary key ring)")
    ResponseEntity<Void> clientPublicKey(@RequestBody byte[] key) {
        keys.replaceClientKey(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inward/name-enquiry")
    @Operation(summary = "Play NIBSS: send a name enquiry (010) to the institution's inward web service")
    InwardResult inwardNameEnquiry(@Valid @RequestBody InwardNameEnquiry request) {
        return inward.nameEnquiry(request);
    }

    @PostMapping("/inward/credit")
    @Operation(summary = "Play NIBSS: send a direct credit (012) to the institution and record it for its status "
            + "query")
    InwardResult inwardCredit(@Valid @RequestBody InwardCredit request) {
        return inward.credit(request);
    }

    record KeysResponse(PgpKeys.KeyDescription simulator, PgpKeys.KeyDescription client) {
    }
}
