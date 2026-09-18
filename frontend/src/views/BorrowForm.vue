<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const route = useRoute()
const activeTab = ref('new')
const highlightedCard = ref(false)

// ---- Tab 1: New Borrow Request ----
const formRef = ref()
const submitting = ref(false)
const items = ref([])
const locations = ref([])

const form = reactive({
  locationId: null,
  lines: [{ itemId: null, qty: 1 }],
})

const rules = {
  locationId: [{ required: true, message: 'Please choose a location', trigger: 'change' }],
}

function lineItemIdRule() {
  return [{ required: true, message: 'Choose an item', trigger: 'change' }]
}

async function loadOptions() {
  const [itemList, locationList] = await Promise.all([http.get('/items'), http.get('/locations')])
  items.value = itemList
  locations.value = locationList
}

function checkDeepLink() {
  if (route.query.tab === 'return' || route.query.tab === 'new') {
    activeTab.value = route.query.tab
  }
  const reqTarget = route.query.requestId || route.query.request || route.query.highlight
  if (reqTarget) {
    const digits = String(reqTarget).replace(/\D/g, '')
    const num = digits ? Number(digits) : Number(reqTarget)
    if (!isNaN(num) && num > 0) {
      returnForm.requestId = num
    }
    highlightedCard.value = true
    setTimeout(() => {
      highlightedCard.value = false
    }, 4500)
  }
  if (route.query.itemId) {
    const itemIdNum = Number(route.query.itemId)
    if (!isNaN(itemIdNum) && form.lines.length > 0) {
      form.lines[0].itemId = itemIdNum
    }
  }
}

onMounted(() => {
  loadOptions()
  checkDeepLink()
})

watch(() => route.query, () => {
  checkDeepLink()
})

function addLine() {
  form.lines.push({ itemId: null, qty: 1 })
}

function removeLine(index) {
  form.lines.splice(index, 1)
}

function hasDuplicateItems() {
  const ids = form.lines.map((l) => l.itemId).filter((id) => id !== null)
  return new Set(ids).size !== ids.length
}

