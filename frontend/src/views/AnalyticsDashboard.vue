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
    // handled by interceptor
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
      borderColor: '#4E88C4',
      backgroundColor: 'rgba(78, 136, 196, 0.2)',
      borderWidth: 2,
      pointBackgroundColor: '#8FB6DD',
      pointBorderColor: '#0B1120',
      pointRadius: 4,
      tension: 0.3,
      fill: true,
    },
  ],
}))

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: {
      backgroundColor: '#111827',
      titleColor: '#F0F5FB',
      bodyColor: '#C6D3E6',
      borderColor: '#243047',
      borderWidth: 1,
    },
  },
  scales: {
    y: {
      beginAtZero: true,
      grid: { color: '#1C2739' },
      ticks: { color: '#6F8099', font: { family: 'IBM Plex Mono', size: 11 } },
    },
    x: {
      grid: { color: '#1C2739' },
      ticks: { color: '#6F8099', font: { family: 'IBM Plex Mono', size: 11 } },
    },
  },
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
    <div class="tech-kicker">GET /api/analytics/* · ADMIN OBSERVED METRICS</div>
    <div class="page-header-row">
      <div>
        <h1 class="page-title">Analytics</h1>
        <p class="page-subtitle">Consumption trends, tool deployment utilisation and automated reorder demand pressure.</p>
      </div>
    </div>

    <!-- Stat Tiles (Industrial Design) -->
    <div class="sf-stat-grid" role="region" aria-label="Analytics KPI Summary">
      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">ACTIVE BORROWS</div>
        <div class="sf-stat-value" style="color: #E6EDF7">{{ summary?.totalActiveBorrows ?? 0 }}</div>
        <div class="sf-stat-note">Tools currently on loan</div>
      </div>

      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">LOW STOCK ITEMS</div>
        <div class="sf-stat-value" :style="{ color: (summary?.totalItemsBelowMinStock ?? 0) > 0 ? '#E0B152' : '#5FC08A' }">
          {{ summary?.totalItemsBelowMinStock ?? 0 }}
        </div>
        <div class="sf-stat-note">Stock below minimum threshold</div>
      </div>

      <div class="sf-stat-tile">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>
        <div class="sf-stat-label">MOST BORROWED ITEM</div>
        <div class="sf-stat-value" style="font-size: 20px; color: #8FB6DD; margin-top: 5px;">
          {{ summary?.mostBorrowedItemName ?? '—' }}
        </div>
        <div class="sf-stat-note">Highest field rotation</div>
      </div>
    </div>

    <!-- Charts & Tool Utilisation Row -->
    <el-row :gutter="20" class="section-row">
      <el-col :xs="24" :md="14">
        <el-card v-loading="loadingTrends" shadow="never" class="sf-custom-card">
          <template #header>
            <div class="card-header-flex">
              <span class="section-title">CONSUMPTION TRENDS</span>
              <span class="header-unit-note">units issued / month</span>
            </div>
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
        <el-card v-loading="loadingUtilization" shadow="never" class="sf-custom-card">
          <template #header>
            <div class="card-header-flex">
              <span class="section-title">TOOL UTILISATION</span>
              <span class="header-unit-note">deployed share</span>
            </div>
          </template>
          <div v-if="toolUtilization.length" class="tool-utilization">
            <div v-for="tool in toolUtilization" :key="tool.itemId" class="tool-row">
              <div class="tool-label">
                <span class="tool-name">{{ tool.itemName }}</span>
                <span class="tool-count-pill">{{ tool.currentlyOut }} of {{ tool.totalOwned }} deployed ({{ utilizationPercent(tool) }}%)</span>
              </div>
              <el-progress
                :percentage="utilizationPercent(tool)"
                :status="utilizationStatus(tool)"
                :aria-label="`${tool.itemName} utilization: ${tool.currentlyOut} of ${tool.totalOwned} (${utilizationPercent(tool)}%)`"
                :stroke-width="6"
              />
            </div>
          </div>
          <el-empty v-else description="No tools tracked in inventory" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Automated Reorder Demand Recommendations -->
    <el-card v-loading="loadingReorder" shadow="never" class="section-row sf-custom-card">
      <template #header>
        <div class="card-header-flex">
          <span class="section-title">REORDER RECOMMENDATIONS &amp; DEMAND FORECAST</span>
          <span class="header-unit-note">SMA moving average</span>
        </div>
      </template>
      <div class="accessible-table-container" tabindex="0" role="region" aria-label="Reorder and Forecasting Report Table">
        <el-table :data="reorderRows" stripe border>
          <el-table-column prop="itemName" label="Item Name" min-width="200">
            <template #default="{ row }">
              <span class="item-name-cell">{{ row.itemName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="currentQty" label="Current Stock" width="140">
            <template #default="{ row }">
              <span class="mono-num">{{ row.currentQty }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="minStockThreshold" label="Minimum Threshold" width="160">
            <template #default="{ row }">
              <span class="mono-num">{{ row.minStockThreshold }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="forecastedDemand" label="SMA Forecast" width="140">
            <template #default="{ row }">
              <span class="mono-num">{{ row.forecastedDemand }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="recommendedQty" label="Recommended Reorder Qty" width="220">
            <template #default="{ row }">
              <span class="recommended-pill" :class="{ highlight: row.recommendedQty > 0 }">
                +{{ row.recommendedQty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="Action" width="150" fixed="right">
            <template #default="{ row }">
              <button
                type="button"
                class="sf-btn-primary"
                style="height: 28px; padding: 0 10px; font-size: 12px"
                :loading="draftingId === row.itemId"
                :disabled="row.recommendedQty <= 0"
                :aria-label="`Draft Material Request for ${row.itemName} with recommended quantity ${row.recommendedQty}`"
                @click="draftMaterialRequest(row)"
              >
                Draft MR
              </button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="No items currently require stock reordering" />
          </template>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.section-row {
  margin-top: 20px;
}

.sf-custom-card {
  border: 1px solid var(--siteflow-border-color);
  background-color: var(--siteflow-bg-card);
  border-radius: 3px;
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-unit-note {
  font-family: var(--siteflow-font-mono);
  font-size: 11px;
  color: var(--siteflow-text-placeholder);
}

.chart-container {
  height: 280px;
}

.tool-utilization {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0;
}

.tool-row .tool-label {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 6px;
  font-size: 13px;
}

.tool-name {
  color: var(--siteflow-text-primary);
  font-weight: 500;
}

.tool-count-pill {
  font-family: var(--siteflow-font-mono);
  font-size: 11.5px;
  color: var(--siteflow-text-muted);
}

.item-name-cell {
  font-weight: 500;
  color: var(--siteflow-text-primary);
}

.mono-num {
  font-family: var(--siteflow-font-mono);
  font-size: 13px;
  color: var(--siteflow-text-primary);
}

.recommended-pill {
  font-family: var(--siteflow-font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--siteflow-text-muted);
}

.recommended-pill.highlight {
  color: #e0b152;
}
</style>
