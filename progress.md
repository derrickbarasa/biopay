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

### Frontend — household
- [x] View household on a **new page (no popups)** — dedicated route `/app/households/:householdNumber` (`HouseholdDetailPage.vue`); the old detail dialog was removed and the eye action now navigates. Shows all fields the backend currently returns + alternates + biometric status.
  - [ ] **Images**: blocked — no endpoint returns captured image filenames (GET_HOUSEHOLD only returns an `imageStatus` count). Needs a backend field (read from the `images` table) before photos can render. Backend/DB work — see decision below.
- [~] Filters — existing: organisation, state, county, location, village, gender, status, and a name/number/ID search (document number is covered by that search). Still to add: **vulnerability status, legal status, registration date range**.
  - [ ] `vulnerability_status` / `legal_status`: the `households` table has no such columns today (mobile captures vulnerability status, but the web schema doesn't expose it). Needs a migration + backend before these filters/graphs are real.
- [~] Graphs — by **age, gender, status** done (client-side over the filtered rows, on the Households page, toggle "Show Breakdown"). Legal-status / vulnerability-status graphs blocked on the same missing columns.
- [ ] Audit history — e.g. when it was paid. Blocked: needs a backend read joining `payments` / `audit_logs` for the household. Backend/DB work.

### Verifications
- [ ] OTP (mobile does **not** require OTP), email, TOTP.

### Subscription
- [ ] Implement subscription — archive data once subscription ends; to access it they pay; 30-day grace period.

### Data export  ✅ done
- [x] Household data — "Export CSV" on the Households page exports the currently filtered rows (`HouseholdsPage.vue`, uses `utils/csv`).
- [x] Alternate data — "Export" on the household detail page exports that household's alternates (`HouseholdDetailPage.vue`). (No list-all-alternates endpoint exists, so export is per-household.)
- [x] Payment data — already present on the Payments page ("Export CSV", `PaymentsPage.vue`); left as-is.

### Generated data visibility
- [ ] Way to show generated data — add column to show voucher and payment cycles.

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
- Next candidates (frontend-verifiable): "voucher/payment cycle" columns if backend already returns them; datatables search polish on remaining pages. Backend-gated items await the compile-only decision or the subscription/face-rec product calls.
