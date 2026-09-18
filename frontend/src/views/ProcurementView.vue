<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../auth'

const route = useRoute()
const isAdmin = computed(() => auth.role === 'ADMIN' || auth.role === 'PROCUREMENT')
const canApprove = computed(() => auth.role === 'ADMIN')
const activeTab = ref('submit')
const highlightedId = ref('')

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
  if (submitting.value) return

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
    if (canApprove.value) {
      await loadPendingRequests()
    }
  } catch {
    // handled by interceptor
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
  if (approvingId.value !== null) return

  approvingId.value = row.id
  try {
    await http.post(`/procurement/material-requests/${row.id}/approve`)
    ElMessage.success('Material request approved.')
    await loadPendingRequests()
    if (isAdmin.value) {
      await loadApprovedRequests()
    }
  } catch {
    // handled by interceptor
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
    await http.post(`/procurement/material-requests/${rejectTarget.value.id}/reject`, {
      note: rejectForm.note,
    })
    ElMessage.success('Material request rejected.')
    rejectDialogVisible.value = false
    await loadPendingRequests()
  } catch {
    // handled by interceptor
  } finally {
    rejecting.value = false
  }
}

// ---- Tab 3: Approved Requests & PO Generation ----
const approvedRequests = ref([])
const loadingApproved = ref(false)

const poDialogVisible = ref(false)
const poFormRef = ref()
const poTarget = ref(null)
const generating = ref(false)
const poForm = reactive({
  supplierName: '',
  expectedDeliveryDate: null,
})
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
  if (generating.value) return

  const valid = await poFormRef.value.validate().catch(() => false)
  if (!valid) return

  generating.value = true
  try {
    const po = await http.post(`/procurement/material-requests/${poTarget.value.id}/generate-po`, {
      supplierName: poForm.supplierName,
      expectedDeliveryDate: poForm.expectedDeliveryDate,
    })
    ElMessage.success(`Purchase Order ${po.poNumber} generated.`)
    poDialogVisible.value = false
    await loadApprovedRequests()
    await loadPurchaseOrders()
  } catch {
    // handled by interceptor
  } finally {
    generating.value = false
  }
}

// ---- Tab 4: Purchase Orders List ----
const purchaseOrders = ref([])
const loadingPos = ref(false)

async function loadPurchaseOrders() {
  loadingPos.value = true
  try {
    purchaseOrders.value = await http.get('/procurement/purchase-orders')
  } catch {
    // quiet fallback
  } finally {
    loadingPos.value = false
  }
}

function formatDate(value) {
  return value ? new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }) : '—'
}

function checkDeepLink() {
  if (route.query.tab) {
    const validTabs = ['submit', 'pending', 'approved', 'pos']
    if (validTabs.includes(route.query.tab)) {
      activeTab.value = route.query.tab
    }
  }
  const target = route.query.highlight || route.query.poNumber || route.query.requestId || route.query.id
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
  if (row.id && String(row.id).toLowerCase() === h) return true
  const digitsOnly = h.replace(/\D/g, '')
  if (digitsOnly && row.id && String(row.id) === digitsOnly) return true
  if (row.poNumber && String(row.poNumber).toLowerCase().includes(h)) return true
  if (h.includes(String(row.id))) return true
  return false
}

function getRowClass({ row }) {
  return isHighlighted(row) ? 'sf-row-highlight' : ''
}

onMounted(async () => {
  checkDeepLink()
  await loadItems()
  if (canApprove.value) {
    await loadPendingRequests()
  }
  if (isAdmin.value) {
    await loadApprovedRequests()
    await loadPurchaseOrders()
  }
})

watch(() => route.query, () => {
  checkDeepLink()
})
</script>

