# ADR-0006: Practical AI Module Scope (Chat, Text Transformation, and Async STT)

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Atria includes an embedded AI module (`modules/ai`) to assist employees in their daily workflow. To keep the architecture grounded and maintainable:
- We avoid over-promising autonomous "AI-Agentic" workflows and instead focus on three concrete, high-utility capabilities:
  1. **Embedded AI Chat** (`ai_chat_session`, `ai_chat_message`) with Server-Sent Events (SSE) streaming.
  2. **Workplace Text Transformation** (polishing informal leave reasons into formal business prose, summarizing meeting notes, translating between `zh-TW`, `en-US`, and `ja-JP`).
  3. **Audio-to-Text Transcription (STT)** (`ai_stt_job`) for converting uploaded audio recordings into text transcripts.
- Audio transcription can take tens of seconds to several minutes and must not block HTTP request threads.
- Local development and CI must run cleanly without requiring paid third-party API keys.

## Decision

1. **Pluggable `AiProvider` Abstraction:**
   - Define clean `ChatProvider` and `SpeechToTextProvider` interfaces in `io.github.skr416.atria.modules.ai`.
   - Provide a default `MockAiProvider` profile that simulates SSE token streaming and STT transcription locally with zero external API keys, alongside configurable production adapters.
2. **Asynchronous STT Job Queue (`ai_stt_job`):**
   - Uploading an audio file stores the file via `FileStorageService`, creates an `ai_stt_job` row with status `PENDING`, and immediately returns the `jobId` to the client.
   - A background worker thread pool processes the job (`PENDING` -> `PROCESSING` -> `COMPLETED` / `FAILED`) and stores the resulting transcript text and duration.
3. **Usage Quota Tracking (`ai_usage_quota`):**
   - Track token counts and audio transcription seconds per user per month to prevent runaway API costs.

## Consequences

- Reviewers and developers can clone the repository and test the entire AI UI (streaming chat, text transformation, audio upload & transcription lifecycle) out of the box using the mock provider, or switch to live LLM/STT APIs via configuration.
