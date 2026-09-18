<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../auth'

const route = useRoute()
const items = ref([])
const loading = ref(false)
const searchText = ref('')
const categoryFilter = ref('')
const highlightedCode = ref('')

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

// Computed KPI tiles for the design
const lowStockCount = computed(() => items.value.filter((i) => i.totalQty <= i.minStockThreshold).length)
const toolCount = computed(() => items.value.filter((i) => i.category === 'TOOL' || i.category === 'LIFTING_GEAR').length)
const healthyCount = computed(() => items.value.filter((i) => i.totalQty > i.minStockThreshold).length)

function checkDeepLink() {
  const target = route.query.itemCode || route.query.highlight || route.query.q
  if (target) {
    const code = String(target).trim()
    searchText.value = code
    highlightedCode.value = code.toLowerCase()

    if (route.query.action === 'adjust' && canAdjustStock.value && items.value.length) {
      const found = items.value.find(
        (i) => i.itemCode.toLowerCase() === code.toLowerCase() || String(i.id) === code
      )
      if (found) {
        openAdjustmentDialog(found)
      }
    }

    setTimeout(() => {
      highlightedCode.value = ''
    }, 4500)
  }
}

function isRowHighlighted(row) {
  if (!highlightedCode.value) return false
  return (
    row.itemCode.toLowerCase().includes(highlightedCode.value) ||
    String(row.id) === highlightedCode.value
  )
}

function getRowClass({ row }) {
  return isRowHighlighted(row) ? 'sf-row-highlight' : ''
}

