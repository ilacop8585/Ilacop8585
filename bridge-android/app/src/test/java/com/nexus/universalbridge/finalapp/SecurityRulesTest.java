package com.nexus.universalbridge.finalapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class SecurityRulesTest {
    @Test public void blocksPasswordType() {
        assertTrue(SecurityRules.isSensitiveField("password", "", "login", "pw", "", ""));
    }

    @Test public void blocksOtpAndOneTimeCode() {
        assertTrue(SecurityRules.isSensitiveField("text", "one-time-code", "code", "verification_otp", "", "Codice"));
        assertTrue(SecurityRules.isSensitiveField("text", "", "totp", "", "2FA code", ""));
    }

    @Test public void blocksTokensAndSecrets() {
        assertTrue(SecurityRules.isSensitiveField("text", "", "api_token", "", "", ""));
        assertTrue(SecurityRules.isSensitiveField("text", "", "", "client_secret", "", ""));
    }

    @Test public void allowsOrdinaryFields() {
        assertFalse(SecurityRules.isSensitiveField("text", "username", "note", "note", "Nota", "Scrivi una nota"));
        assertFalse(SecurityRules.isSensitiveField("email", "email", "email", "email", "Email", "nome@example.com"));
    }
}
