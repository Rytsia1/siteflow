<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../auth'

const auditLogs = ref([])
const totalCount = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const selectedLog = ref(null)
const drawerVisible = ref(false)

const filters = reactive({
  page: 0,
  size: 20,
  action: '',
  resourceType: '',
  actor: '',
  status: '',
})

const actionOptions = [
  { value: 'LOGIN_SUCCESS', label: 'Login Success' },
  { value: 'LOGIN_FAILURE', label: 'Login Failure' },
  { value: 'BORROW_REQUEST_CREATED', label: 'Borrow Request Created' },
  { value: 'BORROW_REQUEST_APPROVED', label: 'Borrow Request Approved' },
  { value: 'BORROW_REQUEST_REJECTED', label: 'Borrow Request Rejected' },
  { value: 'BORROW_REQUEST_CANCELLED', label: 'Borrow Request Cancelled' },
  { value: 'ITEM_BORROWED', label: 'Item / Tool Borrowed' },
  { value: 'ITEM_RETURNED', label: 'Item / Tool Returned' },
  { value: 'STOCK_ADJUSTED', label: 'Stock Adjusted' },
  { value: 'MATERIAL_REQUEST_CREATED', label: 'Material Request Created' },
  { value: 'MATERIAL_REQUEST_APPROVED', label: 'Material Request Approved' },
  { value: 'MATERIAL_REQUEST_REJECTED', label: 'Material Request Rejected' },
  { value: 'MATERIAL_REQUEST_CANCELLED', label: 'Material Request Cancelled' },
  { value: 'PURCHASE_ORDER_CREATED', label: 'Purchase Order Created' },
  { value: 'USER_DEACTIVATED', label: 'User Deactivated' },
]

const resourceTypeOptions = [
  { value: 'BORROW_REQUEST', label: 'Borrow Request' },
  { value: 'MATERIAL_REQUEST', label: 'Material Request' },
  { value: 'PURCHASE_ORDER', label: 'Purchase Order' },
  { value: 'ITEM', label: 'Item Stock' },
  { value: 'ITEM_INSTANCE', label: 'Tool Instance' },
  { value: 'USER', label: 'User Account' },
  { value: 'AUTH', label: 'Authentication' },
]

const statusOptions = [
  { value: 'SUCCESS', label: 'Success (✓)' },
  { value: 'REJECTED', label: 'Rejected (✕)' },
  { value: 'FAILURE', label: 'Failure (⚠)' },
]

function formatTimestamp(ts) {
  if (!ts) return '-'
  try {
    const d = new Date(ts)
    return d.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return ts
  }
}

function getStatusTagType(status) {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'REJECTED':
      return 'warning'
    case 'FAILURE':
      return 'danger'
    default:
      return 'info'
  }
}

function getStatusSymbol(status) {
  switch (status) {
    case 'SUCCESS':
      return '✓ '
    case 'REJECTED':
      return '✕ '
    case 'FAILURE':
      return '⚠ '
    default:
      return ''
  }
}

