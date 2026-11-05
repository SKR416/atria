# Atria — 勤怠管理・申請承認ワークフロープラットフォーム

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **現在の開発ステータス：** `Phase 2 — フロントエンド3ホール骨格・IAM管理コンソール（完了）` ｜ **次フェーズ：** `Phase 3a — 勤怠コア・マルチリージョンカレンダー`  
> Phase 0〜2（`atria-backend` IAM/RBAC/データスコープ/AES-256-GCM 暗号化 ＋ `atria-frontend` Vue 3 3つのレイアウトおよび IAM 管理画面）が完了しています。全フェーズの実装計画は [docs/ROADMAP.md](./docs/ROADMAP.md) を参照してください。

Atria は、勤怠管理と申請・承認(ワークフロー)を一つにまとめたエンタープライズ向けプラットフォームです。従業員の打刻や休暇・打刻修正の申請から、上長・HR による多段階承認、勤怠記録への自動反映までを一貫して扱います。Spring Boot 3 と Vue 3 によるモジュラーモノリス構成で、RBAC、動的フォーム、3言語(繁体字中国語・英語・日本語)対応を備えています。

---

## なぜ「Atria」なのか？

> *Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

**Atria** は「アトリウム（中央広間・ホール）」の複数形であり、単一のバックエンド中枢を囲む「3つのホール（入り口）」を表しています：

- **`/public`（公開サイト）：** ログイン不要で閲覧できる社外向けのお知らせ一覧（ピン留め・カテゴリ絞り込み対応）。
- **`/portal`（従業員ポータル / ESS）：** 全従業員が日常利用する専用ポータル。出退勤打刻、会社カレンダー確認、休暇申請・打刻修正申請、社内通知、および軽量 AI ツールを提供。
- **`/admin`（管理コンソール）：** 上長・HR・システム管理者向けの管理システム。トップ画面のダッシュボードで部門内の出欠勤状況や打刻履歴をリアルタイムに把握し、多段階承認、会社カレンダー設定、動的フォーム管理、RBAC 権限管理を実行。

---

## 技術スタック

| レイヤー | 採用技術 | 用途 |
| :--- | :--- | :--- |
| **バックエンド** | Java 21 LTS, Spring Boot 3 | モジュラーモノリス基盤 (`io.github.skr416.atria`) |
| **セキュリティ** | Spring Security 6, JWT (jjwt) | ステートレス認証、RBAC、部門ツリーに基づくデータスコープ制御 |
| **データベース** | PostgreSQL 16, Flyway | リレーショナル管理＋動的フォーム・多言語フィールド用の `JSONB` |
| **フロントエンド** | Vue 3, Vite, TypeScript, PrimeVue, ApexCharts | 高情報密度な日系B2B UI、3つのレイアウト（`/public`, `/portal`, `/admin`）を持つ SPA |
| **多言語対応 (i18n)** | Spring `MessageSource`, `vue-i18n` | 繁体字中国語 (`zh-TW`)・英語 (`en-US`)・日本語 (`ja-JP`) の3言語対応 |
| **API 仕様** | Springdoc OpenAPI 3 | Swagger ドキュメント生成およびフロントエンド型定義連携 |

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
| **Phase 1** | `v0.2.0-iam` | IAM スキーマ (`iam_*`)、JWT 認証、RBAC、部門ツリー、`@DataScope`、`@AuditLog`、AES-256-GCM | ✅ 完了 |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 フロントエンド骨格 (`atria-frontend`)、3つのレイアウト、IAM 管理画面、`hire_date` (`V2`) | ✅ 完了 |
| **Phase 3a** | `v0.4.0-attendance-core` | マルチリージョンカレンダー (`TW`/`JP`)、不変打刻ログ (`att_punch_log`) + 日次集計 (`att_daily_record`)、Bot 打刻、月次締め、`/portal`・`/admin` カレンダー UI | ⏳ 次フェーズ |
| **Phase 3b** | `v0.4.5-leave-overtime` | 休暇種別・勤続年数付与ルール (`hire_date`)、休暇時間計算・残高台帳、残業割増率・上限エンジン、代休換算 | ⏳ 計画中 |
| **Phase 4** | `v0.5.0-workflow` | 動的フォーム (`JSONB`)、構造化条件による多段階承認ワークフロー (`wf_*`)、代理承認 (`wf_deputy`)、通知基盤 (`ntf_*`) | ⏳ 計画中 |
| **Phase 5** | `v0.6.0-dashboard-notice` | 上長向け勤怠ダッシュボード、打刻異常フィルタ、給与計算用 Excel/CSV 出力、社外・社内お知らせ機能 (`ann_*`) | ⏳ 計画中 |
| **Phase 6** | `v0.7.0-ai-suite` | 軽量 AI チャット (SSE)、職場テキスト変換、非同期音声ファイルの文字起こし (STT) | ⏳ 計画中 |

---

## アーキテクチャ意思決定記録 (ADRs)

設計上の判断とトレードオフはすべて英語で [`docs/adr/`](./docs/adr/) に記録されています（繁体字中国語訳は [`docs/adr/zh-TW/`](./docs/adr/zh-TW/)）：

- [ADR-0001: モジュラーモノリス構成と技術スタック選定](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md)
- [ADR-0002: 3層構造による3言語 i18n アーキテクチャ (`zh-TW`, `en-US`, `ja-JP`)](./docs/adr/ADR-0002-three-tier-i18n-architecture.md)
- [ADR-0003: 3つの入り口（Three Halls）とカレンダー駆動型勤怠モデル](./docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md)
- [ADR-0004: RBAC と部門ツリーを組み合わせたデータスコープ制御](./docs/adr/ADR-0004-rbac-and-department-data-scope.md)
- [ADR-0005: 動的フォームのバージョニングと多段階承認ワークフロー](./docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md)
- [ADR-0006: 実用的な AI モジュール設計（チャット・テキスト変換・非同期音声文字起こし）](./docs/adr/ADR-0006-ai-module-scope-and-async-stt.md)
- [ADR-0007: プロジェクト命名（Atria）およびテーブル接頭辞の統一規則](./docs/adr/ADR-0007-project-naming.md)
- [ADR-0008: エンタープライズ勤怠・休暇・残業・承認ワークフローの詳細仕様（日台労働法規対応）](./docs/adr/ADR-0008-attendance-leave-overtime-refinements.md)

---

## ライセンス

[MIT License](./LICENSE) の下で公開されています。
