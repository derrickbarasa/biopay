export type UserRole = 'ANCHOR' | 'ORGANISATION' | 'SUPERVISOR'

/** Mirrors com.biopay.utilities.OrgModules -- the modules an organisation can be
 *  registered with, chosen at CREATE_ORGANIZATION and editable via UPDATE_ORGANIZATION_MODULES. */
export type OrgModule = 'HOUSEHOLDS' | 'ALTERNATES' | 'CASH_TRANSFERS' | 'VOUCHERS' | 'FOOD_DISTRIBUTION'

export const ORG_MODULES: { code: OrgModule; label: string }[] = [
  { code: 'HOUSEHOLDS', label: 'Household Registration' },
  { code: 'ALTERNATES', label: 'Alternates Registration' },
  { code: 'CASH_TRANSFERS', label: 'Cash Transfers' },
  { code: 'VOUCHERS', label: 'Voucher Redemption' },
  { code: 'FOOD_DISTRIBUTION', label: 'Food Distribution' },
]

/** ISO-ish common country list for the organisation form's Country dropdown -- kept short and
 *  regionally relevant rather than exhaustive; "Other" always lets a form move forward. */
export const COUNTRIES: string[] = [
  'South Sudan', 'Kenya', 'Uganda', 'Ethiopia', 'Sudan', 'Somalia', 'Tanzania', 'Rwanda',
  'Democratic Republic of the Congo', 'Nigeria', 'Chad', 'Central African Republic', 'Other',
]

export type VerificationMethod = 'BIOMETRIC' | 'FACIAL'

export interface SessionUser {
  id: number
  email: string
  firstName?: string
  lastName?: string
  otherNames?: string
  role: UserRole
  anchorId?: number | null
  partnerCode?: string | null
  permissions?: string[]
  enabledModules?: OrgModule[]
  totpEnabled?: boolean
  /** The one designated cross-anchor operator (admin@biopay.com) -- sees every anchor's
   *  organisations/households/payments instead of being scoped to just their own. */
  systemAdmin?: boolean
}

/** OTP method offered on the "choose a verification method" step after password login. */
export type OtpMethod = 'EMAIL' | 'TOTP'

/** Returned by LOGIN_USER / SIGNUP_ANCHOR once the password check passes -- not a session yet,
 *  just enough to drive the OTP step (see VERIFY_LOGIN_OTP, which is what actually logs in). */
export interface PendingLogin {
  pendingToken: string
  methods: OtpMethod[]
  maskedEmail: string
}
