import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const IDLE_TIMEOUT_MS = 15 * 60 * 1000
const PROMPT_WINDOW_MS = 30 * 1000
const ACTIVITY_EVENTS = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll', 'wheel'] as const

/**
 * Signs the user out after 15 minutes without any mouse/keyboard/touch activity in the dashboard --
 * a signed-in tab left open on a shared machine exposes beneficiary PII. Rather than logging out
 * silently, it gives a 30-second window to confirm they're still there; once that window opens,
 * further mouse movement no longer resets it (only the confirm button does) so a screensaver or
 * cat-on-the-keyboard can't keep a genuinely abandoned session alive indefinitely.
 */
export function useIdleLogout() {
  const auth = useAuthStore()
  const router = useRouter()
  const showPrompt = ref(false)

  let idleTimer: ReturnType<typeof setTimeout> | undefined
  let expiryTimer: ReturnType<typeof setTimeout> | undefined

  function clearTimers() {
    clearTimeout(idleTimer)
    clearTimeout(expiryTimer)
  }

  function scheduleIdlePrompt() {
    clearTimers()
    idleTimer = setTimeout(() => {
      showPrompt.value = true
      expiryTimer = setTimeout(forceLogout, PROMPT_WINDOW_MS)
    }, IDLE_TIMEOUT_MS)
  }

  function onActivity() {
    if (showPrompt.value) return
    scheduleIdlePrompt()
  }

  function confirmStillHere() {
    showPrompt.value = false
    scheduleIdlePrompt()
  }

  async function forceLogout() {
    clearTimers()
    showPrompt.value = false
    await auth.logout()
    router.push({ name: 'login' })
  }

  onMounted(() => {
    ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, onActivity, { passive: true }))
    scheduleIdlePrompt()
  })

  onBeforeUnmount(() => {
    ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, onActivity))
    clearTimers()
  })

  return { showPrompt, confirmStillHere, logoutNow: forceLogout }
}
