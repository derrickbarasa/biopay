import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { routeNavigating } from '@/composables/useRouteProgress'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/pages/LandingPage.vue'),
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/pages/LoginPage.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('@/pages/SignupPage.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/pages/ForgotPasswordPage.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/pages/ResetPasswordPage.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/verify-otp',
      name: 'verify-otp',
      component: () => import('@/pages/VerifyOtpPage.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/app',
      component: () => import('@/layouts/DefaultLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'dashboard' } },
        { path: 'access-denied', name: 'access-denied', component: () => import('@/pages/AccessDeniedPage.vue') },
        { path: 'dashboard', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue'), meta: { permission: 'VIEW_REPORTS' } },
        { path: 'anchors', name: 'anchors', component: () => import('@/pages/AnchorsPage.vue'), meta: { roles: ['ANCHOR'], systemOnly: true } },
        { path: 'users', name: 'users', component: () => import('@/pages/UsersPage.vue'), meta: { roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_USERS' } },
        { path: 'roles', name: 'roles', component: () => import('@/pages/RolesPage.vue'), meta: { roles: ['ANCHOR'], permission: 'ACCESS_ROLES' } },
        {
          path: 'organizations',
          name: 'organizations',
          component: () => import('@/pages/OrganizationsPage.vue'),
          meta: { roles: ['ANCHOR'], permission: 'ACCESS_ORGANISATIONS' },
        },
        {
          path: 'households',
          name: 'households',
          component: () => import('@/pages/HouseholdsPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS', permission: 'ACCESS_HOUSEHOLDS' },
        },
        {
          path: 'households/:householdNumber',
          name: 'household-detail',
          component: () => import('@/pages/HouseholdDetailPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS', permission: 'ACCESS_HOUSEHOLDS' },
        },
        {
          path: 'officers',
          name: 'officers',
          component: () => import('@/pages/OfficersPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_SUPERVISORS' },
        },
        {
          path: 'payments',
          name: 'payments',
          component: () => import('@/pages/PaymentsPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS', permission: 'ACCESS_PAYMENTS' },
        },
        {
          path: 'payroll',
          name: 'payroll',
          component: () => import('@/pages/PayrollPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS', permission: 'ACCESS_PAYMENT_CYCLES' },
        },
        {
          path: 'vouchers',
          name: 'vouchers',
          component: () => import('@/pages/VouchersPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'VOUCHERS', permission: 'ACCESS_VOUCHERS' },
        },
        {
          path: 'locations',
          name: 'locations',
          component: () => import('@/pages/LocationsPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_LOCATIONS' },
        },
        {
          path: 'attendance',
          name: 'attendance',
          component: () => import('@/pages/AttendancePage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_ATTENDANCE' },
        },
        { path: 'settings', name: 'settings', component: () => import('@/pages/SettingsPage.vue') },
        { path: 'subscription', name: 'subscription', component: () => import('@/pages/SubscriptionPage.vue'), meta: { roles: ['ANCHOR'], anchorSubscription: true, permission: 'ACCESS_SUBSCRIPTION' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'home' } },
  ],
})

// Fires before the target route's lazy chunk is even requested, so the
// top progress bar appears the instant a click registers rather than only
// once the (potentially slow) chunk fetch + data load finish.
router.beforeEach(() => {
  routeNavigating.value = true
  return true
})

router.afterEach(() => {
  routeNavigating.value = false
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresGuest && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const allowedRoles = to.meta.roles as string[] | undefined
  if (allowedRoles && auth.role && !auth.isSystemAdmin && !allowedRoles.includes(auth.role)) {
    return { name: 'access-denied' }
  }
  const requiredModule = to.meta.module as string | undefined
  if (requiredModule && !auth.hasModule(requiredModule)) {
    return { name: 'access-denied' }
  }
  const requiredPermission = to.meta.permission as string | undefined
  if (requiredPermission && !auth.can(requiredPermission)) return { name: 'access-denied' }
  if (to.meta.systemOnly && !auth.isSystemAdmin) return { name: 'access-denied' }
  if (to.meta.anchorSubscription && !auth.isAnchorAdministrator && !auth.isSystemAdmin) return { name: 'access-denied' }
  return true
})

// A browser can retain an old lazy-route URL after the dev server restarts or a
// new production build is deployed. Vue Router otherwise leaves the current
// view blank when that module fetch fails. Reload the requested application
// route once so the browser receives the current HTML and asset manifest.
let recoveringLazyRoute = false
router.onError((error, to) => {
  routeNavigating.value = false
  const message = error instanceof Error ? error.message : String(error)
  const isLazyRouteFailure = /Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk [\d]+ failed|ChunkLoadError/i.test(message)

  if (isLazyRouteFailure && !recoveringLazyRoute && to.fullPath.startsWith('/app')) {
    recoveringLazyRoute = true
    window.location.assign(to.fullPath)
  }
})

export default router
