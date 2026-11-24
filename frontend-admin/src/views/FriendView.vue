<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, ChatDotRound, UserFilled, Refresh } from '@element-plus/icons-vue'
import { friendApi } from '@/api'
import { getUser } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const isPlatform = computed(() => getUser()?.role === 'ADMIN')

const keyword = ref('')
const searching = ref(false)
const loading = ref(false)
const results = ref([])
const total = ref(0)
const page = ref(1)
const size = 12

/** 分类只在输入关键词搜索后生效（不允许不加好友就浏览全部管理员） */
const CATS = [
  { key: 'ALL', label: '全部结果' },
  { key: 'ADDABLE', label: '可申请' },
  { key: 'FRIEND', label: '已是好友' }
]
const filter = ref('ALL')

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
    ElMessage.warning('请输入昵称、用户名或管理员 ID 搜索')
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

const switchFilter = key => {
  if (!keyword.value.trim()) {
    ElMessage.warning('请先输入昵称 / 用户名 / 管理员 ID 再筛选')
    return
  }
  if (filter.value === key) return
  filter.value = key
  onSearch(true)
}

/** 申请加好友：对方同意后才能聊天（超级管理员默认拥有全部商家好友，无需申请） */
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
    await ElMessageBox.confirm(`拒绝「${u.nickname}」的好友申请？`, '拒绝申请', { type: 'warning' })
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

