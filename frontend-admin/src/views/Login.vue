<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import { setAuth } from '@/utils/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onSubmit = async () => {
  if (loading.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  loading.value = true
  try {
    const r = await authApi.login(form)
    if (!['ADMIN', 'MERCHANT'].includes(r.data.role)) {
      ElMessage.error('该账号没有后台权限，请先由超级管理员设为普通管理员')
      return
    }
    setAuth(r.data.token, r.data)
    const who = r.data.shopName ? `${r.data.shopName} · ${r.data.nickname || r.data.username}` : (r.data.nickname || r.data.username)
    ElMessage.success(`欢迎回来，${who}`)
    router.push('/dashboard')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="admin-auth-page">
    <aside class="admin-auth-aside">
      <div class="auth-mark">
        <div class="side-logo">星</div>
        <div>
          <div style="font-size:17px;font-weight:800;letter-spacing:-.02em">星选商城</div>
          <div style="font-size:10.5px;letter-spacing:.18em;text-transform:uppercase;color:rgba(255,255,255,.5)">Admin Console</div>
        </div>
      </div>

      <div>
        <h1 class="auth-hero-title">运营<br /><em>控制台</em></h1>
        <p class="auth-lede">
          超级管理员管全站，普通管理员（商家）只管自己的店铺：商品、订单与看板都按店铺隔离。
        </p>
        <div class="auth-feature"><i></i>商品：上架、下架、库存维护（仅本店）</div>
        <div class="auth-feature"><i></i>订单：发货、完成与 CSV 导出（仅本店）</div>
        <div class="auth-feature"><i></i>概览：店铺销售额、热销排行、库存预警</div>
      </div>

      <div class="auth-foot">© {{ new Date().getFullYear() }} 星选商城 · 买手严选</div>
    </aside>

    <main class="admin-auth-main">
      <div class="admin-auth-form">
        <h1 class="auth-title">后台登录</h1>

        <el-form ref="formRef" class="auth-form" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名 / 昵称" prop="username">
            <el-input v-model="form.username" size="large" :prefix-icon="User" placeholder="请输入用户名或昵称" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              :prefix-icon="Lock"
              show-password
              placeholder="请输入密码"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <button class="btn-grad auth-submit" :disabled="loading" @click="onSubmit">
            {{ loading ? '登录中…' : '进入后台' }}
          </button>
        </el-form>

      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-form { margin-top: 26px; }
.btn-grad {
  display: inline-flex; align-items: center; justify-content: center;
  width: 100%; height: 46px; border: none; border-radius: 10px;
  background: var(--brand-500);
  color: #fff; font-size: 15px; font-weight: 700; cursor: pointer;
  box-shadow: 0 6px 18px rgba(225, 37, 27, .22);
  transition: background .18s ease, box-shadow .18s ease;
}
.btn-grad:hover { background: var(--brand-600); box-shadow: 0 10px 26px rgba(225, 37, 27, .30); }
.btn-grad:disabled { opacity: .6; cursor: not-allowed; }
</style>
