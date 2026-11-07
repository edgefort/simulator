package com.edgefort.simulator.provider.nibss.nip.security;

import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PacketTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsmCodecTest {

    private static final PgpSecretKeys RECIPIENT = PgpSecretKeys.generate("Recipient");
    private static final PgpSecretKeys OTHER = PgpSecretKeys.generate("Other");

    @Test
    void roundTripsAShortMessageAsOneTerminatedUppercaseHexChunk() {
        String message = "<NESingleRequest><SessionID>000001100913103301000000000001</SessionID></NESingleRequest>";

        String encrypted = SsmCodec.encrypt(message, encryptionKey());

        assertThat(encrypted).matches("[0-9A-F]+;");
        assertThat(SsmCodec.decrypt(encrypted, RECIPIENT)).isEqualTo(message);
    }

    @Test
    void splitsLongMessagesIntoTerminatedChunksOf1024Characters() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; builder.length() < 2500; index++) {
            builder.append("<Narration>").append(index).append(" é ₦ </Narration>");
        }
        String message = builder.substring(0, 2500);

        String encrypted = SsmCodec.encrypt(message, encryptionKey());

        assertThat(encrypted).endsWith(";").matches("([0-9A-F]+;){3}");
        List<String> chunks = Arrays.stream(encrypted.split(";")).toList();
        assertThat(chunks).hasSize(3);
        assertThat(SsmCodec.decrypt(chunks.get(0) + ";", RECIPIENT)).isEqualTo(message.substring(0, 1024));
        assertThat(SsmCodec.decrypt(chunks.get(1) + ";", RECIPIENT)).isEqualTo(message.substring(1024, 2048));
        assertThat(SsmCodec.decrypt(chunks.get(2) + ";", RECIPIENT)).isEqualTo(message.substring(2048));
        assertThat(SsmCodec.decrypt(encrypted, RECIPIENT)).isEqualTo(message);
    }

    @Test
    void exactlyOneChunkHoldsA1024CharacterMessage() {
        String message = "x".repeat(1024);

        assertThat(SsmCodec.encrypt(message, encryptionKey())).matches("[0-9A-F]+;");
        assertThat(SsmCodec.encrypt(message + "y", encryptionKey())).matches("([0-9A-F]+;){2}");
    }

    @Test
    void decryptAcceptsLowercaseHexAndAMissingFinalTerminator() {
        String message = "x".repeat(1500);
        String encrypted = SsmCodec.encrypt(message, encryptionKey());

        String lowercase = encrypted.toLowerCase(Locale.ROOT);
        String unterminated = lowercase.substring(0, lowercase.length() - 1);

        assertThat(SsmCodec.decrypt(lowercase, RECIPIENT)).isEqualTo(message);
        assertThat(SsmCodec.decrypt(unterminated, RECIPIENT)).isEqualTo(message);
        assertThat(SsmCodec.decrypt(" " + unterminated.replace(";", ";;\n") + " ", RECIPIENT)).isEqualTo(message);
    }

    @Test
    void writesBinaryPgpWithoutAnIntegrityProtectionPacket() throws Exception {
        String encrypted = SsmCodec.encrypt("x".repeat(2000), encryptionKey());

        for (String chunk : encrypted.split(";")) {
            byte[] packets = HexFormat.of().parseHex(chunk);
            assertThat(packets[0] & 0x80).as("binary packet header, not ASCII armor").isNotZero();
            try (BCPGInputStream input = new BCPGInputStream(new ByteArrayInputStream(packets))) {
                assertThat(input.nextPacketTag()).isEqualTo(PacketTags.PUBLIC_KEY_ENC_SESSION);
                input.readPacket();
                assertThat(input.nextPacketTag())
                        .isEqualTo(PacketTags.SYMMETRIC_KEY_ENC)
                        .isNotEqualTo(PacketTags.SYM_ENC_INTEGRITY_PRO);
            }
        }
    }

    @Test
    void decryptAcceptsIntegrityProtectedUncompressedMessagesWithOtherCiphers() throws Exception {
        PGPEncryptedDataGenerator encryptor = new PGPEncryptedDataGenerator(
                new BcPGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
        );
        encryptor.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(encryptionKey()));
        byte[] data = "<TSQuerySingleRequest/>".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream encrypted = encryptor.open(bytes, new byte[256])) {
            try (OutputStream literal = new PGPLiteralDataGenerator()
                    .open(encrypted, PGPLiteralData.UTF8, "message.xml", data.length, new Date())) {
                literal.write(data);
            }
        }

        String payload = HexFormat.of().formatHex(bytes.toByteArray()) + ";";

        assertThat(SsmCodec.decrypt(payload, RECIPIENT)).isEqualTo("<TSQuerySingleRequest/>");
    }

    @Test
    void rejectsPayloadsThatAreNotSsmMessagesForTheKey() {
        String forOther = SsmCodec.encrypt("secret", PgpKeys.encryptionKey(OTHER.publicKeyRing()));

        assertThatThrownBy(() -> SsmCodec.decrypt(forOther, RECIPIENT)).isInstanceOf(SsmException.class);
        assertThatThrownBy(() -> SsmCodec.decrypt("ZZZZ;", RECIPIENT)).isInstanceOf(SsmException.class);
        assertThatThrownBy(() -> SsmCodec.decrypt("0A0B0C;", RECIPIENT)).isInstanceOf(SsmException.class);
        assertThatThrownBy(() -> SsmCodec.decrypt(";;", RECIPIENT)).isInstanceOf(SsmException.class);
        assertThatThrownBy(() -> SsmCodec.decrypt("", RECIPIENT)).isInstanceOf(SsmException.class);
    }

    @Test
    void keysRoundTripThroughBinaryAndArmoredForms() {
        PGPPublicKeyRing ring = RECIPIENT.publicKeyRing();

        assertThat(PgpKeys.binary(PgpKeys.parsePublicKeyRing(PgpKeys.binary(ring)))).isEqualTo(PgpKeys.binary(ring));
        assertThat(PgpKeys.armored(ring)).startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----");
        assertThat(PgpKeys.binary(PgpKeys.parsePublicKeyRing(
                PgpKeys.armored(ring).getBytes(StandardCharsets.US_ASCII)))).isEqualTo(PgpKeys.binary(ring));
        assertThat(PgpKeys.encryptionKey(ring).isMasterKey()).isFalse();
        assertThat(PgpKeys.describe(ring).userIds()).containsExactly("Recipient");
        assertThatThrownBy(() -> PgpKeys.parsePublicKeyRing("garbage".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(SsmException.class);
    }

    @Test
    void passphraseProtectedSecretKeyRingsDecryptOnlyWithTheirPassphrase() throws Exception {
        char[] passphrase = "test-passphrase".toCharArray();
        PGPKeyRingGenerator rings = protectedKeyRing(passphrase);
        byte[] secretRing = rings.generateSecretKeyRing().getEncoded();

        PgpSecretKeys keys = PgpSecretKeys.parse(secretRing, passphrase);
        String encrypted = SsmCodec.encrypt("<NESingleRequest/>", PgpKeys.encryptionKey(keys.publicKeyRing()));

        assertThat(SsmCodec.decrypt(encrypted, keys)).isEqualTo("<NESingleRequest/>");
        assertThat(PgpKeys.binary(keys.publicKeyRing())).isEqualTo(PgpKeys.binary(rings.generatePublicKeyRing()));
        assertThatThrownBy(() -> PgpSecretKeys.parse(secretRing, "wrong".toCharArray()))
                .isInstanceOf(SsmException.class);
    }

    private static org.bouncycastle.openpgp.PGPPublicKey encryptionKey() {
        return PgpKeys.encryptionKey(RECIPIENT.publicKeyRing());
    }

    private static PGPKeyRingGenerator protectedKeyRing(char[] passphrase) throws Exception {
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();
        generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), new SecureRandom(), 2048, 25));
        PGPKeyPair primary = new BcPGPKeyPair(
                PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_GENERAL, generator.generateKeyPair(), new Date()
        );
        PGPDigestCalculator checksum = new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
        PGPKeyRingGenerator rings = new PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                primary,
                "Protected",
                checksum,
                null,
                null,
                new BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256),
                new BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, checksum).build(passphrase)
        );
        return rings;
    }
}
