<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ChatDotRound, UserFilled } from '@element-plus/icons-vue'
import { friendApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const searching = ref(false)
const loading = ref(false)
const results = ref([])
const total = ref(0)
const page = ref(1)
const size = 12

/** 分类只在输入关键词搜索后生效（不允许不加好友就浏览全部用户） */
const CATS = [
  { key: 'ALL', label: '全部结果' },
  { key: 'ADDABLE', label: '可申请' },
  { key: 'FRIEND', label: '已是好友' }
]
const filter = ref('ALL')

// 官方 / 商家账号在用户端的好友范围受限，搜索池不含普通用户，需给出边界引导
const userStore = useUserStore()
const isStaff = computed(() => ['ADMIN', 'MERCHANT'].includes(userStore.userInfo?.role))

const friends = ref([])
const friendLoading = ref(false)

const requests = ref([])
const requestLoading = ref(false)

const initial = u => (u.nickname || u.username || '?').slice(0, 1).toUpperCase()

const loadFriends = async () => {
  friendLoading.value = true
  try {
    const r = await friendApi.list()
    friends.value = r.data || []
  } catch (e) {
    friends.value = []
  } finally {
    friendLoading.value = false
  }
}

const loadRequests = async () => {
  requestLoading.value = true
  try {
    const r = await friendApi.requests()
    requests.value = r.data || []
  } catch (e) {
    requests.value = []
  } finally {
    requestLoading.value = false
  }
}

const onSearch = async (resetPage = true) => {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入昵称、用户名或用户 ID 搜索用户')
    return
  }
  if (resetPage) page.value = 1
  searching.value = true
  loading.value = true
  try {
    const r = await friendApi.search({
      keyword: keyword.value.trim(),
      filter: filter.value,
      current: page.value,
      size
    })
    results.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch (e) {
    results.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 切换分类：必须有关键词，在关键词范围内筛选 */
const switchFilter = key => {
  if (!keyword.value.trim()) {
    ElMessage.warning('请先输入昵称 / 用户名 / 用户 ID 再筛选')
    return
  }
  if (filter.value === key) return
  filter.value = key
  onSearch(true)
}

/** 申请加好友：对方同意后才能聊天 */
const addFriend = async u => {
  try {
    await friendApi.add({ userId: u.id })
    ElMessage.success(`已向 ${u.nickname} 发送好友申请，等待对方同意`)
    u.pending = true
  } catch (e) { /* 统一提示 */ }
}

const acceptRequest = async u => {
  try {
    await friendApi.accept(u.id)
    ElMessage.success(`已同意 ${u.nickname} 的好友申请`)
    await loadRequests()
    loadFriends()
  } catch (e) { /* 统一提示 */ }
}

const rejectRequest = async u => {
  try {
    await ElMessageBox.confirm(`拒绝「${u.nickname}」的好友申请？对方不会收到通知。`, '拒绝申请', { type: 'warning' })
    await friendApi.reject(u.id)
    ElMessage.success('已拒绝')
    await loadRequests()
  } catch (e) { /* 取消或失败 */ }
}

const removeFriend = async u => {
  try {
    await ElMessageBox.confirm(`确认删除好友「${u.nickname}」？双方会话将从列表移除（重新加好友可恢复）。`, '删除好友', { type: 'warning' })
    await friendApi.remove(u.id)
    ElMessage.success('已删除好友')
    u.friend = false
    loadFriends()
  } catch (e) { /* 取消或失败 */ }
}

const goChat = u => {
  router.push({
    path: '/messages',
    query: { peerId: u.id, scene: 'FRIEND', peerName: u.nickname || u.username }
  })
}

/** 点头像 / 昵称查看对方主页 */
const goProfile = u => router.push(`/user/${u.id}`)

watch(() => route.query.keyword, k => {
  keyword.value = String(k || '')
  searching.value = false
  results.value = []
  total.value = 0
  page.value = 1
  if (keyword.value.trim()) onSearch(true)
})

onMounted(() => {
  loadFriends()
  loadRequests()
  const kw = route.query.keyword
  if (kw) {
    keyword.value = String(kw)
    onSearch(true)
  }
})
</script>

<template>
  <div class="page social-page">
    <div class="page-head">
      <h1 class="page-title">我的好友</h1>
      <p class="page-desc">按昵称、用户名或用户 ID 搜索并发送申请，对方同意后即可聊天。</p>
    </div>

    <!-- 好友申请：对方同意前不能聊天 -->
    <div v-if="requests.length || requestLoading" class="card" style="margin-bottom:16px">
      <div class="card-head">
        <div class="card-title"><el-icon><UserFilled /></el-icon> 好友申请（{{ requests.length }}）</div>
        <span class="social-hint">对方申请加你为好友，同意后才能互相聊天</span>
      </div>
      <div v-loading="requestLoading">
        <div class="user-grid">
          <div v-for="u in requests" :key="`req-${u.id}`" class="user-card">
            <el-avatar :size="44" :src="u.avatar || undefined">{{ initial(u) }}</el-avatar>
            <div class="user-main">
              <div class="user-name">{{ u.nickname }}</div>
              <div class="user-meta">@{{ u.username }} · ID {{ u.id }}</div>
            </div>
            <div class="user-actions">
              <el-button size="small" type="primary" @click="acceptRequest(u)">同意</el-button>
              <el-button size="small" @click="rejectRequest(u)">拒绝</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-head">
        <div class="card-title"><el-icon><Search /></el-icon> 搜索用户</div>
        <span class="social-hint">只能搜到开放搜索的普通用户 · 不会展示与你无关的用户</span>
      </div>
      <div class="social-search">
        <el-input
          v-model="keyword"
          placeholder="输入昵称 / 用户名 / 用户 ID"
          clearable
          style="max-width:420px"
          @keyup.enter="onSearch(true)"
        >
          <template #append><el-button :loading="loading" @click="onSearch(true)">搜索</el-button></template>
        </el-input>
      </div>

      <div v-if="!searching" class="social-empty social-idle">
        <div class="empty-title">输入关键词搜索用户</div>
        <p class="empty-sub">出于隐私考虑，不会展示全部用户列表；搜索到后发送申请，对方同意即成为好友。</p>
      </div>

      <div v-else v-loading="loading" class="social-results">
        <div class="cat-tabs">
          <button
            v-for="c in CATS"
            :key="c.key"
            type="button"
            class="cat-tab"
            :class="{ 'is-active': filter === c.key }"
            @click="switchFilter(c.key)"
          >
            {{ c.label }}
          </button>
          <span class="result-sub">关键词「{{ keyword }}」 · 共 {{ total }} 位用户</span>
        </div>

        <div v-if="!results.length" class="social-empty">
          <div class="empty-title">{{ filter === 'FRIEND' ? '没有匹配的好友' : '没有找到匹配的用户' }}</div>
          <p v-if="isStaff" class="empty-sub">
            作为官方 / 商家账号，你的可加好友范围受限（仅商家之间互加、官方与商家默认好友），普通用户不在可搜索范围内。<br />
            如需联系普通用户，请前往后台「用户管理」使用「发通知」发送系统通知。
          </p>
          <p v-else class="empty-sub">
            {{ filter === 'FRIEND' ? '先在「全部结果」或「可申请」里发送好友申请吧。' : '可以换个昵称，或直接输入对方 ID 试试。' }}
          </p>
        </div>
        <div v-else class="user-grid">
          <div v-for="u in results" :key="u.id" class="user-card">
            <el-avatar :size="46" class="click-avatar" title="查看主页" :src="u.avatar || undefined" @click="goProfile(u)">{{ initial(u) }}</el-avatar>
            <div class="user-main">
              <div class="user-name">
                {{ u.nickname }}
                <span v-if="u.friend" class="pill completed">已是好友</span>
                <span v-else-if="u.pending" class="pill mute">已申请</span>
              </div>
              <div class="user-meta">@{{ u.username }} · ID {{ u.id }}</div>
              <div v-if="u.shopName" class="user-shop">{{ u.shopName }}</div>
            </div>
            <div class="user-actions">
              <el-button v-if="u.friend" size="small" @click="goChat(u)">发消息</el-button>
              <el-button v-else-if="u.pending" size="small" disabled>已申请</el-button>
              <el-button
                v-else
                size="small"
                type="primary"
                :disabled="!u.addable"
                @click="addFriend(u)"
              >{{ u.addable ? '申请好友' : '不可添加' }}</el-button>
            </div>
          </div>
        </div>
        <div v-if="total > size" class="social-pager">
          <el-pagination
            layout="prev, pager, next"
            :total="total"
            :page-size="size"
            :current-page="page"
            @current-change="p => { page = p; onSearch(false) }"
          />
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <div class="card-head">
        <div class="card-title"><el-icon><UserFilled /></el-icon> 好友（{{ friends.length }}）</div>
      </div>
      <div v-loading="friendLoading">
        <div v-if="!friends.length" class="social-empty">
          <div class="empty-title">还没有好友</div>
          <p class="empty-sub">在上面搜索并发送好友申请，对方同意后就能开始聊天、分享商品和评价。</p>
        </div>
        <div v-else class="user-grid">
          <div v-for="u in friends" :key="u.id" class="user-card">
            <el-avatar :size="46" class="click-avatar" title="查看主页" :src="u.avatar || undefined" @click="goProfile(u)">{{ initial(u) }}</el-avatar>
            <div class="user-main">
              <div class="user-name">{{ u.nickname }}</div>
              <div class="user-meta">@{{ u.username }} · ID {{ u.id }}</div>
            </div>
            <div class="user-actions">
              <el-button size="small" type="primary" plain @click="goChat(u)">
                <el-icon><ChatDotRound /></el-icon> 发消息
              </el-button>
              <el-button v-if="!u.system" size="small" text type="danger" @click="removeFriend(u)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.social-page { max-width: var(--page-max); margin: 0 auto; }
.social-hint { font-size: 12px; color: var(--text-3); }
.social-search { margin-top: 4px; }
.social-results { margin-top: 16px; }
.social-idle { padding: 40px 0; }
.cat-tabs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.cat-tab {
  padding: 6px 14px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  transition: all .18s;
}
.cat-tab:hover { border-color: var(--brand); color: var(--brand); }
.cat-tab.is-active { background: var(--brand); border-color: var(--brand); color: #fff; }
.result-sub { font-size: 12px; color: var(--text-3); margin-left: 4px; }
.user-grid { display: flex; flex-direction: column; gap: 10px; }
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface);
}
.user-main { flex: 1; min-width: 0; }
.click-avatar { cursor: pointer; flex: none; transition: opacity .15s; }
.click-avatar:hover { opacity: .82; }
.user-name { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; font-weight: 600; color: var(--text-1); }
/* 状态标签不被昵称挤压：始终完整显示「已是好友」，必要时整行换行 */
.user-name > .pill { flex: none; }
.pill.mute { color: var(--text-3); }
.user-meta { margin-top: 3px; font-size: 12px; color: var(--text-3); }
.user-shop { margin-top: 3px; font-size: 12px; color: var(--brand); }
.user-actions { display: flex; align-items: center; gap: 6px; flex: none; }
.social-empty { padding: 32px 0; text-align: center; }
.social-pager { display: flex; justify-content: flex-end; margin-top: 14px; }

@media (max-width: 640px) {
  .user-card { align-items: flex-start; }
  .user-actions { flex-direction: column; }
}
</style>
