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

const navItems: NavItem[] = [
  { title: 'Dashboard', icon: 'mdi-view-dashboard', to: '/app/dashboard' },
  { title: 'Organizations', icon: 'mdi-domain', to: '/app/organizations', roles: ['ANCHOR'] },
  { title: 'Locations', icon: 'mdi-map-marker-radius', to: '/app/locations', roles: ['ANCHOR', 'ORGANISATION'] },
  { title: 'Households', icon: 'mdi-home-group', to: '/app/households', roles: ['ANCHOR', 'ORGANISATION'], module: 'HOUSEHOLDS' },
  { title: 'Officers', icon: 'mdi-account-tie', to: '/app/officers', roles: ['ANCHOR', 'ORGANISATION'] },
  { title: 'Payments', icon: 'mdi-cash-multiple', to: '/app/payments', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
  { title: 'Payment Cycles', icon: 'mdi-calendar-cash', to: '/app/payroll', roles: ['ANCHOR', 'ORGANISATION'], module: 'CASH_TRANSFERS' },
  { title: 'Vouchers', icon: 'mdi-ticket-confirmation', to: '/app/vouchers', roles: ['ANCHOR', 'ORGANISATION'], module: 'VOUCHERS' },
  { title: 'Attendance', icon: 'mdi-calendar-check', to: '/app/attendance', roles: ['ANCHOR', 'ORGANISATION'] },
  { title: 'Settings', icon: 'mdi-cog', to: '/app/settings' },
]

const visibleNavItems = computed(() =>
  navItems.filter((item) =>
    (!item.roles || (auth.role && item.roles.includes(auth.role)))
    && (!item.module || auth.hasModule(item.module)),
  ),
)

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <v-navigation-drawer v-model="drawer" color="#062f2d" theme="dark" class="app-drawer">
    <div class="pa-5 d-flex align-center">
      <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="drawer-logo" />
    </div>
    <v-divider />
    <v-list nav density="comfortable">
      <v-list-item
        v-for="item in visibleNavItems"
        :key="item.to"
        :to="item.to"
        :prepend-icon="item.icon"
        :title="item.title"
        rounded="lg"
        color="primary"
      />
    </v-list>
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
.drawer-logo { width: 196px; height: auto; }
.app-drawer :deep(.v-list-item) { margin: 4px 12px; min-height: 46px; opacity: .86; }
.app-drawer :deep(.v-list-item--active) { background: rgba(13, 148, 136, .24); opacity: 1; }
.app-bar { background: rgba(255, 255, 255, .94) !important; backdrop-filter: blur(12px); }
.dashboard-main { background: radial-gradient(circle at 100% 0, #ecfdf5 0, transparent 28rem), #f8fafc; min-height: 100vh; }
</style>
