<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { favoriteApi } from '@/api'
import { useProductActions } from '@/composables/useProductActions'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const favIds = ref([])
const { addToCart } = useProductActions(favIds)

const load = async () => {
  loading.value = true
  try {
    const r = await favoriteApi.list()
    list.value = r.data || []
    favIds.value = list.value.map(p => p.id)
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

// 在收藏页取消收藏后即时移出列表
const onToggleFav = async product => {
  await favoriteApi.remove(product.id)
  list.value = list.value.filter(p => p.id !== product.id)
  favIds.value = favIds.value.filter(id => id !== product.id)
}

onMounted(load)
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">我的收藏</h2>
        <div class="section-sub">共 {{ list.length }} 件收藏商品 · 已收藏商品会同步到账号</div>
      </div>
      <el-button text @click="router.push('/')">去逛逛 →</el-button>
    </div>

    <div v-if="loading" class="product-grid">
      <div v-for="i in 4" :key="i" class="sk" />
    </div>

    <div v-else-if="!list.length" class="empty">
      <div class="empty-icon">藏</div>
      <div class="empty-title">还没有收藏商品</div>
      <p class="empty-sub">在商品卡片右上角点击星标即可收藏，收藏后可随时回来查看。</p>
      <el-button class="mt-16" type="primary" @click="router.push('/')">去挑几件好物</el-button>
    </div>

    <div v-else class="product-grid">
      <ProductCard
        v-for="p in list"
        :key="p.id"
        :product="p"
        :favIds="favIds"
        @add="addToCart"
        @toggle-fav="onToggleFav"
      />
    </div>
  </div>
</template>

<style scoped>
.sk {
  height: 300px;
  border-radius: var(--r-md);
  background: linear-gradient(100deg, #f3f3f3 30%, #fafafa 45%, #f3f3f3 60%);
  background-size: 200% 100%;
  animation: shimmer 1.3s infinite linear;
}
@keyframes shimmer { from { background-position: 150% 0; } to { background-position: -50% 0; } }
</style>
