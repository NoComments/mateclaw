<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChat } from '@/composables/chat/useChat'
import type { Message } from '@/types'

const { t } = useI18n()

const AGENT_ID = 1000000020

// ── conversation ─────────────────────────────────────────────────────────────
const conversationId = ref<string>(`conv_analyst_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`)

// ── useChat composable ────────────────────────────────────────────────────────
const { messages, isGenerating, sendMessage } = useChat({
  baseUrl: '',
  onStreamEnd: () => {
    scrollToBottom()
  },
})

// ── welcome message ───────────────────────────────────────────────────────────
const WELCOME: Message = {
  id: 'welcome',
  conversationId: conversationId.value,
  role: 'assistant',
  content: '你好！我是数据分析专家，请先上传您的数据集，然后描述您想要的分析。',
  contentParts: [],
  createTime: new Date().toISOString(),
}

onMounted(() => {
  messages.value = [WELCOME]
})

// ── input ─────────────────────────────────────────────────────────────────────
const inputText = ref('')

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || isGenerating.value) return

  inputText.value = ''

  try {
    await sendMessage(text, {
      conversationId: conversationId.value,
      agentId: AGENT_ID,
    })
  } catch (e: unknown) {
    console.error('[AnalystChat] sendMessage failed:', e)
  }

  await nextTick()
  scrollToBottom()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

// ── scroll ────────────────────────────────────────────────────────────────────
const messagesRef = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}
</script>

<template>
  <div class="mc-page-shell analyst-chat-shell">
    <div class="mc-page-frame analyst-chat-frame">
      <!-- header -->
      <div class="mc-page-header analyst-chat-header">
        <div>
          <div class="mc-page-kicker">Analytics</div>
          <h1 class="mc-page-title">{{ t('analytics.analyst') }}</h1>
        </div>
      </div>

      <!-- chat surface -->
      <div class="mc-surface-card analyst-chat-card">
        <!-- message list -->
        <div ref="messagesRef" class="analyst-messages">
          <div
            v-for="msg in messages"
            :key="String(msg.id)"
            class="analyst-bubble-row"
            :class="msg.role === 'user' ? 'analyst-bubble-row--user' : 'analyst-bubble-row--assistant'"
          >
            <div
              class="analyst-bubble"
              :class="msg.role === 'user' ? 'analyst-bubble--user' : 'analyst-bubble--assistant'"
            >
              <span class="analyst-bubble__text">{{ msg.content }}</span>
            </div>
          </div>

          <!-- thinking indicator -->
          <div v-if="isGenerating" class="analyst-bubble-row analyst-bubble-row--assistant">
            <div class="analyst-bubble analyst-bubble--assistant analyst-bubble--thinking">
              <span class="analyst-thinking-dot" />
              <span class="analyst-thinking-dot" />
              <span class="analyst-thinking-dot" />
            </div>
          </div>
        </div>

        <!-- input bar -->
        <div class="analyst-input-bar">
          <textarea
            v-model="inputText"
            class="analyst-textarea"
            :placeholder="t('chat.inputPlaceholder', '输入消息…')"
            :disabled="isGenerating"
            rows="1"
            @keydown="handleKeydown"
          />
          <button
            class="analyst-send-btn btn-primary"
            :disabled="isGenerating || !inputText.trim()"
            @click="handleSend"
          >
            {{ t('common.send', '发送') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analyst-chat-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.analyst-chat-frame {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.analyst-chat-header {
  flex-shrink: 0;
}

.analyst-chat-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

/* message list */
.analyst-messages {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* bubble rows */
.analyst-bubble-row {
  display: flex;
}

.analyst-bubble-row--user {
  justify-content: flex-end;
}

.analyst-bubble-row--assistant {
  justify-content: flex-start;
}

/* bubbles */
.analyst-bubble {
  max-width: 70%;
  padding: 0.625rem 0.875rem;
  border-radius: var(--mc-radius-md, 8px);
  line-height: 1.6;
  font-size: 0.9375rem;
  word-break: break-word;
  white-space: pre-wrap;
}

.analyst-bubble--user {
  background-color: var(--mc-color-primary, #4f46e5);
  color: var(--mc-color-on-primary, #fff);
  border-bottom-right-radius: 2px;
}

.analyst-bubble--assistant {
  background-color: var(--mc-surface-2, var(--el-fill-color-light, #f5f5f5));
  color: var(--mc-text-primary, var(--el-text-color-primary, #303133));
  border-bottom-left-radius: 2px;
}

/* thinking indicator */
.analyst-bubble--thinking {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.625rem 1rem;
}

.analyst-thinking-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background-color: var(--mc-color-text-secondary, var(--el-text-color-secondary, #909399));
  animation: analyst-bounce 1.2s infinite ease-in-out;
}

.analyst-thinking-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.analyst-thinking-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes analyst-bounce {
  0%, 80%, 100% { transform: translateY(0); opacity: 0.5; }
  40% { transform: translateY(-6px); opacity: 1; }
}

/* input bar */
.analyst-input-bar {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 0.625rem;
  padding: 0.875rem 1.25rem;
  border-top: 1px solid var(--mc-border-color, var(--el-border-color-lighter, #ebeef5));
}

.analyst-textarea {
  flex: 1;
  resize: none;
  border: 1px solid var(--mc-border-color, var(--el-border-color, #dcdfe6));
  border-radius: var(--mc-radius-sm, 6px);
  padding: 0.5rem 0.75rem;
  font-size: 0.9375rem;
  line-height: 1.5;
  color: var(--mc-text-primary, var(--el-text-color-primary, #303133));
  background-color: var(--mc-surface-1, var(--el-bg-color, #fff));
  outline: none;
  field-sizing: content;
  max-height: 10rem;
  overflow-y: auto;
  transition: border-color 0.15s;
}

.analyst-textarea:focus {
  border-color: var(--mc-color-primary, #4f46e5);
}

.analyst-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.analyst-send-btn {
  flex-shrink: 0;
  height: 2.25rem;
  padding: 0 1rem;
}
</style>
