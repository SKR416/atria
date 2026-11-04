# ADR-0004：RBAC 結合部門樹資料範圍

> 本文件為 [ADR-0004 英文版](../ADR-0004-rbac-and-department-data-scope.md) 之中文譯本；如有歧義，以英文版為準。

- **狀態：** 已採納
- **日期：** 2026-10-28

## 背景

純粹的角色權限控管（RBAC：`User -> Role -> Permission`）決定了使用者可以存取「哪些功能」（例如檢視差勤儀表板或核准申請），但無法表達管理者獲准查看或核准「誰的資料」。在企業環境中：
- 部門主管應僅能查看自己所屬部門（以及可選的子部門）內員工的差勤記錄與核准其申請。
- 人資與系統管理員則需要全公司範圍的能見度。
- 當主管請假時，其簽核權限必須能暫時委派給代理簽核人，而無需永久提升其角色權限。

## 決策

1. **階層式部門樹（`iam_department`）：**
   - 每個部門定義 `parent_id`、`tree_path`（物化路徑，如 `/1/4/12/`）與 `leader_id`（部門主管）。
2. **角色資料範圍（`DataScopeType`）：**
   - `iam_role` 中的每個角色均被指派一個 `data_scope` 層級：
     - `ALL`：全公司範圍的完整資料存取權限（例如人資、超級管理員）。
     - `DEPT_AND_SUB`：可存取使用者所屬部門及其所有下層子部門。
     - `DEPT_ONLY`：存取權限僅限於使用者所在的直屬部門。
     - `SELF_ONLY`：存取權限僅限於使用者本人的記錄。
   - 自訂的 `@DataScope` AOP 切面會動態將部門/使用者述詞附加至差勤與簽核流程查詢中。
3. **帳號安全、緊急密碼復原與軟刪除：**
   - **密碼雜湊與 DBA 緊急復原：** `iam_user.password_hash` 中的密碼採用 Spring Security 的 `DelegatingPasswordEncoder`，預設為 `{bcrypt}`（強度 12）。為了在使用者或管理員遭鎖定時支援 DBA/維運團隊的緊急排障，若資料庫中的密碼缺少 `{bcrypt}` 前綴（例如 `{noop}Temp123456` 或直接透過 SQL 輸入的原始明文），認證服務允許單次登入，並自動標記 `must_change_password = TRUE`，將該連線階段嚴格限制於密碼變更端點，並在變更後立即以 `{bcrypt}` 重新雜湊新密碼。
   - **暴力破解防護鎖定：** 追蹤連續登入失敗次數（`failed_login_attempts`），連續失敗 5 次即鎖定帳號 15 分鐘（`locked_until`）。
   - **非破壞性軟刪除：** 使用 PostgreSQL 部分唯一索引（`CREATE UNIQUE INDEX uq_iam_user_username ON iam_user(username) WHERE is_deleted = FALSE;`），以便將離職/已停用的員工封存，既不影響日後重新聘用，也不會與唯一的使用者名稱/Email 條件限制產生衝突。
4. **欄位層級加密（`AES-256-GCM`）與附件儲存策略：**
   - **敏感資料庫欄位：** 高度敏感欄位（例如出站 Webhook 機密金鑰、外部 API Token 與機敏個人識別資料）透過 JPA `AttributeConverter`（`AesGcmStringConverter`）進行透明加密，採用 `AES-256-GCM` 演算法，搭配隨機 96 位元 IV 與具備版本號的前綴（`{v1}<Base64(IV + Ciphertext + AuthTag)>`）。金鑰加密金鑰（KEK）透過環境變數（`ATRIA_CRYPTO_KEK`）注入，並提供適用於本機開發的安全備用值——免除管理外部 `.key` 檔案的負擔，同時支援未來的金鑰輪換。
   - **上傳文件與音訊檔案：** 檔案二進位資料透過 `FileStorageService` 儲存於資料庫外部，採用按日期分區的隨機 UUID 路徑（`{category}/{yyyy-MM-dd}/{uuid}.{ext}`），資料庫中則記錄中繼資料與 `SHA-256` 完整性總和檢查碼。檔案二進位本體刻意不進行應用程式層的串流加密，以保留 HTTP byte-range 串流傳輸能力（供 PDF/音訊預覽與 `whisper.cpp` STT 處理使用）及主機防毒軟體的檢測能力；存取權限嚴格由 JWT 認證與 `@DataScope` 授權把關。
5. **具時效性的代理簽核（`wf_deputy`）：**
   - 主管可在簽核流程模組中設定代理人（`principal_user_id`、`deputy_user_id`、`start_at`、`end_at`、`active`），使簽核流程路由在主管缺勤期間永不卡關。

## 影響

- 相同的 `/admin` 差勤儀表板與簽核端點可同時服務部門主管與人資，無需重複撰寫 Controller——每位呼叫者會依其 `data_scope` 自動只看見獲准查看的資料列。
- 物化路徑 `tree_path` 支援快速的子樹過濾（`LIKE '/1/4/%'`），無需在每次讀取查詢時執行遞迴 SQL。
- 維運人員在緊急狀況下可直接於資料庫中救援被鎖定的帳號，登入後不會在待存儲狀態下殘留明文密碼，且機敏機密資料透過 `AES-256-GCM` 獲得保護，可抵禦資料庫 dump 外洩風險。
