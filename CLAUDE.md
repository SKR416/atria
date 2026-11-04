# Atria Project Rules & Index

Atria 是結合差勤（Kintai）與動態表單簽核（Workflow）的企業級模組化單體系統（Spring Boot 3.3 + Java 21 + Vue 3 + PostgreSQL/H2，支援繁中/英/日三語）。

## 1. 常用建置與測試指令 (Build & Verify)
- **後端編譯檢查**：`cd backend && ./mvnw.cmd test-compile`
- **後端執行測試**：`cd backend && ./mvnw.cmd test`
- **JDK 21 路徑**：`C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot`

## 2. 按需讀取路由表 (Read on Demand — 避免平時塞滿 Context)
平時**不要**一次讀取所有文件；僅在觸及對應領域時讀取（英文原版於 `docs/adr/`，繁中對照於 `docs/adr/zh-TW/`）：
- **跨 Session 進度與接續待辦** ➔ `docs/NEXT_STEPS.md`、`docs/ROADMAP.md`
- **模組邊界、套件結構、資料表命名 (`iam_`/`att_`/`wf_`/`ntf_`/`ann_`/`ai_`)** ➔ `docs/ARCHITECTURE.md`、`docs/adr/ADR-0001-modular-monolith-and-tech-stack.md`
- **多語系 (i18n)、錯誤碼 (`ErrorCode`)、時區 (UTC 儲存)** ➔ `docs/adr/ADR-0002-three-tier-i18n-architecture.md`
- **差勤打卡、雙表 (`att_punch_log` + `att_daily_record`)、台日行事曆與假勤加班規則 (Phase 3a/3b)** ➔ `docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md`、`docs/adr/ADR-0008-attendance-leave-overtime-refinements.md`
- **RBAC 權限、JWT 認證、部門資料範圍 (Data Scope)** ➔ `docs/adr/ADR-0004-rbac-and-department-data-scope.md`
- **動態表單 Schema 版本控制、簽核狀態機、併發樂觀鎖** ➔ `docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md`、`docs/adr/ADR-0008-attendance-leave-overtime-refinements.md`
- **AI 模組與非同步語音轉文字 (STT)** ➔ `docs/adr/ADR-0006-ai-module-scope-and-async-stt.md`

## 3. 核心程式碼慣例 (Core Conventions)
- **統一響應與例外**：Controller 一律回傳 `ApiResponse<T>` / `PageResult<T>`；業務邏輯錯誤拋出 `BusinessException(ErrorCode)`，嚴禁直接硬編碼錯誤訊息字串。
- **三語同步更新**：每當新增 `ErrorCode`，必須同步更新 `messages_zh_TW.properties`、`messages_en_US.properties`、`messages_ja_JP.properties` 與預設 `messages.properties`。
- **階段收尾**：每完成一個垂直切片或獨立階段並 commit 後，更新 `docs/NEXT_STEPS.md` 並提醒使用者執行 `/clear`。
