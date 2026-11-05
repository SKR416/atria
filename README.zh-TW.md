# Atria — 差勤與簽核流程管理平台 (Attendance & Approval Workflow Platform)

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **目前開發進度：** `Phase 3a — 差勤核心、多地區行事曆與員工自助入口（已完成，v0.4.0-attendance-core）`  
> **下一階段：** `Phase 3b — 假別額度帳本、台日法定加班與簽核事件契約（v0.4.5-leave-overtime）`  
> 目前已完成 Phase 0–3a 實作與驗證（後端 17 項整合測試 + 前端 TypeScript 與 Vite 建置通過）。完整開發時程與交接清單請參閱 [docs/ROADMAP.md](./docs/ROADMAP.md) 與 [docs/NEXT_STEPS.md](./docs/NEXT_STEPS.md)。

Atria 是一套整合差勤管理與申請審批流程的企業級平台。員工可打卡、提出請假、補打卡與加班申請，由直屬主管與 HR 透過多層級流程審批，核准後自動回寫差勤紀錄與假別額度。後端採 Spring Boot 3.3，前端採 Vue 3.5，為模組化單體架構，具備 RBAC、部門子樹資料範圍隔離 (`@DataScope`)、動態表單與三語系（繁中／英／日）支援。

---

## 為什麼命名為「Atria」？

> *Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

**Atria** 是拉丁文與英文中「中庭／大廳（atrium）」的複數形，象徵三個服務大廳圍繞著同一個後端中樞運作：

- **`/public`（公開前台大廳）：** 免登入瀏覽的對外系統公告，支援置頂、分類篩選與三語切換。
- **`/portal`（員工差勤與請假入口 / ESS）：** 全體員工的日常工作台，整合上下班打卡、互動式公司行事曆、請假／補打卡／加班申請、內部公告欄，以及輕量 AI 工具。
- **`/admin`（主管與管理主系統）：** 專供部門主管、HR 與系統管理員使用的管理中樞，提供團隊出缺勤看板、多層級簽核、台日多地區 (`TW` / `JP`) 公司行事曆定義、動態表單設計與 IAM 權限管理。

---

## 目前已實作功能 (Phase 0 – Phase 3a)

依 [ADR-0007](./docs/adr/zh-TW/ADR-0007-project-naming.md) 規範，本節如實記載目前已於儲存庫落地並通過驗證的功能：

- **Phase 0 — 基礎底座 (`v0.1.0-foundation`)：**
  - Spring Boot 3.3.5 (Java 21 LTS) 模組化單體骨架，內建統一回應 `ApiResponse<T>`、分頁 `PageResult<T>`、`BusinessException` 與 `GlobalExceptionHandler`。
  - 三層式多語系架構（`zh-TW`、`en-US`、`ja-JP`），透過 Spring `MessageSource` (`messages_*.properties`) 解析錯誤碼，並強制全系統 UTC 時間戳記儲存（[ADR-0002](./docs/adr/zh-TW/ADR-0002-three-tier-i18n-architecture.md)）。
  - 安全與基礎工具：`ClientIpResolver`（支援反向代理之真實客戶端 IP 解析）、`HmacSignatureVerifier`（`HMAC-SHA256` ±5 分鐘時間窗防重送簽章驗證）、`LocalFileStorageService`（日期分區 + UUID 隔離儲存與 `SHA-256` 校驗碼）。
- **Phase 1 — 身分認證、部門組織樹與存取控制 (`v0.2.0-iam`)：**
  - Flyway `V1__init_iam_schema.sql`（PostgreSQL 16 與 H2 雙版本）：`iam_department`（含 `timezone` 時區與物化路徑 `tree_path`）、`iam_user`、`iam_role`、`iam_permission`、`iam_user_role`、`iam_role_permission`、`iam_audit_log`。
  - Spring Security 6 無狀態 JWT 認證（記憶體 Access Token + `HttpOnly` Refresh Token Cookie）、`BCrypt` (strength 12) 雜湊、連續 5 次登入失敗鎖定 15 分鐘（`locked_until`），以及 **DBA 明文／`{noop}` 緊急救援機制**（單次登入後強制觸發 `ErrorCode 1003` 變更密碼閘門並重設為 BCrypt，見 [ADR-0004](./docs/adr/zh-TW/ADR-0004-rbac-and-department-data-scope.md)）。
  - 敏感欄位透明加解密：`AesGcmCipher` 與 `AesGcmStringConverter`（採用 `AES-256-GCM`、96-bit 隨機 IV 與 `{v1}` 版本前綴）。
  - `@DataScope` AOP 部門子樹資料範圍過濾（支援 `ALL`、`DEPT_AND_SUB`、`DEPT_ONLY`、`SELF_ONLY`）與 `@AuditLog` AOP 管理操作稽核日誌。
