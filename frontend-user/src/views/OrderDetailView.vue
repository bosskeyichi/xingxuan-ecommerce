<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api'
import { formatPrice, moneyMinus } from '@/utils/price'
import OrderReviewDialog from '@/components/OrderReviewDialog.vue'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(true)
const reviewVisible = ref(false)

const statusLabel = s => ({
  PENDING_PAYMENT: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成', CANCELLED: '已取消'
}[s] || s)
const statusPill = s => ({
  PENDING_PAYMENT: 'pending', PAID: 'paid', SHIPPED: 'shipped', COMPLETED: 'completed', CANCELLED: 'cancelled'
}[s] || '')
const format = t => t ? String(t).replace('T', ' ').slice(0, 19) : '—'

const STEPS = ['PENDING_PAYMENT', 'PAID', 'SHIPPED', 'COMPLETED']
const stepIndex = computed(() => order.value ? STEPS.indexOf(order.value.status) : -1)

const load = async () => {
  loading.value = true
  try {
    const r = await orderApi.detail(route.params.orderNo)
    order.value = r.data
  } catch (e) {
    order.value = null
  } finally {
    loading.value = false
  }
}

const onPay = async () => {
  await ElMessageBox.confirm('确认支付？（模拟支付，不会产生真实交易）', '模拟支付', { type: 'warning' })
  await orderApi.pay(order.value.orderNo)
  ElMessage.success('支付成功')
  load()
}

const onCancel = async () => {
  await ElMessageBox.confirm('确认取消该订单？库存将自动回退。', '取消订单', { type: 'warning' })
  await orderApi.cancel(order.value.orderNo)
  ElMessage.success('订单已取消')
  load()
}

const onConfirm = async () => {
  await ElMessageBox.confirm('确认已收到货物？', '确认收货', { type: 'warning' })
  await orderApi.confirm(order.value.orderNo)
  ElMessage.success('已确认收货，现在可以评价商品了')
  await load()
  reviewVisible.value = true
}

const onDelete = async () => {
  await ElMessageBox.confirm('确认删除该历史订单？删除后不可恢复。', '删除订单', { type: 'warning' })
  await orderApi.remove(order.value.orderNo)
  ElMessage.success('订单已删除')
  router.push('/orders')
}

