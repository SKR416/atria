# Atria 開發交接與待辦清單 (Handover & Next Steps)

本文件供跨 Session 接續開發使用，記錄目前進度、本機環境就緒狀態、已完成里程碑與下一階段（Phase 3a）開工指令。

---

## 一、 目前進度與專案現況 (Current Status)

- **專案路徑**：`C:\Users\a1083\OneDrive\文件\atria`
- **Git 狀態**：`main` 分支，Phase 0 (`v0.1.0-foundation`)、Phase 1 (`v0.2.0-iam`) 與 Phase 2 (`v0.3.0-frontend-shell`) 已完成實作與驗證（通過後端 13 項整合測試 + 前端 `vue-tsc` 與 `vite build` 打包）
- **已完成工作**：
  - [x] **Phase 0 基礎底座 (`v0.1.0-foundation`)**：Monorepo 結構、三語 README、ADR 文件群、Spring Boot 3.3.5 + Java 21 骨架、統一響應 `ApiResponse<T>` / `PageResult<T>`、三語 `ErrorCode` i18n、`ClientIpResolver`、`HmacSignatureVerifier`。
  - [x] **Phase 1 IAM 與權限核心 (`v0.2.0-iam`)**：
    - [x] `AesGcmCipher` 與 `AesGcmStringConverter`（AES-256-GCM 96-bit 隨機 IV + `{v1}` 版本前綴欄位透明加解密）。
    - [x] Flyway `V1__init_iam_schema.sql`（PostgreSQL 16 與 H2 雙版本：`iam_department`, `iam_user`, `iam_role`, `iam_permission`, `iam_user_role`, `iam_role_permission`, `iam_audit_log`）。
    - [x] Spring Security 6 + Stateless JWT（`access` / `pwd_change` / `refresh` HttpOnly Cookie）、BCrypt (strength 12)、連續 5 次失敗鎖定 15 分鐘、DBA 明文/`{noop}` 救急單次登入強制改密閘門（`ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED` 1003）。
    - [x] `@DataScope` AOP 資料範圍過濾與 `@AuditLog` AOP 管理異動稽核軌跡。
    - [x] 部門樹 CRUD（含子孫 `tree_path` 級聯更新與防迴圈保護）、角色與權限 CRUD、使用者 CRUD（含軟刪除釋放帳號重用、管理員重設密碼與手動解鎖）。
  - [x] **Phase 2 前端三大廳殼層與 IAM 管理後台 (`v0.3.0-frontend-shell`)**：
    - [x] 初始化 `frontend/` (`atria-frontend`)：Vue 3.5 + Vite 6 + TypeScript + PrimeVue 4 (Aura) + Lucide Icons + Pinia + Vue Router + `vue-i18n`（支援 `zh-TW`, `en-US`, `ja-JP` 三語即時切換與高密度日系 B2B 排版）。
    - [x] 實作 Pinia 記憶體 Access Token 管理與 Axios 攔截器（自動附帶 `Authorization` 與 `Accept-Language`、401 自動透過 `/api/v1/auth/refresh` 無感換證、攔截 `1003` 強制導向 `/change-password`）。
    - [x] 實作三大廳 Layout 殼層（`PublicLayout` `/public`、`PortalLayout` `/portal`、`AdminLayout` `/admin`）及登入頁 (`/login`)、強制變更密碼頁 (`/change-password`)。
    - [x] 實作 IAM 後台管理頁面：部門組織樹 (`DepartmentManageView.vue` `TreeTable`)、員工帳號管理 (`UserManageView.vue`)、角色與權限矩陣 (`RoleManageView.vue`)、系統稽核日誌 (`AuditLogView.vue`)。
    - [x] 依 `ADR-0008` Decision 11 新增 Flyway `V2__add_user_hire_date.sql`（PostgreSQL & H2 `iam_user.hire_date DATE`），完成後端 `User` Entity / DTO / 第 7 項整合測試，以及前端員工管理列表「到職日」欄位與 `DatePicker`（一般員工必填，僅 `admin`/`sys_*`/`bot_*` 或 `SUPER_ADMIN` 系統帳號可免填）。

---

## 二、 本機環境準備 (Environment Checklist)

