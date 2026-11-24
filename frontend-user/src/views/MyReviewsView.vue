<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Share, Delete, ChatLineSquare } from '@element-plus/icons-vue'
import { reviewApi } from '@/api'
import ShareDialog from '@/components/ShareDialog.vue'

const router = useRouter()

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const selected = ref([])

const shareVisible = ref(false)
const shareRow = ref(null)

const allChecked = computed(() => list.value.length > 0 && selected.value.length === list.value.length)
const indeterminate = computed(() => selected.value.length > 0 && selected.value.length < list.value.length)

const load = async () => {
  loading.value = true
  try {
    const r = await reviewApi.minePage({ current: page.value, size })
    list.value = r.data?.records || []
    total.value = r.data?.total || 0
    selected.value = []
  } finally {
    loading.value = false
  }
}

const toggleAll = v => {
  selected.value = v ? list.value.map(x => x.id) : []
}

const removeOne = async row => {
  try {
    await ElMessageBox.confirm('确认删除这条评价？删除后可以重新评价。', '删除评价', { type: 'warning' })
    await reviewApi.remove(row.id)
    ElMessage.success('评价已删除')
    load()
  } catch (e) { /* 取消或失败 */ }
}

const removeBatch = async () => {
  if (!selected.value.length) return ElMessage.warning('请先勾选要删除的评价')
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selected.value.length} 条评价？`, '批量删除', { type: 'warning' })
    await reviewApi.batchDelete(selected.value)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* 取消或失败 */ }
}

const openShare = row => {
  shareRow.value = row
  shareVisible.value = true
}

const goProduct = row => {
  if (row.productId) router.push(`/product/${row.productId}`)
}

onMounted(load)
</script>

<template>
  <div class="page reviews-page">
    <div class="page-head">
      <h1 class="page-title">我的评价</h1>
      <p class="page-desc">查看、删除或把评价分享给好友。</p>
    </div>

    <div class="card">
      <div class="card-head">
        <div class="card-title">
          <el-icon><ChatLineSquare /></el-icon> 共 {{ total }} 条评价
        </div>
        <div class="reviews-actions">
          <el-checkbox
            :model-value="allChecked"
            :indeterminate="indeterminate"
            @change="toggleAll"
          >全选</el-checkbox>
          <el-button size="small" type="danger" plain :disabled="!selected.length" @click="removeBatch">
            <el-icon><Delete /></el-icon> 批量删除{{ selected.length ? `(${selected.length})` : '' }}
          </el-button>
        </div>
      </div>

      <div v-loading="loading">
        <div v-if="!list.length" class="empty" style="padding:48px 0">
          <div class="empty-title">还没有评价</div>
          <p class="empty-sub">确认收货后，可以在订单里评价商品。</p>
        </div>

        <div v-else class="review-list">
          <div v-for="r in list" :key="r.id" class="review-item">
            <el-checkbox
              :model-value="selected.includes(r.id)"
              @change="v => { if (v) selected.push(r.id); else selected = selected.filter(x => x !== r.id) }"
            />
            <img
              v-if="r.productCover"
              :src="r.productCover"
              class="review-cover"
              alt=""
              @click="goProduct(r)"
            />
            <div class="review-main">
              <div class="review-top">
                <span class="review-product" @click="goProduct(r)">{{ r.productName || '商品' }}</span>
                <el-rate :model-value="r.rating" disabled size="small" />
                <span v-if="r.anonymous" class="pill mute">匿名</span>
                <span v-if="r.hidden" class="pill danger">已被屏蔽</span>
              </div>
              <div class="review-content">{{ r.content }}</div>
              <div class="review-meta">
                {{ r.createdAt?.replace('T', ' ').slice(0, 16) }}
                <span v-if="r.appends && r.appends.length"> · {{ r.appends.length }} 条追评</span>
              </div>
            </div>
            <div class="review-ops">
              <el-button size="small" text @click="openShare(r)">
                <el-icon><Share /></el-icon> 分享
              </el-button>
              <el-button size="small" text type="danger" @click="removeOne(r)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="total > size" class="reviews-pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="p => { page = p; load() }"
        />
      </div>
    </div>

    <ShareDialog
      v-model="shareVisible"
      type="REVIEW"
      :target-id="shareRow?.id"
      :title="shareRow ? `${shareRow.productName || '商品'} · 我的评价` : ''"
    />
  </div>
</template>

<style scoped>
.reviews-page { max-width: var(--page-max); margin: 0 auto; }
.reviews-actions { display: flex; align-items: center; gap: 12px; }
.review-list { display: flex; flex-direction: column; }
.review-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px dashed var(--border);
}
.review-item:last-child { border-bottom: 0; }
.review-cover {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  cursor: pointer;
  flex: none;
}
.review-main { flex: 1; min-width: 0; }
.review-top { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.review-product {
  font-weight: 600;
  color: var(--text-1);
  cursor: pointer;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-content {
  margin-top: 6px;
  font-size: 14px;
  color: var(--text-2);
  line-height: 1.6;
  word-break: break-word;
}
.review-meta { margin-top: 6px; font-size: 12px; color: var(--text-3); }
.review-ops { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; flex: none; }
.reviews-pager { display: flex; justify-content: flex-end; margin-top: 14px; }

@media (max-width: 640px) {
  .review-item { flex-wrap: wrap; }
  .review-ops { flex-direction: row; }
}
</style>
