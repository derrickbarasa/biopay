import { reactive } from 'vue'

type ConfirmColor = 'error' | 'warning' | 'primary' | 'secondary' | 'success'

interface ConfirmOptions {
  title: string
  message: string
  confirmLabel?: string
  color?: ConfirmColor
  /** When set (e.g. "DELETE"), the confirm button stays disabled until the user types this
   *  exact text -- an extra deliberate step for irreversible actions, on top of the dialog
   *  itself, so a reflexive click can't trigger a permanent delete. */
  requireTypedText?: string
}

const state = reactive({
  open: false,
  title: '',
  message: '',
  confirmLabel: 'Continue',
  color: 'secondary' as ConfirmColor,
  requireTypedText: null as string | null,
  typedInput: '',
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
      color: options.color ?? 'secondary',
      requireTypedText: options.requireTypedText ?? null,
      typedInput: '',
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
