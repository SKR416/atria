# Atria — 勤怠管理・申請承認ワークフロープラットフォーム

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **現在の開発ステータス：** `Phase 4 — 動的フォーム・多段階承認ワークフロー・アプリ内通知＆月次休暇ダイジェスト（完了, v0.5.0-workflow）`  
> **次フェーズ：** `Phase 5 — 公開サイト企業お知らせ・非同期 AI 音声テキスト変換 (STT)・最終リリース整備（v1.0.0）`  
> Phase 0〜4 の実装および検証（バックエンドテスト40件［ArchUnit 8ルール・DB改ざん防止テスト含む］＋フロントエンド ESLint 9・485キー検証静的 UI/i18n ゲート・TypeScript・Vite ビルド）が完了しています。全体計画と引き継ぎ事項は [docs/ROADMAP.md](./docs/ROADMAP.md) および [docs/NEXT_STEPS.md](./docs/NEXT_STEPS.md) を参照してください。

Atria は、勤怠管理と申請・承認（ワークフロー）を一つにまとめたエンタープライズ向けプラットフォームです。従業員の出退勤打刻や休暇・打刻修正・残業の申請から、上長・HR による多段階承認、勤怠記録および休暇残高への自動反映までを一貫して扱います。Spring Boot 3.3 と Vue 3.5 によるモジュラーモノリス構成で、RBAC、部門サブツリーに基づくデータスコープ制御（`@DataScope`）、動的フォーム、3言語（繁体字中国語・英語・日本語）対応を備えています。

---

## なぜ「Atria」なのか？

> *Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

**Atria** は「アトリウム（中央広間・ホール）」の複数形であり、単一のバックエンド中枢を囲む「3つのホール（入り口）」を表しています：

- **`/public`（公開サイト）：** ログイン不要で閲覧できる社外向けのお知らせ一覧（ピン留め・カテゴリ絞り込み・3言語切り替え対応）。
- **`/portal`（従業員ポータル / ESS）：** 全従業員が日常利用する専用ワークスペース。出退勤打刻、会社カレンダー確認、休暇・打刻修正・残業申請、社内通知、および軽量 AI ツールを提供。
- **`/admin`（管理コンソール）：** 部門長・HR・システム管理者向けの管理中枢。部門内の出欠勤ダッシュボード、多段階承認、日台マルチリージョン（`TW` / `JP`）会社カレンダー設定、動的フォーム管理、IAM 権限管理を実行。

---

## 実装済み機能 (Phase 0 – Phase 4)

[ADR-0007](./docs/adr/ADR-0007-project-naming.md) の規定に基づき、現在リポジトリに実装・検証済みの機能を記載します：

- **Phase 0 — 基盤構築 (`v0.1.0-foundation`)：**
  - Spring Boot 3.3.5 (Java 21 LTS) モジュラーモノリス骨格、統一レスポンス `ApiResponse<T>`、`PageResult<T>`、`BusinessException`、`GlobalExceptionHandler`。
  - 3層構造による3言語 i18n（`zh-TW`, `en-US`, `ja-JP`）：Spring `MessageSource` (`messages_*.properties`) と UTC タイムスタンプ保存の徹底（[ADR-0002](./docs/adr/ADR-0002-three-tier-i18n-architecture.md)）。
  - セキュリティ・共通ユーティリティ：`ClientIpResolver`（プロキシ対応クライアント IP 解決）、`HmacSignatureVerifier`（`HMAC-SHA256` ±5分リプレイ攻撃防止検証）、`LocalFileStorageService`（日付パーティション＋UUID 分離保存および `SHA-256` チェックサム）。