/** 点头像查看对方主页 */
const goProfile = u => router.push(`/user/${u.id}`)

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
  <div>
    <div class="page-head">
      <h1 class="page-title">好友</h1>
      <p class="page-desc">
        普通管理员（商家）之间需发送申请、对方同意后才能聊天；超级管理员与商家之间为系统默认好友，双方列表都会自动出现且不可删除。
      </p>
    </div>

    <div class="card notice-card">
      <el-icon><UserFilled /></el-icon>
      <span v-if="isPlatform">当前是超级管理员：全部商家的好友关系已自动建立，可直接在「消息」中发起会话，无需也无法手动删除。</span>
      <span v-else>你与超级管理员已是系统默认好友，会自动出现在好友列表和消息列表里，可直接沟通运营事项。</span>
    </div>

    <!-- 好友申请：对方同意前不能聊天 -->
    <div v-if="requests.length || requestLoading" class="card" style="margin-bottom:16px">
      <div class="card-head">
        <div class="card-title"><el-icon><UserFilled /></el-icon> 好友申请（{{ requests.length }}）</div>
        <span class="text-xs text-muted">对方申请加你为好友，同意后才能互相聊天</span>
      </div>
      <div v-loading="requestLoading">
        <div class="user-grid">
          <div v-for="u in requests" :key="`req-${u.id}`" class="user-card">
            <el-avatar :size="44" class="click-avatar" title="查看主页" :src="u.avatar || undefined" @click="goProfile(u)">{{ initial(u) }}</el-avatar>
            <div class="user-main">
              <div class="user-name">{{ u.nickname }}</div>
              <div class="user-meta">@{{ u.username }} · ID {{ u.id }}</div>
              <div v-if="u.shopName" class="user-shop">店铺：{{ u.shopName }}</div>
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
        <div class="card-title"><el-icon><Search /></el-icon> 搜索管理员</div>
        <span class="text-xs text-muted">支持昵称 / 用户名 / 管理员 ID · 不会展示与你无关的管理员</span>
      </div>
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="输入昵称 / 用户名 / 管理员 ID"
          clearable
          style="max-width:420px"
          @keyup.enter="onSearch(true)"
        >
          <template #append><el-button :loading="loading" @click="onSearch(true)">搜索</el-button></template>
        </el-input>
        <el-button :icon="Refresh" text @click="loadFriends">刷新好友</el-button>
      </div>

      <div v-if="!searching" class="empty" style="padding:40px 0">
        <div class="empty-title">输入关键词搜索管理员</div>
        <p class="empty-sub">出于隐私考虑，不会展示全部管理员列表；搜索到后发送申请，对方同意即成为好友。</p>
      </div>

      <div v-else v-loading="loading" class="result-block">
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
          <span class="result-sub">关键词「{{ keyword }}」 · 共 {{ total }} 位管理员</span>
        </div>

        <div v-if="!results.length" class="empty">
          <div class="empty-title">{{ filter === 'FRIEND' ? '没有匹配的好友' : '没有找到匹配的管理员' }}</div>
          <p class="empty-sub">可以换个昵称，或直接输入对方 ID 试试。</p>
        </div>
        <template v-else>
          <div class="user-grid">
            <div v-for="u in results" :key="u.id" class="user-card">
              <el-avatar :size="44" class="click-avatar" title="查看主页" :src="u.avatar || undefined" @click="goProfile(u)">{{ initial(u) }}</el-avatar>
              <div class="user-main">
                <div class="user-name">
                  {{ u.nickname }}
                  <span v-if="u.friend" class="pill completed">已是好友</span>
                  <span v-else-if="u.pending" class="pill mute">已申请</span>
                </div>
                <div class="user-meta">@{{ u.username }} · ID {{ u.id }}</div>
                <div v-if="u.shopName" class="user-shop">店铺：{{ u.shopName }}</div>
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
          <div v-if="total > size" class="pager">
            <el-pagination
              layout="prev, pager, next"
              :total="total"
              :page-size="size"
              :current-page="page"
              @current-change="p => { page = p; onSearch(false) }"
            />
          </div>
        </template>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <div class="card-head">
        <div class="card-title"><el-icon><UserFilled /></el-icon> 我的好友（{{ friends.length }}）</div>
      </div>
      <div v-loading="friendLoading">
        <div v-if="!friends.length" class="empty">
          <div class="empty-title">还没有好友</div>
          <p class="empty-sub">在上面搜索其他管理员并发送申请，对方同意后就能互相发消息沟通运营事项。</p>
        </div>
        <div v-else class="user-grid">
          <div v-for="u in friends" :key="u.id" class="user-card">
            <el-avatar :size="44" class="click-avatar" title="查看主页" :src="u.avatar || undefined" @click="goProfile(u)">{{ initial(u) }}</el-avatar>
            <div class="user-main">
              <div class="user-name">
                {{ u.nickname }}
                <span v-if="u.system" class="sys-tag">系统默认</span>
              </div>
              <div class="user-meta">@{{ u.username }} · ID {{ u.id }}</div>
              <div v-if="u.shopName" class="user-shop">店铺：{{ u.shopName }}</div>
              <div v-if="u.system" class="user-shop">超级管理员与商家的好友关系由系统默认建立，不可删除</div>
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
.notice-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--text-2);
  border-left: 3px solid var(--brand, #c8202a);
}
.search-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 4px; }
.cat-tabs { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.cat-tab {
  padding: 5px 13px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  font-size: 12.5px;
  color: var(--text-2);
  cursor: pointer;
  transition: all .18s;
}
.cat-tab:hover { border-color: var(--brand, #c8202a); color: var(--brand, #c8202a); }
.cat-tab.is-active { background: var(--brand, #c8202a); border-color: var(--brand, #c8202a); color: #fff; }
.result-sub { font-size: 12px; color: var(--text-3); margin-left: 4px; }
.result-block { margin-top: 16px; }
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
.user-name { display: flex; align-items: center; gap: 8px; font-weight: 600; color: var(--text-1); }
.sys-tag {
  padding: 0 6px;
  border-radius: 999px;
  background: var(--surface-2, #f2f3f5);
  color: var(--text-3);
  font-size: 11px;
  font-weight: 400;
  line-height: 18px;
}
.user-meta { margin-top: 3px; font-size: 12px; color: var(--text-3); }
.user-shop { margin-top: 3px; font-size: 12px; color: var(--brand, #c8202a); }
.user-actions { display: flex; align-items: center; gap: 6px; flex: none; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
