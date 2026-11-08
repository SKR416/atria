# Atria Project Rules & Index

Atria 是結合差勤（Kintai）與動態表單簽核（Workflow）的企業級模組化單體系統（Spring Boot 3.3 + Java 21 + Vue 3 + PostgreSQL/H2，支援繁中/英/日三語）。

## 1. 常用建置與測試指令 (Build & Verify)
- **後端編譯檢查**：`cd backend && ./mvnw.cmd test-compile`
- **後端執行測試**：`cd backend && ./mvnw.cmd test`
- **JDK 21 路徑**：`C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot`
- **本機服務啟動與瀏覽器預覽**：因路徑含中文，後端**嚴禁使用 `spring-boot:run`**，一律先 `./mvnw.cmd package -DskipTests` 再以 `java -Dfile.encoding=UTF-8 -jar target/atria-backend-0.1.0-SNAPSHOT.jar` 背景啟動（完整啟動四步驟、測試帳號與 UI/i18n 驗收清單見 `docs/LOCAL_PREVIEW_SOP.md`，可委派 `haiku-scout` 執行打包與探針檢查）。
- **知識圖譜檢索 (`graphify`)**：
  - 查波及範圍：`graphify affected "<Symbol>"`
  - 查呼叫/關聯路徑：`graphify path "<NodeA>" "<NodeB>"`
  - 查概念與跨檔關聯：`graphify query "<Question>"`
  - 增量更新圖譜（純 AST 零 Token 消耗）：`graphify update .`

## 2. 按需讀取路由表 (Read on Demand — 避免平時塞滿 Context)
平時**不要**一次讀取所有文件；跨檔案結構探索或修改既有類別前，優先使用 `graphify` 定位精確的 `檔案:行號`，再按需讀取對應區間（英文原版於 `docs/adr/`，繁中對照於 `docs/adr/zh-TW/`）：
- **跨 Session 進度與接續待辦** ➔ `docs/NEXT_STEPS.md`、`docs/ROADMAP.md`
- **本機前後端啟動、瀏覽器預覽與 UI/i18n 驗收 SOP** ➔ `docs/LOCAL_PREVIEW_SOP.md`
- **階段完成 / `git push` 前三語 README 與 ADR 對齊檢驗清單** ➔ `docs/README_SYNC_CHECKLIST.md`
- **模組邊界、套件結構、資料表命名 (`iam_`/`att_`/`wf_`/`ntf_`/`ann_`/`ai_`)** ➔ `docs/ARCHITECTURE.md`、`docs/adr/ADR-0001-modular-monolith-and-tech-stack.md`
- **多語系 (i18n)、錯誤碼 (`ErrorCode`)、時區 (UTC 儲存)** ➔ `docs/adr/ADR-0002-three-tier-i18n-architecture.md`
- **差勤打卡、雙表 (`att_punch_log` + `att_daily_record`)、台日行事曆與假勤加班規則 (Phase 3a/3b)** ➔ `docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md`、`docs/adr/ADR-0008-attendance-leave-overtime-refinements.md`
- **RBAC 權限、JWT 認證、部門資料範圍 (Data Scope)** ➔ `docs/adr/ADR-0004-rbac-and-department-data-scope.md`
- **動態表單 Schema 版本控制、簽核狀態機、併發樂觀鎖** ➔ `docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md`、`docs/adr/ADR-0008-attendance-leave-overtime-refinements.md`
- **AI 模組與非同步語音轉文字 (STT)** ➔ `docs/adr/ADR-0006-ai-module-scope-and-async-stt.md`

## 3. 核心程式碼慣例 (Core Conventions)
- **統一響應與例外**：Controller 一律回傳 `ApiResponse<T>` / `PageResult<T>`；業務邏輯錯誤拋出 `BusinessException(ErrorCode)`，嚴禁直接硬編碼錯誤訊息字串。
- **三語同步更新**：每當新增 `ErrorCode`，必須同步更新 `messages_zh_TW.properties`、`messages_en_US.properties`、`messages_ja_JP.properties` 與預設 `messages.properties`。
- **測試真實性鐵律**：測試失敗時必須先判斷是業務程式碼錯誤或測試案例錯誤；**嚴禁為了讓測試通過而擅自弱化斷言或偷改測試**，若確需修改測試必須明確說明業務理由。完成階段前必須通過後端 `./mvnw.cmd test` 與前端 `npm run lint && npm run build`。
- **前端可測試性標記**：新增的關鍵互動 UI 元素（如主要按鈕、表單輸入、對話框、狀態徽章）需加上語義化的 `data-testid` 屬性，便於自動化驗收。
- **階段收尾與推送門檻**：每完成一個垂直切片或獨立階段、或準備執行 `git push` 推進階段進度前，**必須先按需讀取並執行 `docs/README_SYNC_CHECKLIST.md`**，將三語 README（`README.md`、`README.zh-TW.md`、`README.ja.md`）、`docs/ROADMAP.md`、`docs/NEXT_STEPS.md`、`docs/ARCHITECTURE.md` 與相關 ADR 完整對齊當前實作進度；並執行 `graphify update .` 同步知識圖譜，於 commit / push 後提醒使用者執行 `/clear`。
