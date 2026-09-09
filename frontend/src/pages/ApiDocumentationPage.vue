<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const toast = useToast()
const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:7730/biopay'

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('Copied to clipboard')
  } catch {
    toast.error('Could not copy — select and copy manually')
  }
}

const loginExample = `POST ${baseUrl}/authentication
Content-Type: application/json

{
  "processingCode": "LOGIN_USER",
  "email": "you@yourorganisation.org",
  "password": "your-password"
}`

const loginResponseExample = `{
  "responseCode": "000",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "5b1c1e2a-...",
  "user": { "id": 42, "role": "ORGANISATION", "...": "..." }
}`

const refreshExample = `POST ${baseUrl}/authentication
Content-Type: application/json

{
  "processingCode": "REFRESH_TOKEN",
  "refreshToken": "5b1c1e2a-..."
}`

const envelopeExample = `POST ${baseUrl}/api/v1/req
Content-Type: application/json
Authorization: Bearer <accessToken>

{
  "processingCode": "<ONE_OF_THE_CODES_BELOW>",
  "...": "code-specific fields, see below"
}`

const createHouseholdExample = `{
  "processingCode": "CREATE_HOUSEHOLD",
  "organisationCode": "ORG001",
  "householdName": "Jane Doe",
  "age": 34,
  "gender": "FEMALE",
  "maritalStatus": "MARRIED",
  "spouseName": "John Doe",
  "idNumber": "ID-00219",
  "phoneNumber": "+211900000000",
  "householdSize": 5,
  "femaleDependants": 3,
  "maleDependants": 1,
  "stateCode": "STC001",
  "countyCode": "CTY004",
  "payamCode": "LOC010",
  "bomaCode": "VLG022"
}`

const bulkUploadExample = `{
  "processingCode": "BULK_UPLOAD_HOUSEHOLDS",
  "organisationCode": "ORG001",
  "villageCode": "VLG022",
  "fileName": "batch-1.xlsx",
  "rows": [
    {
      "householdName": "Jane Doe",
      "age": 34,
      "gender": "FEMALE",
      "maritalStatus": "MARRIED",
      "spouseName": "John Doe",
      "idNumber": "ID-00219",
      "phoneNumber": "+211900000000",
      "householdSize": 5,
      "femaleDependants": 3,
      "maleDependants": 1
    }
  ]
}`

const createAlternateExample = `{
  "processingCode": "CREATE_ALTERNATE",
  "householdNumber": "HH00019",
  "alternateName": "Mary Doe",
  "relationship": "DAUGHTER",
  "age": 22,
  "idNumber": "ID-55210",
  "phoneNumber": "+211900000001",
  "gender": "FEMALE"
}`

const requestOtpExample = `{
  "processingCode": "REQUEST_PAYROLL_OTP",
  "action": "GENERATE",
  "actorEmail": "you@yourorganisation.org"
}`

const generatePayrollExample = `{
  "processingCode": "GENERATE_PAYROLL",
  "organisationCode": "ORG001",
  "periodStart": "2026-09-01",
  "periodEnd": "2026-09-30",
  "amountPerHousehold": 5000,
  "currency": "USD",
  "exchangeRate": 1,
  "householdNumbers": ["HH00019", "HH00020", "HH00021"],
  "otpCode": "482913"
}`

const updatePaymentExample = `{
  "processingCode": "UPDATE_PAYMENT_STATUS",
  "paymentReference": "PMT00042",
  "status": "PAID"
}`

const householdFields = [
  { field: 'organisationCode', required: true, notes: 'Your organisation code (Organisation accounts can omit it; it is inferred from your login).' },
  { field: 'householdName', required: true, notes: 'Full name of the household head.' },
  { field: 'age', required: false, notes: 'Whole number.' },
  { field: 'gender', required: false, notes: 'e.g. MALE / FEMALE.' },
  { field: 'maritalStatus', required: false, notes: 'e.g. SINGLE / MARRIED / WIDOWED.' },
  { field: 'idNumber', required: false, notes: 'National ID or equivalent.' },
  { field: 'phoneNumber', required: false, notes: 'Include country code.' },
  { field: 'householdSize', required: false, notes: 'Total members in the household.' },
  { field: 'femaleDependants / maleDependants', required: false, notes: 'Whole numbers.' },
  { field: 'stateCode / countyCode / payamCode / bomaCode', required: false, notes: 'Codes from GET_HOUSEHOLD_LOCATIONS (state/county/location/village) — see Reference lookups below.' },
]

