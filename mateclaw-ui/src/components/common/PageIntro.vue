<template>
  <section v-if="!hidden" class="page-intro mc-surface-card" :aria-label="title">
    <div class="page-intro__hero">
      <div class="page-intro__badge" :style="badgeStyle">
        <span v-if="emoji" class="page-intro__badge-emoji">{{ emoji }}</span>
        <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L9.91 8.26 3.27 8.27l5.46 3.94L6.82 18.5 12 14.77l5.18 3.73-1.91-6.29 5.46-3.94-6.64-.01L12 2z"/>
        </svg>
      </div>
      <div class="page-intro__lead">
        <h3 class="page-intro__title">{{ title }}</h3>
        <p class="page-intro__body">{{ body }}</p>
      </div>
      <button class="page-intro__close" :title="hideLabel" @click="dismiss">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
          <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
      </button>
    </div>
    <div v-if="features && features.length" class="page-intro__features">
      <div v-for="(f, i) in features" :key="i" class="page-intro__feature">
        <span class="page-intro__feature-icon" aria-hidden="true">{{ f.icon }}</span>
        <span class="page-intro__feature-text">{{ f.text }}</span>
      </div>
    </div>
    <p v-if="footer" class="page-intro__footer">{{ footer }}</p>
  </section>
  <div v-else-if="reopenLabel" class="page-intro-reopen">
    <button class="page-intro-reopen__btn" @click="hidden = false">
      <span>💡</span>{{ reopenLabel }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'

interface IntroFeature {
  icon: string
  text: string
}

const props = defineProps<{
  /** Unique localStorage key; collisions across pages must be avoided. */
  storageKey: string
  title: string
  body: string
  features?: IntroFeature[]
  footer?: string
  hideLabel: string
  reopenLabel?: string
  /** Optional emoji shown inside the gradient badge; falls back to the star icon. */
  emoji?: string
  /** Gradient stops for the badge — defaults to the brand primary gradient. */
  badgeFrom?: string
  badgeTo?: string
}>()

const hidden = ref<boolean>(
  typeof localStorage !== 'undefined' && localStorage.getItem(props.storageKey) === '1',
)

function dismiss() {
  hidden.value = true
  try { localStorage.setItem(props.storageKey, '1') } catch { /* private mode */ }
}

watch(hidden, v => {
  if (!v) {
    try { localStorage.removeItem(props.storageKey) } catch { /* noop */ }
  }
})

const badgeStyle = computed(() => {
  if (!props.badgeFrom && !props.badgeTo) return undefined
  const from = props.badgeFrom || 'var(--mc-primary)'
  const to = props.badgeTo || 'var(--mc-primary-hover)'
  return { background: `linear-gradient(135deg, ${from}, ${to})` }
})
</script>

<style scoped>
.page-intro {
  position: relative;
  padding: 18px 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background:
    radial-gradient(circle at 0% 0%, rgba(217, 119, 87, 0.13), transparent 55%),
    radial-gradient(circle at 100% 100%, rgba(99, 102, 241, 0.10), transparent 50%),
    var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  overflow: hidden;
}
html.dark .page-intro {
  background:
    radial-gradient(circle at 0% 0%, rgba(217, 119, 87, 0.18), transparent 55%),
    radial-gradient(circle at 100% 100%, rgba(99, 102, 241, 0.14), transparent 50%),
    var(--mc-bg-elevated);
}
.page-intro__hero {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
.page-intro__badge {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: #fff;
  box-shadow: 0 6px 18px -8px rgba(217, 119, 87, 0.55);
}
.page-intro__badge-emoji { font-size: 22px; line-height: 1; }
.page-intro__lead { flex: 1; min-width: 0; }
.page-intro__title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.2px;
  color: var(--mc-text-primary);
}
.page-intro__body {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--mc-text-secondary);
}
.page-intro__close {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: var(--mc-text-tertiary);
  cursor: pointer;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.page-intro__close:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.page-intro__features {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.page-intro__feature {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.55);
  border: 1px solid var(--mc-border);
  min-width: 0;
}
html.dark .page-intro__feature { background: rgba(255, 255, 255, 0.05); }
.page-intro__feature-icon {
  font-size: 16px;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 8px;
  background: var(--mc-bg-sunken);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.page-intro__feature-text {
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--mc-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.page-intro__footer {
  margin: 0;
  font-size: 12px;
  color: var(--mc-text-tertiary);
  line-height: 1.5;
}
.page-intro-reopen { display: flex; justify-content: flex-end; }
.page-intro-reopen__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--mc-border);
  background: var(--mc-bg-elevated);
  color: var(--mc-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.page-intro-reopen__btn:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
@media (max-width: 720px) {
  .page-intro__features { grid-template-columns: 1fr; }
}
</style>
