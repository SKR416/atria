# Atria 開發交接與待辦清單 (Handover & Next Steps)

本文件供跨 Session 接續開發使用，記錄目前進度、本機環境就緒狀態、已完成里程碑與下一階段（Phase 3b）開工指令。

---

## 一、 目前進度與專案現況 (Current Status)

- **專案路徑**：`C:\Users\a1083\OneDrive\文件\atria`
- **Git 狀態**：`main` 分支，Phase 0 (`v0.1.0-foundation`)、Phase 1 (`v0.2.0-iam`)、Phase 2 (`v0.3.0-frontend-shell`)、Phase 3a (`v0.4.0-attendance-core`) 與 Phase 3a.5（品質閘門、ArchUnit 架構測試與 `att_punch_log` 雙層防竄改加固）已完成實作與驗證（通過後端 24 項測試 + 前端 `npm run lint` 0 警告與 `npm run build` 打包）
- **已完成工作**：
  - [x] **Phase 0 基礎底座 (`v0.1.0-foundation`)**：Monorepo 結構、三語 README、ADR 文件群、Spring Boot 3.3.5 + Java 21 骨架、統一響應 `ApiResponse<T>` / `PageResult<T>`、三語 `ErrorCode` i18n、`ClientIpResolver`、`HmacSignatureVerifier`。
  - [x] **Phase 1 IAM 與權限核心 (`v0.2.0-iam`)**：
    - [x] `AesGcmCipher` 與 `AesGcmStringConverter`（AES-256-GCM 96-bit 隨機 IV + `{v1}` 版本前綴欄位透明加解密）。
    - [x] Flyway `V1__init_iam_schema.sql`（PostgreSQL 16 與 H2 雙版本：`iam_department`, `iam_user`, `iam_role`, `iam_permission`, `iam_user_role`, `iam_role_permission`, `iam_audit_log`）。
    - [x] Spring Security 6 + Stateless JWT（`access` / `pwd_change` / `refresh` HttpOnly Cookie）、BCrypt (strength 12)、連續 5 次失敗鎖定 15 分鐘、DBA 明文/`{noop}` 救急單次登入強制改密閘門（`ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED` 1003）。
    - [x] `@DataScope` AOP 資料範圍過濾與 `@AuditLog` AOP 管理異動稽核軌跡。
    - [x] 部門樹 CRUD（含子孫 `tree_path` 級聯更新與防迴圈保護）、角色與權限 CRUD、使用者 CRUD（含軟刪除釋放帳號重用、管理員重設密碼與手動解鎖）。
  - [x] **Phase 2 前端三大廳殼層與 IAM 管理後台 (`v0.3.0-frontend-shell`)**：
    - [x] 初始化 `frontend/` (`atria-frontend`)：Vue 3.5 + Vite 6 + TypeScript + PrimeVue 4 (`AtriaBluePreset` Aura 皇家藍主題) + Lucide Icons + Pinia + Vue Router + `vue-i18n`（支援 `zh-TW`, `en-US`, `ja-JP` 三語即時切換與高密度日系 B2B 排版）。
    - [x] 實作 Pinia 記憶體 Access Token 管理與 Axios 攔截器（自動附帶 `Authorization` 與 `Accept-Language`、401 自動透過 `/api/v1/auth/refresh` 無感換證、攔截 `1003` 強制導向 `/change-password`）。
    - [x] 實作三大廳 Layout 殼層（`PublicLayout` `/public`、`PortalLayout` `/portal`、`AdminLayout` `/admin`）及登入頁 (`/login`)、強制變更密碼頁 (`/change-password`)。
    - [x] 實作 IAM 後台管理頁面：部門組織樹 (`DepartmentManageView.vue` `TreeTable`，支援新增根部門與帶入父部門之新增子部門)、員工帳號管理 (`UserManageView.vue`)、角色與權限矩陣 (`RoleManageView.vue`)、系統稽核日誌 (`AuditLogView.vue`)。
    - [x] 依 `ADR-0008` Decision 11 新增 Flyway `V2__add_user_hire_date.sql`（PostgreSQL & H2 `iam_user.hire_date DATE`），完成後端 `User` Entity / DTO / 整合測試，以及前端員工管理列表「到職日」欄位與 `DatePicker`。
  - [x] **Phase 3a 差勤核心、多地區行事曆與員工自助廳 (`v0.4.0-attendance-core`)**：
    - [x] Flyway `V3__init_attendance_core_schema.sql`（PostgreSQL & H2 雙版本）：`iam_department.calendar_code`、`att_work_calendar`、`att_punch_rule`、`att_punch_log`（Append-only）、`att_daily_record`（含 `@Version` 樂觀鎖）、`att_monthly_close`，以及 `att:*` 權限與 `TW` / `JP` 2026 年種子行事曆。
    - [x] 多地區企業行事曆服務與整年範本產生器（`WorkCalendarService` + `StatutoryHolidayGenerator`）：內建台灣 (`TW` 國定假日 + 除夕農曆連假 + 週末補假) 與日本 (`JP` 內閣府國民之祝日 + Happy Monday + 春秋分天文近似公式 + 振替休日 + 國民之休日) 法定演算法，HR 可一鍵產生整年 365/366 天後逐日覆寫補班日或假日。
    - [x] Web 與代理 Bot 打卡引擎 (`PunchService`)：Append-only `att_punch_log`（含 `ClientIpResolver` 真實 IP 與 `HmacSignatureVerifier` `HMAC-SHA256` ±5 分鐘防重放驗證）、60 秒防重複打卡 (`2001`)、`04:00 AM` 跨日切算、上下班取整與寬限期、以及固定扣除 `12:00`–`13:00` 午休 60 分鐘之 `att_daily_record` 即時派生。
    - [x] 每日跨日日結排程 (`DailySettlementJob`) 與月結關帳鎖定 (`MonthlyCloseService` + `PendingRequestBlockingChecker` SPI，檢查未決 `PENDING` 單據阻擋關帳 `2010`、鎖定區間拒絕異動 `2005` 與解鎖重開)。
    - [x] 前端 `/portal` 員工自助打卡與 7 欄式月曆檢視 (`PortalHomeView.vue`)、`/admin/attendance/calendar` 多地區行事曆與打卡規則管理 (`CalendarManageView.vue`)、`/admin/attendance/monthly-close` 月結關帳與日結批次管理 (`MonthlyCloseView.vue`)，且全系統地區、時區、角色與權限標籤 100% 納入三語字典。
  - [x] **Phase 3a.5 品質閘門、ArchUnit 架構邊界與 DB 層防竄改加固**：
    - [x] **`att_punch_log` 雙層防竄改（JPA + PostgreSQL Trigger）**：於 `db/migration/postgresql/V3__init_attendance_core_schema.sql` 實作 `fn_prevent_att_punch_log_mutation()` 與 `trg_att_punch_log_immutable`（`BEFORE UPDATE OR DELETE ON att_punch_log` 強制拋錯），並於 `PunchLog.java` 加上 `@Immutable` 與 `@PreUpdate` / `@PreRemove` 攔截；新增 `PunchLogImmutabilityTests.java` 與 `application-pgtest.yml` 雙引擎驗證。
    - [x] **ArchUnit 模組邊界測試 (`ModularArchitectureTest.java`)**：強制檢驗 5 條 ADR-0001 架構鐵律（禁止 Controller 直接存取 Repository、`common` 不得依賴業務模組、`iam` 不得反向依賴上層業務模組、`AttendanceRecordCalculator` 核心計算隔離、頂層業務模組間無循環依賴），防止後續階段子代理跨模組違規引用。
    - [x] **GitHub Actions CI (`.github/workflows/ci.yml`) 與前端抓錯 ESLint (`frontend/eslint.config.js`)**：CI 自動啟動 `services: postgres:16-alpine` 執行 H2 全量測試與 `-Dspring.profiles.active=pgtest` 真實 PostgreSQL 16 Flyway + Append-Only Trigger 驗證；前端導入 ESLint 9 flat config（僅啟用抓錯規則、關閉排版噪音），落實 `./mvnw.cmd test` 與 `npm run lint && npm run build` 雙重驗收門檻。

