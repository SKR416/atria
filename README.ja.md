# Atria — 勤怠管理・申請承認ワークフロープラットフォーム

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **現在の開発ステータス：** `Phase 3a — 勤怠コア・マルチリージョンカレンダー・従業員ポータル（完了, v0.4.0-attendance-core）`  
> **次フェーズ：** `Phase 3b — 休暇台帳・法定残業エンジン・承認イベント契約（v0.4.5-leave-overtime）`  
> Phase 0〜3a の実装および検証（バックエンドテスト24件［ArchUnit・DB改ざん防止テスト含む］＋フロントエンド ESLint / TypeScript / Vite ビルド）が完了しています。全体計画と引き継ぎ事項は [docs/ROADMAP.md](./docs/ROADMAP.md) および [docs/NEXT_STEPS.md](./docs/NEXT_STEPS.md) を参照してください。

Atria は、勤怠管理と申請・承認（ワークフロー）を一つにまとめたエンタープライズ向けプラットフォームです。従業員の出退勤打刻や休暇・打刻修正・残業の申請から、上長・HR による多段階承認、勤怠記録および休暇残高への自動反映までを一貫して扱います。Spring Boot 3.3 と Vue 3.5 によるモジュラーモノリス構成で、RBAC、部門サブツリーに基づくデータスコープ制御（`@DataScope`）、動的フォーム、3言語（繁体字中国語・英語・日本語）対応を備えています。

---

## なぜ「Atria」なのか？

> *Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

**Atria** は「アトリウム（中央広間・ホール）」の複数形であり、単一のバックエンド中枢を囲む「3つのホール（入り口）」を表しています：

- **`/public`（公開サイト）：** ログイン不要で閲覧できる社外向けのお知らせ一覧（ピン留め・カテゴリ絞り込み・3言語切り替え対応）。
- **`/portal`（従業員ポータル / ESS）：** 全従業員が日常利用する専用ワークスペース。出退勤打刻、会社カレンダー確認、休暇・打刻修正・残業申請、社内通知、および軽量 AI ツールを提供。
- **`/admin`（管理コンソール）：** 部門長・HR・システム管理者向けの管理中枢。部門内の出欠勤ダッシュボード、多段階承認、日台マルチリージョン（`TW` / `JP`）会社カレンダー設定、動的フォーム管理、IAM 権限管理を実行。

---

## 実装済み機能 (Phase 0 – Phase 3a)

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
  - **日次締めバッチ＆月次締めロック (`DailySettlementJob`, `MonthlyCloseService`)：** 日付切替時刻後の `MISSING_PUNCH` / `ABSENT` 自動確定、および `PendingRequestBlockingChecker` SPI による未承認 `PENDING` 申請ブロックチェック (`ErrorCode 2010`)、締め済み期間の変更防止 (`ErrorCode 2005`)、ロック解除機能。
  - **アーキテクチャ境界テスト＆CI検証 (`ModularArchitectureTest`, `.github/workflows/ci.yml`)：** ADR-0001 モジュラーモノリス境界を強制する ArchUnit 5ルール、PostgreSQL 16 + H2 デュアルエンジン CI パイプライン（`pgtest` プロファイル）、およびバグ検出特化型 ESLint 9 設定。
  - **従業員セルフサービスポータル＆HR勤怠管理画面：**
    - **従業員ポータル (`PortalHomeView.vue`)：** 部門タイムゾーンリアルタイム時計、ワンクリック打刻＆生打刻履歴、月次 KPI、対話型7列月間カレンダー＆日次詳細モーダル。
    - **勤務カレンダー・打刻規則管理 (`CalendarManageView.vue`)：** `TW`/`JP` 月間カレンダーエディタ、年間カレンダー一括生成モーダル、日別編集、打刻丸め・日付変更時刻設定。
    - **月次締め・日次締め管理 (`MonthlyCloseView.vue`)：** 未承認申請事前チェック、月次締め実行／ロック解除、日次締め手動実行および履歴一覧。

---

## 技術スタック

