<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import { messageApi, settingApi } from '@/api'

const route = useRoute()
const router = useRouter()

// 提示音：读取「消息设置」里的开关，新消息到达时播放短音
const soundOn = ref(true)
let lastUnread = 0
const loadSoundPref = async () => {
  try {
    const r = await settingApi.get()
    soundOn.value = r.data?.notifySound !== 0
  } catch (e) { /* 默认开 */ }
}
let audioCtx = null
const beep = () => {
  if (!soundOn.value) return
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)()
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    osc.connect(gain)
    gain.connect(audioCtx.destination)
    osc.type = 'sine'
    osc.frequency.value = 880
    gain.gain.setValueAtTime(0.08, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + 0.28)
    osc.start()
    osc.stop(audioCtx.currentTime + 0.3)
  } catch (e) { /* 忽略自动播放限制 */ }
}
// 各分类未读数：用来 tab 上的小角标，看清是好友消息还是系统通知没看
const unreadStat = ref({ FRIEND: 0, SERVICE: 0, SYSTEM: 0 })
const refreshUnreadCount = async () => {
  try {
    const r = await messageApi.unread()
    const total = Number(r.data?.total || 0)
    unreadStat.value = {
      FRIEND: Number(r.data?.FRIEND || 0),
      SERVICE: Number(r.data?.SERVICE || 0),
      SYSTEM: Number(r.data?.SYSTEM || 0)
    }
    if (total > lastUnread) beep()
    lastUnread = total
  } catch (e) { /* 静默 */ }
}

const tab = ref(route.query.scene === 'SERVICE' ? 'SERVICE' : 'FRIEND')
const conversations = ref([])
const active = ref(null)
const messages = ref([])
const input = ref('')
const convLoading = ref(false)
const msgLoading = ref(false)
const sending = ref(false)

const systemList = ref([])
const systemTotal = ref(0)
const systemPage = ref(1)

const listRef = ref(null)
const isSystem = computed(() => tab.value === 'SYSTEM')

