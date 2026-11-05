# ADR-0007：專案命名（"Atria"）與資料表前綴對齊

> 本文件為 [ADR-0007 英文版](../ADR-0007-project-naming.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納
- **日期：** 2026-10-28

## 背景

在最初的腦力激盪階段，專案曾使用 `Enterprise Base & ESS Portal` 與 `com.enterprise.base` 等暫定名稱，且數個資料庫資料表前綴已與其所屬的領域模組脫節：
- 身分與存取管理（IAM）模組命名為 `iam`，但其資料表卻使用 `sys_` 前綴（`sys_user`、`sys_department`、`sys_role`、`sys_permission`、`sys_audit_log`）。
- 簽核代理（`sys_role_deputy`）被置於 `sys_` 之下，即便它實質掌管的是 `workflow` 模組中的簽核路由。
- `att_leave_request` 同時儲存請假申請與漏打卡補卡申請，使得資料表名稱中的 `leave` 不夠精確。

我們同時也需要一個好記、無命名衝突且能反映系統架構的專案名稱，而非空泛的流行術語。

## 曾考量的替代方案

- **`enterprise-starter` / `business-admin`：** 過於通用；GitHub 上有數百個樣板儲存庫使用這些名稱，且無法傳達核心領域（差勤 + 簽核流程）。
- **`kintai-flow`：** 混合日語羅馬拼音與英語；在三語文件中顯得不夠自然。
- **`atrium`（單數）：** 能傳達中央中庭大廳的意象，但無法體現三個獨立的進入點（`/public`、`/portal`、`/admin`）。

## 決策

1. **專案名稱 —— `Atria`：**
   - **名稱：** `Atria`（儲存庫：`atria`）
   - **副標題：** *Attendance & Approval Workflow Platform*
   - **架構隱喻：** *Atria 為 atrium（中庭）的複數形：圍繞著單一共享後端核心樞紐的三大廳（公開站點、員工自助入口、管理後台）。*
   - **座標規格：**
     - Java 套件 / Maven `groupId`：`io.github.skr416.atria`
     - Maven `artifactId`：`atria-backend`
     - 前端 npm 套件名稱：`atria-frontend`
   - **衝突檢查：** 已確認 GitHub 上 `SKR416/atria` 尚未使用。

2. **嚴格的模組與資料表前綴對齊：**
   - 將所有 `sys_*` 資料表重新命名為 `iam_*`（`iam_user`、`iam_department`、`iam_role`、`iam_permission`、`iam_audit_log`），以與 `iam` 模組一致。
   - 將 `sys_role_deputy` 移至 `workflow`（`wf_`）模組下並重新命名為 `wf_deputy`。
   - 將 `att_leave_request` 重新命名為 `att_request`，並加入 `request_type` 欄位（`LEAVE` | `PUNCH_CORRECTION` | `OVERTIME`）。
   - 為 Phase 4 導入的通知中心基礎底座增設 `ntf_*` 資料表前綴（見 [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md)）。

3. **文件與用詞紀律：**
   - 維護根目錄的三語 README（`README.md`、`README.zh-TW.md`、`README.ja.md`）與英文 ADR（`docs/adr/`）。
   - 準確地將系統描述為**模組化單體（modular monolith）**（絕不稱為「雲原生」或「AI-Agentic」）。
   - README 必須隨時載明當前 Phase/Tag，且僅描述已實作完成的功能。

## 影響

- 每個 Java 模組（`iam`、`attendance`、`workflow`、`announcement`、`ai`）現在皆能精準對應至其 SQL 資料表前綴（`iam_`、`att_`、`wf_`、`ann_`、`ai_`）。
- **Atria** 這個名稱在文件與技術面試中提供了清晰、令人印象深刻的論述敘事。
