<script setup>
import { onMounted, ref } from 'vue'
import { post, request } from '../api/request'
import { categories, conditions, price, statuses } from '../market'

const fields = () => ({
  title: '', description: '', category: 'BOOKS', campus: '', conditionCode: 'GOOD',
  saleType: 'FIXED_PRICE', price: '', startPrice: '', bidIncrement: '', durationMinutes: 10,
})
const items = ref([])
const form = ref(fields())
const editing = ref(null)
const image = ref(null)
const imageInput = ref(null)
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const editable = ['DRAFT', 'REJECTED', 'OFF_SHELF', 'UNSOLD', 'ON_SALE']
const withdrawable = ['PENDING_REVIEW', 'ON_SALE', 'AUCTIONING']

async function load() {
  items.value = await request('/api/seller/items')
}

function select(item) {
  editing.value = item?.id ?? null
  form.value = fields()
  if (item) {
    for (const key of Object.keys(form.value)) {
      form.value[key] = item[key] ?? form.value[key]
    }
  }
  image.value = null
  if (imageInput.value) imageInput.value.value = ''
  error.value = ''
  feedback.value = ''
}

async function run(action) {
  error.value = ''
  feedback.value = ''
  busy.value = true
  try {
    await action()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

function save() {
  return run(async () => {
    const input = { ...form.value }
    const auction = input.saleType === 'AUCTION'
    input.price = auction ? null : input.price
    if (!auction) {
      input.startPrice = null
      input.bidIncrement = null
      input.durationMinutes = null
    }
    if (editing.value === null) {
      const result = await post('/api/seller/items', input)
      editing.value = result.id
    } else {
      await request('/api/seller/items/' + editing.value, { method: 'PUT', body: input })
    }
    await load()
    if (image.value) {
      const upload = new FormData()
      upload.append('file', image.value)
      await post('/api/seller/items/' + editing.value + '/image', upload)
      await load()
    }
    select()
    feedback.value = '已保存草稿，可从下方提交审核。'
  })
}

function change(item, action) {
  return run(async () => {
    const path = '/api/seller/items/' + item.id
    if (action === 'delete') await request(path, { method: 'DELETE' })
    else await post(path + '/' + action)
    if (editing.value === item.id) select()
    await load()
  })
}

onMounted(() => run(load))
</script>

<template>
  <main class="shell page">
    <h1>我的发布</h1>
    <form class="panel stack" @submit.prevent="save">
      <div class="section-heading">
        <h2>{{ editing ? '修改商品' : '新建草稿' }}</h2>
        <button v-if="editing" type="button" @click="select()">新建草稿</button>
      </div>
      <div class="form-grid">
        <label>商品标题 <input v-model.trim="form.title" maxlength="100" required /></label>
        <label>校区 <input v-model.trim="form.campus" maxlength="64" required /></label>
        <label>分类
          <select v-model="form.category">
            <option v-for="(label, code) in categories" :key="code" :value="code">{{ label }}</option>
          </select>
        </label>
        <label>成色
          <select v-model="form.conditionCode">
            <option v-for="(label, code) in conditions" :key="code" :value="code">{{ label }}</option>
          </select>
        </label>
        <label>出售方式
          <select v-model="form.saleType">
            <option value="FIXED_PRICE">一口价</option>
            <option value="AUCTION">限时拍卖</option>
          </select>
        </label>
        <label v-if="form.saleType === 'FIXED_PRICE'">标价（元）
          <input v-model="form.price" type="number" min="0.01" step="0.01" required />
        </label>
        <template v-else>
          <label>起拍价（元） <input v-model="form.startPrice" type="number" min="0.01" step="0.01" required /></label>
          <label>最小加价（元） <input v-model="form.bidIncrement" type="number" min="0.01" step="0.01" required /></label>
          <label>拍卖时长（分钟） <input v-model="form.durationMinutes" type="number" min="1" max="10080" required /></label>
        </template>
      </div>
      <label>商品描述 <textarea v-model.trim="form.description" maxlength="4000" rows="3" required></textarea></label>
      <label>商品图片（JPG 或 PNG，最多 5 MB）
        <input ref="imageInput" type="file" accept="image/jpeg,image/png" @change="image = $event.target.files[0] ?? null" />
      </label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="feedback" role="status">{{ feedback }}</p>
      <div><button class="primary" :disabled="busy">保存草稿</button></div>
    </form>

    <h2>商品列表</h2>
    <p v-if="!items.length && !busy" class="notice">暂无已保存商品。</p>
    <ul class="stack plain-list">
      <li v-for="item in items" :key="item.id" class="panel">
        <div class="section-heading">
          <h3>{{ item.title }}</h3>
          <span class="tag">{{ statuses[item.status] }}</span>
        </div>
        <p class="muted">{{ item.campus }} · {{ item.saleType === 'AUCTION' ? '限时拍卖' : price(item.price) }}</p>
        <p v-if="item.reviewReason" class="error">驳回原因：{{ item.reviewReason }}</p>
        <div class="actions">
          <RouterLink v-if="['ON_SALE', 'AUCTIONING', 'RESERVED', 'SOLD', 'UNSOLD'].includes(item.status)" :to="'/items/' + item.id">查看详情</RouterLink>
          <button v-if="editable.includes(item.status)" type="button" :disabled="busy" @click="select(item)">编辑</button>
          <button v-if="item.status === 'DRAFT'" type="button" class="primary" :disabled="busy" @click="change(item, 'submit')">提交审核</button>
          <button v-if="withdrawable.includes(item.status) && item.highestPrice == null" type="button" :disabled="busy" @click="change(item, 'withdraw')">撤回或下架</button>
          <button v-if="item.status === 'DRAFT'" type="button" :disabled="busy" @click="change(item, 'delete')">删除草稿</button>
        </div>
      </li>
    </ul>
  </main>
</template>
