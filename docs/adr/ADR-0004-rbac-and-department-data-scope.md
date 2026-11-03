# ADR-0004: RBAC Combined with Department Tree Data Scope

- **Status:** Accepted
- **Date:** 2026-10-28

## Context

Pure Role-Based Access Control (RBAC: `User -> Role -> Permission`) determines *which features* a user can access (e.g., viewing the attendance dashboard or approving requests), but cannot express *whose data* a manager is allowed to see or approve. In an enterprise setting:
- A department manager should only see attendance records and approve requests for employees within their own department (and optionally sub-departments).
- HR and system administrators need company-wide visibility.
- When a manager is on leave, their approval authority must be temporarily delegable to a deputy without granting permanent role elevation.

## Decision

1. **Hierarchical Department Tree (`iam_department`):**
   - Each department defines `parent_id`, `tree_path` (materialized path such as `/1/4/12/`), and `leader_id` (the department manager).
2. **Role Data Scope (`DataScopeType`):**
   - Each role in `iam_role` is assigned a `data_scope` level:
     - `ALL`: Full company-wide data access (e.g., HR, Super Admin).
     - `DEPT_AND_SUB`: Access to the user's department and all descendant departments.
     - `DEPT_ONLY`: Access restricted to the user's immediate department.
     - `SELF_ONLY`: Access restricted to the user's own records.
   - A custom `@DataScope` AOP aspect dynamically appends department/user predicates to attendance and workflow queries.
3. **Account Security, Emergency Password Recovery & Soft Delete:**
   - **Password Hashing & DBA Emergency Recovery:** Passwords in `iam_user.password_hash` use Spring Security's `DelegatingPasswordEncoder` defaulting to `{bcrypt}` (strength 12). To support emergency DBA/ops troubleshooting when a user or admin is locked out, if a password in the database lacks the `{bcrypt}` prefix (e.g., `{noop}Temp123456` or raw plaintext entered directly via SQL), the authentication service permits a one-time login, automatically flags `must_change_password = TRUE`, restricts the session strictly to the password-change endpoint, and immediately re-hashes the new password with `{bcrypt}`.
   - **Brute-Force Lockout:** Track consecutive failed login attempts (`failed_login_attempts`) and lock the account for 15 minutes (`locked_until`) upon 5 consecutive failures.
   - **Non-Destructive Soft Deletion:** Use PostgreSQL Partial Unique Indexes (`CREATE UNIQUE INDEX uq_iam_user_username ON iam_user(username) WHERE is_deleted = FALSE;`) so resigned/deactivated employees can be archived without blocking future re-hires or colliding with unique username/email constraints.
4. **Field-Level Encryption (`AES-256-GCM`) & Attachment Storage Strategy:**
   - **Sensitive Database Columns:** High-sensitivity fields (such as outbound webhook secrets, external API tokens, and sensitive personal identifiers) are encrypted transparently via a JPA `AttributeConverter` (`AesGcmStringConverter`) using `AES-256-GCM` with a random 96-bit IV and versioned prefix (`{v1}<Base64(IV + Ciphertext + AuthTag)>`). The Key Encryption Key (KEK) is injected via environment variable (`ATRIA_CRYPTO_KEK`) with a safe fallback for local development—eliminating external `.key` file management while supporting future key rotation.
   - **Uploaded Documents & Audio Files:** File binaries are stored outside the database via `FileStorageService` using date-partitioned random UUID paths (`{category}/{yyyy-MM-dd}/{uuid}.{ext}`), while the database records metadata and a `SHA-256` integrity checksum. Application-layer stream encryption is intentionally omitted on file binaries to preserve HTTP byte-range streaming (for PDF/audio preview and `whisper.cpp` STT processing) and host antivirus inspectability; access is strictly gated by JWT authentication and `@DataScope` authorization.
5. **Time-Bounded Approval Deputy (`wf_deputy`):**
   - Managers can configure a deputy (`principal_user_id`, `deputy_user_id`, `start_at`, `end_at`, `active`) in the workflow module so approval routing never stalls during manager absences.

## Consequences

- The same `/admin` attendance dashboard and approval endpoints serve both department managers and HR without duplicating controllers—each caller automatically sees only the rows permitted by their `data_scope`.
- Materialized `tree_path` allows fast subtree filtering (`LIKE '/1/4/%'`) without requiring recursive SQL on every read query.
- Operations staff can recover locked-out accounts directly in the database during emergencies without leaving plaintext passwords at rest after login, and sensitive secrets remain protected against database dump leaks via `AES-256-GCM`.
