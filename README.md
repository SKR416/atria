# Atria — Attendance & Approval Workflow Platform

[English](./README.md) | [繁體中文](./README.zh-TW.md) | [日本語](./README.ja.md)

> **Current Status:** `Phase 0 — Foundation (In Progress)`  
> Only repository structure, architecture documentation, and Architecture Decision Records (ADRs) are committed at this stage. See [docs/ROADMAP.md](./docs/ROADMAP.md) for the full 7-phase implementation schedule.

Atria is an enterprise platform that brings attendance tracking and approval workflows together. Employees clock in and submit leave or punch-correction requests, managers and HR approve them through a multi-level workflow, and approved requests are written back to attendance records automatically. Built as a Spring Boot 3 + Vue 3 modular monolith with RBAC, dynamic forms, and trilingual i18n (`zh-TW` / `en-US` / `ja-JP`).

---

## Why "Atria"?

*Atria is the plural of atrium: three halls (public site, employee portal, admin console) around one shared backend hub.*

- **`/public` (Public Site):** Unauthenticated external announcements with category filtering and pinned notices.
- **`/portal` (Employee Portal / ESS):** Daily workspace for all employees to clock in/out, view the corporate work calendar, submit leave or punch-correction requests, read internal announcements, and use lightweight AI utilities.
- **`/admin` (Admin Console):** Management hub for managers, HR, and system administrators to monitor team attendance on the dashboard, approve multi-level requests, define the corporate work calendar, configure dynamic forms, and manage RBAC permissions.

---

## Tech Stack

| Layer | Technology | Purpose |
| :--- | :--- | :--- |
| **Backend** | Java 21 LTS, Spring Boot 3 | Modular monolith core (`io.github.skr416.atria`) |
| **Security** | Spring Security 6, JWT (jjwt) | Stateless authentication, RBAC, and department data-scope filtering |
| **Database** | PostgreSQL 16, Flyway | Relational core with native `JSONB` for dynamic forms and i18n fields |
| **Frontend** | Vue 3, Vite, TypeScript, PrimeVue, ApexCharts | SPA with high information density B2B layout and three shells (`/public`, `/portal`, `/admin`) |
| **i18n** | Spring `MessageSource`, `vue-i18n` | Trilingual support (`zh-TW`, `en-US`, `ja-JP`) across API, DB, and UI |
| **API Contract** | Springdoc OpenAPI 3 | Swagger documentation and frontend TypeScript type generation |

---

## Repository Structure

```text
atria/
├── docs/
│   ├── ROADMAP.md                # 7-phase milestone plan and Git tag schedule
│   ├── ARCHITECTURE.md           # Modular monolith layout and database schema overview
│   └── adr/                      # Architecture Decision Records (ADR-0001 to ADR-0007)
├── backend/                      # Maven artifactId: atria-backend (Phase 0+)
└── frontend/                     # npm package: atria-frontend (Phase 2+)
```

---

## Implementation Roadmap & Status

| Phase | Target Tag | Scope | Status |
| :--- | :--- | :--- | :--- |
| **Phase 0** | `v0.1.0-foundation` | Repository setup, ADRs, Spring Boot 3 skeleton, `ApiResponse<T>`, trilingual `ErrorCode` i18n | 🔄 In Progress |
| **Phase 1** | `v0.2.0-iam` | IAM schema (`iam_*`), JWT auth, RBAC, department tree, `@DataScope`, audit log | ⏳ Planned |
| **Phase 2** | `v0.3.0-frontend-shell` | Vue 3 shell (`atria-frontend`), three layouts (`/public`, `/portal`, `/admin`), IAM admin UI | ⏳ Planned |
| **Phase 3** | `v0.4.0-attendance-portal` | HR work calendar, clock-in/out punch, leave calculator, employee portal (`/portal`) | ⏳ Planned |
| **Phase 4** | `v0.5.0-workflow` | Dynamic form templates (`JSONB`), multi-level approval workflow (`wf_*`), attendance write-back | ⏳ Planned |
| **Phase 5** | `v0.6.0-dashboard-notice` | Manager attendance dashboard, anomaly filtering, public & internal announcements (`ann_*`) | ⏳ Planned |
| **Phase 6** | `v0.7.0-ai-suite` | Embedded AI chat, workplace text transformation, and async audio-to-text transcription (STT) | ⏳ Planned |

---

## Architecture Decision Records (ADRs)

All architectural decisions and trade-offs are documented in English under [`docs/adr/`](./docs/adr/):

- [ADR-0001: Modular Monolith Architecture and Core Tech Stack](./docs/adr/ADR-0001-modular-monolith-and-tech-stack.md)
- [ADR-0002: Three-Tier Trilingual i18n Architecture (`zh-TW`, `en-US`, `ja-JP`)](./docs/adr/ADR-0002-three-tier-i18n-architecture.md)
- [ADR-0003: Three-Hall Routing and Calendar-Driven Attendance Model](./docs/adr/ADR-0003-three-halls-and-calendar-driven-attendance.md)
- [ADR-0004: RBAC Combined with Department Tree Data Scope](./docs/adr/ADR-0004-rbac-and-department-data-scope.md)
- [ADR-0005: Dynamic Form Versioning and Multi-Level Approval Workflow](./docs/adr/ADR-0005-dynamic-form-versioning-and-approval-workflow.md)
- [ADR-0006: Practical AI Module Scope (Chat, Text Transformation, and Async STT)](./docs/adr/ADR-0006-ai-module-scope-and-async-stt.md)
- [ADR-0007: Project Naming ("Atria") and Schema Prefix Alignment](./docs/adr/ADR-0007-project-naming.md)

---

## License

Licensed under the [MIT License](./LICENSE).
