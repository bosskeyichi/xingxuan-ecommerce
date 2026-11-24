<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, User, Phone, Message } from '@element-plus/icons-vue'
import { cartApi, orderApi, authApi, addressApi, couponApi } from '@/api'
import { formatPrice } from '@/utils/price'
import { useUserStore } from '@/stores/user'
import AddressForm from '@/components/AddressForm.vue'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const items = ref([])
const loading = ref(false)
const pageLoading = ref(true)

const total = computed(() => items.value.reduce((s, r) => s + Number(r.subtotal || 0), 0))
const count = computed(() => items.value.reduce((s, r) => s + Number(r.quantity || 0), 0))

// 多商家：按店铺分组展示，提交后每个店铺生成一笔订单
const shopGroups = computed(() => {
  const map = new Map()
  for (const r of items.value) {
    const key = r.shopId ?? 0
    if (!map.has(key)) {
      map.set(key, { shopId: key, shopName: r.shopName || '店铺', items: [], subtotal: 0 })
    }
    const g = map.get(key)
    g.items.push(r)
    g.subtotal += Number(r.subtotal || 0)
  }
  return [...map.values()]
})

// 优惠券：一张券只抵扣一笔店铺订单。
// 全平台券按「单笔最大店铺金额」判定；店铺券必须本次结算含该店铺商品且金额达门槛
const coupons = ref([])
const pickedCouponId = ref(null)
const maxShopSubtotal = computed(() => shopGroups.value.reduce((m, g) => Math.max(m, g.subtotal), 0))
const shopSubtotalMap = computed(() =>
  Object.fromEntries(shopGroups.value.map(g => [g.shopId, g.subtotal])))
const usableCoupons = computed(() => coupons.value.filter(c => {
  const base = c.shopId ? (shopSubtotalMap.value[c.shopId] || 0) : maxShopSubtotal.value
  return base > 0 && (!Number(c.threshold) || base >= Number(c.threshold))
}))
const pickedCoupon = computed(() => usableCoupons.value.find(x => x.id === pickedCouponId.value) || null)
// 与后端一致：店铺券最多抵掉该店铺订单，平台券最多抵掉命中的那笔店铺订单，不能把总额减成负数
const discount = computed(() => {
  const c = pickedCoupon.value
  if (!c) return 0
  const base = c.shopId ? (shopSubtotalMap.value[c.shopId] || 0) : maxShopSubtotal.value
  return Math.min(Number(c.discount || 0), base)
})
const payTotal = computed(() => Math.max(0, total.value - discount.value))

