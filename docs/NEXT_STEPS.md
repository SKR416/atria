# Atria 開發交接與待辦清單 (Handover & Next Steps)

本文件供跨 Session 接續開發使用，記錄目前進度、本機環境就緒狀態、已完成里程碑與下一階段（Phase 4）開工指令。

---

## 一、 目前進度與專案現況 (Current Status)

- **專案路徑**：`C:\Users\a1083\OneDrive\文件\atria`
- **Git 狀態**：`main` 分支，Phase 0 (`v0.1.0-foundation`)、Phase 1 (`v0.2.0-iam`)、Phase 2 (`v0.3.0-frontend-shell`)、Phase 3a (`v0.4.0-attendance-core`)、Phase 3a.5（品質閘門、ArchUnit 架構測試與 `att_punch_log` 雙層防竄改加固）、Phase 3b (`v0.4.5-leave-overtime` 假別額度帳本、台日法定加班與簽核事件契約）與 **Phase 4 (`v0.5.0-workflow` 動態表單、多層級簽核流程、站內通知與每月假勤摘要)** 已全部完成實作與驗證（通過後端 40 項自動化測試 + 前端 `npm run lint` 0 警告/錯誤 [含 485 鍵靜態 UI/i18n 守門] 與 `npm run build` 打包通過）
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
    - [x] 每日跨日日結排程 (`DailySettlementJob`) 與月結關帳鎖定 (`MonthlyCloseService` + `PendingRequestChecker` SPI，檢查未決 `PENDING` 單據阻擋關帳 `2010`、鎖定區間拒絕異動 `2005` 與解鎖重開)。
    - [x] 前端 `/portal` 員工自助打卡與 7 欄式月曆檢視 (`PortalHomeView.vue`)、`/admin/attendance/calendar` 多地區行事曆與打卡規則管理 (`CalendarManageView.vue`)、`/admin/attendance/monthly-close` 月結關帳與日結批次管理 (`MonthlyCloseView.vue`)，且全系統地區、時區、角色與權限標籤 100% 納入三語字典。
  - [x] **Phase 3a.5 品質閘門、ArchUnit 架構邊界與 DB 層防竄改加固**：
    - [x] **`att_punch_log` 雙層防竄改（JPA + PostgreSQL Trigger）**：於 `db/migration/postgresql/V3__init_attendance_core_schema.sql` 實作 `fn_prevent_att_punch_log_mutation()` 與 `trg_att_punch_log_immutable`（`BEFORE UPDATE OR DELETE ON att_punch_log` 強制拋錯），並於 `PunchLog.java` 加上 `@Immutable` 與 `@PreUpdate` / `@PreRemove` 攔截；新增 `PunchLogImmutabilityTests.java` 與 `application-pgtest.yml` 雙引擎驗證。
    - [x] **ArchUnit 模組邊界測試 (`ModularArchitectureTest.java`)**：強制檢驗 5 條 ADR-0001 架構鐵律（禁止 Controller 直接存取 Repository、`common` 不得依賴業務模組、`iam` 不得反向依賴上層業務模組、`AttendanceRecordCalculator` 核心計算隔離、頂層業務模組間無循環依賴）。
    - [x] **GitHub Actions CI (`.github/workflows/ci.yml`) 與前端抓錯 ESLint (`frontend/eslint.config.js`)**：CI 自動啟動 `services: postgres:16-alpine` 執行 H2 全量測試與 `-Dspring.profiles.active=pgtest` 真實 PostgreSQL 16 Flyway + Append-Only Trigger 驗證；前端導入 ESLint 9 flat config。
  - [x] **Phase 3b 假別額度帳本、台日法定加班與簽核事件契約 (`v0.4.5-leave-overtime`)**：
    - [x] **Flyway `V4__init_leave_overtime_schema.sql`（PostgreSQL & H2 雙版本）**：建立 `att_leave_type`（含 `min_unit_minutes`）、`att_leave_grant_rule`（`ANNIVERSARY` / `CALENDAR_YEAR`）、`att_request`（含樂觀鎖 `version` 與五態狀態機）、`att_comp_leave_grant`（含 `band_breakdown_json`）、`att_leave_balance`（含 `source_comp_grant_id`）、`att_leave_balance_tx`（FIFO 逐桶異動明細）、`att_overtime_rate_rule`（TW 實務 `1.34`/`1.67`/`2.67`/`2.00` 與 JP 法定倍率種子）、`att_overtime_cap_rule`（`WARN`/`BLOCK`），並為 `att_punch_log.correction_request_id` 補上指向 `att_request(id)` 之外鍵。
    - [x] **台日年資特休自動給假引擎 (`AnnualLeaveGrantService`, `LeaveTypeService`)**：依 `iam_user.hire_date` 到職週年制（`ANNIVERSARY`）計算台灣勞基法第 38 條（含滿 10 年以上每滿 1 年加給 1 日至 30 日上限）與日本勞基法第 39 條（含給假 ≥ 10 日時自動設定 `2400` 分鐘年休 5 日取得義務目標），並回報缺漏 `hire_date` 之帳號清單。
    - [x] **請假時數計算器與 FIFO 額度帳本 (`LeaveDurationCalculatorImpl`, `LeaveBalanceLedgerService`)**：將請假區間與部門行事曆工作時段取交集、扣除 `12:00`–`13:00` 午休並依 `min_unit_minutes` 無條件進位；依 `valid_until ASC`（FIFO）預扣／實扣，並透過 `att_leave_balance_tx` 確保駁回、抽單或撤銷時 100% 精準原路退回原額度桶。
    - [x] **台日加班倍率／上限引擎與補休折算 (`OvertimeRuleAndCalculatorService`, `CompLeaveGrantService`)**：以 `BigDecimal` + `RoundingMode.CEILING` 計算累計級距加權分鐘數、檢核單月／跨月／年度上限（`WARN` / `BLOCK` `2020`）、支援 `PAY` 與 `COMP_LEAVE`（`HOURS_1_TO_1` / `RATE_MULTIPLIED`）並同步建立關聯 `att_leave_balance` 額度桶，且支援依 `band_breakdown_json` 執行台灣勞基法第 32-1 條補休到期未休折算還原。
    - [x] **單向簽核領域事件契約與考勤重算整合 (`AttendanceRequestService`, `AttendanceRecordCalculator`, `DatabasePendingRequestChecker`, `AttRequestGuardImpl`, `AttendanceRequestStubController`)**：於 `attendance.event` 定義無循環依賴之領域事件、核准上午／下午請假時自動順延遲到或提前早退起算基準、核准補打卡時寫入 `att_punch_log(CORRECTION)` 並重算當日考勤、真實查詢未決 `PENDING` 單據阻擋月結關帳（`2010`）、防止已動用補休之加班單遭撤銷（`2025`），並提供 `@Profile({"local", "pgtest"})` 開發測試簽核 Stub。
    - [x] **前端 `/portal` 差勤申請工作台與 `/admin/attendance/leave-overtime` 假勤加班規則後台 (`PortalHomeView.vue`, `LeaveOvertimeManageView.vue`)**，並通過 `Phase3bLeaveOvertimeIntegrationTests`（8 大端到端情境測試，全專案共 32 項測試全數通過）。
  - [x] **Phase 4 動態表單、多層級簽核流程與通知中心 (`v0.5.0-workflow`)**：
    - [x] **Flyway `V5__init_workflow_and_notification_schema.sql`（PostgreSQL 16 與 H2 雙版本）**：建立動態表單模板 `wf_form_template`、簽核流程定義 `wf_flow_definition`、簽核節點 `wf_flow_node`、結構化條件 `wf_flow_node_condition`、簽核實例 `wf_instance`（含 `@Version` 樂觀鎖與不可變結構快照）、簽核歷程 `wf_task_log`、職務代理 `wf_deputy`、站內通知 `ntf_notification` 與摘要日誌 `ntf_digest_log` 共 9 張新表，並植入 `wf:instance:self`、`wf:template:read`、`wf:template:write`、`wf:instance:reassign`、`ntf:digest:trigger` 權限。
    - [x] **動態表單 JSON Schema 與不可變版本機制 (`wf_form_template`, `wf_instance`)**：支援 `TEXT`、`TEXTAREA`、`NUMBER`、`SELECT`、`DATE` 欄位型態，模板定義異動時自動升版（`v1` -> `v2`），且每筆送出的簽核實例 (`wf_instance`) 永久凍結儲存提交當下的 `form_version` 與 `schema_snapshot_json`。
    - [x] **純 Java 結構化條件多層級簽核引擎 (`StructuredConditionEvaluator`, `ApproverResolverService`, `WorkflowEngineService`)**：零 SpEL 表達式注入風險，純 Java 運算 `wf_flow_node_condition`（`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`，同組 `logical_group` 為 AND，跨組為 OR），沿部門組織樹向上解析主管（`DIRECT_LEADER`, `PARENT_LEADER`, `SPECIFIC_ROLE`）並具備 `HR_ADMIN` -> `SUPER_ADMIN` 安全兜底與申請人同簽核人自動跳過（`selfApprovalAutoSkip`）。
    - [x] **差勤雙向領域事件迴路 (`attendance.event.*`)**：監聽 `AttRequestSubmittedEvent` / `AttRequestCancelledEvent` 自動建立或取消連動 `wf_instance`；簽核終態（核准／駁回／撤銷）發布 `AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent`，並透過 `AttRequestGuard` 嚴密保護已核准單據之撤銷前置檢驗。
    - [x] **退回修改 (`差戻し`) 與欄位修訂對比 (`diff_json`)**：獨立動態表單 (`target_type = 'DYNAMIC_FORM'`) 支援退回草稿修改與版本對比；差勤單據嚴禁退回修改（拋出 `3009` `WF_RETURN_TO_DRAFT_NOT_ALLOWED_FOR_ATT_REQUEST`），捍衛 FIFO 凍結額度一致性。
    - [x] **職務代理人授權 (`wf_deputy`) 與 HR 強制改派 (`REASSIGN`)**：支援指定日期區間之代理規則，內建禁止代理自己 (`3012`) 與重疊區間防護 (`3013`)；簽核歷程 `wf_task_log` 完整記錄雙軌稽核（實際代理操作者 `actor_user_id` 與被代理主管 `delegator_user_id`），並支援管理員強制改派任務。
    - [x] **站內通知中心與每月假勤摘要冪等排程 (`notification` 模組)**：頂部導航即時未讀通知鈴鐺 (`NotificationBell.vue`)、開發環境日誌模擬郵件通道 (`EmailNotificationChannel`)，以及每月假勤額度與即將到期提醒排程 `LeaveBalanceDigestJob`（以 `ntf_digest_log` 具備 `UNIQUE (user_id, period_year_month)` 確保排程不重複寄送）。
    - [x] **前端畫面與品質驗收守門員**：實作 `NotificationBell.vue`（站內通知鈴鐺抽屜）、`/portal` 員工簽核中心面板 (`PortalWorkflowPanel.vue`)、`/admin/workflow/manage` 流程與表單管理後台 (`WorkflowManageView.vue`)，以及無瀏覽器靜態 UI/i18n 守門腳本 `frontend/scripts/check-ui-i18n.mjs`。

---

## 二、 本機環境準備 (Environment Checklist)

| 工具 | 目前狀態 | 建議動作 / 安裝指令 |
| :--- | :--- | :--- |
| **Node.js** | ✅ 已安裝 (`v24.14.1`) | `frontend/` 依賴已安裝，`npm run lint`（ESLint 9 零警告 + `scripts/check-ui-i18n.mjs` 485 鍵靜態守門零錯誤）與 `npm run build`（`vue-tsc -b && vite build`）驗證通過 |
| **Java JDK 21** | ✅ 已安裝 (`Temurin 21.0.13 LTS`) | 路徑：`C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` |
| **Maven Wrapper** | ✅ 已內建 (`mvnw` / `mvnw.cmd`) | `cd backend && ./mvnw.cmd test` 通過 40 項測試（8 條 ArchUnit 模組架構金字塔規則 + 7 IAM + 8 Phase 3a Attendance + 11 Phase 3b Leave/Overtime + 5 Phase 4a Workflow/Notification + 1 DB 防竄改 + 1 Spring Context） |
| **PostgreSQL 16** | ✅ CI 內建 (`postgres:16-alpine`) | 本機預設 H2 快速測試；GitHub Actions CI 透過 `-Dspring.profiles.active=pgtest` 跑真實 PostgreSQL 16 Flyway（V1–V5）與 Trigger 驗證 |

---

## 三、 下一階段待辦：Phase 5 公開前台企業公告、非同步 AI 語音轉文字 (STT) 與最終發布收尾 (`v1.0.0`)

依據 `docs/ROADMAP.md`、`docs/adr/ADR-0006-ai-module-scope-and-async-stt.md` 與專案整體架構設計：

1. **Flyway Migration `V6__init_announcement_and_ai_schema.sql`（PostgreSQL 與 H2 雙版本）**：
   - 建立公告表 `ann_notice`（支援 `PUBLIC` / `INTERNAL` 範疇、多語系標題與富文本內容、置頂旗標、發布狀態）、已讀回條表 `ann_read_receipt`。
   - 建立 AI 對話階段表 `ai_chat_session`、對話訊息表 `ai_chat_message`、非同步語音轉文字任務表 `ai_stt_job`（狀態：`PENDING` / `PROCESSING` / `COMPLETED` / `FAILED`）以及用量配額表 `ai_usage_quota`。
2. **主管差勤看板與計薪加權工時匯出 (`dashboard` 模組)**：
   - 實作 `/admin/dashboard` 主管差勤即時看板：ApexCharts 出缺勤 KPI 趨勢、當日異常打卡篩選、待審核單據預警。
   - 實作每月計薪加權時數匯出：支援 Apache POI SXSSF 串流產生 Excel (`.xlsx`) 與 UTF-8 BOM CSV，包含平日／休息日／例假日各級距加權工時與未休代休結算金。
3. **企業公告系統 (`announcement` 模組)**：
   - 公開前台 (`/public`)：免登入公告列表、分類篩選、置頂顯示與三語切換。
   - 員工入口 (`/portal`)：內部公告廣播、已讀標記 (`ann_read_receipt`) 與未讀徽章連動。
4. **務實的 AI 模組與非同步語音轉文字 (`ai` 模組)**：
   - 實作統一 `AiProvider` SPI 與 zero-key local mock 實作。
   - 實作 SSE 串流對話與職場文字轉換模板（請假理由修飾、週報摘要、語氣轉換）。
   - 實作獨立 `whisper.cpp` HTTP 介接客戶端與非同步音檔轉文字任務排程 (`ai_stt_job`)。
5. **外部 Webhook 機器人通知通道 (`WebhookBotNotificationChannel`)**：
   - 支援 Slack / Microsoft Teams / LINE 機器人 Proxy Webhook 轉發重大考勤或簽核事件。
6. **產品化最終驗收與打包發布 (`v1.0.0`)**：
   - 執行三語 README、ADR 與全系統功能端到端驗收，建立 `v1.0.0` 發布標籤。

---

## 四、 新 Session 開工引導 Prompt (直接複製貼上)

新 Session 開始時，可直接貼上以下提示詞喚醒 Agent：

```text
我們正在開發 Atria 專案（路徑：C:\Users\a1083\OneDrive\文件\atria）。
Phase 4（v0.5.0-workflow）已順利完工並通過 40 項後端測試與前端品質守門。
請讀取 CLAUDE.md、docs/NEXT_STEPS.md、docs/ROADMAP.md 與 docs/adr/ADR-0006-ai-module-scope-and-async-stt.md，
準備規劃並執行 Phase 5：公開前台企業公告、非同步 AI 語音轉文字 (STT) 與最終發布收尾（v1.0.0）。
```
