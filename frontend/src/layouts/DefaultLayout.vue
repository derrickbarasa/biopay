<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const drawer = ref(true)

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
      <router-view />
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
</style>