const form = reactive({ receiverName: '', receiverPhone: '', receiverAddr: '', remark: '' })
const rules = {
  receiverName: [{ required: true, message: '请输入收货人姓名', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入 11 位有效手机号', trigger: 'blur' }
  ],
  receiverAddr: [{ required: true, message: '请输入收货地址', trigger: 'blur' }]
}

// ---------- 地址簿 ----------
const addresses = ref([])
const pickedId = ref(null)
const addrDialog = ref(false)

const loadAddresses = async () => {
  try {
    const r = await addressApi.list()
    addresses.value = r.data || []
    const def = addresses.value.find(a => a.isDefault === 1) || addresses.value[0]
    if (def) pickAddress(def)
  } catch (e) { addresses.value = [] }
}

const pickAddress = a => {
  pickedId.value = a.id
  form.receiverName = a.receiverName
  form.receiverPhone = a.receiverPhone
  form.receiverAddr = `${a.region} ${a.detail}`
}

const useManualAddress = () => {
  pickedId.value = null
  form.receiverAddr = ''
}

const onAddrSaved = () => {
  addrDialog.value = false
  loadAddresses()
  ElMessage.success('地址已保存，可直接选用')
}

const load = async () => {
  pageLoading.value = true
  try {
    const r = await cartApi.list()
    items.value = (r.data || []).filter(x => x.selected === 1)
    if (!items.value.length) {
      ElMessage.warning('请先在购物车中选择商品')
      router.push('/cart')
      return
    }
    await loadAddresses()
    if (userStore.isLogin) {
      try {
        // 金额筛选交给前端按店铺判定，这里取全部未使用券
        const r2 = await couponApi.usable(0)
        coupons.value = r2.data || []
      } catch (e) { coupons.value = [] }
    }
    if (!pickedId.value) {
      try {
        const me = await authApi.me()
        if (me.data) {
          form.receiverName = form.receiverName || me.data.nickname || me.data.username
          if (!form.receiverPhone && me.data.phone) form.receiverPhone = me.data.phone
        }
      } catch (e) { /* 忽略 */ }
    }
  } catch (e) {
    items.value = []
  } finally {
    pageLoading.value = false
  }
}

// 售罄 / 超库存的行后端会直接报错，这里提前拦下，别让用户填完地址才失败
const invalidItems = computed(() => items.value.filter(
  i => Number(i.stock) <= 0 || Number(i.quantity) > Number(i.stock)))

const onSubmit = async () => {
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (invalidItems.value.length) {
    ElMessage.warning('有商品库存不足，请返回购物车取消勾选后再结算')
    return
  }
  loading.value = true
  try {
    const r = await orderApi.submit({ ...form, userCouponId: pickedCouponId.value || undefined })
    // 已结算的购物车行被后端清空，同步顶栏件数
    window.dispatchEvent(new Event('cart:refresh'))
    const orderNos = Array.isArray(r.data) ? r.data : [r.data]
    if (orderNos.length > 1) {
      // 跨店订单按店铺拆开（各自发货 / 售后），但支付可以一次付清
      let payNow = true
      try {
        await ElMessageBox.confirm(
          `已按店铺拆分为 ${orderNos.length} 笔订单，合计 ¥${payTotal.value.toFixed(2)}。是否一次性支付全部？`,
          '合并支付',
          { type: 'info', confirmButtonText: '全部支付', cancelButtonText: '稍后支付' }
        )
      } catch (e) { payNow = false }
      if (payNow) {
        const pr = await orderApi.payBatch(orderNos)
        ElMessage.success(`订单已创建，已支付 ${pr.data ?? orderNos.length} 笔`)
        router.push({ path: '/orders', query: { status: 'PAID' } })
      } else {
        ElMessage.success(`已生成 ${orderNos.length} 笔订单，可在订单页勾选后合并支付`)
        router.push({ path: '/orders', query: { status: 'PENDING_PAYMENT' } })
      }
    } else {
      ElMessage.success('订单创建成功')
      router.push(`/order/${orderNos[0]}`)
    }
  } finally { loading.value = false }
}

onMounted(load)

// 商品变动导致券不再可用时自动取消选择，别让提交后才报错
watch(usableCoupons, list => {
  if (pickedCouponId.value && !list.some(c => c.id === pickedCouponId.value)) {
    pickedCouponId.value = null
  }
})
</script>

<template>
  <div v-loading="pageLoading">
    <div class="section-head">
      <div>
        <h2 class="section-title">确认订单</h2>
        <div class="section-sub">请核对商品与收货信息，提交后可在订单页完成模拟支付</div>
      </div>
      <el-button text @click="$router.push('/cart')">← 返回购物车</el-button>
    </div>

    <div class="checkout-layout">
      <div style="display:flex;flex-direction:column;gap:20px">
        <div class="form-card">
          <h3><span class="idx">1</span>收货地址</h3>
          <div v-if="addresses.length" class="addr-pick-grid" style="margin-bottom:18px">
            <div
              v-for="a in addresses"
              :key="a.id"
              class="addr-pick"
              :class="{ 'is-active': pickedId === a.id }"
              @click="pickAddress(a)"
            >
              <span v-if="pickedId === a.id" class="tick">✓</span>
              <div class="addr-head">
                <b>{{ a.receiverName }}</b>
                <span class="font-num text-sm text-muted">{{ a.receiverPhone }}</span>
                <span v-if="a.isDefault === 1" class="pill brand" style="margin-left:auto">默认</span>
              </div>
              <div class="addr-region">{{ a.region }}</div>
              <div class="addr-detail">{{ a.detail }}</div>
            </div>
            <button class="addr-new" @click="addrDialog = true">＋ 新增收货地址</button>
          </div>
          <div v-else class="addr-new" style="margin-bottom:18px" @click="addrDialog = true">
            ＋ 还没有地址簿，点击新增收货地址
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
            <div class="form-row-2">
              <el-form-item label="收货人" prop="receiverName">
                <el-input v-model="form.receiverName" :prefix-icon="User" placeholder="请输入收货人姓名" />
              </el-form-item>
              <el-form-item label="手机号" prop="receiverPhone">
                <el-input v-model="form.receiverPhone" :prefix-icon="Phone" placeholder="11 位手机号" maxlength="11" />
              </el-form-item>
            </div>
            <el-form-item label="收货地址" prop="receiverAddr">
              <el-input
                v-model="form.receiverAddr"
                :prefix-icon="Location"
                type="textarea"
                :rows="2"
                placeholder="省 / 市 / 区 / 详细地址"
              />
            </el-form-item>
            <el-form-item label="订单备注">
              <el-input v-model="form.remark" :prefix-icon="Message" type="textarea" :rows="2" placeholder="选填：给商家的留言" />
            </el-form-item>
          </el-form>
        </div>

        <div class="form-card">
          <h3><span class="idx">2</span>商品清单</h3>
          <div v-if="shopGroups.length > 1" class="split-tip">
            本次结算涉及 {{ shopGroups.length }} 家店铺，提交后将生成 {{ shopGroups.length }} 笔订单，可分别支付与发货
          </div>
          <div v-for="g in shopGroups" :key="`ck-${g.shopId}`" class="ck-shop">
            <div class="ck-shop-head">
              <span class="ck-shop-name">{{ g.shopName }}</span>
              <span class="text-sm text-muted">小计 ¥{{ g.subtotal.toFixed(2) }}</span>
            </div>
            <div v-for="v in g.items" :key="v.id" class="cart-row" style="padding:14px 0">
              <div class="cart-thumb" style="width:64px;height:64px">
                <img v-if="v.productCover" :src="v.productCover" :alt="v.productName" loading="lazy" />
                <div v-else class="img-ph">{{ (v.productName || '?')[0] }}</div>
              </div>
              <div class="cart-info">
                <div class="cart-name">{{ v.productName }}</div>
                <div class="cart-meta">¥{{ formatPrice(v.price) }} × {{ v.quantity }}</div>
              </div>
              <div class="cart-sub price"><span class="cur">¥</span>{{ formatPrice(v.subtotal) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="cart-summary-card">
        <h3 style="font-size:16px;margin-bottom:18px">订单摘要</h3>
        <div class="summary-row"><span>商品件数</span><span class="fw-600">{{ count }} 件</span></div>
        <div class="summary-row"><span>商品总价</span><span class="price price-sm"><span class="cur">¥</span>{{ total.toFixed(2) }}</span></div>
        <div class="summary-row"><span>运费</span><span class="fw-600" style="color:var(--success)">免运费</span></div>

        <div v-if="usableCoupons.length" class="summary-coupon">
          <div class="summary-row" style="margin-bottom:8px"><span>优惠券</span><span class="fw-600 text-muted">共 {{ usableCoupons.length }} 张可用</span></div>
          <div class="coupon-pick">
            <label v-for="c in usableCoupons" :key="c.id" class="coupon-opt" :class="{ on: pickedCouponId === c.id }">
              <input type="radio" :value="c.id" v-model="pickedCouponId" name="coupon" />
              <span class="coupon-opt-amt">¥{{ formatPrice(c.discount) }}</span>
              <span class="coupon-opt-name">{{ c.name }}（满{{ c.threshold }}）</span>
              <span class="coupon-opt-scope">{{ c.shopId ? (c.shopName || '店铺券') : '全平台' }}</span>
            </label>
          </div>
          <div class="coupon-tip">
            店铺券只能抵扣对应店铺的订单；全平台券抵扣金额最高且满足门槛的那笔店铺订单
          </div>
        </div>

        <div class="summary-row">
          <span>优惠</span>
          <span class="fw-600" :style="{ color: discount > 0 ? 'var(--danger)' : 'var(--text-3)' }">
            {{ discount > 0 ? '- ¥' + discount.toFixed(2) : '—' }}
          </span>
        </div>
        <div class="summary-row total">
          <span>应付总额</span>
          <span class="price summary-total"><span class="cur">¥</span>{{ payTotal.toFixed(2) }}</span>
        </div>

        <button class="btn-grad" :disabled="loading || invalidItems.length > 0" @click="onSubmit">
          {{ loading ? '提交中…' : (invalidItems.length ? '请先处理库存不足的商品' : '提交订单') }}
        </button>
        <div v-if="invalidItems.length" class="cart-note" style="color:var(--danger)">
          有 {{ invalidItems.length }} 件商品库存不足，请返回购物车取消勾选后再结算
        </div>
        <div class="cart-note">模拟支付环境 · 不会产生任何真实交易</div>
      </div>
    </div>

    <!-- 快捷新增地址 -->
    <el-dialog v-model="addrDialog" title="新增收货地址" width="460px">
      <AddressForm @saved="onAddrSaved" @cancel="addrDialog = false" />
    </el-dialog>
  </div>
</template>

<style scoped>
.split-tip {
  margin-bottom: 14px;
  padding: 10px 14px;
  border-radius: var(--r-sm);
  background: var(--bg-soft, #fbfaf8);
  border: 1px solid var(--line);
  font-size: 13px;
  color: var(--text-2);
}
.ck-shop + .ck-shop { border-top: 1px dashed var(--line); }
.ck-shop-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0 2px;
}
.ck-shop-name { font-weight: 600; font-size: 14px; }
.coupon-opt-scope {
  margin-left: auto;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--text-2);
  background: var(--surface-2, #f5f1ea);
  white-space: nowrap;
}
.coupon-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.6;
}
</style>
