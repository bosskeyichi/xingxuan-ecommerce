<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Bell } from '@element-plus/icons-vue'
import { settingApi } from '@/api'

const loading = ref(false)
const saving = ref(false)

const form = reactive({
  searchable: 1,
  friendPolicy: 'ALL',
  showEmail: 0,
  showPhone: 0,
  showReviews: 1,
  receiveFriendMsg: 1,
  receiveServiceMsg: 1,
  receiveSystemMsg: 1,
  notifySound: 1
})

const load = async () => {
  loading.value = true
  try {
    const r = await settingApi.get()
    if (r.data) {
      Object.keys(form).forEach(k => {
        if (r.data[k] !== undefined && r.data[k] !== null) form[k] = r.data[k]
      })
    }
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    await settingApi.save({ ...form })
    ElMessage.success('设置已保存')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page settings-page">
    <div class="page-head">
      <h1 class="page-title">设置</h1>
      <p class="page-desc">隐私与消息偏好会立即生效。</p>
    </div>

    <div v-loading="loading" class="settings-grid">
      <div class="card">
        <div class="card-head">
          <div class="card-title"><el-icon><Lock /></el-icon> 隐私设置</div>
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">允许被搜索</div>
            <div class="setting-tip">关闭后，其他用户无法通过昵称、用户名或 ID 找到你</div>
          </div>
          <el-switch v-model="form.searchable" :active-value="1" :inactive-value="0" />
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">谁可以加我为好友</div>
            <div class="setting-tip">选择「不允许」后，别人无法向你发送好友请求</div>
          </div>
          <el-radio-group v-model="form.friendPolicy">
            <el-radio value="ALL">所有人</el-radio>
            <el-radio value="NOBODY">不允许</el-radio>
          </el-radio-group>
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">公开我的评价</div>
            <div class="setting-tip">关闭后，你写的评价不会出现在商品页</div>
          </div>
          <el-switch v-model="form.showReviews" :active-value="1" :inactive-value="0" />
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">公开邮箱</div>
            <div class="setting-tip">好友卡片与搜索结果中展示你的邮箱</div>
          </div>
          <el-switch v-model="form.showEmail" :active-value="1" :inactive-value="0" />
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">公开手机号</div>
            <div class="setting-tip">好友卡片与搜索结果中展示你的手机号</div>
          </div>
          <el-switch v-model="form.showPhone" :active-value="1" :inactive-value="0" />
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div class="card-title"><el-icon><Bell /></el-icon> 消息设置</div>
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">接收好友消息</div>
            <div class="setting-tip">关闭后，好友无法向你发送消息；平台官方（超级管理员）的消息不受此开关限制</div>
          </div>
          <el-switch v-model="form.receiveFriendMsg" :active-value="1" :inactive-value="0" />
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">接收客服消息</div>
            <div class="setting-tip">关闭后，店铺客服的回复不会送达</div>
          </div>
          <el-switch v-model="form.receiveServiceMsg" :active-value="1" :inactive-value="0" />
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">接收系统通知</div>
            <div class="setting-tip">举报处理结果等通知</div>
          </div>
          <el-switch v-model="form.receiveSystemMsg" :active-value="1" :inactive-value="0" />
        </div>

        <div class="setting-row">
          <div class="setting-text">
            <div class="setting-name">新消息提示音</div>
            <div class="setting-tip">有未读消息时在顶栏提示</div>
          </div>
          <el-switch v-model="form.notifySound" :active-value="1" :inactive-value="0" />
        </div>
      </div>
    </div>

    <div class="settings-actions">
      <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
    </div>
  </div>
</template>

<style scoped>
.settings-page { max-width: 980px; margin: 0 auto; }
.settings-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px dashed var(--border);
}
.setting-row:last-child { border-bottom: 0; }
.setting-text { min-width: 0; }
.setting-name { font-size: 14px; font-weight: 600; color: var(--text-1); }
.setting-tip { margin-top: 3px; font-size: 12px; color: var(--text-3); line-height: 1.5; }
.settings-actions { margin-top: 16px; display: flex; justify-content: flex-end; }

@media (max-width: 820px) {
  .settings-grid { grid-template-columns: 1fr; }
}
</style>
