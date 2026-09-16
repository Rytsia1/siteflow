<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api/http'

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
</script>

<template>
  <el-card style="max-width: 640px">
    <template #header>
      <h3>New Borrow Request</h3>
    </template>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="Location" prop="locationId">
        <el-select v-model="form.locationId" placeholder="Select a location" style="width: 100%">
          <el-option
            v-for="loc in locations"
            :key="loc.id"
            :label="loc.locationName"
            :value="loc.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Items">
        <div v-for="(line, index) in form.lines" :key="index" class="item-row">
          <el-form-item
            :prop="`lines.${index}.itemId`"
            :rules="lineItemIdRule()"
            class="item-select"
          >
            <el-select v-model="line.itemId" placeholder="Search item" filterable style="width: 100%">
              <el-option
                v-for="item in items"
                :key="item.id"
                :label="`${item.itemCode} — ${item.name}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-input-number v-model="line.qty" :min="1" />
          <el-button
            type="danger"
            plain
            :disabled="form.lines.length === 1"
            @click="removeLine(index)"
          >
            Remove
          </el-button>
        </div>
        <el-button @click="addLine">+ Add Item</el-button>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          Submit Borrow Request
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>
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
