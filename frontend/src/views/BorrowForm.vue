<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const activeTab = ref('new')

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

onMounted(loadOptions)

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
    // interceptor already showed the error toast (e.g. insufficient stock)
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
    // interceptor already showed the error toast
  } finally {
    submittingReturn.value = false
  }
}
</script>

<template>
  <div class="borrow-form-view">
    <h1 class="page-title">Tool Borrowing & Returns</h1>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="New Borrow Request" name="new">
        <el-card style="max-width: 640px">
          <template #header>
            <h2 class="section-title">New Borrow Request</h2>
          </template>

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <el-form-item label="Location" prop="locationId">
              <el-select
                v-model="form.locationId"
                placeholder="Select warehouse or site"
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

            <el-form-item label="Items to Borrow">
              <div v-for="(line, index) in form.lines" :key="index" class="item-row">
                <el-form-item
                  :prop="`lines.${index}.itemId`"
                  :rules="lineItemIdRule()"
                  class="item-select"
                >
                  <el-select
                    v-model="line.itemId"
                    placeholder="Select item"
                    :aria-label="`Select item for line ${index + 1}`"
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in items"
                      :key="item.id"
                      :label="`${item.itemCode} - ${item.name}`"
                      :value="item.id"
                    />
                  </el-select>
                </el-form-item>
                <el-input-number
                  v-model="line.qty"
                  :min="1"
                  :aria-label="`Quantity for line ${index + 1}`"
                  controls-position="right"
                />
                <el-button
                  type="danger"
                  plain
                  :disabled="form.lines.length === 1"
                  :aria-label="`Remove line ${index + 1}`"
                  @click="removeLine(index)"
                >
                  Remove
                </el-button>
              </div>
              <el-button aria-label="Add another item line" @click="addLine">+ Add Item</el-button>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="submitting" :disabled="submitting" @click="handleSubmit">
                Submit Borrow Request
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="Process Return" name="return">
        <el-card style="max-width: 640px">
          <template #header>
            <h2 class="section-title">Process Return</h2>
          </template>

          <el-form ref="returnFormRef" :model="returnForm" :rules="returnRules" label-position="top">
            <el-form-item label="Borrow Request ID" prop="requestId">
              <el-input-number
                v-model="returnForm.requestId"
                :min="1"
                controls-position="right"
                placeholder="Enter Borrow Request ID"
                aria-label="Borrow Request ID"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="Items to Return">
              <div v-for="(line, index) in returnForm.lines" :key="index" class="item-row">
                <el-form-item
                  :prop="`lines.${index}.borrowItemId`"
                  :rules="returnItemIdRule()"
                  class="item-select"
                >
                  <el-input-number
                    v-model="line.borrowItemId"
                    :min="1"
                    controls-position="right"
                    placeholder="Borrow Item ID"
                    :aria-label="`Borrow Item ID for line ${index + 1}`"
                    style="width: 100%"
                  />
                </el-form-item>
                <el-input-number
                  v-model="line.qty"
                  :min="1"
                  :aria-label="`Return quantity for line ${index + 1}`"
                  controls-position="right"
                />
                <el-button
                  type="danger"
                  plain
                  :disabled="returnForm.lines.length === 1"
                  :aria-label="`Remove return line ${index + 1}`"
                  @click="removeReturnLine(index)"
                >
                  Remove
                </el-button>
              </div>
              <el-button aria-label="Add another return item line" @click="addReturnLine">+ Add Item</el-button>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="submittingReturn" :disabled="submittingReturn" @click="handleReturnSubmit">
                Process Return
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
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
  margin-bottom: 0;
}
</style>
