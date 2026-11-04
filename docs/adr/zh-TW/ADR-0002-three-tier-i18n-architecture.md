# ADR-0002：三層式三語多語系（i18n）架構（`zh-TW`、`en-US`、`ja-JP`）

> 本文件為 [ADR-0002 英文版](../ADR-0002-three-tier-i18n-architecture.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納
- **日期：** 2026-10-28

## 背景

Atria 從第一天起即支援三種預設語系：
- **繁體中文（`zh-TW`）**
- **英文（`en-US`）**
- **日文（`ja-JP`）**

若將向使用者展示的字串寫死在 Java 例外、資料庫欄位或 Vue 範本中，會導致在地化不完整且容易出錯。我們需要在 API 層、持久化層、前端 UI 以及時間戳記處理之間，建立一套統一的多語系（i18n）策略。

## 評估方案

### 方案 A：資料庫中使用獨立的翻譯關聯表（`*_translation`）
- **優點：** 嚴格正規化的關聯式 Schema。
- **缺點：** 對每個查表（角色、部門、假別、公告）都需要額外的 `LEFT JOIN` 查詢，使 JPA 對應與分頁查詢變得複雜。

### 方案 B：領域實體採用 PostgreSQL `JSONB` 的三層式多語系（i18n）
- **優點：**
  1. **後端 API 層：** 業務例外攜帶型別化的 `ErrorCode` 列舉（絕不使用硬編碼字串）。`GlobalExceptionHandler` 依據請求的 `Accept-Language` 標頭，透過 Spring 的 `MessageSource`（`messages_zh_TW.properties`、`messages_en_US.properties`、`messages_ja_JP.properties`）解析在地化訊息。
  2. **資料庫層：** 動態的使用者自訂標籤（部門名稱、角色名稱、假別名稱、假日標籤、公告標題）儲存於單一 `JSONB` 欄位中：
     ```json
     {"zh-TW": "特休假", "en-US": "Annual Leave", "ja-JP": "年次有給休暇"}
     ```
  3. **前端層：** `vue-i18n` 管理靜態 UI 標籤，並將當前生效的語系同步至 PrimeVue 的語系設定（`primevue.config.locale`）與 Axios 的 `Accept-Language` 標頭。

## 決策

我們採納**方案 B（三層式多語系（i18n）架構）**，並配合嚴格的 UTC 時間戳記儲存原則：
- 資料庫中的所有時間戳記一律使用 PostgreSQL `TIMESTAMPTZ` 並以 **UTC** 儲存，序列化為 ISO-8601 字串，並在用戶端的時區進行格式化呈現。

## 影響

- 新增業務錯誤時，必須在 `ErrorCode` 中新增列舉常數，並在全部三個 `.properties` resource bundle 中新增對應的 key。
- 需要查詢或索引 PostgreSQL `JSONB` 內的特定語言值時，可依需求使用表達式索引（例如 `(name_i18n ->> 'zh-TW')`）。
