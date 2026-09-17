<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../auth'

const items = ref([])
const loading = ref(false)
const searchText = ref('')
const categoryFilter = ref('')

const canAdjustStock = computed(() => auth.role === 'ADMIN' || auth.role === 'WAREHOUSE_STAFF')
const locations = ref([])
const adjustDialogVisible = ref(false)
const adjustFormRef = ref()
const adjusting = ref(false)
const adjustForm = reactive({
  itemId: null,
  locationId: null,
  adjustmentType: 'IN',
  qty: 1,
  reason: '',
})

const adjustRules = {
  itemId: [{ required: true, message: 'Please select an item', trigger: 'change' }],
  locationId: [{ required: true, message: 'Please select a location', trigger: 'change' }],
  adjustmentType: [{ required: true, message: 'Please select adjustment type', trigger: 'change' }],
  qty: [{ required: true, message: 'Quantity must be at least 1', trigger: 'change' }],
}

const categoryOptions = [
  { value: 'TOOL', label: 'Tool' },
  { value: 'CONSUMABLE', label: 'Consumable' },
  { value: 'LIFTING_GEAR', label: 'Lifting Gear' },
]

const categoryTagType = {
  TOOL: 'primary',
  CONSUMABLE: 'success',
  LIFTING_GEAR: 'warning',
}

function categoryLabel(category) {
  return categoryOptions.find((c) => c.value === category)?.label || category
}

const filteredItems = computed(() => {
  const term = searchText.value.trim().toLowerCase()
  return items.value.filter((item) => {
    const matchesText =
      !term || item.itemCode.toLowerCase().includes(term) || item.name.toLowerCase().includes(term)
    const matchesCategory = !categoryFilter.value || item.category === categoryFilter.value
    return matchesText && matchesCategory
  })
})

async function loadItems() {
  loading.value = true
  try {
    items.value = await http.get('/items')
  } finally {
    loading.value = false
  }
}

async function loadLocations() {
  if (locations.value.length === 0) {
    try {
      locations.value = await http.get('/locations')
    } catch {
      // interceptor already showed error toast
    }
  }
}

async function openAdjustmentDialog(row) {
  await loadLocations()
  adjustForm.itemId = row?.id || null
  adjustForm.locationId = null
  adjustForm.adjustmentType = 'IN'
  adjustForm.qty = 1
  adjustForm.reason = ''
  adjustDialogVisible.value = true
}

async function submitAdjustment() {
  const valid = await adjustFormRef.value.validate().catch(() => false)
  if (!valid) return

  adjusting.value = true
  try {
    await http.post('/stock-adjustments', {
      itemId: adjustForm.itemId,
      locationId: adjustForm.locationId,
      adjustmentType: adjustForm.adjustmentType,
      qty: adjustForm.qty,
      reason: adjustForm.reason?.trim() || null,
    })
    ElMessage.success('Stock adjustment recorded.')
    adjustDialogVisible.value = false
    await loadItems()
  } catch {
    // interceptor already showed the error toast
  } finally {
    adjusting.value = false
  }
}

onMounted(() => {
  loadItems()
  if (canAdjustStock.value) {
    loadLocations()
  }
})
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="searchText"
        placeholder="Search by item code or name"
        clearable
        style="width: 280px"
      />
      <el-select v-model="categoryFilter" placeholder="All categories" clearable style="width: 200px">
        <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-button :loading="loading" @click="loadItems">Refresh</el-button>
      <el-button v-if="canAdjustStock" type="primary" @click="openAdjustmentDialog()">
        Adjust Stock
      </el-button>
    </div>

    <el-table v-loading="loading" :data="filteredItems" stripe border>
      <el-table-column prop="itemCode" label="Item Code" width="140" />
      <el-table-column prop="name" label="Name" min-width="200" />
      <el-table-column label="Category" width="150">
        <template #default="{ row }">
          <el-tag :type="categoryTagType[row.category] || 'info'">{{ categoryLabel(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="Unit" width="100" />
      <el-table-column label="Current Stock" width="160">
        <template #default="{ row }">
          <el-tag :type="row.totalQty <= row.minStockThreshold ? 'danger' : 'success'">
            {{ row.totalQty }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canAdjustStock" label="Actions" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openAdjustmentDialog(row)">Adjust</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="No items found" />
      </template>
    </el-table>

    <el-dialog v-model="adjustDialogVisible" title="Adjust Stock" width="480px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-position="top">
        <el-form-item label="Item" prop="itemId">
          <el-select v-model="adjustForm.itemId" placeholder="Select item" filterable style="width: 100%">
            <el-option
              v-for="item in items"
              :key="item.id"
              :label="`${item.itemCode} — ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Location" prop="locationId">
          <el-select v-model="adjustForm.locationId" placeholder="Select location" filterable style="width: 100%">
            <el-option
              v-for="loc in locations"
              :key="loc.id"
              :label="loc.locationName"
              :value="loc.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Adjustment Type" prop="adjustmentType">
          <el-radio-group v-model="adjustForm.adjustmentType">
            <el-radio-button label="IN">Stock In (+)</el-radio-button>
            <el-radio-button label="OUT">Stock Out (-)</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Quantity" prop="qty">
          <el-input-number v-model="adjustForm.qty" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Reason" prop="reason">
          <el-input
            v-model="adjustForm.reason"
            type="textarea"
            :rows="2"
            placeholder="Reason for stock correction (e.g. cycle count discrepancy, damaged goods)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="adjusting" @click="submitAdjustment">
          Submit Adjustment
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
