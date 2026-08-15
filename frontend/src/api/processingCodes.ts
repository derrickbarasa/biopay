/**
 * Every processingCode the backend's two dispatch routes accept. Public
 * codes go through POST /biopay/authentication (no Authorization header);
 * everything else goes through the JWT-protected POST /biopay/api/v1/req.
 * Mirrors com.biopay.main.EntryPoint's `publicCodes` array exactly.
 */
export const PUBLIC_PROCESSING_CODES = new Set([
  'LOGIN_USER',
  'LOGIN_SUPERVISOR',
  'REFRESH_TOKEN',
  'REQUEST_LOGIN_OTP',
  'VERIFY_LOGIN_OTP',
  'SIGNUP_ANCHOR',
  'REQUEST_PASSWORD_RESET',
  'RESET_PASSWORD',
] as const)

export type ProcessingCode =
  // auth (public)
  | 'LOGIN_USER'
  | 'LOGIN_SUPERVISOR'
  | 'REFRESH_TOKEN'
  | 'REQUEST_LOGIN_OTP'
  | 'VERIFY_LOGIN_OTP'
  | 'SIGNUP_ANCHOR'
  | 'REQUEST_PASSWORD_RESET'
  | 'RESET_PASSWORD'
  // auth (authenticated)
  | 'LOGOUT'
  | 'CHANGE_PASSWORD'
  | 'ME'
  | 'TOTP_SETUP_INIT'
  | 'TOTP_SETUP_CONFIRM'
  | 'TOTP_DISABLE'
  // organizations
  | 'CREATE_ORGANIZATION'
  | 'UPDATE_ORGANIZATION'
  | 'DELETE_ORGANIZATION'
  | 'TOGGLE_ORGANIZATION_STATUS'
  | 'GET_ORGANIZATION'
  | 'GET_ORGANIZATIONS'
  // officers
  | 'CREATE_OFFICER'
  | 'UPDATE_OFFICER'
  | 'DELETE_OFFICER'
  | 'ASSIGN_OFFICER_LOCATION'
  | 'GET_OFFICER'
  | 'GET_OFFICERS'
  // households / alternates
  | 'CREATE_HOUSEHOLD'
  | 'UPDATE_HOUSEHOLD'
  | 'DELETE_HOUSEHOLD'
  | 'GET_HOUSEHOLD'
  | 'GET_HOUSEHOLDS'
  | 'GET_HOUSEHOLD_HISTORY'
  | 'GET_HOUSEHOLD_VOUCHER'
  | 'CHECK_HOUSEHOLD_DUPLICATE'
  | 'BULK_UPLOAD_HOUSEHOLDS'
  | 'CREATE_ALTERNATE'
  | 'UPDATE_ALTERNATE'
  | 'DELETE_ALTERNATE'
  | 'GET_ALTERNATES'
  // organisation modules
  | 'GET_ORGANIZATION_MODULES'
  | 'UPDATE_ORGANIZATION_MODULES'
  // subscription
  | 'GET_SUBSCRIPTION'
  | 'RENEW_SUBSCRIPTION'
  // geography (states / counties / locations / villages)
  | 'GET_STATES'
  | 'CREATE_STATE'
  | 'GET_COUNTIES'
  | 'CREATE_COUNTY'
  | 'GET_LOCATIONS'
  | 'CREATE_LOCATION'
  | 'GET_VILLAGES'
  | 'CREATE_VILLAGE'
  | 'UPDATE_GEO_NODE'
  | 'DELETE_GEO_NODE'
  // vouchers
  | 'CREATE_VOUCHER'
  | 'BULK_ISSUE_VOUCHERS'
  | 'GET_VOUCHERS'
  | 'GET_VOUCHER'
  | 'VOID_VOUCHER'
  | 'REDEEM_VOUCHER'
  | 'VOUCHER_SUMMARY'
  // payroll
  | 'REQUEST_PAYROLL_OTP'
  | 'GENERATE_PAYROLL'
  | 'APPROVE_PAYROLL'
  | 'REJECT_PAYROLL'
  | 'DISBURSE_PAYROLL'
  | 'DELETE_PAYROLL'
  | 'GET_PAYROLL'
  | 'GET_PAYROLLS'
  // payments
  | 'GET_PAYMENTS'
  | 'GET_PAYMENT'
  | 'UPDATE_PAYMENT_STATUS'
  | 'DELETE_PAYMENT'
  | 'PAYMENT_SUMMARY'
  // dashboard
  | 'DASHBOARD_METRICS'
  | 'DASHBOARD_PAYMENTS_CHART'
  | 'DASHBOARD_HOUSEHOLDS_CHART'
  // attendance
  | 'GET_ATTENDANCE'
  | 'RECORD_ATTENDANCE'
  // biometric / Android (rarely called from the web dashboard, listed for completeness)
  | 'UPLOAD_HOUSEHOLD_BIO'
  | 'UPLOAD_ALTERNATE_BIO'
  | 'ENROLL_FINGERPRINT'
  | 'VERIFY_FINGERPRINT'
  | 'SYNC_FINGERPRINTS'
  | 'UPLOAD_IMAGE'
  | 'SYNC_IMAGES'
  | 'SYNC_PAYMENTS'
  | 'RECORD_FIELD_PAYMENT'
  | 'BIOMETRIC_LOGIN'
