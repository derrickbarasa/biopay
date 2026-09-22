import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import { baseURL, storedToken } from '@/api/client'

export type ChatMode = 'site' | 'dashboard'

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

// Must not exceed the backend's own MAX_HISTORY_TURNS (see Chat.java) -- history longer than
// that is rejected outright there rather than silently trimmed, so this caps it first.
const MAX_HISTORY_TURNS = 8

interface ModeState {
  messages: ChatMessage[]
  sending: boolean
  loginIntent: boolean
}

function emptyModeState(): ModeState {
  return { messages: [], sending: false, loginIntent: false }
}

/**
 * Conversation state for both chat surfaces (anonymous site FAQ bot and the signed-in dashboard
 * assistant), kept separate by {@link ChatMode} so a visitor's marketing-site chat never bleeds
 * into the dashboard assistant's history if the same tab later logs in. Never persisted --
 * matches the backend, which never stores a chat message either (see Chat.java's doc comment).
 *
 * Talks to the backend's two streaming routes directly via `fetch` rather than the usual
 * `dispatch()` helper: a chat reply is newline-delimited JSON chunks over time, and reading that
 * incrementally needs a raw `ReadableStream` reader that axios's browser adapter doesn't expose.
 */
export const useChatStore = defineStore('chat', () => {
  const isOpen = ref(false)
  const state = reactive<Record<ChatMode, ModeState>>({
    site: emptyModeState(),
    dashboard: emptyModeState(),
  })

  function toggle() {
    isOpen.value = !isOpen.value
  }

  function close() {
    isOpen.value = false
  }

  function reset(mode: ChatMode) {
    Object.assign(state[mode], emptyModeState())
  }

  function dismissLoginIntent(mode: ChatMode) {
    state[mode].loginIntent = false
  }

  async function send(mode: ChatMode, text: string) {
    const trimmed = text.trim()
    const modeState = state[mode]
    if (!trimmed || modeState.sending) return

    // Snapshot history BEFORE pushing this turn -- the backend expects prior turns only, the
    // new message is sent as its own field (see Chat.buildUserPrompt).
    const history = modeState.messages.slice(-MAX_HISTORY_TURNS * 2)
    modeState.messages.push({ role: 'user', content: trimmed })
    const assistantMessage: ChatMessage = { role: 'assistant', content: '' }
    modeState.messages.push(assistantMessage)
    modeState.sending = true
    modeState.loginIntent = false

    try {
      const path = mode === 'dashboard' ? '/api/v1/chat-stream' : '/site/chat-stream'
      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (mode === 'dashboard') {
        const token = storedToken()
        if (token) headers.Authorization = `Bearer ${token}`
      }

      const response = await fetch(`${baseURL}${path}`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ message: trimmed, history }),
      })
      if (!response.ok || !response.body) {
        throw new Error(`Request failed (${response.status})`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let sawDone = false

      while (true) {
        const { value, done } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        let newlineIndex = buffer.indexOf('\n')
        while (newlineIndex >= 0) {
          const line = buffer.slice(0, newlineIndex).trim()
          buffer = buffer.slice(newlineIndex + 1)
          if (line && applyChunk(line, assistantMessage, modeState)) sawDone = true
          newlineIndex = buffer.indexOf('\n')
        }
      }
      const trailing = buffer.trim()
      if (trailing && applyChunk(trailing, assistantMessage, modeState)) sawDone = true
      if (!sawDone && !assistantMessage.content) {
        assistantMessage.content = 'Something went wrong. Please try again.'
      }
    } catch {
      assistantMessage.content = assistantMessage.content
        || "Couldn't reach the assistant. Please check your connection and try again."
    } finally {
      modeState.sending = false
    }
  }

  /** Applies one parsed ndjson line to the in-flight assistant message. Returns true once the
   *  line carries {done: true}, so the caller can tell a real end from the stream just ending. */
  function applyChunk(line: string, assistantMessage: ChatMessage, modeState: ModeState): boolean {
    let chunk: any
    try {
      chunk = JSON.parse(line)
    } catch {
      return false
    }
    if (typeof chunk.delta === 'string') {
      assistantMessage.content += chunk.delta
    }
    if (chunk.done) {
      if (chunk.error) {
        assistantMessage.content = chunk.message || 'The assistant is temporarily unavailable.'
      } else if (typeof chunk.reply === 'string') {
        assistantMessage.content = chunk.reply
      }
      if (chunk.loginIntent) {
        modeState.loginIntent = true
      }
      return true
    }
    return false
  }

  return { isOpen, state, toggle, close, reset, dismissLoginIntent, send }
})