| 工具 | 目前狀態 | 建議動作 / 安裝指令 |
| :--- | :--- | :--- |
| **Node.js** | ✅ 已安裝 (`v24.14.1`) | `frontend/` 依賴已安裝，`npm run build` 驗證通過 |
| **Java JDK 21** | ✅ 已安裝 (`Temurin 21.0.13 LTS`) | 路徑：`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| **Maven Wrapper** | ✅ 已內建 (`mvnw` / `mvnw.cmd`) | `cd backend && ./mvnw.cmd test` 通過 13 項整合測試 |
| **PostgreSQL 16** | ⏳ 選配 / Phase 3+ 整合測試目標 | 本地開發預設支援 H2 與 PostgreSQL 16 雙語法 Migration |

---

## 三、 下一階段待辦：Phase 3a 差勤核心、多地區行事曆與員工自助廳 (`v0.4.0-attendance-core`)

依 `docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md` 與 `docs/adr/ADR-0008-attendance-leave-overtime-refinements.md`（Decision 1, 2, 3, 7, 13）：

1. **Flyway Migration `V3__init_attendance_core_schema.sql`（PostgreSQL 與 H2 雙版本）**
   - [ ] `iam_department` 新增 `calendar_code VARCHAR(16) DEFAULT 'TW'`。
   - [ ] 建立 `att_work_calendar`（含 `calendar_code`、`work_date`、`day_type`：`WORKDAY` | `MAKEUP_WORKDAY` | `REST_DAY` | `STATUTORY_REST_DAY` | `NATIONAL_HOLIDAY`、標準上下班時間與三語假日名稱 `JSONB`）。
   - [ ] 建立 `att_punch_rule`（寬限分鐘數、取整規則、`day_cutoff_hour` 預設 `4` 點跨日切算、午休固定扣除區間 `12:00`–`13:00`）。
   - [ ] 建立不可竄改原始打卡軌跡表 `att_punch_log`（Append-only：`user_id`, `raw_punch_time` UTC, `client_ip`, `source` `WEB`|`BOT`|`CORRECTION`, `bot_client_id`）與每日衍生考勤彙總表 `att_daily_record`（`work_date`, `effective_clock_in`, `effective_clock_out`, `worked_minutes`, `status`, `@Version`）。
   - [ ] 建立月結關帳鎖定表 `att_monthly_close`。
2. **後端差勤核心服務與 REST API (`attendance` 模組)**
   - [ ] 多地區企業行事曆服務与「產生整年行事曆」API：內建台灣 (`TW`) 與日本 (`JP`) 法定例假日/休息日/國定假日演算法範本，供 HR 一鍵預填後微調。
   - [ ] 打卡 API（Web 員工打卡 + HMAC 防重放簽章驗證之代理 Bot 打卡）：寫入 `att_punch_log` 並即時重算當日 `att_daily_record`。
   - [ ] 每日跨日切算排程 (`DailySettlementJob`) 與月結關帳 API（檢查若有未決 `PENDING` 申請單則阻擋關帳並回傳清單）。
3. **前端 `/portal` 員工自助大廳與 `/admin` 人資行事曆後台**
   - [ ] `/portal` 實作即時打卡卡片（顯示部門時區時間、今日上下班狀態）與個人月曆檢視。
   - [ ] `/admin/attendance/calendar` 與 `/admin/attendance/monthly-close` 實作多地區行事曆產生/編輯器與月結關帳管理頁。

---

## 四、 新 Session 開工引導 Prompt (直接複製貼上)

新 Session 開始時，可直接貼上以下提示詞喚醒 Agent：

```text
我們正在開發 Atria 專案（路徑：C:\Users\a1083\OneDrive\文件\atria）。
請讀取 CLAUDE.md、docs/NEXT_STEPS.md、docs/ROADMAP.md、docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md 與 docs/adr/ADR-0008-attendance-leave-overtime-refinements.md，
我們現在正式開始進行 Phase 3a（差勤核心：Flyway V3 多地區行事曆 TW/JP 範本、不可竄改 att_punch_log + 衍生 att_daily_record、Web/Bot 打卡、每日日結與月結關帳、以及前端 /portal 打卡月曆與 /admin 行事曆管理 UI）。
請依規範先思考並完成共用區域（V3 SQL、Entity、ErrorCode 三語訊息、DTO 與前端 API 型別）後，提出 Subagent 派工計畫。
```
