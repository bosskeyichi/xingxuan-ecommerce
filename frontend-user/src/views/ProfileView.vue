<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Lock, Location, EditPen, UserFilled, ChatDotRound, ChatLineSquare, Warning, Setting } from '@element-plus/icons-vue'
import { authApi, orderApi, favoriteApi, addressApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const user = ref(null)
const tab = ref(route.query.tab || 'profile')
watch(() => route.query.tab, t => { if (t) tab.value = t })
const saving = ref(false)
const orderCount = ref(0)
const favCount = ref(0)

const profileForm = reactive({ nickname: '', email: '', phone: '', avatar: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const pwdRef = ref()

// ---------- 收货地址簿 ----------
const addresses = ref([])
const addrVisible = ref(false)
const addrSaving = ref(false)
const editingId = ref(null)
const addrRef = ref()
const addrForm = reactive({ receiverName: '', receiverPhone: '', region: '', detail: '', isDefault: false })
const addrRules = {
  receiverName: [{ required: true, message: '请输入收货人姓名', trigger: 'blur' }],
  receiverPhone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  region: [{ required: true, message: '请输入所在地区', trigger: 'blur' }],
  detail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }]
}

const loadAddresses = async () => {
  try {
    const r = await addressApi.list()
    addresses.value = r.data || []
  } catch (e) { addresses.value = [] }
}

const openAdd = () => {
  editingId.value = null
  Object.assign(addrForm, { receiverName: '', receiverPhone: '', region: '', detail: '', isDefault: false })
  addrVisible.value = true
}

const openEdit = a => {
  editingId.value = a.id
  Object.assign(addrForm, {
    receiverName: a.receiverName,
    receiverPhone: a.receiverPhone,
    region: a.region,
    detail: a.detail,
    isDefault: a.isDefault === 1
  })
  addrVisible.value = true
}

const saveAddr = async () => {
  await addrRef.value.validate()
  addrSaving.value = true
  try {
    const payload = { ...addrForm, isDefault: addrForm.isDefault ? 1 : 0 }
    if (editingId.value) await addressApi.update(editingId.value, payload)
    else await addressApi.add(payload)
    ElMessage.success('地址已保存')
    addrVisible.value = false
    await loadAddresses()
  } finally { addrSaving.value = false }
}

const delAddr = async id => {
  await ElMessageBox.confirm('确定删除该收货地址吗？', '删除地址', { type: 'warning' })
  await addressApi.remove(id)
  ElMessage.success('已删除')
  await loadAddresses()
}

const setDefault = async id => {
  await addressApi.setDefault(id)
  ElMessage.success('已设为默认地址')
  await loadAddresses()
}

const initial = computed(() => {
  const n = user.value?.nickname || user.value?.username || 'U'
  return n[0].toUpperCase()
})

// ---------- 头像自定义 ----------
const avatarPreview = computed(() => profileForm.avatar)

const onAvatarChange = e => {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) { ElMessage.warning('请选择图片文件'); return }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片不能超过 5MB'); return }
  const url = URL.createObjectURL(file)
  const img = new Image()
  img.onload = () => {
    // 居中裁剪为正方形，再压缩到 200×200，控制存储体积
    const side = Math.min(img.width, img.height)
    const sx = (img.width - side) / 2
    const sy = (img.height - side) / 2
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 200
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#F7F5F1'
    ctx.fillRect(0, 0, 200, 200)
    ctx.drawImage(img, sx, sy, side, side, 0, 0, 200, 200)
    profileForm.avatar = canvas.toDataURL('image/jpeg', 0.86)
    URL.revokeObjectURL(url)
  }
  img.onerror = () => { URL.revokeObjectURL(url); ElMessage.error('图片解析失败，请换一张') }
  img.src = url
}

const resetAvatar = () => { profileForm.avatar = '' }

const profileRules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^(1\d{10})?$/, message: '手机号格式不正确', trigger: 'blur' }]
}
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: (r, v, cb) => v === pwdForm.newPassword ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' }
  ]
}

const loadUser = async () => {
  const r = await authApi.me()
  user.value = r.data
  profileForm.nickname = r.data.nickname || ''
  profileForm.email = r.data.email || ''
  profileForm.phone = r.data.phone || ''
  profileForm.avatar = r.data.avatar || ''
}

const loadStats = async () => {
  try {
    const o = await orderApi.list({ current: 1, size: 1 })
    orderCount.value = o.data.total
  } catch (e) { orderCount.value = 0 }
  try {
    const f = await favoriteApi.ids()
    favCount.value = (f.data || []).length
  } catch (e) { favCount.value = 0 }
}

