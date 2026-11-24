<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { reportApi } from '@/api'
import { Warning } from '@element-plus/icons-vue'

const router = useRouter()

const list = ref([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = 6

const load = async () => {
  loading.value = true
  try {
    const r = await reportApi.mine({ current: page.value, size })
    list.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const statusText = s => ({ PENDING: '待处理', ACCEPTED: '举报成立', REJECTED: '已驳回' }[s] || s)
const statusClass = s => ({ PENDING: 'pending', ACCEPTED: 'completed', REJECTED: 'cancelled' }[s] || 'mute')
const typeText = t => (t === 'SHOP' ? '店铺' : '商品')

const openTarget = r => {
  router.push(r.targetType === 'SHOP' ? `/shop/${r.targetId}` : `/product/${r.targetId}`)
}

onMounted(load)
</script>

<template>
  <div class="page report-page">
    <div class="page-head">
      <h1 class="page-title">我的举报</h1>
      <p class="page-desc">查看你提交过的商品 / 店铺举报，以及管理员处理结果。</p>
    </div>

    <div v-loading="loading" class="report-list">
      <div v-if="!list.length" class="card empty-card">
        <el-icon :size="30"><Warning /></el-icon>
        <div class="empty-title">还没有举报记录</div>
        <p class="empty-sub">在商品页或店铺页点击「举报」，提交后会在这里跟进处理进度。</p>
      </div>

      <div v-for="r in list" :key="r.id" class="card report-item">
        <div class="report-head">
          <div class="report-target">
            <div class="report-cover">
              <img v-if="r.targetCover" :src="r.targetCover" :alt="r.targetTitle" loading="lazy" />
              <span v-else>{{ typeText(r.targetType)[0] }}</span>
            </div>
            <div class="report-target-info">
              <div class="report-title" @click="openTarget(r)">{{ r.targetTitle || ('#' + r.targetId) }}</div>
              <div class="report-meta">
                <span class="pill mute">{{ typeText(r.targetType) }}</span>
                <span v-if="r.shopName" class="text-xs text-muted">店铺：{{ r.shopName }}</span>
                <span class="text-xs text-muted">举报于 {{ r.createdAt }}</span>
              </div>
            </div>
          </div>
          <span class="pill" :class="statusClass(r.status)">{{ statusText(r.status) }}</span>
        </div>

        <div class="report-body">
          <div class="report-row"><span class="report-label">举报类型</span><span>{{ r.type }}</span></div>
          <div class="report-row"><span class="report-label">举报说明</span><span>{{ r.content }}</span></div>
          <div v-if="r.status !== 'PENDING'" class="report-reply">
            <div class="report-row">
              <span class="report-label">处理结果</span>
              <span>{{ statusText(r.status) }} · {{ r.handlerName || '管理员' }} · {{ r.handledAt }}</span>
            </div>
            <div v-if="r.handleNote" class="report-row">
              <span class="report-label">处理说明</span>
              <span>{{ r.handleNote }}</span>
            </div>
          </div>
          <div v-else class="report-pending">平台 / 店铺管理员正在核实，处理完成后会通过系统消息通知你。</div>
        </div>
      </div>

      <div v-if="total > size" class="report-pager">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="p => { page = p; load() }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-page { max-width: var(--page-max); margin: 0 auto; }
.report-list { display: flex; flex-direction: column; gap: 12px; }
.empty-card { padding: 56px 24px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 8px; }
.report-item { padding: 16px 18px; }
.report-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.report-target { display: flex; gap: 12px; min-width: 0; }
.report-cover {
  width: 52px; height: 52px; flex: none; border-radius: 10px; overflow: hidden;
  background: var(--surface-2); display: grid; place-items: center; color: var(--ink-3); font-size: 15px;
}
.report-cover img { width: 100%; height: 100%; object-fit: cover; }
.report-target-info { min-width: 0; }
.report-title { font-weight: 600; font-size: 14.5px; color: var(--text-1); cursor: pointer; }
.report-title:hover { color: var(--brand); }
.report-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 5px; }
.report-body { margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--border); display: flex; flex-direction: column; gap: 8px; }
.report-row { display: flex; gap: 12px; font-size: 13.5px; line-height: 1.7; color: var(--text-2); }
.report-label { flex: none; width: 68px; color: var(--text-3); font-size: 13px; }
.report-reply { padding: 10px 12px; border-radius: var(--r-sm); background: var(--surface-2); display: flex; flex-direction: column; gap: 6px; }
.report-pending { font-size: 12.5px; color: var(--text-3); }
.report-pager { display: flex; justify-content: flex-end; }
</style>
