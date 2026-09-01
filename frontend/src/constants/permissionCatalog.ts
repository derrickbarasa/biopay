export interface PermissionGroupDefinition {
  key: string
  label: string
  description: string
  icon: string
  permissions: string[]
}

export const PERMISSION_GROUPS: PermissionGroupDefinition[] = [
  {
    key: 'REPORTS', label: 'Reports', icon: 'mdi-chart-box-outline',
    description: 'Control who can see dashboard reports and download them.',
    permissions: ['VIEW_REPORTS', 'DOWNLOAD_REPORTS'],
  },
  {
    key: 'HOUSEHOLDS_ALTERNATES', label: 'Households & alternates', icon: 'mdi-home-group',
    description: 'Choose the operational areas this role can open and work in.',
    permissions: ['ACCESS_HOUSEHOLDS', 'ACCESS_ALTERNATES', 'ACCESS_PAYMENTS', 'PAY_ONLINE', 'ACCESS_PAYMENT_CYCLES', 'ACCESS_VOUCHERS', 'ACCESS_ATTENDANCE'],
  },
  {
    key: 'USER_MANAGEMENT', label: 'User management', icon: 'mdi-account-cog-outline',
    description: 'Control access to users, roles, permissions and field officers.',
    permissions: ['ACCESS_USERS', 'ACCESS_ROLES', 'ACCESS_PERMISSIONS', 'ACCESS_SUPERVISORS'],
  },
  {
    key: 'SYSTEM_SETUP', label: 'System setup', icon: 'mdi-tune-variant',
    description: 'Organisation setup, locations and anchor subscription access.',
    permissions: ['ACCESS_ORGANISATIONS', 'ACCESS_LOCATIONS', 'ACCESS_SUBSCRIPTION'],
  },
]

const LEGACY_CODES = new Set([
  'DASHBOARD', 'REPORTS', 'DOWNLOAD_HOUSEHOLDS', 'HOUSEHOLDS', 'USER_MANAGEMENT',
  'MANAGE_ORGANISATIONS', 'MANAGE_USERS', 'MANAGE_ROLES', 'MANAGE_OFFICERS',
  'MANAGE_HOUSEHOLDS', 'MANAGE_PAYMENTS', 'MANAGE_VOUCHERS',
  'VIEW_DASHBOARD', 'EXPORT_REPORTS', 'VIEW_ORGANISATIONS', 'CREATE_ORGANISATIONS',
  'EDIT_ORGANISATIONS', 'DELETE_ORGANISATIONS', 'MANAGE_ORGANISATION_STATUS',
  'VIEW_USERS', 'CREATE_USERS', 'EDIT_USERS', 'MANAGE_USER_STATUS', 'VIEW_ROLES',
  'CREATE_ROLES', 'EDIT_ROLES', 'VIEW_OFFICERS', 'CREATE_OFFICERS', 'EDIT_OFFICERS',
  'DELETE_OFFICERS', 'MANAGE_OFFICER_STATUS', 'ASSIGN_OFFICER_LOCATIONS',
  'VIEW_LOCATIONS', 'CREATE_LOCATIONS', 'EDIT_LOCATIONS', 'DELETE_LOCATIONS', 'IMPORT_LOCATIONS',
  'VIEW_HOUSEHOLDS', 'CREATE_HOUSEHOLDS', 'EDIT_HOUSEHOLDS', 'REVIEW_HOUSEHOLDS',
  'DELETE_HOUSEHOLDS', 'IMPORT_HOUSEHOLDS', 'EXPORT_HOUSEHOLDS', 'VIEW_ALTERNATES',
  'CREATE_ALTERNATES', 'EDIT_ALTERNATES', 'EXPORT_ALTERNATES', 'DELETE_ALTERNATES',
  'VIEW_PAYMENTS', 'MARK_PAYMENTS_PAID', 'DELETE_PAYMENTS', 'EXPORT_PAYMENTS',
  'VIEW_PAYMENT_CYCLES', 'CREATE_PAYMENT_CYCLES', 'APPROVE_PAYMENT_CYCLES',
  'REJECT_PAYMENT_CYCLES', 'DISBURSE_PAYMENT_CYCLES', 'DELETE_PAYMENT_CYCLES',
  'VIEW_VOUCHERS', 'ISSUE_VOUCHERS', 'BULK_ISSUE_VOUCHERS', 'REDEEM_VOUCHERS',
  'VOID_VOUCHERS', 'VIEW_ATTENDANCE', 'EXPORT_ATTENDANCE', 'RECORD_ATTENDANCE',
  'VIEW_SUBSCRIPTION', 'MANAGE_SUBSCRIPTION',
])

export function normalizePermissionCode(code: string): string {
  return code.trim().toUpperCase().replaceAll(' ', '_')
}

export function isLegacyPermission(code: string): boolean {
  return LEGACY_CODES.has(normalizePermissionCode(code))
}

export function permissionActionLabel(code: string): string {
  return code.toLowerCase().replaceAll('_', ' ').replace(/^access /, '').replace(/^./, (letter) => letter.toUpperCase())
}