async function loadItems() {
  loading.value = true
  try {
    items.value = await http.get('/items')
    checkDeepLink()
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
  if (adjusting.value) return

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
    ElMessage.success('Stock adjusted successfully.')
    adjustDialogVisible.value = false
    await loadItems()
  } catch {
    // interceptor handled error
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

watch(() => route.query, () => checkDeepLink())
</script>

<template>
  <div class="inventory-page">
    <div class="tech-kicker">GET /api/items · CATALOG &amp; STOCKS</div>
    <div class="page-header-row">
      <div>
        <h1 class="page-title">Inventory</h1>
        <p class="page-subtitle">Materials, tools and lifting gear across all site stores.</p>
      </div>
      <div class="header-actions">
        <button
          v-if="canAdjustStock"
          type="button"
          class="sf-btn-primary"
          aria-label="Open manual stock adjustment dialog"
          @click="openAdjustmentDialog()"
        >
          Adjust Stock
        </button>
      </div>
    </div>

    <!-- Stat Tiles (Industrial Design) -->
    <div class="sf-stat-grid" role="region" aria-label="Inventory Metrics Summary">
      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">TOTAL ITEMS</div>
        <div class="sf-stat-value" style="color: #F0F5FB">{{ items.length }}</div>
        <div class="sf-stat-note">Registered catalog</div>
      </div>

      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">LOW / OUT OF STOCK</div>
        <div class="sf-stat-value" :style="{ color: lowStockCount > 0 ? '#E8756A' : '#5FC08A' }">
          {{ lowStockCount }}
        </div>
        <div class="sf-stat-note">Items below threshold</div>
      </div>

      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">TOOLS &amp; GEAR</div>
        <div class="sf-stat-value" style="color: #8FB6DD">{{ toolCount }}</div>
        <div class="sf-stat-note">Tracked equipment</div>
      </div>

      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">IN STOCK (NORMAL)</div>
        <div class="sf-stat-value" style="color: #5FC08A">{{ healthyCount }}</div>
        <div class="sf-stat-note">Healthy inventory level</div>
      </div>
    </div>

    <!-- Filter & Search Toolbar -->
    <div class="inventory-toolbar" role="search" aria-label="Inventory search and filter tools">
      <el-input
        v-model="searchText"
        placeholder="Search by item code or name..."
        aria-label="Search inventory by item code or name"
        clearable
        style="width: 280px"
      />
      <el-select
        v-model="categoryFilter"
        placeholder="All categories"
        aria-label="Filter items by category"
        clearable
        style="width: 180px"
      >
        <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <button
        type="button"
        class="sf-btn-secondary"
        :disabled="loading"
        aria-label="Refresh inventory items"
        @click="loadItems"
      >
        Refresh
      </button>

      <div class="toolbar-spacer"></div>

      <span class="item-count-label">
        {{ filteredItems.length }} OF {{ items.length }} ITEMS
      </span>
    </div>

    <!-- Accessible Data Table -->
    <div class="accessible-table-container" tabindex="0" aria-label="Inventory items table">
      <el-table v-loading="loading" :data="filteredItems" stripe border>
        <el-table-column prop="itemCode" label="Item Code" width="150">
          <template #default="{ row }">
            <span class="code-id">{{ row.itemCode }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="Item Name" min-width="220">
          <template #default="{ row }">
            <span class="item-name-cell">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Category" width="160">
          <template #default="{ row }">
            <span class="category-pill" :class="row.category.toLowerCase()">
              {{ categoryLabel(row.category) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="unit" label="Unit" width="100">
          <template #default="{ row }">
            <span class="mono-unit">{{ row.unit }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Current Stock" width="200">
          <template #default="{ row }">
            <span
              :class="['status-badge', row.totalQty <= row.minStockThreshold ? 'danger' : 'success']"
              :aria-label="`Stock level: ${row.totalQty} units ${row.totalQty <= row.minStockThreshold ? '(Low stock below threshold of ' + row.minStockThreshold + ')' : '(In stock)'}`"
            >
              <span v-if="row.totalQty <= row.minStockThreshold">⚠️ {{ row.totalQty }} (Low Stock)</span>
              <span v-else>✓ {{ row.totalQty }}</span>
            </span>
          </template>
        </el-table-column>

        <el-table-column v-if="canAdjustStock" label="Actions" width="120" fixed="right">
          <template #default="{ row }">
            <button
              type="button"
              class="sf-btn-secondary"
              style="height: 28px; padding: 0 10px; font-size: 12px"
              :aria-label="`Adjust stock for ${row.name} (${row.itemCode})`"
              @click="openAdjustmentDialog(row)"
            >
              Adjust
            </button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty
            v-if="searchText || categoryFilter"
            description="No items match your search or filter criteria. Try clearing the filter."
          />
          <el-empty v-else description="No inventory items currently registered in the system." />
        </template>
      </el-table>
    </div>

    <!-- Stock Adjustment Modal Dialog -->
    <el-dialog
      v-model="adjustDialogVisible"
      title="Adjust Stock"
      width="480px"
      aria-modal="true"
      custom-class="sf-dialog"
    >
      <div class="tech-kicker">POST /api/stock-adjustments</div>
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
        <button type="button" class="sf-btn-secondary" @click="adjustDialogVisible = false">
          Cancel
        </button>
        <button
          type="button"
          class="sf-btn-primary"
          :disabled="adjusting"
          @click="submitAdjustment"
        >
          Submit Adjustment
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
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.inventory-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.toolbar-spacer {
  flex: 1 1 auto;
}

.item-count-label {
  font-family: var(--siteflow-font-mono);
  font-size: 11.5px;
  color: var(--siteflow-text-placeholder);
  letter-spacing: 0.06em;
}

.item-name-cell {
  font-weight: 500;
  color: var(--siteflow-text-primary);
}

.category-pill {
  font-family: var(--siteflow-font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  padding: 3px 6px;
  border-radius: 2px;
  border: 1px solid var(--siteflow-border-subtle);
  background-color: var(--siteflow-bg-surface);
  color: #9dafc6;
}

.category-pill.tool {
  color: #8fb6dd;
  border-color: rgba(143, 182, 221, 0.3);
  background-color: rgba(143, 182, 221, 0.08);
}

.category-pill.consumable {
  color: #5fc08a;
  border-color: rgba(95, 192, 138, 0.3);
  background-color: rgba(95, 192, 138, 0.08);
}

.category-pill.lifting_gear {
  color: #e0b152;
  border-color: rgba(224, 177, 82, 0.3);
  background-color: rgba(224, 177, 82, 0.08);
}

.mono-unit {
  font-family: var(--siteflow-font-mono);
  font-size: 12px;
  color: var(--siteflow-text-muted);
}
</style>
