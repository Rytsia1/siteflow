<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { auth, logout, isAuthenticated } from './auth'

const router = useRouter()

// Theme State
const isDark = ref(false)

function initTheme() {
  const saved = typeof localStorage !== 'undefined' ? localStorage.getItem('siteflow.theme') : null
  if (saved === 'dark' || (!saved && typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
    isDark.value = true
    document.documentElement.classList.add('dark')
  } else {
    isDark.value = false
    document.documentElement.classList.remove('dark')
  }
}

function toggleTheme() {
  isDark.value = !isDark.value
  if (isDark.value) {
    document.documentElement.classList.add('dark')
    localStorage.setItem('siteflow.theme', 'dark')
  } else {
    document.documentElement.classList.remove('dark')
    localStorage.setItem('siteflow.theme', 'light')
  }
}

// Notification State
const notificationVisible = ref(false)
const notifications = ref([
  {
    id: 1,
    title: 'Tool Return Inspection Pending',
    detail: 'Impact Drill returned with condition: Needs Repair',
    timestamp: '15m ago',
    route: '/assets',
    roles: ['ADMIN', 'WAREHOUSE_STAFF'],
    unread: true,
  },
  {
    id: 2,
    title: 'Borrow Request #3 Approved',
    detail: 'Your request for Angle Grinder has been approved',
    timestamp: '1h ago',
    route: '/borrow',
    roles: ['FIELD_STAFF'],
    unread: true,
  },
  {
    id: 3,
    title: 'Low Stock Threshold Warning',
    detail: 'Safety Gloves stock fell below minimum threshold (10 units)',
    timestamp: '2h ago',
    route: '/inventory',
    roles: ['ADMIN', 'WAREHOUSE_STAFF'],
    unread: true,
  },
  {
    id: 4,
    title: 'Material Request #1 Ready for PO',
    detail: 'Foundation reinforcement material approved',
    timestamp: '3h ago',
    route: '/procurement',
    roles: ['ADMIN', 'PROCUREMENT'],
    unread: false,
  },
])

const userNotifications = computed(() => {
  return notifications.value.filter(
    (n) => !n.roles || n.roles.includes(auth.role) || auth.role === 'ADMIN'
  )
})

const unreadCount = computed(() => {
  return userNotifications.value.filter((n) => n.unread).length
})

function markAllAsRead() {
  notifications.value.forEach((n) => {
    n.unread = false
  })
}

function handleNotificationClick(item) {
  item.unread = false
  notificationVisible.value = false
  if (item.route) {
    router.push(item.route)
  }
}

function handleLogout() {
  logout()
  router.push('/login')
}

onMounted(() => {
  initTheme()
})
</script>

<template>
  <a href="#main-content" class="skip-link">Skip to main content</a>

  <el-container v-if="isAuthenticated()" class="app-shell">
    <el-header class="app-header" role="banner">
      <div class="brand">SiteFlow</div>
      <nav aria-label="Main Navigation" class="nav-wrapper">
        <el-menu mode="horizontal" router :default-active="$route.path" class="nav-menu">
          <el-menu-item index="/inventory">Inventory</el-menu-item>
          <el-menu-item index="/borrow">Borrow Request</el-menu-item>
          <el-menu-item index="/procurement">Procurement</el-menu-item>
          <el-menu-item v-if="auth.role === 'ADMIN' || auth.role === 'WAREHOUSE_STAFF'" index="/assets">
            Asset Scanner
          </el-menu-item>
          <el-menu-item v-if="auth.role === 'ADMIN'" index="/approvals">Approvals</el-menu-item>
          <el-menu-item v-if="auth.role === 'ADMIN'" index="/analytics">Analytics</el-menu-item>
          <el-menu-item v-if="auth.role === 'ADMIN'" index="/audit">Audit Trail</el-menu-item>
        </el-menu>
      </nav>

      <div class="user-info">
        <!-- Notification Center Popover -->
        <el-popover
          v-model:visible="notificationVisible"
          placement="bottom-end"
          :width="360"
          trigger="click"
        >
          <template #reference>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="badge-item">
              <el-button
                circle
                size="small"
                :aria-label="`Notifications (${unreadCount} unread)`"
                :aria-expanded="notificationVisible"
              >
                🔔
              </el-button>
            </el-badge>
          </template>

          <div class="notification-panel" role="region" aria-label="Notifications Center">
            <div class="notification-header">
              <span class="notification-title">Notifications</span>
              <el-button
                v-if="unreadCount > 0"
                link
                size="small"
                aria-label="Mark all notifications as read"
                @click="markAllAsRead"
              >
                Mark all as read
              </el-button>
            </div>

            <div v-if="userNotifications.length > 0" class="notification-list" role="list">
              <button
                v-for="item in userNotifications"
                :key="item.id"
                class="notification-item"
                :class="{ unread: item.unread }"
                role="listitem"
                :aria-label="`${item.title}, ${item.detail}, ${item.timestamp} ${item.unread ? '(Unread)' : '(Read)'}`"
                @click="handleNotificationClick(item)"
              >
                <div class="notification-item-top">
                  <span class="item-title">{{ item.title }}</span>
                  <span class="item-time">{{ item.timestamp }}</span>
                </div>
                <div class="item-detail">{{ item.detail }}</div>
                <div class="item-status">
                  <span v-if="item.unread" class="status-indicator unread-badge">● Unread</span>
                  <span v-else class="status-indicator read-badge">✓ Read</span>
                </div>
              </button>
            </div>
            <div v-else class="notification-empty">
              <p>No notifications available.</p>
            </div>
          </div>
        </el-popover>

        <!-- Theme Toggle Button -->
        <el-button
          circle
          size="small"
          :aria-label="isDark ? 'Switch to light mode' : 'Switch to dark mode'"
          @click="toggleTheme"
        >
          {{ isDark ? '☀️' : '🌙' }}
        </el-button>

        <span class="username-label">{{ auth.username }}</span>
        <el-button size="small" aria-label="Log out of application" @click="handleLogout">Log Out</el-button>
      </div>
    </el-header>

    <el-main id="main-content" role="main" tabindex="-1">
      <router-view />
    </el-main>

    <el-footer class="app-footer" role="contentinfo">
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

.app-header {
  display: flex;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--siteflow-border-color);
  background-color: var(--siteflow-bg-card);
}

