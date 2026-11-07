package com.edgefort.simulator.provider.nibss.nip.security;

import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.KeyIdentifier;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPMarker;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * The NIBSS Security Module (SSM) payload format.
 *
 * <p>Encryption splits the plaintext into consecutive 1024-character substrings. Each chunk's UTF-8 bytes are
 * OpenPGP public-key encrypted as a binary (not ASCII-armored) message without an integrity-protection packet,
 * containing a ZIP-compressed literal data packet and using CAST5, which mirrors the legacy SSM library defaults.
 * Each encrypted chunk is written as uppercase hexadecimal followed by {@code ;}.</p>
 *
 * <p>Decryption splits on {@code ;}, ignores empty tokens, accepts either hexadecimal case, and accepts any
 * symmetric algorithm, compression, or integrity protection that the OpenPGP implementation supports.</p>
 */
public final class SsmCodec {

    public static final int CHUNK_LENGTH = 1024;
    public static final char CHUNK_TERMINATOR = ';';

    private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();
    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RANDOM = new SecureRandom();

    private SsmCodec() {
    }

    public static String encrypt(String plaintext, PGPPublicKey recipient) {
        if (plaintext == null) {
            throw new SsmException("Nothing to encrypt");
        }
        StringBuilder encrypted = new StringBuilder();
        int start = 0;
        do {
            int end = Math.min(plaintext.length(), start + CHUNK_LENGTH);
            byte[] chunk = plaintext.substring(start, end).getBytes(StandardCharsets.UTF_8);
            encrypted.append(UPPER_HEX.formatHex(encryptChunk(chunk, recipient))).append(CHUNK_TERMINATOR);
            start = end;
        } while (start < plaintext.length());
        return encrypted.toString();
    }

    public static String decrypt(String ciphertext, PgpSecretKeys keys) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new SsmException("Encrypted payload is empty");
        }
        StringBuilder plaintext = new StringBuilder();
        int chunks = 0;
        for (String token : ciphertext.split(String.valueOf(CHUNK_TERMINATOR))) {
            String hex = token.strip();
            if (hex.isEmpty()) {
                continue;
            }
            byte[] chunk;
            try {
                chunk = HEX.parseHex(hex);
            }
            catch (IllegalArgumentException exception) {
                throw new SsmException("Encrypted payload is not hexadecimal", exception);
            }
            plaintext.append(new String(decryptChunk(chunk, keys), StandardCharsets.UTF_8));
            chunks++;
        }
        if (chunks == 0) {
            throw new SsmException("Encrypted payload contains no chunk");
        }
        return plaintext.toString();
    }

    private static byte[] encryptChunk(byte[] data, PGPPublicKey recipient) {
        PGPEncryptedDataGenerator encryptor = new PGPEncryptedDataGenerator(
                new BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.CAST5)
                        .setWithIntegrityPacket(false)
                        .setSecureRandom(RANDOM)
        );
        encryptor.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(recipient).setSecureRandom(RANDOM));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream encrypted = encryptor.open(bytes, new byte[1 << 12])) {
            PGPCompressedDataGenerator compressor = new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
            try (OutputStream compressed = compressor.open(encrypted)) {
                PGPLiteralDataGenerator literal = new PGPLiteralDataGenerator();
                try (OutputStream content = literal.open(
                        compressed, PGPLiteralData.BINARY, "", data.length, new Date()
                )) {
                    content.write(data);
                }
            }
        }
        catch (IOException | PGPException exception) {
            throw new SsmException("Unable to encrypt the payload", exception);
        }
        return bytes.toByteArray();
    }

    private static byte[] decryptChunk(byte[] chunk, PgpSecretKeys keys) {
        try {
            PGPEncryptedDataList encryptedData = encryptedDataList(
                    new BcPGPObjectFactory(PGPUtil.getDecoderStream(new ByteArrayInputStream(chunk)))
            );
            for (PGPEncryptedData candidate : encryptedData) {
                if (!(candidate instanceof PGPPublicKeyEncryptedData data)) {
                    continue;
                }
                for (PGPKeyPair keyPair : candidateKeys(data, keys)) {
                    InputStream clear;
                    try {
                        clear = data.getDataStream(new BcPublicKeyDataDecryptorFactory(keyPair));
                    }
                    catch (PGPException wrongKey) {
                        continue;
                    }
                    byte[] plaintext = literalContent(new BcPGPObjectFactory(clear));
                    if (data.isIntegrityProtected() && !data.verify()) {
                        throw new SsmException("Encrypted payload failed the integrity check");
                    }
                    return plaintext;
                }
            }
            throw new SsmException("Encrypted payload is not addressed to a known private key");
        }
        catch (IOException | PGPException | RuntimeException exception) {
            if (exception instanceof SsmException ssm) {
                throw ssm;
            }
            throw new SsmException("Unable to decrypt the payload", exception);
        }
    }

    private static List<PGPKeyPair> candidateKeys(PGPPublicKeyEncryptedData data, PgpSecretKeys keys) {
        KeyIdentifier recipient = data.getKeyIdentifier();
        if (recipient == null || recipient.isWildcard()) {
            return new ArrayList<>(keys.keyPairs());
        }
        return keys.keyPair(recipient.getKeyId()).map(List::of).orElseGet(List::of);
    }

    private static PGPEncryptedDataList encryptedDataList(BcPGPObjectFactory factory) throws IOException {
        Object next;
        while ((next = factory.nextObject()) != null) {
            if (next instanceof PGPEncryptedDataList list) {
                return list;
            }
            if (!(next instanceof PGPMarker)) {
                break;
            }
        }
        throw new SsmException("Encrypted payload is not an OpenPGP encrypted message");
    }

    private static byte[] literalContent(BcPGPObjectFactory factory) throws IOException, PGPException {
        Object next;
        while ((next = factory.nextObject()) != null) {
            if (next instanceof PGPCompressedData compressed) {
                return literalContent(new BcPGPObjectFactory(compressed.getDataStream()));
            }
            if (next instanceof PGPLiteralData literal) {
                try (InputStream content = literal.getInputStream()) {
                    return content.readAllBytes();
                }
            }
            if (!(next instanceof PGPOnePassSignatureList || next instanceof PGPSignatureList
                    || next instanceof PGPMarker)) {
                break;
            }
        }
        throw new SsmException("Encrypted payload contains no literal data");
    }
}
