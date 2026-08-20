import { ref } from 'vue'

/**
 * Shared across the router (which drives it) and App.vue (which renders it).
 * A route's own data usually takes far longer to load than the navigation
 * itself -- without this, clicking a nav item gave no feedback until that
 * data arrived, which reads as "nothing happened, click again" on a slow
 * connection even though the navigation already succeeded.
 */
export const routeNavigating = ref(false)
