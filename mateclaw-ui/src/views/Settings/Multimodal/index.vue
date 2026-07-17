<template>
  <div class="mm-page">
    <!-- 页头 -->
    <div class="mm-header">
      <h2 class="mm-title">{{ t('settings.multimodalTitle') }}</h2>
      <p class="mm-desc">{{ t('settings.multimodalDesc') }}</p>
    </div>

    <!-- Tab 导航栏 -->
    <div class="mm-tabbar">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="mm-tab"
        :class="{ active: activeTab === tab.id }"
        @click="setTab(tab.id)"
      >
        <span class="mm-tab-icon" v-html="tab.icon"></span>
        <span class="mm-tab-label">{{ tab.label }}</span>
      </button>
    </div>

    <!-- Tab 内容区 -->
    <div class="mm-body">
      <ImageSettings v-if="activeTab === 'image'" />
      <TtsSettings v-else-if="activeTab === 'tts'" />
      <SttSettings v-else-if="activeTab === 'stt'" />
      <VideoSettings v-else-if="activeTab === 'video'" />
      <MusicSettings v-else-if="activeTab === 'music'" />
      <Model3dSettings v-else-if="activeTab === 'model3d'" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import ImageSettings from '@/views/Settings/Image/index.vue'
import TtsSettings from '@/views/Settings/Tts/index.vue'
import SttSettings from '@/views/Settings/Stt/index.vue'
import VideoSettings from '@/views/Settings/Video/index.vue'
import MusicSettings from '@/views/Settings/Music/index.vue'
import Model3dSettings from '@/views/Settings/Model3D/index.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

type TabId = 'image' | 'tts' | 'stt' | 'video' | 'music' | 'model3d'

const tabs = computed(() => [
  {
    id: 'image' as TabId,
    label: t('settings.sections.image'),
    icon: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>',
  },
  {
    id: 'tts' as TabId,
    label: t('settings.sections.tts'),
    icon: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>',
  },
  {
    id: 'stt' as TabId,
    label: t('settings.sections.stt'),
    icon: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z"/><path d="M19 10v2a7 7 0 0 1-14 0v-2"/><line x1="12" y1="19" x2="12" y2="23"/><line x1="8" y1="23" x2="16" y2="23"/></svg>',
  },
  {
    id: 'video' as TabId,
    label: t('settings.sections.video'),
    icon: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>',
  },
  {
    id: 'music' as TabId,
    label: t('settings.sections.music'),
    icon: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>',
  },
  {
    id: 'model3d' as TabId,
    label: t('settings.sections.model3d'),
    icon: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2 L21 7 L21 17 L12 22 L3 17 L3 7 Z"/><path d="M3 7 L12 12 L21 7"/><path d="M12 12 L12 22"/></svg>',
  },
])

const validTabIds: TabId[] = ['image', 'tts', 'stt', 'video', 'music', 'model3d']

const activeTab = computed<TabId>(() => {
  const q = route.query.tab as string
  return (validTabIds.includes(q as TabId) ? q : 'image') as TabId
})

function setTab(id: TabId) {
  router.replace({ path: '/settings/multimodal', query: { tab: id } })
}
</script>

<style scoped>
.mm-page {
  width: 100%;
}

.mm-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 20px;
}

.mm-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.mm-desc {
  margin: 0;
  font-size: 14px;
  color: var(--mc-text-secondary);
}

/* Tab bar */
.mm-tabbar {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  padding: 4px;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 14px;
  margin-bottom: 24px;
}

.mm-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--mc-text-secondary);
  background: transparent;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.mm-tab:hover {
  background: var(--mc-bg-sunken);
  color: var(--mc-text-primary);
}

.mm-tab.active {
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(217, 109, 70, 0.15);
}

.mm-tab-icon {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  opacity: 0.8;
}

.mm-tab.active .mm-tab-icon {
  opacity: 1;
}

.mm-tab-icon :deep(svg) {
  display: block;
}

/* Tab content: strip the top header margin that each sub-page has */
.mm-body :deep(.section-header) {
  display: none;
}

@media (max-width: 720px) {
  .mm-tabbar {
    gap: 2px;
  }
  .mm-tab {
    padding: 6px 10px;
    font-size: 12px;
  }
}
</style>
