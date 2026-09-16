<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

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

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

onMounted(loadPending)
</script>

<template>
  <div>
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
        <el-empty description="No pending requests" />
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
