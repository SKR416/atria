package io.github.skr416.atria.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signature generator and constant-time verifier with timestamp replay defense
 * for authorized company Bot punch/notification endpoints.
 */
public final class HmacSignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long DEFAULT_MAX_SKEW_SECONDS = 300L; // +/- 5 minutes

    private HmacSignatureVerifier() {
    }

    /**
     * Computes hex-encoded HMAC-SHA256 signature for "{timestamp}.{payload}".
     */
    public static String sign(String secretKey, long epochSeconds, String payload) {
        try {
            String canonical = epochSeconds + "." + (payload != null ? payload : "");
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", ex);
        }
    }

    /**
     * Verifies both timestamp freshness (+/- 5 minutes) and constant-time HMAC equality.
     */
    public static boolean verify(String secretKey, long requestEpochSeconds, String payload, String providedHexSignature) {
        if (secretKey == null || secretKey.isBlank() || providedHexSignature == null || providedHexSignature.isBlank()) {
            return false;
        }
        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - requestEpochSeconds) > DEFAULT_MAX_SKEW_SECONDS) {
            return false;
        }
        String expectedHex = sign(secretKey, requestEpochSeconds, payload);
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                providedHexSignature.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }
}