- **Phase 2 — 前端三大廳殼層、IAM 管理後台與員工到職日 (`v0.3.0-frontend-shell`)：**
  - Vue 3.5 + Vite 6 + TypeScript + PrimeVue 4 (`AtriaBluePreset` Aura 皇家藍主題) + Pinia + Vue Router 4 + `vue-i18n` 單頁應用，採高資訊密度日系 B2B 介面設計。
  - Axios 攔截器：自動附帶 JWT 與 `Accept-Language`、401 自動透過 `/api/v1/auth/refresh` 無感換證、攔截 `1003` 強制導向 `/change-password`。
  - 三大廳獨立版面（`PublicLayout` `/public`、`PortalLayout` `/portal`、`AdminLayout` `/admin`）及登入頁 (`/login`)、強制變更密碼頁 (`/change-password`)。
  - `/admin/iam/*` 完整管理後台：
    - **部門組織管理 (`DepartmentManageView.vue`)：** `TreeTable` 階層部門樹、支援新增根部門與帶入父部門之新增子部門、主管指派、子孫 `tree_path` 級聯更新與地區時區／行事曆代碼 (`TW`/`JP`) 設定。
    - **員工帳號管理 (`UserManageView.vue`)：** 分頁 `DataTable`、多語系角色標籤、`hireDate` 到職日選擇器 (`DatePicker`)、管理員重設密碼、手動解除鎖定與軟刪除。
    - **角色與權限矩陣 (`RoleManageView.vue`)：** 角色 CRUD、`DataScopeType` 資料範圍設定與互動式權限勾選矩陣。
    - **系統稽核日誌 (`AuditLogView.vue`)：** 分頁檢視管理端異動軌跡。
  - Flyway `V2__add_user_hire_date.sql`（`iam_user.hire_date DATE`）：打通 Entity、DTO、整合測試與前端員工表單（[ADR-0008](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md) 決策 11）。
- **Phase 3a — 差勤核心、多地區行事曆與員工自助入口 (`v0.4.0-attendance-core`)：**
  - Flyway `V3__init_attendance_core_schema.sql`（PostgreSQL 16 與 H2 雙版本）：`iam_department.calendar_code`、`att_work_calendar`（`WORKDAY`、`MAKEUP_WORKDAY`、`REST_DAY`、`STATUTORY_REST_DAY`、`NATIONAL_HOLIDAY`）、`att_punch_rule`、不可變原始打卡表 `att_punch_log`、每日衍生考勤表 `att_daily_record`（含 `@Version` 樂觀鎖）與月結關帳表 `att_monthly_close`。
  - **台日多地區行事曆與整年範本產生器 (`WorkCalendarService`, `StatutoryHolidayGenerator`)：** 內建台灣（`TW` 國定假日、農曆春節/端午/中秋與週末補假）與日本（`JP` 內閣府國民之祝日、Happy Monday、春秋分天文近似公式、振替休日與國民之休日）整年 365/366 天產生器，支援 HR 逐日覆寫補班日與自訂地區打卡規則。
  - **不可變打卡帳本與當日考勤即時派生 (`PunchService`)：** 支援員工 Web 打卡 (`/api/v1/attendance/punch`) 與 `HMAC-SHA256` ±5 分鐘防重送之代理 Bot 打卡 (`/api/v1/attendance/bot/punch`)、60 秒防重複打卡 (`ErrorCode 2001`)、`04:00 AM` 跨日切算、上下班取整與寬限期計算、以及自動扣除 `12:00`–`13:00` 午休 60 分鐘之淨工時派生。
  - **每日跨日日結排程與月結關帳鎖定 (`DailySettlementJob`, `MonthlyCloseService`)：** 跨日切算點後自動結算前一日 `MISSING_PUNCH` / `ABSENT` 狀態；月結關帳透過 `PendingRequestBlockingChecker` SPI 檢查未決 `PENDING` 單據阻擋關帳 (`ErrorCode 2010`)、拒絕已關帳月份異動 (`ErrorCode 2005`) 並支援 HR 解鎖重開。
  - **前端員工自助大廳與人資差勤後台：**
    - **員工自助入口 (`PortalHomeView.vue`)：** 部門時區即時時鐘、一鍵打卡與 Append-only 原始打卡明細、當月 KPI 指標條，以及互動式 7 欄月曆與單日考勤詳情彈窗。
    - **多地區行事曆與打卡規則管理 (`CalendarManageView.vue`)：** `TW`/`JP` 視覺化月曆編輯器、一鍵產生整年範本彈窗、單日覆寫與地區打卡規則設定。
    - **月結關帳與日結批次管理 (`MonthlyCloseView.vue`)：** 關帳前未決單據檢查、月結鎖定／解鎖重開、手動觸發日結排程與關帳歷史紀錄。

---

