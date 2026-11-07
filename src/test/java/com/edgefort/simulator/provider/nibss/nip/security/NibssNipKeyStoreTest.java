package com.edgefort.simulator.provider.nibss.nip.security;

import com.edgefort.simulator.provider.nibss.nip.config.NibssNipProperties;
import com.edgefort.simulator.support.PgpTestKeys;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NibssNipKeyStoreTest {

    /** Published in NIBSS_NIP.md; institutions pin this key in their test configuration. */
    private static final String BUILT_IN_FINGERPRINT = "86114BA1ABFFCA5D322DCCE909AC730FDC684398";

    @Test
    void usesTheSameBuiltInTestKeyOnEveryStartWhenNoKeyIsConfigured() {
        NibssNipKeyStore first = new NibssNipKeyStore(new NibssNipProperties());
        NibssNipKeyStore second = new NibssNipKeyStore(new NibssNipProperties());

        assertThat(fingerprint(first)).isEqualTo(BUILT_IN_FINGERPRINT);
        assertThat(fingerprint(second)).isEqualTo(BUILT_IN_FINGERPRINT);
    }

    @Test
    void configuredKeyReplacesTheBuiltInTestKey() {
        NibssNipProperties properties = new NibssNipProperties();
        properties.setSimulatorPrivateKey(
                Base64.getEncoder().encodeToString(PgpTestKeys.armoredSecretKeyRing("Staging NIBSS"))
        );

        assertThat(fingerprint(new NibssNipKeyStore(properties))).isNotEqualTo(BUILT_IN_FINGERPRINT);
    }

    private static String fingerprint(NibssNipKeyStore store) {
        return PgpKeys.describe(store.simulatorPublicKey()).fingerprint();
    }
}