- **Phase 1 — 認証・部門ツリー・アクセス制御 (`v0.2.0-iam`)：**
  - Flyway `V1__init_iam_schema.sql`（PostgreSQL 16 & H2 対応）：`iam_department`（`timezone` およびマテリアライズドパス `tree_path`）、`iam_user`、`iam_role`、`iam_permission`、`iam_user_role`、`iam_role_permission`、`iam_audit_log`。
  - ステートレス JWT 認証（インメモリ Access Token ＋ `HttpOnly` Refresh Token Cookie）、`BCrypt` (strength 12) ハッシュ化、5回連続失敗による15分間アカウントロック（`locked_until`）、および **DBA 緊急時平文／`{noop}` パスワード復旧機能**（初回ログイン時に `ErrorCode 1003` パスワード変更ゲートへ強制誘導し BCrypt で再ハッシュ化、[ADR-0004](./docs/adr/ADR-0004-rbac-and-department-data-scope.md)）。
  - 透過的フィールド暗号化：`AesGcmCipher` および `AesGcmStringConverter`（`AES-256-GCM`、96-bit ランダム IV、`{v1}` バージョンプレフィックス）。
  - `@DataScope` AOP による部門サブツリーデータスコープフィルタリング（`ALL`, `DEPT_AND_SUB`, `DEPT_ONLY`, `SELF_ONLY`）および `@AuditLog` AOP による管理操作監査ログ。
- **Phase 2 — フロントエンド3ホール骨格・IAM管理画面・入社日対応 (`v0.3.0-frontend-shell`)：**
  - Vue 3.5 + Vite 6 + TypeScript + PrimeVue 4 (`AtriaBluePreset` Aura テーマ) + Pinia + Vue Router 4 + `vue-i18n` による高情報密度な日系 B2B SPA。
  - Axios インターセプター：JWT・`Accept-Language` 自動付与、401 発生時の `/api/v1/auth/refresh` 自動トークン更新、`1003` 検出時の `/change-password` 強制リダイレクト。
  - 3つのレイアウトシェル（`PublicLayout` `/public`、`PortalLayout` `/portal`、`AdminLayout` `/admin`）および `/login`、`/change-password`。
  - `/admin/iam/*` 管理コンソール画面群：
    - **部門管理 (`DepartmentManageView.vue`)：** `TreeTable` による階層部門ツリー、ルート／子部門作成コンテキスト、部門長指定、`tree_path` カスケード更新、タイムゾーンおよびカレンダー地域（`TW` / `JP`）設定。
    - **ユーザー管理 (`UserManageView.vue`)：** ページング対応 `DataTable`、多言語ロールバッジ、入社日 `hireDate` (`DatePicker`)、管理者パスワードリセット、ロック手動解除、論理削除。
    - **ロール・権限マトリクス (`RoleManageView.vue`)：** ロール CRUD、`DataScopeType` 設定、対話型権限チェックマトリクス。
    - **監査ログ (`AuditLogView.vue`)：** 管理操作ログのページング検索・閲覧。
  - Flyway `V2__add_user_hire_date.sql`（`iam_user.hire_date DATE`）の追加および Entity・DTO・統合テスト・管理 UI への統合（[ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md) Decision 11）。