async function handleSubmit() {
  if (submitting.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (form.lines.length === 0) {
    ElMessage.warning('Add at least one item to borrow.')
    return
  }
  if (hasDuplicateItems()) {
    ElMessage.warning('Each item can only appear once — adjust the quantity instead of adding it twice.')
    return
  }

  submitting.value = true
  try {
    await http.post('/borrow-requests', {
      locationId: form.locationId,
      items: form.lines.map((l) => ({ itemId: l.itemId, qty: l.qty })),
    })
    ElMessage.success('Borrow request submitted.')
    form.locationId = null
    form.lines = [{ itemId: null, qty: 1 }]
    formRef.value.clearValidate()
  } catch {
    // interceptor handled error
  } finally {
    submitting.value = false
  }
}

// ---- Tab 2: Process Return ----
const returnFormRef = ref()
const submittingReturn = ref(false)

const returnForm = reactive({
  requestId: null,
  lines: [{ borrowItemId: null, qty: 1 }],
})

const returnRules = {
  requestId: [{ required: true, message: 'Please enter a Borrow Request ID', trigger: 'change' }],
}

function returnItemIdRule() {
  return [{ required: true, message: 'Enter borrow item ID', trigger: 'change' }]
}

function addReturnLine() {
  returnForm.lines.push({ borrowItemId: null, qty: 1 })
}

function removeReturnLine(index) {
  returnForm.lines.splice(index, 1)
}

function hasDuplicateReturnItems() {
  const ids = returnForm.lines.map((l) => l.borrowItemId).filter((id) => id !== null)
  return new Set(ids).size !== ids.length
}

async function handleReturnSubmit() {
  if (submittingReturn.value) return

  const valid = await returnFormRef.value.validate().catch(() => false)
  if (!valid) return

  if (returnForm.lines.length === 0) {
    ElMessage.warning('Add at least one item to return.')
    return
  }
  if (hasDuplicateReturnItems()) {
    ElMessage.warning('Each borrow item can only appear once per return request.')
    return
  }

  submittingReturn.value = true
  try {
    await http.post(`/borrow-requests/${returnForm.requestId}/returns`, {
      items: returnForm.lines.map((l) => ({ borrowItemId: l.borrowItemId, qty: l.qty })),
    })
    ElMessage.success('Return processed successfully.')
    returnForm.requestId = null
    returnForm.lines = [{ borrowItemId: null, qty: 1 }]
    returnFormRef.value.clearValidate()
  } catch {
    // interceptor handled error
  } finally {
    submittingReturn.value = false
  }
}
</script>

<template>
  <div class="borrow-form-view">
    <div class="tech-kicker">POST /api/borrow-requests · POST /{id}/returns</div>
    <div class="page-header-row">
      <div>
        <h1 class="page-title">Borrowing</h1>
        <p class="page-subtitle">Who holds what, and when it is due back to the warehouse store.</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="sf-borrow-tabs">
      <!-- Tab 1: New Borrow Request -->
      <el-tab-pane label="New Borrow Request" name="new">
        <div class="sf-card-container">
          <div class="sf-form-card">
            <span class="sf-corner-mark top-left">+</span>
            <span class="sf-corner-mark top-right">+</span>
            <span class="sf-corner-mark bottom-left">+</span>
            <span class="sf-corner-mark bottom-right">+</span>

            <div class="card-header-bar">
              <span class="header-tag">NEW REQUEST REQUISITION</span>
            </div>

            <div class="form-content">
              <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
                <el-form-item label="Origin Store Location" prop="locationId">
                  <el-select
                    v-model="form.locationId"
                    placeholder="Select warehouse or site location"
                    aria-label="Select location"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="loc in locations"
                      :key="loc.id"
                      :label="loc.locationName"
                      :value="loc.id"
                    />
                  </el-select>
                </el-form-item>

                <div class="form-section-title">EQUIPMENT LINES</div>

                <div v-for="(line, index) in form.lines" :key="index" class="item-row">
                  <div class="item-col">
                    <el-form-item
                      :prop="`lines.${index}.itemId`"
                      :rules="lineItemIdRule()"
                      style="margin-bottom: 0"
                    >
                      <el-select
                        v-model="line.itemId"
                        placeholder="Select item to borrow"
                        :aria-label="`Select item for line ${index + 1}`"
                        filterable
                        style="width: 100%"
                      >
                        <el-option
                          v-for="item in items"
                          :key="item.id"
                          :label="`${item.itemCode} — ${item.name}`"
                          :value="item.id"
                        />
                      </el-select>
                    </el-form-item>
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
                      :disabled="form.lines.length === 1"
                      :aria-label="`Remove line ${index + 1}`"
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
                    aria-label="Add another item line"
                    @click="addLine"
                  >
                    + Add Another Item
                  </button>
                </div>

                <div class="submit-action-container">
                  <button
                    type="button"
                    class="sf-btn-primary"
                    style="height: 36px; padding: 0 18px"
                    :disabled="submitting"
                    @click="handleSubmit"
                  >
                    {{ submitting ? 'Submitting...' : 'Submit Borrow Request' }}
                  </button>
                </div>
              </el-form>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: Process Return -->
      <el-tab-pane label="Process Return" name="return">
        <div class="sf-card-container">
          <div class="sf-form-card" :class="{ 'sf-card-highlight': highlightedCard && activeTab === 'return' }">
            <span class="sf-corner-mark top-left">+</span>
            <span class="sf-corner-mark top-right">+</span>
            <span class="sf-corner-mark bottom-left">+</span>
            <span class="sf-corner-mark bottom-right">+</span>

            <div class="card-header-bar">
              <span class="header-tag">RETURN PROCESSING</span>
            </div>

            <div class="form-content">
              <el-form ref="returnFormRef" :model="returnForm" :rules="returnRules" label-position="top">
                <el-form-item label="Borrow Request ID" prop="requestId">
                  <el-input-number
                    v-model="returnForm.requestId"
                    :min="1"
                    controls-position="right"
                    placeholder="e.g. 1"
                    aria-label="Borrow Request ID"
                    style="width: 100%"
                  />
                </el-form-item>

                <div class="form-section-title">ITEMS TO RETURN</div>

                <div v-for="(line, index) in returnForm.lines" :key="index" class="item-row">
                  <div class="item-col">
                    <el-form-item
                      :prop="`lines.${index}.borrowItemId`"
                      :rules="returnItemIdRule()"
                      style="margin-bottom: 0"
                    >
                      <el-input-number
                        v-model="line.borrowItemId"
                        :min="1"
                        controls-position="right"
                        placeholder="Borrow Item Line ID"
                        :aria-label="`Borrow Item ID for line ${index + 1}`"
                        style="width: 100%"
                      />
                    </el-form-item>
                  </div>
                  <div class="qty-col">
                    <el-input-number
                      v-model="line.qty"
                      :min="1"
                      :aria-label="`Return quantity for line ${index + 1}`"
                      controls-position="right"
                      style="width: 110px"
                    />
                  </div>
                  <div class="action-col">
                    <button
                      type="button"
                      class="sf-btn-danger"
                      style="height: 32px; padding: 0 10px; font-size: 12px"
                      :disabled="returnForm.lines.length === 1"
                      :aria-label="`Remove line ${index + 1}`"
                      @click="removeReturnLine(index)"
                    >
                      ✕
                    </button>
                  </div>
                </div>

                <div class="add-row-container">
                  <button
                    type="button"
                    class="sf-btn-secondary"
                    aria-label="Add another return line"
                    @click="addReturnLine"
                  >
                    + Add Return Line
                  </button>
                </div>

                <div class="submit-action-container">
                  <button
                    type="button"
                    class="sf-btn-primary"
                    style="height: 36px; padding: 0 18px"
                    :disabled="submittingReturn"
                    @click="handleReturnSubmit"
                  >
                    {{ submittingReturn ? 'Processing...' : 'Confirm Return' }}
                  </button>
                </div>
              </el-form>
            </div>
          </div>
        </div>
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
</style>
