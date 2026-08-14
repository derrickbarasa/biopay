import { ref } from 'vue'

interface ToastState {
  show: boolean
  message: string
  color: 'success' | 'error' | 'info' | 'warning'
}

const state = ref<ToastState>({ show: false, message: '', color: 'success' })

export function useToast() {
  function notify(message: string, color: ToastState['color'] = 'success') {
    state.value = { show: true, message, color }
  }
  return {
    state,
    success: (message: string) => notify(message, 'success'),
    error: (message: string) => notify(message, 'error'),
    info: (message: string) => notify(message, 'info'),
    warning: (message: string) => notify(message, 'warning'),
  }
}
