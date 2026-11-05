# ADR-0005：動態表單版本控制與多層級簽核流程

> 本文件為 [ADR-0005 英文版](../ADR-0005-dynamic-form-versioning-and-approval-workflow.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納（部分由 [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md) 補充修訂）
- **日期：** 2026-10-28

## 背景

Atria 同時支援內建差勤申請（`att_request`：請假與補卡）以及管理員自訂的動態表單（`wf_form_template`）。動態表單系統中常見的一項缺陷是：當管理員在正式環境修改表單的欄位 Schema 時，會導致數個月前提交的歷史簽核記錄無法正確渲染或欄位對齊錯誤。

## 決策

1. **採用 PostgreSQL `JSONB` Schema 搭配不可變版本快照（`wf_form_template`）：**
   - 表單欄位定義（欄位 key、元件類型、驗證規則、三語標籤）儲存於 `schema_json JSONB` 欄位中。
   - 每次發布變更都會遞增範本的 `version` 編號。
   - 當送出簽核流程實例（`wf_instance`）時，同時記錄使用者的 `form_data JSONB`，以及對送出當下生效之確切 `form_version`（與 `schema_snapshot JSONB`）的快照參照。
2. **資料表驅動的簽核流程與條件規則（`wf_flow_definition`、`wf_flow_node`、`wf_flow_node_condition`）：**
   - 簽核路由完全透過關聯式資料庫表格驅動，而非硬編碼邏輯或資料庫觸發器：
     - `wf_flow_definition`：定義流程代碼、表單類型與三語名稱。
     - `wf_flow_node`：設定有序的簽核關卡（`step_order`）、簽核人策略（`DIRECT_LEADER`、`PARENT_LEADER`、`SPECIFIC_ROLE`）、自我簽核自動跳過旗標與代理允許旗標。
     - `wf_flow_node_condition`：定義由 Java 簽核流程引擎評估的動態啟動條件（例如 `duration_days > 5` 會在人資歸檔前額外啟動 `DIVISION_DIRECTOR` 關卡）。
   - 人資管理員可透過專屬的 UI 卡片介面設定門檻規則，無需修改應用程式原始碼或重新啟動服務。
3. **明確的簽核流程狀態機與生命週期操作（`wf_instance` 與 `wf_task_log`）：**
   - 狀態：`DRAFT`、`PENDING`、`APPROVED`、`REJECTED`、`REVOKED`。
   - **退回草稿（方案 A - 差戻し）：** 簽核人可將待簽核表單退回至 `DRAFT`。當申請人修改並重新送出時，簽核流程重新由關卡 1（Step 1）開始，並提供稽核 Diff 突顯與前次送出的變更差異。
   - **申請人抽單（取り下げ）：** 申請人可在最終核准前抽回任何表單。抽單會將表單轉移為 `DRAFT`，並向先前已核准較早關卡的所有簽核人廣播站內通知。
   - **自我簽核迴避：** 若申請人即為目前簽核關卡的主管，引擎會自動將該關卡跳過至上級部門主管。
   - **代理簽核（`wf_deputy`）：** 代理簽核會同時記錄操作者與委託人的 ID，以利嚴格的合規性稽核。
   - **樂觀鎖（`@Version`）與人資強制改派：** `wf_instance` 與 `att_leave_balance` 強制實施 JPA `@Version` 樂觀鎖，以防止主管與代理人同時進行重複核准。當簽核人離職或無法處理時，人資管理員可強制改派待辦任務（`REASSIGN`），並於 `wf_task_log` 中留下完整稽核日誌。
4. **交易性差勤回寫（由 [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md) 補充修訂）：**
   - 當連結至 `att_request` 的簽核流程實例進入終態或抽單時，會在同一個資料庫交易內發布領域事件（`AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent`）：
     - `LEAVE`（請假）：將 `att_leave_balance` 中凍結的 `pending_hours` 轉為 `used_hours`，並更新 `att_daily_record` 為 `ON_LEAVE`。
     - `PUNCH_CORRECTION`（補卡）：於 `att_punch_log` 新增一筆 `CORRECTION` 來源紀錄，並重新計算 `att_daily_record`。
     - `OVERTIME`（加班）：認列核准之加權加班分鐘數或轉入補休發放帳本（`att_comp_leave_grant`）。

## 影響

- 無論未來範本如何修改，歷史簽核表單均永久可稽核且精確渲染。
- 使用輕量級的領域狀態機取代嵌入繁重的 BPMN 2.0 引擎（如 Camunda 或 Flowable），能維持較低的記憶體佔用並具備高度程式碼易讀性，同時涵蓋 95% 的企業簽核情境。
