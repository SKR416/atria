package io.github.skr416.atria;

import io.github.skr416.atria.common.util.ClientIpResolver;
import io.github.skr416.atria.common.util.HmacSignatureVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AtriaApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Health check endpoint returns UP and defaults to zh-TW locale")
    void healthEndpointShouldReturnUpWithDefaultTraditionalChinese() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("atria-backend"))
                .andExpect(jsonPath("$.data.locale").value("zh-TW"))
                .andExpect(jsonPath("$.data.welcome").value("歡迎使用 Atria 企業考勤與多層級審批工作流平台"));
    }

    @Test
    @DisplayName("i18n endpoint resolves English (en-US) greeting and message")
    void i18nEndpointShouldResolveEnglishLocale() throws Exception {
        mockMvc.perform(get("/api/v1/i18n/test")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Operation completed successfully"))
                .andExpect(jsonPath("$.data.resolvedLocale").value("en-US"))
                .andExpect(jsonPath("$.data.greeting").value("Welcome to Atria Attendance & Approval Workflow Platform"));
    }

    @Test
    @DisplayName("i18n endpoint resolves Japanese (ja-JP) greeting and message")
    void i18nEndpointShouldResolveJapaneseLocale() throws Exception {
        mockMvc.perform(get("/api/v1/i18n/test")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("処理が正常に完了しました"))
                .andExpect(jsonPath("$.data.resolvedLocale").value("ja-JP"))
                .andExpect(jsonPath("$.data.greeting").value("Atria 勤怠管理・多段階承認ワークフロープラットフォームへようこそ"));
    }

    @Test
    @DisplayName("BusinessException translates ErrorCode across zh-TW, en-US, and ja-JP")
    void businessExceptionShouldTranslateAcrossAllThreeLocales() throws Exception {
        // 1. Traditional Chinese (zh-TW)
        mockMvc.perform(get("/api/v1/i18n/test").param("error", "true")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-TW"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("該申請單已全數核准結案，無法直接撤回，請改提銷假或變更申請"));

        // 2. English (en-US)
        mockMvc.perform(get("/api/v1/i18n/test").param("error", "true")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("This request has already been finally approved and cannot be revoked directly"));

        // 3. Japanese (ja-JP)
        mockMvc.perform(get("/api/v1/i18n/test").param("error", "true")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ja-JP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3002))
                .andExpect(jsonPath("$.message").value("この申請は既に最終承認されているため、直接取り下げることはできません"));
    }

    @Test
    @DisplayName("HmacSignatureVerifier validates fresh signature and blocks expired replay or tampered payload")
    void hmacSignatureVerifierShouldProtectBotEndpoints() {
        String secret = "atria-bot-hmac-secret-2026";
        String payload = "{\"userId\":101,\"action\":\"CLOCK_IN\"}";
        long now = Instant.now().getEpochSecond();

        String validSig = HmacSignatureVerifier.sign(secret, now, payload);
        assertTrue(HmacSignatureVerifier.verify(secret, now, payload, validSig));

        // Tampered payload must fail
        assertFalse(HmacSignatureVerifier.verify(secret, now, "{\"userId\":999,\"action\":\"CLOCK_IN\"}", validSig));

        // Replay attack older than 5 minutes (300s) must fail
        long expiredTimestamp = now - 600L;
        String expiredSig = HmacSignatureVerifier.sign(secret, expiredTimestamp, payload);
        assertFalse(HmacSignatureVerifier.verify(secret, expiredTimestamp, payload, expiredSig));
    }

    @Test
    @DisplayName("ClientIpResolver extracts first real IP from X-Forwarded-For proxy chain")
    void clientIpResolverShouldExtractRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.42, 10.0.0.1");
        assertEquals("203.0.113.42", ClientIpResolver.resolve(request));
    }
}
