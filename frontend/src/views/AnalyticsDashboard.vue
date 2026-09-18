<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'
import http from '../api/http'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend)

const summary = ref(null)
const loadingSummary = ref(false)

const trends = ref([])
const loadingTrends = ref(false)

const toolUtilization = ref([])
const loadingUtilization = ref(false)

const reorderRows = ref([])
const loadingReorder = ref(false)
const draftingId = ref(null)

async function loadSummary() {
  loadingSummary.value = true
  try {
    summary.value = await http.get('/analytics/summary')
  } finally {
    loadingSummary.value = false
  }
}

function isoDate(date) {
  return date.toISOString().slice(0, 10)
}

async function loadTrends() {
  loadingTrends.value = true
  try {
    const endDate = new Date()
    const startDate = new Date()
    startDate.setMonth(startDate.getMonth() - 5)
    trends.value = await http.get('/analytics/trends', {
      params: { startDate: isoDate(startDate), endDate: isoDate(endDate) },
    })
  } finally {
    loadingTrends.value = false
  }
}

async function loadToolUtilization() {
  loadingUtilization.value = true
  try {
    toolUtilization.value = await http.get('/analytics/tool-utilization')
  } finally {
    loadingUtilization.value = false
  }
}

/** Combines the low-stock and per-item forecast endpoints into one actionable report row. */
async function loadReorderReport() {
  loadingReorder.value = true
  try {
    const lowStockItems = await http.get('/analytics/low-stock')
    reorderRows.value = await Promise.all(
      lowStockItems.map(async (item) => {
        const forecast = await http.get(`/analytics/forecast/${item.id}`)
        const shortfall = Math.max(item.minStockThreshold - item.totalQty, 0)
        return {
          itemId: item.id,
          itemName: item.name,
          currentQty: item.totalQty,
          minStockThreshold: item.minStockThreshold,
          forecastedDemand: forecast.forecastedDemand,
          recommendedQty: shortfall + forecast.forecastedDemand,
        }
      }),
    )
  } finally {
    loadingReorder.value = false
  }
}

async function draftMaterialRequest(row) {
  draftingId.value = row.itemId
  try {
    await http.post('/procurement/material-requests', {
      justification: `Auto-reorder recommendation for ${row.itemName}`,
      items: [{ itemId: row.itemId, qty: row.recommendedQty }],
    })
    ElMessage.success(`Material request drafted for ${row.itemName}.`)
  } catch {
    // interceptor already showed the error toast
  } finally {
    draftingId.value = null
  }
}

function utilizationPercent(tool) {
  return tool.totalOwned ? Math.round((tool.currentlyOut / tool.totalOwned) * 100) : 0
}

function utilizationStatus(tool) {
  const percent = utilizationPercent(tool)
  if (percent >= 90) return 'exception'
  if (percent >= 70) return 'warning'
  return ''
}

const chartData = computed(() => ({
  labels: trends.value.map((t) => t.period),
  datasets: [
    {
      label: 'Consumable Outflow',
      data: trends.value.map((t) => t.totalOutflow),
      borderColor: '#409EFF',
      backgroundColor: 'rgba(64, 158, 255, 0.15)',
      tension: 0.3,
      fill: true,
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: { y: { beginAtZero: true } },
}

onMounted(() => {
  loadSummary()
  loadTrends()
  loadToolUtilization()
  loadReorderReport()
})
</script>

<template>
  <div class="analytics-dashboard">
    <h1 class="page-title">Analytics & Demand Forecasting</h1>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="8">
        <el-card v-loading="loadingSummary" shadow="never">
          <el-statistic title="Total Items Borrowed" :value="summary?.totalActiveBorrows ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="8">
        <el-card v-loading="loadingSummary" shadow="never">
          <el-statistic title="Items Low on Stock" :value="summary?.totalItemsBelowMinStock ?? 0" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="8">
        <el-card v-loading="loadingSummary" shadow="never">
          <div class="text-stat-title">Most Active Item</div>
          <div class="text-stat-value">{{ summary?.mostBorrowedItemName ?? '—' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :md="14">
        <el-card v-loading="loadingTrends" shadow="never">
          <template #header>
            <h2 class="section-title">Consumption Trends (last 6 months)</h2>
          </template>
          <div class="chart-container" role="region" aria-label="Consumption Trends Line Chart">
            <Line
              v-if="trends.length"
              :data="chartData"
              :options="chartOptions"
              aria-label="Consumable outflow trends line chart over the last 6 months"
            />
            <el-empty v-else description="No consumption data in this period" />
          </div>

          <!-- Accessible data table alternative for screen readers -->
          <table v-if="trends.length" class="sr-only">
            <caption>Consumable Outflow Trends (Last 6 Months)</caption>
            <thead>
              <tr>
                <th scope="col">Period</th>
                <th scope="col">Consumable Outflow</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in trends" :key="t.period">
                <td>{{ t.period }}</td>
                <td>{{ t.totalOutflow }}</td>
              </tr>
            </tbody>
          </table>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="10">
        <el-card v-loading="loadingUtilization" shadow="never">
          <template #header>
            <h2 class="section-title">Tool Utilization</h2>
          </template>
          <div v-if="toolUtilization.length" class="tool-utilization">
            <div v-for="tool in toolUtilization" :key="tool.itemId" class="tool-row">
              <div class="tool-label">
                <span>{{ tool.itemName }}</span>
                <span>{{ tool.currentlyOut }} of {{ tool.totalOwned }} deployed ({{ utilizationPercent(tool) }}%)</span>
              </div>
              <el-progress
                :percentage="utilizationPercent(tool)"
                :status="utilizationStatus(tool)"
                :aria-label="`${tool.itemName} utilization: ${tool.currentlyOut} of ${tool.totalOwned} (${utilizationPercent(tool)}%)`"
              />
            </div>
          </div>
          <el-empty v-else description="No tools tracked" />
        </el-card>
      </el-col>
    </el-row>

    <el-card v-loading="loadingReorder" shadow="never" class="section-row">
      <template #header>
        <h2 class="section-title">Reorder &amp; Forecasting Report</h2>
      </template>
      <div class="accessible-table-container" tabindex="0" role="region" aria-label="Reorder and Forecasting Report Table">
        <el-table :data="reorderRows" stripe border>
          <el-table-column prop="itemName" label="Item Name" min-width="180" />
          <el-table-column prop="currentQty" label="Current Stock" width="130" />
          <el-table-column prop="minStockThreshold" label="Minimum Threshold" width="160" />
          <el-table-column prop="forecastedDemand" label="SMA Forecast" width="130" />
          <el-table-column prop="recommendedQty" label="Recommended Reorder Qty" width="200" />
          <el-table-column label="Action" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                :loading="draftingId === row.itemId"
                :disabled="row.recommendedQty <= 0"
                :aria-label="`Draft Material Request for ${row.itemName} with recommended quantity ${row.recommendedQty}`"
                @click="draftMaterialRequest(row)"
              >
                Draft MR
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="No items currently need reordering" />
          </template>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.analytics-dashboard {
  display: flex;
  flex-direction: column;
}

.section-row {
  margin-top: 16px;
}

.text-stat-title {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-bottom: 8px;
}

.text-stat-value {
  font-size: 24px;
  font-weight: 600;
}

.chart-container {
  height: 320px;
}

.tool-utilization {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tool-row .tool-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 14px;
}
</style>
