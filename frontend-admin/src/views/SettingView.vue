<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Bell } from '@element-plus/icons-vue'
import { settingApi } from '@/api'
import { getUser } from '@/utils/auth'

const isPlatform = computed(() => getUser()?.role === 'ADMIN')

const loading = ref(true)
const saving = ref(false)

const form = reactive({
  searchable: true,
  friendPolicy: 'ALL',
  showEmail: false,
  showPhone: false,
  receiveFriendMsg: true,
  receiveServiceMsg: true,
  receiveSystemMsg: true,
  notifySound: true
})

const load = async () => {
  loading.value = true
  try {
    const r = await settingApi.get()
    const d = r.data || {}
    form.searchable = d.searchable !== 0
    form.friendPolicy = d.friendPolicy || 'ALL'
    form.showEmail = d.showEmail === 1
    form.showPhone = d.showPhone === 1
    form.receiveFriendMsg = d.receiveFriendMsg !== 0
    form.receiveServiceMsg = d.receiveServiceMsg !== 0
    form.receiveSystemMsg = d.receiveSystemMsg !== 0
    form.notifySound = d.notifySound !== 0
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    await settingApi.save({
      searchable: form.searchable ? 1 : 0,
      friendPolicy: form.friendPolicy,
      showEmail: form.showEmail ? 1 : 0,
      showPhone: form.showPhone ? 1 : 0,
      receiveFriendMsg: form.receiveFriendMsg ? 1 : 0,
      receiveServiceMsg: form.receiveServiceMsg ? 1 : 0,
      receiveSystemMsg: form.receiveSystemMsg ? 1 : 0,
      notifySound: form.notifySound ? 1 : 0
    })
    ElMessage.success('设置已保存，立即生效')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <h1 class="page-title">设置</h1>
      <p class="page-desc">隐私与消息偏好 · 与用户端共用同一份个人设置，保存后两端同时生效。</p>
    </div>

    <div class="setting-grid">
      <div class="card">
        <h3 class="card-title"><el-icon><Lock /></el-icon> 隐私设置</h3>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">允许被搜索</div>
            <div class="setting-desc">
              关闭后，其他管理员在「好友」页按昵称 / 用户名 / ID 都搜不到你，已有好友关系不受影响。
            </div>
          </div>
          <el-switch v-model="form.searchable" />
        </div>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">谁可以加我为好友</div>
            <div class="setting-desc">
              选择「任何人不允许」后，别人无法再向你发起好友申请，你仍可以主动加别人。
            </div>
          </div>
          <el-select v-model="form.friendPolicy" style="width:150px">
            <el-option label="所有人可加" value="ALL" />
            <el-option label="不允许添加" value="NOBODY" />
          </el-select>
        </div>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">公开邮箱</div>
            <div class="setting-desc">开启后，好友搜索结果里会展示你的邮箱，便于运营联络。</div>
          </div>
          <el-switch v-model="form.showEmail" />
        </div>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">公开手机号</div>
            <div class="setting-desc">开启后，好友搜索结果里会展示你的手机号，请谨慎开启。</div>
          </div>
          <el-switch v-model="form.showPhone" />
        </div>

        <div v-if="isPlatform" class="setting-tip">
          超级管理员默认拥有全部商家的好友关系，因此「允许被搜索」只影响普通管理员之间的互加。
        </div>
      </div>

      <div class="card">
        <h3 class="card-title"><el-icon><Bell /></el-icon> 消息设置</h3>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">接收管理员私聊</div>
            <div class="setting-desc">关闭后，其他商家无法再给你发送好友消息（已有记录仍可查看）；超级管理员代表平台官方，其消息不受此开关限制。</div>
          </div>
          <el-switch v-model="form.receiveFriendMsg" />
        </div>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">接收买家客服咨询</div>
            <div class="setting-desc">关闭后，买家在商品页点「联系客服」将无法把消息送达你的店铺。</div>
          </div>
          <el-switch v-model="form.receiveServiceMsg" />
        </div>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">接收系统通知</div>
            <div class="setting-desc">
              包括新举报提醒、举报处理结果等。关闭后将收不到这些关键提醒，建议保持开启。
            </div>
          </div>
          <el-switch v-model="form.receiveSystemMsg" />
        </div>

        <div class="setting-item">
          <div class="setting-text">
            <div class="setting-label">消息提示音</div>
            <div class="setting-desc">有新消息时在浏览器内播放提示音（需页面处于前台）。</div>
          </div>
          <el-switch v-model="form.notifySound" />
        </div>

        <div class="setting-tip">
          关闭「接收系统通知」后，举报提醒与处理结果将不再推送，请确认后再关闭。
        </div>
      </div>
    </div>

    <div class="save-bar">
      <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      <el-button text @click="load">恢复为已保存的设置</el-button>
    </div>
  </div>
</template>

<style scoped>
.setting-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
.card-title { display: flex; align-items: center; gap: 8px; }
.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 0;
  border-bottom: 1px dashed var(--border);
}
.setting-item:last-of-type { border-bottom: 0; }
.setting-label { font-size: 13.5px; font-weight: 600; color: var(--text-1); }
.setting-desc { margin-top: 4px; font-size: 12.5px; color: var(--text-3); line-height: 1.6; }
.setting-tip {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  font-size: 12.5px;
  color: var(--text-2);
  line-height: 1.7;
}
.save-bar { display: flex; gap: 10px; margin-top: 18px; }
@media (max-width: 1100px) { .setting-grid { grid-template-columns: 1fr; } }
</style>
