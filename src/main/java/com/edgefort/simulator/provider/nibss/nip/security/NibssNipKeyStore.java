package com.edgefort.simulator.provider.nibss.nip.security;

import com.edgefort.simulator.provider.nibss.nip.config.NibssNipProperties;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The simulator's own SSM key pair (it plays NIBSS) and the connected institution's public key.
 *
 * <p>The simulator key pair comes from {@code config.simulator-private-key} when set, otherwise a fresh RSA-2048 key
 * pair is generated at startup; institutions then download the public key through the admin API. The institution's
 * public key comes from {@code config.client-public-key} and can be replaced through the admin API. Key material is
 * never logged.</p>
 */
@Component
public class NibssNipKeyStore {

    static final String SIMULATOR_USER_ID = "NIBSS NIP Simulator";

    private static final Logger logger = LoggerFactory.getLogger(NibssNipKeyStore.class);

    private final PgpSecretKeys simulatorKeys;
    private final AtomicReference<PGPPublicKeyRing> clientPublicKey = new AtomicReference<>();

    public NibssNipKeyStore(NibssNipProperties properties) {
        this.simulatorKeys = simulatorKeys(properties);
        if (!properties.getClientPublicKey().isBlank()) {
            try {
                clientPublicKey.set(PgpKeys.parsePublicKeyRing(base64(properties.getClientPublicKey())));
            }
            catch (SsmException exception) {
                throw new IllegalStateException(
                        "simulator.providers.nibss-nip.config.client-public-key is invalid: " + exception.getMessage()
                );
            }
        }
        logger.info("NIBSS NIP simulator key {}; client key {}",
                PgpKeys.describe(simulatorKeys.publicKeyRing()).fingerprint(),
                clientKey().map(ring -> PgpKeys.describe(ring).fingerprint()).orElse("not configured"));
    }

    public PgpSecretKeys simulatorKeys() {
        return simulatorKeys;
    }

    public PGPPublicKeyRing simulatorPublicKey() {
        return simulatorKeys.publicKeyRing();
    }

    public Optional<PGPPublicKeyRing> clientKey() {
        return Optional.ofNullable(clientPublicKey.get());
    }

    /**
     * The key responses and inward requests are encrypted to.
     */
    public Optional<PGPPublicKey> clientEncryptionKey() {
        return clientKey().map(PgpKeys::encryptionKey);
    }

    /**
     * Replaces the institution's public key. Throws {@link SsmException} when the key cannot be used.
     */
    public PGPPublicKeyRing replaceClientKey(byte[] encoded) {
        PGPPublicKeyRing ring = PgpKeys.parsePublicKeyRing(encoded);
        clientPublicKey.set(ring);
        logger.info("NIBSS NIP client key replaced: {}", PgpKeys.describe(ring).fingerprint());
        return ring;
    }

    private static PgpSecretKeys simulatorKeys(NibssNipProperties properties) {
        if (properties.getSimulatorPrivateKey().isBlank()) {
            return PgpSecretKeys.generate(SIMULATOR_USER_ID);
        }
        try {
            return PgpSecretKeys.parse(
                    base64(properties.getSimulatorPrivateKey()),
                    properties.getSimulatorPrivateKeyPassphrase().toCharArray()
            );
        }
        catch (SsmException exception) {
            throw new IllegalStateException(
                    "simulator.providers.nibss-nip.config.simulator-private-key is invalid: " + exception.getMessage()
            );
        }
    }

    private static byte[] base64(String value) {
        try {
            return Base64.getMimeDecoder().decode(value.strip());
        }
        catch (IllegalArgumentException exception) {
            throw new SsmException("value is not Base64");
        }
    }
}
