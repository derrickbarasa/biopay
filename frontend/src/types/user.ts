export type UserRole = 'ANCHOR' | 'ORGANISATION' | 'SUPERVISOR'

/** Mirrors com.biopay.utilities.OrgModules -- the modules an organisation can be
 *  registered with, chosen at CREATE_ORGANIZATION and editable via UPDATE_ORGANIZATION_MODULES. */
export type OrgModule = 'HOUSEHOLDS' | 'ALTERNATES' | 'CASH_TRANSFERS' | 'VOUCHERS'

export const ORG_MODULES: { code: OrgModule; label: string }[] = [
  { code: 'HOUSEHOLDS', label: 'Household Registration' },
  { code: 'ALTERNATES', label: 'Alternates Registration' },
  { code: 'CASH_TRANSFERS', label: 'Cash Transfers' },
  { code: 'VOUCHERS', label: 'Voucher Redemption' },
]

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
