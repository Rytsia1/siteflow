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
  </el-container>
  <router-view v-else />
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
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
