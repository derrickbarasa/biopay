<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'

const auth = useAuthStore()
const router = useRouter()
const toast = useToast()
const drawer = ref(true)

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

const isArchived = computed(() => subscription.value.status === 'ARCHIVED')
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
      { title: 'Organizations', icon: 'mdi-domain', to: '/app/organizations', roles: ['ANCHOR'] },
      { title: 'Officers', icon: 'mdi-account-tie', to: '/app/officers', roles: ['ANCHOR', 'ORGANISATION'] },
      { title: 'Locations', icon: 'mdi-map-marker-radius', to: '/app/locations', roles: ['ANCHOR', 'ORGANISATION'] },
    ],
  },
  {
    title: 'Transfers',
    items: [
      { title: 'Households', icon: 'mdi-home-group', to: '/app/households', roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS' },
      { title: 'Payments', icon: 'mdi-cash-multiple', to: '/app/payments', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
      { title: 'Payment Cycles', icon: 'mdi-calendar-cash', to: '/app/payroll', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
      { title: 'Vouchers', icon: 'mdi-ticket-confirmation', to: '/app/vouchers', roles: ['ANCHOR', 'ORGANISATION'], module: 'VOUCHERS' },
      { title: 'Attendance', icon: 'mdi-calendar-check', to: '/app/attendance', roles: ['ANCHOR', 'ORGANISATION'] },
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
  <v-navigation-drawer v-model="drawer" color="surface" class="app-drawer">
    <div class="pa-5 d-flex align-center">
      <img src="/biopay_logo_dark.svg" alt="BioPay" class="drawer-logo" />
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

  <v-app-bar color="surface" elevation="0" border class="app-bar">
    <v-app-bar-nav-icon @click="drawer = !drawer" />
    <v-breadcrumbs :items="[{ title: $route.name?.toString() ?? '' }]" class="text-capitalize" />
    <v-spacer />
    <v-chip class="mr-3 font-weight-bold" color="secondary" variant="tonal" size="small">{{ auth.roleLabel }}</v-chip>
    <v-menu>
      <template #activator="{ props }">
        <v-btn v-bind="props" variant="text" class="text-none">
          <v-avatar color="primary" size="32" class="mr-2">
            <span class="text-caption">{{ auth.initials }}</span>
          </v-avatar>
          {{ auth.fullName }}
          <v-icon icon="mdi-chevron-down" class="ml-1" />
        </v-btn>
      </template>
      <v-list density="compact">
        <v-list-item to="/settings" prepend-icon="mdi-account" title="Profile & Settings" />
        <v-divider />
        <v-list-item prepend-icon="mdi-logout" title="Log out" @click="handleLogout" />
      </v-list>
    </v-menu>
  </v-app-bar>

  <v-main class="dashboard-main">
    <v-container fluid class="pa-4 pa-md-7">
      <!-- Grace period: subscription lapsed but still within the 30-day window. -->
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
            The 30-day grace period has ended and your data is archived. Renew the subscription
            to restore access.
          </p>
          <v-btn v-if="auth.isAnchor" color="primary" :loading="renewing" @click="renewSubscription">
            Renew subscription
          </v-btn>
          <p v-else class="text-caption text-medium-emphasis">
            Please contact your anchor administrator to renew.
          </p>
          <div class="mt-4">
            <v-btn variant="text" size="small" prepend-icon="mdi-logout" @click="handleLogout">Log out</v-btn>
          </div>
        </v-card>
      </div>

      <router-view v-else />
    </v-container>
  </v-main>
</template>

<style scoped>
.drawer-logo { width: 176px; height: auto; }
.app-drawer { border-right: 1px solid rgba(15, 23, 42, .08); }
.app-drawer :deep(.v-list) { display: flex; flex-direction: column; }
.app-drawer :deep(.v-list-item) { margin: 3px 12px; min-height: 44px; color: #334155; }
.app-drawer :deep(.v-list-item-title) { font-weight: 500; }
.app-drawer :deep(.v-list-item--active) { background: rgba(13, 148, 136, .12); color: #0f766e; }
.app-drawer :deep(.v-list-item--active .v-list-item-title) { font-weight: 600; }
.drawer-subheader { font-size: .68rem; letter-spacing: .08em; text-transform: uppercase; opacity: .55; padding-inline-start: 20px !important; }
.app-bar { background: rgba(255, 255, 255, .94) !important; backdrop-filter: blur(12px); }
.dashboard-main { background: radial-gradient(circle at 100% 0, #ecfeff 0, transparent 28rem), #f8fafc; min-height: 100vh; }
.archived-gate { display: flex; justify-content: center; padding-top: 8vh; }
</style>
