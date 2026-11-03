# Atria 開發交接與待辦清單 (Handover & Next Steps)

本文件供跨 Session 接續開發使用，記錄目前進度、本機環境就緒狀態、Phase 0 具體實作項目與後續指令。

---

## 一、 目前進度與專案現況 (Current Status)

- **專案路徑**：`C:\Users\a1083\OneDrive\文件\atria`
- **Git 狀態**：`main` 分支，Phase 0 已完成並驗證通過
- **已完成工作**：
  - [x] Monorepo 根目錄結構、`.gitignore` (Java/Node/IDE/機密排除)、`LICENSE` (MIT)、專案根目錄輕量索引 `CLAUDE.md`
  - [x] 三語說明文件：`README.md` (EN)、`README.zh-TW.md` (繁中)、`README.ja.md` (日文)
  - [x] 系統架構圖與模組/資料表命名規範：`docs/ARCHITECTURE.md`
  - [x] 七階段垂直切片實作里程碑：`docs/ROADMAP.md`
  - [x] 7 份全英文架構決策紀錄：`docs/adr/ADR-0001` ~ `ADR-0007`（含 `ADR-0004` 三層加密與 DBA 明文救急強制改密規範）
  - [x] Phase 0 後端基礎底座、三語 i18n、統一例外處理與整合測試通過

---

## 二、 本機環境準備 (Environment Checklist)

| 工具 | 目前狀態 | 建議動作 / 安裝指令 |
| :--- | :--- | :--- |
| **Node.js** | ✅ 已安裝 (`v24.14.1`) | 前端環境就緒，Phase 2 時直接可用 |
| **Java JDK 21** | ✅ 已安裝 (`Temurin 21.0.13 LTS`) | 路徑：`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| **Maven Wrapper** | ✅ 已內建 (`mvnw` / `mvnw.cmd`) | 自動管理 Apache Maven `3.9.9` |
| **PostgreSQL 16** | ⏳ 選配 | 本地開發預設啟用 H2 PostgreSQL 相容模式；正式環境再連線 PostgreSQL 16 |

---

## 三、 Phase 0 基礎底座實作狀態 (已完成 ✅)

### 1. 後端專案骨架建置 (`backend/` - `atria-backend`)
- [x] 建立 `backend/pom.xml` (`io.github.skr416.atria:atria-backend:0.1.0-SNAPSHOT`，含 Spring Boot 3.3.5, Validation, Data JPA, PostgreSQL, H2, Flyway, Springdoc OpenAPI, Apache POI SXSSF, Lombok)
- [x] 補齊 Maven Wrapper 腳本（`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`）
- [x] 建立主入口類別 `io.github.skr416.atria.AtriaApplication.java`（強制設定全域 UTC 時區）

### 2. 多語系與統一例外/響應底座 (Three-Tier i18n Foundation)
- [x] 建立統一響應封裝：`ApiResponse<T>` 與 `PageResult<T>`
- [x] 建立錯誤碼與例外體系：`ErrorCode`、`BusinessException`、`GlobalExceptionHandler`
- [x] 建立三語系資源檔 (`src/main/resources/i18n/`)：`messages_zh_TW.properties`、`messages_en_US.properties`、`messages_ja_JP.properties`
- [x] 配置 `I18nConfig`、`OpenApiConfig`、`CorsConfig` 及基礎抽象（`BaseAuditEntity`、`FileStorageService`、`NotificationChannel`）

### 3. 多環境組態檔 (H2 免等快速啟動 / PostgreSQL 正式模式)
- [x] `application.yml`、`application-local.yml`、`application-prod.yml`

### 4. 驗證端點與單元測試
- [x] 建立 `HealthCheckController`（`/api/v1/health` 與 `/api/v1/i18n/test`）
- [x] 撰寫並通過 `AtriaApplicationTests` 4 項整合測試（驗證預設 `zh-TW`、`en-US`、`ja-JP` 語系解析與 `BusinessException` 三語錯誤翻譯）

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
請讀取 CLAUDE.md、docs/NEXT_STEPS.md、docs/ROADMAP.md 與 docs/adr/ADR-0004-rbac-and-department-data-scope.md，
我們現在正式開始進行 Phase 1（IAM、部門樹、RBAC 資料範圍、JWT 認證、DBA 明文救急強制改密與 AES-256-GCM 欄位加密轉換器）的後端與前端實作。
```