- **Phase 3a — 勤怠コア・マルチリージョンカレンダー・従業員ポータル (`v0.4.0-attendance-core`)：**
  - Flyway `V3__init_attendance_core_schema.sql`（PostgreSQL 16 & H2 対応）：`iam_department.calendar_code`、`att_work_calendar`（`WORKDAY`, `MAKEUP_WORKDAY`, `REST_DAY`, `STATUTORY_REST_DAY`, `NATIONAL_HOLIDAY`）、`att_punch_rule`、追記専用 `att_punch_log`、日次集計 `att_daily_record`（`@Version` 楽観的ロック付）、月次締め `att_monthly_close`。
  - **日台マルチリージョン勤務カレンダー＆年間テンプレート生成 (`WorkCalendarService`, `StatutoryHolidayGenerator`)：** 日本（`JP` 内閣府国民の祝日・ハッピーマンデー・春分/秋分天文計算・振替休日・国民の休日）および台湾（`TW` 法定休日・旧暦祝日・振替休日）の年間365/366日一括生成、日別の振替出勤日設定、地域別打刻規則管理。
  - **不変打刻台帳＆日次勤怠リアルタイム導出 (`PunchService`)：** Web自己打刻 (`/api/v1/attendance/punch`) および `HMAC-SHA256` リプレイ防止検証付き Bot 代理打刻 (`/api/v1/attendance/bot/punch`)、`att_punch_log` の2層改ざん防止保護（PostgreSQL `BEFORE UPDATE OR DELETE` トリガー `trg_att_punch_log_immutable` ＋ JPA `@Immutable` / `@PreUpdate` / `@PreRemove`）、60秒重複打刻防止 (`ErrorCode 2001`)、午前 `04:00` 論理日付切替、丸め・猶予計算、昼休憩1時間（`12:00`–`13:00`）自動控除。
  - **日次締めバッチ＆月次締めロック (`DailySettlementJob`, `MonthlyCloseService`)：** 日付切替時刻後の `MISSING_PUNCH` / `ABSENT` 自動確定、および `PendingRequestChecker` SPI による未承認 `PENDING` 申請ブロックチェック (`ErrorCode 2010`)、締め済み期間の変更防止 (`ErrorCode 2005`)、ロック解除機能。
  - **アーキテクチャ境界テスト＆CI検証 (`ModularArchitectureTest`, `.github/workflows/ci.yml`)：** ADR-0001 モジュラーモノリス境界を強制する ArchUnit 5ルール、PostgreSQL 16 + H2 デュアルエンジン CI パイプライン（`pgtest` プロファイル）、およびバグ検出特化型 ESLint 9 設定。
  - **従業員セルフサービスポータル＆HR勤怠管理画面：**
    - **従業員ポータル (`PortalHomeView.vue`)：** 部門タイムゾーンリアルタイム時計、ワンクリック打刻＆生打刻履歴、月次 KPI、対話型7列月間カレンダー＆日次詳細モーダル。
    - **勤務カレンダー・打刻規則管理 (`CalendarManageView.vue`)：** `TW`/`JP` 月間カレンダーエディタ、年間カレンダー一括生成モーダル、日別編集、打刻丸め・日付変更時刻設定。
    - **月次締め・日次締め管理 (`MonthlyCloseView.vue`)：** 未承認申請事前チェック、月次締め実行／ロック解除、日次締め手動実行および履歴一覧。
