# Atria 開發交接與待辦清單 (Handover & Next Steps)

本文件供跨 Session 接續開發使用，記錄目前進度、本機環境就緒狀態、Phase 0 具體實作項目與後續指令。

---

## 一、 目前進度與專案現況 (Current Status)

- **專案路徑**：`C:\Users\a1083\OneDrive\文件\atria`
- **Git 狀態**：`main` 分支，目前 Commit `cd23c69`
- **已完成工作**：
  - [x] Monorepo 根目錄結構、`.gitignore` (Java/Node/IDE/機密排除)、`LICENSE` (MIT)
  - [x] 三語說明文件：`README.md` (EN)、`README.zh-TW.md` (繁中)、`README.ja.md` (日文)
  - [x] 系統架構圖與模組/資料表命名規範：`docs/ARCHITECTURE.md`
  - [x] 七階段垂直切片實作里程碑：`docs/ROADMAP.md`
  - [x] 7 份全英文架構決策紀錄：`docs/adr/ADR-0001` ~ `ADR-0007`

---

## 二、 本機環境準備 (Environment Checklist)

| 工具 | 目前狀態 | 建議動作 / 安裝指令 |
| :--- | :--- | :--- |
| **Node.js** | ✅ 已安裝 (`v24.14.1`) | 前端環境就緒，Phase 2 時直接可用 |
| **Java JDK 21** | ❌ 尚未安裝 | 於 CMD/PowerShell 執行：<br>`winget install EclipseAdoptium.Temurin.21.JDK`<br>*(重開終端機後 `java -version` 確認)* |
| **PostgreSQL 16** | ❌ 尚未安裝 | 於 CMD/PowerShell 執行：<br>`winget install PostgreSQL.PostgreSQL.16`<br>*(Phase 0 初期可先使用 H2 內嵌資料庫免安裝起跑)* |

---

## 三、 Phase 0 即刻待實作項目清單 (Action Items for Next Session)

新 Session 啟動後，直接執行 **Phase 0 (基礎底座建置)**：

### 1. 後端專案骨架建置 (`backend/` - `atria-backend`)
- [ ] 建立 `backend/pom.xml`：
  - `groupId`: `io.github.skr416.atria`
  - `artifactId`: `atria-backend`
  - `version`: `0.1.0-SNAPSHOT`
  - 依賴項：`spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `postgresql`, `h2`, `flyway-core`, `springdoc-openapi-starter-webmvc-ui`, `lombok`
- [ ] 補齊 Maven Wrapper 腳本（`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`）
- [ ] 建立主入口類別 `io.github.skr416.atria.AtriaApplication.java`

### 2. 多語系與統一例外/響應底座 (Three-Tier i18n Foundation)
- [ ] 建立統一響應封裝：
  - `io.github.skr416.atria.common.api.ApiResponse<T>` (`code`, `message`, `data`, `timestamp`)
  - `io.github.skr416.atria.common.api.PageResult<T>` (`list`, `total`, `page`, `size`)
- [ ] 建立錯誤碼與例外體系：
  - `ErrorCode` (Enum: `SUCCESS(200, "success")`, `SYSTEM_ERROR(500, "error.system")`, `VALIDATION_FAILED(400, "error.validation")`...)
  - `BusinessException` (繼承 `RuntimeException`，攜帶 `ErrorCode` 與可變參數)
  - `GlobalExceptionHandler` (@RestControllerAdvice，攔截 `BusinessException` 與各類校驗例外)
- [ ] 建立三語系資源檔 (`src/main/resources/i18n/`)：
  - `messages_zh_TW.properties` (繁中預設)
  - `messages_en_US.properties` (英文)
  - `messages_ja_JP.properties` (日文)
- [ ] 配置 `I18nConfig` (整合 `AcceptHeaderLocaleResolver`，預設 Locale 為 `zh_TW`)

### 3. 多環境組態檔 (H2 免等快速啟動 / PostgreSQL 正式模式)
- [ ] `application.yml` (通用設定：JPA、i18n 路徑、OpenAPI)
- [ ] `application-local.yml` (預設啟用 H2 內嵌記憶體資料庫 + H2 Web Console，開箱即測)
- [ ] `application-prod.yml` (PostgreSQL 16 + 連線池)

### 4. 驗證端點與單元測試
- [ ] 建立 `HealthCheckController`：
  - 提供 `/api/v1/health` 端點
  - 提供 `/api/v1/i18n/test` 端點（驗證送出 `Accept-Language: ja-JP`、`en-US`、`zh-TW` 時能正確回傳對應語言之訊息）
- [ ] 驗證並提交第一個功能 Tag：`git tag v0.1.0-foundation`

---

## 四、 GitHub 遠端儲存庫設定 (手動或等後續推送)

當準備好推送到 GitHub 時：
- **Repo 名稱**：`SKR416/atria`
- **About**：*Atria: attendance and approval workflows in one platform. Spring Boot 3 + Vue 3 modular monolith with RBAC, dynamic forms, and trilingual i18n (zh-TW / EN / JA).*
- **Topics**：`kintai`, `workflow`, `approval`, `spring-boot`, `vue3`, `rbac`, `i18n`, `postgresql`

---

## 五、 新 Session 開工引導 Prompt (直接複製貼上)

新 Session 開始時，可直接貼上以下提示詞喚醒 Agent：

```text
我們正在開發 Atria 專案（路徑：C:\Users\a1083\OneDrive\文件\atria）。
請讀取 docs/NEXT_STEPS.md、docs/ROADMAP.md 與 docs/ARCHITECTURE.md，
我們現在開始進行 Phase 0 的後端程式碼實作（建立 atria-backend 骨架、統一 ApiResponse、三語系 ErrorCode 與測試端點）。
```
