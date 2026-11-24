<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Warning } from '@element-plus/icons-vue'
import { reportApi } from '@/api'
import { getUser } from '@/utils/auth'

const isPlatform = computed(() => getUser()?.role === 'ADMIN')

const list = ref([])
const total = ref(0)
const loading = ref(false)
const page = reactive({ current: 1, size: 10 })
const filters = reactive({ status: '', keyword: '' })

const pending = ref(0)
const loadPending = async () => {
  try {
    const r = await reportApi.pending()
    pending.value = Number(r.data || 0)
    window.dispatchEvent(new Event('admin:reports-changed'))
  } catch (e) { pending.value = 0 }
}

const load = async () => {
  loading.value = true
  try {
    const r = await reportApi.page({
      status: filters.status || undefined,
      keyword: filters.keyword.trim() || undefined,
      current: page.current,
      size: page.size
    })
    list.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const onSearch = () => { page.current = 1; load() }
const reset = () => { filters.status = ''; filters.keyword = ''; onSearch() }

const statusText = s => ({ PENDING: '待处理', ACCEPTED: '举报成立', REJECTED: '已驳回' }[s] || s)
const statusClass = s => ({ PENDING: 'pending', ACCEPTED: 'completed', REJECTED: 'cancelled' }[s] || 'mute')
const typeText = t => (t === 'SHOP' ? '店铺' : '商品')

const dialog = reactive({ visible: false, row: null, status: 'ACCEPTED', note: '' })
const handling = ref(false)

const openHandle = row => {
  dialog.visible = true
  dialog.row = row
  dialog.status = 'ACCEPTED'
  dialog.note = ''
}

const submitHandle = async () => {
  if (!dialog.row) return
  handling.value = true
  try {
    await reportApi.handle(dialog.row.id, { status: dialog.status, note: dialog.note.trim() })
    ElMessage.success('已处理，并已通过系统消息通知举报人')
    dialog.visible = false
    load()
    loadPending()
  } catch (e) { /* 统一提示 */ } finally {
    handling.value = false
  }
}

const openTarget = row => {
  if (!row.targetId) return
  const shopUrl = import.meta.env.VITE_SHOP_URL
    || (location.port === '5174' ? `${location.protocol}//${location.hostname}:5173` : '')
  const path = row.targetType === 'SHOP' ? `/shop/${row.targetId}` : `/product/${row.targetId}`
  window.open((shopUrl || '') + path, '_blank')
}

watch(() => page.current, load)

onMounted(() => {
  load()
  loadPending()
})
</script>

<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">举报管理</h1>
      <p class="page-desc">
        {{ isPlatform
          ? '平台可查看全部店铺的商品 / 店铺举报，处理结果会以系统消息回馈给举报人。'
          : '只显示本店商品与店铺的举报，处理结果会以系统消息回馈给举报人。' }}
        <span v-if="pending" class="pill danger" style="margin-left:8px">待处理 {{ pending }}</span>
      </p>
    </div>

    <div class="card">
      <div class="toolbar">
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:150px" @change="onSearch">
          <el-option label="待处理" value="PENDING" />
          <el-option label="举报成立" value="ACCEPTED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索举报说明 / 举报类型"
          clearable
          style="width:260px"
          @keyup.enter="onSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button :icon="Refresh" text @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column label="举报对象" min-width="230">
          <template #default="{ row }">
            <div class="target-cell">
              <div class="target-cover">
                <img v-if="row.targetCover" :src="row.targetCover" alt="" />
                <span v-else>{{ typeText(row.targetType)[0] }}</span>
              </div>
              <div class="target-info">
                <a class="target-name" @click="openTarget(row)">{{ row.targetTitle || ('#' + row.targetId) }}</a>
                <div class="target-meta">
                  <span class="pill mute">{{ typeText(row.targetType) }}</span>
                  <span v-if="row.shopName" class="text-xs text-muted">店铺：{{ row.shopName }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="举报类型" width="110" />
        <el-table-column prop="content" label="举报说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="reporterName" label="举报人" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="pill" :class="statusClass(row.status)">{{ statusText(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理信息" min-width="180">
          <template #default="{ row }">
            <template v-if="row.status !== 'PENDING'">
              <div class="text-xs">{{ row.handlerName || '管理员' }} · {{ row.handledAt }}</div>
              <div v-if="row.handleNote" class="text-xs text-muted">{{ row.handleNote }}</div>
            </template>
            <span v-else class="text-xs text-muted">待处理</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="举报时间" width="120" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" size="small" type="primary" @click="openHandle(row)">处理</el-button>
            <span v-else class="text-xs text-muted">已处理</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty">
            <el-icon :size="26"><Warning /></el-icon>
            <div class="empty-title">暂无举报</div>
            <p class="empty-sub">用户在前台商品页 / 店铺页提交举报后会出现在这里。</p>
          </div>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          layout="total, prev, pager, next, sizes"
          :total="total"
          :current-page="page.current"
          :page-size="page.size"
          :page-sizes="[10, 20, 50]"
          @current-change="p => { page.current = p }"
          @size-change="s => { page.size = s; page.current = 1; load() }"
        />
      </div>
    </div>

    <el-dialog v-model="dialog.visible" title="处理举报" width="560px">
      <div v-if="dialog.row" class="detail">
        <div class="detail-row">
          <span class="detail-label">举报对象</span>
          <span>{{ typeText(dialog.row.targetType) }}：{{ dialog.row.targetTitle || ('#' + dialog.row.targetId) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报类型</span>
          <span>{{ dialog.row.type }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报说明</span>
          <span>{{ dialog.row.content }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">举报人</span>
          <span>{{ dialog.row.reporterName }} · {{ dialog.row.createdAt }}</span>
        </div>
      </div>

      <el-form label-position="top" style="margin-top:12px">
        <el-form-item label="处理结果" required>
          <el-radio-group v-model="dialog.status">
            <el-radio value="ACCEPTED">举报成立（已处理）</el-radio>
            <el-radio value="REJECTED">驳回举报</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明（会一起发给举报人）">
          <el-input
            v-model="dialog.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="例如：已下架该商品并警告店铺；或：经核实内容正常。"
          />
        </el-form-item>
      </el-form>

      <p class="text-xs text-muted">
        处理完成后会立即向举报人发送一条系统通知，举报人可在用户端「消息中心 → 系统通知」和「我的举报」中查看。
      </p>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 14px; }
.target-cell { display: flex; align-items: center; gap: 10px; }
.target-cover {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  overflow: hidden;
  flex: none;
  display: grid;
  place-items: center;
  background: var(--surface-2);
  color: var(--text-3);
  font-size: 13px;
}
.target-cover img { width: 100%; height: 100%; object-fit: cover; }
.target-info { min-width: 0; }
.target-name {
  display: block;
  font-weight: 600;
  font-size: 13px;
  color: var(--text-1);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.target-name:hover { color: var(--brand, #c8202a); }
.target-meta { display: flex; align-items: center; gap: 8px; margin-top: 3px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.detail { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; background: var(--surface-2); border-radius: var(--r-sm); }
.detail-row { display: flex; gap: 10px; font-size: 13px; line-height: 1.7; }
.detail-label { flex: none; width: 68px; color: var(--text-3); }
</style>