- **Phase 3b — 休暇台帳・法定残業エンジン・承認イベント契約 (`v0.4.5-leave-overtime`)：**
  - Flyway `V4__init_leave_overtime_schema.sql`（PostgreSQL 16 & H2 対応）：`att_leave_type`（`min_unit_minutes`）、`att_leave_grant_rule`（`ANNIVERSARY` / `CALENDAR_YEAR`）、`att_request`（`PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` / `REVOKED`）、`att_comp_leave_grant`（`band_breakdown_json`）、`att_leave_balance`（`source_comp_grant_id`）、`att_leave_balance_tx`（`FREEZE`, `COMMIT`, `RESTORE_PENDING`, `RESTORE_USED`）、`att_overtime_rate_rule`（`TW` `1.34`/`1.67`/`2.67`/`2.00` & `JP` `1.25`/`1.35`）、`att_overtime_cap_rule`（`WARN`/`BLOCK`）、および外部キー `fk_att_punch_log_correction_request`。
  - **勤続年数に応じた年次有給休暇自動付与エンジン (`AnnualLeaveGrantService`, `LeaveTypeService`)：** 入社日基準（`ANNIVERSARY`）にて台湾労基法第38条（勤続10年超の毎年+1日・上限30日含む）および日本労基法第39条（0.5年、1.5年…最大20日、10日以上付与時の年5日［`2400`分］取得義務ターゲット自動設定）を判定し、入社日未設定アカウントをレポート。
  - **休暇時間計算＆FIFO残高台帳 (`LeaveDurationCalculatorImpl`, `LeaveBalanceLedgerService`)：** 申請区間と部門カレンダーの所定労働時間の共通部分から昼休憩（`12:00`–`13:00`）を控除し `min_unit_minutes` 単位で切り上げ。有効期限順（**`valid_until ASC` / FIFO**）にバケットを引き当て、`att_leave_balance_tx` にバケット別の変動履歴を記録することで却下・取下げ・承認取消時に100%正確に元のバケットへ復元。
  - **日台残業割増率・上限エンジン＆代休換算 (`OvertimeRuleAndCalculatorService`, `CompLeaveGrantService`)：** `BigDecimal` + `RoundingMode.CEILING` による整数分切り上げ計算、月次・複数月・年次上限チェック（`WARN` / `BLOCK`）、残業手当（`PAY`）と代休・補休（`COMP_LEAVE`：`HOURS_1_TO_1` または `RATE_MULTIPLIED`）の選択および連動 `att_leave_balance` バケット生成、`band_breakdown_json` を用いた台湾労基法第32-1条の期限切れ未消化補休の割増賃金換算。
  - **単方向承認イベント契約＆日次勤怠再計算 (`AttendanceRequestService`, `AttendanceRecordCalculator`, `DatabasePendingRequestChecker`, `AttRequestGuardImpl`, `AttendanceRequestStubController`)：** `attendance.event` パッケージによる循環依存のないイベント設計、午前／午後半休承認時の遅刻・早退判定基準時刻のシフト、打刻修正承認時の `att_punch_log(CORRECTION)` 追記および日次集計再計算、未決 `PENDING` 申請による月次締めブロック（`2010`）、使用済み代休の残業承認取消ガード（`2025`）、および開発・テスト用承認スタブ（`@Profile({"local", "pgtest"})`）。
  - **従業員ポータル申請UI＆HR休暇・残業ルール管理画面 (`PortalHomeView.vue`, `LeaveOvertimeManageView.vue`)：** FIFO休暇残高カード、90日以内期限切れ代休アラート、日本年5日有給取得義務プログレスバー、休暇・残業・打刻修正申請モーダル、申請一覧（取下げ＆テスト承認スタブ操作付）、および5タブ構成のHR管理コンソール（`/admin/attendance/leave-overtime`）。
- **Phase 4 — 動的フォーム・多段階承認ワークフロー・通知基盤 (`v0.5.0-workflow`)：**
  - **Flyway `V5__init_workflow_and_notification_schema.sql`（PostgreSQL 16 & H2 対応）：** 9つの新規テーブル（`wf_form_template`、`wf_flow_definition`、`wf_flow_node`、`wf_flow_node_condition`、`wf_instance`、`wf_task_log`、`wf_deputy`、`ntf_notification`、`ntf_digest_log`）および専用権限（`wf:instance:self`、`wf:template:read`、`wf:template:write`、`wf:instance:reassign`、`ntf:digest:trigger`）。
  - **動的フォーム JSON Schema と不変バージョン管理 (`wf_form_template`, `wf_instance`)：** `TEXT`、`TEXTAREA`、`NUMBER`、`SELECT`、`DATE` フィールド定義に対応し、テンプレート更新時に自動リビジョンアップ（`v1` -> `v2`）。申請インスタンス (`wf_instance`) ごとに提出時点の `form_version` と `schema_snapshot_json` を不変スナップショットとして永続化。
  - **SpEL 不使用・構造化条件多段階承認エンジン (`StructuredConditionEvaluator`, `ApproverResolverService`, `WorkflowEngineService`)：** SpEL 注入脆弱性を排除した純 Java による `wf_flow_node_condition` 評価（`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`、同一 `logical_group` 内は AND、グループ間は OR）。部門ツリーを遡る承認者解決（`DIRECT_LEADER`, `PARENT_LEADER`, `SPECIFIC_ROLE`）、`HR_ADMIN` -> `SUPER_ADMIN` フォールバック、申請者本人の自動スキップ（`selfApprovalAutoSkip`）を完備。
  - **勤怠双方向ドメインイベントループ (`attendance.event.*`)：** `AttRequestSubmittedEvent` / `AttRequestCancelledEvent` によるワークフロー連動起票・取消、承認終態での `AttRequestApprovedEvent` / `AttRequestRejectedEvent` / `AttRequestRevokedEvent` 発行および `AttRequestGuard` による取消前検証。
  - **差戻し修正 (`差戻し`) とフィールド差分追跡 (`diff_json`)：** 汎用動的フォーム (`target_type = 'DYNAMIC_FORM'`) での差戻し・再申請・差分比較をサポート。勤怠連動申請では FIFO 凍結バケット整合性を保護するため差戻しを禁止（`3009` `WF_RETURN_TO_DRAFT_NOT_ALLOWED_FOR_ATT_REQUEST`）。
  - **代理承認設定 (`wf_deputy`) と HR 強制再割当 (`REASSIGN`)：** 期間指定代理ルール、自己代理防止 (`3012`)、期間重複防止 (`3013`) ガード。監査ログ `wf_task_log` における代理実行者 (`actor_user_id`) と被代理者 (`delegator_user_id`) の2軸記録、管理者による強制再割当。
  - **アプリ内通知センターと月次休暇ダイジェストバッチ (`notification` モジュール)：** ヘッダー常駐型未読通知ベル (`NotificationBell.vue`)、開発環境向けログ記録メール通知 (`EmailNotificationChannel`)、および `ntf_digest_log` の `UNIQUE (user_id, period_year_month)` 制約による月次休暇残高ダイジェストの冪等配信 (`LeaveBalanceDigestJob`)。
  - **フロントエンド UI コンポーネントおよび品質検証ゲート：** `NotificationBell.vue`（通知ドロワー）、`/portal` 従業員承認ワークスペース (`PortalWorkflowPanel.vue`)、`/admin/workflow/manage` ワークフロールール・フォーム管理画面 (`WorkflowManageView.vue`)、および3言語 485キーの完全一致・PrimeVue `<Button>` テキスト重複防止・View `data-testid` を静的検証する `frontend/scripts/check-ui-i18n.mjs`。

