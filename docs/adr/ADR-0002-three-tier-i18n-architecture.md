# ADR-0002: Three-Tier Trilingual i18n Architecture (`zh-TW`, `en-US`, `ja-JP`)

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Atria supports three default locales from day one:
- **Traditional Chinese (`zh-TW`)**
- **English (`en-US`)**
- **Japanese (`ja-JP`)**

Hardcoding user-facing strings in Java exceptions, database columns, or Vue templates makes localization incomplete and error-prone. We need a unified internationalization (i18n) strategy across the API layer, persistence layer, frontend UI, and timestamp handling.

## Options Considered

### Option A: Separate Translation Join Tables in Database (`*_translation`)
- **Pros:** Strictly normalized relational schema.
- **Cons:** Requires additional `LEFT JOIN` queries on every lookup table (roles, departments, leave types, announcements), complicating JPA mappings and pagination.

### Option B: Three-Tier i18n with PostgreSQL `JSONB` for Domain Entities
- **Pros:**
  1. **Backend API Tier:** Business exceptions carry a typed `ErrorCode` enum (never hardcoded strings). `GlobalExceptionHandler` resolves localized messages via Spring `MessageSource` (`messages_zh_TW.properties`, `messages_en_US.properties`, `messages_ja_JP.properties`) using the request's `Accept-Language` header.
  2. **Database Tier:** Dynamic user-defined labels (department names, role names, leave type names, holiday labels, announcement titles) are stored in a single `JSONB` column:
     ```json
     {"zh-TW": "特休假", "en-US": "Annual Leave", "ja-JP": "年次有給休暇"}
     ```
  3. **Frontend Tier:** `vue-i18n` manages static UI labels and synchronizes the active locale with Element Plus's `ElConfigProvider` and the Axios `Accept-Language` header.

## Decision

We adopt **Option B (Three-Tier i18n Architecture)** along with strict UTC timestamp storage:
- All database timestamps use PostgreSQL `TIMESTAMPTZ` stored in **UTC**, serialized as ISO-8601 strings, and formatted in the client's timezone.

## Consequences

- Adding a new business error requires adding an enum constant to `ErrorCode` and corresponding keys in all three `.properties` bundles.
- Querying or indexing specific language values inside PostgreSQL `JSONB` can use expression indexes (`(name_i18n ->> 'zh-TW')`) when needed.
