# 三語 README 與架構文件同步檢驗規範 (README & Docs Sync Checklist)

> **觸發時機（When to Read）**：
> 1. 每完成一個開發階段（Phase / Sub-phase）準備收尾 commit 時。
> 2. 準備執行 `git push` 將階段成果推送至遠端儲存庫前。
> 3. 使用者要求更新或檢查 README / 文件進度時。
>
> **核心設計原則**：本文件獨立存放於 `docs/README_SYNC_CHECKLIST.md`，平時日常寫 Code 不預載入 Context；一旦進入「階段收尾 / 推送」環節，Agent 必須先讀取本檔並逐項執行檢驗，禁止只改狀態徽章就草率提交。

---

## 一、 必備同步檔案群 (Target Files)

每次推進階段時，以下檔案必須視為同一個原子群組進行交叉比對：

1. **三語根目錄 README（缺一不可）**：
   - `README.md`（英文）
   - `README.zh-TW.md`（繁體中文）
   - `README.ja.md`（日文）
2. **進度與架構文件**：
   - `docs/ROADMAP.md`（階段完成勾選 `[x]` 與狀態表 `✅ Completed` / `⏳ Next`）
   - `docs/NEXT_STEPS.md`（目前進度、測試驗證數量、下一階段開工待辦與引導 Prompt）
   - `docs/ARCHITECTURE.md`（實際套件結構樹與資料表前綴/欄位說明）
3. **架構決策紀錄（ADR）**：
   - `docs/adr/ADR-*.md`（英文正本）
   - `docs/adr/zh-TW/ADR-*.md` 及 `docs/adr/zh-TW/README.md`（繁中譯本與索引）

---

## 二、 三語 README 七大必檢要素 (7 Essential Elements of README)

更新三語 README 時，必須逐節檢查以下 **7 大要素**，確保三語版本結構 100% 對稱、沒有遺漏章節或夾雜錯誤語系字元：

### 1. 頂部進度橫幅 (Status Banner)
- [ ] **Current Status** 明確標示最新完成的階段名稱與對應 Git Tag（例如 `Phase 2 — ... (Completed, v0.3.0-frontend-shell)`）。
- [ ] **Next Milestone** 明確標示緊接著要進行的階段與目標 Git Tag。
- [ ] **驗證摘要**：簡述目前已通過的後端整合測試數量與前端建置驗證狀態，並附 `docs/ROADMAP.md` 與 `docs/NEXT_STEPS.md` 連結。

### 2. 已實作功能專章 (`Implemented Capabilities` / `目前已實作功能` / `実装済み機能`)
- [ ] 依據 `ADR-0007` Decision 3（*READMEs must always state the current phase/tag and describe only what is implemented*），逐階段條列從 `Phase 0` 到**當前已完成階段**的所有落地成果：
  - **Flyway 版本範圍與資料表**：列出已建立至第幾版（如 `V1`–`V2`）及核心資料表名。
  - **後端模組與核心機制**：已實作的 Controller / Service / AOP（如 `@DataScope`, `@AuditLog`）、安全與加解密機制、排程或領域事件。
  - **前端畫面與路由**：已在 `/public`、`/portal`、`/admin` 實作完成的實際 Vue 頁面元件名稱（如 `DepartmentManageView.vue`）與核心互動能力。

### 3. 技術棧表格 (`Tech Stack` / `技術棧選型` / `技術スタック`)
- [ ] 檢查表格中的框架版本、安全元件（如 `BCrypt`, `AES-256-GCM`）、資料庫 Flyway 版本範圍（如 `V1`–`V2`）、前端核心套件（如 `PrimeVue 4`, `Pinia`, `Vue Router 4`, `Lucide Icons`）是否反映目前已引入的真實依賴。

### 4. 專案目錄結構樹 (`Repository Structure` / `專案目錄結構` / `リポジトリ構造`)
- [ ] **三語 README 皆必須包含此區塊**（嚴禁英文版有、中日文版漏掉）。
- [ ] `backend/` 與 `frontend/` 子目錄樹必須反映當前已落地的實際套件與資料夾（例如新增了 `attendance/` 模組或 `V3` 遷移檔時，目錄樹與註解須同步推進），且與 `docs/ARCHITECTURE.md` 一致。

### 5. 本地快速啟動 (`Quick Start` / `快速啟動` / `クイックスタート`)
- [ ] 確認啟動指令、連接埠（Backend `8080`, Frontend `5173`）、預設 Profile（`local`）與種子測試帳號清單是否與最新程式碼相符。

### 6. 開發階段里程碑表格 (`Implementation Roadmap & Status`)
- [ ] 表格的階段切分（含 `Phase 3a` / `Phase 3b` 等子階段）、目標 Git Tag、實作範圍描述與狀態圖示（`✅ Completed` / `⏳ Next` / `⏳ Planned`）必須與 `docs/ROADMAP.md` 完全一致。

### 7. 架構決策紀錄表格 (`Architecture Decision Records (ADRs)`)
- [ ] 完整列出所有 `ADR-0001` ～ 最新 `ADR-000N`，且標題與 `docs/adr/` 正本及 `docs/adr/zh-TW/README.md` 索引完全一致（禁止在 README 中自行杜撰不同標題）。
- [ ] 附加上英文正本與繁中譯本雙連結，以及 `docs/adr/zh-TW/README.md` 索引連結。
- [ ] 若某篇舊 ADR 被新 ADR 補充修訂（例如 `ADR-0003`、`ADR-0005` 被 `ADR-0008` 補充修訂），必須在狀態欄明確標註 `Accepted (refined by ADR-0008)` / `已採納（部分由 ADR-0008 補充修訂）`。

---

## 三、 四步標準檢驗流程 (4-Step Verification SOP)

在修改完 README 與關聯文件、準備執行 `git commit` / `git push` 前，依序執行以下 4 步檢驗：

### Step 1：ADR 後續待辦回頭清查 (ADR Consequences Audit)
- 檢查最新一篇或多篇 ADR 底部的 `Consequences`（影響）章節，確認是否有註明「某階段完成後必須更新某文件或某資料表命名」（例如舊表名 `att_punch_record` 替換為 `att_punch_log` + `att_daily_record`）。
- 使用 `Grep` 搜尋 `docs/` 與 `README*`，確認已廢棄的舊表名、舊套件路徑（如 `modules/iam`）或未定案佔位文字（如 `Exact Git tags are finalized when...`）已全數更新。

### Step 2：三語章節對稱與跨語系殘字檢查 (Trilingual Symmetry & Typo Check)
- 對照 `README.md`、`README.zh-TW.md`、`README.ja.md` 的二级標題（`##`）數量與順序，確認三份檔案章節結構 100% 對齊。
- 檢查 `README.ja.md` 是否誤殘留繁體中文字彙（如將「プロジェクト」誤寫為「專案」），以及 `README.zh-TW.md` 連結是否正確指向 `./docs/adr/zh-TW/`。

### Step 3：實際目錄與 `ARCHITECTURE.md` 對照 (Codebase Reality Check)
- 對照 `backend/src/main/java`、`backend/src/main/resources/db/migration` 與 `frontend/src` 的實際目錄，確認 `README*` 與 `docs/ARCHITECTURE.md` 中的目錄樹、Flyway 版本號（`V1`–`Vn`）與測試案例數量完全吻合。

### Step 4：Git Diff 乾淨度確認 (Pre-Push Diff Verification)
- 執行 `git diff README.md README.zh-TW.md README.ja.md docs/` 檢視變更內容，確認無誤後再與階段提交一併（或獨立成 `docs(readme): ...` commit）推送至遠端。
