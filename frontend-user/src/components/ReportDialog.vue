<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { reportApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** PRODUCT / SHOP */
  targetType: { type: String, default: 'PRODUCT' },
  targetId: { type: [Number, String], default: null },
  targetTitle: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const TYPES = ['假冒伪劣', '虚假宣传', '违规内容', '侵权', '其他']
const form = reactive({ type: '', content: '' })
const submitting = ref(false)

const typeText = computed(() => (props.targetType === 'SHOP' ? '店铺' : '商品'))

const submit = async () => {
  if (!form.type) return ElMessage.warning('请选择举报类型')
  if (!form.content.trim()) return ElMessage.warning('请填写举报说明')
  if (!props.targetId) return ElMessage.warning('举报对象不存在')
  submitting.value = true
  try {
    await reportApi.submit({
      targetType: props.targetType,
      targetId: Number(props.targetId),
      type: form.type,
      content: form.content.trim()
    })
    ElMessage.success('举报已提交，处理结果会通过系统消息通知你')
    form.type = ''
    form.content = ''
    emit('update:modelValue', false)
  } catch (e) { /* 统一提示 */ } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`举报${typeText}`"
    width="520px"
    @update:model-value="v => emit('update:modelValue', v)"
  >
    <div v-if="targetTitle" class="report-target">{{ targetTitle }}</div>
    <el-form label-position="top">
      <el-form-item label="举报类型" required>
        <el-radio-group v-model="form.type" class="report-types">
          <el-radio v-for="t in TYPES" :key="t" :value="t" border>{{ t }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="举报说明" required>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="请描述具体问题，例如虚假宣传、货不对板、侵权内容等"
        />
      </el-form-item>
    </el-form>
    <p class="report-tip">
      举报会同时送达该{{ typeText }}所属店铺的管理员与平台管理员，处理结果将以系统消息回馈给你。
    </p>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="danger" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.report-target {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  font-size: 13px;
  color: var(--text-2);
}
.report-types { display: flex; flex-wrap: wrap; gap: 8px; }
.report-types :deep(.el-radio) { margin-right: 0; }
.report-tip { font-size: 12px; color: var(--text-3); line-height: 1.6; }
</style>