---

## 技術スタック

| レイヤー | 採用技術 | 用途 |
| :--- | :--- | :--- |
| **バックエンド** | Java 21 LTS, Spring Boot 3.3.5 | モジュラーモノリス基盤 (`io.github.skr416.atria`) |
| **セキュリティ・暗号化** | Spring Security 6, JWT (`jjwt`), BCrypt, `AES-256-GCM` | ステートレス認証、DBA緊急パスワード復旧 (`1003`)、RBAC、`@DataScope` 部門スコープ制御、フィールド暗号化 (`AesGcmStringConverter`) |
| **データベース** | PostgreSQL 16 (ローカルデモ用 H2 対応), Flyway (`V1`–`V5`) | リレーショナル管理＋多言語・動的フォーム用 `JSONB`、論理削除用部分ユニークインデックス |
| **フロントエンド** | Vue 3.5, Vite 6, TypeScript, PrimeVue 4, Pinia, Vue Router 4, Lucide Icons, ApexCharts | 高情報密度な日系B2B UI、3つのレイアウト（`/public`, `/portal`, `/admin`）、サプライチェーン安全性（SCRM）に準拠した MIT コンポーネント群（[ADR-0001](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md)） |
| **多言語対応 (i18n)** | Spring `MessageSource`, `vue-i18n` | 繁体字中国語 (`zh-TW`)・英語 (`en-US`)・日本語 (`ja-JP`) の3言語対応 |
| **API 仕様** | Springdoc OpenAPI 3 | Swagger ドキュメント生成およびフロントエンド TypeScript 型定義連携 |

---

## リポジトリ構造 (Repository Structure)

