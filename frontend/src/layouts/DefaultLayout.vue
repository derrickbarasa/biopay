<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDisplay } from 'vuetify'
import { useAuthStore } from '@/stores/auth'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { useIdleLogout } from '@/composables/useIdleLogout'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const toast = useToast()
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
}
const subscription = ref<SubscriptionStatus>({ status: 'NONE' })
const renewing = ref(false)

async function fetchSubscription() {
  try {
    const res = await dispatch<{ results: SubscriptionStatus }>('GET_SUBSCRIPTION')
    subscription.value = res.results ?? { status: 'NONE' }
  } catch {
    // Fail-open: never block the app because the status check itself failed.
    subscription.value = { status: 'NONE' }
  }
}

async function renewSubscription() {
  renewing.value = true
  try {
    await dispatch('RENEW_SUBSCRIPTION')
    toast.success('Subscription renewed')
    await fetchSubscription()
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Renewal failed')
  } finally {
    renewing.value = false
  }
}

// The Subscription page itself must stay reachable even when archived -- otherwise
// there's no way to see invoices or reach the renew action that unlocks everything else.
const isArchived = computed(() => subscription.value.status === 'ARCHIVED' && route.name !== 'subscription')
const inGrace = computed(() => subscription.value.status === 'GRACE')

onMounted(fetchSubscription)

interface NavItem {
  title: string
  icon: string
  to: string
  roles?: string[]
  module?: string
}

interface NavSection {
  title: string
  items: NavItem[]
}

// Grouped navigation: Dashboard / Configs / Transfers / Settings, matching the
// progress.md sidebar structure. Role + module gating is unchanged -- it is now
// applied per-item within each section (see visibleSections below).
const navSections: NavSection[] = [
  {
    title: '',
    items: [
      { title: 'Dashboard', icon: 'mdi-view-dashboard-outline', to: '/app/dashboard' },
    ],
  },
  {
    title: 'Configs',
    items: [
      { title: 'Anchor', icon: 'mdi-bank-outline', to: '/app/anchors', roles: ['ANCHOR'] },
      { title: 'Organizations', icon: 'mdi-domain', to: '/app/organizations', roles: ['ANCHOR'] },
      { title: 'Users', icon: 'mdi-account-multiple-outline', to: '/app/users', roles: ['ANCHOR', 'ORGANISATION'] },
      { title: 'Roles & Permissions', icon: 'mdi-shield-account-outline', to: '/app/roles', roles: ['ANCHOR'] },
      { title: 'Officers', icon: 'mdi-account-tie', to: '/app/officers', roles: ['ANCHOR', 'ORGANISATION'] },
      { title: 'Locations', icon: 'mdi-map-marker-radius', to: '/app/locations', roles: ['ANCHOR', 'ORGANISATION'] },
    ],
  },
  {
    title: 'Transfers',
    items: [
      { title: 'Households', icon: 'mdi-home-group', to: '/app/households', roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS' },
      { title: 'Payments', icon: 'mdi-cash-multiple', to: '/app/payments', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
      { title: 'Attendance', icon: 'mdi-calendar-check', to: '/app/attendance', roles: ['ANCHOR', 'ORGANISATION'] },
    ],
  },
  {
    title: 'Payment Generation',
    items: [
      { title: 'Payment Cycles', icon: 'mdi-calendar-month-outline', to: '/app/payroll', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
      { title: 'Vouchers', icon: 'mdi-ticket-confirmation', to: '/app/vouchers', roles: ['ANCHOR', 'ORGANISATION'], module: 'VOUCHERS' },
    ],
  },
  {
    title: '',
    items: [
      { title: 'Subscription', icon: 'mdi-credit-card-outline', to: '/app/subscription' },
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
  return (!item.roles || (!!auth.role && item.roles.includes(auth.role)))
    && (!item.module || auth.hasModule(item.module))
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
          :to="item.to"
          :prepend-icon="item.icon"
          :title="item.title"
          rounded="lg"
          color="primary"
        />
      </template>
    </v-list>

    <template #append>
      <v-divider />
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
    <v-app-bar-nav-icon density="compact" @click="drawer = !drawer" />
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
        <v-list-item to="/app/settings" prepend-icon="mdi-account" title="Profile & Settings" />
        <v-divider />
        <v-list-item prepend-icon="mdi-logout" title="Log out" @click="handleLogout" />
      </v-list>
    </v-menu>
  </v-app-bar>

  <v-main class="dashboard-main">
    <v-container fluid class="pa-3 pa-md-5">
      <v-dialog v-model="showIdlePrompt" max-width="420" persistent>
        <v-card>
          <v-card-title>Still there?</v-card-title>
          <v-card-text>
            You've been inactive for a while. For your security, you'll be signed out in 30 seconds
            unless you confirm you're still using this device.
          </v-card-text>
          <v-card-actions>
            <v-spacer />
            <v-btn variant="text" @click="logoutNow">Log out</v-btn>
            <v-btn color="primary" @click="confirmStillHere">Yes, I'm still here</v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>

      <!-- Grace period: subscription lapsed but still within the 7-day window. -->
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
          <v-btn v-if="auth.isAnchor" color="warning" variant="flat" size="small" :loading="renewing" @click="renewSubscription">
            Renew now
          </v-btn>
        </div>
      </v-alert>

      <!-- Archived: grace exhausted -> gate access behind renewal. -->
      <div v-if="isArchived" class="archived-gate">
        <v-card variant="flat" border class="pa-8 text-center" max-width="520">
          <v-icon icon="mdi-lock-clock" size="48" color="error" class="mb-3" />
          <h2 class="text-h6 font-weight-bold mb-2">Subscription expired</h2>
          <p class="text-body-2 text-medium-emphasis mb-4">
            The 7-day grace period has ended and your data is archived. Renew the subscription
            to restore access.
          </p>
          <v-btn v-if="auth.isAnchor" color="secondary" :loading="renewing" @click="renewSubscription">
            Renew subscription
          </v-btn>
          <p v-else class="text-caption text-medium-emphasis">
            Please contact your anchor administrator to renew.
          </p>
          <div class="mt-4 d-flex ga-2 justify-center">
            <v-btn variant="text" size="small" prepend-icon="mdi-credit-card-outline" to="/app/subscription">View subscription</v-btn>
            <v-btn variant="text" size="small" prepend-icon="mdi-logout" @click="handleLogout">Log out</v-btn>
          </div>
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
.app-bar { background: rgba(255, 255, 255, .94) !important; backdrop-filter: blur(12px); }
.app-bar :deep(.v-breadcrumbs) { font-size: .82rem; padding-inline: 4px; }
.dashboard-main { background: #f8fafc; min-height: 100vh; }
.archived-gate { display: flex; justify-content: center; padding-top: 8vh; }
@media (max-width: 600px) {
  .role-chip, .user-name { display: none; }
  .app-bar :deep(.v-breadcrumbs) { padding-inline: 8px; }
  .app-bar :deep(.v-btn) { min-width: 40px; padding-inline: 4px; }
}
</style>
