<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useChat } from '@/composables/chat/useChat'
import MessageList from '@/components/chat/MessageList.vue'
import type { Message } from '@/types'

const { t } = useI18n()

const AGENT_ID = 1000000020

const conversationId = ref<string>(`conv_analyst_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`)

const { messages, isGenerating, sendMessage, stopGeneration } = useChat({
  baseUrl: '',
})

const WELCOME: Message = {
  id: 'welcome',
  conversationId: conversationId.value,
  role: 'assistant',
  content: '你好！我是数据分析专家。请先在"数据集"页面上传 Excel 数据，然后告诉我您想分析什么。',
  contentParts: [{ type: 'text', text: '你好！我是数据分析专家。请先在"数据集"页面上传 Excel 数据，然后告诉我您想分析什么。' }],
  createTime: new Date().toISOString(),
}

onMounted(() => {
  messages.value = [WELCOME]
})

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
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const SUGGESTIONS = [
  '按市汇总期末肉鸡存栏数',
  '哪个县肉鸡存栏最多？前 5 名',
  '统计每个市的代养户占比',
  '导出河南省所有数据为 Excel',
]

function handleSuggestion(s: string) {
  inputText.value = s
}
</script>

<template>
  <div class="mc-page-shell analyst-chat-shell">
    <div class="mc-page-frame analyst-chat-frame">
      <div class="mc-page-header analyst-chat-header">
        <div>
          <div class="mc-page-kicker">Analytics</div>
          <h1 class="mc-page-title">{{ t('analytics.analyst') }}</h1>
        </div>
        <button v-if="isGenerating" class="stop-btn" @click="stopGeneration">
          {{ t('chat.stop', '停止') }}
        </button>
      </div>

      <div class="mc-surface-card analyst-chat-card">
        <MessageList
          :messages="messages"
          :loading="isGenerating"
          :title="t('analytics.analyst')"
          :subtitle="t('analytics.analystWelcome', '上传数据集后用自然语言提问，我会帮你查询、分析并生成报告')"
          :suggestions="SUGGESTIONS"
          :auto-scroll="true"
          @suggestion-click="handleSuggestion"
        />

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
            class="analyst-send-btn"
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
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stop-btn {
  padding: 6px 14px;
  border: 1px solid var(--mc-danger, #e74c3c);
  color: var(--mc-danger, #e74c3c);
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.stop-btn:hover {
  background: var(--mc-danger-bg, rgba(231, 76, 60, 0.08));
}

.analyst-chat-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 0;
  overflow: hidden;
}

/* MessageList 已经管理自己的滚动 — 它需要 flex:1 撑开 */
.analyst-chat-card :deep(.message-list) {
  flex: 1;
  min-height: 0;
}

.analyst-input-bar {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 0.625rem;
  padding: 0.875rem 1.25rem;
  border-top: 1px solid var(--mc-border, var(--el-border-color-lighter, #ebeef5));
}

.analyst-textarea {
  flex: 1;
  resize: none;
  border: 1px solid var(--mc-border, var(--el-border-color, #dcdfe6));
  border-radius: var(--mc-radius-sm, 6px);
  padding: 0.5rem 0.75rem;
  font-size: 0.9375rem;
  line-height: 1.5;
  color: var(--mc-text-primary, var(--el-text-color-primary, #303133));
  background-color: var(--mc-bg-elevated, var(--el-bg-color, #fff));
  outline: none;
  field-sizing: content;
  max-height: 10rem;
  overflow-y: auto;
  transition: border-color 0.15s;
}

.analyst-textarea:focus {
  border-color: var(--mc-primary, #4f46e5);
}

.analyst-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.analyst-send-btn {
  flex-shrink: 0;
  height: 2.25rem;
  padding: 0 1.25rem;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--mc-primary, #4f46e5), var(--mc-primary-hover, #6366f1));
  color: white;
  font-weight: 600;
  cursor: pointer;
  font-size: 14px;
}
.analyst-send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.analyst-send-btn:not(:disabled):hover {
  opacity: 0.9;
}
</style>
