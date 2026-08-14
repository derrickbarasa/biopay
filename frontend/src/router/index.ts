import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

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
        { path: 'dashboard', name: 'dashboard', component: () => import('@/pages/DashboardPage.vue') },
        {
          path: 'organizations',
          name: 'organizations',
          component: () => import('@/pages/OrganizationsPage.vue'),
          meta: { roles: ['ANCHOR'] },
        },
        {
          path: 'households',
          name: 'households',
          component: () => import('@/pages/HouseholdsPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS' },
        },
        {
          path: 'officers',
          name: 'officers',
          component: () => import('@/pages/OfficersPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'] },
        },
        {
          path: 'payments',
          name: 'payments',
          component: () => import('@/pages/PaymentsPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
        },
        {
          path: 'payroll',
          name: 'payroll',
          component: () => import('@/pages/PayrollPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
        },
        {
          path: 'vouchers',
          name: 'vouchers',
          component: () => import('@/pages/VouchersPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'], module: 'VOUCHERS' },
        },
        {
          path: 'locations',
          name: 'locations',
          component: () => import('@/pages/LocationsPage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'] },
        },
        {
          path: 'attendance',
          name: 'attendance',
          component: () => import('@/pages/AttendancePage.vue'),
          meta: { roles: ['ANCHOR', 'ORGANISATION'] },
        },
        { path: 'settings', name: 'settings', component: () => import('@/pages/SettingsPage.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'home' } },
  ],
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
  if (allowedRoles && auth.role && !allowedRoles.includes(auth.role)) {
    return { name: 'dashboard' }
  }
  const requiredModule = to.meta.module as string | undefined
  if (requiredModule && !auth.hasModule(requiredModule)) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
