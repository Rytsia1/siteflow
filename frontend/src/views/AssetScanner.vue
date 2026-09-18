<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const route = useRoute()
const mode = ref('checkout')
const highlightedCard = ref(false)
const scanValue = ref('')
const scanInputRef = ref()
const borrowRequestId = ref(null)
const toolCondition = ref(null)
const submitting = ref(false)
const lastScanned = ref(null)

const conditionOptions = [
  { value: 'GOOD', label: 'Good Condition' },
  { value: 'NEEDS_REPAIR', label: 'Needs Repair' },
  { value: 'BROKEN', label: 'Broken' },
]

function focusScanInput() {
  nextTick(() => scanInputRef.value?.focus())
}

function handleModeChange() {
  scanValue.value = ''
  focusScanInput()
}

async function handleScan() {
  const code = scanValue.value.trim()
  if (!code || submitting.value) return

  if (mode.value === 'checkout') {
    await checkout(code)
  } else {
    await processReturn(code)
  }
}

async function checkout(code) {
  if (!borrowRequestId.value) {
    ElMessage.warning('Enter the Borrow Request ID before scanning.')
    scanValue.value = ''
    focusScanInput()
    return
  }

  submitting.value = true
  try {
    const instance = await http.post('/assets/checkout', {
      serialNumber: code,
      borrowRequestId: borrowRequestId.value,
    })
    ElMessage.success(`Checked out asset ${instance.serialNumber}.`)
    lastScanned.value = {
      serialNumber: instance.serialNumber,
      action: 'CHECKOUT',
      borrowRequestId: borrowRequestId.value,
      time: 'Just now',
    }
    scanValue.value = ''
  } catch {
    // handled by interceptor
  } finally {
    submitting.value = false
    focusScanInput()
  }
}

async function processReturn(code) {
  if (!toolCondition.value) {
    ElMessage.warning('Select a tool condition before scanning.')
    scanValue.value = ''
    focusScanInput()
    return
  }

  submitting.value = true
  try {
    const instance = await http.post('/assets/return', {
      serialNumber: code,
      toolCondition: toolCondition.value,
    })
    ElMessage.success(`Returned asset ${instance.serialNumber}.`)
    lastScanned.value = {
      serialNumber: instance.serialNumber,
      action: 'RETURN',
      condition: toolCondition.value,
      time: 'Just now',
    }
    scanValue.value = ''
  } catch {
    // handled by interceptor
  } finally {
    submitting.value = false
    focusScanInput()
  }
}

function checkDeepLink() {
  if (route.query.mode === 'return' || route.query.mode === 'checkout') {
    mode.value = route.query.mode
  }
  const code = route.query.code || route.query.highlight || route.query.serialNumber || route.query.tag
  if (code) {
    scanValue.value = String(code)
    highlightedCard.value = true
    setTimeout(() => {
      highlightedCard.value = false
    }, 4500)
  }
  if (route.query.borrowRequestId) {
    const brId = Number(route.query.borrowRequestId)
    if (!isNaN(brId) && brId > 0) {
      borrowRequestId.value = brId
    }
  }
  focusScanInput()
}

onMounted(() => {
  checkDeepLink()
})

watch(() => route.query, () => {
  checkDeepLink()
})
</script>

