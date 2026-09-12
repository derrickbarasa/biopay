<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDisplay } from 'vuetify'
import { useAuthStore } from '@/stores/auth'
import { dispatch } from '@/api/client'
import { useIdleLogout } from '@/composables/useIdleLogout'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const { mdAndUp } = useDisplay()
const drawer = ref(mdAndUp.value)
const { showPrompt: showIdlePrompt, confirmStillHere, logoutNow } = useIdleLogout()

// Shrinks the whole authenticated shell (see .app-shell-scale in style.css) so
// the dashboard, sidebar, and tables fit an ordinary laptop viewport at 100%
// zoom without scrolling. Scoped to this layout's lifetime only.
onMounted(() => document.documentElement.classList.add('app-shell-scale'))
onUnmounted(() => document.documentElement.classList.remove('app-shell-scale'))

watch(mdAndUp, (isDesktop) => {
  drawer.value = isDesktop
})

// ---- Subscription lifecycle (per anchor) --------------------------------------
interface SubscriptionStatus {
  status: 'NONE' | 'ACTIVE' | 'GRACE' | 'ARCHIVED'
  expiresAt?: string
  daysToExpiry?: number
  daysToArchive?: number
  anchorActive?: boolean
}
const subscription = ref<SubscriptionStatus>({ status: 'NONE', anchorActive: true })

async function fetchSubscription() {
  if (auth.isSystemAdmin || !auth.user?.anchorId) return
  try {
    const res = await dispatch<{ results: SubscriptionStatus }>('GET_SUBSCRIPTION')
    subscription.value = res.results ?? { status: 'NONE', anchorActive: true }
  } catch {
    // Fail-open: never block the app because the status check itself failed.
    subscription.value = { status: 'NONE', anchorActive: true }
  }
}

function goToMakePayment() {
  router.push('/app/subscription/pay')
}

// The Subscription and Make Payment pages must stay reachable even when archived --
// otherwise there's no way to see invoices or reach the payment flow that unlocks
// everything else.
const isArchived = computed(() => !auth.isSystemAdmin && !!auth.user?.anchorId && subscription.value.status === 'ARCHIVED'
  && route.name !== 'subscription' && route.name !== 'subscription-pay')
const inGrace = computed(() => !auth.isSystemAdmin && !!auth.user?.anchorId && subscription.value.status === 'GRACE')

// A deactivated anchor's own admin account can't even log in (see Auth#loginUser), so this
// only ever fires for organisation/field-officer sessions whose anchor was deactivated after
// they were already signed in -- a distinct condition from subscription ARCHIVED (the anchor
// itself still functions there; here it's been switched off entirely).
const isAnchorDeactivated = computed(() => !auth.isSystemAdmin && !auth.isAnchorAdministrator
  && !!auth.user?.anchorId && subscription.value.anchorActive === false)

// An archived anchor administrator can only reach the Subscription/Make Payment pages
// (and Log out, which lives outside visibleSections) until they pay; the sidebar itself
// is trimmed down to match instead of just relying on the content-area gate below.
const navLockedToSubscription = computed(() => isArchived.value && auth.isAnchorAdministrator)

onMounted(fetchSubscription)

interface NavItem {
  title: string
  icon: string
  to: string
  roles?: string[]
  module?: string
  permission?: string
  systemOnly?: boolean
  anchorSubscription?: boolean
}

interface NavSection {
  title: string
  items: NavItem[]
}

