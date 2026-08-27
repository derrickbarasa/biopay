import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { dispatch } from '@/api/client'
import { clearSession, readStoredUser, storeSession, storedToken } from '@/api/client'
import type { OtpMethod, PendingLogin, SessionUser, UserRole } from '@/types/user'
import { normalizePermissionCode } from '@/constants/permissionCatalog'

interface SessionResponse {
  responseCode: string
  responseMessage: string
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: SessionUser
}

type LoginResponse = PendingLogin | SessionResponse

function isSessionResponse(data: LoginResponse): data is SessionResponse {
  return 'accessToken' in data && 'refreshToken' in data && 'user' in data
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(storedToken())
  const user = ref<SessionUser | null>(readStoredUser<SessionUser>())
  // The OTP step's in-flight state -- deliberately in-memory only (not persisted), so a page
  // refresh mid-verification just means starting the login over rather than an OTP-pending
  // token sitting in localStorage for its whole (short) lifetime.
  const pendingLogin = ref<PendingLogin | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const role = computed<UserRole | null>(() => user.value?.role ?? null)
  const isSystemAdmin = computed(() => !!user.value?.systemAdmin || role.value === 'SYSTEM')
  const isAnchorAdministrator = computed(() => role.value === 'ANCHOR' && !isSystemAdmin.value)
  // Existing tenant-management screens use isAnchor to mean cross-organisation access.
  // The separately exposed isAnchorAdministrator remains strict.
  const isAnchor = computed(() => isAnchorAdministrator.value || isSystemAdmin.value)
  const isOrganisation = computed(() => role.value === 'ORGANISATION')
  const isSupervisor = computed(() => role.value === 'SUPERVISOR')

  const fullName = computed(() => {
    if (!user.value) return 'User'
    const name = [user.value.firstName, user.value.lastName ?? user.value.otherNames].filter(Boolean).join(' ')
    return name || user.value.email.split('@')[0]
  })

  const initials = computed(() => {
    const u = user.value
    if (!u) return 'U'
    const a = (u.firstName ?? u.email)[0]
    const b = (u.lastName ?? u.otherNames ?? '')[0]
    return (a + (b ?? '')).toUpperCase()
  })

  const roleLabel = computed(() => {
    if (isSystemAdmin.value) return 'Super Admin'
    switch (role.value) {
      case 'ANCHOR': return 'Anchor Administrator'
      case 'ORGANISATION': return 'Organisation Administrator'
      case 'SUPERVISOR': return 'Field Officer'
      default: return 'User'
    }
  })

  function can(permission: string): boolean {
    if (isSystemAdmin.value) return true
    const granted = new Set((user.value?.permissions ?? []).map(normalizePermissionCode))
    const aliases: Record<string, string[]> = {
      VIEW_REPORTS: ['VIEW_REPORTS', 'VIEW_DASHBOARD', 'DASHBOARD', 'REPORTS'],
      DOWNLOAD_REPORTS: ['DOWNLOAD_REPORTS', 'EXPORT_REPORTS', 'DOWNLOAD_HOUSEHOLDS', 'EXPORT_HOUSEHOLDS', 'EXPORT_ALTERNATES', 'EXPORT_PAYMENTS', 'EXPORT_ATTENDANCE'],
      ACCESS_HOUSEHOLDS: ['ACCESS_HOUSEHOLDS', 'HOUSEHOLDS', 'MANAGE_HOUSEHOLDS', 'VIEW_HOUSEHOLDS'],
      ACCESS_ALTERNATES: ['ACCESS_ALTERNATES', 'HOUSEHOLDS', 'MANAGE_HOUSEHOLDS', 'VIEW_ALTERNATES'],
      ACCESS_PAYMENTS: ['ACCESS_PAYMENTS', 'MANAGE_PAYMENTS', 'VIEW_PAYMENTS'],
      ACCESS_PAYMENT_CYCLES: ['ACCESS_PAYMENT_CYCLES', 'MANAGE_PAYMENTS', 'VIEW_PAYMENT_CYCLES'],
      ACCESS_VOUCHERS: ['ACCESS_VOUCHERS', 'MANAGE_VOUCHERS', 'VIEW_VOUCHERS'],
      ACCESS_ATTENDANCE: ['ACCESS_ATTENDANCE', 'VIEW_ATTENDANCE', 'RECORD_ATTENDANCE', 'MANAGE_OFFICERS'],
      ACCESS_USERS: ['ACCESS_USERS', 'USER_MANAGEMENT', 'MANAGE_USERS', 'VIEW_USERS'],
      ACCESS_ROLES: ['ACCESS_ROLES', 'MANAGE_ROLES', 'VIEW_ROLES'],
      ACCESS_PERMISSIONS: ['ACCESS_PERMISSIONS', 'MANAGE_ROLES'],
      ACCESS_SUPERVISORS: ['ACCESS_SUPERVISORS', 'MANAGE_OFFICERS', 'VIEW_OFFICERS'],
      ACCESS_ORGANISATIONS: ['ACCESS_ORGANISATIONS', 'MANAGE_ORGANISATIONS', 'VIEW_ORGANISATIONS'],
      ACCESS_LOCATIONS: ['ACCESS_LOCATIONS', 'VIEW_LOCATIONS', 'MANAGE_ORGANISATIONS', 'MANAGE_HOUSEHOLDS'],
      ACCESS_SUBSCRIPTION: ['ACCESS_SUBSCRIPTION', 'VIEW_SUBSCRIPTION', 'MANAGE_SUBSCRIPTION'],
    }
    const accepted = aliases[permission] ?? [permission]
    if (accepted.some((code) => granted.has(code))) return true
    // Subscription was historically available to every anchor and had no permission row.
    return permission === 'ACCESS_SUBSCRIPTION' && isAnchor.value
  }

  /** Anchors implicitly have every module; organisation/supervisor sessions are
   *  limited to whatever was enabled for their organisation (see OrgModules on the backend). */
  function hasModule(module: string): boolean {
    if (isAnchor.value) return true
    return user.value?.enabledModules?.includes(module as any) ?? false
  }

  function applySession(data: SessionResponse) {
    token.value = data.accessToken
    user.value = data.user
    storeSession(data.accessToken, data.refreshToken, data.user)
    pendingLogin.value = null
  }

  /** Returns true when the server requires the OTP page, or false when the current
   *  environment explicitly permits password-only login and returned a full session. */
  async function login(email: string, password: string): Promise<boolean> {
    const data = await dispatch<LoginResponse>('LOGIN_USER', { email, password })
    if (isSessionResponse(data)) {
      applySession(data)
      return false
    }
    pendingLogin.value = data
    return true
  }

  /** Account creation follows the server's configured OTP mode too. */
  async function signup(fields: { name: string; authorisedName?: string; email: string; phone?: string; address?: string; password: string }): Promise<boolean> {
    const data = await dispatch<LoginResponse>('SIGNUP_ANCHOR', fields)
    if (isSessionResponse(data)) {
      applySession(data)
      return false
    }
    pendingLogin.value = data
    return true
  }

  /** EMAIL sends a fresh code; TOTP has nothing to send (the code comes from the user's app). */
  async function requestLoginOtp(method: OtpMethod) {
    if (!pendingLogin.value) throw new Error('Your session has expired. Please log in again')
    await dispatch('REQUEST_LOGIN_OTP', { pendingToken: pendingLogin.value.pendingToken, method })
  }

  /** The actual login completion -- on success the real session is issued and stored. */
  async function verifyLoginOtp(method: OtpMethod, code: string) {
    if (!pendingLogin.value) throw new Error('Your session has expired. Please log in again')
    const data = await dispatch<SessionResponse>('VERIFY_LOGIN_OTP', {
      pendingToken: pendingLogin.value.pendingToken, method, code,
    })
    applySession(data)
    return data.user
  }

  function cancelPendingLogin() {
    pendingLogin.value = null
  }

  async function requestPasswordReset(email: string) {
    await dispatch('REQUEST_PASSWORD_RESET', { email })
  }

  async function resetPassword(resetToken: string, newPassword: string) {
    await dispatch('RESET_PASSWORD', { token: resetToken, newPassword })
  }

  async function logout() {
    const refreshToken = localStorage.getItem('bp_refresh')
    try {
      if (refreshToken) await dispatch('LOGOUT', { refreshToken })
    } catch {
      // Best-effort server-side revoke; always clear the local session regardless.
    }
    token.value = null
    user.value = null
    pendingLogin.value = null
    clearSession()
  }

  async function refreshProfile() {
    if (!token.value) return
    const data = await dispatch<{ user: SessionUser }>('ME')
    user.value = data.user
    localStorage.setItem('bp_user', JSON.stringify(data.user))
  }

  async function updateProfile(firstName: string, lastName: string) {
    await dispatch('UPDATE_PROFILE', { firstName, lastName })
    await refreshProfile()
  }

  return {
    token, user, pendingLogin, isAuthenticated, role, isAnchor, isAnchorAdministrator, isOrganisation, isSupervisor, isSystemAdmin,
    fullName, initials, roleLabel, can, hasModule,
    login, signup, requestLoginOtp, verifyLoginOtp, cancelPendingLogin, requestPasswordReset, resetPassword,
    logout, refreshProfile, updateProfile,
  }
})
