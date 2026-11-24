<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reviewApi } from '@/api'
import { formatPrice } from '@/utils/price'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  order: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const reviews = ref([])
const loading = ref(false)
const saving = ref(null)
const forms = reactive({})

const items = computed(() => props.order?.items || [])
const byProduct = computed(() => {
  const map = {}
  reviews.value.forEach(r => { map[r.productId] = r })
  return map
})

const formOf = id => forms[id] || (forms[id] = { rating: 5, content: '', append: '', anonymous: false, mode: '' })
const stars = n => '★'.repeat(n || 0) + '☆'.repeat(5 - (n || 0))
const fmt = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''
const isEdited = r => r.updatedAt && r.createdAt && fmt(r.updatedAt) !== fmt(r.createdAt)

const reload = async () => {
  if (!props.order?.orderNo) return
  loading.value = true
  try {
    const r = await reviewApi.mineByOrder(props.order.orderNo)
    reviews.value = r.data || []
    items.value.forEach(i => {
      const f = formOf(i.productId)
      const rev = byProduct.value[i.productId]
      f.mode = ''
      f.append = ''
      f.rating = rev?.rating || 5
      f.content = rev?.content || ''
      f.anonymous = rev?.anonymous === true
    })
  } catch (e) {
    reviews.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, open => { if (open) reload() })

const startEdit = item => {
  const rev = byProduct.value[item.productId]
  const f = formOf(item.productId)
  f.mode = 'edit'
  f.rating = rev?.rating || 5
  f.content = rev?.content || ''
  f.anonymous = rev?.anonymous === true
}
const startAppend = item => {
  const f = formOf(item.productId)
  f.mode = 'append'
  f.append = ''
}
const cancelForm = item => { formOf(item.productId).mode = '' }

const submitAdd = async item => {
  const f = formOf(item.productId)
  if (!f.content.trim()) return ElMessage.warning('请先写下你的使用感受')
  saving.value = item.productId
  try {
    await reviewApi.add({
      productId: item.productId,
      orderNo: props.order.orderNo,
      rating: f.rating,
      content: f.content.trim(),
      anonymous: f.anonymous ? 1 : 0
    })
    ElMessage.success(f.anonymous ? '匿名评价已发布' : '评价已发布')
    await reload()
    emit('changed')
  } finally {
    saving.value = null
  }
}

const saveEdit = async item => {
  const rev = byProduct.value[item.productId]
  const f = formOf(item.productId)
  if (!f.content.trim()) return ElMessage.warning('评价内容不能为空')
  saving.value = item.productId
  try {
    await reviewApi.update(rev.id, {
      productId: item.productId,
      rating: f.rating,
      content: f.content.trim(),
      anonymous: f.anonymous ? 1 : 0
    })
    ElMessage.success('评价已更新')
    await reload()
    emit('changed')
  } finally {
    saving.value = null
  }
}

const submitAppend = async item => {
  const rev = byProduct.value[item.productId]
  const f = formOf(item.productId)
  if (!f.append.trim()) return ElMessage.warning('请输入追评内容')
  saving.value = item.productId
  try {
    await reviewApi.append(rev.id, f.append.trim())
    ElMessage.success('追评已发布')
    await reload()
    emit('changed')
  } finally {
    saving.value = null
  }
}

const removeReview = async item => {
  const rev = byProduct.value[item.productId]
  await ElMessageBox.confirm('删除后该评价将从商品页消失，确认删除？', '删除评价', { type: 'warning' })
  await reviewApi.remove(rev.id)
  ElMessage.success('评价已删除')
  await reload()
  emit('changed')
}
</script>

<template>
  <el-dialog v-model="visible" title="商品评价" width="700px" class="order-review-dialog" destroy-on-close>
    <div v-if="order" class="ord-review">
      <div class="ord-review-head">
        <span class="ord-no">{{ order.orderNo }}</span>
        <span class="ord-tip">确认收货后可评价，已评价支持追评 / 修改 / 删除</span>
      </div>

      <div v-loading="loading" class="ord-review-list">
        <div v-for="i in items" :key="i.id" class="ord-review-item">
          <div class="or-item-head">
            <div class="or-thumb">
              <img v-if="i.productCover" :src="i.productCover" :alt="i.productName" loading="lazy" />
              <span v-else>{{ (i.productName || '?')[0] }}</span>
            </div>
            <div class="or-item-info">
              <div class="or-item-name">{{ i.productName }}</div>
              <div class="or-item-meta">¥{{ formatPrice(i.price) }} × {{ i.quantity }}</div>
            </div>
            <span v-if="byProduct[i.productId]?.hidden" class="or-hidden">已被管理员屏蔽</span>
          </div>

          <!-- 已评价 -->
          <template v-if="byProduct[i.productId]">
            <div class="or-existing">
              <div class="or-stars">
                {{ stars(byProduct[i.productId].rating) }}
                <span class="or-time">评价于 {{ fmt(byProduct[i.productId].createdAt) }}</span>
                <span v-if="isEdited(byProduct[i.productId])" class="or-time">修改于 {{ fmt(byProduct[i.productId].updatedAt) }}</span>
              </div>
              <div class="or-content">{{ byProduct[i.productId].content || '（未填写评价内容）' }}</div>
              <div v-for="a in byProduct[i.productId].appends || []" :key="a.id" class="or-append">
                <span class="or-append-tag">追评</span>
                <span class="or-append-text">{{ a.content }}</span>
                <span class="or-time">{{ fmt(a.createdAt) }}</span>
              </div>
            </div>

            <div v-if="!formOf(i.productId).mode" class="or-actions">
              <el-button size="small" text type="primary" @click="startAppend(i)">追加评价</el-button>
              <el-button size="small" text type="primary" @click="startEdit(i)">修改评价</el-button>
              <el-button size="small" text class="or-del" @click="removeReview(i)">删除评价</el-button>
            </div>

            <div v-else class="or-form">
              <div v-if="formOf(i.productId).mode === 'append'" class="or-field">
                <el-input
                  v-model="formOf(i.productId).append"
                  type="textarea"
                  :rows="2"
                  maxlength="500"
                  show-word-limit
                  placeholder="追加使用体验，例如：用了一周后……"
                />
              </div>
              <template v-else>
                <div class="or-field or-rate-row">
                  <span class="text-sm text-2">评分</span>
                  <el-rate v-model="formOf(i.productId).rating" :colors="['#ff6a00', '#ff6a00', '#ff6a00']" />
                </div>
                <div class="or-field">
                  <el-input
                    v-model="formOf(i.productId).content"
                    type="textarea"
                    :rows="3"
                    maxlength="500"
                    show-word-limit
                    placeholder="修改你的评价内容"
                  />
                </div>
                <div class="or-field">
                  <el-checkbox v-model="formOf(i.productId).anonymous">
                    匿名评价（商品页显示为「匿名用户」）
                  </el-checkbox>
                </div>
              </template>
              <div class="or-form-btns">
                <el-button size="small" @click="cancelForm(i)">取消</el-button>
                <el-button
                  v-if="formOf(i.productId).mode === 'append'"
                  size="small"
                  type="primary"
                  :loading="saving === i.productId"
                  @click="submitAppend(i)"
                >发布追评</el-button>
                <el-button
                  v-else
                  size="small"
                  type="primary"
                  :loading="saving === i.productId"
                  @click="saveEdit(i)"
                >保存修改</el-button>
              </div>
            </div>
          </template>

          <!-- 未评价 -->
          <div v-else class="or-form">
            <div class="or-field or-rate-row">
              <span class="text-sm text-2">评分</span>
              <el-rate v-model="formOf(i.productId).rating" :colors="['#ff6a00', '#ff6a00', '#ff6a00']" />
            </div>
            <div class="or-field">
              <el-input
                v-model="formOf(i.productId).content"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="说说这件商品的使用感受吧"
              />
            </div>
            <div class="or-field">
              <el-checkbox v-model="formOf(i.productId).anonymous">
                匿名评价（商品页显示为「匿名用户」）
              </el-checkbox>
            </div>
            <div class="or-form-btns">
              <el-button
                size="small"
                type="primary"
                :loading="saving === i.productId"
                @click="submitAdd(i)"
              >发布评价</el-button>
            </div>
          </div>
        </div>

        <div v-if="!items.length" class="or-empty">该订单没有可评价的商品</div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ord-review-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.ord-no { font-family: var(--font-num); font-size: 12.5px; color: var(--ink-2); }
.ord-tip { font-size: 12px; color: var(--ink-3); }
.ord-review-list { max-height: 58vh; overflow-y: auto; padding-right: 4px; }
.ord-review-item { border: 1px solid var(--border); border-radius: var(--r-md); padding: 14px 16px; margin-bottom: 12px; }
.or-item-head { display: flex; align-items: center; gap: 12px; }
.or-thumb {
  width: 46px; height: 46px; border-radius: 8px; overflow: hidden; flex-shrink: 0;
  background: var(--surface-2); display: flex; align-items: center; justify-content: center;
  color: var(--ink-3); font-family: var(--font-display);
}
.or-thumb img { width: 100%; height: 100%; object-fit: cover; }
.or-item-info { min-width: 0; flex: 1; }
.or-item-name { font-weight: 600; font-size: 13.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.or-item-meta { font-size: 12px; color: var(--ink-3); margin-top: 2px; }
.or-hidden {
  font-size: 11.5px; color: var(--danger); border: 1px solid color-mix(in srgb, var(--danger) 35%, transparent);
  border-radius: 999px; padding: 2px 8px; white-space: nowrap;
}
.or-existing { margin-top: 12px; padding: 12px; background: var(--surface-2); border-radius: var(--r-sm); }
.or-stars { color: var(--promo); font-size: 12.5px; letter-spacing: .08em; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.or-time { color: var(--ink-4); font-size: 11.5px; letter-spacing: 0; }
.or-content { margin-top: 6px; font-size: 13.5px; line-height: 1.8; color: var(--ink-2); white-space: pre-wrap; word-break: break-word; }
.or-append { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--border-strong); font-size: 13px; color: var(--ink-2); line-height: 1.75; }
.or-append-tag {
  display: inline-block; margin-right: 6px; padding: 0 6px; border-radius: 4px;
  background: var(--brand-soft); color: var(--brand); font-size: 11px; line-height: 18px;
}
.or-append-text { white-space: pre-wrap; word-break: break-word; }
.or-actions { display: flex; justify-content: flex-end; gap: 4px; margin-top: 8px; }
.or-del { color: var(--danger) !important; }
.or-form { margin-top: 12px; }
.or-field { margin-bottom: 10px; }
.or-rate-row { display: flex; align-items: center; gap: 10px; }
.or-form-btns { display: flex; justify-content: flex-end; gap: 8px; }
.or-empty { text-align: center; color: var(--ink-3); font-size: 13px; padding: 24px 0; }
</style>
