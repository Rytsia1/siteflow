<script setup>
import { useRouter } from 'vue-router'
import { auth, logout, isAuthenticated } from './auth'

const router = useRouter()

function handleLogout() {
  logout()
  router.push('/login')
}
</script>

<template>
  <el-container v-if="isAuthenticated()" class="app-shell">
    <el-header class="app-header">
      <div class="brand">SiteFlow</div>
      <el-menu mode="horizontal" router :default-active="$route.path" class="nav-menu">
        <el-menu-item index="/inventory">Inventory</el-menu-item>
        <el-menu-item index="/borrow">Borrow Request</el-menu-item>
        <el-menu-item index="/procurement">Procurement</el-menu-item>
        <el-menu-item v-if="auth.role === 'ADMIN' || auth.role === 'WAREHOUSE_STAFF'" index="/assets">
          Asset Scanner
        </el-menu-item>
        <el-menu-item v-if="auth.role === 'ADMIN'" index="/approvals">Approvals</el-menu-item>
        <el-menu-item v-if="auth.role === 'ADMIN'" index="/analytics">Analytics</el-menu-item>
      </el-menu>
      <div class="user-info">
        <span>{{ auth.username }}</span>
        <el-button size="small" @click="handleLogout">Log Out</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
    <el-footer class="app-footer">
      <span>SiteFlow &copy; 2026</span>
      <span class="footer-divider">&bull;</span>
      <router-link to="/privacy" class="footer-link">Privacy Policy</router-link>
      <span class="footer-divider">&bull;</span>
      <router-link to="/terms" class="footer-link">Terms of Service</router-link>
    </el-footer>
  </el-container>
  <router-view v-else />
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 13px;
  color: #909399;
  border-top: 1px solid #e4e7ed;
  height: 48px;
}

.footer-link {
  color: #909399;
  text-decoration: none;
}

.footer-link:hover {
  color: #409eff;
  text-decoration: underline;
}

.footer-divider {
  margin: 0 10px;
  color: #dcdfe6;
}

.app-header {
  display: flex;
  align-items: center;
  gap: 24px;
  border-bottom: 1px solid #e4e7ed;
}

.brand {
  font-weight: 700;
  font-size: 18px;
}

.nav-menu {
  flex: 1;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