<template>
  <div class="procurement-view">
    <div class="tech-kicker">POST /api/procurement/material-requests/{id}/generate-po · ADMIN, PROCUREMENT</div>
    <div class="page-header-row">
      <div>
        <h1 class="page-title">Procurement &amp; Material Requests</h1>
        <p class="page-subtitle">Every purchase order originates from an approved site material requisition.</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="sf-procurement-tabs">
      <!-- Tab 1: New Material Request -->
      <el-tab-pane label="New Material Request" name="submit">
        <div class="sf-card-container">
          <div class="sf-form-card">
            <span class="sf-corner-mark top-left">+</span>
            <span class="sf-corner-mark top-right">+</span>
            <span class="sf-corner-mark bottom-left">+</span>
            <span class="sf-corner-mark bottom-right">+</span>

            <div class="card-header-bar">
              <span class="header-tag">MATERIAL REQUISITION FORM</span>
            </div>

            <div class="form-content">
              <el-form label-position="top">
                <el-form-item label="Operational Justification">
                  <el-input
                    v-model="mrForm.justification"
                    type="textarea"
                    :rows="3"
                    placeholder="Why is this material needed on site? (e.g. pour scheduled for east retaining wall)"
                    aria-label="Justification for material request"
                  />
                </el-form-item>

                <div class="form-section-title">REQUIRED MATERIALS</div>

                <div v-for="(line, index) in mrForm.lines" :key="index" class="item-row">
                  <div class="item-col">
                    <el-select
                      v-model="line.itemId"
                      placeholder="Search material or consumable"
                      filterable
                      :aria-label="`Material item for line ${index + 1}`"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="item in items"
                        :key="item.id"
                        :label="`${item.itemCode} — ${item.name}`"
                        :value="item.id"
                      />
                    </el-select>
                  </div>
                  <div class="qty-col">
                    <el-input-number
                      v-model="line.qty"
                      :min="1"
                      :aria-label="`Quantity for line ${index + 1}`"
                      controls-position="right"
                      style="width: 110px"
                    />
                  </div>
                  <div class="action-col">
                    <button
                      type="button"
                      class="sf-btn-danger"
                      style="height: 32px; padding: 0 10px; font-size: 12px"
                      :disabled="mrForm.lines.length === 1"
                      :aria-label="`Remove material line ${index + 1}`"
                      @click="removeLine(index)"
                    >
                      ✕
                    </button>
                  </div>
                </div>

                <div class="add-row-container">
                  <button
                    type="button"
                    class="sf-btn-secondary"
                    aria-label="Add another material item line"
                    @click="addLine"
                  >
                    + Add Item Line
                  </button>
                </div>

                <div class="submit-action-container">
                  <button
                    type="button"
                    class="sf-btn-primary"
                    style="height: 36px; padding: 0 18px"
                    :disabled="submitting"
                    @click="submitMaterialRequest"
                  >
                    {{ submitting ? 'Submitting...' : 'Submit Material Request' }}
                  </button>
                </div>
              </el-form>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Pending Approval -->
      <el-tab-pane v-if="canApprove" name="pending">
        <template #label>
          <span class="tab-label">
            Pending Approval
            <span class="sf-tab-counter">{{ pendingRequests.length }}</span>
          </span>
        </template>

        <div class="toolbar-row">
          <span class="section-kicker">REQUISITIONS AWAITING DECISION</span>
          <button
            type="button"
            class="sf-btn-secondary"
            :disabled="loadingPending"
            aria-label="Refresh pending material requests"
            @click="loadPendingRequests"
          >
            Refresh
          </button>
        </div>

        <div class="accessible-table-container" tabindex="0" role="region" aria-label="Pending Material Requests Table">
          <el-table v-loading="loadingPending" :data="pendingRequests" :row-class-name="getRowClass" stripe border>
            <el-table-column prop="id" label="MR ID" width="100">
              <template #default="{ row }">
                <span class="code-id">MR-#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="requesterName" label="Requester" min-width="170" />
            <el-table-column prop="justification" label="Justification" min-width="240" show-overflow-tooltip />
            <el-table-column label="Requested On" width="160">
              <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
            </el-table-column>
            <el-table-column label="Actions" width="200" fixed="right">
              <template #default="{ row }">
                <button
                  type="button"
                  class="sf-btn-primary"
                  style="height: 28px; padding: 0 10px; font-size: 12px; margin-right: 8px"
                  :disabled="approvingId !== null"
                  :aria-label="`Approve material request #${row.id} for ${row.requesterName}`"
                  @click="approve(row)"
                >
                  Approve
                </button>
                <button
                  type="button"
                  class="sf-btn-danger"
                  style="height: 28px; padding: 0 10px; font-size: 12px"
                  :disabled="approvingId !== null"
                  :aria-label="`Reject material request #${row.id} for ${row.requesterName}`"
                  @click="openRejectDialog(row)"
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
      </el-tab-pane>

      <!-- Tab 3: Approved Requests & Generate PO -->
      <el-tab-pane v-if="isAdmin" name="approved">
        <template #label>
          <span class="tab-label">
            Approved Requests
            <span class="sf-tab-counter">{{ approvedRequests.length }}</span>
          </span>
        </template>

        <div class="toolbar-row">
          <span class="section-kicker">READY FOR PURCHASE ORDER ISSUANCE</span>
          <button
            type="button"
            class="sf-btn-secondary"
            :disabled="loadingApproved"
            aria-label="Refresh approved material requests"
            @click="loadApprovedRequests"
          >
            Refresh
          </button>
        </div>

        <div class="accessible-table-container" tabindex="0" role="region" aria-label="Approved Material Requests Table">
          <el-table v-loading="loadingApproved" :data="approvedRequests" :row-class-name="getRowClass" stripe border>
            <el-table-column prop="id" label="MR ID" width="100">
              <template #default="{ row }">
                <span class="code-id">MR-#{{ row.id }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="requesterName" label="Requester" min-width="170" />
            <el-table-column prop="justification" label="Justification" min-width="240" show-overflow-tooltip />
            <el-table-column label="Approved On" width="160">
              <template #default="{ row }">{{ formatDate(row.requestDate) }}</template>
            </el-table-column>
            <el-table-column label="Action" width="150" fixed="right">
              <template #default="{ row }">
                <button
                  type="button"
                  class="sf-btn-primary"
                  style="height: 28px; padding: 0 10px; font-size: 12px"
                  :aria-label="`Generate Purchase Order for material request #${row.id}`"
                  @click="openPoDialog(row)"
                >
                  Generate PO
                </button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="No approved requests waiting for purchase orders" />
            </template>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- Tab 4: Purchase Orders -->
      <el-tab-pane v-if="isAdmin" name="pos">
        <template #label>
          <span class="tab-label">
            Purchase Orders
            <span class="sf-tab-counter">{{ purchaseOrders.length }}</span>
          </span>
        </template>

        <div class="toolbar-row">
          <span class="section-kicker">ISSUED ORDERS &amp; FULFILLMENT</span>
          <button
            type="button"
            class="sf-btn-secondary"
            :disabled="loadingPos"
            aria-label="Refresh purchase orders"
            @click="loadPurchaseOrders"
          >
            Refresh
          </button>
        </div>

        <div class="accessible-table-container" tabindex="0" role="region" aria-label="Purchase Orders Table">
          <el-table v-loading="loadingPos" :data="purchaseOrders" :row-class-name="getRowClass" stripe border>
            <el-table-column prop="poNumber" label="PO Number" width="160">
              <template #default="{ row }">
                <span class="code-id">{{ row.poNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="supplierName" label="Supplier" min-width="200" />
            <el-table-column label="Status" width="140">
              <template #default="{ row }">
                <span class="status-badge info">{{ row.status || 'ISSUED' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="Expected Delivery" width="180">
              <template #default="{ row }">{{ formatDate(row.expectedDeliveryDate) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="No purchase orders issued yet" />
            </template>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Reject Requisition Dialog -->
    <el-dialog v-model="rejectDialogVisible" title="Reject Material Request" width="440px" aria-modal="true">
      <div class="tech-kicker">REASON REQUIRED · PERMANENT ACTION</div>
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-position="top">
        <el-form-item label="Reason for rejection" prop="note">
          <el-input
            v-model="rejectForm.note"
            type="textarea"
            :rows="3"
            placeholder="Explain why this request is rejected"
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

    <!-- Generate PO Dialog -->
    <el-dialog v-model="poDialogVisible" title="Generate Purchase Order" width="460px" aria-modal="true">
      <div class="tech-kicker">POST /api/procurement/material-requests/{id}/generate-po</div>
      <el-form ref="poFormRef" :model="poForm" :rules="poRules" label-position="top">
        <el-form-item label="Supplier Name" prop="supplierName">
          <el-input v-model="poForm.supplierName" placeholder="e.g. Karya Beton Sentosa" aria-label="Supplier Name" />
        </el-form-item>
        <el-form-item label="Expected Delivery Date">
          <el-date-picker
            v-model="poForm.expectedDeliveryDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            aria-label="Expected Delivery Date"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <button type="button" class="sf-btn-secondary" @click="poDialogVisible = false">Cancel</button>
        <button type="button" class="sf-btn-primary" :disabled="generating" @click="submitGeneratePo">
          {{ generating ? 'Generating...' : 'Generate Purchase Order' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.sf-card-container {
  max-width: 680px;
  margin-top: 10px;
}

.sf-form-card {
  position: relative;
  background-color: var(--siteflow-bg-card);
  border: 1px solid var(--siteflow-border-color);
  border-radius: 3px;
  overflow: hidden;
}

.card-header-bar {
  padding: 12px 18px;
  background-color: #0f1826;
  border-bottom: 1px solid var(--siteflow-border-color);
}

.header-tag {
  font-family: var(--siteflow-font-mono);
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--siteflow-text-placeholder);
}

.form-content {
  padding: 22px 20px;
}

.form-section-title {
  font-family: var(--siteflow-font-mono);
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: #6f8099;
  margin: 16px 0 10px;
}

.item-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.item-col {
  flex: 1 1 auto;
}

.qty-col {
  flex: 0 0 auto;
}

.action-col {
  flex: 0 0 auto;
}

.add-row-container {
  margin-top: 8px;
  margin-bottom: 20px;
}

.submit-action-container {
  padding-top: 16px;
  border-top: 1px solid var(--siteflow-border-subtle);
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
  background-color: rgba(143, 182, 221, 0.12);
  color: #8fb6dd;
  border: 1px solid rgba(143, 182, 221, 0.35);
  padding: 1px 6px;
  border-radius: 2px;
}
</style>
