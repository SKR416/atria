# Atria — 差勤與簽核流程管理平台 (Attendance & Approval Workflow Platform)

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **目前開發進度：** `Phase 2 — 前端三大廳骨架與 IAM 管理後台（已完成）` ｜ **下一階段：** `Phase 3a — 差勤核心與多地區行事曆`  
> 目前已完成 Phase 0–2（`atria-backend` IAM/RBAC/部門資料範圍/AES-256-GCM 加密 + `atria-frontend` Vue 3 三大廳版面與 IAM 管理後台）。完整開發時程請參閱 [docs/ROADMAP.md](./docs/ROADMAP.md)。

Atria 是一套整合差勤管理與申請審批流程的企業級平台。員工可打卡、提出請假與補打卡申請，由直屬主管與 HR 透過多層級流程審批，核准後自動回寫差勤紀錄。後端採 Spring Boot 3，前端採 Vue 3，為模組化單體架構，具備 RBAC、動態表單與三語系（繁中／英／日）支援。

---

## 為什麼命名為「Atria」？

> *Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

**Atria** 是拉丁文與英文中「中庭／大廳（atrium）」的複數形，象徵三個服務大廳圍繞著同一個後端中樞運作：

- **`/public`（公開前台大廳）：** 免登入瀏覽的對外系統公告，支援置頂與分類篩選。
- **`/portal`（員工差勤與請假入口）：** 全體員工的日常工作台，整合上下班打卡、互動式公司行事曆、點擊日期請假／申請補打卡、內部公告欄，以及輕量 AI 工具。
- **`/admin`（主管與管理主系統）：** 專供部門主管、HR 與系統管理員使用的管理中樞，首頁儀表板即時顯示團隊出缺勤狀況與打卡紀錄，並提供多層級簽核、HR 公司行事曆定義、動態表單設計與 RBAC 權限管理。

---

## 技術棧選型

| 層級 | 技術選型 | 說明 |
| :--- | :--- | :--- |
| **後端核心** | Java 21 LTS, Spring Boot 3 | 模組化單體架構 (`io.github.skr416.atria`) |
| **安全與權限** | Spring Security 6, JWT (jjwt) | 無狀態認證、RBAC 角色權限、部門樹資料範圍過濾 (`@DataScope`) |
| **資料庫** | PostgreSQL 16, Flyway | 關聯式資料庫搭配原生 `JSONB` 儲存動態表單與多語系欄位 |
| **前端核心** | Vue 3, Vite, TypeScript, PrimeVue, ApexCharts | 單頁應用 (SPA)，高資訊密度日系 B2B 介面，內建三套獨立版面配置 (`/public`, `/portal`, `/admin`) |
| **多語系 (i18n)** | Spring `MessageSource`, `vue-i18n` | 全面支援繁體中文 (`zh-TW`)、英文 (`en-US`)、日文 (`ja-JP`) |
| **API 契約** | Springdoc OpenAPI 3 | 自動產生 Swagger 文件與前端 TypeScript 型別 |

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
| **Phase 1** | `v0.2.0-iam` | IAM 資料表 (`iam_*`)、JWT 認證、RBAC、部門樹與主管關聯、`@DataScope`、`@AuditLog`、AES-256-GCM | ✅ 已完成 |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 前端骨架 (`atria-frontend`)、三大廳版面、IAM 帳號與權限管理畫面、`hire_date` (`V2`) | ✅ 已完成 |
| **Phase 3a** | `v0.4.0-attendance-core` | 多地區行事曆 (`TW`/`JP` 模板)、不可變打卡紀錄 (`att_punch_log`) + 日結表 (`att_daily_record`)、Bot 代打卡、月結鎖定、`/portal` 與 `/admin` 行事曆介面 | ⏳ 下一階段 |
| **Phase 3b** | `v0.4.5-leave-overtime` | 假別與年資特休給假規則 (`hire_date`)、請假時數計算器與額度帳本、台日加班費率與上限引擎、補休折算 | ⏳ 規劃中 |
| **Phase 4** | `v0.5.0-workflow` | 動態表單模板 (`JSONB`)、結構化條件多層級審批流程 (`wf_*`)、職務代理人 (`wf_deputy`)、通知中心 (`ntf_*`) | ⏳ 規劃中 |
| **Phase 5** | `v0.6.0-dashboard-notice` | 主管首頁出缺勤即時看板、異常紀錄篩選、計薪加權時數 Excel/CSV 匯出、對外與內部公告系統 (`ann_*`) | ⏳ 規劃中 |
| **Phase 6** | `v0.7.0-ai-suite` | 嵌入式 AI 對話 (SSE Chat)、職場文字轉換、非同步音檔轉文字記錄 (STT) | ⏳ 規劃中 |

---

## 架構決策紀錄 (ADRs)

所有架構取捨與命名決策皆記錄於 [`docs/adr/`](./docs/adr/) 目錄下（繁體中文對照版位於 [`docs/adr/zh-TW/`](./docs/adr/zh-TW/)）：

- [ADR-0001: 模組化單體架構與核心技術棧選型](./docs/adr/zh-TW/ADR-0001-modular-monolith-and-tech-stack.md) ([EN](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md))
- [ADR-0002: 三層式多語系架構 (`zh-TW`, `en-US`, `ja-JP`)](./docs/adr/zh-TW/ADR-0002-three-tier-i18n-architecture.md) ([EN](./docs/adr/ADR-0002-three-tier-i18n-architecture.md))
- [ADR-0003: 三入口路由分流與行事曆驅動之差勤模型](./docs/adr/zh-TW/ADR-0003-three-halls-and-calendar-driven-attendance.md) ([EN](./docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md))
- [ADR-0004: RBAC 結合部門樹直屬主管與資料範圍隔離](./docs/adr/zh-TW/ADR-0004-rbac-and-department-data-scope.md) ([EN](./docs/adr/ADR-0004-rbac-and-department-data-scope.md))
- [ADR-0005: 動態表單版本快照與多層級審批流程](./docs/adr/zh-TW/ADR-0005-dynamic-form-versioning-and-approval-workflow.md) ([EN](./docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md))
- [ADR-0006: AI 模組務實邊界（對話、文字轉換與非同步語音轉文字）](./docs/adr/zh-TW/ADR-0006-ai-module-scope-and-async-stt.md) ([EN](./docs/adr/ADR-0006-ai-module-scope-and-async-stt.md))
- [ADR-0007: 專案命名決策（Atria）與資料表前綴對齊規範](./docs/adr/zh-TW/ADR-0007-project-naming.md) ([EN](./docs/adr/ADR-0007-project-naming.md))
- [ADR-0008: 企業級差勤、假勤、加班與簽核流程精煉規範（台日勞基法對齊）](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md) ([EN](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md))

---

## 授權條款

本專案採用 [MIT License](./LICENSE) 授權。
