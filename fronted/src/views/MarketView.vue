<script setup>
import { onMounted, ref } from 'vue'
import { getCatalog } from '../api/catalog'

const items = ref([])
const loading = ref(true)
const failed = ref(false)

const categoryLabels = {
  BOOKS: '教材书籍',
  DORM: '宿舍生活',
  ELECTRONICS: '数码配件',
  SPORTS: '运动用品',
  OTHER: '其他闲置',
}
const conditionLabels = { NEW: '全新', LIKE_NEW: '近新', GOOD: '良好', FAIR: '有使用痕迹' }
const priceFormat = new Intl.NumberFormat('zh-CN', {
  style: 'currency',
  currency: 'CNY',
  maximumFractionDigits: 2,
  minimumFractionDigits: 0,
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    items.value = await getCatalog()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <main class="shell market">
    <section class="intro">
      <h1>校园闲置集市</h1>
      <p>浏览同学发布的一口价商品和限时拍卖。</p>
    </section>

    <section aria-labelledby="catalog-heading">
      <div class="catalog-heading">
        <h2 id="catalog-heading">最新上架</h2>
        <button type="button" :disabled="loading" @click="load">刷新商品</button>
      </div>

      <p v-if="loading" class="notice" role="status">正在加载商品…</p>
      <p v-else-if="failed" class="notice" role="alert">暂时无法读取商品，请稍后重试。</p>
      <p v-else-if="!items.length" class="notice" role="status">暂无上架商品。</p>

      <ul v-else class="catalog">
        <li v-for="item in items" :key="item.id" class="item">
          <p class="category">{{ categoryLabels[item.category] ?? item.category }}</p>
          <h3>{{ item.title }}</h3>
          <p class="metadata">{{ item.campus }} · {{ conditionLabels[item.conditionCode] }}</p>
          <div class="price">
            <strong>{{ priceFormat.format(item.displayPrice) }}</strong>
            <span v-if="item.saleType === 'AUCTION'">{{ item.hasBids ? '当前价' : '起拍价' }}</span>
            <span v-else>一口价</span>
          </div>
        </li>
      </ul>
    </section>
  </main>
</template>
