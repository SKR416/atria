# ADR-0008：差勤、請假與加班規則補充及 Phase 3/4 施工順序

> 本文件為 [ADR-0008 英文版](../ADR-0008-attendance-leave-overtime-refinements.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納
- **日期：** 2026-11-04
- **補充修訂：** ADR-0003（差勤模型）、ADR-0005（簽核引擎）

## 背景

在 Phase 2（前端殼層）進行期間所舉行的設計審查中，浮現了 ADR-0003 與 ADR-0005 之間的不相容與缺漏處：

1. ADR-0003 宣告 `raw_punch_time` 為不可變（immutable），然而 ADR-0005 卻說明已核准的補卡會「更新 `att_punch_record`」。單一資料表既無法同時滿足這兩者，亦無法塑模一日多次打卡的情境。
2. 部門支援多種時區（`Asia/Taipei`、`Asia/Tokyo`），但單一全公司適用的行事曆無法表達不同國家的國定假日。
3. 加班費倍率取決於日子的*法定類別*（台灣：例假日 vs 休息日 vs 國定假日；日本：法定休日 vs 所定休日），這是現有的 `WORKDAY / HOLIDAY / MAKEUP_DAY` 所無法區分的。
4. Phase 3 的差勤申請在 Phase 4 簽核流程引擎建置完成前即需要簽核機制；Phase 4 的抽單通知在 Phase 5 前即需要通知機制。
5. H2 的相容模式不支援 Phase 4 所仰賴的 PostgreSQL `JSONB` 運算子。

**定位：** Atria 是一項作為*能力展示（capability showcase）*而打造的作品集專案。各項規則均依循符合台灣《勞動基準法》與日本《労働基準法》的正式生產級標準設計，並以**具備內建 TW/JP 模板的規則表**實作，而非硬編碼（hardcode）於程式邏輯中。預載的法定種子數值於任何正式上線使用前，必須經由 HR/法務人員查核確認。

## 決策

### 1. 將打卡資料拆分為不可變日誌與衍生每日出勤紀錄

- **`att_punch_log`**（僅可新增）：每次打卡事件一筆資料列 —— `user_id`、`raw_punch_time`（UTC，秒精度）、`client_ip`、`source`（`WEB` | `BOT` | `CORRECTION`）、`bot_client_id`。資料列絕不更新或刪除，並於 ORM 層（`@Immutable`、`@PreUpdate`、`@PreRemove`）與 PostgreSQL 資料庫層（`BEFORE UPDATE OR DELETE` 觸發器 `trg_att_punch_log_immutable`）實作雙重強制阻擋。
- **`att_daily_record`**（衍生）：每位使用者每個邏輯工作日一筆資料列 —— `work_date`（依據該部門時區，並遵循 `day_cutoff_hour` 換日時點）、`effective_clock_in`、`effective_clock_out`、`worked_minutes`、`status`（`NORMAL` | `LATE` | `EARLY_LEAVE` | `MISSING_PUNCH` | `ON_LEAVE` | `ABSENT`）、`version`（`@Version`）。
- 經核准的 `PUNCH_CORRECTION`（補卡申請）會新增一筆來源為 `CORRECTION` 且關聯至該筆 `att_request` 的打卡日誌資料列，接著重新計算每日出勤紀錄。原始打卡日誌保持原封不動，以供勞動法規稽核之需。
- 取代 ADR-0003 中所提及的單一 `att_punch_record` 資料表。

### 2. 具備法定日子類別與 TW/JP 模板的多區域行事曆

- `att_work_calendar` 增設 **`calendar_code`**（例如 `TW`、`JP`）；`iam_department.calendar_code` 將各部門綁定至特定行事曆。
- **日子類別（Day types）：** `WORKDAY`、`MAKEUP_WORKDAY`、`REST_DAY`（休息日 / 所定休日）、`STATUTORY_REST_DAY`（例假日 / 法定休日）、`NATIONAL_HOLIDAY`。
- HR 擁有完整的行事曆編輯權限。**「產生年度（Generate Year）」動作**可從模板預先填入一整年的行事曆，其後由 HR 自由微調：
  - **TW 模板：** 週六 = `REST_DAY`，週日 = `STATUTORY_REST_DAY`；固定國曆節日；農曆節日（農曆新年、端午節、中秋節）透過農曆庫演算法計算；補假規則。每年公佈的調整放假日／週六補班日（人事總處公告）由 HR 手動輸入。
  - **JP 模板：** 週六 = `REST_DAY`，週日 = `STATUTORY_REST_DAY`；依日本內閣府公佈規則演算法推算之節日（固定日期、Happy Monday 制度、春分／秋分公式、振替休日、国民の休日）。
- 模板完全由 Java 程式內建產生（無外部 API 依賴），並針對官方公佈的放假清單撰寫單元測試以確保正確性。

### 3. 每日結算排程與核准單據之每日出勤重算整合

- 各時區於 `day_cutoff_hour`（預設為 `04:00`）後執行排程工作，以終結並結算前一個邏輯工作日，產出 `MISSING_PUNCH` / `ABSENT` 等狀態。
- 經核准的請假、加班或補卡申請僅會觸發重新計算受影響的日期（呼叫 `AttendanceRecordCalculator.recomputeDailyRecord(userId, workDate, settledPass)`）：
  - **已核准請假 (`LEAVE`)：** 自動彙總當日已核准的 `leaveMinutes`。若請假區間涵蓋排班起點（如 `09:00`–`13:00`），遲到起算基準自動順延至請假結束時間（`13:00`）；若涵蓋排班終點（如 `14:00`–`18:00`），早退起算基準提前至請假開始時間（`14:00`）。當 `workedMinutes + leaveMinutes >= 480` 且無遲到早退時，狀態判定為 `NORMAL`（當 `leaveMinutes >= 480` 全日請假時判定為 `ON_LEAVE`）。
  - **已核准加班 (`OVERTIME`)：** 自動彙總當日已核准加班時數寫入 `att_daily_record.overtime_minutes`。**Phase 3b 直接採用核准時數**；核准時數與實際打卡時間取交集的邏輯暫緩實作。
  - **已核准補卡 (`PUNCH_CORRECTION`)：** 新增一筆 `source = CORRECTION` 的不可變紀錄至 `att_punch_log`，透過 V3 既有的 `correction_request_id` 欄位關聯申請單（V4 補上指向 `att_request` 的外鍵），隨後直接呼叫 `recomputeDailyRecord` 重算。

### 4. 假別、最小單位、依年資給予之特休與 FIFO 額度帳本

- **`att_leave_type`**：三語名稱（`JSONB`）、`min_unit_minutes`（例如 30、60、240 = 半天；依各假別可設定）、支薪旗標（paid flag）、餘額扣抵追蹤旗標（balance-tracked flag）。
- **請假時數計算：** 淨請假分鐘數 = 請假區間 ∩ 行事曆工作時間，扣除固定的 12:00–13:00 午休時間，並**無條件進位**至 `min_unit_minutes` 的整數倍。
- **先進先出扣抵（FIFO）與精確帳本異動明細（`att_leave_balance_tx`）：** 當員工同時擁有多筆有效 `att_leave_balance` 額度桶（例如：去年度遞延特休與今年度特休、或不同到期日之加班補休），系統預設依 **`valid_until ASC`（先進先出，最早到期者優先扣抵）** 自動挑選額度桶。每次送單預扣（`PENDING`）、核准實扣（`APPROVED`）或駁回／撤銷退還（`REJECTED` / `REVOKED`），均逐筆記錄於 `att_leave_balance_tx`（`request_id`、`balance_id`、`tx_type`、`minutes`），確保跨多個效期桶子時仍能 **100% 精準原路退回**原帳本。
- **補休額度單一可信來源：** 每筆 `att_comp_leave_grant` 核發時同步建立一筆 `att_leave_balance` 額度桶（`source_comp_grant_id` 指向 grant）。補休用量**只**記錄於 `att_leave_balance` / `att_leave_balance_tx`，grant 資料列不儲存已休分鐘數。
- **`att_leave_grant_rule`**（規則表，依 `calendar_code` 預載種子資料）：
  - **TW（第 38 條）：** 滿 6 個月至 1 年：3 天；滿 1 至 2 年：7 天；滿 2 至 3 年：10 天；滿 3 至 5 年：14 天；滿 5 至 10 年：15 天；滿 10 年以上：每滿 1 年加給 1 天，上限為 30 天。給假基準欄位保留支援 `ANNIVERSARY`（到職週年制）與 `CALENDAR_YEAR`（曆年制），**Phase 3b 排程演算法直接實作最明確通用的到職週年制（`ANNIVERSARY`）**。年度終結未休日數：依勞資協商折發工資或遞延一年。
  - **JP（第 39 條）：** 滿 6 個月：10 天；滿 1.5 年：11 天；滿 2.5 年：12 天；滿 3.5 年：14 天；滿 4.5 年：16 天；滿 5.5 年：18 天；滿 6.5 年以上：20 天。要求前一週期出勤率 ≥ 80%；時效 2 年過期。小時特休（時間単位年休）以每年 5 天為上限。追蹤被給予 ≥ 10 天年休之員工每年強制休 5 天的法定義務（年5日取得義務）。
- 自動給假排程工作會自動建立 `att_leave_balance` 資料列；HR 仍可手動給假或設定協商假。
- **前置要件：** `iam_user.hire_date DATE`（見決策 11）。

### 5. 加班記錄、倍率規則與上限

- Atria 記錄**經核准的加班分鐘數及其加權等效時數**，絕不記錄金錢金額（不儲存任何薪資資料）。計薪系統後續可取用加權時數匯出檔（Phase 5）。
- **`att_overtime_rate_rule`**：`calendar_code`、`day_type`、`from_minute`、`to_minute`、`multiplier`（`NUMERIC(6,4)`）、`is_night_premium`。預載模板（可由 HR 編輯）：
  - **TW：** 採台灣企業實務標準倍率 —— 平日加班前 2 小時 **`×1.34`**（`1.3400`），第 3 小時起 **`×1.67`**（`1.6700`）；休息日出勤前 2 小時 **`×1.34`**、第 3–8 小時 **`×1.67`**、第 9–12 小時 **`×2.67`**；例假日與國定假日出勤 **`×2.00`**。
    - *已知簡化：* 依勞基法第 39、40 條，國定假日或例假日出勤 8 小時內應加發**一日**工資（按日計，而非按分鐘），超過 8 小時部分適用加班倍率；例假日出勤僅限第 40 條情形且須給予補假。目前以按分鐘 `×2.00` 計算屬建模簡化，正式上線前須由 HR／法務確認。
  - **JP：** 法定加班 ×1.25；單月加班超過 60 小時部分 ×1.50；法定休日出勤 ×1.35；深夜（22:00–05:00）額外加給 +0.25。
    - *Phase 3b 引擎範圍：* 計算器僅評估累計分鐘級距（`from_minute` / `to_minute`）。深夜加給（依時鐘時段判定）與單月超過 60 小時級距（依當月累計判定）僅預載種子資料供顯示，**暫不納入計算**，留待後續階段。
- **`att_overtime_cap_rule`**：可設定的每月／跨月／年度上限，並可在送出申請時採取 `WARN` 或 `BLOCK` 處置動作。種子資料：TW 每月 46 小時（經工會或勞資會議同意得延長至每月 54 小時、每 3 個月 138 小時）；JP 36 協定（三六協定）每月 45 小時且每年 360 小時、特別條款每年 720 小時、含假日加班單月 < 100 小時、2 至 6 個月平均 ≤ 80 小時、超過 45 小時每年最多 6 次。
- 每筆 `OVERTIME`（加班申請）可選擇補償方式：`PAY` 或 `COMP_LEAVE`。

### 6. 補休轉換（可設定折算比例與無條件進位）

- 依 `calendar_code`（或各倍率規則）設定 **`comp_leave_mode`**：
  - `HOURS_1_TO_1` —— 法定基準：補休分鐘數 = 加班分鐘數。
  - `RATE_MULTIPLIED` —— 企業激勵（業界常見作法）：補休分鐘數 = `CEILING(Σ(各級距分鐘數 × 級距 multiplier))`，統一以 `BigDecimal` 運算並依優惠員工原則對分鐘數採 **`RoundingMode.CEILING` 無條件進位至整數分鐘**。驗證機制強制要求實質折算比例 ≥ 1.0。
- 每筆 `att_comp_leave_grant` 補休發放資料列均會儲存其來源 `att_request`、**各倍率級距之原始加班分鐘數 JSON（`band_breakdown_json`）**、給予的補休分鐘數以及 `expires_at` 到期時間；已休／剩餘分鐘數一律從其關聯的 `att_leave_balance` 額度桶讀取（見決策 4）。
- **TW 到期／離職結算（第 32-1 條）：** 未休畢之補休必須依加班費率折發工資。引擎以未休比例（額度桶剩餘 ÷ 給予分鐘數）透過 `band_breakdown_json` 將未休畢的比例直接還原為各級距之*原始*加班分鐘數（避免二次乘除造成進位誤差），並產出應發工資的加權時數紀錄。
- **JP：** 標準代休僅能抵銷基礎 1.0 倍的部分；無論設定何種模式，加成部分（例如 0.25）均記錄為剩餘應發加成工資，除非 HR 依據勞資協定針對單月超過 60 小時部分設定代替休暇。

### 7. 含簽核中申請的月結關帳

- **只要有任何處於 `PENDING` 狀態的申請牽涉到該月份，月結關帳即會被阻擋**；API 會回傳阻擋關帳的申請清單供 HR 處理。系統不會進行自動駁回。
- 排程自動關帳遵循相同規則，並在受到阻擋時主動通知 HR。

### 8. 每月假別餘額彙總與 Phase 3b / Phase 4 階段分工

- **Phase 3b 實作範圍：** 先行實作「**員工假別餘額 + 90 天內即將到期補休 + 日本年休 5 日義務達成進度**」之**查詢 Service 與 `/portal` 看板 API**，讓員工與 HR 於前端即可即時檢視。
- **Phase 4 實作範圍（`LeaveBalanceDigestJob`）：** 待 Phase 4 通知基礎底座（`ntf_*` 資料表與 `EmailNotificationChannel`，見決策 10）落地後，再掛上每月 1 日 09:00 的定期寄信排程（依 `(user_id, period)` 冪等發送）。

### 9. Phase 3 ↔ Phase 4 簽核契約與單向依賴流向

- 為嚴格遵守 ArchUnit 模組無循環依賴鐵律（`ModularArchitectureTest`），**所有差勤申請領域事件統一放置於 `io.github.skr416.atria.attendance.event` 套件**：`AttRequestSubmittedEvent`、`AttRequestCancelledEvent`、`AttRequestApprovedEvent`、`AttRequestRejectedEvent`、`AttRequestRevokedEvent`。
- **固定單向依賴流向（`workflow` ➔ `attendance`）：** `attendance` 模組完全不依賴 `workflow`，僅負責在送單時發布 `AttRequestSubmittedEvent`、申請人抽單時發布 `AttRequestCancelledEvent`（供簽核引擎關閉流程實例），並監聽 `Approved` / `Rejected` / `Revoked` 事件以執行額度凍結／實扣／原路退還（`att_leave_balance_tx`）、補打卡寫入 `att_punch_log(CORRECTION)` 以及觸發 `att_daily_record` 重算。
- **`att_request` 狀態機**（含 `version` 樂觀鎖欄位）：
  - `PENDING` → `APPROVED` | `REJECTED`（簽核結果事件）。
  - `PENDING` → `CANCELLED`（申請人抽單，釋放預扣額度）。
  - `APPROVED` → `REVOKED`（核准後撤銷，退還額度並重算受影響日期）。
  - `REJECTED`、`CANCELLED`、`REVOKED` 為終態。
  - **防護規則**（由 `attendance` 對外公開的防護服務在送單、抽單、撤銷入口檢查；Phase 4 簽核引擎開啟撤銷流程前也呼叫同一服務）：凡牽涉已關帳月份的申請一律拒絕並回傳 `ATT_MONTHLY_PERIOD_CLOSED`（2005），須待 HR 重開該月；選擇 `COMP_LEAVE` 的 `OVERTIME` 申請，若其補休額度桶已有已休或預扣分鐘數則不得撤銷。
- Phase 3b 於 `attendance` 內部提供**僅限開發與測試環境啟用的直接簽核端點 Stub**（`@Profile({"local", "pgtest"})`，即本機執行與 H2／PostgreSQL 測試所使用的 profile），直接發布上述事件以驗證完整閉環；Phase 4 簽核引擎完成後直接發布相同事件即可無縫接軌。

### 10. 簽核流程引擎優化（Phase 4）

- **簽核人備援機制（Approver fallback）：** 若節點解析出的主管從缺（無 `leader_id` 或已達組織樹頂端），則沿著部門樹向上尋找；若仍未找到，則分派給 `HR` 角色。
- **條件判定：** `wf_flow_node_condition` 中的條件為結構化資料列（`field_key`、`operator`、`value`、邏輯分組），於 Java 內部求值。不採用 SpEL 或自由格式的表達式字串（防止表達式注入攻擊，並方便 HR 建置卡片式操作介面）。
- **會簽／平行簽核明確排除於範疇之外。**
- **通知基礎底座提前至 Phase 4：** `NotificationChannel` 抽象層、站內通知、`EmailNotificationChannel` 以及寄送紀錄（新資料表前綴 `ntf_`）。Phase 5 保留 Webhook Bot 管道與系統公告。每月彙總通知（決策 8）將在此基礎底座落地後啟用。

### 11. `iam_user.hire_date`

- 透過 Flyway `V2__add_user_hire_date.sql`（PostgreSQL 與 H2）新增為可為 null 的 `DATE` 欄位（日曆日期，非 UTC 時間戳記）。允許為 null 是為了容納系統帳號；後台管理介面則強制員工必須填寫。給假排程工作會跳過未填寫 `hire_date` 的使用者並向 HR 回報。
- 因此 Phase 3 的資料庫遷移檔將從 `V3` 起算。

### 12. 以 PostgreSQL 為測試目標

- 本機安裝的 PostgreSQL 16 成為**唯一可信來源與整合測試目標**（專用 `atria_test` 資料庫）。允許使用 PostgreSQL 專有功能（`JSONB` 運算子、部分唯一索引）。
- H2 遷移目錄在 Phase 3 期間予以保留，以供免安裝展示之用。在 Phase 4 啟動時，重新評估是否改以 Docker Compose 展示棧取代 H2。

### 13. Phase 3 拆分

| 子階段 | 目標 Git Tag | 範圍 |
| :--- | :--- | :--- |
| **3a — 差勤核心** | `v0.4.0-attendance-core` | 行事曆 + TW/JP 模板、`att_punch_log` / `att_daily_record`、打卡規則與跨日分界、Bot 打卡、每日結算排程、月結關帳、`/portal` 打卡卡片與行事曆、`/admin` 行事曆 UI |
| **3b — 請假與加班** | `v0.4.5-leave-overtime` | 假別與最小單位、給假規則與排程、請假計算器、假別餘額帳本、加班倍率／上限規則、補休帳本與到期折發工資、簽核事件契約 + 測試端點 Stub |

## 暫緩項目（明確不在當前階段）

- 依個別員工排定之輪班班表（保留擴充點 `WorkScheduleResolver`）。
- 自動匯入政府公告之調整放假日。
- 基於 GPS 定位的打卡限制（3a 中可選加入 IP 允許清單）。
- 部署拓撲結構（CI Pipeline 已於 Phase 3a.5 完成）。
- 核准加班時數與實際打卡時間取交集（Phase 3b 直接採用核准時數）。
- 日本深夜加給與單月超過 60 小時加班級距之計算（Phase 3b 僅預載種子資料）。

## 影響

- 透過結構性設計保留勞動法規稽核完整性：原始打卡紀錄絕不變更。
- 兩地法規均以資料（Data）而非程式碼（Code）形式呈現；新增營運區域僅需預載規則資料列與行事曆模板。
- Phase 3 與 Phase 4 可藉由事件契約獨立進行開發與測試。
- `ROADMAP.md`、`ARCHITECTURE.md` 以及 ADR-0003／ADR-0005 的資料表命名（`att_punch_record` → `att_punch_log` + `att_daily_record`）已於 Phase 2 完成時同步更新對齊。
