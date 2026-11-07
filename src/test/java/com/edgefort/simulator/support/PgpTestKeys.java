package com.edgefort.simulator.support;

import com.edgefort.simulator.provider.nibss.nip.security.PgpSecretKeys;
import org.bouncycastle.bcpg.ArmoredOutputStream;
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
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Date;

/**
 * Throwaway SSM-style key pairs for tests: an RSA-2048 signing primary key with an RSA-2048 encryption subkey and
 * CAST5/ZIP preferences, exported as an unprotected ASCII-armored secret key ring.
 *
 * <p>{@link #main(String[])} writes such a ring to a file; it produced the simulator's built-in test key.</p>
 */
public final class PgpTestKeys {

    private PgpTestKeys() {
    }

    public static PgpSecretKeys generate(String userId) {
        return PgpSecretKeys.parse(armoredSecretKeyRing(userId), new char[0]);
    }

    public static byte[] armoredSecretKeyRing(String userId) {
        SecureRandom random = new SecureRandom();
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), random, 2048, 25));
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

            PGPKeyRingGenerator rings = new PGPKeyRingGenerator(
                    PGPSignature.POSITIVE_CERTIFICATION,
                    primary,
                    userId,
                    new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
                    primaryPackets.generate(),
                    null,
                    new BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256),
                    null
            );
            rings.addSubKey(encryption, encryptionPackets.generate(), null);

            ByteArrayOutputStream armored = new ByteArrayOutputStream();
            try (ArmoredOutputStream out = new ArmoredOutputStream(armored)) {
                rings.generateSecretKeyRing().encode(out);
            }
            return armored.toByteArray();
        }
        catch (PGPException | IOException exception) {
            throw new IllegalStateException("Unable to generate an OpenPGP key pair", exception);
        }
    }

    /**
     * Usage: {@code PgpTestKeys <user id> <output file>}.
     */
    public static void main(String[] args) throws IOException {
        Files.write(Path.of(args[1]), armoredSecretKeyRing(args[0]));
    }
}
