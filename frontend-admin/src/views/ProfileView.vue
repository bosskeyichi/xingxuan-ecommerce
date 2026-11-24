<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'
import { getToken, getUser, setAuth, clearAuth } from '@/utils/auth'

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const pwdLoading = ref(false)
const user = ref(null)

const form = reactive({ nickname: '', email: '', phone: '', avatar: '' })
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const roleText = computed(() => (user.value?.role === 'ADMIN' ? '超级管理员' : '普通管理员（商家）'))
const initial = computed(() => (form.nickname || user.value?.username || '管')[0].toUpperCase())

const profileRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 20, message: '昵称 1-20 个字符', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^(1[3-9]\d{9})?$/, message: '手机号格式不正确', trigger: 'blur' }]
}
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (r, v, cb) => (v === pwd.newPassword ? cb() : cb(new Error('两次输入的密码不一致'))), trigger: 'blur' }
  ]
}

const profileRef = ref()
const pwdRef = ref()

const load = async () => {
  loading.value = true
  try {
    const r = await authApi.me()
    user.value = r.data
    form.nickname = r.data.nickname || r.data.username || ''
    form.email = r.data.email || ''
    form.phone = r.data.phone || ''
    form.avatar = r.data.avatar || ''
  } finally {
    loading.value = false
  }
}

const onAvatarChange = e => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!/^image\//.test(file.type)) { ElMessage.warning('请选择图片文件'); return }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片不要超过 5MB'); return }
  const reader = new FileReader()
  reader.onload = ev => {
    const img = new Image()
    img.onload = () => {
      const size = 200
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext('2d')
      const s = Math.min(img.width, img.height)
      ctx.drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, size, size)
      form.avatar = canvas.toDataURL('image/jpeg', 0.86)
    }
    img.src = ev.target.result
  }
  reader.readAsDataURL(file)
}

const onSaveProfile = async () => {
  const ok = await profileRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const r = await authApi.updateProfile({ ...form })
    user.value = r.data
    // /auth/me 返回的是基础资料，合并登录信息以免丢掉 shopName / shopId
    setAuth(getToken(), { ...(getUser() || {}), ...r.data, scope: 'ADMIN' })
    window.dispatchEvent(new CustomEvent('admin:profile-updated'))
    ElMessage.success('资料已更新')
  } finally { saving.value = false }
}

const onChangePassword = async () => {
  const ok = await pwdRef.value.validate().catch(() => false)
  if (!ok) return
  pwdLoading.value = true
  try {
    await authApi.changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    clearAuth()
    router.push('/login')
  } finally { pwdLoading.value = false }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-desc">后台账号资料与登录密码 · 与商城用户端账号互通，但登录会话相互独立</p>
      </div>
    </div>

    <div class="profile-grid">
      <div class="card">
        <h3 class="profile-card-title">账号资料</h3>
        <div class="profile-avatar-row">
          <div class="profile-avatar">
            <img v-if="form.avatar" :src="form.avatar" alt="avatar" />
            <span v-else>{{ initial }}</span>
          </div>
          <div>
            <div class="profile-username">@{{ user?.username }}</div>
            <div class="profile-role">{{ roleText }}</div>
            <div class="profile-actions">
              <label class="profile-upload">
                更换头像
                <input type="file" accept="image/*" @change="onAvatarChange" />
              </label>
              <el-button v-if="form.avatar" text type="danger" size="small" @click="form.avatar = ''">移除</el-button>
            </div>
            <div class="text-xs text-muted">建议 200×200 以上方图，上传后自动居中裁剪压缩</div>
          </div>
        </div>

        <el-form ref="profileRef" :model="form" :rules="profileRules" label-position="top">
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" maxlength="20" placeholder="显示在后台顶栏的名字" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="用于接收通知（可留空）" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="11 位手机号（可留空）" />
          </el-form-item>
          <el-button type="primary" :loading="saving" @click="onSaveProfile">保存资料</el-button>
        </el-form>
      </div>

      <div class="card">
        <h3 class="profile-card-title">修改密码</h3>
        <p class="text-xs text-muted" style="margin-bottom:14px">
          修改成功后需要重新登录；如忘记密码，可由超级管理员在「用户管理」里重置数据恢复为 123456。
        </p>
        <el-form ref="pwdRef" :model="pwd" :rules="pwdRules" label-position="top">
          <el-form-item label="当前密码" prop="oldPassword">
            <el-input v-model="pwd.oldPassword" type="password" show-password placeholder="请输入当前登录密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="pwd.newPassword" type="password" show-password placeholder="6-32 位" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm">
            <el-input v-model="pwd.confirm" type="password" show-password placeholder="再次输入新密码" />
          </el-form-item>
          <el-button type="primary" :loading="pwdLoading" @click="onChangePassword">修改密码</el-button>
        </el-form>

        <div class="profile-tip">
          <b>权限说明</b>
          <ul>
            <li>超级管理员：全平台数据、店铺开店 / 关店、用户与分类治理、平台券发放。</li>
            <li>普通管理员（商家）：只管理本店商品、订单发货、本店优惠券与看板。</li>
          </ul>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:18px">
      <h3 class="profile-card-title">互动与设置</h3>
      <p class="text-xs text-muted" style="margin-bottom:14px">
        管理员之间可以互加好友并私聊；隐私与消息开关与商城用户端共用同一份配置。
      </p>
      <div class="quick-grid">
        <button class="quick-item" @click="router.push('/friends')">
          <b>好友</b>
          <span>搜索管理员、加好友、发起私聊</span>
        </button>
        <button class="quick-item" @click="router.push('/messages')">
          <b>消息</b>
          <span>管理员私聊、买家客服咨询、系统通知</span>
        </button>
        <button class="quick-item" @click="router.push('/settings')">
          <b>隐私与消息设置</b>
          <span>是否可被搜索、谁能加好友、消息接收开关</span>
        </button>
        <button class="quick-item" @click="router.push('/reports')">
          <b>举报管理</b>
          <span>{{ roleText }}可查看并处理所辖范围的举报</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}
@media (max-width: 980px) { .profile-grid { grid-template-columns: 1fr; } }
.card { padding: 22px 22px 18px; }
.profile-card-title {
  margin: 0 0 16px;
  font-family: var(--font-display, serif);
  font-size: 17px;
  letter-spacing: .03em;
}
.profile-avatar-row { display: flex; gap: 16px; margin-bottom: 20px; }
.profile-avatar {
  width: 76px;
  height: 76px;
  flex: none;
  border-radius: 20px;
  overflow: hidden;
  display: grid;
  place-items: center;
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  background: var(--brand);
}
.profile-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-username { font-weight: 600; font-size: 15px; }
.profile-role { font-size: 12.5px; color: var(--text-3); margin: 3px 0 8px; }
.profile-actions { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.profile-upload {
  display: inline-block;
  padding: 5px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color .2s, color .2s;
}
.profile-upload:hover { border-color: var(--brand); color: var(--brand); }
.profile-upload input { display: none; }
.profile-tip {
  margin-top: 20px;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--surface-2, #f6f2ec);
  font-size: 12.5px;
  color: var(--text-2);
  line-height: 1.8;
}
.profile-tip ul { margin: 6px 0 0; padding-left: 18px; }
.quick-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 12px; }
.quick-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--surface);
  text-align: left;
  cursor: pointer;
  transition: border-color .2s, transform .2s;
}
.quick-item:hover { border-color: var(--brand, #c8202a); transform: translateY(-1px); }
.quick-item b { font-size: 13.5px; color: var(--text-1); }
.quick-item span { font-size: 12px; color: var(--text-3); line-height: 1.6; }
</style>