```text
atria/
├── .github/workflows/ci.yml      # GitHub Actions CI (H2 + PostgreSQL 16 Flyway/Trigger + ESLint & Vite)
├── docs/
│   ├── ROADMAP.md                # マイルストーン計画および Git タグスケジュール (Phase 0〜Phase 5)
│   ├── ARCHITECTURE.md           # モジュラーモノリス構造およびデータベーススキーマ概要
│   ├── NEXT_STEPS.md             # セッション間引き継ぎおよびタスクチェックリスト
│   └── adr/                      # アーキテクチャ意思決定記録 (ADR-0001〜ADR-0008 英語版原本)
│       └── zh-TW/                # 繁体字中国語版 ADR 翻訳およびインデックス
├── backend/                      # Maven artifactId: atria-backend (Spring Boot 3.3.5, Java 21)
│   └── src/
│       ├── main/java/io/github/skr416/atria/
│       │   ├── common/           # ApiResponse, ErrorCode, AesGcmCipher, HmacSignatureVerifier 等
│       │   ├── config/           # I18nConfig, OpenApiConfig, CorsConfig
│       │   ├── iam/              # 認証、ユーザー、部門ツリー、ロール・権限、@DataScope、@AuditLog
│       │   ├── attendance/       # 日台勤務カレンダー、不変打刻ログ・日次集計、FIFO休暇台帳、残業・代休、承認イベント＆スタブ
│       │   ├── workflow/         # 動的フォーム、構造化条件多段階承認エンジン、申請インスタンス状態機、代理承認
│       │   └── notification/     # アプリ内通知、メールログ通知、月次休暇残高ダイジェストバッチ
│       ├── main/resources/
│       │   ├── db/migration/     # Flyway V1–V5 SQL マイグレーション (postgresql/ & h2/)
│       │   └── i18n/             # 3言語メッセージバンドル (zh_TW, en_US, ja_JP)
│       └── test/                 # IAM・勤怠・ワークフロー/通知・改ざん防止・ArchUnit テスト (40件)
└── frontend/                     # npm package: atria-frontend (Vue 3.5, Vite 6, TypeScript, PrimeVue 4)
    ├── scripts/                  # check-ui-i18n.mjs (485キー3言語完全一致・Buttonラベル重複防止・data-testid 静的検証)
    └── src/
        ├── api/                  # Axios クライアント (JWT 自動更新 & 1003 ガード) + IAM・Attendance・Workflow API
        ├── components/
        │   ├── common/           # 3言語スイッチャー LanguageSwitcher・アプリ内通知ベル NotificationBell
        │   └── portal/           # PortalWorkflowPanel 従業員申請履歴・承認受信用受信箱パネル
        ├── i18n/                 # vue-i18n ロケール辞書 (zh-TW, en-US, ja-JP)
        ├── layouts/              # PublicLayout, PortalLayout, AdminLayout
        ├── router/               # 3ホールルートガードおよびパスワード変更強制リダイレクト
        ├── stores/               # Pinia auth ストア (インメモリ Access Token & プロフィール)
        └── views/                # Public, Portal, Auth, Admin (IAM + 勤怠・休暇残業ルール・ワークフロー管理) 画面
```

---

## クイックスタート (Local Development)

### 1. バックエンド起動 (`atria-backend`)
デフォルトで `local` プロファイル（インメモリ H2 データベース）にてポート `8080` で起動し、デモアカウント（`admin`, `hr_lead`, `eng_mgr`, `emp_alice`, `emp_locked`／パスワードはいずれも `Password123!`）が自動投入されます：
```bash
cd backend
./mvnw spring-boot:run
```

### 2. フロントエンド起動 (`atria-frontend`)
ポート `5173` で起動し、`/api` リクエストを `http://localhost:8080` へプロキシします：
```bash
cd frontend
npm install
npm run dev
```

---

## 開発ロードマップ