## 技術棧選型

| 層級 | 技術選型 | 說明 |
| :--- | :--- | :--- |
| **後端核心** | Java 21 LTS, Spring Boot 3.3.5 | 模組化單體架構 (`io.github.skr416.atria`) |
| **安全與加解密** | Spring Security 6, JWT (`jjwt`), BCrypt, `AES-256-GCM` | 無狀態認證、DBA 明文救急強制改密 (`1003`)、RBAC、`@DataScope` 部門樹資料隔離、欄位級加密 (`AesGcmStringConverter`) |
| **資料庫** | PostgreSQL 16 (本地免安裝支援 H2), Flyway (`V1`–`V3`) | 關聯式核心搭配原生 `JSONB` 儲存多語系與動態表單，並以部分唯一索引支援軟刪除 |
| **前端核心** | Vue 3.5, Vite 6, TypeScript, PrimeVue 4, Pinia, Vue Router 4, Lucide Icons, ApexCharts | 高資訊密度日系 B2B 單頁應用，內建三大廳版面 (`/public`, `/portal`, `/admin`)，全線採用符合供應鏈合規 (SCRM) 之 MIT 元件（[ADR-0001](./docs/adr/zh-TW/ADR-0001-modular-monolith-and-tech-stack.md)） |
| **多語系 (i18n)** | Spring `MessageSource`, `vue-i18n` | API、資料庫與前端 UI 全面支援繁體中文 (`zh-TW`)、英文 (`en-US`)、日文 (`ja-JP`) |
| **API 契約** | Springdoc OpenAPI 3 | Swagger 文件產生與前端 TypeScript 型別對齊 |

---

## 專案目錄結構 (Repository Structure)

```text
atria/
├── docs/
│   ├── ROADMAP.md                # 開發階段里程碑與 Git Tag 規劃 (Phase 0 至 Phase 6)
│   ├── ARCHITECTURE.md           # 模組化單體套件結構與資料表前綴總覽
│   ├── NEXT_STEPS.md             # 跨 Session 開發交接與待辦清單
│   └── adr/                      # 英文架構決策紀錄正本 (ADR-0001 至 ADR-0008)
│       └── zh-TW/                # 繁體中文 ADR 譯本與索引 (README.md)
├── backend/                      # Maven artifactId: atria-backend (Spring Boot 3.3.5, Java 21)
│   └── src/
│       ├── main/java/io/github/skr416/atria/
│       │   ├── common/           # ApiResponse, ErrorCode, AesGcmCipher, HmacSignatureVerifier 等
│       │   ├── config/           # I18nConfig, OpenApiConfig, CorsConfig
│       │   ├── iam/              # 認證、使用者、部門樹、角色權限、@DataScope、@AuditLog
│       │   └── attendance/       # 台日多地區行事曆、不可變打卡日誌與當日考勤、日結排程、月結關帳
│       ├── main/resources/
│       │   ├── db/migration/     # Flyway V1–V3 SQL 遷移腳本 (postgresql/ 與 h2/)
│       │   └── i18n/             # 三語系訊息檔 (zh_TW, en_US, ja_JP)
│       └── test/                 # Phase 1 IAM 與 Phase 3a Attendance 端到端整合測試 (17 項)
└── frontend/                     # npm package: atria-frontend (Vue 3.5, Vite 6, TypeScript, PrimeVue 4)
    └── src/
        ├── api/                  # Axios HTTP 客戶端 (JWT 自動換證與 1003 攔截) 與 IAM / Attendance API
        ├── components/common/    # 三語切換器 LanguageSwitcher
        ├── i18n/                 # vue-i18n 語系字典 (zh-TW, en-US, ja-JP)
        ├── layouts/              # PublicLayout, PortalLayout, AdminLayout 三大廳殼層
        ├── router/               # 三大廳路由守衛與強制改密導向
        ├── stores/               # Pinia auth store (記憶體 Access Token 與個人資料)
        └── views/                # Public, Portal (打卡與月曆), Auth 與 Admin (IAM + 差勤後台) 頁面
```

---

## 快速啟動 (Local Development)

### 1. 啟動後端 (`atria-backend`)
預設以 `local` profile（記憶體 H2 資料庫）於 `8080` 埠啟動，並自動植入測試帳號（`admin`、`hr_lead`、`eng_mgr`、`emp_alice`、`emp_locked`，密碼皆為 `Password123!`）：
```bash
cd backend
./mvnw spring-boot:run
```

### 2. 啟動前端 (`atria-frontend`)
預設於 `5173` 埠啟動，並將 `/api` 代理轉發至 `http://localhost:8080`：
```bash
cd frontend
npm install
npm run dev
```

---

## 開發階段與里程碑 (Roadmap)

