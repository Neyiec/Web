<script setup>
import { onMounted, ref } from 'vue'
import { post, request } from '../api/request'
import { categories, conditions, price } from '../market'

const pending = ref([])
const reasons = ref({})
const error = ref('')
const busy = ref(false)

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    await action()
    pending.value = await request('/api/admin/reviews')
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

function review(item, approved) {
  return run(() => post('/api/admin/reviews/' + item.id, {
    version: item.version, approved, reason: reasons.value[item.id] || '',
  }))
}

onMounted(() => run(async () => {}))
</script>

<template>
  <main class="shell page">
    <h1>商品审核</h1>
    <p v-if="error" class="error notice" role="alert">{{ error }}</p>
    <p v-else-if="!pending.length && !busy" class="notice">暂无待审核商品。</p>
    <ul class="stack plain-list">
      <li v-for="item in pending" :key="item.id" class="panel stack">
        <h2>{{ item.title }}</h2>
        <p class="muted">{{ item.campus }} · {{ categories[item.category] }} · {{ conditions[item.conditionCode] }}</p>
        <p class="description">{{ item.description }}</p>
        <img v-if="item.hasImage" class="review-image" :src="'/api/admin/reviews/' + item.id + '/image'" :alt="item.title" />
        <p>{{ item.saleType === 'AUCTION' ? '起拍价 ' + price(item.startPrice) + ' · ' + item.durationMinutes + ' 分钟' : '一口价 ' + price(item.price) }}</p>
        <label>驳回原因 <input v-model="reasons[item.id]" maxlength="500" /></label>
        <div class="actions">
          <button type="button" class="primary" :disabled="busy" @click="review(item, true)">通过并上架</button>
          <button type="button" :disabled="busy || !reasons[item.id]?.trim()" @click="review(item, false)">驳回</button>
        </div>
      </li>
    </ul>
  </main>
</template>