| フェーズ | ターゲットタグ | 実装範囲 | 状態 |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | リポジトリ初期化、ADR 文書、Spring Boot 3 骨格、`ApiResponse<T>`、3言語エラーコード | ✅ 完了 |
| **Phase 1** | `v0.2.0-iam` | IAM スキーマ (`iam_*`)、JWT 認証、RBAC、部門ツリー、`@DataScope`、`@AuditLog`、`AES-256-GCM` | ✅ 完了 |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 フロントエンド骨格 (`atria-frontend`)、3つのレイアウト、IAM 管理画面、`hire_date` (`V2`) | ✅ 完了 |
| **Phase 3a** | `v0.4.0-attendance-core` | マルチリージョンカレンダー (`TW`/`JP` テンプレート)、不変打刻ログ (`att_punch_log`) + 日次集計 (`att_daily_record`)、Bot 打刻、日次締め・月次締め、`/portal`・`/admin` カレンダー UI | ✅ 完了 |
| **Phase 3b** | `v0.4.5-leave-overtime` | 休暇種別・勤続年数付与ルール (`hire_date`)、休暇時間計算・残高台帳、日台残業割増率・上限エンジン、代休換算、承認イベント契約 + テストスタブ | ✅ 完了 |
| **Phase 4** | `v0.5.0-workflow` | 動的フォーム (`wf_form_template`)、構造化条件による多段階承認ワークフロー (`wf_*`)、承認者フォールバック、代理承認 (`wf_deputy`)、通知基盤 (`ntf_*`)・月次残高ダイジェスト | ✅ 完了 |
| **Phase 5** | `v1.0.0` | 公開サイト企業お知らせ (`ann_*`)、上長勤怠ダッシュボード・給与計算用加重時間出力、非同期 AI 音声文字起こし (`ai_stt_job`)・最終リリース整備 | ⏳ 次フェーズ |

---

## アーキテクチャ意思決定記録 (ADRs)

設計上の判断とトレードオフはすべて英語で [`docs/adr/`](./docs/adr/) に記録されており、繁体字中国語訳は [`docs/adr/zh-TW/README.md`](./docs/adr/zh-TW/README.md) にまとめられています：

| 番号 | タイトル | ステータス |
| :--- | :--- | :--- |
| [ADR-0001](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md) ([繁中](./docs/adr/zh-TW/ADR-0001-modular-monolith-and-tech-stack.md)) | モジュラーモノリス構成とコア技術スタック選定 | 採択済 |
| [ADR-0002](./docs/adr/ADR-0002-three-tier-i18n-architecture.md) ([繁中](./docs/adr/zh-TW/ADR-0002-three-tier-i18n-architecture.md)) | 3層構造による3言語 i18n アーキテクチャ (`zh-TW`, `en-US`, `ja-JP`) | 採択済 |
| [ADR-0003](./docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md) ([繁中](./docs/adr/zh-TW/ADR-0003-three-halls-and-calendar-driven-attendance.md)) | 3つの入り口（Three Halls）とカレンダー駆動型勤怠モデル | 採択済（一部 [ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md) により改訂） |
| [ADR-0004](./docs/adr/ADR-0004-rbac-and-department-data-scope.md) ([繁中](./docs/adr/zh-TW/ADR-0004-rbac-and-department-data-scope.md)) | RBAC と部門ツリーデータスコープ・DBA緊急復旧・3層暗号化戦略 | 採択済 |
| [ADR-0005](./docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md) ([繁中](./docs/adr/zh-TW/ADR-0005-dynamic-form-versioning-and-approval-workflow.md)) | 動的フォームのバージョニングと多段階承認ワークフロー | 採択済（一部 [ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md) により改訂） |
| [ADR-0006](./docs/adr/ADR-0006-ai-module-scope-and-async-stt.md) ([繁中](./docs/adr/zh-TW/ADR-0006-ai-module-scope-and-async-stt.md)) | 実用的な AI モジュール設計（チャット・テキスト変換・非同期音声文字起こし） | 採択済 |
| [ADR-0007](./docs/adr/ADR-0007-project-naming.md) ([繁中](./docs/adr/zh-TW/ADR-0007-project-naming.md)) | プロジェクト命名（"Atria"）およびテーブル接頭辞の統一規則 (`iam_`, `att_`, `wf_`, `ntf_`, `ann_`, `ai_`) | 採択済 |
| [ADR-0008](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md) ([繁中](./docs/adr/zh-TW/ADR-0008-attendance-leave-overtime-refinements.md)) | 勤怠・休暇・残業ルールの詳細化および Phase 3/4 実装順序（日台労働法規対応） | 採択済 |

---

## ライセンス

[MIT License](./LICENSE) の下で公開されています。