| 階段 | 目標 Git Tag | 實作範圍 | 狀態 |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | 儲存庫初始化、ADR 文件、Spring Boot 3 骨架、`ApiResponse<T>`、三語系錯誤碼 | ✅ 已完成 |
| **Phase 1** | `v0.2.0-iam` | IAM 資料表 (`iam_*`)、JWT 認證、RBAC、部門樹與主管關聯、`@DataScope`、`@AuditLog`、`AES-256-GCM` | ✅ 已完成 |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 前端骨架 (`atria-frontend`)、三大廳版面、IAM 帳號與權限管理畫面、`hire_date` (`V2`) | ✅ 已完成 |
| **Phase 3a** | `v0.4.0-attendance-core` | 多地區行事曆 (`TW`/`JP` 模板)、不可變打卡日誌 (`att_punch_log`) + 衍生日結表 (`att_daily_record`)、Bot 代打卡、每日結算、月結鎖定、`/portal` 與 `/admin` 行事曆介面 | ✅ 已完成 |
| **Phase 3b** | `v0.4.5-leave-overtime` | 假別與年資特休給假規則 (`hire_date`)、請假時數計算器與額度帳本、台日加班倍率與上限引擎、補休折算、簽核事件契約 + 測試 Stub | ⏳ 下一階段 |
| **Phase 4** | `v0.5.0-workflow` | 動態表單模板 (`wf_form_template`)、結構化條件多層級審批流程 (`wf_*`)、簽核人備援、職務代理人 (`wf_deputy`)、通知中心 (`ntf_*`) 與每月餘額摘要信 | ⏳ 規劃中 |
| **Phase 5** | `v0.6.0-dashboard-notice` | 主管首頁出缺勤即時看板、異常紀錄篩選、計薪加權時數 Excel/CSV 匯出、Webhook Bot 通知、公開 (`/public`) 與內部 (`/portal`) 公告系統 (`ann_*`) | ⏳ 規劃中 |
| **Phase 6** | `v0.7.0-ai-suite` | 嵌入式 AI 對話 (SSE Chat)、職場文字轉換、非同步音檔轉文字記錄 (`ai_stt_job` 獨立 `whisper.cpp` 服務) | ⏳ 規劃中 |

---

## 架構決策紀錄 (ADRs)

所有架構決策皆以英文正本記錄於 [`docs/adr/`](./docs/adr/)，並於 [`docs/adr/zh-TW/README.md`](./docs/adr/zh-TW/README.md) 提供完整繁體中文譯本索引：

| 編號 | 標題 | 狀態 |
| :--- | :--- | :--- |
| [ADR-0001](./docs/adr/zh-TW/ADR-0001-modular-monolith-and-tech-stack.md) ([EN](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md)) | 模組化單體架構與核心技術棧 | 已採納 |
| [ADR-0002](./docs/adr/zh-TW/ADR-0002-three-tier-i18n-architecture.md) ([EN](./docs/adr/ADR-0002-three-tier-i18n-architecture.md)) | 三層式三語多語系（i18n）架構 (`zh-TW`, `en-US`, `ja-JP`) | 已採納 |
| [ADR-0003](./docs/adr/zh-TW/ADR-0003-three-halls-and-calendar-driven-attendance.md) ([EN](./docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md)) | 三大廳路由與行事曆驅動差勤模型 | 已採納（部分由 [ADR-0008](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md) 補充修訂） |
| [ADR-0004](./docs/adr/zh-TW/ADR-0004-rbac-and-department-data-scope.md) ([EN](./docs/adr/ADR-0004-rbac-and-department-data-scope.md)) | RBAC 結合部門樹資料範圍、DBA 救急與三層加密策略 | 已採納 |
| [ADR-0005](./docs/adr/zh-TW/ADR-0005-dynamic-form-versioning-and-approval-workflow.md) ([EN](./docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md)) | 動態表單版本控制與多層級簽核流程 | 已採納（部分由 [ADR-0008](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md) 補充修訂） |
| [ADR-0006](./docs/adr/zh-TW/ADR-0006-ai-module-scope-and-async-stt.md) ([EN](./docs/adr/ADR-0006-ai-module-scope-and-async-stt.md)) | 務實的 AI 模組範疇（對話、文字轉換與非同步語音轉文字） | 已採納 |
| [ADR-0007](./docs/adr/zh-TW/ADR-0007-project-naming.md) ([EN](./docs/adr/ADR-0007-project-naming.md)) | 專案命名（"Atria"）與資料表前綴對齊 (`iam_`, `att_`, `wf_`, `ntf_`, `ann_`, `ai_`) | 已採納 |
| [ADR-0008](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md) ([EN](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md)) | 差勤、請假與加班規則補充及 Phase 3/4 施工順序（台日勞基法對齊） | 已採納 |

---

## 授權條款

本專案採用 [MIT License](./LICENSE) 授權。
