<script setup>
import { reactive, ref } from 'vue'
import { addressApi } from '@/api'

const props = defineProps({ address: { type: Object, default: null } })
const emit = defineEmits(['saved', 'cancel'])

const formRef = ref()
const saving = ref(false)

const form = reactive({
  receiverName: '',
  receiverPhone: '',
  region: '',
  detail: '',
  isDefault: false
})

const rules = {
  receiverName: [{ required: true, message: '请输入收货人姓名', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  region: [{ required: true, message: '请输入所在地区', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

const onSubmit = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form, isDefault: form.isDefault ? 1 : 0 }
    if (props.address?.id) await addressApi.update(props.address.id, payload)
    else await addressApi.add(payload)
    emit('saved')
  } finally { saving.value = false }
}
</script>

<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
    <el-form-item label="收货人" prop="receiverName">
      <el-input v-model="form.receiverName" placeholder="姓名" maxlength="50" />
    </el-form-item>
    <el-form-item label="手机号" prop="receiverPhone">
      <el-input v-model="form.receiverPhone" placeholder="11 位手机号" maxlength="11" />
    </el-form-item>
    <el-form-item label="所在地区" prop="region">
      <el-input v-model="form.region" placeholder="省 市 区（如：北京市 朝阳区 望京街道）" maxlength="100" />
    </el-form-item>
    <el-form-item label="详细地址" prop="detail">
      <el-input v-model="form.detail" type="textarea" :rows="2" placeholder="街道、楼牌号等" maxlength="200" />
    </el-form-item>
    <el-checkbox v-model="form.isDefault">设为默认地址</el-checkbox>
  </el-form>
  <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:10px">
    <el-button @click="emit('cancel')">取消</el-button>
    <el-button type="primary" :disabled="saving" @click="onSubmit">
      {{ saving ? '保存中…' : '保存' }}
    </el-button>
  </div>
</template>
