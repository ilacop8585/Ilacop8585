package com.nexus.universalbridge.finalapp;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SecurityRules {
    private SecurityRules() {}

    public static final String SENSITIVE_REGEX_JS = "(password|passwd|pwd|secret|token|otp|totp|one[-_ ]?time|2fa|mfa|passcode|verification[-_ ]?code)";
    private static final Pattern SENSITIVE = Pattern.compile(SENSITIVE_REGEX_JS, Pattern.CASE_INSENSITIVE);

    public static boolean isSensitiveField(String type, String autocomplete, String id, String name, String aria, String placeholder) {
        if (type != null && "password".equals(type.toLowerCase(Locale.ROOT))) return true;
        String ac = autocomplete == null ? "" : autocomplete.toLowerCase(Locale.ROOT);
        if (ac.contains("current-password") || ac.contains("new-password") || ac.contains("one-time-code")) return true;
        String joined = safe(id) + " " + safe(name) + " " + safe(aria) + " " + safe(placeholder);
        return SENSITIVE.matcher(joined).find();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
