<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../auth'

const isAdmin = computed(() => auth.role === 'ADMIN' || auth.role === 'PROCUREMENT')
// Approve/reject is ADMIN-only (see backend ProcurementController), unlike list/generate-PO
// which also allow PROCUREMENT — isAdmin above must not be reused here or a PROCUREMENT
// user would see Approve/Reject buttons that always fail with 403.
const canApprove = computed(() => auth.role === 'ADMIN')
const activeTab = ref('submit')

// ---- Tab 1: Submit Material Request ----
const items = ref([])
const submitting = ref(false)
const mrForm = reactive({
  justification: '',
  lines: [{ itemId: null, qty: 1 }],
})

function addLine() {
  mrForm.lines.push({ itemId: null, qty: 1 })
}

function removeLine(index) {
  mrForm.lines.splice(index, 1)
}

async function loadItems() {
  items.value = await http.get('/items')
}

async function submitMaterialRequest() {
  if (mrForm.lines.some((l) => !l.itemId)) {
    ElMessage.warning('Choose an item for every line.')
    return
  }

  submitting.value = true
  try {
    await http.post('/procurement/material-requests', {
      justification: mrForm.justification,
      items: mrForm.lines.map((l) => ({ itemId: l.itemId, qty: l.qty })),
    })
    ElMessage.success('Material request submitted.')
    mrForm.justification = ''
    mrForm.lines = [{ itemId: null, qty: 1 }]
  } catch {
    // interceptor already showed the error toast
  } finally {
    submitting.value = false
  }
}

// ---- Tab 2: Pending Approval ----
const pendingRequests = ref([])
const loadingPending = ref(false)
const approvingId = ref(null)

const rejectDialogVisible = ref(false)
const rejectFormRef = ref()
const rejectTarget = ref(null)
const rejecting = ref(false)
const rejectForm = reactive({ note: '' })
const rejectRules = {
  note: [{ required: true, message: 'A rejection note is required', trigger: 'blur' }],
}

async function loadPendingRequests() {
  loadingPending.value = true
  try {
    pendingRequests.value = await http.get('/procurement/material-requests', {
      params: { status: 'SUBMITTED' },
    })
  } finally {
    loadingPending.value = false
  }
}

async function approve(row) {
  approvingId.value = row.id
  try {
    await http.post(`/procurement/material-requests/${row.id}/approve`)
    ElMessage.success('Material request approved.')
    // The approved request leaves Pending and belongs on the Approved tab now.
    await Promise.all([loadPendingRequests(), loadApprovedRequests()])
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
    await http.post(`/procurement/material-requests/${rejectTarget.value.id}/reject`, {
      note: rejectForm.note,
    })
    ElMessage.success('Material request rejected.')
    rejectDialogVisible.value = false
    await loadPendingRequests()
  } catch {
    // interceptor already showed the error toast
  } finally {
    rejecting.value = false
  }
}

// ---- Tab 3: Approved Requests -> Generate PO ----
const approvedRequests = ref([])
const loadingApproved = ref(false)
const poDialogVisible = ref(false)
const poFormRef = ref()
const poTarget = ref(null)
const generating = ref(false)
const poForm = reactive({ supplierName: '', expectedDeliveryDate: null })
const poRules = {
  supplierName: [{ required: true, message: 'Supplier name is required', trigger: 'blur' }],
}

async function loadApprovedRequests() {
  loadingApproved.value = true
  try {
    approvedRequests.value = await http.get('/procurement/material-requests', {
      params: { status: 'APPROVED' },
    })
  } finally {
    loadingApproved.value = false
  }
}

function openPoDialog(row) {
  poTarget.value = row
  poForm.supplierName = ''
  poForm.expectedDeliveryDate = null
  poDialogVisible.value = true
}

