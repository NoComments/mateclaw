<template>
  <div class="login-page">
    <!-- Dot-grid background -->
    <div class="login-grid" aria-hidden="true"></div>
    <!-- Top glow -->
    <div class="login-glow" aria-hidden="true"></div>

    <div class="login-center">
      <!-- Status bar -->
      <div class="status-bar">
        <span class="status-dot"></span>
        <span class="status-text">AUTH_SYSTEM · ONLINE</span>
        <span class="status-line"></span>
        <span class="status-ver">v{{ appVersion }}</span>
      </div>

      <!-- Brand -->
      <div class="login-brand">
        <div class="brand-logo">
          <img src="/logo/qingwenclaws_logo_s.svg" alt="QingwenClaws" class="logo-img" />
        </div>
        <div class="brand-copy">
          <h1 class="brand-name">Qingwen<span class="brand-accent">Claws</span></h1>
          <p class="brand-sub">enterprise intelligence platform</p>
        </div>
      </div>

      <!-- Form card -->
      <form class="login-card" @submit.prevent="handleLogin" novalidate>
        <div class="card-top-rule" aria-hidden="true"></div>

        <div class="field">
          <label class="field-label" for="username">{{ t('login.fields.username') }}</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            class="field-input"
            :placeholder="t('login.placeholders.username')"
            autocomplete="username"
            required
          />
        </div>

        <div class="field">
          <label class="field-label" for="password">{{ t('login.fields.password') }}</label>
          <div class="input-wrap">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              class="field-input"
              :placeholder="t('login.placeholders.password')"
              autocomplete="current-password"
              required
            />
            <button type="button" class="eye-btn" @click="showPassword = !showPassword" :aria-label="showPassword ? '隐藏密码' : '显示密码'">
              <svg v-if="!showPassword" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </div>

        <div v-if="errorMsg" class="error-msg" role="alert">{{ errorMsg }}</div>

        <button type="submit" class="login-btn" :disabled="loading">
          <span v-if="!loading">{{ t('login.signIn') }}</span>
          <span v-else class="loading-dots" aria-label="登录中">
            <span></span><span></span><span></span>
          </span>
        </button>

        <p class="login-hint" v-html="t('login.hint')"></p>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/index'
import { version as appVersion } from '../../package.json'

