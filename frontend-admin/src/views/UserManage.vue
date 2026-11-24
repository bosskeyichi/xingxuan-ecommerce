<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adminUserApi } from '@/api'

const list = ref([])
const page = reactive({ current: 1, size: 10, total: 0 })
const query = reactive({ keyword: '' })
const loading = ref(false)

// 超级管理员主动给指定用户发送系统通知
const notifyVisible = ref(false)
const notifyTarget = ref(null)
const notifyContent = ref('')
const notifyLoading = ref(false)
const openNotify = row => {
  notifyTarget.value = row
  notifyContent.value = ''
  notifyVisible.value = true
}
const submitNotify = async () => {
  if (!notifyContent.value.trim()) { ElMessage.warning('请输入通知内容'); return }
  notifyLoading.value = true
  try {
    await adminUserApi.notify(notifyTarget.value.id, notifyContent.value.trim())
    ElMessage.success('系统通知已发送')
    notifyVisible.value = false
  } finally { notifyLoading.value = false }
}

const load = async () => {
  loading.value = true
  try {
    const r = await adminUserApi.page({
      current: page.current,
      size: page.size,
      keyword: query.keyword || undefined
    })
    list.value = r.data.records
    page.total = r.data.total
  } finally { loading.value = false }
}

const onStatus = async (row, v) => {
  try {
    await adminUserApi.status(row.id, v ? 1 : 0)
    ElMessage.success('状态已更新')
    load()
  } catch (e) { load() }
}

const onReset = async (row) => {
  const merchantTip = row.role === 'MERCHANT'
    ? '该账号是普通管理员（商家），本店商品会被下架、优惠券会被删除，店铺资料恢复默认；已成交订单作为交易记录保留。'
    : ''
  await ElMessageBox.confirm(
    `确认重置用户「${row.username}」？将清空其订单、购物车、优惠券、收藏、评价（含追评）、收货地址与浏览足迹。`
    + merchantTip
    + '账号资料（昵称 / 邮箱 / 手机号 / 头像）恢复初始，登录密码重置为 123456。此操作不可恢复。',
    '重置用户数据',
    { type: 'warning', confirmButtonText: '确认重置', cancelButtonText: '取消' }
  )
  await adminUserApi.reset(row.id)
  ElMessage.success(`「${row.username}」已重置，密码恢复为 123456`)
  load()
}

const roleText = row => (row.role === 'ADMIN' ? '超级管理员' : row.role === 'MERCHANT' ? '普通管理员' : '普通用户')
const roleClass = row => (row.role === 'ADMIN' ? 'brand' : row.role === 'MERCHANT' ? 'completed' : 'mute')

const onRole = async (row) => {
  const toMerchant = row.role !== 'MERCHANT'
  await ElMessageBox.confirm(
    toMerchant
      ? `将「${row.username}」设为普通管理员（商家）？系统会自动创建一家店铺，对方可管理本店商品、订单发货、优惠券与看板。`
      : `取消「${row.username}」的普通管理员身份？需先清空该店商品与订单，确认后店铺会被删除。`,
    toMerchant ? '设为普通管理员' : '取消普通管理员',
    { type: 'warning', confirmButtonText: toMerchant ? '确认提升' : '确认取消' }
  )
  await adminUserApi.role(row.id, toMerchant ? 'MERCHANT' : 'USER')
  ElMessage.success(toMerchant ? '已设为普通管理员，请对方重新登录' : '已取消管理员身份')
  load()
}

const onDelete = async (row) => {
  await ElMessageBox.confirm(
    `确认删除用户「${row.username}」？其订单、购物车、优惠券、收藏、评价、收货地址与浏览足迹会一并清理，操作不可恢复。`,
    '删除确认',
    { type: 'warning' }
  )
  await adminUserApi.delete(row.id)
  ElMessage.success('已删除')
  if (list.value.length === 1 && page.current > 1) page.current -= 1
  load()
}

const fmt = t => String(t || '').replace('T', ' ').slice(0, 16) || '—'

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1 class="page-title">用户管理</h1>
      <p class="page-desc">共 {{ page.total }} 位注册用户 · 可调整角色（超级管理员 / 普通管理员 / 普通用户）、启用禁用、重置数据或删除</p>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="query.keyword"
        :prefix-icon="Search"
        placeholder="搜索用户名 / 昵称"
        clearable
        style="width:260px"
        @keyup.enter="page.current = 1; load()"
        @clear="page.current = 1; load()"
      />
      <el-button type="primary" @click="page.current = 1; load()">查询</el-button>
      <el-button @click="query.keyword = ''; page.current = 1; load()">重置</el-button>
    </div>

    <div class="card">
      <el-table :data="list" class="admin-table" v-loading="loading">
        <el-table-column label="ID" width="80">
          <template #default="{ row }"><span class="font-num text-xs">{{ String(row.id).padStart(3, '0') }}</span></template>
        </el-table-column>
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:10px">
              <img v-if="row.avatar" :src="row.avatar" class="row-avatar" :alt="row.username" />
              <span v-else class="row-avatar">{{ (row.nickname || row.username || 'U')[0].toUpperCase() }}</span>
              <div>
                <div style="font-weight:600">{{ row.nickname || row.username }}</div>
                <div class="text-xs text-muted">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }"><span class="font-num text-xs">{{ row.email || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }"><span class="font-num text-xs">{{ row.phone || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="角色" width="160">
          <template #default="{ row }">
            <span class="pill" :class="roleClass(row)">{{ roleText(row) }}</span>
            <div v-if="row.shopName" class="text-xs text-muted" style="margin-top:2px">{{ row.shopName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="112">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-switch
                :model-value="row.status === 1"
                :disabled="row.role === 'ADMIN'"
                @change="v => onStatus(row, v)"
              />
              <span class="text-xs" :style="{ color: row.status === 1 ? 'var(--success)' : 'var(--text-3)' }">
                {{ row.status === 1 ? '启用' : '已禁用' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }"><span class="font-num text-xs">{{ fmt(row.createdAt) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="350" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                v-if="row.role !== 'ADMIN'"
                class="act-role"
                text
                type="primary"
                @click="onRole(row)"
              >{{ row.role === 'MERCHANT' ? '取消管理员' : '设为管理员' }}</el-button>
              <el-button class="act-reset" text :disabled="row.role === 'ADMIN'" @click="onReset(row)">重置数据</el-button>
              <el-button
                class="act-del"
                text
                :disabled="row.role === 'ADMIN' || !!row.shopId"
                :title="row.shopId ? '店铺店主需先在店铺管理调整归属' : ''"
                @click="onDelete(row)"
              >删除</el-button>
              <el-button
                v-if="row.role !== 'ADMIN'"
                class="act-notify"
                text
                type="warning"
                @click="openNotify(row)"
              >发通知</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty">
            <div class="empty-icon" style="font-family:var(--font-display)">人</div>
            <div class="empty-title">暂无用户</div>
            <p class="empty-sub">换个关键词试试。</p>
          </div>
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="load"
      />
    </div>

    <el-dialog v-model="notifyVisible" title="发送系统通知" width="480px">
      <p class="text-xs text-muted" style="margin-top:-6px">
        将以「平台官方」身份向 <b>{{ notifyTarget?.nickname || notifyTarget?.username }}</b> 发送系统通知，强制送达（不受对方接收设置影响）。
      </p>
      <el-input
        v-model="notifyContent"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="请输入通知内容…"
      />
      <template #footer>
        <el-button @click="notifyVisible = false">取消</el-button>
        <el-button type="primary" :loading="notifyLoading" @click="submitNotify">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>
