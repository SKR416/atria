# ADR-0001: Modular Monolith Architecture and Core Tech Stack

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Atria integrates corporate attendance tracking (work calendar, daily clock-in/out, leave and punch-correction requests), multi-level approval workflows with dynamic forms, RBAC administration, dual-scope announcements, and lightweight AI utilities. We needed to choose between:

1. A server-side rendered (SSR) monolith using Spring Boot + Thymeleaf.
2. A microservices architecture with separate repositories and databases per domain.
3. A **Modular Monolith** backend (Spring Boot 3 + PostgreSQL 16) paired with a decoupled **Single-Page Application** frontend (Vue 3 + TypeScript + PrimeVue + ApexCharts) in a single repository (Monorepo).

## Options Considered

### Option A: Spring Boot + Thymeleaf (SSR)
- **Pros:** Single build artifact; no separate frontend build pipeline.
- **Cons:** Interactive calendar views, dynamic form schema rendering, and Server-Sent Events (SSE) for AI chat require heavy client-side state management, which quickly becomes brittle in jQuery/Thymeleaf templates.

### Option B: Distributed Microservices
- **Pros:** Independent deployment and scaling per service.
- **Cons:** Massive operational and distributed-transaction overhead (e.g., when an approved workflow instance must atomically update an attendance record). Unnecessary complexity for an enterprise foundation platform.

### Option C: Modular Monolith (Spring Boot 3 + PostgreSQL 16 + Vue 3 in a Monorepo)
- **Pros:**
  - Clean domain boundaries (`iam`, `attendance`, `workflow`, `dashboard`, `announcement`, `ai`) enforced via Java package structure while retaining ACID transactions across approval workflows and attendance write-backs.
  - PostgreSQL 16 provides native `JSONB` for dynamic form schemas and trilingual dictionary fields, plus recursive CTEs for department hierarchies.
  - **Frontend Stack & SCRM Compliance:** Adopts **PrimeVue** (Europe/PrimeTek, 100% MIT) and **ApexCharts** (100% MIT), strictly eliminating components with geopolitical provenance scrutiny (e.g. Baidu ECharts or Alibaba Element Plus) while providing enterprise-grade high information density (`DataTable`, `TreeTable`, `Timeline`) and attendance heatmap visualizations out of the box.
  - Supporting libraries: `@fullcalendar/vue3` for corporate shift schedules, `@tiptap/vue-3` for sanitized announcement authoring, and standalone `whisper.cpp` HTTP services for local STT transcription without JVM crash risks.
  - A monorepo keeps backend code, frontend code, and ADRs in lockstep per Git commit.

## Decision

We adopt **Option C**: a **Spring Boot 3 (Java 21 LTS) + PostgreSQL 16 + Vue 3 (Vite, TypeScript, PrimeVue, ApexCharts) Modular Monolith** managed in a single repository (`atria`).

## Consequences

- Cross-module interactions (such as approval completion updating attendance status) execute within local database transactions or Spring application events without network serialization overhead.
- Module boundaries must be kept clean: modules should communicate via public service interfaces rather than reaching directly into another module's repositories.
