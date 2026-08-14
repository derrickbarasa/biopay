# BioPay — Project Progress Tracker

This .md file is used to track the project process and is updated as work is
done, to prevent re-reading the whole codebase unnecessarily. It is the guide
to go through, tracking every step of the project. Update it after every check
and note any issues that come about here, ensuring it is updated after every
session. The work to be done is below.

**Status legend:** `[ ]` not started · `[~]` in progress · `[x]` done · `[?]` blocked / needs decision

---

## Repo map (quick reference)

- `backend/`  — Java 17, Vert.x reactive verticles, MSSQL. Dispatch by `processingCode` over the event bus (`main/EntryPoint.java`). Services in `services/`.
- `frontend/` — Vue 3 + Vuetify 4 + Vite + TS. Single `dispatch()` client (`src/api/client.ts`), codes catalog (`src/api/processingCodes.ts`), pages in `src/pages/`.
- `mobile/nca/` — Android (Java/Kotlin) field app, Morpho fingerprint SDK, offline SyncAdapter.
- `database/` — MSSQL migrations (`migrations/`) + seed. Idempotent, no FK constraints by convention.

---

## Roadmap / work to be done

### Mobile — `mobile/nca` (field agent app)
- [ ] **Facial recognition** — a person can decide to register by face **or** fingerprint **or** both.
- [ ] **Voucher redemption** — one can redeem a voucher on the device.

### Frontend — website (public marketing site)
- [~] **Redesign** — more images, carousel, less green (adapt the theme color), remove pricing, add "request for demo".
  - [x] Global theme adapted away from green → teal/blue-green (`plugins/vuetify.ts` primary `#0D9488`). Green kept only for success states.
  - [ ] Landing page: more images, carousel, remove pricing, add "request a demo" flow.

### Frontend — dashboard
- [ ] Add more graphs and more data.
- [ ] Generate and print payment vouchers — voucher shows full names of the person, image, and QR code for the household.