.brand {
  font-weight: 700;
  font-size: 18px;
  color: var(--siteflow-text-primary);
}

.nav-wrapper {
  flex: 1;
}

.nav-menu {
  border-bottom: none;
  background-color: transparent;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--siteflow-text-primary);
}

/* Notification Popover Styling */
.notification-panel {
  display: flex;
  flex-direction: column;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--siteflow-border-color);
  margin-bottom: 8px;
}

.notification-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--siteflow-text-primary);
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
}

.notification-item {
  display: flex;
  flex-direction: column;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
  width: 100%;
  font-family: inherit;
  color: inherit;
}

.notification-item:hover,
.notification-item:focus-visible {
  background-color: var(--siteflow-bg-page);
  border-color: var(--siteflow-border-color);
}

.notification-item.unread {
  background-color: rgba(64, 158, 255, 0.08);
  border-color: rgba(64, 158, 255, 0.2);
}

.notification-item-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}

.item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--siteflow-text-primary);
}

.item-time {
  font-size: 11px;
  color: var(--siteflow-text-muted);
}

.item-detail {
  font-size: 12px;
  color: var(--siteflow-text-regular);
  margin-top: 2px;
  line-height: 1.4;
}

.item-status {
  margin-top: 4px;
}

.status-indicator {
  font-size: 11px;
  font-weight: 500;
}

.unread-badge {
  color: #409eff;
}

.read-badge {
  color: var(--siteflow-text-muted);
}

.notification-empty {
  text-align: center;
  color: var(--siteflow-text-muted);
  font-size: 13px;
  padding: 16px 0;
}

/* App Footer */
.app-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 13px;
  color: var(--siteflow-text-muted);
  border-top: 1px solid var(--siteflow-border-color);
  background-color: var(--siteflow-bg-card);
  height: 48px;
}

.footer-link {
  color: var(--siteflow-text-muted);
  text-decoration: none;
}

.footer-link:hover,
.footer-link:focus-visible {
  color: var(--siteflow-link-color);
  text-decoration: underline;
}

.footer-divider {
  margin: 0 10px;
  color: var(--siteflow-border-color);
}
</style>
