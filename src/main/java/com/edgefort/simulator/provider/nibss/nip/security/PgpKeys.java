package com.edgefort.simulator.provider.nibss.nip.security;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;

/**
 * OpenPGP public key handling for SSM. SSM itself exchanges keys in binary (non-armored) form; ASCII armor is
 * accepted and offered for convenience.
 */
public final class PgpKeys {

    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();

    private PgpKeys() {
    }

    /**
     * Parses an ASCII-armored or binary public key ring that contains an encryption-capable key.
     */
    public static PGPPublicKeyRing parsePublicKeyRing(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw new SsmException("Public key is empty");
        }
        PGPPublicKeyRingCollection rings;
        try (InputStream decoded = PGPUtil.getDecoderStream(new ByteArrayInputStream(encoded))) {
            rings = new PGPPublicKeyRingCollection(decoded, new BcKeyFingerprintCalculator());
        }
        catch (IOException | PGPException | RuntimeException exception) {
            throw new SsmException("Not an OpenPGP public key ring", exception);
        }
        Iterator<PGPPublicKeyRing> iterator = rings.getKeyRings();
        while (iterator.hasNext()) {
            PGPPublicKeyRing ring = iterator.next();
            if (hasEncryptionKey(ring)) {
                return ring;
            }
        }
        throw new SsmException("The key ring contains no encryption-capable public key");
    }

    /**
     * The key used to encrypt for the ring's owner: the first encryption-capable subkey, otherwise the primary key.
     */
    public static PGPPublicKey encryptionKey(PGPPublicKeyRing ring) {
        PGPPublicKey primary = null;
        for (PGPPublicKey key : ring) {
            if (!key.isEncryptionKey()) {
                continue;
            }
            if (!key.isMasterKey()) {
                return key;
            }
            primary = key;
        }
        if (primary == null) {
            throw new SsmException("The key ring contains no encryption-capable public key");
        }
        return primary;
    }

    public static byte[] binary(PGPPublicKeyRing ring) {
        try {
            return ring.getEncoded();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static String armored(PGPPublicKeyRing ring) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ArmoredOutputStream armor = ArmoredOutputStream.builder().clearHeaders().build(bytes)) {
            ring.encode(armor);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    /**
     * Identifies a key ring without exposing key material.
     */
    public static KeyDescription describe(PGPPublicKeyRing ring) {
        PGPPublicKey primary = ring.getPublicKey();
        List<String> userIds = new ArrayList<>();
        primary.getUserIDs().forEachRemaining(userIds::add);
        PGPPublicKey encryption = encryptionKey(ring);
        return new KeyDescription(
                UPPER_HEX.formatHex(primary.getFingerprint()),
                keyId(primary.getKeyID()),
                keyId(encryption.getKeyID()),
                List.copyOf(userIds)
        );
    }

    static String keyId(long keyId) {
        return UPPER_HEX.toHexDigits(keyId);
    }

    private static boolean hasEncryptionKey(PGPPublicKeyRing ring) {
        for (PGPPublicKey key : ring) {
            if (key.isEncryptionKey()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param fingerprint     primary key fingerprint, uppercase hexadecimal
     * @param keyId           primary key ID, 16 uppercase hexadecimal digits
     * @param encryptionKeyId ID of the key messages are encrypted to
     */
    public record KeyDescription(String fingerprint, String keyId, String encryptionKeyId, List<String> userIds) {
    }
}
