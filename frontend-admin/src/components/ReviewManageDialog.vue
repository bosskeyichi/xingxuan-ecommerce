<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminReviewApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  product: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const list = ref([])
const loading = ref(false)
const page = reactive({ current: 1, size: 5, total: 0 })

const stars = n => '★'.repeat(n || 0) + '☆'.repeat(5 - (n || 0))
const fmt = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''

const load = async () => {
  if (!props.product?.id) return
  loading.value = true
  try {
    const r = await adminReviewApi.page({
      productId: props.product.id,
      current: page.current,
      size: page.size
    })
    list.value = r.data.records || []
    page.total = r.data.total || 0
  } catch (e) {
    list.value = []
    page.total = 0
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, open => {
  if (open) {
    page.current = 1
    load()
  }
})

const toggleHide = async row => {
  if (row.hidden) {
    await adminReviewApi.unhide(row.id)
    ElMessage.success('已取消屏蔽，评价将重新展示')
  } else {
    await ElMessageBox.confirm(
      '屏蔽后该评价不会显示在商品页，也不会计入评分；可随时取消屏蔽恢复。',
      '屏蔽评价',
      { type: 'warning', confirmButtonText: '确认屏蔽', cancelButtonText: '取消' }
    )
    await adminReviewApi.hide(row.id)
    ElMessage.success('评价已屏蔽')
  }
  await load()
  emit('changed')
}

const remove = async row => {
  await ElMessageBox.confirm(
    `确认删除「${row.username || '该用户'}」的这条评价？删除后商品页将不再显示，且不可恢复。`,
    '删除评价',
    { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
  )
  await adminReviewApi.remove(row.id)
  ElMessage.success('评价已删除')
  if (list.value.length === 1 && page.current > 1) page.current -= 1
  await load()
  emit('changed')
}
</script>

<template>
  <el-dialog v-model="visible" width="760px" class="review-manage-dialog">
    <template #header>
      <div class="rm-head">
        <h3>评价管理</h3>
        <span class="rm-product">{{ product?.name || '商品' }} · 共 {{ page.total }} 条</span>
      </div>
    </template>

    <div v-loading="loading" class="rm-body">
      <div v-if="!loading && !list.length" class="rm-empty">
        <div class="rm-empty-icon">评</div>
        <div>该商品暂无评价</div>
        <p>用户确认收货后可在「我的订单」中发表评价。</p>
      </div>

      <div v-for="r in list" :key="r.id" class="rm-item" :class="{ 'is-hidden': r.hidden }">
        <div class="rm-item-head">
          <span class="rm-avatar">{{ (r.username || 'U')[0].toUpperCase() }}</span>
          <div class="rm-user">
            <div class="rm-name">
              {{ r.username || '匿名用户' }}
              <span v-if="r.hidden" class="rm-badge">已屏蔽</span>
            </div>
            <div class="rm-meta">
              <span class="rm-stars">{{ stars(r.rating) }}</span>
              <span>{{ fmt(r.createdAt) }}</span>
              <span v-if="r.updatedAt && fmt(r.updatedAt) !== fmt(r.createdAt)">修改于 {{ fmt(r.updatedAt) }}</span>
            </div>
          </div>
          <div class="rm-actions">
            <el-button size="small" text type="primary" @click="toggleHide(r)">
              {{ r.hidden ? '取消屏蔽' : '屏蔽' }}
            </el-button>
            <el-button size="small" text type="danger" @click="remove(r)">删除</el-button>
          </div>
        </div>

        <div class="rm-content">{{ r.content || '（未填写评价内容）' }}</div>

        <div v-for="a in r.appends || []" :key="a.id" class="rm-append">
          <span class="rm-append-tag">追评</span>
          <span class="rm-append-text">{{ a.content }}</span>
          <span class="rm-time">{{ fmt(a.createdAt) }}</span>
        </div>
      </div>
    </div>

    <div v-if="page.total > page.size" class="rm-pager">
      <el-pagination
        v-model:current-page="page.current"
        :page-size="page.size"
        :total="page.total"
        layout="prev, pager, next"
        small
        background
        @current-change="load"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rm-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.rm-head h3 { margin: 0; font-size: 16px; font-weight: 700; }
.rm-product { font-size: 12.5px; color: var(--text-3); }
.rm-body { max-height: 56vh; overflow-y: auto; padding-right: 4px; }
.rm-empty { text-align: center; padding: 38px 0; color: var(--text-3); font-size: 13px; }
.rm-empty-icon {
  width: 42px; height: 42px; margin: 0 auto 10px; border-radius: 50%;
  background: var(--surface-3); display: flex; align-items: center; justify-content: center;
  font-family: var(--font-num); font-weight: 700; color: var(--text-2);
}
.rm-empty p { margin-top: 4px; font-size: 12px; }
.rm-item {
  padding: 14px 16px; margin-bottom: 10px;
  border: 1px solid var(--border); border-radius: 10px;
  background: var(--surface);
}
.rm-item.is-hidden { background: var(--surface-2); opacity: .85; }
.rm-item-head { display: flex; align-items: flex-start; gap: 10px; }
.rm-avatar {
  width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0;
  background: var(--brand-50); color: var(--brand-600);
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px;
}
.rm-user { flex: 1; min-width: 0; }
.rm-name { font-weight: 600; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.rm-badge {
  font-size: 11px; font-weight: 400; color: var(--danger);
  background: var(--danger-bg); border-radius: 999px; padding: 1px 8px;
}
.rm-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 3px; font-size: 11.5px; color: var(--text-3); }
.rm-stars { color: #ff8a00; letter-spacing: .08em; }
.rm-actions { display: flex; gap: 2px; flex-shrink: 0; }
.rm-content {
  margin-top: 10px; font-size: 13px; line-height: 1.8; color: var(--text-2);
  white-space: pre-wrap; word-break: break-word;
}
.rm-append {
  display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap;
  margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--border-strong);
  font-size: 12.5px; line-height: 1.75; color: var(--text-2);
}
.rm-append-tag {
  padding: 0 6px; border-radius: 4px; background: var(--brand-50);
  color: var(--brand-600); font-size: 11px; flex-shrink: 0;
}
.rm-append-text { flex: 1; min-width: 0; white-space: pre-wrap; word-break: break-word; }
.rm-time { font-size: 11.5px; color: var(--text-3); }
.rm-pager { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
