<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { Odometer, User, Goods, Wallet, Tickets, Warning, TrendCharts } from '@element-plus/icons-vue'
import { statsApi, shopApi } from '@/api'
import { getUser } from '@/utils/auth'

/** 平台管理员的「全平台」选项值；选具体店铺时用店铺 id */
const ALL = 'ALL'

const stats = ref({ ordersCount: {} })
const trend = ref([])
const topProducts = ref([])
const lowStock = ref([])
const loading = ref(false)

// 平台管理员可按店铺下钻；商家只有本店数据，不显示筛选与排行
const isPlatform = getUser()?.role === 'ADMIN'
const shops = ref([])
const pickedShopId = ref(ALL)
const pickedShop = computed(() => shops.value.find(s => s.id === pickedShopId.value) || null)
const isScoped = computed(() => stats.value.scope === 'SHOP')

const scopeTag = computed(() => {
  if (!isPlatform) return '本店数据'
  return pickedShopId.value === ALL ? '全平台数据' : '单店铺数据'
})
const scopeDesc = computed(() => {
  if (!isPlatform) return '仅统计当前店铺的商品、订单与销售额'
  return pickedShopId.value === ALL
    ? '用户、商品、订单与销售的关键指标总览'
    : `当前查看：${pickedShop.value?.name || `店铺 #${pickedShopId.value}`}`
})

const userLabel = computed(() => {
  if (!isScoped.value) return '注册用户'
  return isPlatform ? '店铺成交用户' : '本店成交用户'
})
const userFoot = computed(() => (isScoped.value ? '在该店铺下过单的用户数' : '累计注册账号数'))

const maxAmount = computed(() => Math.max(...trend.value.map(t => Number(t.amount) || 0), 1))
const maxSales = computed(() => Math.max(...topProducts.value.map(p => Number(p.sales) || 0), 1))
const trendTotal = computed(() => trend.value.reduce((s, t) => s + Number(t.amount || 0), 0))

// 店铺经营排行：按累计销售额降序，点击行即可把上方数据范围切到该店铺
const shopRank = computed(() => [...shops.value]
  .sort((a, b) => Number(b.salesAmount || 0) - Number(a.salesAmount || 0)))
const platformSales = computed(() => shopRank.value.reduce((s, x) => s + Number(x.salesAmount || 0), 0))
const maxShopSales = computed(() => Math.max(...shopRank.value.map(s => Number(s.salesAmount || 0)), 1))
const sharePct = row => {
  if (!platformSales.value) return '0%'
  return ((Number(row.salesAmount || 0) / platformSales.value) * 100).toFixed(1) + '%'
}
const barWidth = row => Math.max(3, (Number(row.salesAmount || 0) / maxShopSales.value) * 100) + '%'

const fmt = n => Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const dayLabel = d => String(d).slice(5)

const load = async () => {
  loading.value = true
  const params = pickedShopId.value === ALL ? {} : { shopId: pickedShopId.value }
  try {
    const [o, t, tp, ls] = await Promise.all([
      statsApi.overview(params),
      statsApi.trend(7, params),
      statsApi.topProducts(6, params),
      statsApi.lowStock(10, params)
    ])
    stats.value = o.data || {}
    trend.value = t.data || []
    topProducts.value = tp.data || []
    lowStock.value = ls.data || []
  } finally { loading.value = false }
}

const loadShops = async () => {
  if (!isPlatform) return
  try {
    const r = await shopApi.adminList()
    shops.value = r.data || []
  } catch (e) { shops.value = [] }
}

const pickShop = row => { pickedShopId.value = row.id }
const resetScope = () => { pickedShopId.value = ALL }

