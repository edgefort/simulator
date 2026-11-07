package com.edgefort.simulator.provider.nibss.nip.security;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An OpenPGP secret key ring with its private keys unlocked in memory, used to decrypt SSM payloads.
 */
public final class PgpSecretKeys {

    private final PGPPublicKeyRing publicKeyRing;
    private final Map<Long, PGPKeyPair> keyPairs;

    private PgpSecretKeys(PGPPublicKeyRing publicKeyRing, Map<Long, PGPKeyPair> keyPairs) {
        this.publicKeyRing = publicKeyRing;
        this.keyPairs = Map.copyOf(keyPairs);
    }

    /**
     * Reads an ASCII-armored or binary secret key ring and unlocks every private key with the passphrase.
     */
    public static PgpSecretKeys parse(byte[] encoded, char[] passphrase) {
        if (encoded == null || encoded.length == 0) {
            throw new SsmException("Secret key is empty");
        }
        PGPSecretKeyRingCollection rings;
        try (InputStream decoded = PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded))) {
            rings = new PGPSecretKeyRingCollection(decoded, new BcKeyFingerprintCalculator());
        }
        catch (IOException | PGPException | RuntimeException exception) {
            throw new SsmException("Not an OpenPGP secret key ring", exception);
        }
        Iterator<PGPSecretKeyRing> iterator = rings.getKeyRings();
        if (!iterator.hasNext()) {
            throw new SsmException("The secret key ring is empty");
        }
        PGPSecretKeyRing ring = iterator.next();
        PBESecretKeyDecryptor decryptor =
                new BcPBESecretKeyDecryptorBuilder(new BcPGPDigestCalculatorProvider()).build(passphrase);
        List<PGPPublicKey> publicKeys = new ArrayList<>();
        Map<Long, PGPKeyPair> keyPairs = new LinkedHashMap<>();
        for (PGPSecretKey secretKey : ring) {
            publicKeys.add(secretKey.getPublicKey());
            if (secretKey.isPrivateKeyEmpty()) {
                continue;
            }
            try {
                PGPPrivateKey privateKey = secretKey.extractPrivateKey(decryptor);
                keyPairs.put(secretKey.getKeyID(), new PGPKeyPair(secretKey.getPublicKey(), privateKey));
            }
            catch (PGPException exception) {
                throw new SsmException("Unable to unlock the secret key; check the passphrase", exception);
            }
        }
        PGPPublicKeyRing publicKeyRing = new PGPPublicKeyRing(publicKeys);
        PgpKeys.encryptionKey(publicKeyRing);
        return new PgpSecretKeys(publicKeyRing, keyPairs);
    }

    public PGPPublicKeyRing publicKeyRing() {
        return publicKeyRing;
    }

    Optional<PGPKeyPair> keyPair(long keyId) {
        return Optional.ofNullable(keyPairs.get(keyId));
    }

    Collection<PGPKeyPair> keyPairs() {
        return keyPairs.values();
    }
}