const router = useRouter()
const { t } = useI18n()
const loading = ref(false)
const showPassword = ref(false)
const errorMsg = ref('')
const form = reactive({ username: '', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res: any = await authApi.login(form)
    const data = res.data || res
    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', String(data.id || '1'))
    localStorage.setItem('username', data.username || form.username)
    localStorage.setItem('role', data.role || 'user')
    router.push('/')
  } catch (e: any) {
    errorMsg.value = typeof e === 'string' ? e : t('login.failed')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* ── Page shell ── */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0D0F14;
  padding: 24px;
  position: relative;
  overflow: hidden;
}

/* Dot grid */
.login-grid {
  position: fixed;
  inset: 0;
  background-image: radial-gradient(rgba(79, 110, 247, 0.2) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: radial-gradient(ellipse at center, black 20%, transparent 72%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 20%, transparent 72%);
  pointer-events: none;
}

/* Top glow */
.login-glow {
  position: fixed;
  top: -120px;
  left: 50%;
  transform: translateX(-50%);
  width: 600px;
  height: 400px;
  background: radial-gradient(ellipse, rgba(79, 110, 247, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

/* ── Center column ── */
.login-center {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 28px;
  position: relative;
  z-index: 1;
  animation: fadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* ── Status bar ── */
.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #4F6EF7;
  box-shadow: 0 0 8px rgba(79, 110, 247, 0.8);
  flex-shrink: 0;
  animation: pulse 2.4s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.5; }
}

.status-text {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.1em;
  color: rgba(79, 110, 247, 0.7);
  white-space: nowrap;
}

.status-line {
  flex: 1;
  height: 1px;
  background: rgba(79, 110, 247, 0.15);
}

.status-ver {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.15);
  white-space: nowrap;
}

/* ── Brand ── */
.login-brand {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-logo {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: rgba(79, 110, 247, 0.1);
  border: 1px solid rgba(79, 110, 247, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 0 24px rgba(79, 110, 247, 0.12);
}

.logo-img {
  width: 34px;
  height: 34px;
  object-fit: contain;
  filter: drop-shadow(0 0 8px rgba(79, 110, 247, 0.4));
}

.brand-name {
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 28px;
  font-weight: 700;
  color: #F0F2FF;
  letter-spacing: -0.03em;
  margin: 0;
  line-height: 1;
}

.brand-accent { color: #4F6EF7; }

.brand-sub {
  font-family: 'IBM Plex Mono', monospace;
  font-size: 10px;
  font-weight: 300;
  color: rgba(240, 242, 255, 0.25);
  letter-spacing: 0.06em;
  margin-top: 5px;
}

/* ── Card ── */
.login-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 28px 28px 24px;
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(20px);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Top gradient rule */
.card-top-rule {
  position: absolute;
  top: 0; left: 50%;
  transform: translateX(-50%);
  width: 60%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(79, 110, 247, 0.5), transparent);
  border-radius: 1px;
}

/* ── Fields ── */
.field { display: flex; flex-direction: column; gap: 6px; }

.field-label {
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 11px;
  font-weight: 500;
  color: rgba(240, 242, 255, 0.4);
  letter-spacing: 0.04em;
}

.input-wrap { position: relative; display: flex; align-items: center; }

.field-input {
  width: 100%;
  padding: 11px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: #F0F2FF;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
  -webkit-appearance: none;
}

.input-wrap .field-input { padding-right: 42px; }

.field-input::placeholder { color: rgba(240, 242, 255, 0.18); }

.field-input:focus {
  border-color: rgba(79, 110, 247, 0.5);
  background: rgba(79, 110, 247, 0.04);
  box-shadow: 0 0 0 3px rgba(79, 110, 247, 0.08);
}

/* Eye toggle */
.eye-btn {
  position: absolute;
  right: 12px;
  width: 28px;
  height: 28px;
  border: none;
  background: none;
  cursor: pointer;
  color: rgba(240, 242, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: color 0.15s;
}
.eye-btn:hover { color: rgba(79, 110, 247, 0.8); }

/* ── Error ── */
.error-msg {
  padding: 10px 14px;
  background: rgba(224, 90, 74, 0.12);
  border: 1px solid rgba(224, 90, 74, 0.3);
  border-radius: 8px;
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 13px;
  color: #E05A4A;
}

/* ── Button ── */
.login-btn {
  width: 100%;
  padding: 12px;
  background: #4F6EF7;
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-family: 'Plus Jakarta Sans', sans-serif;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s, transform 0.15s, box-shadow 0.2s;
  box-shadow: 0 4px 16px rgba(79, 110, 247, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.12);
  margin-top: 2px;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(79, 110, 247, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

.login-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* Loading dots */
.loading-dots { display: flex; gap: 5px; align-items: center; }
.loading-dots span {
  width: 5px; height: 5px;
  background: white; border-radius: 50%;
  animation: bounce 1.2s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30%           { transform: translateY(-5px); }
}

/* ── Hint ── */
.login-hint {
  text-align: center;
  font-family: 'IBM Plex Mono', monospace;
  font-size: 11px;
  color: rgba(240, 242, 255, 0.15);
  margin: 0;
  line-height: 1.6;
}

.login-hint :deep(code) {
  background: rgba(79, 110, 247, 0.12);
  color: rgba(107, 138, 251, 0.9);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
}

/* ── Mobile ── */
@media (max-width: 480px) {
  .login-page { padding: 16px; }
  .login-card { padding: 22px 20px 20px; }
  .brand-name { font-size: 24px; }
}
</style>
