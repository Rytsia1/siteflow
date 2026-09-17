<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const activeTab = ref('borrow')

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

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

onMounted(() => {
  loadPending()
  loadPendingMr()
})
</script>

<template>
  <div>
    <el-tabs v-model="activeTab">
      <!-- Borrow Requests Tab -->
      <el-tab-pane label="Borrow Requests" name="borrow">
        <div class="toolbar">
          <h3>Pending Borrow Requests</h3>
          <el-button :loading="loading" @click="loadPending">Refresh</el-button>
        </div>

        <el-table v-loading="loading" :data="requests" stripe border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="requesterName" label="Requester" min-width="160" />
          <el-table-column prop="locationName" label="Location" min-width="160" />
          <el-table-column label="Requested On" width="180">
            <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
          </el-table-column>
          <el-table-column label="Actions" width="220" fixed="right">
            <template #default="{ row }">
              <el-button type="success" size="small" :loading="approvingId === row.id" @click="approve(row)">
                Approve
              </el-button>
              <el-button type="danger" size="small" @click="openRejectDialog(row)">Reject</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="No pending borrow requests" />
          </template>
        </el-table>

        <el-dialog v-model="rejectDialogVisible" title="Reject Borrow Request" width="420px">
          <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-position="top">
            <el-form-item label="Rejection Note" prop="note">
              <el-input
                v-model="rejectForm.note"
                type="textarea"
                :rows="3"
                placeholder="Explain why this request is rejected"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="rejectDialogVisible = false">Cancel</el-button>
            <el-button type="danger" :loading="rejecting" @click="submitReject">Reject</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- Material Requests Tab -->
      <el-tab-pane label="Material Requests" name="material">
        <div class="toolbar">
          <h3>Pending Material Requests</h3>
          <el-button :loading="loadingMr" @click="loadPendingMr">Refresh</el-button>
        </div>

        <el-table v-loading="loadingMr" :data="materialRequests" stripe border>
          <el-table-column prop="id" label="MR ID" width="90" />
          <el-table-column prop="requesterName" label="Requester" min-width="160" />
          <el-table-column prop="justification" label="Justification" min-width="220" show-overflow-tooltip />
          <el-table-column label="Requested On" width="180">
            <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
          </el-table-column>
          <el-table-column label="Actions" width="220" fixed="right">
            <template #default="{ row }">
              <el-button
                type="success"
                size="small"
                :loading="approvingMrId === row.id"
                @click="approveMr(row)"
              >
                Approve
              </el-button>
              <el-button type="danger" size="small" @click="openRejectMrDialog(row)">
                Reject
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="No pending material requests" />
          </template>
        </el-table>

        <el-dialog v-model="rejectMrDialogVisible" title="Reject Material Request" width="420px">
          <el-form ref="rejectMrFormRef" :model="rejectMrForm" :rules="rejectMrRules" label-position="top">
            <el-form-item label="Rejection Note" prop="note">
              <el-input
                v-model="rejectMrForm.note"
                type="textarea"
                :rows="3"
                placeholder="Explain why this material request is rejected"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="rejectMrDialogVisible = false">Cancel</el-button>
            <el-button type="danger" :loading="rejectingMr" @click="submitRejectMr">Reject</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
