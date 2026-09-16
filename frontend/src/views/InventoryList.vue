<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api/http'

const items = ref([])
const loading = ref(false)
const searchText = ref('')
const categoryFilter = ref('')

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

onMounted(loadItems)
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
      <template #empty>
        <el-empty description="No items found" />
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
