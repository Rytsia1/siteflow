<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

const mode = ref('checkout')
const scanValue = ref('')
const scanInputRef = ref()
const borrowRequestId = ref(null)
const toolCondition = ref(null)
const submitting = ref(false)

const conditionOptions = [
  { value: 'GOOD', label: 'Good' },
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
    scanValue.value = ''
  } catch {
    // interceptor already showed the error toast
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
    scanValue.value = ''
  } catch {
    // interceptor already showed the error toast
  } finally {
    submitting.value = false
    focusScanInput()
  }
}

onMounted(focusScanInput)
</script>

<template>
  <div class="asset-scanner-view">
    <h1 class="page-title">Asset Barcode & QR Scanner</h1>
    <el-card style="max-width: 560px">
      <template #header>
        <h2 class="section-title">Scan Operations</h2>
      </template>

      <el-radio-group v-model="mode" aria-label="Scanner Mode" style="margin-bottom: 20px" @change="handleModeChange">
        <el-radio-button label="checkout">Checkout Mode</el-radio-button>
        <el-radio-button label="return">Return Mode</el-radio-button>
      </el-radio-group>

      <el-form label-position="top">
        <el-form-item v-if="mode === 'checkout'" label="Borrow Request ID">
          <el-input-number
            v-model="borrowRequestId"
            :min="1"
            controls-position="right"
            aria-label="Borrow Request ID for checkout"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item v-else label="Tool Condition">
          <el-select
            v-model="toolCondition"
            placeholder="Select condition"
            aria-label="Tool Condition on return"
            style="width: 100%"
          >
            <el-option v-for="opt in conditionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="Scan Barcode / QR Code">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-input
              ref="scanInputRef"
              v-model="scanValue"
              placeholder="Scan or type a serial number"
              aria-label="Asset barcode or serial number"
              :disabled="submitting"
              @keyup.enter="handleScan"
            />
            <el-button
              type="primary"
              :loading="submitting"
              :disabled="submitting || !scanValue.trim()"
              aria-label="Process asset scan"
              @click="handleScan"
            >
              Process
            </el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>
