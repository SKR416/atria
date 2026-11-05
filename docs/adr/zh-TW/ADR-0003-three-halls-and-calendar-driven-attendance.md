# ADR-0003：三大廳路由與行事曆驅動差勤模型

> 本文件為 [ADR-0003 英文版](../ADR-0003-three-halls-and-calendar-driven-attendance.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納（部分由 [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md) 補充修訂）
- **日期：** 2026-10-28

## 背景

早期規劃曾考慮採用完整的排班引擎（輪班制、大夜班、個別員工輪值表）以及為所有使用者提供單一單體管理側邊欄。經檢討後：

1. **排班複雜度：** 大多數標準辦公室與企業環境運作於由人資定義的統一企業行事曆，而非個別員工輪班表。預先建置複雜的排班引擎會延誤核心「差勤至簽核」閉環的交付進度。
2. **角色 UX 分離：** 一般員工只需要一個乾淨的每日入口以進行上班/下班打卡、檢視企業工作行事曆、提交請假或補卡申請、閱讀內部公告以及使用 AI 工具。對每位員工暴露複雜的管理控制台會大幅降低易用性。

## 決策

1. **行事曆驅動差勤模型與多時區換日時點（`att_work_calendar`）：**
   - 不採用個別排班，改由人資定義多區域工作行事曆（依 [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md) 增設 `calendar_code` 如 `TW`、`JP`，及五種法定日類型 `WORKDAY`、`MAKEUP_WORKDAY`、`REST_DAY`、`STATUTORY_REST_DAY`、`NATIONAL_HOLIDAY`）以及標準上下班時間（例如 `09:00`–`18:00`）。
   - 各部門與分公司在 `iam_department.timezone` 中定義其在地時區（例如 `Asia/Taipei`、`Asia/Tokyo`）並綁定 `iam_department.calendar_code`。工作日與換日時點依員工所屬部門時區進行判定，而資料庫中所有時間戳記均維持儲存為 UTC。
   - 由 `WorkScheduleResolver` 介面負責解析任何使用者在特定日期的預期工時。最初實作（`CalendarBasedScheduleResolver`）讀取 `att_work_calendar`，若日後需要支援個別員工排班表，仍保留充足的擴充點。
2. **雙表分離打卡架構（`att_punch_log` + `att_daily_record`）與閉環差勤（由 [ADR-0008](./ADR-0008-attendance-leave-overtime-refinements.md) 補充修訂）：**
   - 打卡資料拆分為兩張資料表，以兼顧勞動法規稽核之不可竄改性與每日出勤狀態衍生：
     - `att_punch_log`（僅可新增之不可變日誌）：記錄每次原始打卡事件（伺服器 UTC 時間戳記 `raw_punch_time` 精確至秒、用戶端 IP `client_ip`、來源 `source` `WEB` | `BOT` | `CORRECTION`、`bot_client_id`），絕不更新或刪除。
     - `att_daily_record`（衍生每日出勤紀錄）：每位使用者每個邏輯工作日（`work_date`）一筆，儲存依 `att_punch_rule` 計算之認列上下班時間（`effective_clock_in` / `effective_clock_out`）、`worked_minutes`、出勤狀態 `status` 與 `@Version` 樂觀鎖。
   - 提供經授權的第三方 Bot 打卡端點，允許通過驗證的代理程式（例如公司 Slack/Teams/LINE Bot）提交打卡。
   - 休息時間計算遵循標準固定扣除規則：與 12:00–13:00 重疊的班表自動扣除 1 小時無薪休息時間。
   - 加班追蹤嚴格以簽核為準：下班打卡時間超過排定結束時間不會自動產生加班時數；認列加班必須具備已核准的 `OVERTIME` 簽核流程申請，在維持法規合規性的同時兼顧管理協商空間（例如將加班轉換為補休）。
   - 每日記錄依據 `att_work_calendar` 判定為明確的狀態：`NORMAL`、`LATE`、`EARLY_LEAVE`、`MISSING_PUNCH`、`ON_LEAVE` 或 `ABSENT`。
   - **跨午夜換日時點（`day_cutoff_hour`）：** `att_punch_rule` 定義邏輯換日時點（預設為凌晨 `04:00`），使跨越午夜的深夜加班下班打卡能正確歸屬至前一個工作日。
   - **Bot 端點防重送 HMAC 安全機制：** 第三方 Bot 打卡請求透過 `HMAC-SHA256` 簽章驗證搭配 ±5 分鐘時間戳記視窗（`HmacSignatureVerifier`）與真實用戶端 IP 解析（`ClientIpResolver`）進行認證。
   - **假別餘額帳本（`att_leave_balance`）：** 追蹤 `total_hours`、`used_hours`、`pending_hours`（送出申請時凍結，最終核准時認列扣除，遭駁回/退回/抽單時釋放）以及 `expires_at`，並提供人資介面以發放經協商之補休。
   - **月結關帳鎖定（`att_monthly_close`）與逾期補登阻擋：** 支援人資手動月結關帳與排程自動關帳（例如在次月指定日期鎖定上個月），只要該月份仍有 `PENDING` 簽核中申請即阻擋關帳；關帳後禁止回溯修改工時或請假記錄。超出設定期限（`max_retroactive_days`）的補登申請將在送出時直接予以阻擋。
   - 請假申請、補卡申請與加班申請共用 `att_request` 資料表，透過 `request_type`（`LEAVE` | `PUNCH_CORRECTION` | `OVERTIME`）區分。經核准事件（`AttRequestApprovedEvent`）觸發後，補卡申請會新增一筆 `CORRECTION` 來源之 `att_punch_log` 並於同一交易內重新計算 `att_daily_record`，確保原始打卡紀錄不被竄改。
3. **三大廳前端架構：**
   - `/public`：無需認證的外部公開網站。
   - `/portal`：簡潔、以行事曆為核心的員工自助入口（ESS），用於打卡、請假/補卡申請、內部公告與 AI 工具。
   - `/admin`：僅限部門主管、人資與管理員存取的管理後台，首頁具備即時團隊差勤儀表板。

## 影響

- 第三階段（Phase 3）的實作範圍大幅精簡，能更快速交付端到端的差勤與簽核流程。
- Vue Router 中的路由守衛搭配 Spring Security 中的角色與資料範圍檢查，將一般員工的自助服務（`/portal`）與管理層的操作（`/admin`）乾淨俐落地分離。
