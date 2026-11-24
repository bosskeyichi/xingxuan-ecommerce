<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Star, Switch, ShoppingCart } from '@element-plus/icons-vue'
import { compare } from '@/utils/compare'
import { coverUrl } from '@/utils/img'

const props = defineProps({
  product: { type: Object, required: true },
  index: { type: [Number, String], default: null },
  favIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['add', 'toggle-fav'])

const router = useRouter()

const isFav = computed(() => props.favIds.includes(props.product.id))
const isCompare = computed(() => compare.has(props.product.id))

// 库存状态：售罄遮罩 / 低库存提示（无库存字段时按「未同步」处理，不禁用加购）
const stock = computed(() => Number(props.product.stock ?? 0))
const soldOut = computed(() => props.product.stock != null && stock.value <= 0)
const lowStock = computed(() => props.product.stock != null && stock.value > 0 && stock.value <= 10)
// 包邮由店铺在商品编辑里维护，未设置时按包邮展示（与平台历史文案一致）
const freeShipping = computed(() => Number(props.product.freeShipping ?? 1) === 1)

const priceLabel = computed(() => {
  const v = Number(props.product.price || 0)
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
})

// 选品编号：用商品 id 生成稳定的「NO.007」，作为店铺的编号语言
const noLabel = computed(() => 'NO.' + String(props.product.id ?? 0).padStart(3, '0'))
// 一句话卖点：直接取商品描述，没有则留空不占位
const note = computed(() => (props.product.description || '').trim())

const goDetail = () => router.push(`/product/${props.product.id}`)
const goShop = e => {
  e.stopPropagation()
  if (props.product.shopId) router.push({ name: 'shop', params: { id: props.product.shopId } })
}
const onAdd = e => { e.stopPropagation(); emit('add', props.product) }
const onFav = e => { e.stopPropagation(); emit('toggle-fav', props.product) }
const onCompare = e => {
  e.stopPropagation()
  if (isCompare.value) compare.remove(props.product.id)
  else compare.add(props.product)
  window.dispatchEvent(new Event('compare-changed'))
}
</script>

<template>
  <article class="pcard" @click="goDetail">
    <div class="pcard-media">
      <img v-if="product.cover" :src="coverUrl(product.cover)" :alt="product.name" loading="lazy" />
      <div v-else class="img-ph">{{ (product.name || '?')[0] }}</div>
      <span class="pcard-no">{{ noLabel }}</span>

      <div class="pcard-tools">
        <button class="pcard-tool" :class="{ on: isFav }" :title="isFav ? '取消收藏' : '收藏'" @click="onFav">
          <el-icon :size="15"><Star /></el-icon>
        </button>
        <button class="pcard-tool" :class="{ on: isCompare }" :title="isCompare ? '移出对比' : '加入对比'" @click="onCompare">
          <el-icon :size="15"><Switch /></el-icon>
        </button>
      </div>

      <div v-if="soldOut" class="pcard-mask">已售罄</div>
    </div>

    <div class="pcard-body">
      <div class="pcard-tags">
        <span v-if="freeShipping" class="pcard-tag">包邮</span>
        <span v-if="lowStock" class="pcard-tag is-warn">仅剩 {{ stock }} 件</span>
      </div>
      <h3 class="pcard-name" :title="product.name">{{ product.name }}</h3>
      <p v-if="product.shopName" class="pcard-shop">
        <span class="pcard-shop-name" :title="`进入 ${product.shopName}`" @click="goShop">{{ product.shopName }}</span>
      </p>
      <p v-if="note" class="pcard-note" :title="note">{{ note }}</p>
      <div class="pcard-foot">
        <span class="pcard-price"><sup>¥</sup>{{ priceLabel }}</span>
        <button class="pcard-add" :disabled="soldOut" @click="onAdd">
          <el-icon :size="14"><ShoppingCart /></el-icon>
          {{ soldOut ? '售罄' : '加购' }}
        </button>
      </div>
      <div class="pcard-sales">
        已售 {{ product.sales || 0 }} 件
        <span v-if="product.stock != null" class="pcard-dot">·</span>
        <span v-if="product.stock != null">库存 {{ product.stock }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.pcard {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-md);
  overflow: hidden;
  cursor: pointer;
  transition: transform .22s cubic-bezier(.2, .7, .3, 1), box-shadow .22s ease, border-color .22s ease;
}
.pcard:hover {
  transform: translateY(-3px);
  box-shadow: var(--sh-md);
  border-color: #ffe0dd;
}
.pcard-media {
  position: relative;
  aspect-ratio: 1 / 1;
  background: var(--surface-2);
  overflow: hidden;
}
.pcard-media img {
  width: 100%; height: 100%; object-fit: cover;
  transition: transform .5s cubic-bezier(.2, .7, .3, 1);
}
.pcard:hover .pcard-media img { transform: scale(1.05); }

.pcard-tools {
  position: absolute;
  top: 10px; right: 10px;
  display: flex; flex-direction: column; gap: 8px;
}
.pcard-tool {
  width: 30px; height: 30px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, .06);
  background: rgba(255, 255, 255, .92);
  color: var(--ink-3);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  opacity: 0;
  transform: translateX(6px);
  transition: all .2s ease;
  backdrop-filter: blur(4px);
}
.pcard:hover .pcard-tool { opacity: 1; transform: none; }
.pcard-tool:hover { color: var(--brand); border-color: var(--brand); }
.pcard-tool.on { opacity: 1; transform: none; color: var(--brand); border-color: #ffd4d0; background: var(--brand-soft); }

.pcard-no {
  position: absolute;
  top: 10px; left: 10px;
  padding: 1px 7px;
  border-radius: 2px;
  background: rgba(255, 255, 255, .9);
  color: var(--gold);
  font-family: var(--font-num);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .1em;
  line-height: 16px;
  backdrop-filter: blur(4px);
}

.pcard-mask {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, .72);
  color: var(--ink-2);
  font-size: 13px; letter-spacing: .1em;
  backdrop-filter: blur(1px);
}

