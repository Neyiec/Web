<script setup>
import { onMounted, ref } from 'vue'
import { getCatalog } from '../api/catalog'
import { categories, conditions, price } from '../market'

const items = ref([])
const keyword = ref('')
const category = ref('')
const loading = ref(true)
const failed = ref('')

async function load() {
  loading.value = true
  failed.value = ''
  try {
    items.value = await getCatalog(keyword.value, category.value)
  } catch (failure) {
    failed.value = failure.message
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
      <form class="market-filters" @submit.prevent="load">
        <label>搜索商品 <input v-model.trim="keyword" maxlength="100" placeholder="商品标题或校区" /></label>
        <label>商品分类
          <select v-model="category">
            <option value="">全部分类</option>
            <option v-for="(label, code) in categories" :key="code" :value="code">{{ label }}</option>
          </select>
        </label>
        <button class="primary" :disabled="loading">查找</button>
      </form>
    </section>

    <section aria-labelledby="catalog-heading">
      <div class="catalog-heading">
        <h2 id="catalog-heading">商品列表</h2>
        <button type="button" :disabled="loading" @click="load">刷新商品</button>
      </div>

      <p v-if="loading" class="notice" role="status">正在加载商品…</p>
      <p v-else-if="failed" class="notice" role="alert">{{ failed }}</p>
      <p v-else-if="!items.length" class="notice" role="status">暂无符合条件的商品。</p>

      <ul v-else class="catalog">
        <li v-for="item in items" :key="item.id" class="item">
          <RouterLink :to="'/items/' + item.id" class="catalog-link">
            <img v-if="item.hasImage" class="card-image" :src="'/api/items/' + item.id + '/image'" :alt="item.title" loading="lazy" />
            <p class="category">{{ categories[item.category] ?? item.category }}</p>
            <h3>{{ item.title }}</h3>
            <p class="metadata">{{ item.campus }} · {{ conditions[item.conditionCode] }}</p>
            <div class="price">
              <strong>{{ price(item.displayPrice) }}</strong>
              <span v-if="item.saleType === 'AUCTION'">{{ item.hasBids ? '当前价' : '起拍价' }}</span>
              <span v-else>一口价</span>
            </div>
          </RouterLink>
        </li>
      </ul>
    </section>
  </main>
</template>
