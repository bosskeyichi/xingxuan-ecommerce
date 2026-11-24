<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Shop, ArrowLeft, Lock } from '@element-plus/icons-vue'
import { friendApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const card = ref(null)
const error = ref('')

const isSelf = computed(() => Number(route.params.id) === Number(userStore.userInfo?.id))
const initial = computed(() => (card.value?.nickname || card.value?.username || '?').slice(0, 1).toUpperCase())

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const r = await friendApi.card(Number(route.params.id))
    card.value = r.data || null
  } catch (e) {
    card.value = null
    error.value = '无法查看该用户资料'
  } finally {
    loading.value = false
  }
}

const addFriend = async () => {
  if (!card.value) return
  try {
    await friendApi.add({ userId: card.value.id })
    ElMessage.success(`已向 ${card.value.nickname} 发送好友申请，等待对方同意`)
    card.value.pending = true
  } catch (e) { /* 统一提示 */ }
}

const chat = () => {
  if (!card.value) return
  router.push({
    path: '/messages',
    query: { peerId: card.value.id, scene: 'FRIEND', peerName: card.value.nickname }
  })
}

onMounted(load)
</script>

<template>
  <div class="page user-page">
    <div class="page-head">
      <el-button text :icon="ArrowLeft" @click="router.back()">返回</el-button>
    </div>

    <div v-loading="loading" class="card user-card">
      <div v-if="error" class="empty">
        <el-icon :size="28"><Lock /></el-icon>
        <div class="empty-title">{{ error }}</div>
        <p class="empty-sub">对方可能关闭了「允许被搜索」，只有好友才能查看资料。</p>
      </div>

      <template v-else-if="card">
        <div class="user-hero">
          <el-avatar :size="72" :src="card.avatar || undefined">{{ initial }}</el-avatar>
          <div class="user-hero-main">
            <div class="user-hero-name">
              {{ card.nickname }}
              <span v-if="card.friend" class="pill completed">好友</span>
              <span v-else-if="card.pending" class="pill mute">已申请</span>
              <span v-if="isSelf" class="pill">我自己</span>
            </div>
            <div class="user-hero-meta">@{{ card.username }} · ID {{ card.id }}</div>
            <div v-if="card.shopId" class="user-hero-shop">
              <el-icon><Shop /></el-icon>
              <router-link :to="`/shop/${card.shopId}`">{{ card.shopName }}</router-link>
            </div>
          </div>
          <div class="user-hero-actions">
            <template v-if="isSelf">
              <el-button type="primary" @click="router.push('/profile')">编辑我的资料</el-button>
            </template>
            <template v-else-if="card.friend">
              <el-button type="primary" @click="chat">
                <el-icon><ChatDotRound /></el-icon> 发消息
              </el-button>
            </template>
            <template v-else-if="card.pending">
              <el-button disabled>已发送申请</el-button>
            </template>
            <template v-else>
              <el-button type="primary" :disabled="!card.addable" @click="addFriend">
                {{ card.addable ? '申请加好友' : '对方不接受好友申请' }}
              </el-button>
            </template>
          </div>
        </div>

        <div class="user-info">
          <div class="info-row">
            <span class="info-label">昵称</span>
            <span class="info-value">{{ card.nickname }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">用户名</span>
            <span class="info-value">@{{ card.username }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">邮箱</span>
            <span class="info-value">{{ card.email || '未公开' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">手机号</span>
            <span class="info-value">{{ card.phone || '未公开' }}</span>
          </div>
          <div v-if="card.shopName" class="info-row">
            <span class="info-label">店铺</span>
            <span class="info-value">
              <router-link :to="`/shop/${card.shopId}`">{{ card.shopName }}</router-link>
            </span>
          </div>
        </div>

        <p class="user-tip">
          为保护隐私，联系方式仅对方在「设置 - 隐私」中开放时才会显示；成为好友后即可互相发消息、分享商品与评价。
        </p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.user-page { max-width: 860px; margin: 0 auto; }
.user-card { padding: 22px 24px; }
.user-hero { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; }
.user-hero-main { flex: 1; min-width: 220px; }
.user-hero-name { display: flex; align-items: center; gap: 8px; font-size: 20px; font-weight: 700; color: var(--text-1); }
.user-hero-meta { margin-top: 6px; font-size: 13px; color: var(--text-3); }
.user-hero-shop { margin-top: 8px; display: flex; align-items: center; gap: 6px; font-size: 13px; }
.user-hero-shop a { color: var(--brand); text-decoration: none; }
.user-hero-actions { display: flex; gap: 8px; }
.user-info { margin-top: 22px; border-top: 1px dashed var(--border); padding-top: 16px; display: grid; gap: 10px; }
.info-row { display: flex; gap: 14px; font-size: 13.5px; }
.info-label { width: 64px; color: var(--text-3); flex: none; }
.info-value { color: var(--text-1); }
.info-value a { color: var(--brand); text-decoration: none; }
.user-tip { margin-top: 18px; font-size: 12px; line-height: 1.8; color: var(--text-3); }
</style>