// Grouped navigation: Dashboard / Biodata / Payment Generation / Configs / User management /
// Settings. Role + module gating is unchanged -- it is now applied per-item
// within each section (see visibleSections below).
const navSections: NavSection[] = [
  {
    title: '',
    items: [
      { title: 'Dashboard', icon: 'mdi-view-dashboard-outline', to: '/app/dashboard', permission: 'VIEW_REPORTS' },
    ],
  },
  {
    title: 'Biodata',
    items: [
      { title: 'Households', icon: 'mdi-home-group', to: '/app/households', roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS', permission: 'ACCESS_HOUSEHOLDS' },
      { title: 'Payments', icon: 'mdi-cash-multiple', to: '/app/payments', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS', permission: 'ACCESS_PAYMENTS' },
      { title: 'Attendance', icon: 'mdi-calendar-check', to: '/app/attendance', roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_ATTENDANCE' },
    ],
  },
  {
    title: 'Payment Generation',
    items: [
      { title: 'Payment Cycles', icon: 'mdi-calendar-month-outline', to: '/app/payroll', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS', permission: 'ACCESS_PAYMENT_CYCLES' },
      { title: 'Vouchers', icon: 'mdi-ticket-confirmation', to: '/app/vouchers', roles: ['ANCHOR', 'ORGANISATION'], module: 'VOUCHERS', permission: 'ACCESS_VOUCHERS' },
    ],
  },
  {
    title: 'Configs',
    items: [
      { title: 'Anchors', icon: 'mdi-bank-outline', to: '/app/anchors', roles: ['ANCHOR'], systemOnly: true },
      { title: 'Organizations', icon: 'mdi-domain', to: '/app/organizations', roles: ['ANCHOR'], permission: 'ACCESS_ORGANISATIONS' },
      { title: 'Locations', icon: 'mdi-map-marker-radius', to: '/app/locations', roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_LOCATIONS' },
      { title: 'API Documentation', icon: 'mdi-api', to: '/app/api-documentation', permission: 'ACCESS_API_DOCS' },
    ],
  },
  {
    title: 'Android App',
    items: [
      { title: 'App', icon: 'mdi-android', to: '/app/android-app', permission: 'ACCESS_ANDROID_APP' },
    ],
  },
  {
    title: 'User Management',
    items: [
      { title: 'Users', icon: 'mdi-account-multiple-outline', to: '/app/users', roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_USERS' },
      { title: 'API Access', icon: 'mdi-key-outline', to: '/app/api-access', roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_USERS' },
      { title: 'Field Officers', icon: 'mdi-account-tie', to: '/app/officers', roles: ['ANCHOR', 'ORGANISATION'], permission: 'ACCESS_SUPERVISORS' },
      { title: 'Roles & Permissions', icon: 'mdi-shield-account-outline', to: '/app/roles', roles: ['ANCHOR'], permission: 'ACCESS_ROLES' },
      { title: 'Subscription', icon: 'mdi-credit-card-outline', to: '/app/subscription', roles: ['ANCHOR'], anchorSubscription: true, permission: 'ACCESS_SUBSCRIPTION' },
      { title: 'Billing', icon: 'mdi-cash-check', to: '/app/billing', roles: ['ANCHOR'], systemOnly: true },
    ],
  },
  {
    title: '',
    items: [
      { title: 'Settings', icon: 'mdi-cog-outline', to: '/app/settings' },
    ],
  },
]

function itemVisible(item: NavItem): boolean {
  if (navLockedToSubscription.value) return item.to === '/app/subscription'
  return (!item.roles || auth.isSystemAdmin || (!!auth.role && item.roles.includes(auth.role)))
    && (!item.module || auth.hasModule(item.module))
    && (!item.permission || auth.can(item.permission))
    && (!item.systemOnly || auth.isSystemAdmin)
    && (!item.anchorSubscription || auth.isAnchorAdministrator || auth.isSystemAdmin)
}

// Only sections that still have at least one visible item are rendered, so a
// role that can see nothing under a group doesn't get an empty header.
const visibleSections = computed(() =>
  navSections
    .map((section) => ({ ...section, items: section.items.filter(itemVisible) }))
    .filter((section) => section.items.length > 0),
)

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

// A previous fix drove navigation from a plain @click handler while still
// passing `:to` for styling -- but `:to` makes Vuetify's own internal
// RouterLink/useLink wiring ALSO bind a click handler to the same element,
// so a single click fired two competing `router.push` calls. Vue Router
// cancels the first in-flight navigation when the second starts; when both
// targeted the same URL, the second was silently treated as a no-op against
// the (just-cancelled) first, so neither ever completed -- only a second,
// solitary click actually got through. The real fix is to stop giving
// v-list-item/v-btn a `:to` at all here, so Vuetify never wires its own
// navigation; `:active`/route-equality drives the styling instead, and this
// handler is the only thing that ever calls `router.push`.
function onNavClick(event: MouseEvent | KeyboardEvent, to: string) {
  event.preventDefault()
  if (route.path !== to) router.push(to)
}
</script>

<template>
  <v-navigation-drawer v-model="drawer" width="216" color="primary-darken-1" theme="dark" class="app-drawer">
    <div class="drawer-brand d-flex align-center">
      <img src="/biopay_logo_horizontal_light.svg" alt="BioPay — Biometric Payment Solutions" class="drawer-logo" />
    </div>
    <v-divider />

    <v-list nav density="comfortable" class="flex-grow-1">
      <template v-for="(section, i) in visibleSections" :key="i">
        <v-list-subheader v-if="section.title" class="drawer-subheader">{{ section.title }}</v-list-subheader>
        <v-list-item
          v-for="item in section.items"
          :key="item.to"
          :active="route.path === item.to"
          :prepend-icon="item.icon"
          :title="item.title"
          rounded="lg"
          color="primary"
          :ripple="false"
          role="link"
          @click="onNavClick($event, item.to)"
        />
      </template>
    </v-list>

    <template #append>
      <v-divider class="logout-divider" />
      <v-list nav density="comfortable">
        <v-list-item
          prepend-icon="mdi-logout"
          title="Log out"
          rounded="lg"
          base-color="error"
          @click="handleLogout"
        />
      </v-list>
    </template>
  </v-navigation-drawer>

  <v-app-bar color="surface" elevation="0" border density="compact" class="app-bar">
    <v-app-bar-nav-icon density="compact" aria-label="Toggle navigation" @click="drawer = !drawer" />
    <v-breadcrumbs :items="[{ title: $route.name?.toString() ?? '' }]" class="text-capitalize" density="compact" />
    <v-spacer />
    <v-chip class="role-chip mr-3 font-weight-bold" color="secondary" variant="tonal" size="small">{{ auth.roleLabel }}</v-chip>
    <v-menu>
      <template #activator="{ props }">
        <v-btn v-bind="props" variant="text" size="small" class="text-none">
          <v-avatar color="primary" size="28" class="mr-2">
            <span class="text-caption">{{ auth.initials }}</span>
          </v-avatar>
          <span class="user-name">{{ auth.fullName }}</span>
          <v-icon icon="mdi-chevron-down" class="ml-1" />
        </v-btn>
      </template>
      <v-list density="compact">
        <v-list-item
          :active="route.path === '/app/settings'" prepend-icon="mdi-account" title="Profile & Settings" :ripple="false" role="link"
          @click="onNavClick($event, '/app/settings')"
        />
        <v-divider />
        <v-list-item prepend-icon="mdi-logout" title="Log out" @click="handleLogout" />
      </v-list>
    </v-menu>
  </v-app-bar>

  <v-main class="dashboard-main">
    <v-container fluid class="pa-3 px-md-5 py-md-3">
      <v-dialog v-model="showIdlePrompt" max-width="420" persistent>
        <v-card>
          <dialog-close-button @close="confirmStillHere" />
          <v-card-title>Still there?</v-card-title>
          <v-card-text>
            You've been inactive for a while. For your security, you'll be signed out in 30 seconds
            unless you confirm you're still using this device.
          </v-card-text>
          <v-card-actions>
            <v-spacer />
            <v-btn variant="text" @click="logoutNow">Log out</v-btn>
            <v-btn color="secondary" @click="confirmStillHere">Yes, I'm still here</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>

      <!-- Grace period: an anchor remains usable for four days after expiry. -->
      <v-alert
        v-if="inGrace"
        type="warning" variant="tonal" class="mb-4" border="start"
        icon="mdi-clock-alert-outline"
      >
        <div class="d-flex align-center flex-wrap ga-2">
          <div>
            <strong>Subscription expired.</strong>
            You have {{ subscription.daysToArchive ?? 0 }} day(s) of grace left before data is archived.
          </div>
          <v-spacer />
          <v-btn v-if="auth.isAnchorAdministrator" color="warning" variant="flat" size="small" @click="goToMakePayment">
            Make payment
          </v-btn>
        </div>
      </v-alert>

      <!-- Anchor deactivated: distinct from subscription expiry -- the anchor itself was
           switched off by the platform owner, so there's no self-service remedy for anyone
           under it (org or field officer alike); both simply see "Contact your anchor". -->
      <div v-if="isAnchorDeactivated" class="archived-gate">
        <v-card variant="flat" border class="pa-8 text-center" max-width="520">
          <v-icon icon="mdi-domain-off" size="48" color="error" class="mb-3" />
          <p class="lockout-heading mb-2">Contact Your Anchor</p>
          <p class="text-body-2 text-medium-emphasis mb-4">This organisation's anchor has been deactivated. Access is restored once the anchor is reactivated.</p>
          <v-btn variant="text" size="small" prepend-icon="mdi-logout" @click="handleLogout">Log out</v-btn>
        </v-card>
      </div>

      <!-- Archived: grace exhausted -> gate access behind payment. Message and available
           actions differ by role: the anchor administrator can act directly; an organisation
           user is told to contact the anchor above them; a field officer is locked out of the
           dashboard entirely and told to contact their own organisation above them. -->
      <div v-else-if="isArchived" class="archived-gate">
        <v-card variant="flat" border class="pa-8 text-center" max-width="520">
          <v-icon icon="mdi-lock-clock" size="48" color="error" class="mb-3" />
          <h2 class="text-h6 font-weight-bold mb-2">Subscription expired</h2>

          <template v-if="auth.isAnchorAdministrator">
            <p class="text-body-2 text-medium-emphasis mb-4">
              The 4-day grace period has ended and your data is archived. Make a payment to restore access.
            </p>
            <v-btn color="secondary" @click="goToMakePayment">Make payment</v-btn>
            <div class="mt-4 d-flex ga-2 justify-center">
              <v-btn variant="text" size="small" prepend-icon="mdi-credit-card-outline" @click="onNavClick($event, '/app/subscription')">View subscription</v-btn>
              <v-btn variant="text" size="small" prepend-icon="mdi-logout" @click="handleLogout">Log out</v-btn>
            </div>
          </template>
          <template v-else-if="auth.isSupervisor">
            <p class="lockout-heading mb-2">Contact Your Org</p>
            <p class="text-body-2 text-medium-emphasis mb-4">
              Your organisation's anchor has an expired subscription, so field officer access is locked.
              Ask your organisation to contact their anchor to restore access.
            </p>
            <v-btn variant="text" size="small" prepend-icon="mdi-logout" @click="handleLogout">Log out</v-btn>
          </template>
          <template v-else>
            <p class="lockout-heading mb-2">Contact Your Anchor</p>
            <p class="text-body-2 text-medium-emphasis mb-4">
              This organisation's anchor has an expired subscription. Access is restored once the anchor makes a payment.
            </p>
            <v-btn variant="text" size="small" prepend-icon="mdi-logout" @click="handleLogout">Log out</v-btn>
          </template>
        </v-card>
      </div>

      <router-view v-else />
    </v-container>
  </v-main>
</template>

<style scoped>
.drawer-brand { min-height: 56px; margin: 8px; padding: 8px 10px; }
.drawer-logo { display: block; width: min(100%, 148px); height: auto; }
.app-drawer { border-right: 1px solid rgba(15, 118, 110, .65); background: #0f766e !important; font-size: .86rem; }
.app-drawer :deep(.v-list) { display: flex; flex-direction: column; padding-block: 4px; }
.app-drawer :deep(.v-list-item) { margin: 1px 8px; min-height: 36px; padding-inline: 10px; color: rgba(255, 255, 255, .86); }
.app-drawer :deep(.v-list-item-title) { font-weight: 500; font-size: .84rem; }
.app-drawer :deep(.v-list-item__prepend .v-icon) { font-size: 1.1rem; }
.app-drawer :deep(.v-list-item--active) { background: rgba(255, 255, 255, .16); color: #fff; }
.app-drawer :deep(.v-list-item--active .v-list-item-title) { font-weight: 600; }
.drawer-subheader { color: rgba(255, 255, 255, .68) !important; font-size: .64rem; letter-spacing: .08em; text-transform: uppercase; padding-inline-start: 18px !important; min-height: 28px !important; }
.app-drawer :deep(.v-divider) { border-color: rgba(255, 255, 255, .18); }
.app-drawer :deep(.logout-divider) { border-color: rgba(255, 255, 255, .42); opacity: 1; margin-top: 8px; }
.app-bar { background: rgba(255, 255, 255, .94) !important; backdrop-filter: blur(12px); }
.app-bar :deep(.v-breadcrumbs) { font-size: .82rem; padding-inline: 4px; }
.dashboard-main {
  height: 100dvh;
  min-height: 100vh;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  scrollbar-width: thin;
  scrollbar-color: #94a3b8 transparent;
  background: #f8fafc;
}
.dashboard-main::-webkit-scrollbar { display: block; width: 9px; }
.dashboard-main::-webkit-scrollbar-track { background: transparent; }
.dashboard-main::-webkit-scrollbar-thumb { border: 3px solid transparent; border-radius: 999px; background: #94a3b8; background-clip: padding-box; }
.dashboard-main::-webkit-scrollbar-thumb:hover { background: #64748b; background-clip: padding-box; }
.archived-gate { display: flex; justify-content: center; padding-top: 8vh; }
.lockout-heading { margin: 0; color: #0f172a; font-size: 1.6rem; font-weight: 800; letter-spacing: -.01em; }
@media (max-width: 600px) {
  .role-chip, .user-name { display: none; }
  .app-bar :deep(.v-breadcrumbs) { padding-inline: 8px; }
  .app-bar :deep(.v-btn) { min-width: 40px; padding-inline: 4px; }
}
</style>