const copyNo = async (no) => {
  try {
    await navigator.clipboard.writeText(no)
    ElMessage.success('快递单号已复制')
  } catch (e) {
    ElMessage.warning('复制失败，请手动复制：' + no)
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
  <div v-if="order">
    <div class="section-head">
      <div>
        <h2 class="section-title">订单详情</h2>
        <div class="section-sub font-num">{{ order.orderNo }}</div>
      </div>
      <el-button text @click="$router.push('/orders')">← 返回列表</el-button>
    </div>

    <!-- 状态 + 进度 -->
    <div class="panel mb-24">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:20px">
        <div style="display:flex;align-items:center;gap:12px">
          <span class="pill" :class="statusPill(order.status)">{{ statusLabel(order.status) }}</span>
          <span class="text-sm text-muted">
            {{ order.status === 'PENDING_PAYMENT' ? '请尽快完成支付，超时订单将自动取消' : '订单状态实时更新' }}
          </span>
        </div>
        <div style="display:flex;align-items:baseline;gap:8px">
          <span class="text-sm text-muted">订单金额</span>
          <span v-if="order.discountAmount" class="text-sm text-muted" style="text-decoration:line-through">¥{{ formatPrice(order.totalAmount) }}</span>
          <span class="price price-lg"><span class="cur">¥</span>{{ moneyMinus(order.totalAmount, order.discountAmount) }}</span>
        </div>
      </div>

      <div v-if="order.status !== 'CANCELLED'" class="step-bar">
        <div
          v-for="(s, i) in ['提交订单', '完成支付', '已发货', '交易完成']"
          :key="s"
          class="step-item"
          :class="{ 'is-done': stepIndex >= i }"
        >
          <span class="step-line" />
          <span class="step-dot">{{ i + 1 }}</span>
          <span class="step-label">{{ s }}</span>
        </div>
      </div>
      <div v-else class="text-sm text-muted" style="text-align:center;padding:12px 0">
        该订单已取消，库存已自动回退
      </div>
    </div>

    <div class="panel mb-24">
      <h3 style="font-size:16px;margin-bottom:18px">收货信息</h3>
      <div class="kv-grid">
        <div><span class="kv-key">订单号</span><span class="kv-val font-num">{{ order.orderNo }}</span></div>
        <div>
          <span class="kv-key">店铺</span>
          <span class="kv-val">
            <router-link v-if="order.shopId" class="shop-link" :to="{ name: 'shop', params: { id: order.shopId } }">
              {{ order.shopName }}
            </router-link>
            <template v-else>—</template>
          </span>
        </div>
        <div><span class="kv-key">下单时间</span><span class="kv-val">{{ format(order.createdAt) }}</span></div>
        <div><span class="kv-key">收货人</span><span class="kv-val">{{ order.receiverName }}</span></div>
        <div><span class="kv-key">联系电话</span><span class="kv-val font-num">{{ order.receiverPhone }}</span></div>
        <div class="kv-full"><span class="kv-key">收货地址</span><span class="kv-val">{{ order.receiverAddr }}</span></div>
        <div v-if="order.remark" class="kv-full">
          <span class="kv-key">订单备注</span><span class="kv-val text-2">{{ order.remark }}</span>
        </div>
        <div><span class="kv-key">支付时间</span><span class="kv-val">{{ format(order.paidAt) }}</span></div>
        <div><span class="kv-key">发货时间</span><span class="kv-val">{{ format(order.shippedAt) }}</span></div>
        <div v-if="order.status === 'SHIPPED' || order.status === 'COMPLETED'">
          <span class="kv-key">物流信息</span>
          <span class="kv-val font-num">
            {{ (order.trackingCompany ? order.trackingCompany + ' · ' : '') + (order.trackingNo || '暂未填写单号') }}
            <span v-if="order.trackingNo" class="copy-btn" @click="copyNo(order.trackingNo)">复制单号</span>
          </span>
        </div>
      </div>
    </div>

    <div class="panel">
      <h3 style="font-size:16px;margin-bottom:8px">商品明细</h3>
      <div v-for="i in order.items" :key="i.id" class="cart-row" style="padding:14px 0">
        <div class="cart-thumb" style="width:64px;height:64px">
          <img v-if="i.productCover" :src="i.productCover" :alt="i.productName" loading="lazy" />
          <div v-else class="img-ph">{{ (i.productName || '?')[0] }}</div>
        </div>
        <div class="cart-info">
          <div class="cart-name" @click="$router.push(`/product/${i.productId}`)">{{ i.productName }}</div>
          <div class="cart-meta">¥{{ formatPrice(i.price) }} × {{ i.quantity }}</div>
        </div>
        <div class="cart-sub price"><span class="cur">¥</span>{{ formatPrice(i.subtotal) }}</div>
      </div>

      <div style="display:flex;justify-content:flex-end;align-items:baseline;gap:10px;padding-top:16px;border-top:1px dashed var(--border-strong);margin-top:8px">
        <span class="text-sm text-2">商品金额</span>
        <span class="price price-md"><span class="cur">¥</span>{{ formatPrice(order.totalAmount) }}</span>
        <template v-if="order.discountAmount">
          <span class="text-sm text-2">优惠</span>
          <span class="price price-md" style="color:var(--danger)"><span class="cur">¥</span>-{{ formatPrice(order.discountAmount) }}</span>
        </template>
        <span class="text-sm text-2">实付</span>
        <span class="price price-lg"><span class="cur">¥</span>{{ moneyMinus(order.totalAmount, order.discountAmount) }}</span>
      </div>

      <div v-if="order.status === 'PENDING_PAYMENT' || order.status === 'SHIPPED'" style="display:flex;justify-content:flex-end;gap:12px;margin-top:20px;flex-wrap:wrap">
        <el-button v-if="order.status === 'PENDING_PAYMENT'" @click="onCancel">取消订单</el-button>
        <el-button v-if="order.status === 'PENDING_PAYMENT'" type="primary" size="large" @click="onPay">模拟支付</el-button>
        <el-button v-if="order.status === 'SHIPPED'" type="success" size="large" @click="onConfirm">确认收货</el-button>
      </div>

      <div v-if="order.status === 'COMPLETED' || order.status === 'CANCELLED'" style="display:flex;justify-content:flex-end;gap:12px;margin-top:20px;flex-wrap:wrap">
        <el-button v-if="order.status === 'COMPLETED'" type="primary" plain @click="reviewVisible = true">评价商品</el-button>
        <el-button class="order-del-btn" @click="onDelete">删除订单</el-button>
      </div>
    </div>
  </div>

  <div v-else-if="!loading" class="empty">
    <div class="empty-icon">单</div>
    <div class="empty-title">订单不存在或无权查看</div>
    <p class="empty-sub">该订单可能已被删除，请返回列表重新选择。</p>
    <el-button class="mt-16" type="primary" @click="$router.push('/orders')">返回订单列表</el-button>
  </div>

  <OrderReviewDialog v-model="reviewVisible" :order="order" />
  </div>
</template>
