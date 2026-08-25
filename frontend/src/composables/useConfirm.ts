import { reactive } from 'vue'

type ConfirmColor = 'error' | 'warning' | 'primary' | 'secondary'

interface ConfirmOptions {
  title: string
  message: string
  confirmLabel?: string
  color?: ConfirmColor
}

const state = reactive({
  open: false,
  title: '',
  message: '',
  confirmLabel: 'Continue',
  color: 'primary' as ConfirmColor,
})

let resolvePending: ((confirmed: boolean) => void) | null = null

function settle(confirmed: boolean) {
  state.open = false
  resolvePending?.(confirmed)
  resolvePending = null
}

export function useConfirm() {
  function confirmAction(options: ConfirmOptions): Promise<boolean> {
    resolvePending?.(false)
    Object.assign(state, {
      open: true,
      title: options.title,
      message: options.message,
      confirmLabel: options.confirmLabel ?? 'Continue',
      color: options.color ?? 'primary',
    })
    return new Promise<boolean>((resolve) => { resolvePending = resolve })
  }

  return {
    state,
    confirmAction,
    confirm: () => settle(true),
    cancel: () => settle(false),
  }
}