const scrollBottom = async () => {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

const loadConversations = async () => {
  if (isSystem.value) return
  convLoading.value = true
  try {
    const r = await messageApi.conversations(tab.value)
    conversations.value = r.data || []
    mergeTempConv()
  } catch (e) {
    conversations.value = []
  } finally {
    convLoading.value = false
  }
}

/** 轮询会覆盖列表，这里保留「从外部跳转进来」的临时会话，别让会话突然消失 */
const mergeTempConv = () => {
  const a = active.value
  if (!a || !a.temp) return
  // 临时会话只属于它自己的分类，切到其他 tab 时不能混进来
  if (a.scene !== tab.value) return
  if (!conversations.value.some(c => c.peerId === a.peerId && c.scene === a.scene)) {
    conversations.value.unshift(a)
  }
}

/** 从会话列表删除会话：只是不再显示，消息不删除，对方再来新消息会自动恢复 */
const removeConv = async c => {
  try {
    await ElMessageBox.confirm(`删除与「${c.peerName}」的会话？聊天记录会保留，对方再来新消息时会话会重新出现。`, '删除会话', { type: 'warning' })
  } catch (e) { return }
  try {
    await messageApi.hide(c.peerId, c.scene)
    conversations.value = conversations.value.filter(x => !(x.peerId === c.peerId && x.scene === c.scene))
    if (active.value && active.value.peerId === c.peerId && active.value.scene === c.scene) {
      active.value = null
      messages.value = []
      // 同步清掉 URL 参数，否则刷新页面又会把刚删的会话打开
      router.replace({ path: '/messages', query: {} })
    }
    ElMessage.success('会话已删除')
    window.dispatchEvent(new Event('message:refresh'))
  } catch (e) { /* 统一提示 */ }
}

const loadHistory = async (silent = false) => {
  const a = active.value
  if (!a) return
  if (!silent) msgLoading.value = true
  try {
    const r = await messageApi.history({ peerId: a.peerId, scene: a.scene, current: 1, size: 100 })
    messages.value = (r.data?.records || []).slice().reverse()
    if (!silent) scrollBottom()
  } catch (e) {
    // 非静默场景（用户主动点开会话）失败时给出提示，别让右侧一片空白让人困惑
    if (!silent) ElMessage.warning('无法打开该会话，可能对方已不是你的好友')
  } finally {
    msgLoading.value = false
  }
}

const openConv = async c => {
  // 会话属于哪个分类，tab 就跟着切，别让「客服 tab 里打开好友会话」这种错位
  if (c.scene !== tab.value && c.scene !== 'SYSTEM') tab.value = c.scene
  active.value = c
  messages.value = []
  await loadHistory()
  try { await messageApi.read(c.peerId, c.scene) } catch (e) { /* 忽略 */ }
  if (c.unread) c.unread = 0
  // 始终同步角标：顶栏的未读数由 message:refresh 事件驱动
  refreshUnreadCount()
  window.dispatchEvent(new Event('message:refresh'))
  const query = { peerId: c.peerId, scene: c.scene, peerName: c.peerName || undefined }
  if (c.shopId) query.shopId = c.shopId
  if (c.shopName) query.shopName = c.shopName
  router.replace({ path: '/messages', query })
}

/** 从商品页「联系客服」或好友页跳过来时，可能还没有会话，这里补一个空会话 */
const ensureFromQuery = async () => {
  const peerId = Number(route.query.peerId)
  if (!peerId) return
  const scene = route.query.scene === 'SERVICE' ? 'SERVICE' : 'FRIEND'
  // URL 指定了会话分类，tab 必须跟随（否则切换 tab 后又被旧参数拉回来）
  tab.value = scene

  let c = conversations.value.find(x => x.peerId === peerId && x.scene === scene)
  if (!c) {
    c = {
      peerId,
      scene,
      temp: true,
      peerName: route.query.peerName || (scene === 'SERVICE' ? '店铺客服' : `用户 #${peerId}`),
      peerHandle: '',
      peerAvatar: '',
      shopId: route.query.shopId ? Number(route.query.shopId) : null,
      shopName: route.query.shopName || '',
      lastContent: '',
      lastTime: null,
      unread: 0,
      friend: scene === 'FRIEND'
    }
    conversations.value.unshift(c)
  }
  await openConv(c)

  // 用后端资料补齐真实昵称 / 头像 / 店铺，别让标题显示「用户 #5」这类占位名
  try {
    const r = await messageApi.peer(peerId, scene)
    const d = r.data || {}
    const target = conversations.value.find(x => x.peerId === peerId && x.scene === scene) || c
    Object.assign(target, {
      peerName: d.peerName || target.peerName,
      peerHandle: d.peerHandle || '',
      peerAvatar: d.peerAvatar || '',
      shopId: d.shopId ?? target.shopId ?? null,
      shopName: d.shopName || target.shopName || '',
      friend: !!d.friend
    })
    if (active.value && active.value.peerId === peerId && active.value.scene === scene) {
      active.value = target
    }
  } catch (e) { /* 保持占位名 */ }
}

/** 点头像看对方主页：好友进用户主页，客服进店铺页 */
const goPeer = c => {
  if (!c) return
  if (c.scene === 'SERVICE') {
    if (c.shopId) router.push(`/shop/${c.shopId}`)
    return
  }
  if (c.scene === 'FRIEND') router.push(`/user/${c.peerId}`)
}

const send = async () => {
  const content = input.value.trim()
  if (!content) return
  if (!active.value) return ElMessage.warning('请先选择会话')
  sending.value = true
  try {
    const payload = { toId: active.value.peerId, scene: active.value.scene, type: 'TEXT', content }
    if (active.value.scene === 'SERVICE') {
      payload.shopId = active.value.shopId || Number(route.query.shopId) || undefined
    }
    const r = await messageApi.send(payload)
    messages.value.push(r.data)
    input.value = ''
    active.value.lastContent = content
    scrollBottom()
    window.dispatchEvent(new Event('message:refresh'))
  } catch (e) { /* 统一提示 */ } finally {
    sending.value = false
  }
}

const onEnter = e => {
  if (e.shiftKey) return
  e.preventDefault()
  send()
}

const openCard = m => {
  if (!m.targetLinkId) return
  router.push(m.type === 'SHOP' ? `/shop/${m.targetLinkId}` : `/product/${m.targetLinkId}`)
}

const loadSystem = async () => {
  try {
    const r = await messageApi.system({ current: systemPage.value, size: 10 })
    systemList.value = r.data?.records || []
    systemTotal.value = r.data?.total || 0
    // 后端在查询时已把系统通知标记为已读，这里刷新顶栏角标
    refreshUnreadCount()
    window.dispatchEvent(new Event('message:refresh'))
  } catch (e) {
    systemList.value = []
  }
}

const switchTab = async name => {
  tab.value = name
  active.value = null
  messages.value = []
  // 清掉 URL 上的旧会话参数，别让切换分类后被旧参数重新打开
  if (route.query.peerId || route.query.scene || route.query.peerName) {
    router.replace({ path: '/messages', query: {} })
  }
  if (name === 'SYSTEM') {
    await loadSystem()
  } else {
    await loadConversations()
  }
}

let timer = null
const poll = async () => {
  if (document.visibilityState !== 'visible') return
  if (!isSystem.value) {
    await loadConversations()
    if (active.value) await loadHistory(true)
  }
  await refreshUnreadCount()
}

onMounted(async () => {
  await loadSoundPref()
  await loadConversations()
  await ensureFromQuery()
  await refreshUnreadCount()
  timer = setInterval(poll, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// 已在消息页时再从好友页 / 商品页跳过来（组件被复用，不触发 onMounted），这里接管
watch(() => route.query.peerId, nv => {
  if (!nv) return
  // 自己点击会话时 URL 也会变，这种情况已经打开过了，不必重复加载
  if (active.value && String(active.value.peerId) === String(nv)) return
  ensureFromQuery()
})
</script>

<template>
  <div class="page msg-page">
    <div class="page-head">
      <h1 class="page-title">消息中心</h1>
      <p class="page-desc">好友聊天、店铺客服咨询与系统通知。</p>
    </div>

    <el-tabs v-model="tab" class="msg-tabs" @tab-change="switchTab">
      <el-tab-pane name="FRIEND">
        <template #label>
          好友消息
          <span v-if="unreadStat.FRIEND" class="tab-badge">{{ unreadStat.FRIEND > 99 ? '99+' : unreadStat.FRIEND }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="SERVICE">
        <template #label>
          客服消息
          <span v-if="unreadStat.SERVICE" class="tab-badge">{{ unreadStat.SERVICE > 99 ? '99+' : unreadStat.SERVICE }}</span>
        </template>
      </el-tab-pane>
      <el-tab-pane name="SYSTEM">
        <template #label>
          系统通知
          <span v-if="unreadStat.SYSTEM" class="tab-badge">{{ unreadStat.SYSTEM > 99 ? '99+' : unreadStat.SYSTEM }}</span>
        </template>
      </el-tab-pane>
    </el-tabs>

    <div v-if="!isSystem" class="msg-layout">
      <aside v-loading="convLoading" class="card msg-side">
        <div class="msg-side-head">会话（{{ conversations.length }}）</div>
        <div v-if="!conversations.length" class="msg-empty">暂无会话</div>
        <div
          v-for="c in conversations"
          :key="`${c.scene}-${c.peerId}`"
          class="conv-item"
          :class="{ 'is-active': active && active.peerId === c.peerId && active.scene === c.scene }"
          @click="openConv(c)"
        >
          <el-avatar
            :size="40"
            class="conv-avatar"
            :src="c.peerAvatar || undefined"
            :title="c.scene === 'SERVICE' ? '查看店铺' : '查看主页'"
            @click.stop="goPeer(c)"
          >{{ (c.peerName || '?').slice(0, 1) }}</el-avatar>
          <div class="conv-main">
            <div class="conv-name">
              {{ c.peerName }}
              <span v-if="c.scene === 'SERVICE'" class="conv-tag">客服</span>
            </div>
            <div class="conv-last">{{ c.lastContent || '开始聊天吧' }}</div>
          </div>
          <div class="conv-meta">
            <span class="conv-time">{{ c.lastTime }}</span>
            <span v-if="c.unread" class="conv-badge">{{ c.unread > 99 ? '99+' : c.unread }}</span>
          </div>
          <span class="conv-del" title="删除会话" @click.stop="removeConv(c)">&times;</span>
        </div>
      </aside>

      <section class="card msg-main">
        <template v-if="active">
          <div class="chat-head">
            <el-avatar
              :size="34"
              class="conv-avatar"
              :src="active.peerAvatar || undefined"
              :title="active.scene === 'SERVICE' ? '查看店铺' : '查看主页'"
              @click="goPeer(active)"
            >
              {{ (active.peerName || '?').slice(0, 1) }}
            </el-avatar>
            <div>
              <div class="chat-name">
                {{ active.peerName }}
                <el-button
                  v-if="active.scene === 'FRIEND'"
                  text
                  size="small"
                  class="chat-profile-btn"
                  @click="goPeer(active)"
                >查看主页</el-button>
                <el-button
                  v-else-if="active.shopId"
                  text
                  size="small"
                  class="chat-profile-btn"
                  @click="goPeer(active)"
                >进店看看</el-button>
              </div>
              <div class="chat-sub">
                {{ active.scene === 'SERVICE' ? (active.shopName || '店铺客服') : ('@' + (active.peerHandle || '')) }}
              </div>
            </div>
          </div>

          <div ref="listRef" v-loading="msgLoading" class="chat-list">
            <div v-if="!messages.length" class="msg-empty">
              <div class="empty-title">还没有消息</div>
              <p class="empty-sub">发一条消息打个招呼吧。</p>
            </div>
            <div v-for="m in messages" :key="m.id" class="chat-row" :class="{ 'is-mine': m.mine }">
              <el-avatar :size="32" :src="m.fromAvatar || undefined">
                {{ (m.fromName || '?').slice(0, 1) }}
              </el-avatar>
              <div class="chat-body">
                <div class="chat-meta">{{ m.mine ? '我' : m.fromName }} · {{ m.time }}</div>
                <div v-if="m.type === 'TEXT'" class="bubble">{{ m.content }}</div>
                <div v-else class="bubble share-bubble" @click="openCard(m)">
                  <img v-if="m.targetCover" :src="m.targetCover" class="share-cover" alt="" />
                  <div class="share-info">
                    <div class="share-title">{{ m.targetTitle || '分享内容' }}</div>
                    <div class="share-sub">{{ m.targetSubtitle }}</div>
                    <div class="share-tip">点击查看详情</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="chat-input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              resize="none"
              maxlength="2000"
              placeholder="输入消息，Enter 发送，Shift + Enter 换行"
              @keydown.enter="onEnter"
            />
            <el-button type="primary" :loading="sending" @click="send">发送</el-button>
          </div>
        </template>
        <div v-else class="msg-empty msg-empty-big">
          <el-icon :size="30"><ChatDotRound /></el-icon>
          <div class="empty-title">选择左侧会话开始聊天</div>
        </div>
      </section>
    </div>

    <div v-else class="card system-card">
      <div v-if="!systemList.length" class="msg-empty msg-empty-big">
        <div class="empty-title">暂无系统通知</div>
        <p class="empty-sub">举报处理结果等通知会出现在这里。</p>
      </div>
      <div v-else class="system-list">
        <div v-for="m in systemList" :key="m.id" class="system-item">
          <div class="system-time">{{ m.time }}</div>
          <div class="system-content">{{ m.content }}</div>
        </div>
      </div>
      <div v-if="systemTotal > 10" class="msg-pager">
        <el-pagination
          layout="prev, pager, next"
          :total="systemTotal"
          :page-size="10"
          :current-page="systemPage"
          @current-change="p => { systemPage = p; loadSystem() }"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.msg-page { max-width: var(--page-max); margin: 0 auto; }
.msg-tabs { margin-bottom: 4px; }
.tab-badge {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  margin-left: 5px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 10.5px;
  line-height: 16px;
  text-align: center;
  vertical-align: 1px;
}
.conv-avatar { cursor: pointer; flex: none; }
.chat-profile-btn { margin-left: 4px; font-size: 12px; padding: 0 4px; height: 20px; }
.msg-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  min-height: 540px;
}
.msg-side { padding: 8px; overflow-y: auto; max-height: 640px; }
.msg-side-head {
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}
.conv-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background .15s;
}
.conv-item:hover { background: var(--surface-2); }
.conv-item.is-active { background: var(--brand-soft); }
.conv-del {
  position: absolute;
  top: 6px;
  right: 8px;
  width: 18px;
  height: 18px;
  line-height: 16px;
  border-radius: 50%;
  text-align: center;
  font-size: 14px;
  color: var(--text-3);
  opacity: 0;
  transition: opacity .15s, background .15s, color .15s;
}
.conv-item:hover .conv-del { opacity: 1; }
.conv-del:hover { background: var(--danger, #e5484d); color: #fff; }
.conv-main { flex: 1; min-width: 0; }
.conv-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-tag {
  font-size: 11px;
  font-weight: 500;
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: 4px;
  padding: 0 4px;
}
.conv-last {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-meta { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; flex: none; }
.conv-time { font-size: 11px; color: var(--text-3); }
.conv-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.msg-main { display: flex; flex-direction: column; padding: 0; overflow: hidden; }
.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.chat-name { font-weight: 600; color: var(--text-1); }
.chat-sub { font-size: 12px; color: var(--text-3); }
.chat-list {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  max-height: 470px;
  min-height: 360px;
  background: var(--surface-2);
}
.chat-row { display: flex; gap: 8px; margin-bottom: 14px; }
.chat-row.is-mine { flex-direction: row-reverse; }
.chat-body { max-width: 72%; }
.chat-meta { font-size: 11px; color: var(--text-3); margin-bottom: 4px; }
.chat-row.is-mine .chat-meta { text-align: right; }
.bubble {
  padding: 9px 12px;
  border-radius: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-1);
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-row.is-mine .bubble { background: var(--brand); border-color: var(--brand); color: #fff; }
.share-bubble { display: flex; gap: 10px; padding: 8px; cursor: pointer; min-width: 220px; }
.chat-row.is-mine .share-bubble { background: var(--surface); border-color: var(--border); color: var(--text-1); }
.share-cover { width: 56px; height: 56px; border-radius: 6px; object-fit: cover; flex: none; }
.share-info { min-width: 0; }
.share-title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-sub {
  font-size: 12px;
  color: var(--text-3);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-tip { font-size: 11px; color: var(--brand); margin-top: 4px; }
.chat-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}
.chat-input .el-input { flex: 1; }
.msg-empty { padding: 24px 12px; text-align: center; color: var(--text-3); font-size: 13px; }
.msg-empty-big {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 420px;
}
.system-card { padding: 16px; }
.system-list { display: flex; flex-direction: column; gap: 12px; }
.system-item {
  padding: 12px 14px;
  border-left: 3px solid var(--brand);
  background: var(--surface-2);
  border-radius: var(--r-xs);
}
.system-time { font-size: 12px; color: var(--text-3); margin-bottom: 4px; }
.system-content { font-size: 14px; color: var(--text-1); line-height: 1.6; }
.msg-pager { display: flex; justify-content: flex-end; margin-top: 12px; }

@media (max-width: 900px) {
  .msg-layout { grid-template-columns: 1fr; }
  .msg-side { max-height: 260px; }
}
</style>
