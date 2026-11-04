# ADR-0006：務實的 AI 模組範疇（對話、文字轉換與非同步語音轉文字）

> 本文件為 [ADR-0006 英文版](../ADR-0006-ai-module-scope-and-async-stt.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納
- **日期：** 2026-10-28

## 背景

Atria 內建嵌入式 AI 模組（`modules/ai`），旨在協助員工處理日常工作流程。為確保架構務實且具可維護性：
- 我們避免過度承諾自主性的「AI-Agentic」工作流程，而是專注於三項具體且實用性高的功能：
  1. **嵌入式 AI 對話**（`ai_chat_session`、`ai_chat_message`），具備 Server-Sent Events (SSE) 串流。
  2. **職場文字轉換**（將口語非正式的請假事由修飾為正式商務用語、摘要會議記錄、以及在 `zh-TW`、`en-US` 與 `ja-JP` 之間進行翻譯）。
  3. **語音轉文字（STT）**（`ai_stt_job`），將上傳的錄音檔轉錄為文字逐字稿。
- 語音轉錄可能需要數十秒至數分鐘，絕不能阻塞 HTTP 請求執行緒。
- 本機開發與 CI 流程必須能在不需要付費第三方 API 金鑰的情況下順暢執行。

## 決策

1. **可插拔的 `AiProvider` 抽象層：**
   - 在 `io.github.skr416.atria.modules.ai` 中定義乾淨的 `ChatProvider` 與 `SpeechToTextProvider` 介面。
   - 提供預設的 `MockAiProvider` Profile，可在完全不需要外部 API 金鑰的情況下，於本機模擬 SSE Token 串流與 STT 轉錄，並同時提供可設定的正式環境介接 Adapter。
   - **正式環境 STT 引擎（`whisper.cpp`）：** 語音轉文字使用獨立的 `whisper.cpp` HTTP 服務（或 `faster-whisper` 容器）。將原生推論引擎運行於與 JVM 解耦的獨立行程中，消除了 JVM 崩潰風險（例如 C/C++ JNI 崩潰導致的嚴重 Segmentation Fault），並確保 Spring Boot 在核心差勤作業時的穩定性。
2. **非同步 STT 工作佇列（`ai_stt_job`）：**
   - 上傳音訊檔案時，透過 `FileStorageService` 儲存檔案，建立一筆狀態為 `PENDING` 的 `ai_stt_job` 資料列，並立即將 `jobId` 回傳給客戶端。
   - 由背景 Worker 執行緒池處理該工作（`PENDING` -> `PROCESSING` -> `COMPLETED` / `FAILED`），並儲存產出的逐字稿文字與音訊時長。
3. **用量配額追蹤（`ai_usage_quota`）：**
   - 依使用者按月追蹤 Token 數量與語音轉錄秒數，防止 API 成本失控暴增。

## 影響

- 審查者與開發者在 Clone 專案後，即可開箱使用 Mock Provider 測試完整的 AI UI（串流對話、文字轉換、音訊上傳與轉錄生命週期），亦可透過設定切換至真實的 LLM/STT API。
