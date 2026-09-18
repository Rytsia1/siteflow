<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { loginSuccess } from '../auth'
import http from '../api/http'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
  password: [{ required: true, message: 'Password is required', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await http.post('/auth/login', {
      username: form.username,
      password: form.password,
    })
    loginSuccess(data.accessToken, data.username || form.username, data.role)
    router.push('/inventory')
  } catch {
    // interceptor handled error
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card-container">
      <span class="sf-corner-mark top-left">+</span>
      <span class="sf-corner-mark top-right">+</span>
      <span class="sf-corner-mark bottom-left">+</span>
      <span class="sf-corner-mark bottom-right">+</span>

      <div class="login-header">
        <div class="login-logo-row">
          <div class="login-logo-box">SF</div>
          <div class="login-brand-title">SITEFLOW</div>
        </div>
        <div class="login-kicker">SITE MATERIAL &amp; LOGISTICS PLATFORM</div>
      </div>

      <div class="login-body">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
          <el-form-item label="Username" prop="username">
            <el-input
              id="login-username"
              v-model="form.username"
              placeholder="e.g. pekerja"
              autocomplete="username"
              aria-required="true"
            />
          </el-form-item>
          <el-form-item label="Password" prop="password">
            <el-input
              id="login-password"
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              show-password
              aria-required="true"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>
          <el-form-item style="margin-top: 24px; margin-bottom: 8px;">
            <button
              type="button"
              class="sf-btn-primary"
              style="width: 100%; height: 38px; justify-content: center; font-size: 14px;"
              :disabled="loading"
              @click="handleSubmit"
            >
              {{ loading ? 'Authenticating...' : 'Sign In' }}
            </button>
          </el-form-item>
        </el-form>
      </div>

      <div class="login-footer">
        <router-link to="/privacy" class="legal-link">Privacy Policy</router-link>
        <span class="divider">&bull;</span>
        <router-link to="/terms" class="legal-link">Terms of Service</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: var(--siteflow-bg-page);
  padding: 20px;
}

.login-card-container {
  position: relative;
  width: 100%;
  max-width: 380px;
  background-color: var(--siteflow-bg-card);
  border: 1px solid var(--siteflow-border-subtle);
  border-radius: 3px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.55);
  overflow: hidden;
}

.login-header {
  padding: 24px 24px 16px;
  background-color: #0f1826;
  border-bottom: 1px solid var(--siteflow-border-color);
  text-align: center;
}

.login-logo-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 6px;
}

.login-logo-box {
  width: 26px;
  height: 26px;
  border: 1px solid #4e88c4;
  background-color: #152741;
  display: grid;
  place-items: center;
  font-family: var(--siteflow-font-mono);
  font-size: 12px;
  font-weight: 600;
  color: #8fb6dd;
  border-radius: 2px;
}

.login-brand-title {
  font-family: var(--siteflow-font-heading);
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: #f0f5fb;
}

.login-kicker {
  font-family: var(--siteflow-font-mono);
  font-size: 10px;
  letter-spacing: 0.1em;
  color: #6f8099;
}

.login-body {
  padding: 24px 24px 12px;
}

.login-footer {
  padding: 14px 24px 18px;
  text-align: center;
  font-size: 12px;
  color: var(--siteflow-text-muted);
  border-top: 1px solid var(--siteflow-border-color);
  background-color: #0f1826;
}

.legal-link {
  color: #6f8099;
  text-decoration: none;
  font-weight: 500;
}

.legal-link:hover,
.legal-link:focus-visible {
  color: #8fb6dd;
  text-decoration: underline;
}

.divider {
  margin: 0 8px;
  color: #3a4760;
}
</style>
