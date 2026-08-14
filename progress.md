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

### Mobile — `mobile/nca` (field agent app)  — 📋 planned only (per decision; no code written)
Context: Android `com.flexmoney.nca`, Java/Kotlin, Morpho fingerprint SDK, offline `ContentProvider`+`SyncAdapter`, Volley/OkHttp to the backend dispatch API. Cannot be built/tested in the web session (no Android SDK/NDK), so this is a concrete plan for later.

#### A. Facial recognition (register by face / fingerprint / both)
- **SDK choice (recommended): fully on-device, offline-first** — Google **ML Kit Face Detection** (free) to find/validate a single clear face + **TensorFlow Lite MobileFaceNet** (or FaceNet) to compute a 128/512-d embedding for 1:1 verification. Add liveness later (commercial SDKs — Innovatrics/Regula/Neurotechnology — if higher assurance is required; they carry licensing cost). Rationale: matches the existing offline-first, no-signal-at-capture model and needs no per-match server call.
- **Enrollment**: add a "Register by: Fingerprint / Face / Both" choice in the registration flow (reuse existing `PhotoActivity` for capture). Compute the embedding on-device, encrypt at rest with the existing `Crypto` (same as fingerprint templates), store locally + sync.
- **Backend** (mirror fingerprints): migration `faces` table (supervisor_id, beneficiary_type, beneficiary_id, uuid, embedding [encrypted], partner_code, status, created_at, stored_at). New codes in `Biometric.java`: `ENROLL_FACE`, `VERIFY_FACE` (cosine-similarity threshold, e.g. ≥0.6 — mirrors the placeholder `VERIFY_FINGERPRINT`), `SYNC_FACES` (offline bundle, like `SYNC_FINGERPRINTS`; also add faces to `BIOMETRIC_LOGIN`).
- **Verification at payment**: `PaymentVerificationActivity` offers fingerprint OR face; face path captures → embeds → compares against the synced enrolled embedding for the household on-device (offline).
- **Data model**: add a `registration_method` (FINGERPRINT/FACE/BOTH) column to households/alternates so the app knows which verification to offer.

#### B. Voucher redemption on device
- **Backend is already primed**: `REDEEM_VOUCHER` accepts optional biometric-match fields (matchedFingerprint/latitude/longitude) for exactly this flow (see `Voucher.java` javadoc); the `vouchers` table has `redeemed_by_officer_id`, `matched_fp`, lat/long. Add `SYNC_VOUCHERS` (mirror `SYNC_PAYMENTS`) so issued vouchers can be validated offline.
- **Mobile flow**: new `VoucherRedemptionActivity` — scan the printed voucher QR (add `zxing-android-embedded`) or pick from the synced list → verify household biometric (fingerprint/face) → call `REDEEM_VOUCHER { voucherCode, matchedFingerprint, latitude, longitude }`. Queue offline and sync via the existing `SyncAdapter`. Add a "Redeem Voucher" entry to `MainActivity` gated by the VOUCHERS org module; add a `RecyclerVoucherAdapter`.
- **QR note**: the web-printed voucher QR currently encodes the **household number**; for direct scan-to-redeem, either encode `voucher_code` on the voucher QR or have the app resolve the household → its issued vouchers.

#### Files to touch (later)
- Backend: `database/migrations/0xx_faces.sql`; `Biometric.java` (ENROLL_FACE/VERIFY_FACE/SYNC_FACES); `Voucher.java` (SYNC_VOUCHERS); processing-code catalog.
- Mobile: registration-method choice UI; TFLite model asset + embedding util; face capture/verify activities; `VoucherRedemptionActivity` + adapter; `SyncAdapter`/provider extended to faces + voucher redemptions; QR-scanner dependency.

### Frontend — website (public marketing site)  ✅ done
- [x] Global theme adapted away from green → teal/blue-green (`plugins/vuetify.ts` primary `#0D9488`). Green kept only for success states.
- [x] Landing page (`LandingPage.vue`): recolored its own token palette + hero scrim green → teal; **removed the entire pricing section** (+ nav/footer/CTA links repointed); added a **Request-a-demo** section with a working form (opens a prefilled mail to demo@biopay.app). Carousel hero already existed (kept). Verified visually via a production-build screenshot.
  - Note: "more images" — the hero references `/hero/*.jpg` that aren't in the repo (only a README); drop real photos there and they render automatically. Left the existing gradient/scrim look otherwise.

### Frontend — dashboard  ✅ done (voucher backend verified by mvn compile only)
- [x] More graphs — added an "amount generated by organisation" bar chart (anchor) from existing metrics; retuned dashboard accent green → teal.
- [x] Generate and print payment vouchers — new backend `GET_HOUSEHOLD_VOUCHER` returns a self-contained slip (household full name, photo + QR both inlined as data URIs; QR via new `QrSupport` reusing ZXing). "Print Voucher" button on the household detail page opens a print-ready window.