const payrollFields = [
  { field: 'organisationCode', required: true, notes: 'Required for Organisation accounts; Anchor/System accounts pass it explicitly.' },
  { field: 'periodStart / periodEnd', required: true, notes: 'ISO dates, e.g. "2026-09-01".' },
  { field: 'amountPerHousehold', required: true, notes: 'Payout amount per household, in the payout currency.' },
  { field: 'currency', required: true, notes: 'e.g. USD, SSP, KES, UGX, ETB, EUR, GBP.' },
  { field: 'exchangeRate', required: true, notes: 'USD exchange rate for the chosen currency; use 1 for USD.' },
  { field: 'householdNumbers', required: true, notes: 'Array of household numbers to include in this cycle.' },
  { field: 'otpCode', required: true, notes: 'From REQUEST_PAYROLL_OTP — emailed to the account generating the cycle.' },
]
</script>

<template>
  <div>
    <div class="mb-4">
      <h1 class="text-h5 font-weight-bold">API Documentation</h1>
      <p class="text-caption text-medium-emphasis mb-0">
        Integrate an external system with BioPay: upload households, generate payment cycles, add alternates,
        and read back records using the same REST API the dashboard itself uses.
      </p>
    </div>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-2">How it works</h2>
        <p class="text-body-2 mb-3">
          There is no separate "developer" API — every integration calls the exact same two endpoints the web
          dashboard uses, sending a <code>processingCode</code> that names the action. Sign in once to get a token,
          then send that token with every request.
        </p>
        <v-table density="compact" class="mb-3">
          <thead>
            <tr>
              <th>Endpoint</th>
              <th>Auth</th>
              <th>Purpose</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>POST {{ baseUrl }}/authentication</code></td>
              <td>None</td>
              <td>Login and token refresh only.</td>
            </tr>
            <tr>
              <td><code>POST {{ baseUrl }}/api/v1/req</code></td>
              <td>Bearer token</td>
              <td>Every other action — households, alternates, payments, payroll, lookups.</td>
            </tr>
          </tbody>
        </v-table>
        <v-alert type="info" variant="tonal" density="compact">
          Every response is JSON with a <code>responseCode</code> ("000" means success) and a <code>responseMessage</code>.
          Any other code is an error — read <code>responseMessage</code> for why.
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-2">1. Authenticate</h2>
        <p class="text-body-2 mb-2">Log in with your dashboard account credentials to get an access token and a refresh token.</p>
        <div class="code-block mb-2">
          <pre>{{ loginExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(loginExample)" />
        </div>
        <p class="text-caption text-medium-emphasis mb-1">Response:</p>
        <div class="code-block mb-3">
          <pre>{{ loginResponseExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(loginResponseExample)" />
        </div>
        <p class="text-body-2 mb-2">
          Access tokens expire; use the refresh token to get a new one without asking the user to log in again.
          A refresh token is rotated on every use — always store the newest one returned.
        </p>
        <div class="code-block">
          <pre>{{ refreshExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(refreshExample)" />
        </div>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-2">2. Call the API</h2>
        <p class="text-body-2 mb-2">
          Every other action is one POST to <code>/api/v1/req</code> with your access token in the
          <code>Authorization</code> header and a <code>processingCode</code> in the body:
        </p>
        <div class="code-block">
          <pre>{{ envelopeExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(envelopeExample)" />
        </div>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-3">Upload households</h2>

        <h3 class="text-body-1 font-weight-medium mb-1">Single household — <code>CREATE_HOUSEHOLD</code></h3>
        <div class="code-block mb-3">
          <pre>{{ createHouseholdExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(createHouseholdExample)" />
        </div>

        <h3 class="text-body-1 font-weight-medium mb-1">Bulk upload (up to 1000 rows, one village per file) — <code>BULK_UPLOAD_HOUSEHOLDS</code></h3>
        <p class="text-body-2 mb-2">
          This is the same action used by the dashboard's Excel bulk-upload — every row shares the
          <code>villageCode</code> given at the top level.
        </p>
        <div class="code-block mb-3">
          <pre>{{ bulkUploadExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(bulkUploadExample)" />
        </div>

        <v-table density="compact">
          <thead>
            <tr>
              <th>Field</th>
              <th>Required</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="f in householdFields" :key="f.field">
              <td><code>{{ f.field }}</code></td>
              <td>{{ f.required ? 'Yes' : 'No' }}</td>
              <td>{{ f.notes }}</td>
            </tr>
          </tbody>
        </v-table>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-2">Add alternates</h2>
        <p class="text-body-2 mb-2">
          An alternate is a backup person who can collect payment on a household's behalf —
          <code>CREATE_ALTERNATE</code> attaches one to an existing household.
        </p>
        <div class="code-block">
          <pre>{{ createAlternateExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(createAlternateExample)" />
        </div>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-3">Upload payments (generate a payment cycle)</h2>
        <p class="text-body-2 mb-2">
          Payments are created by generating a payment cycle for a set of households — the same flow as
          Payment Generation in the dashboard. It's two calls: request a verification code, then generate
          using that code.
        </p>
        <h3 class="text-body-1 font-weight-medium mb-1">Step 1 — <code>REQUEST_PAYROLL_OTP</code></h3>
        <div class="code-block mb-3">
          <pre>{{ requestOtpExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(requestOtpExample)" />
        </div>
        <p class="text-body-2 mb-2">A 6-digit code is emailed to the account making the request. Use it within a few minutes.</p>

        <h3 class="text-body-1 font-weight-medium mb-1">Step 2 — <code>GENERATE_PAYROLL</code></h3>
        <div class="code-block mb-3">
          <pre>{{ generatePayrollExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(generatePayrollExample)" />
        </div>

        <v-table density="compact" class="mb-4">
          <thead>
            <tr>
              <th>Field</th>
              <th>Required</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="f in payrollFields" :key="f.field">
              <td><code>{{ f.field }}</code></td>
              <td>{{ f.required ? 'Yes' : 'No' }}</td>
              <td>{{ f.notes }}</td>
            </tr>
          </tbody>
        </v-table>

        <h3 class="text-body-1 font-weight-medium mb-1">Update an individual payment — <code>UPDATE_PAYMENT_STATUS</code></h3>
        <div class="code-block">
          <pre>{{ updatePaymentExample }}</pre>
          <v-btn size="x-small" variant="text" icon="mdi-content-copy" class="copy-btn" @click="copy(updatePaymentExample)" />
        </div>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border>
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-2">Reading data back</h2>
        <p class="text-body-2 mb-2">Use these read-only codes to look up records or the location codes needed above.</p>
        <v-table density="compact">
          <thead>
            <tr>
              <th>processingCode</th>
              <th>Returns</th>
            </tr>
          </thead>
          <tbody>
            <tr><td><code>GET_HOUSEHOLDS</code></td><td>Paginated household list, filterable by organisation/location/status.</td></tr>
            <tr><td><code>GET_HOUSEHOLD</code></td><td>One household, with alternates/fingerprint/image status.</td></tr>
            <tr><td><code>GET_ALTERNATES</code></td><td>Alternates for a household.</td></tr>
            <tr><td><code>GET_PAYMENTS</code></td><td>Individual payment records, filterable by cycle/status.</td></tr>
            <tr><td><code>GET_PAYROLLS</code> / <code>GET_PAYROLL</code></td><td>Payment cycles list / one cycle with its line items.</td></tr>
            <tr><td><code>GET_HOUSEHOLD_LOCATIONS</code></td><td>State/county/location/village codes to use as <code>stateCode</code>/<code>countyCode</code>/<code>payamCode</code>/<code>bomaCode</code> and <code>villageCode</code> above (pass <code>level</code>: STATE, COUNTY, LOCATION or VILLAGE).</td></tr>
            <tr><td><code>GET_ORGANIZATIONS</code></td><td>Organisation codes and names under your anchor.</td></tr>
          </tbody>
        </v-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
h2, h3 { color: rgb(var(--v-theme-on-surface)); }
.code-block {
  position: relative;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 12px 40px 12px 14px;
  overflow-x: auto;
}
.code-block pre { margin: 0; font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace; font-size: .78rem; white-space: pre; }
.copy-btn { position: absolute; top: 4px; right: 4px; color: #e2e8f0 !important; }
code { background: rgba(15, 23, 42, .06); padding: 1px 5px; border-radius: 4px; font-size: .85em; }
</style>