const saveProfile = async () => {
  saving.value = true
  try {
    const r = await authApi.updateProfile({
      nickname: profileForm.nickname,
      email: profileForm.email,
      phone: profileForm.phone,
      avatar: profileForm.avatar || ''
    })
    ElMessage.success('资料已更新')
    user.value = r.data
    profileForm.avatar = r.data.avatar || ''
    userStore.userInfo = {
      ...userStore.userInfo,
      nickname: r.data.nickname,
      avatar: r.data.avatar || ''
    }
    sessionStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
  } finally { saving.value = false }
}

const changePwd = async () => {
  await pwdRef.value.validate()
  saving.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    userStore.logout()
    router.push('/login')
  } finally { saving.value = false }
}

onMounted(async () => { await loadUser(); await loadStats(); await loadAddresses() })
</script>

<template>
  <div v-if="user">
    <div class="section-head">
      <div>
        <h2 class="section-title">个人中心</h2>
        <div class="section-sub">管理你的资料、密码与偏好设置</div>
      </div>
    </div>

    <div class="profile-layout">
      <!-- 左侧名片 -->
      <div>
        <div class="profile-card">
          <div class="profile-cover" />
          <div class="profile-body">
            <img v-if="user.avatar" :src="user.avatar" class="profile-avatar-img" alt="头像" />
            <div v-else class="profile-avatar-lg">{{ initial }}</div>
            <div class="profile-name">{{ user.nickname || user.username }}</div>
            <div class="profile-handle">@{{ user.username }}</div>
            <div style="margin-top:12px">
              <span class="pill" :class="user.role === 'ADMIN' ? 'brand' : (user.role === 'MERCHANT' ? 'paid' : 'completed')">
                {{ user.role === 'ADMIN' ? '超级管理员' : (user.role === 'MERCHANT' ? '商家' : '普通用户') }}
              </span>
            </div>
            <div style="display:flex;gap:24px;justify-content:center;margin-top:20px">
              <div>
                <div class="price price-md" style="color:var(--text-1)">{{ orderCount }}</div>
                <div class="text-xs text-muted">订单</div>
              </div>
              <div>
                <div class="price price-md" style="color:var(--text-1)">{{ favCount }}</div>
                <div class="text-xs text-muted">收藏</div>
              </div>
              <div>
                <div class="price price-md" style="color:var(--text-1)">{{ String(user.id).padStart(3, '0') }}</div>
                <div class="text-xs text-muted">ID</div>
              </div>
            </div>
          </div>
        </div>

        <div class="profile-side-menu">
          <button class="side-menu-item" :class="{ 'is-active': tab === 'profile' }" @click="tab = 'profile'">
            <el-icon><User /></el-icon>资料概览
          </button>
          <button class="side-menu-item" :class="{ 'is-active': tab === 'edit' }" @click="tab = 'edit'">
            <el-icon><EditPen /></el-icon>编辑资料
          </button>
          <button class="side-menu-item" :class="{ 'is-active': tab === 'pwd' }" @click="tab = 'pwd'">
            <el-icon><Lock /></el-icon>修改密码
          </button>
          <button class="side-menu-item" :class="{ 'is-active': tab === 'address' }" @click="tab = 'address'">
            <el-icon><Location /></el-icon>收货地址
          </button>
          <div class="side-menu-group">互动与设置</div>
          <button class="side-menu-item" @click="router.push('/friends')">
            <el-icon><UserFilled /></el-icon>我的好友
          </button>
          <button class="side-menu-item" @click="router.push('/messages')">
            <el-icon><ChatDotRound /></el-icon>消息中心
          </button>
          <button class="side-menu-item" @click="router.push('/my-reviews')">
            <el-icon><ChatLineSquare /></el-icon>我的评价
          </button>
          <button class="side-menu-item" @click="router.push('/my-reports')">
            <el-icon><Warning /></el-icon>我的举报
          </button>
          <button class="side-menu-item" @click="router.push('/settings')">
            <el-icon><Setting /></el-icon>设置
          </button>
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="panel">
        <div v-show="tab === 'profile'">
          <h3 style="font-size:17px;margin-bottom:20px">账户信息</h3>
          <div class="kv-grid">
            <div><span class="kv-key">用户 ID</span><span class="kv-val font-num">{{ user.id }}</span></div>
            <div><span class="kv-key">用户名</span><span class="kv-val">{{ user.username }}</span></div>
            <div><span class="kv-key">昵称</span><span class="kv-val">{{ user.nickname || '—' }}</span></div>
            <div><span class="kv-key">角色</span><span class="kv-val">{{ user.role === 'ADMIN' ? '超级管理员' : (user.role === 'MERCHANT' ? '商家' : '普通用户') }}</span></div>
            <div><span class="kv-key">邮箱</span><span class="kv-val">{{ user.email || '—' }}</span></div>
            <div><span class="kv-key">手机号</span><span class="kv-val">{{ user.phone || '—' }}</span></div>
            <div>
              <span class="kv-key">账户状态</span>
              <span class="kv-val">
                <span class="pill" :class="user.status === 1 ? 'completed' : 'cancelled'">
                  {{ user.status === 1 ? '正常' : '已禁用' }}
                </span>
              </span>
            </div>
            <div><span class="kv-key">注册时间</span><span class="kv-val">{{ String(user.createdAt || '').replace('T', ' ').slice(0, 19) || '—' }}</span></div>
          </div>
        </div>

        <div v-show="tab === 'edit'">
          <h3 style="font-size:17px;margin-bottom:20px">编辑资料</h3>

          <div class="avatar-edit">
            <div class="avatar-edit-preview">
              <img v-if="avatarPreview" :src="avatarPreview" alt="头像预览" />
              <span v-else>{{ initial }}</span>
            </div>
            <div class="avatar-edit-side">
              <div class="avatar-edit-title">头像</div>
              <div class="avatar-edit-actions">
                <label class="btn-outline">
                  上传图片
                  <input type="file" accept="image/*" hidden @change="onAvatarChange" />
                </label>
                <button type="button" class="link-btn" @click="resetAvatar">恢复默认</button>
              </div>
              <p class="avatar-edit-hint">支持 JPG / PNG，自动居中裁剪为方形并压缩至 200×200，单个文件 ≤ 5MB。</p>
            </div>
          </div>

          <el-form :model="profileForm" :rules="profileRules" label-position="top" style="max-width:520px">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="展示在订单与评价中的名字" maxlength="50" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="you@example.com" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" placeholder="11 位手机号" maxlength="11" />
            </el-form-item>
            <button class="btn-grad" :disabled="saving" @click="saveProfile">保存资料</button>
          </el-form>
        </div>

        <div v-show="tab === 'pwd'">
          <h3 style="font-size:17px;margin-bottom:8px">修改密码</h3>
          <p class="text-sm text-muted mb-24">修改成功后需要重新登录，请妥善保管新密码。</p>
          <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-position="top" style="max-width:420px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirm">
              <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入新密码" />
            </el-form-item>
            <button class="btn-grad" :disabled="saving" @click="changePwd">确认修改</button>
          </el-form>
        </div>

        <div v-show="tab === 'address'">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px">
            <h3 style="font-size:17px;margin:0">收货地址</h3>
            <button class="btn-grad" style="height:38px;padding:0 18px;font-size:13px;letter-spacing:.08em" @click="openAdd">新增地址</button>
          </div>

          <div v-if="!addresses.length" class="empty" style="padding:48px 0">
            <div class="empty-icon">址</div>
            <div class="empty-title">还没有收货地址</div>
            <p class="empty-sub">添加地址后，下单结算时可直接选用。</p>
          </div>

          <div v-else class="addr-grid">
            <div v-for="a in addresses" :key="a.id" class="addr-card">
              <div class="addr-head">
                <b>{{ a.receiverName }}</b>
                <span class="font-num text-sm text-muted">{{ a.receiverPhone }}</span>
                <span v-if="a.isDefault === 1" class="pill brand" style="margin-left:auto">默认</span>
              </div>
              <div class="addr-region">{{ a.region }}</div>
              <div class="addr-detail">{{ a.detail }}</div>
              <div class="addr-actions">
                <a v-if="a.isDefault !== 1" @click="setDefault(a.id)">设为默认</a>
                <a @click="openEdit(a)">编辑</a>
                <a class="is-danger" @click="delAddr(a.id)">删除</a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑地址 -->
    <el-dialog v-model="addrVisible" :title="editingId ? '编辑地址' : '新增地址'" width="460px">
      <el-form ref="addrRef" :model="addrForm" :rules="addrRules" label-position="top">
        <el-form-item label="收货人" prop="receiverName">
          <el-input v-model="addrForm.receiverName" placeholder="姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="receiverPhone">
          <el-input v-model="addrForm.receiverPhone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="所在地区" prop="region">
          <el-input v-model="addrForm.region" placeholder="省 市 区（如：北京市 朝阳区 望京街道）" maxlength="100" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="addrForm.detail" type="textarea" :rows="2" placeholder="街道、楼牌号等" maxlength="200" />
        </el-form-item>
        <el-checkbox v-model="addrForm.isDefault">设为默认地址</el-checkbox>
      </el-form>
      <template #footer>
        <el-button @click="addrVisible = false">取消</el-button>
        <el-button type="primary" :disabled="addrSaving" @click="saveAddr">
          {{ addrSaving ? '保存中…' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
