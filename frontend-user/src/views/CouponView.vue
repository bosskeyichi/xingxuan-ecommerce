<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { couponApi } from '@/api'
import { formatPrice } from '@/utils/price'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const tab = ref('available')
const available = ref([])
const mine = ref([])
const loading = ref(false)

const isLogin = computed(() => userStore.isLogin)
const mineIds = computed(() => mine.value.map(c => c.couponId))

const loadAvailable = async () => {
  const r = await couponApi.available()
  available.value = r.data || []
}
const loadMine = async () => {
  if (!isLogin.value) { mine.value = []; return }
  try {
    const r = await couponApi.mine()
    mine.value = r.data || []
  } catch (e) { mine.value = [] }
}
const claim = async c => {
  if (!isLogin.value) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: '/coupons' } })
    return
  }
  if (mineIds.value.includes(c.id)) { ElMessage.info('这张券已经领取过了'); return }
  await couponApi.claim(c.id)
  ElMessage.success('领取成功，结算时可抵扣')
  await Promise.all([loadAvailable(), loadMine()])
}

const fmtDate = t => (t ? String(t).replace('T', ' ').slice(0, 10) : '长期有效')
const statusText = s => ({ UNUSED: '未使用', USED: '已使用' }[s] || s)
// 领取进度：保留 1 位小数，别让 1/999 被四舍五入成 0%（看起来像没领到）
const claimedRate = c => {
  const total = Number(c.total || 0)
  const claimed = Number(c.claimed || 0)
  if (!total || claimed <= 0) return 0
  return Math.min(100, Math.round(claimed / total * 1000) / 10)
}
const rateText = c => {
  const r = claimedRate(c)
  if (r <= 0) return '0%'
  if (r < 0.1) return '<0.1%'
  return (r >= 10 ? Math.round(r) : r.toFixed(1)) + '%'
}
// 只要有领取记录，进度条至少留 2% 宽度，保证肉眼可见
const barWidth = c => {
  const r = claimedRate(c)
  return (Number(c.claimed || 0) > 0 ? Math.max(r, 2) : 0) + '%'
}

const switchTab = async t => {
  tab.value = t
  if (t === 'mine') await loadMine()
}

onMounted(async () => {
  await loadAvailable()
  await loadMine()
})
</script>

<template>
  <div>
    <div class="section-head">
      <div>
        <h2 class="section-title">优惠券中心</h2>
        <div class="section-sub">领取后结算时自动抵扣，可与满减活动同享</div>
      </div>
    </div>

    <div class="coupon-layout">
      <div>
        <div class="order-tabs">
          <button class="order-tab" :class="{ 'is-active': tab === 'available' }" @click="switchTab('available')">
            可领取（{{ available.length }}）
          </button>
          <button class="order-tab" :class="{ 'is-active': tab === 'mine' }" @click="switchTab('mine')">
            我的优惠券（{{ mine.length }}）
          </button>
        </div>

        <!-- 可领取 -->
        <div v-if="tab === 'available'">
          <div v-if="!available.length" class="empty">
            <div class="empty-icon">券</div>
            <div class="empty-title">暂无可领取的优惠券</div>
            <p class="empty-sub">新券会不定期开放领取，稍后再来看看。</p>
          </div>
          <div v-else class="coupon-grid">
            <div v-for="c in available" :key="c.id" class="coupon-card">
              <div class="coupon-left">
                <div class="coupon-amount"><span>¥</span>{{ formatPrice(c.discount) }}</div>
                <div class="coupon-th">满 {{ c.threshold }} 可用</div>
              </div>
              <div class="coupon-body">
                <div class="coupon-name">{{ c.name }}</div>
                <div class="coupon-desc">
                  <span class="coupon-scope" :class="{ 'is-shop': c.shopId }">{{ c.shopId ? (c.shopName || '店铺券') : '全平台' }}</span>
                  {{ c.description || '结算时自动抵扣' }}
                </div>
                <div class="coupon-date">有效期至 {{ fmtDate(c.endAt) }}</div>
                <div class="coupon-progress">
                  <div class="cp-track"><i :style="{ width: barWidth(c) }" /></div>
                  <span>已领 {{ c.claimed || 0 }} / {{ c.total || 0 }} · {{ rateText(c) }}</span>
                </div>
              </div>
              <button
                class="coupon-btn"
                :class="{ 'is-done': mineIds.includes(c.id) }"
                :disabled="mineIds.includes(c.id)"
                @click="claim(c)"
              >{{ mineIds.includes(c.id) ? '已领取' : '领取' }}</button>
            </div>
          </div>
        </div>

        <!-- 我的优惠券 -->
        <div v-else>
          <div v-if="!isLogin" class="empty">
            <div class="empty-icon">登</div>
            <div class="empty-title">登录后查看我的优惠券</div>
            <p class="empty-sub">登录后已领取的券会保存在账号中。</p>
            <el-button class="mt-16" type="primary" @click="router.push({ path: '/login', query: { redirect: '/coupons' } })">去登录</el-button>
          </div>
          <div v-else-if="!mine.length" class="empty">
            <div class="empty-icon">券</div>
            <div class="empty-title">还没有优惠券</div>
            <p class="empty-sub">去「可领取」页领取你的第一张券吧。</p>
            <el-button class="mt-16" @click="switchTab('available')">去看看</el-button>
          </div>
          <div v-else class="coupon-grid">
            <div v-for="c in mine" :key="c.id" class="coupon-card" :class="{ 'is-used': c.status === 'USED' }">
              <div class="coupon-left">
                <div class="coupon-amount"><span>¥</span>{{ formatPrice(c.discount) }}</div>
                <div class="coupon-th">满 {{ c.threshold }} 可用</div>
              </div>
              <div class="coupon-body">
                <div class="coupon-name">{{ c.name }}</div>
                <div class="coupon-desc">
                  <span class="coupon-scope" :class="{ 'is-shop': c.shopId }">{{ c.shopId ? (c.shopName || '店铺券') : '全平台' }}</span>
                  {{ c.description || '结算时自动抵扣' }}
                </div>
                <div class="coupon-date">有效期至 {{ fmtDate(c.endAt) }}</div>
              </div>
              <span class="pill" :class="c.status === 'USED' ? 'cancelled' : 'brand'">{{ statusText(c.status) }}</span>
            </div>
          </div>
        </div>
      </div>

      <aside class="coupon-rules">
        <h3>使用规则</h3>
        <ol>
          <li>优惠券需在有效期内使用，过期作废。</li>
          <li>每笔订单限用一张，不与其他券叠加。</li>
          <li>店铺券仅限该店铺的商品使用，全平台券可用于任意店铺订单。</li>
          <li>订单商品小计需达到门槛金额才可使用。</li>
          <li>订单取消后，未使用的券会退回账户。</li>
        </ol>
        <router-link to="/" class="coupon-rules-link">去挑选商品 →</router-link>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.coupon-layout { display: grid; grid-template-columns: 1fr 280px; gap: 20px; align-items: start; }
