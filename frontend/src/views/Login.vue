<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../api/auth'

const router = useRouter()

// 当前在哪个 tab：'login' 或 'register'
const activeTab = ref('login')
const loading = ref(false)

// 表单数据（用 ref 包裹，输入框 v-model 双向绑定）
const form = ref({
  username: '',
  password: '',
  nickname: '',
  phone: '',
})

const HIGHLIGHTS = [
  { icon: 'Search', text: '全站招领信息一网打尽' },
  { icon: 'MagicStick', text: '描述一句话，AI 帮你找' },
  { icon: 'Bell', text: '发布后失主能直接联系你' },
]

async function login() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    // 1. 调后端登录接口
    const res = await request.post('/user/login', {
      username: form.value.username,
      password: form.value.password,
    })
    // 2. 拿到 token 和用户信息，存起来
    auth.setLogin(res.data.token, res.data.nickname, res.data.userId)
    ElMessage.success('登录成功')
    // 3. 跳到物品列表页
    router.push('/items')
  } finally {
    loading.value = false
  }
}

async function register() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await request.post('/user/register', {
      username: form.value.username,
      password: form.value.password,
      nickname: form.value.nickname,
      phone: form.value.phone,
    })
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="lf-panel box">
      <!-- 左：品牌区。窄屏会隐藏，只留表单 -->
      <aside class="brand">
        <div class="brand-logo"><el-icon><Search /></el-icon></div>
        <h2 class="brand-title">校园失物招领</h2>
        <p class="brand-sub">让每一件丢失的东西都能回家</p>

        <ul class="brand-list">
          <li v-for="h in HIGHLIGHTS" :key="h.text">
            <el-icon><component :is="h.icon" /></el-icon>{{ h.text }}
          </li>
        </ul>
      </aside>

      <!-- 右：表单 -->
      <section class="form-side">
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名">
                <el-input
                  v-model="form.username"
                  size="large"
                  placeholder="请输入用户名"
                  @keyup.enter="login"
                >
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-form-item label="密码">
                <el-input
                  v-model="form.password"
                  size="large"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                  @keyup.enter="login"
                >
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-button
                type="primary"
                size="large"
                style="width: 100%"
                :loading="loading"
                @click="login"
              >
                登录
              </el-button>
            </el-form>

            <p class="switch-hint">
              还没有账号？<a @click="activeTab = 'register'">立即注册</a>
            </p>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="用户名">
                <el-input v-model="form.username" size="large" placeholder="3-20 位">
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-form-item label="密码">
                <el-input
                  v-model="form.password"
                  size="large"
                  type="password"
                  placeholder="6-30 位"
                  show-password
                >
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-form-item label="昵称（选填）">
                <el-input v-model="form.nickname" size="large" placeholder="别人看到的名字">
                  <template #prefix><el-icon><Avatar /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-form-item label="手机号（选填）">
                <el-input v-model="form.phone" size="large" placeholder="方便失主联系你">
                  <template #prefix><el-icon><Iphone /></el-icon></template>
                </el-input>
              </el-form-item>

              <el-button
                type="primary"
                size="large"
                style="width: 100%"
                :loading="loading"
                @click="register"
              >
                注册
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  padding: 48px 16px;
}

.box {
  display: grid;
  grid-template-columns: 320px minmax(0, 380px);
  width: 100%;
  max-width: 700px;
  overflow: hidden;
}

/* ── 左：品牌区 ───────────────────────────────────────────── */
.brand {
  padding: 40px 28px;
  color: #fff;
  background: linear-gradient(150deg, #4f46e5 0%, #7c74ee 55%, #8b7bf0 100%);
  display: flex;
  flex-direction: column;
}

.brand-logo {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  font-size: 22px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.brand-title {
  margin-top: 22px;
  font-size: 21px;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.brand-sub {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  opacity: 0.85;
}

.brand-list {
  list-style: none;
  margin-top: auto;
  padding-top: 32px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.brand-list li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  opacity: 0.92;
}

/* ── 右：表单 ─────────────────────────────────────────────── */
.form-side {
  padding: 32px 32px 24px;
  background: var(--lf-surface);
}

.switch-hint {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--lf-text-muted);
}

.switch-hint a {
  color: var(--el-color-primary);
  cursor: pointer;
  font-weight: 500;
}

/* 窄屏：隐藏品牌区，表单占满 */
@media (max-width: 680px) {
  .box {
    grid-template-columns: 1fr;
    max-width: 400px;
  }

  .brand {
    display: none;
  }

  .form-side {
    padding: 24px;
  }
}
</style>