---

## 二、 本機環境準備 (Environment Checklist)

| 工具 | 目前狀態 | 建議動作 / 安裝指令 |
| :--- | :--- | :--- |
| **Node.js** | ✅ 已安裝 (`v24.14.1`) | `frontend/` 依賴已安裝，`npm run lint`（ESLint 9 零警告）與 `npm run build`（`vue-tsc -b && vite build`）驗證通過 |
| **Java JDK 21** | ✅ 已安裝 (`Temurin 21.0.13 LTS`) | 路徑：`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| **Maven Wrapper** | ✅ 已內建 (`mvnw` / `mvnw.cmd`) | `cd backend && ./mvnw.cmd test` 通過 24 項測試（IAM 7 項 + Attendance 9 項 + Immutability 2 項 + ArchUnit 5 項 + Context 1 項） |
| **PostgreSQL 16** | ✅ CI 內建 (`postgres:16-alpine`) | 本機預設 H2 快速測試；GitHub Actions CI 透過 `-Dspring.profiles.active=pgtest` 跑真實 PostgreSQL 16 Flyway 與 Trigger 驗證 |

---

## 三、 下一階段待辦：Phase 3b 假別額度帳本、台日法定加班與簽核事件契約 (`v0.4.5-leave-overtime`)

依 `docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md` 與 `docs/adr/ADR-0008-attendance-leave-overtime-refinements.md`（Decision 4, 5, 6, 8, 9, 10, 12）：

1. **Flyway Migration `V4__init_leave_overtime_schema.sql`（PostgreSQL 與 H2 雙版本）**
   - [ ] 建立 `att_leave_type`（含 `min_unit_minutes` 最小請假粒度、`is_paid`、`requires_attachment`、三語名稱 `JSONB`）。
   - [ ] 建立 `att_leave_grant_rule`（`TW` 勞基法第 38 條 / `JP` 勞基法第 39 條年資特休給假級距表與日本年休 5 日義務追蹤）與 `att_leave_balance`（`total_minutes`, `used_minutes`, `pending_minutes`, `valid_from`, `valid_until`）。
   - [ ] 建立 `att_overtime_rate_rule`（`TW`/`JP` 分段費率倍率表，含平日延長、休息日、例假日、國定假日與日本深夜加成 `22:00`–`05:00`）、`att_overtime_cap_rule`（單月/3個月/年度上限時數與 `WARN`/`BLOCK` 策略）、`att_comp_leave_grant`（補休折算 `HOURS_1_TO_1` / `RATE_MULTIPLIED` 與到期未休還原原分段時數）。
   - [ ] 建立差勤申請主表 `att_request`（統一涵蓋 `LEAVE` 請假、`PUNCH_CORRECTION` 補打卡、`OVERTIME` 加班，並實作 `PendingRequestBlockingChecker` 銜接月結關帳阻擋檢查）。
2. **後端請假、年資特休、加班費率引擎與簽核事件契約**
   - [ ] 年資特休自動給假服務與排程（依 `iam_user.hire_date` 與部門 `calendar_code` 自動核發特休額度）。
   - [ ] 請假有效工時計算器（自動比對部門 `att_work_calendar` 排除例休假與 `12:00`–`13:00` 午休，並依 `min_unit_minutes` 檢核與預扣 `pending_minutes`）。
   - [ ] 台日加班費率分段計算與上限檢核引擎，以及加班轉補休 (`COMP_LEAVE`) 帳本。
   - [ ] 定義跨模組領域事件契約（`AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent`）與僅限 `local`/`test` profile 啟用之開發驗證簽核 Stub API，讓 Phase 3b 可獨立完成「送單 ➔ 預扣/檢核 ➔ 核准回寫 `att_daily_record` 與 `att_punch_log(CORRECTION)` / 駁回退還額度」完整閉環測試。
3. **前端 `/portal` 差勤申請工作台與 `/admin` 假別／加班規則管理 UI**
   - [ ] `/portal` 新增請假、補打卡、加班申請與個人假別餘額／補休到期看板。
   - [ ] `/admin` 新增假別與年資給假規則、台日加班費率與上限規則管理頁。

---

## 四、 新 Session 開工引導 Prompt (直接複製貼上)

新 Session 開始時，可直接貼上以下提示詞喚醒 Agent：

```text
我們正在開發 Atria 專案（路徑：C:\Users\a1083\OneDrive\文件\atria）。
請讀取 CLAUDE.md、docs/NEXT_STEPS.md、docs/ROADMAP.md、docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md 與 docs/adr/ADR-0008-attendance-leave-overtime-refinements.md，
我們現在正式開始進行 Phase 3b（假勤與加班核心：Flyway V4 假別與額度帳本 att_leave_*、台日年資特休給假、請假時數計算器、台日加班分段倍率與上限規則 att_overtime_*、補休折算 att_comp_leave_grant、差勤申請單 att_request 與簽核領域事件契約 + dev/test 簽核 Stub，以及前端 /portal 請假/補打卡/加班申請與 /admin 假勤規則 UI）。
請依規範先思考並完成共用區域（V4 SQL、Entity、ErrorCode 三語訊息、領域事件契約、DTO 與前端 API 型別）後，提出 Subagent 派工計畫。
```