.coupon-scope {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10.5px;
  color: #7a6a4f;
  background: #f3ecdd;
}
.coupon-scope.is-shop { color: var(--brand); background: #fdeceb; }
.coupon-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 16px; }

.coupon-card {
  display: flex;
  align-items: stretch;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  overflow: hidden;
  position: relative;
  transition: all .2s ease;
}
.coupon-card:hover { box-shadow: var(--sh-md); border-color: #ffd4d0; }
.coupon-left {
  width: 112px;
  flex-shrink: 0;
  background: linear-gradient(150deg, #e1251b, #ff5a3c);
  color: #fff;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  padding: 18px 10px;
  position: relative;
}
.coupon-left::after {
  content: '';
  position: absolute; right: -6px; top: 0; bottom: 0;
  width: 12px;
  background: radial-gradient(circle at 6px 6px, transparent 5px, var(--surface) 5px);
  background-size: 12px 12px;
  background-repeat: repeat-y;
}
.coupon-amount { font-family: var(--font-num); font-size: 34px; font-weight: 700; line-height: 1; }
.coupon-amount span { font-size: 15px; margin-right: 2px; font-weight: 500; }
.coupon-th { font-size: 11.5px; margin-top: 8px; opacity: .9; }
.coupon-body { flex: 1; padding: 16px 18px; display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.coupon-name { font-size: 15px; font-weight: 600; }
.coupon-desc { font-size: 12.5px; color: var(--ink-3); }
.coupon-date { font-size: 11.5px; color: var(--ink-4); }
.coupon-progress { display: flex; align-items: center; gap: 8px; margin-top: 6px; font-size: 11px; color: var(--ink-3); }
.cp-track { flex: 1; height: 4px; border-radius: 2px; background: var(--surface-3); overflow: hidden; }
.cp-track i { display: block; height: 100%; background: var(--promo); border-radius: 2px; }

.coupon-btn {
  align-self: center;
  margin-right: 16px;
  height: 34px; padding: 0 18px;
  border-radius: var(--r-full);
  border: 1px solid var(--brand);
  background: var(--brand);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  transition: all .18s ease;
}
.coupon-btn:hover { background: var(--brand-deep); border-color: var(--brand-deep); }
.coupon-btn.is-done { background: var(--surface-2); border-color: var(--border-strong); color: var(--ink-3); cursor: default; }
.coupon-card.is-used { opacity: .68; }
.coupon-card.is-used .coupon-left { background: linear-gradient(150deg, #9a9a9a, #bdbdbd); }
.coupon-card .pill { align-self: center; margin-right: 18px; }

.coupon-rules {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  padding: 20px;
  position: sticky;
  top: calc(var(--header-h) + 16px);
}
.coupon-rules h3 { font-size: 14.5px; font-weight: 700; margin-bottom: 14px; }
.coupon-rules ol { display: flex; flex-direction: column; gap: 10px; padding-left: 18px; }
.coupon-rules li { font-size: 12.5px; color: var(--ink-3); line-height: 1.75; list-style: decimal; }
.coupon-rules-link { display: inline-block; margin-top: 18px; font-size: 12.5px; color: var(--brand); }

@media (max-width: 1080px) {
  .coupon-layout { grid-template-columns: 1fr; }
  .coupon-rules { position: static; }
}
</style>
