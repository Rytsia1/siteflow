<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const route = useRoute()
const activeTab = ref('borrow')
const highlightedId = ref('')

// ---- Tab 1: Borrow Requests ----
const requests = ref([])
const loading = ref(false)
const approvingId = ref(null)

const rejectDialogVisible = ref(false)
const rejectFormRef = ref()
const rejectTarget = ref(null)
const rejecting = ref(false)
const rejectForm = reactive({ note: '' })
const rejectRules = {
  note: [{ required: true, message: 'A rejection note is required', trigger: 'blur' }],
}

async function loadPending() {
  loading.value = true
  try {
    requests.value = await http.get('/approvals/borrow-requests/pending')
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  if (approvingId.value !== null) return

  approvingId.value = row.id
  try {
    await http.post(`/approvals/borrow-requests/${row.id}/approve`)
    ElMessage.success('Borrow request approved.')
    await loadPending()
  } catch {
    // interceptor already showed the error toast
  } finally {
    approvingId.value = null
  }
}

function openRejectDialog(row) {
  rejectTarget.value = row
  rejectForm.note = ''
  rejectDialogVisible.value = true
}

async function submitReject() {
  if (rejecting.value) return

  const valid = await rejectFormRef.value.validate().catch(() => false)
  if (!valid) return

  rejecting.value = true
  try {
    await http.post(`/approvals/borrow-requests/${rejectTarget.value.id}/reject`, {
      note: rejectForm.note,
    })
    ElMessage.success('Borrow request rejected.')
    rejectDialogVisible.value = false
    await loadPending()
  } catch {
    // interceptor already showed the error toast
  } finally {
    rejecting.value = false
  }
}

// ---- Tab 2: Material Requests ----
const materialRequests = ref([])
const loadingMr = ref(false)
const approvingMrId = ref(null)

const rejectMrDialogVisible = ref(false)
const rejectMrFormRef = ref()
const rejectMrTarget = ref(null)
const rejectingMr = ref(false)
const rejectMrForm = reactive({ note: '' })
const rejectMrRules = {
  note: [{ required: true, message: 'A rejection note is required', trigger: 'blur' }],
}

async function loadPendingMr() {
  loadingMr.value = true
  try {
    materialRequests.value = await http.get('/procurement/material-requests', {
      params: { status: 'SUBMITTED' },
    })
  } finally {
    loadingMr.value = false
  }
}

async function approveMr(row) {
  if (approvingMrId.value !== null) return

  approvingMrId.value = row.id
  try {
    await http.post(`/procurement/material-requests/${row.id}/approve`)
    ElMessage.success('Material request approved.')
    await loadPendingMr()
  } catch {
    // interceptor already showed the error toast
  } finally {
    approvingMrId.value = null
  }
}

function openRejectMrDialog(row) {
  rejectMrTarget.value = row
  rejectMrForm.note = ''
  rejectMrDialogVisible.value = true
}

async function submitRejectMr() {
  if (rejectingMr.value) return

  const valid = await rejectMrFormRef.value.validate().catch(() => false)
  if (!valid) return

  rejectingMr.value = true
  try {
    await http.post(`/procurement/material-requests/${rejectMrTarget.value.id}/reject`, {
      note: rejectMrForm.note,
    })
    ElMessage.success('Material request rejected.')
    rejectMrDialogVisible.value = false
    await loadPendingMr()
  } catch {
    // interceptor already showed the error toast
  } finally {
    rejectingMr.value = false
  }
}

function checkDeepLink() {
  if (route.query.tab === 'material' || route.query.tab === 'borrow') {
    activeTab.value = route.query.tab
  }
  const target = route.query.highlight || route.query.request || route.query.requestId || route.query.id
  if (target) {
    highlightedId.value = String(target).trim().toLowerCase()
    setTimeout(() => {
      highlightedId.value = ''
    }, 4500)
  }
}

function isHighlighted(row) {
  if (!highlightedId.value) return false
  const h = highlightedId.value
  const idStr = String(row.id).toLowerCase()
  if (idStr === h) return true
  // Match digits, e.g. "MR-2026-014" or "MR-#14" vs "14" or full reference match
  const digitsOnly = h.replace(/\D/g, '')
  if (digitsOnly && digitsOnly === idStr) return true
  if (h.includes(idStr)) return true
  return false
}

function getRowClass({ row }) {
  return isHighlighted(row) ? 'sf-row-highlight' : ''
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—'
}

onMounted(() => {
  checkDeepLink()
  loadPending()
  loadPendingMr()
})

watch(() => route.query, () => {
  checkDeepLink()
})
</script>

<template>
  <div class="approval-dashboard-view">
    <div class="tech-kicker">GET /api/approvals/borrow-requests/pending · ADMIN</div>
    <div class="page-header-row">
      <div>
        <h1 class="page-title">Approvals</h1>
        <p class="page-subtitle">One decision per item. Approving issues the asset or unlocks procurement; rejecting needs a reason.</p>
      </div>
    </div>

    <!-- Approvals Tabs -->
    <el-tabs v-model="activeTab" class="sf-approval-tabs">
      <!-- Borrow Requests Tab -->
      <el-tab-pane name="borrow">
        <template #label>
          <span class="tab-label">
            Borrow Requests
            <span class="sf-tab-counter">{{ requests.length }}</span>
          </span>
        </template>

        <div class="toolbar-row">
          <span class="section-kicker">PENDING BORROW DECISIONS</span>
          <button
            type="button"
            class="sf-btn-secondary"
            :disabled="loading"
            aria-label="Refresh pending borrow requests"
            @click="loadPending"
          >
            Refresh Queue
          </button>
        </div>

        <!-- Decision Cards Grid (Design Layout) -->
        <div v-if="requests.length > 0" class="sf-queue-grid">
          <div
            v-for="row in requests"
            :key="row.id"
            class="sf-approval-card"
            :class="{ 'sf-card-highlight': isHighlighted(row) }"
          >
            <div class="card-header">
              <span class="card-ref-id">BR-#{{ row.id }}</span>
              <span class="status-badge warning">BORROW REQUEST</span>
            </div>
            <div class="card-body">
              <div class="card-title">{{ row.requesterName }}</div>
              <div class="card-meta">Location: {{ row.locationName }}</div>

              <div class="card-facts-grid">
                <div>
                  <span class="fact-label">REQUESTER</span>
                  <span class="fact-value">{{ row.requesterName }}</span>
                </div>
                <div>
                  <span class="fact-label">REQUESTED ON</span>
                  <span class="fact-value">{{ formatDate(row.requestDate) }}</span>
                </div>
              </div>

              <div class="card-actions">
                <button
                  type="button"
                  class="sf-btn-danger"
                  :disabled="approvingId !== null"
                  :aria-label="`Reject borrow request #${row.id} for ${row.requesterName}`"
                  @click="openRejectDialog(row)"
                >
                  Reject
                </button>
                <button
                  type="button"
                  class="sf-btn-primary"
                  style="flex: 1 1 auto; justify-content: center"
                  :disabled="approvingId !== null"
                  :aria-label="`Approve borrow request #${row.id} for ${row.requesterName}`"
                  @click="approve(row)"
                >
                  {{ approvingId === row.id ? 'Approving...' : 'Approve' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="!loading" class="sf-empty-box">
          <div class="empty-code">QUEUE CLEAR</div>
          <div class="empty-text">No pending borrow requests awaiting approval.</div>
        </div>

        <!-- Accessible Table for Keyboard & Bulk Inspection -->
        <div class="accessible-table-container" style="margin-top: 24px" tabindex="0" role="region" aria-label="Pending Borrow Requests Table">
          <el-table v-loading="loading" :data="requests" :row-class-name="getRowClass" stripe border>
            <el-table-column prop="id" label="ID" width="100">
              <template #default="{ row }">
                <span class="code-id">BR-#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="requesterName" label="Requester" min-width="180" />
            <el-table-column prop="locationName" label="Location" min-width="180" />
            <el-table-column label="Requested On" width="180">
              <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="200" fixed="right">
              <template #default="{ row }">
                <button
                  type="button"
                  class="sf-btn-primary"
                  style="height: 28px; padding: 0 10px; font-size: 12px; margin-right: 8px"
                  :disabled="approvingId !== null"
                  @click="approve(row)"
                >
                  Approve
                </button>
                <button
                  type="button"
                  class="sf-btn-danger"
                  style="height: 28px; padding: 0 10px; font-size: 12px"
                  :disabled="approvingId !== null"
                  @click="openRejectDialog(row)"
                >
                  Reject
                </button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="No pending borrow requests" />
            </template>
          </el-table>
        </div>

        <!-- Reject Modal -->
        <el-dialog
          v-model="rejectDialogVisible"
          title="Reject Borrow Request"
          width="440px"
          aria-modal="true"
        >
          <div class="tech-kicker">REASON REQUIRED · PERMANENT ACTION</div>
          <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-position="top">
            <el-form-item label="Reason for rejection" prop="note">
              <el-input
                v-model="rejectForm.note"
                type="textarea"
                :rows="3"
                placeholder="Shown to requester. Explain why this request is rejected."
                aria-label="Rejection note explanation"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <button type="button" class="sf-btn-secondary" @click="rejectDialogVisible = false">Cancel</button>
            <button type="button" class="sf-btn-danger" :disabled="rejecting" @click="submitReject">
              {{ rejecting ? 'Rejecting...' : 'Reject Request' }}
            </button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- Material Requests Tab -->
      <el-tab-pane name="material">
        <template #label>
          <span class="tab-label">
            Material Requests
            <span class="sf-tab-counter">{{ materialRequests.length }}</span>
          </span>
        </template>

        <div class="toolbar-row">
          <span class="section-kicker">PENDING REQUISITIONS</span>
          <button
            type="button"
            class="sf-btn-secondary"
            :disabled="loadingMr"
            aria-label="Refresh pending material requests"
            @click="loadPendingMr"
          >
            Refresh Queue
          </button>
        </div>

        <!-- Material Request Decision Cards -->
        <div v-if="materialRequests.length > 0" class="sf-queue-grid">
          <div
            v-for="row in materialRequests"
            :key="row.id"
            class="sf-approval-card"
            :class="{ 'sf-card-highlight': isHighlighted(row) }"
          >
            <div class="card-header">
              <span class="card-ref-id">MR-#{{ row.id }}</span>
              <span class="status-badge warning">MATERIAL REQUEST</span>
            </div>
            <div class="card-body">
              <div class="card-title">{{ row.requesterName }}</div>
              <div class="card-meta">{{ row.justification || 'Standard material requisition' }}</div>

              <div class="card-facts-grid">
                <div>
                  <span class="fact-label">REQUESTER</span>
                  <span class="fact-value">{{ row.requesterName }}</span>
                </div>
                <div>
                  <span class="fact-label">REQUESTED ON</span>
                  <span class="fact-value">{{ formatDate(row.requestDate) }}</span>
                </div>
              </div>

              <div class="card-actions">
                <button
                  type="button"
                  class="sf-btn-danger"
                  :disabled="approvingMrId !== null"
                  :aria-label="`Reject material request #${row.id} for ${row.requesterName}`"
                  @click="openRejectMrDialog(row)"
                >
                  Reject
                </button>
                <button
                  type="button"
                  class="sf-btn-primary"
                  style="flex: 1 1 auto; justify-content: center"
                  :disabled="approvingMrId !== null"
                  :aria-label="`Approve material request #${row.id} for ${row.requesterName}`"
                  @click="approveMr(row)"
                >
                  {{ approvingMrId === row.id ? 'Approving...' : 'Approve' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="!loadingMr" class="sf-empty-box">
          <div class="empty-code">QUEUE CLEAR</div>
          <div class="empty-text">No material requests currently awaiting approval.</div>
        </div>

        <!-- Accessible Table -->
        <div class="accessible-table-container" style="margin-top: 24px" tabindex="0" role="region" aria-label="Pending Material Requests Table">
          <el-table v-loading="loadingMr" :data="materialRequests" :row-class-name="getRowClass" stripe border>
            <el-table-column prop="id" label="MR ID" width="100">
              <template #default="{ row }">
                <span class="code-id">MR-#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="requesterName" label="Requester" min-width="180" />
            <el-table-column prop="justification" label="Justification" min-width="220" show-overflow-tooltip />
            <el-table-column label="Requested On" width="180">
              <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="200" fixed="right">
              <template #default="{ row }">
                <button
                  type="button"
                  class="sf-btn-primary"
                  style="height: 28px; padding: 0 10px; font-size: 12px; margin-right: 8px"
                  :disabled="approvingMrId !== null"
                  @click="approveMr(row)"
                >
                  Approve
                </button>
                <button
                  type="button"
                  class="sf-btn-danger"
                  style="height: 28px; padding: 0 10px; font-size: 12px"
                  :disabled="approvingMrId !== null"
                  @click="openRejectMrDialog(row)"
                >
                  Reject
                </button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="No pending material requests" />
            </template>
          </el-table>
        </div>

        <!-- Reject MR Modal -->
        <el-dialog
          v-model="rejectMrDialogVisible"
          title="Reject Material Request"
          width="440px"
          aria-modal="true"
        >
          <div class="tech-kicker">REASON REQUIRED · PERMANENT ACTION</div>
          <el-form ref="rejectMrFormRef" :model="rejectMrForm" :rules="rejectMrRules" label-position="top">
            <el-form-item label="Reason for rejection" prop="note">
              <el-input
                v-model="rejectMrForm.note"
                type="textarea"
                :rows="3"
                placeholder="Explain why this material request is rejected."
                aria-label="Rejection note explanation"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <button type="button" class="sf-btn-secondary" @click="rejectMrDialogVisible = false">Cancel</button>
            <button type="button" class="sf-btn-danger" :disabled="rejectingMr" @click="submitRejectMr">
              {{ rejectingMr ? 'Rejecting...' : 'Reject Request' }}
            </button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--siteflow-font-heading);
  font-size: 15px;
  letter-spacing: 0.04em;
}

.sf-tab-counter {
  font-family: var(--siteflow-font-mono);
  font-size: 11px;
  background-color: rgba(224, 177, 82, 0.12);
  color: #e0b152;
  border: 1px solid rgba(224, 177, 82, 0.35);
  padding: 1px 6px;
  border-radius: 2px;
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-kicker {
  font-family: var(--siteflow-font-mono);
  font-size: 10.5px;
  letter-spacing: 0.1em;
  color: var(--siteflow-text-placeholder);
}

.sf-queue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.sf-approval-card {
  border: 1px solid var(--siteflow-border-color);
  background-color: var(--siteflow-bg-card);
  border-radius: 3px;
  overflow: hidden;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  background-color: #0f1826;
  border-bottom: 1px solid var(--siteflow-border-color);
}

.card-ref-id {
  font-family: var(--siteflow-font-mono);
  font-size: 13px;
  font-weight: 500;
  color: var(--siteflow-accent);
}

.card-body {
  padding: 14px;
}

.card-title {
  font-family: var(--siteflow-font-heading);
  font-size: 17px;
  font-weight: 600;
  color: var(--siteflow-text-primary);
  margin-bottom: 3px;
}

.card-meta {
  font-size: 12.5px;
  color: var(--siteflow-text-muted);
  margin-bottom: 14px;
}

.card-facts-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
  padding: 10px 0;
  border-top: 1px solid var(--siteflow-border-subtle);
  border-bottom: 1px solid var(--siteflow-border-subtle);
}

.fact-label {
  display: block;
  font-family: var(--siteflow-font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  color: #6f8099;
}

.fact-value {
  display: block;
  font-size: 13px;
  color: #c6d3e6;
  margin-top: 2px;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.sf-empty-box {
  border: 1px dashed var(--siteflow-border-subtle);
  padding: 48px 20px;
  text-align: center;
  background-color: var(--siteflow-bg-card);
  border-radius: 3px;
}

.empty-code {
  font-family: var(--siteflow-font-mono);
  font-size: 12px;
  letter-spacing: 0.1em;
  color: #6f8099;
  margin-bottom: 6px;
}

.empty-text {
  font-size: 13.5px;
  color: #7a8aa3;
}
</style>