### Frontend — household  ✅ done (backend verified by mvn compile only)
- [x] View household on a **new page (no popups)** — dedicated route `/app/households/:householdNumber` (`HouseholdDetailPage.vue`); old dialog removed, eye action navigates.
  - [x] **Images** now render — `GET_HOUSEHOLD` returns `images` (authed `/files` URLs, read from the `images` table); the detail page fetches each photo through `apiClient` as a blob (a plain `<img src>` can't send the JWT) and shows a gallery.
  - [x] Fuller detail fields exposed by `summary()` — marital status, spouse, ID/document number, dependants, beneficiary type, vulnerability & legal status.
- [x] Filters — added **vulnerability status, legal status, registration date range** (`GET_HOUSEHOLDS` new params) on top of existing organisation/state/county/location/village/gender/status/search. (Document number is covered by the name/number/ID search.)
  - Migration **`009_household_attributes.sql`** adds `vulnerability_status` + `legal_status` columns (+ indexes) to `households`. **Must be applied before those filters/graphs return data.**
- [x] Graphs — age, gender, status, **vulnerability status, legal status** (client-side over the filtered rows; "Show Breakdown" toggle on the Households page).
- [x] Audit history — new `GET_HOUSEHOLD_HISTORY` returns the household's `payments` (amount/status/cycle/date — "when it was paid") and `audit_logs` events; both shown on the detail page.

### Verifications
- [ ] OTP (mobile does **not** require OTP), email, TOTP.

### Subscription
- [ ] Implement subscription — archive data once subscription ends; to access it they pay; 30-day grace period.

### Data export  ✅ done
- [x] Household data — "Export CSV" on the Households page exports the currently filtered rows (`HouseholdsPage.vue`, uses `utils/csv`).
- [x] Alternate data — "Export" on the household detail page exports that household's alternates (`HouseholdDetailPage.vue`). (No list-all-alternates endpoint exists, so export is per-household.)
- [x] Payment data — already present on the Payments page ("Export CSV", `PaymentsPage.vue`); left as-is.

### Generated data visibility  ✅ done (backend verified by mvn compile only)
- [x] `GET_HOUSEHOLDS` now returns `voucherCount` and `paymentCycleCount` per household (correlated subqueries over `vouchers.household_number` and distinct `payments.cycle`).
- [x] Households table shows **Vouchers** and **Cycles** columns (chips); counts also included in the CSV export.

### Tables
- [ ] Put datatables in all the tables.

### Sidebar (adopt a lighter color)  ✅ done
- [x] Lighter drawer color — switched from dark green (`#062f2d`) to a light `surface` drawer with teal active states (`DefaultLayout.vue`).
- [x] Dashboard — top-level item.
- [x] Configs — Organizations, Officers, Locations grouped under a "Configs" subheader. (Note: no dedicated *anchors / users / permissions-roles* pages exist yet — only existing routes are linked; see gap below.)
- [x] Transfers — Households, Payments, Payment Cycles (payment generation = payroll), Vouchers, Attendance grouped under "Transfers".
- [x] Settings — top-level item.
- [x] Footer — Log out pinned to drawer bottom (`#append`), red-tinted.
- Gap: `anchors`, `users`, `permissions/roles` have no routes/pages yet — deferred until those pages/backends exist.

### Deduplication
- [ ] Implement deduplication across (beneficiaries).

---

## Session log

### 2026-08-14
- Analyzed full project structure & architecture (backend Vert.x dispatch model, frontend Vue/Vuetify, Android/Morpho mobile, MSSQL migrations).
- Restructured this file into a tracked checklist (all original roadmap items preserved verbatim).
- Confirmed with user: keep existing architecture (Vert.x dispatch / Vue-Vuetify / processingCode contract) — additive changes only. Theme direction: **teal / blue-green**. Sequencing left to my judgement; started with foundational UI.
- **Deliverable 1 — Foundational UI (done):**
  - Theme primary green → teal (`plugins/vuetify.ts`).
  - Sidebar restructured into Dashboard / Configs / Transfers / Settings groups, lighter `surface` color, logout footer (`layouts/DefaultLayout.vue`).
  - Verified: `npm install` + `vue-tsc -b` type-check pass (exit 0).
- **Deliverable 2 — Household detail page + breakdown graphs (done, frontend-only):**
  - New route + `HouseholdDetailPage.vue` (no popups); removed the old dialog; eye action navigates.
  - Client-side breakdown graphs (gender / age / status) on the Households page behind a "Show Breakdown" toggle, using existing `BarChart`.
  - Verified: `vue-tsc -b` type-check + `vite build` both pass (exit 0).
- **Push status:** GitHub write blocked (403, read-only integration). Per user, building locally only; commits stay local until write access is granted. Local commits so far: `0fba6fb` (deliverable 1) + this one.
- **Blocked pending decisions / backend work I can't runtime-verify without the MSSQL DB (can only compile):**
  - Household images endpoint, vulnerability/legal-status columns (migration), household audit/payment history.
  - Subscription/billing approach; mobile face-recognition SDK/licensing.
  - Data export (household/alternate/payment) — mostly frontend CSV, partially doable now.
  - Generated-data columns (voucher & payment cycles) — needs backend to surface those on the list.
- **Deliverable 3 — CSV exports (done, frontend-only):** household export (Households page), per-household alternate export (detail page); payment export already existed. `vue-tsc` + `vite build` pass. Local commit added.
- **Deliverable 4 — Household deep-dive backend (done; backend compile-only, frontend verified):**
  - Migration `009_household_attributes.sql` (vulnerability_status, legal_status + indexes).
  - `Household.java`: fuller `summary()`; new GET_HOUSEHOLDS filters (vulnerability/legal/date range); `GET_HOUSEHOLD` now returns real image URLs; new `GET_HOUSEHOLD_HISTORY` (payments + audit).
  - Frontend: detail page shows photos (authed blob fetch) + payment/audit history + new fields; Households page gains the new filters and vulnerability/legal breakdown charts.
  - Verified: `mvn compile` (backend, exit 0), `vue-tsc -b` + `vite build` (frontend, exit 0). **Not** runtime-tested — no MSSQL here; **migration 009 must be applied** on the target DB.
- **Deliverable 5 — Generated-data columns (done; backend compile-only, frontend verified):** `GET_HOUSEHOLDS` returns per-household `voucherCount` + `paymentCycleCount`; Households table shows Vouchers/Cycles columns + in CSV export. `mvn compile` + `vue-tsc`/`vite build` pass.
- **Still open:** verifications UI audit; deduplication (needs match-key decision); subscription (needs billing decision); mobile face-recognition (needs SDK decision); website landing redesign (frontend). Local commits: `0fba6fb`, `721862e`, `72b42dc`, `55b5485`, + this one — all unpushed (GitHub read-only).