async function submitGeneratePo() {
  const valid = await poFormRef.value.validate().catch(() => false)
  if (!valid) return

  generating.value = true
  try {
    await http.post(`/procurement/material-requests/${poTarget.value.id}/generate-po`, {
      supplierName: poForm.supplierName,
      expectedDeliveryDate: poForm.expectedDeliveryDate,
    })
    ElMessage.success('Purchase order generated.')
    poDialogVisible.value = false
    await loadApprovedRequests()
  } catch {
    // interceptor already showed the error toast
  } finally {
    generating.value = false
  }
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString() : '—'
}

onMounted(() => {
  loadItems()
  if (canApprove.value) {
    loadPendingRequests()
  }
  if (isAdmin.value) {
    loadApprovedRequests()
  }
})
</script>

<template>
  <el-tabs v-model="activeTab">
    <el-tab-pane label="New Material Request" name="submit">
      <el-card style="max-width: 640px">
        <el-form label-position="top">
          <el-form-item label="Justification">
            <el-input
              v-model="mrForm.justification"
              type="textarea"
              :rows="2"
              placeholder="Why is this material needed?"
            />
          </el-form-item>

          <el-form-item label="Items">
            <div v-for="(line, index) in mrForm.lines" :key="index" class="item-row">
              <el-select v-model="line.itemId" placeholder="Search item" filterable class="item-select">
                <el-option
                  v-for="item in items"
                  :key="item.id"
                  :label="`${item.itemCode} — ${item.name}`"
                  :value="item.id"
                />
              </el-select>
              <el-input-number v-model="line.qty" :min="1" />
              <el-button
                type="danger"
                plain
                :disabled="mrForm.lines.length === 1"
                @click="removeLine(index)"
              >
                Remove
              </el-button>
            </div>
            <el-button @click="addLine">+ Add Item</el-button>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submitMaterialRequest">
              Submit Material Request
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </el-tab-pane>

    <el-tab-pane v-if="canApprove" label="Pending Approval" name="pending">
      <div class="toolbar">
        <h3>Pending Material Requests</h3>
        <el-button :loading="loadingPending" @click="loadPendingRequests">Refresh</el-button>
      </div>

      <el-table v-loading="loadingPending" :data="pendingRequests" stripe border>
        <el-table-column prop="id" label="MR ID" width="90" />
        <el-table-column prop="requesterName" label="Requester" min-width="160" />
        <el-table-column prop="justification" label="Justification" min-width="220" show-overflow-tooltip />
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
    </el-tab-pane>

    <el-tab-pane v-if="isAdmin" label="Approved Requests" name="approved">
      <div class="toolbar">
        <h3>Approved Material Requests</h3>
        <el-button :loading="loadingApproved" @click="loadApprovedRequests">Refresh</el-button>
      </div>

      <el-table v-loading="loadingApproved" :data="approvedRequests" stripe border>
        <el-table-column prop="id" label="MR ID" width="90" />
        <el-table-column prop="requesterName" label="Requester" min-width="160" />
        <el-table-column prop="justification" label="Justification" min-width="220" show-overflow-tooltip />
        <el-table-column label="Requested On" width="180">
          <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openPoDialog(row)">Generate PO</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="No approved requests" />
        </template>
      </el-table>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="rejectDialogVisible" title="Reject Material Request" width="420px">
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

  <el-dialog v-model="poDialogVisible" title="Generate Purchase Order" width="420px">
    <el-form ref="poFormRef" :model="poForm" :rules="poRules" label-position="top">
      <el-form-item label="Supplier Name" prop="supplierName">
        <el-input v-model="poForm.supplierName" placeholder="Supplier name" />
      </el-form-item>
      <el-form-item label="Expected Delivery Date">
        <el-date-picker
          v-model="poForm.expectedDeliveryDate"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="poDialogVisible = false">Cancel</el-button>
      <el-button type="primary" :loading="generating" @click="submitGeneratePo">Generate</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.item-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.item-select {
  flex: 1;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
</style>