.pcard-body { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 8px; }
.pcard-tags { display: flex; gap: 6px; flex-wrap: wrap; min-height: 18px; }
.pcard-tag {
  padding: 0 6px;
  border-radius: var(--r-xs);
  border: 1px solid #ffd4d0;
  color: var(--brand);
  background: var(--brand-soft);
  font-size: 10.5px;
  line-height: 16px;
}
.pcard-tag.is-warn { color: var(--promo); border-color: #ffdfc7; background: var(--promo-soft); }
.pcard-name {
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.55;
  color: var(--ink);
  height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pcard-shop {
  margin: 2px 0 0;
  font-size: 11.5px;
  color: var(--ink-3);
  letter-spacing: .02em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pcard-shop-name {
  cursor: pointer;
  border-bottom: 1px dashed transparent;
  transition: color .2s, border-color .2s;
}
.pcard-shop-name:hover { color: var(--brand); border-bottom-color: var(--brand); }
.pcard-note {
  margin: 0;
  font-size: 11.5px;
  color: var(--ink-3);
  letter-spacing: .02em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pcard-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.pcard-price {
  font-family: var(--font-num);
  font-weight: 700;
  font-size: 19px;
  color: var(--price);
  letter-spacing: -.01em;
}
.pcard-price sup { font-size: 11px; font-weight: 600; margin-right: 1px; }
.pcard-add {
  display: inline-flex; align-items: center; gap: 4px;
  height: 28px; padding: 0 10px;
  border-radius: var(--r-full);
  border: 1px solid var(--brand);
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all .18s ease;
}
.pcard-add:hover { background: var(--brand-deep); border-color: var(--brand-deep); }
.pcard-add:disabled { background: var(--surface-3); border-color: var(--surface-3); color: var(--ink-3); cursor: not-allowed; }
.pcard-sales { font-size: 11px; color: var(--ink-4); display: flex; align-items: center; gap: 5px; }
.pcard-dot { color: var(--ink-4); }
</style>