watch(pickedShopId, load)
onMounted(() => {
  loadShops()
  load()
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-head dash-head">
      <div>
        <h1 class="page-title">
          运营概览
          <el-tag
            size="small"
            :type="isScoped ? 'warning' : 'info'"
            effect="light"
            style="margin-left:10px;vertical-align:middle"
          >
            {{ scopeTag }}
          </el-tag>
        </h1>
        <p class="page-desc">
          {{ scopeDesc }}（数据实时刷新）
          <span class="desc-sep">·</span>
          优惠券 {{ stats.couponCount || 0 }} 张（在架 {{ stats.couponAvailable || 0 }} · 已核销 {{ stats.couponUsed || 0 }}）
        </p>
      </div>
      <el-select
        v-if="isPlatform"
        v-model="pickedShopId"
        class="dash-scope"
        style="width:220px"
        placeholder="选择数据范围"
      >
        <el-option :value="ALL" label="全平台（全部店铺）" />
        <el-option v-for="s in shops" :key="s.id" :value="s.id" :label="s.name" />
      </el-select>
    </div>

    <!-- 关键指标 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-icon i-brand"><el-icon><User /></el-icon></div>
        <div class="stat-label">{{ userLabel }}</div>
        <div class="stat-value">{{ stats.userCount || 0 }}</div>
        <div class="stat-foot">{{ userFoot }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon i-cool"><el-icon><Goods /></el-icon></div>
        <div class="stat-label">在售商品</div>
        <div class="stat-value">{{ stats.productCount || 0 }}</div>
        <div class="stat-foot">共 {{ stats.categoryCount || 0 }} 个分类</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon i-success"><el-icon><Tickets /></el-icon></div>
        <div class="stat-label">订单总数</div>
        <div class="stat-value">{{ stats.orderCount || 0 }}</div>
        <div class="stat-foot">{{ isScoped ? '该范围内全部状态订单' : '含所有状态的订单' }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon i-warm"><el-icon><Wallet /></el-icon></div>
        <div class="stat-label">今日销售额</div>
        <div class="stat-value" style="color:var(--price)"><span class="cur">¥</span>{{ fmt(stats.todaySales) }}</div>
        <div class="stat-foot">累计销售额 ¥{{ fmt(stats.totalSales) }}</div>
      </div>
    </div>

    <!-- 趋势 + 排行 -->
    <div style="display:grid;grid-template-columns:1.6fr 1fr;gap:18px;margin-top:18px" class="dash-split">
      <div class="card">
        <div class="card-head">
          <div>
            <div class="card-title">近 7 天销售趋势</div>
            <div class="card-sub">按支付时间统计 · 合计 ¥{{ fmt(trendTotal) }}</div>
          </div>
          <el-tag type="primary" effect="light">GMV</el-tag>
        </div>
        <div v-if="!trend.length" class="empty">
          <div class="empty-icon"><el-icon><TrendCharts /></el-icon></div>
          <div class="empty-title">暂无销售数据</div>
          <p class="empty-sub">完成一笔订单支付后即可看到趋势。</p>
        </div>
        <div v-else class="mini-chart">
          <div v-for="t in trend" :key="t.date" class="mini-col">
            <span class="mini-val">{{ t.count }}</span>
            <div class="mini-bar-wrap">
              <div
                class="mini-bar"
                :style="{ height: Math.max(4, (Number(t.amount) / maxAmount) * 100) + '%' }"
                :title="`${t.date}：¥${fmt(t.amount)}`"
              />
            </div>
            <span class="mini-label">{{ dayLabel(t.date) }}</span>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div class="card-title">热销商品 TOP 6</div>
        </div>
        <div v-if="!topProducts.length" class="empty" style="padding:36px">
          <div class="empty-title">暂无数据</div>
        </div>
        <div v-else class="rank-list">
          <div v-for="(p, i) in topProducts" :key="p.id" class="rank-item">
            <span class="rank-no">{{ i + 1 }}</span>
            <span class="rank-name" :title="p.name">{{ p.name }}</span>
            <span class="rank-track">
              <span class="rank-fill" :style="{ width: (Number(p.sales) / maxSales * 100) + '%' }" />
            </span>
            <span class="rank-val">{{ p.sales }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 订单状态 + 库存预警 -->
    <div style="display:grid;grid-template-columns:1.6fr 1fr;gap:18px;margin-top:18px" class="dash-split">
      <div class="card">
        <div class="card-head">
          <div class="card-title">订单状态分布</div>
        </div>
        <div class="dash-status-grid">
          <div v-for="s in [
            { k: 'PENDING_PAYMENT', l: '待支付', c: 'pending' },
            { k: 'PAID', l: '已支付', c: 'paid' },
            { k: 'SHIPPED', l: '已发货', c: 'shipped' },
            { k: 'COMPLETED', l: '已完成', c: 'completed' },
            { k: 'CANCELLED', l: '已取消', c: 'cancelled' }
          ]" :key="s.k" class="dash-status" @click="$router.push({ path: '/orders', query: { status: s.k } })">
            <span class="pill" :class="s.c">{{ s.l }}</span>
            <div class="stat-value" style="font-size:26px;margin-top:8px">{{ stats.ordersCount?.[s.k] || 0 }}</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div class="card-title">库存预警</div>
          <el-tag v-if="lowStock.length" type="danger" effect="light">{{ lowStock.length }} 项</el-tag>
        </div>
        <div v-if="!lowStock.length" class="empty" style="padding:36px">
          <div class="empty-icon" style="background:var(--success-bg);color:var(--success)">
            <el-icon><Odometer /></el-icon>
          </div>
          <div class="empty-title">库存充足</div>
          <p class="empty-sub">暂无低于 10 件的商品。</p>
        </div>
        <div v-else class="rank-list">
          <div v-for="p in lowStock" :key="p.id" class="rank-item">
            <span class="rank-no" style="background:var(--danger-bg);color:var(--danger)">
              <el-icon><Warning /></el-icon>
            </span>
            <span class="rank-name" :title="p.name">{{ p.name }}</span>
            <span v-if="isPlatform && pickedShopId === ALL && p.shopName" class="rank-shop">{{ p.shopName }}</span>
            <span class="pill danger">剩 {{ p.stock }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 店铺经营排行：平台管理员专属，按店铺拆开看经营数据 -->
    <div v-if="isPlatform" class="card" style="margin-top:18px">
      <div class="card-head">
        <div>
          <div class="card-title">店铺经营排行</div>
          <div class="card-sub">按累计销售额排序 · 点击某一行可把上方数据范围切到该店铺</div>
        </div>
        <div style="display:flex;align-items:center;gap:8px">
          <el-tag type="info" effect="light">{{ shops.length }} 家店铺</el-tag>
          <el-button v-if="pickedShopId !== ALL" text @click="resetScope">返回全平台</el-button>
        </div>
      </div>
      <el-table
        v-if="shopRank.length"
        :data="shopRank"
        class="shop-rank-table"
        style="width:100%"
        @row-click="pickShop"
      >
        <el-table-column type="index" label="#" width="52" />
        <el-table-column label="店铺" min-width="180">
          <template #default="{ row }">
            <div class="sr-shop">
              <span class="sr-name">{{ row.name }}</span>
              <span v-if="row.ownerUsername" class="sr-owner">@{{ row.ownerUsername }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'" effect="light">
              {{ row.status === 1 ? '营业中' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" width="90" align="right" />
        <el-table-column prop="orderCount" label="订单数" width="90" align="right" />
        <el-table-column label="累计销售额" width="130" align="right">
          <template #default="{ row }">
            <span class="sr-sales">¥{{ fmt(row.salesAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="占全平台" width="150">
          <template #default="{ row }">
            <div class="sr-share">
              <span class="sr-bar"><span class="sr-bar-fill" :style="{ width: barWidth(row) }" /></span>
              <span class="sr-pct">{{ sharePct(row) }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <div v-else class="empty" style="padding:36px">
        <div class="empty-title">暂无店铺数据</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.dash-scope {
  flex: none;
}

.shop-rank-table :deep(.el-table__row) {
  cursor: pointer;
}
.sr-shop {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.sr-name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sr-owner {
  font-size: 12px;
  color: var(--text-3);
  flex: none;
}
.sr-sales {
  font-weight: 700;
  color: var(--price);
}
.sr-share {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sr-bar {
  flex: 1;
  height: 6px;
  border-radius: 3px;
  background: rgba(0, 0, 0, .07);
  overflow: hidden;
}
.sr-bar-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--brand, #d13b2e);
  transition: width .3s ease;
}
.sr-pct {
  font-size: 12px;
  color: var(--text-3);
  width: 44px;
  text-align: right;
  flex: none;
}
.rank-shop {
  font-size: 12px;
  color: var(--text-3);
  margin-right: 8px;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .dash-split { grid-template-columns: 1fr !important; }
}
</style>
