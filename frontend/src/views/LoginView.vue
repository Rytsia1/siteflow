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
    // interceptor already surfaced the error toast
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <h2>SiteFlow</h2>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="Username" prop="username">
          <el-input v-model="form.username" placeholder="e.g. pekerja" />
        </el-form-item>
        <el-form-item label="Password" prop="password">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="handleSubmit" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleSubmit">
            Log In
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <router-link to="/privacy" class="legal-link">Privacy Policy</router-link>
        <span class="divider">&bull;</span>
        <router-link to="/terms" class="legal-link">Terms of Service</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.login-card {
  width: 360px;
}

.login-footer {
  margin-top: 16px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}

.legal-link {
  color: #909399;
  text-decoration: none;
}

.legal-link:hover {
  color: #409eff;
  text-decoration: underline;
}

.divider {
  margin: 0 8px;
  color: #dcdfe6;
}
</style>
