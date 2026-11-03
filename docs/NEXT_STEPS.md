# Atria 開發交接與待辦清單 (Handover & Next Steps)

本文件供跨 Session 接續開發使用，記錄目前進度、本機環境就緒狀態、已完成里程碑與下一階段（Phase 2）開工指令。

---

## 一、 目前進度與專案現況 (Current Status)

- **專案路徑**：`C:\Users\a1083\OneDrive\文件\atria`
- **Git 狀態**：`main` 分支，Phase 0 (`v0.1.0-foundation`) 與 Phase 1 (`v0.2.0-iam`) 已完成並通過 12 項整合測試
- **已完成工作**：
  - [x] **Phase 0 基礎底座**：Monorepo 結構、三語 README、7 份英文 ADR (`ADR-0001` ~ `ADR-0007`)、Spring Boot 3.3.5 + Java 21 骨架、統一響應 `ApiResponse<T>` / `PageResult<T>`、三語 `ErrorCode` i18n、`ClientIpResolver`、`HmacSignatureVerifier`。
  - [x] **Phase 1 IAM 與權限核心 (`v0.2.0-iam`)**：
    - [x] `AesGcmCipher` 與 `AesGcmStringConverter`（AES-256-GCM 96-bit 隨機 IV + `{v1}` 版本前綴欄位透明加解密）。
    - [x] Flyway `V1__init_iam_schema.sql`（PostgreSQL 16 與 H2 雙版本：`iam_department`, `iam_user`, `iam_role`, `iam_permission`, `iam_user_role`, `iam_role_permission`, `iam_audit_log` 及預設角色權限種子資料）。
    - [x] Spring Security 6 + Stateless JWT（`access` / `pwd_change` / `refresh` HttpOnly Cookie）、BCrypt (strength 12)、連續 5 次失敗鎖定 15 分鐘、DBA 明文/`{noop}` 救急單次登入自動觸發強制改密攔截閘門（`ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED` 1003）。
    - [x] `@DataScope` AOP 資料範圍過濾（`ALL`, `DEPT_AND_SUB`, `DEPT_ONLY`, `SELF_ONLY`）與 `@AuditLog` AOP 管理異動稽核軌跡。
    - [x] 部門樹 CRUD（含搬移節點時子孫 `tree_path` 自動級聯更新與防迴圈保護）、角色與權限 CRUD、使用者 CRUD（含軟刪除釋放帳號重用、管理員重設密碼與手動解鎖）。
    - [x] 通過 `AtriaApplicationTests` (6 項) + `Phase1IamIntegrationTests` (6 項) 共 12 項整合測試。

---

## 二、 本機環境準備 (Environment Checklist)

| 工具 | 目前狀態 | 建議動作 / 安裝指令 |
| :--- | :--- | :--- |
| **Node.js** | ✅ 已安裝 (`v24.14.1`) | 前端環境就緒，Phase 2 直接可用 |
| **Java JDK 21** | ✅ 已安裝 (`Temurin 21.0.13 LTS`) | 路徑：`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| **Maven Wrapper** | ✅ 已內建 (`mvnw` / `mvnw.cmd`) | 自動管理 Apache Maven `3.9.9` |
| **PostgreSQL 16** | ⏳ 選配 | 本地開發預設啟用 H2 PostgreSQL 相容模式；正式環境再連線 PostgreSQL 16 |

---

## 三、 下一階段待辦：Phase 2 前端三大廳殼層與 IAM 管理後台 (`v0.3.0-frontend-shell`)

1. **前端專案骨架與共用底座 (`frontend/` - `atria-frontend`)**
   - [ ] 初始化 Vue 3 + Vite + TypeScript + PrimeVue + Lucide Icons + Pinia + Vue Router + `vue-i18n`（支援 `zh-TW`, `en-US`, `ja-JP` 三語即時切換與高密度日系 B2B 排版）。
   - [ ] 建立統一 Axios 客戶端與攔截器（自動附帶 Pinia 記憶體中的 Access Token 與 `Accept-Language` Header、401 自動透過 `/api/v1/auth/refresh` HttpOnly Cookie 換證、遇到 `1003` 或 `mustChangePassword` 強制導向改密頁）。
2. **三大廳 Layout 殼層與認證頁面**
   - [ ] 實作 `PublicLayout` (`/public`)、`PortalLayout` (`/portal`)、`AdminLayout` (`/admin`) 與路由守衛。
   - [ ] 實作登入頁 (`/login`) 與初次/DBA 救急強制變更密碼頁 (`/change-password`)。
3. **IAM 組織與權限管理後台 (`/admin/iam/*`)**
   - [ ] 部門樹管理頁（階層式 TreeTable、新增/編輯/搬移上層部門、設定部門時區與主管）。
   - [ ] 使用者管理頁（搭配 `@DataScope` 分頁查詢、關鍵字與部門篩選、新增/編輯員工、角色指派、重設密碼、手動解鎖帳號）。
   - [ ] 角色與權限配置頁（檢視/新增自訂角色、設定 `DataScopeType` 與勾選權限點）及稽核日誌查詢頁。

---

## 四、 新 Session 開工引導 Prompt (直接複製貼上)

新 Session 開始時，可直接貼上以下提示詞喚醒 Agent：

```text
我們正在開發 Atria 專案（路徑：C:\Users\a1083\OneDrive\文件\atria）。
請讀取 CLAUDE.md、docs/NEXT_STEPS.md、docs/ROADMAP.md、docs/adr/ADR-0002-three-tier-i18n-architecture.md 與 docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md，
我們現在正式開始進行 Phase 2（Vue 3 前端三大廳殼層 /public, /portal, /admin、三語切換、JWT 無感換證與強制改密頁、以及 IAM 部門樹/使用者/角色管理後台）。
請依規範先思考並完成前端共用區域後，提出 Subagent 派工計畫（預計幾個 agent、各自負責範圍、主對話驗收方式）。
```
