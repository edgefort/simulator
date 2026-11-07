package com.edgefort.simulator.provider.nibss.nip.security;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An OpenPGP secret key ring with its private keys unlocked in memory, used to decrypt SSM payloads.
 */
public final class PgpSecretKeys {

    private static final int RSA_KEY_SIZE = 2048;
    private static final BigInteger RSA_PUBLIC_EXPONENT = BigInteger.valueOf(0x10001);
    private static final int RSA_PRIME_CERTAINTY = 25;

    private final PGPPublicKeyRing publicKeyRing;
    private final Map<Long, PGPKeyPair> keyPairs;

    private PgpSecretKeys(PGPPublicKeyRing publicKeyRing, Map<Long, PGPKeyPair> keyPairs) {
        this.publicKeyRing = publicKeyRing;
        this.keyPairs = Map.copyOf(keyPairs);
    }

    /**
     * Generates an RSA-2048 key pair the way SSM does: a signing primary key with an encryption subkey. The secret
     * key ring is kept only in memory and is never exported.
     */
    public static PgpSecretKeys generate(String userId) {
        SecureRandom random = new SecureRandom();
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(RSA_PUBLIC_EXPONENT, random, RSA_KEY_SIZE, RSA_PRIME_CERTAINTY));
        try {
            Date now = new Date();
            PGPKeyPair primary = new BcPGPKeyPair(
                    PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_GENERAL, generator.generateKeyPair(), now
            );
            PGPKeyPair encryption = new BcPGPKeyPair(
                    PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_GENERAL, generator.generateKeyPair(), now
            );

            PGPSignatureSubpacketGenerator primaryPackets = new PGPSignatureSubpacketGenerator();
            primaryPackets.setKeyFlags(false, KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA);
            primaryPackets.setPreferredSymmetricAlgorithms(false, new int[]{
                    SymmetricKeyAlgorithmTags.CAST5, SymmetricKeyAlgorithmTags.AES_256, SymmetricKeyAlgorithmTags.AES_128
            });
            primaryPackets.setPreferredHashAlgorithms(false, new int[]{
                    HashAlgorithmTags.SHA256, HashAlgorithmTags.SHA1
            });
            primaryPackets.setPreferredCompressionAlgorithms(false, new int[]{
                    CompressionAlgorithmTags.ZIP, CompressionAlgorithmTags.ZLIB, CompressionAlgorithmTags.UNCOMPRESSED
            });
            PGPSignatureSubpacketGenerator encryptionPackets = new PGPSignatureSubpacketGenerator();
            encryptionPackets.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);

            PGPDigestCalculator checksum = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
            PGPKeyRingGenerator rings = new PGPKeyRingGenerator(
                    PGPSignature.POSITIVE_CERTIFICATION,
                    primary,
                    userId,
                    checksum,
                    primaryPackets.generate(),
                    null,
                    new BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256),
                    null
            );
            rings.addSubKey(encryption, encryptionPackets.generate(), null);
            Map<Long, PGPKeyPair> keyPairs = new LinkedHashMap<>();
            keyPairs.put(primary.getKeyID(), primary);
            keyPairs.put(encryption.getKeyID(), encryption);
            return new PgpSecretKeys(rings.generatePublicKeyRing(), keyPairs);
        }
        catch (PGPException exception) {
            throw new SsmException("Unable to generate an OpenPGP key pair", exception);
        }
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
