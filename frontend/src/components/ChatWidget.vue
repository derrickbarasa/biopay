<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore, type ChatMode } from '@/stores/chat'

const props = defineProps<{ mode: ChatMode }>()

const chat = useChatStore()
const router = useRouter()
const draft = ref('')
const messagesEl = ref<HTMLElement | null>(null)

const modeState = computed(() => chat.state[props.mode])
const title = computed(() => (props.mode === 'dashboard' ? 'BioPay Assistant' : 'Chat with BioPay'))
const greeting = computed(() => (props.mode === 'dashboard'
  ? "Ask me about your households, payments, or anything else on your dashboard."
  : 'Hi! Ask me anything about BioPay.'))

function scrollToBottom() {
  nextTick(() => {
    if (messagesEl.value) messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  })
}

watch(() => modeState.value.messages, scrollToBottom, { deep: true })

async function onSend() {
  const text = draft.value
  if (!text.trim()) return
  draft.value = ''
  scrollToBottom()
  await chat.send(props.mode, text)
}

function goToLogin() {
  chat.dismissLoginIntent(props.mode)
  chat.close()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="chat-widget">
    <v-btn
      v-if="!chat.isOpen"
      class="chat-fab"
      icon
      color="primary"
      size="large"
      elevation="4"
      aria-label="Open chat"
      @click="chat.toggle()"
    >
      <v-icon icon="mdi-chat-processing-outline" />
    </v-btn>

    <v-card v-else class="chat-panel" elevation="8">
      <div class="chat-header">
        <span class="chat-title">{{ title }}</span>
        <v-btn icon variant="text" size="small" color="white" aria-label="Close chat" @click="chat.close()">
          <v-icon icon="mdi-close" />
        </v-btn>
      </div>

      <div ref="messagesEl" class="chat-messages">
        <p v-if="modeState.messages.length === 0" class="chat-empty">{{ greeting }}</p>
        <div
          v-for="(message, index) in modeState.messages"
          :key="index"
          class="chat-bubble"
          :class="message.role"
        >
          <template v-if="message.content">{{ message.content }}</template>
          <v-progress-circular
            v-else-if="modeState.sending && index === modeState.messages.length - 1"
            indeterminate size="16" width="2"
          />
        </div>
        <div v-if="modeState.loginIntent" class="chat-login-hint">
          <v-btn size="small" variant="tonal" color="primary" @click="goToLogin">Go to login</v-btn>
        </div>
      </div>

      <form class="chat-input-row" @submit.prevent="onSend">
        <v-textarea
          v-model="draft"
          rows="1"
          max-rows="4"
          auto-grow
          density="compact"
          variant="outlined"
          hide-details
          placeholder="Type a message..."
          :disabled="modeState.sending"
          @keydown.enter.exact.prevent="onSend"
        />
        <v-btn
          icon
          color="primary"
          type="submit"
          :loading="modeState.sending"
          :disabled="!draft.trim() || modeState.sending"
        >
          <v-icon icon="mdi-send" />
        </v-btn>
      </form>
    </v-card>
  </div>
</template>

<style scoped>
.chat-widget {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 2400;
}
.chat-panel {
  width: min(380px, calc(100vw - 32px));
  height: min(560px, calc(100vh - 96px));
  display: flex;
  flex-direction: column;
  border-radius: 16px;
  overflow: hidden;
}
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 8px 10px 16px;
  background: rgb(var(--v-theme-primary));
  color: #fff;
  font-weight: 600;
}
.chat-title { font-size: .95rem; }
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f8fafc;
}
.chat-empty {
  margin: auto;
  color: #64748b;
  font-size: .85rem;
  text-align: center;
  max-width: 240px;
}
.chat-bubble {
  max-width: 82%;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: .88rem;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-bubble.user {
  align-self: flex-end;
  background: rgb(var(--v-theme-primary));
  color: #fff;
  border-bottom-right-radius: 4px;
}
.chat-bubble.assistant {
  align-self: flex-start;
  background: #fff;
  border: 1px solid #e2e8f0;
  color: #0f172a;
  border-bottom-left-radius: 4px;
}
.chat-login-hint { align-self: center; margin-top: 4px; }
.chat-input-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 10px;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}
@media (max-width: 420px) {
  .chat-widget { right: 12px; bottom: 12px; }
}
</style>