<template>
  <div class="asset-scanner-view">
    <div class="tech-kicker">GET /api/assets/{tag} · POST /api/assets/return</div>
    <div class="page-header-row">
      <div>
        <h1 class="page-title">Asset Scanner</h1>
        <p class="page-subtitle">Scan or simulate physical tool barcode / QR tag to track custody transfer.</p>
      </div>
    </div>

    <div class="scanner-layout">
      <!-- Camera Preview Box (Design Component) -->
      <div class="camera-preview-container">
        <div class="camera-box">
          <span class="camera-target-frame"></span>
          <span class="scan-laser-line"></span>
          <div class="camera-label">QR / BARCODE CAMERA</div>
        </div>
        <div class="camera-hint">
          Point the optical scanner or mobile camera at the printed label on the tool body.
        </div>
      </div>

      <!-- Scanner Controls Card -->
      <div class="scanner-card" :class="{ 'sf-card-highlight': highlightedCard }">
        <span class="sf-corner-mark top-left">+</span>
        <span class="sf-corner-mark top-right">+</span>
        <span class="sf-corner-mark bottom-left">+</span>
        <span class="sf-corner-mark bottom-right">+</span>

        <div class="card-header-bar">
          <span class="header-tag">SCANNER OPERATIONS</span>
        </div>

        <div class="card-body">
          <div class="mode-tabs">
            <button
              type="button"
              class="mode-tab-btn"
              :class="{ active: mode === 'checkout' }"
              @click="mode = 'checkout'; handleModeChange()"
            >
              Checkout Mode
            </button>
            <button
              type="button"
              class="mode-tab-btn"
              :class="{ active: mode === 'return' }"
              @click="mode = 'return'; handleModeChange()"
            >
              Return Mode
            </button>
          </div>

          <el-form label-position="top">
            <el-form-item v-if="mode === 'checkout'" label="Borrow Request ID">
              <el-input-number
                v-model="borrowRequestId"
                :min="1"
                controls-position="right"
                placeholder="e.g. 1"
                aria-label="Borrow Request ID for checkout"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item v-else label="Tool Condition on Return">
              <el-select
                v-model="toolCondition"
                placeholder="Select condition"
                aria-label="Tool Condition on return"
                style="width: 100%"
              >
                <el-option
                  v-for="opt in conditionOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="Asset Serial / Tag Number">
              <div style="display: flex; gap: 8px; width: 100%;">
                <el-input
                  ref="scanInputRef"
                  v-model="scanValue"
                  placeholder="Scan or enter serial (e.g. TL-00482)"
                  aria-label="Asset barcode or serial number"
                  :disabled="submitting"
                  @keyup.enter="handleScan"
                />
                <button
                  type="button"
                  class="sf-btn-primary"
                  :disabled="submitting || !scanValue.trim()"
                  aria-label="Process asset scan"
                  @click="handleScan"
                >
                  {{ submitting ? 'Processing...' : 'Process' }}
                </button>
              </div>
            </el-form-item>
          </el-form>

          <!-- Last Scan Feedback -->
          <div v-if="lastScanned" class="scan-result-card">
            <div class="result-header">
              <span class="status-badge success">✓ ASSET IDENTIFIED</span>
              <span class="result-time">{{ lastScanned.time }}</span>
            </div>
            <div class="result-body">
              <div class="result-serial">{{ lastScanned.serialNumber }}</div>
              <div class="result-detail">
                Operation: <b>{{ lastScanned.action }}</b>
                <span v-if="lastScanned.borrowRequestId"> · Request #{{ lastScanned.borrowRequestId }}</span>
                <span v-if="lastScanned.condition"> · Condition: {{ lastScanned.condition }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.scanner-layout {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 24px;
  max-width: 1000px;
}

.camera-preview-container {
  display: flex;
  flex-direction: column;
}

.camera-box {
  position: relative;
  aspect-ratio: 4 / 3;
  max-height: 280px;
  background-color: #0e1626;
  border: 1px solid var(--siteflow-border-color);
  border-radius: 4px;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.camera-target-frame {
  position: absolute;
  inset: 28px;
  border: 1px solid #26334c;
  border-radius: 3px;
  pointer-events: none;
}

.scan-laser-line {
  position: absolute;
  left: 28px;
  right: 28px;
  height: 2px;
  background-color: #4e88c4;
  box-shadow: 0 0 8px #4e88c4;
  animation: laser-scan 2.4s ease-in-out infinite alternate;
}

@keyframes laser-scan {
  0% { top: 32px; }
  100% { top: calc(100% - 34px); }
}

.camera-label {
  position: absolute;
  bottom: 12px;
  font-family: var(--siteflow-font-mono);
  font-size: 10.5px;
  letter-spacing: 0.12em;
  color: #5c6b83;
}

.camera-hint {
  font-size: 12px;
  color: var(--siteflow-text-muted);
  margin-top: 10px;
  line-height: 1.5;
}

.scanner-card {
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

.card-body {
  padding: 20px;
}

.mode-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
}

.mode-tab-btn {
  flex: 1 1 0;
  height: 32px;
  background-color: #0e1626;
  border: 1px solid var(--siteflow-border-subtle);
  color: #9dafc6;
  font-size: 13px;
  font-weight: 500;
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.12s ease;
}

.mode-tab-btn.active {
  background-color: #16223a;
  border-color: #4e88c4;
  color: #f0f5fb;
  font-weight: 600;
}

.scan-result-card {
  margin-top: 18px;
  border: 1px solid var(--siteflow-border-subtle);
  background-color: #0e1626;
  border-radius: 3px;
  padding: 14px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.result-time {
  font-family: var(--siteflow-font-mono);
  font-size: 11px;
  color: #6f8099;
}

.result-serial {
  font-family: var(--siteflow-font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--siteflow-accent);
}

.result-detail {
  font-size: 12.5px;
  color: #c6d3e6;
  margin-top: 4px;
}
</style>
