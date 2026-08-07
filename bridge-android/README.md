# NEXUS Universal Bridge 2.1.0

Build-only Android client isolated from all active NEXUS repositories.

Core flow:
1. App generates device identity and random token locally; token is encrypted with Android Keystore.
2. App requests pairing from the isolated Supabase `nexus_bridge` service. No Google login.
3. ChatGPT/operator approves the pending device server-side.
4. Chat task appears automatically in the app.
5. User opens the target URL inside the app WebView and personally completes username/password/2FA/CAPTCHA.
6. User presses `ACCESSO COMPLETATO — CONTINUA`.
7. Only then can queued agent actions be delivered. Agent typing is blocked for password/OTP/token/secret fields.
8. The WebView keeps the authenticated session on-device. Only sanitized interactive-element metadata is returned; input values/cookies/storage/auth headers are never sent.
9. Result stays tied to the originating correlation ID.

This branch is a temporary build runner and is never merged into `main`.
