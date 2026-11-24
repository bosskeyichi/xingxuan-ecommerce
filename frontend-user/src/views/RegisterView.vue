<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Message, Postcard, ArrowLeft } from '@element-plus/icons-vue'
import { authApi } from '@/api'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', nickname: '', email: '', password: '', confirm: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6-32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (rule, value, cb) => value === form.password ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await authApi.register({
      username: form.username,
      nickname: form.nickname,
      email: form.email,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="auth-page">
    <aside class="auth-aside">
      <router-link to="/" class="back-home">
        <el-icon :size="14"><ArrowLeft /></el-icon>
        <span>返回商城</span>
      </router-link>

      <div class="auth-brand">
        <div class="brand-mark">星</div>
        <div>
          <div class="auth-brand-name">星选商城</div>
          <div class="auth-brand-sub">Curated Store</div>
        </div>
      </div>

      <div>
        <h1 class="auth-hero-title">
          加入<em>星选</em><br />开启好物之旅
        </h1>
        <p class="auth-hero-desc">
          注册仅需几秒钟，立即解锁专属购物车、收藏夹与优惠券中心。
        </p>
        <div class="auth-feature"><i></i>新人注册即领优惠券</div>
        <div class="auth-feature"><i></i>地址一键保存，结算少填五次</div>
        <div class="auth-feature"><i></i>收藏与足迹自动同步</div>
      </div>

      <div class="auth-foot">© {{ new Date().getFullYear() }} 星选商城</div>
    </aside>

    <main class="auth-main">
      <div class="auth-form">
        <h1 class="auth-title">创建账号</h1>
        <p class="auth-sub">几秒钟即可完成注册</p>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" :prefix-icon="User" placeholder="3-20 位字母或数字" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.nickname" size="large" :prefix-icon="Postcard" placeholder="选填，默认与用户名相同" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" size="large" :prefix-icon="Message" placeholder="you@example.com" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" size="large" :prefix-icon="Lock" show-password placeholder="6-32 位" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirm">
            <el-input v-model="form.confirm" type="password" size="large" :prefix-icon="Lock" show-password placeholder="再次输入密码" />
          </el-form-item>
          <button class="btn-grad auth-submit" :disabled="loading" @click="onSubmit">
            {{ loading ? '注册中…' : '注 册' }}
          </button>
        </el-form>

        <div class="auth-switch">
          已有账号？<a @click="$router.push('/login')">立即登录 →</a>
        </div>

        <router-link to="/" class="back-home is-light">
          <el-icon :size="13"><ArrowLeft /></el-icon>
          <span>返回商城首页</span>
        </router-link>
      </div>
    </main>
  </div>
</template>
