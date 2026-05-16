import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'qingwenclaws-theme'

export const useThemeStore = defineStore('theme', () => {
  function getInitialMode(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
    } catch { /* ignore */ }
    return 'system'
  }

  function resolveIsDark(mode: ThemeMode): boolean {
    if (mode === 'dark') return true
    if (mode === 'light') return false
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  }

  const mode = ref<ThemeMode>(getInitialMode())
  const isDark = ref(resolveIsDark(mode.value))

  function setMode(newMode: ThemeMode) {
    mode.value = newMode
    isDark.value = resolveIsDark(newMode)
    try { localStorage.setItem(STORAGE_KEY, newMode) } catch { /* ignore */ }
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  // Apply .dark class to <html>
  watch(isDark, (dark) => {
    document.documentElement.classList.toggle('dark', dark)
  }, { immediate: true })

  // Listen for OS-level preference changes when in 'system' mode
  const mql = window.matchMedia?.('(prefers-color-scheme: dark)')
  mql?.addEventListener('change', () => {
    if (mode.value === 'system') {
      isDark.value = mql.matches
    }
  })

  return { mode, isDark, setMode, toggle }
})
