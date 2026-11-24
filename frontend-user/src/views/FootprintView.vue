<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { browseApi } from '@/api'
import { useProductActions } from '@/composables/useProductActions'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const favIds = ref([])
const { loadFavIds, addToCart, toggleFav } = useProductActions(favIds)

const load = async () => {
  loading.value = true
  try {
    const r = await browseApi.list()
    list.value = r.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

const clearAll = async () => {
  await browseApi.clear()
  list.value = []
  ElMessage.success('浏览足迹已清空')
}

const removeOne = async id => {
  await browseApi.remove(id)
  list.value = list.value.filter(p => p.id !== id)
}

onMounted(() => {
  loadFavIds()
  load()
})
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">浏览足迹</h2>
        <div class="section-sub">共 {{ list.length }} 件商品 · 浏览记录跟随账号保存，可跨设备同步</div>
      </div>
      <el-button v-if="list.length" text @click="clearAll">清空足迹</el-button>
    </div>

    <div v-loading="loading" class="fp-body">
      <div v-if="!loading && !list.length" class="empty">
        <div class="empty-icon">迹</div>
        <div class="empty-title">还没有浏览记录</div>
        <p class="empty-sub">看过的商品会自动记录在这里，方便你回来对比。</p>
        <el-button class="mt-16" type="primary" @click="router.push('/')">去首页逛逛</el-button>
      </div>

      <div v-else class="product-grid">
        <div v-for="p in list" :key="p.id" class="fp-cell">
          <ProductCard
            :product="p"
            :favIds="favIds"
            @add="addToCart"
            @toggle-fav="toggleFav"
          />
          <button class="fp-remove" title="移出足迹" @click="removeOne(p.id)">移除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fp-body { min-height: 160px; }
.fp-cell { position: relative; }
.fp-remove {
  position: absolute;
  left: 12px;
  bottom: 12px;
  padding: 3px 10px;
  border-radius: var(--r-full);
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, .94);
  color: var(--ink-3);
  font-size: 11px;
  cursor: pointer;
  opacity: 0;
  transition: all .18s ease;
}
.fp-cell:hover .fp-remove { opacity: 1; }
.fp-remove:hover { color: var(--brand); border-color: var(--brand); }
</style>