async function loadAuditLogs() {
  if (auth.role !== 'ADMIN') return
  loading.value = true
  errorMessage.value = ''

  try {
    const params = {
      page: filters.page,
      size: filters.size,
    }
    if (filters.action) params.action = filters.action
    if (filters.resourceType) params.resourceType = filters.resourceType
    if (filters.actor && filters.actor.trim()) params.actor = filters.actor.trim()
    if (filters.status) params.status = filters.status

    const res = await http.get('/audit-logs', { params })
    if (res && res.items) {
      auditLogs.value = res.items
      totalCount.value = res.totalCount
    } else if (Array.isArray(res)) {
      auditLogs.value = res
      totalCount.value = res.length
    }
  } catch (err) {
    errorMessage.value = 'Failed to load audit logs. Please try again or check your administrator permissions.'
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  filters.page = 0
  loadAuditLogs()
}

function resetFilters() {
  filters.page = 0
  filters.action = ''
  filters.resourceType = ''
  filters.actor = ''
  filters.status = ''
  loadAuditLogs()
}

function handlePageChange(newPage) {
  filters.page = newPage - 1 // element-plus is 1-indexed, backend is 0-indexed
  loadAuditLogs()
}

function handleSizeChange(newSize) {
  filters.size = newSize
  filters.page = 0
  loadAuditLogs()
}

function inspectLog(row) {
  selectedLog.value = row
  drawerVisible.value = true
}

onMounted(() => {
  loadAuditLogs()
})
</script>

<template>
  <main id="main-content" class="audit-view" role="main" aria-label="System Audit Trail and Accountability Ledger">
    <div class="tech-kicker">GET /api/audit-logs · AUDIT TRAIL &amp; ACCOUNTABILITY</div>
    <div class="header-section">
      <div>
        <h1 class="page-title">Audit Trail & Accountability Ledger</h1>
        <p class="page-subtitle">
          Immutable event ledger tracking who performed actions, affected resources, and before/after state transitions.
        </p>
      </div>
      <div class="retention-badge" aria-label="Audit Retention Status">
        <el-tag type="info" effect="plain" class="policy-tag">
          Append-Only Ledger • Retention: Protected Business History
        </el-tag>
      </div>
    </div>

    <!-- Error state alert -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      closable
      class="mb-4"
    >
      <template #default>
        <el-button size="small" type="primary" plain class="mt-2" @click="loadAuditLogs">
          Retry Loading
        </el-button>
      </template>
    </el-alert>

    <!-- Filter Toolbar -->
    <el-card class="filter-card mb-4" shadow="never">
      <el-form :inline="true" :model="filters" class="filter-form" @submit.prevent="handleFilter">
        <el-form-item label="Event Action">
          <el-select
            v-model="filters.action"
            placeholder="All Actions"
            clearable
            style="width: 220px"
            aria-label="Filter by Event Action"
          >
            <el-option
              v-for="opt in actionOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Resource">
          <el-select
            v-model="filters.resourceType"
            placeholder="All Resources"
            clearable
            style="width: 170px"
            aria-label="Filter by Resource Type"
          >
            <el-option
              v-for="opt in resourceTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Status">
          <el-select
            v-model="filters.status"
            placeholder="All Statuses"
            clearable
            style="width: 150px"
            aria-label="Filter by Result Status"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="Actor">
          <el-input
            v-model="filters.actor"
            placeholder="Username / SYSTEM"
            clearable
            style="width: 180px"
            aria-label="Search by Actor Username"
            @keyup.enter="handleFilter"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" aria-label="Apply Audit Filters">
            Filter
          </el-button>
          <el-button @click="resetFilters" aria-label="Reset Audit Filters">
            Reset
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Audit Ledger Table -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="auditLogs"
        style="width: 100%"
        stripe
        border
        aria-label="Audit Events Table"
      >
        <template #empty>
          <el-empty description="No audit records match the selected criteria." />
        </template>

        <el-table-column prop="timestamp" label="Timestamp" width="180">
          <template #default="{ row }">
            <span class="timestamp-cell">{{ formatTimestamp(row.timestamp) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="action" label="Action" min-width="190">
          <template #default="{ row }">
            <span class="action-tag">{{ row.action }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="actorUsername" label="Actor" width="150">
          <template #default="{ row }">
            <span v-if="row.actorUsername === 'SYSTEM'" class="actor-system">
              [SYSTEM]
            </span>
            <span v-else class="actor-user">
              👤 {{ row.actorUsername || 'User #' + row.userId }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="Affected Resource" width="180">
          <template #default="{ row }">
            <span v-if="row.resourceType" class="resource-cell">
              <strong>{{ row.resourceType }}</strong>
              <span v-if="row.resourceId"> #{{ row.resourceId }}</span>
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column label="State Transition" min-width="200">
          <template #default="{ row }">
            <div v-if="row.beforeState || row.afterState" class="state-transition">
              <span class="state-before">{{ row.beforeState || 'initial' }}</span>
              <span class="state-arrow" aria-hidden="true"> → </span>
              <span class="state-after">{{ row.afterState || 'final' }}</span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="Result" width="130" align="center">
          <template #default="{ row }">
            <el-tag
              :type="getStatusTagType(row.status)"
              effect="dark"
              class="status-tag"
            >
              {{ getStatusSymbol(row.status) }}{{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="details" label="Reason / Details" min-width="220" show-overflow-tooltip />

        <el-table-column label="Inspect" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              link
              type="primary"
              :aria-label="`Inspect details for audit event #${row.id}`"
              @click="inspectLog(row)"
            >
              View
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-wrapper mt-4">
        <el-pagination
          :current-page="filters.page + 1"
          :page-size="filters.size"
          :page-sizes="[20, 50, 100]"
          :total="totalCount"
          layout="total, sizes, prev, pager, next, jumper"
          aria-label="Audit Log Pagination"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- Inspection Drawer -->
    <el-drawer
      v-model="drawerVisible"
      title="Audit Event Details"
      direction="rtl"
      size="480px"
      aria-label="Audit Event Detailed Inspection"
    >
      <template v-if="selectedLog">
        <div class="drawer-content">
          <div class="detail-header">
            <el-tag :type="getStatusTagType(selectedLog.status)" effect="dark" size="large">
              {{ getStatusSymbol(selectedLog.status) }}{{ selectedLog.status }}
            </el-tag>
            <span class="detail-event-id">Event #{{ selectedLog.id }}</span>
          </div>

          <el-descriptions :column="1" border class="mt-4">
            <el-descriptions-item label="Timestamp">
              {{ formatTimestamp(selectedLog.timestamp) }}
            </el-descriptions-item>

            <el-descriptions-item label="Action / Event">
              <code>{{ selectedLog.action }}</code>
            </el-descriptions-item>

            <el-descriptions-item label="Actor Identity">
              {{ selectedLog.actorUsername || 'SYSTEM' }}
              <span v-if="selectedLog.userId" class="text-muted"> (User ID: {{ selectedLog.userId }})</span>
            </el-descriptions-item>

            <el-descriptions-item label="Client IP Address">
              {{ selectedLog.ipAddress || 'Internal / Direct Session' }}
            </el-descriptions-item>

            <el-descriptions-item label="Resource Type">
              {{ selectedLog.resourceType || 'N/A' }}
            </el-descriptions-item>

            <el-descriptions-item label="Resource ID">
              {{ selectedLog.resourceId || 'N/A' }}
            </el-descriptions-item>

            <el-descriptions-item label="Previous State">
              {{ selectedLog.beforeState || '(none)' }}
            </el-descriptions-item>

            <el-descriptions-item label="Resulting State">
              {{ selectedLog.afterState || '(none)' }}
            </el-descriptions-item>

            <el-descriptions-item label="Contextual Reason / Details">
              {{ selectedLog.details || 'No additional details recorded.' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
    </el-drawer>
  </main>
</template>

<style scoped>
.audit-view {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1.5rem;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.page-title {
  font-size: 1.6rem;
  font-weight: 700;
  margin: 0 0 0.25rem 0;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 0;
  font-size: 0.95rem;
  color: var(--el-text-color-secondary);
}

.policy-tag {
  font-size: 0.85rem;
  padding: 0.5rem 0.75rem;
}

.filter-card {
  background-color: var(--el-bg-color-overlay);
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.table-card {
  background-color: var(--el-bg-color-overlay);
}

.timestamp-cell {
  font-family: monospace;
  font-size: 0.85rem;
}

.action-tag {
  font-weight: 600;
  font-size: 0.85rem;
  color: var(--el-color-primary);
  font-family: monospace;
}

.actor-system {
  font-weight: 700;
  color: var(--el-color-warning);
  font-family: monospace;
}

.actor-user {
  font-size: 0.9rem;
}

.state-transition {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
}

.state-before {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.state-arrow {
  color: var(--el-text-color-placeholder);
  font-weight: bold;
}

.state-after {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.status-tag {
  font-weight: 700;
  font-size: 0.8rem;
  letter-spacing: 0.5px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

.text-muted {
  color: var(--el-text-color-placeholder);
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.detail-event-id {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--el-text-color-secondary);
}
</style>