### Frontend — household  ✅ done (backend verified by mvn compile only)
- [x] View household on a **new page (no popups)** — dedicated route `/app/households/:householdNumber` (`HouseholdDetailPage.vue`); old dialog removed, eye action navigates.
  - [x] **Images** now render — `GET_HOUSEHOLD` returns `images` (authed `/files` URLs, read from the `images` table); the detail page fetches each photo through `apiClient` as a blob (a plain `<img src>` can't send the JWT) and shows a gallery.
  - [x] Fuller detail fields exposed by `summary()` — marital status, spouse, ID/document number, dependants, beneficiary type, vulnerability & legal status.
- [x] Filters — added **vulnerability status, legal status, registration date range** (`GET_HOUSEHOLDS` new params) on top of existing organisation/state/county/location/village/gender/status/search. (Document number is covered by the name/number/ID search.)
  - Migration **`009_household_attributes.sql`** adds `vulnerability_status` + `legal_status` columns (+ indexes) to `households`. **Must be applied before those filters/graphs return data.**
- [x] Graphs — age, gender, status, **vulnerability status, legal status** (client-side over the filtered rows; "Show Breakdown" toggle on the Households page).
- [x] Audit history — new `GET_HOUSEHOLD_HISTORY` returns the household's `payments` (amount/status/cycle/date — "when it was paid") and `audit_logs` events; both shown on the detail page.

### Verifications  ✅ done (already implemented; audited + theme fix)
- [x] Audit result: already fully built — login OTP with **EMAIL / TOTP** method choice (`VerifyOtpPage.vue`), email always-on + **TOTP enable/disable with QR** in Settings (`SettingsPage.vue`, codes `TOTP_SETUP_INIT/CONFIRM/DISABLE`), and password reset flow. Mobile logs in via `BIOMETRIC_LOGIN` (no OTP) — satisfies "mobile doesn't require OTP".
- [x] Fixed the one loose end: the 5 auth pages' background gradients were still green (`#062f2d`/`#047857`) → retuned to teal for theme consistency.

### Subscription  ✅ done (lifecycle + manual renewal; backend verified by mvn compile only)
- [x] Per-anchor subscription (`migration 010_subscriptions.sql`): `expires_at` + `grace_days` (default 30); **status derived in SQL** (ACTIVE / GRACE / ARCHIVED), never stored.
- [x] `Subscription` verticle (deployed in `EntryPoint`): `GET_SUBSCRIPTION` (status + days-to-expiry/archive), `RENEW_SUBSCRIPTION` (manual admin upsert, extends one month from the later of current expiry/today — anchor-only). No external billing gateway (manual renewal, per decision).
- [x] Frontend gating (`DefaultLayout.vue`): a **grace-period banner** with a Renew action, and an **archived gate** that replaces page content with a renew prompt once the grace window ends (anchors renew; orgs are told to contact their anchor). Fail-open if the status check errors.
- [x] **Server-side enforcement (done):** `EntryPoint` now gates every `/api/v1/req` data operation through `dispatchGated` — when the caller's anchor is ARCHIVED it returns **402** ("Subscription expired. Renew to restore access.") instead of dispatching. `Subscription.statusFor(pool, anchorId)` derives the status; exempt codes (`GET_SUBSCRIPTION`, `RENEW_SUBSCRIPTION`, `ME`, `LOGOUT`, `CHANGE_PASSWORD`, `TOTP_*`, `GET_ORGANIZATION_MODULES`) and callers with no anchor pass through, and any non-ARCHIVED status — including a failed lookup (resolves to NONE) — **fails open** so a transient DB issue can't lock the platform out. Verified by `mvn compile` (no DB to runtime-test).

### Data export  ✅ done
- [x] Household data — "Export CSV" on the Households page exports the currently filtered rows (`HouseholdsPage.vue`, uses `utils/csv`).
- [x] Alternate data — "Export" on the household detail page exports that household's alternates (`HouseholdDetailPage.vue`). (No list-all-alternates endpoint exists, so export is per-household.)
- [x] Payment data — already present on the Payments page ("Export CSV", `PaymentsPage.vue`); left as-is.

### Generated data visibility  ✅ done (backend verified by mvn compile only)
- [x] `GET_HOUSEHOLDS` now returns `voucherCount` and `paymentCycleCount` per household (correlated subqueries over `vouchers.household_number` and distinct `payments.cycle`).
- [x] Households table shows **Vouchers** and **Cycles** columns (chips); counts also included in the CSV export.

### Tables  ✅ done
- [x] `LocationsPage` — converted its 4 raw `v-table`s (states/counties/locations/villages) to `v-data-table` with sort/pagination + a per-tab search box.
- [x] Added a client-side search box (`:search`) to every other list datatable: Officers, Organizations, Payments, Vouchers, Payment Cycles, Attendance. Households already has server-side search.
- Intentional exception: the two `v-table`s on the Dashboard are fixed summary widgets (org-amount + recent-activity), not searchable data grids — left as-is.

### Sidebar (adopt a lighter color)  ✅ done
- [x] Lighter drawer color — switched from dark green (`#062f2d`) to a light `surface` drawer with teal active states (`DefaultLayout.vue`).
- [x] Dashboard — top-level item.
- [x] Configs — Organizations, Officers, Locations grouped under a "Configs" subheader. (Note: no dedicated *anchors / users / permissions-roles* pages exist yet — only existing routes are linked; see gap below.)
- [x] Transfers — Households, Payments, Payment Cycles (payment generation = payroll), Vouchers, Attendance grouped under "Transfers".
- [x] Settings — top-level item.
- [x] Footer — Log out pinned to drawer bottom (`#append`), red-tinted.
- Gap: `anchors`, `users`, `permissions/roles` have no routes/pages yet — deferred until those pages/backends exist.

### Deduplication  ✅ done (backend verified by mvn compile only)
- [x] New `CHECK_HOUSEHOLD_DUPLICATE` screens a would-be registration against existing households in the same organisation on the product's stated match key: **same ID/document number** (strongest), **same phone number**, or **same name in the same village**. Returns candidates with per-match reasons (advisory, not a hard block — mirrors the mobile `duplicate`/`matching_score` model).
- [x] Wired into the Add-Household flow: Save runs the check first; if candidates are found they're listed with reasons and creation waits for an explicit **"Register anyway"**.
- Match key was taken from the existing product (landing copy + the `duplicate`/`matching_score` columns), not a new decision — easy to tune (add name+DOB, fuzzy/phonetic matching, or distance-based location matching later).

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
- **Deliverable 6 — Dashboard + printable voucher (done; voucher backend compile-only, frontend verified):** `QrSupport` (generic ZXing QR), `GET_HOUSEHOLD_VOUCHER` (name + inlined photo + QR); detail-page "Print Voucher"; dashboard org-amount chart + teal restyle. `mvn compile` + `vue-tsc`/`vite build` pass.
- **Deliverable 7 — Website landing redesign (done, frontend-only):** teal recolor, pricing removed, request-a-demo form (mailto), links repointed. `vue-tsc`/`vite build` pass; verified with a production-build screenshot.
- **Deliverable 8 — Verifications audit (done, frontend-only):** confirmed OTP/email/TOTP already implemented; retuned the 5 auth-page gradients green → teal. `vue-tsc`/`vite build` pass.
- **Push:** still blocked (GitHub read-only, fixed at session start). Work handed off via `biopay-work.bundle` (regenerate after new commits). Local commits: `0fba6fb`, `721862e`, `72b42dc`, `55b5485`, `1056b96`, `de550f4`, `c70eab3`, + this one.
- **Deliverable 9 — Deduplication (done; backend compile-only, frontend verified):** `CHECK_HOUSEHOLD_DUPLICATE` (ID/phone/name+village) + Add-Household "Register anyway" flow.
- **Deliverable 10 — Subscription (done; backend compile-only, frontend verified):** decision = lifecycle + manual renewal. Migration 010, `Subscription` verticle (GET/RENEW), grace banner + archived gate in `DefaultLayout`. `mvn compile` + `vue-tsc`/`vite build` pass. Server-side dispatch enforcement noted as follow-up.
- **Deliverable 11 — Mobile plan (done):** decision = plan only. Concrete face-recognition + voucher-redemption implementation plan written into the Mobile section above (SDK choice, enrollment/verification flows, backend endpoints, files to touch). No code.
- **Deliverable 12 — Subscription server-side enforcement (done; backend compile-only):** `EntryPoint.dispatchGated` refuses data ops with 402 when the caller's anchor is ARCHIVED, via `Subscription.statusFor`; exempt codes + no-anchor + non-ARCHIVED (incl. failed lookup) fail open. `mvn compile` passes.
- **Deliverable 13 — Datatables (done, frontend-only):** LocationsPage's 4 raw tables → `v-data-table` + search; search box added to the other 6 list pages. `vue-tsc`/`vite build` pass.
- **Roadmap status: every original `progress.md` item is now ✅ done** — except the Mobile code, which is intentionally **plan-only** per your decision (the plan is in the Mobile section above). Minor noted asset/UX gaps remain (hero `/hero/*.jpg` images not in repo; no anchors/users/roles admin pages yet).
- **Push:** still blocked (GitHub read-only, fixed at session start). Hand off via `biopay-work.bundle` (regenerate from `origin/main..HEAD` after each commit). Local commits: `0fba6fb`, `721862e`, `72b42dc`, `55b5485`, `1056b96`, `de550f4`, `c70eab3`, `0a4ded5`, `b7adc83`, + this one (10 code deliverables).
