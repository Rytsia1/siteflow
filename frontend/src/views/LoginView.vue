<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login, setRole } from '../auth'
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
  // Basic auth has no dedicated login endpoint — verify the credential by calling
  // a real protected endpoint. The interceptor's 401 handler shows the error toast.
  login(form.username, form.password)
  try {
    const me = await http.get('/auth/me')
    setRole(me.role)
    router.push('/inventory')
  } catch {
    // interceptor already surfaced the error and cleared auth
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
</style>
