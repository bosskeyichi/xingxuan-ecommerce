<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { friendApi, messageApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** PRODUCT / SHOP / REVIEW */
  type: { type: String, default: 'PRODUCT' },
  targetId: { type: [Number, String], default: null },
  title: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])
const router = useRouter()

const friends = ref([])
const loading = ref(false)
const sendingId = ref(null)

const typeText = computed(() => (
  props.type === 'SHOP' ? '店铺' : props.type === 'REVIEW' ? '评价' : '商品'
))

const initial = u => (u.nickname || u.username || '?').slice(0, 1).toUpperCase()

watch(() => props.modelValue, async visible => {
  if (!visible) return
  loading.value = true
  try {
    const r = await friendApi.list()
    friends.value = r.data || []
  } catch (e) {
    friends.value = []
  } finally {
    loading.value = false
  }
})

const send = async u => {
  if (!props.targetId) return ElMessage.warning('分享内容不存在')
  sendingId.value = u.id
  try {
    await messageApi.send({
      toId: u.id,
      scene: 'FRIEND',
      type: props.type,
      targetId: Number(props.targetId),
      content: props.title
    })
    ElMessage.success(`已分享给 ${u.nickname}`)
    emit('update:modelValue', false)
  } catch (e) { /* 统一提示 */ } finally {
    sendingId.value = null
  }
}

const goFriends = () => {
  emit('update:modelValue', false)
  router.push('/friends')
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`分享${typeText}给好友`"
    width="480px"
    @update:model-value="v => emit('update:modelValue', v)"
  >
    <div v-if="title" class="share-target">{{ title }}</div>
    <div v-loading="loading" class="share-body">
      <div v-if="!friends.length" class="share-empty">
        <div class="empty-title">还没有好友</div>
        <p class="empty-sub">先添加好友，才能把{{ typeText }}分享给对方。</p>
        <el-button type="primary" size="small" @click="goFriends">去加好友</el-button>
      </div>
      <div v-else class="share-list">
        <div v-for="u in friends" :key="u.id" class="share-item">
          <el-avatar :size="38" :src="u.avatar || undefined">{{ initial(u) }}</el-avatar>
          <div class="share-main">
            <div class="share-name">{{ u.nickname }}</div>
            <div class="share-meta">@{{ u.username }}</div>
          </div>
          <el-button
            size="small"
            type="primary"
            :loading="sendingId === u.id"
            @click="send(u)"
          >发送</el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.share-target {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  font-size: 13px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-body { min-height: 80px; max-height: 380px; overflow-y: auto; }
.share-list { display: flex; flex-direction: column; gap: 8px; }
.share-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
}
.share-main { flex: 1; min-width: 0; }
.share-name { font-weight: 600; font-size: 14px; }
.share-meta { font-size: 12px; color: var(--text-3); }
.share-empty { padding: 24px 0; text-align: center; }
</style>