| レイヤー | 採用技術 | 用途 |
| :--- | :--- | :--- |
| **バックエンド** | Java 21 LTS, Spring Boot 3.3.5 | モジュラーモノリス基盤 (`io.github.skr416.atria`) |
| **セキュリティ・暗号化** | Spring Security 6, JWT (`jjwt`), BCrypt, `AES-256-GCM` | ステートレス認証、DBA緊急パスワード復旧 (`1003`)、RBAC、`@DataScope` 部門スコープ制御、フィールド暗号化 (`AesGcmStringConverter`) |
| **データベース** | PostgreSQL 16 (ローカルデモ用 H2 対応), Flyway (`V1`–`V3`) | リレーショナル管理＋多言語・動的フォーム用 `JSONB`、論理削除用部分ユニークインデックス |
| **フロントエンド** | Vue 3.5, Vite 6, TypeScript, PrimeVue 4, Pinia, Vue Router 4, Lucide Icons, ApexCharts | 高情報密度な日系B2B UI、3つのレイアウト（`/public`, `/portal`, `/admin`）、サプライチェーン安全性（SCRM）に準拠した MIT コンポーネント群（[ADR-0001](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md)） |
| **多言語対応 (i18n)** | Spring `MessageSource`, `vue-i18n` | 繁体字中国語 (`zh-TW`)・英語 (`en-US`)・日本語 (`ja-JP`) の3言語対応 |
| **API 仕様** | Springdoc OpenAPI 3 | Swagger ドキュメント生成およびフロントエンド TypeScript 型定義連携 |

---

## リポジトリ構造 (Repository Structure)

```text
atria/
├── .github/workflows/ci.yml      # GitHub Actions CI (H2 + PostgreSQL 16 Flyway/Trigger + ESLint & Vite)
├── docs/
│   ├── ROADMAP.md                # マイルストーン計画および Git タグスケジュール (Phase 0〜Phase 6)
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
│       │   └── attendance/       # 日台勤務カレンダー、不変打刻ログ・日次集計、日次締め・月次締め
│       ├── main/resources/
│       │   ├── db/migration/     # Flyway V1–V3 SQL マイグレーション (postgresql/ & h2/)
│       │   └── i18n/             # 3言語メッセージバンドル (zh_TW, en_US, ja_JP)
│       └── test/                 # IAM・Attendance・改ざん防止・ArchUnit テスト (24件)
└── frontend/                     # npm package: atria-frontend (Vue 3.5, Vite 6, TypeScript, PrimeVue 4)
    └── src/
        ├── api/                  # Axios クライアント (JWT 自動更新 & 1003 ガード) + IAM / Attendance API
        ├── components/common/    # 3言語スイッチャー LanguageSwitcher
        ├── i18n/                 # vue-i18n ロケール辞書 (zh-TW, en-US, ja-JP)
        ├── layouts/              # PublicLayout, PortalLayout, AdminLayout
        ├── router/               # 3ホールルートガードおよびパスワード変更強制リダイレクト
        ├── stores/               # Pinia auth ストア (インメモリ Access Token & プロフィール)
        └── views/                # Public, Portal (打刻・カレンダー), Auth, Admin (IAM + 勤怠管理) 画面
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
| **Phase 3b** | `v0.4.5-leave-overtime` | 休暇種別・勤続年数付与ルール (`hire_date`)、休暇時間計算・残高台帳、日台残業割増率・上限エンジン、代休換算、承認イベント契約 + テストスタブ | ⏳ 次フェーズ |
| **Phase 4** | `v0.5.0-workflow` | 動的フォーム (`wf_form_template`)、構造化条件による多段階承認ワークフロー (`wf_*`)、承認者フォールバック、代理承認 (`wf_deputy`)、通知基盤 (`ntf_*`)・月次残高ダイジェスト | ⏳ 計画中 |
| **Phase 5** | `v0.6.0-dashboard-notice` | 上長向け勤怠ダッシュボード、打刻異常フィルタ、給与計算用加重時間 Excel/CSV 出力、Webhook Bot 通知、社外 (`/public`)・社内 (`/portal`) お知らせ機能 (`ann_*`) | ⏳ 計画中 |
| **Phase 6** | `v0.7.0-ai-suite` | 軽量 AI チャット (SSE)、職場テキスト変換、非同期音声ファイルの文字起こし (`ai_stt_job` 独立 `whisper.cpp` サービス) | ⏳ 計画中 |

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
