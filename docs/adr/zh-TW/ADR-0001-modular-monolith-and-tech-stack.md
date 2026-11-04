# ADR-0001：模組化單體架構與核心技術棧

> 本文件為 [ADR-0001 英文版](../ADR-0001-modular-monolith-and-tech-stack.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納
- **日期：** 2026-10-28

## 背景

Atria 整合了企業差勤追蹤（工作行事曆、每日打卡、請假與補卡申請）、具備動態表單的多層級簽核流程、RBAC（角色權限控管）管理、雙重範圍公告，以及輕量級 AI 工具。我們需要在以下方案間做出抉擇：

1. 使用 Spring Boot + Thymeleaf 的伺服器端渲染（SSR）單體架構。
2. 每個領域具備獨立儲存庫與資料庫的微服務架構。
3. 採用單一儲存庫（Monorepo），後端為**模組化單體**（Spring Boot 3 + PostgreSQL 16），搭配解耦的**單頁應用程式**（Single-Page Application）前端（Vue 3 + TypeScript + PrimeVue + ApexCharts）。

## 評估方案

### 方案 A：Spring Boot + Thymeleaf（SSR）
- **優點：** 單一建置產物；無需獨立的前端建置管線。
- **缺點：** 互動式行事曆檢視、動態表單 Schema 渲染，以及用於 AI 對話的 Server-Sent Events（SSE）都需要大量的前端狀態管理，在 jQuery/Thymeleaf 範本中極易變得脆弱且難以維護。

### 方案 B：分散式微服務
- **優點：** 各服務可獨立部署與擴展。
- **缺點：** 龐大的維運與分散式交易開銷（例如當已核准的簽核流程實例必須以不可分割的原子性更新差勤記錄時）。對企業基礎平台而言屬於不必要的過度複雜化。

### 方案 C：模組化單體（Monorepo 中的 Spring Boot 3 + PostgreSQL 16 + Vue 3）
- **優點：**
  - 透過 Java 套件結構強制落實清晰的領域邊界（`iam`、`attendance`、`workflow`、`dashboard`、`announcement`、`ai`），同時在簽核流程與差勤回寫之間保有 ACID 交易特性。
  - PostgreSQL 16 針對動態表單 Schema 與三語字典欄位提供原生 `JSONB` 支援，並提供適用於部門層級階層的遞迴 CTE。
  - **前端技術棧與 SCRM 合規：** 採用 **PrimeVue**（歐洲 / PrimeTek，100% MIT）與 **ApexCharts**（100% MIT），嚴格排除具地緣政治背景爭議或供應鏈審查疑慮的元件（例如 Baidu ECharts 或 Alibaba Element Plus），同時開箱即提供企業級高資訊密度元件（`DataTable`、`TreeTable`、`Timeline`）與差勤熱圖視覺化功能。
  - 支援函式庫：採用 `@fullcalendar/vue3` 呈現企業排班時程、`@tiptap/vue-3` 用於經無害化消毒的公告編輯，以及獨立的 `whisper.cpp` HTTP 服務進行本機語音轉文字（STT）轉錄，免除 JVM 崩潰風險。
  - 單一儲存庫（Monorepo）確保每次 Git commit 都能讓後端程式碼、前端程式碼與 ADR 保持步調一致。

## 決策

我們採納**方案 C**：在單一儲存庫（`atria`）中管理 **Spring Boot 3（Java 21 LTS）+ PostgreSQL 16 + Vue 3（Vite、TypeScript、PrimeVue、ApexCharts）的模組化單體**架構。

## 影響

- 跨模組互動（例如簽核完成時更新差勤狀態）可於本機資料庫交易或 Spring 應用程式事件內執行，無網路序列化開銷。
- 模組邊界必須嚴格維持清晰：各模組應透過公開服務介面進行通訊，而非直接存取其他模組的 repository。
