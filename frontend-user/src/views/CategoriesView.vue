<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { categoryApi, productApi } from '@/api'
import { coverUrl } from '@/utils/img'

const router = useRouter()
const categories = ref([])
const loading = ref(true)

const CAT_ICON = { 1: '智', 2: '芯', 3: '电', 4: '衣', 5: '妆', 6: '书' }
const catGlyph = c => CAT_ICON[c.id] || (c.name || '选').trim()[0] || '选'

const goCat = id => router.push({ path: '/', query: { categoryId: id } })

onMounted(async () => {
  try {
    const r = await categoryApi.list()
    const list = r.data || []
    // 每个分类补充「在售数量 + 3 件热销样例」，让分类页有可判断的信息
    const enriched = await Promise.all(list.map(async c => {
      try {
        const p = await productApi.list({ categoryId: c.id, sort: 'sales_desc', current: 1, size: 3 })
        return { ...c, total: p.data.total, samples: p.data.records || [] }
      } catch (e) {
        return { ...c, total: 0, samples: [] }
      }
    }))
    categories.value = enriched
  } catch (e) {
    categories.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">全部分类</h2>
        <div class="section-sub">共 {{ categories.length }} 个分类 · 点击任意分类查看该品类全部商品</div>
      </div>
    </div>

    <div v-if="loading" class="cat-rail cat-rail-lg">
      <div v-for="i in 6" :key="i" class="sk" />
    </div>

    <div v-else-if="!categories.length" class="empty">
      <div class="empty-icon">类</div>
      <div class="empty-title">暂无分类</div>
      <p class="empty-sub">稍后再来看看。</p>
    </div>

    <div v-else class="cat-cards">
      <section v-for="(c, i) in categories" :key="c.id" class="cat-card" @click="goCat(c.id)">
        <header class="cat-card-head">
          <div class="cat-glyph cat-glyph-lg">{{ catGlyph(c) }}</div>
          <div class="cat-card-title">
            <h3>{{ c.name }}</h3>
            <span>{{ c.total }} 件在售 · 编号 {{ String(i + 1).padStart(2, '0') }}</span>
          </div>
          <span class="cat-card-go">查看 →</span>
        </header>
        <div class="cat-samples">
          <div v-for="p in c.samples" :key="p.id" class="cat-sample">
            <div class="cat-sample-thumb">
              <img v-if="p.cover" :src="coverUrl(p.cover)" :alt="p.name" loading="lazy" />
              <div v-else class="img-ph">{{ (p.name || '?')[0] }}</div>
            </div>
            <div class="cat-sample-name">{{ p.name }}</div>
            <div class="price cat-sample-price"><span class="cur">¥</span>{{ Number(p.price).toLocaleString() }}</div>
          </div>
          <div v-if="!c.samples.length" class="cat-sample-empty">该分类暂无在售商品</div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.cat-cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.cat-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: 18px 20px 20px;
  cursor: pointer;
  transition: all .2s ease;
}
.cat-card:hover { border-color: #ffd4d0; box-shadow: var(--sh-md); transform: translateY(-2px); }
.cat-card-head { display: flex; align-items: center; gap: 14px; }
.cat-card-title { min-width: 0; }
.cat-card-title h3 { font-size: 16.5px; font-weight: 700; letter-spacing: .02em; }
.cat-card-title span { font-size: 11.5px; color: var(--ink-3); }
.cat-card-go { margin-left: auto; font-size: 12px; color: var(--brand); white-space: nowrap; }
.cat-samples { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 16px; }
.cat-sample { min-width: 0; }
.cat-sample-thumb {
  aspect-ratio: 1 / 1;
  border-radius: var(--r-sm);
  overflow: hidden;
  background: var(--surface-2);
}
.cat-sample-thumb img { width: 100%; height: 100%; object-fit: cover; transition: transform .4s ease; }
.cat-card:hover .cat-sample-thumb img { transform: scale(1.04); }
.cat-sample-name {
  margin-top: 6px; font-size: 11.5px; color: var(--ink-2); line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden;
}
.cat-sample-price { font-size: 13px; }
.cat-sample-empty { grid-column: 1 / -1; font-size: 12px; color: var(--ink-4); padding: 12px 0; }
.sk {
  height: 190px;
  border-radius: var(--r-lg);
  background: linear-gradient(100deg, #f3f3f3 30%, #fafafa 45%, #f3f3f3 60%);
  background-size: 200% 100%;
  animation: shimmer 1.3s infinite linear;
}
@keyframes shimmer { from { background-position: 150% 0; } to { background-position: -50% 0; } }
@media (max-width: 900px) {
  .cat-cards { grid-template-columns: 1fr; }
}
</style>
