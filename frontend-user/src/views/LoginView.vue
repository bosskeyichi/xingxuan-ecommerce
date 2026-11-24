<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, ArrowLeft } from '@element-plus/icons-vue'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const r = await authApi.login(form)
    userStore.setLogin(r.data)
    ElMessage.success(`欢迎回来，${r.data.nickname || r.data.username}`)
    // redirect 只接受站内绝对路径，防止 //evil.com 这类协议相对地址
    const redirect = route.query.redirect
    const safe = typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
    router.push(safe ? redirect : '/')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <aside class="auth-aside">
      <div class="auth-brand">
        <div class="brand-mark">星</div>
        <div>
          <div class="auth-brand-name">星选商城</div>
          <div class="auth-brand-sub">Curated Store</div>
        </div>
      </div>

      <div>
        <h1 class="auth-hero-title">
          好物<br />值得<em>被看见</em>
        </h1>
        <p class="auth-hero-desc">
          登录后可同步购物车、收藏与收货地址，随时查看订单进度，购物不中断。
        </p>
        <div class="auth-feature"><i></i>精选好物，一次只推 100 件</div>
        <div class="auth-feature"><i></i>七天无理由退换，下单更放心</div>
        <div class="auth-feature"><i></i>订单全程可追踪，进度实时同步</div>
      </div>

      <div class="auth-foot">© {{ new Date().getFullYear() }} 星选商城</div>
    </aside>

    <!-- 右侧表单 -->
    <main class="auth-main">
      <div class="auth-form">
        <h1 class="auth-title">欢迎回来</h1>
        <p class="auth-sub">登录后继续你的购物之旅</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名 / 昵称" prop="username">
            <el-input
              v-model="form.username"
              size="large"
              :prefix-icon="User"
              placeholder="请输入用户名或昵称（管理员／商家账号也可直接登录）"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              :prefix-icon="Lock"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
          <button class="btn-grad auth-submit" :disabled="loading" @click="onSubmit">
            {{ loading ? '登录中…' : '登 录' }}
          </button>
        </el-form>

        <div class="auth-switch">
          还没有账号？<a @click="$router.push('/register')">立即注册 →</a>
        </div>

        <router-link to="/" class="back-home is-light">
          <el-icon :size="13"><ArrowLeft /></el-icon>
          <span>返回商城首页</span>
        </router-link>
      </div>
    </main>
  </div>
</template>
