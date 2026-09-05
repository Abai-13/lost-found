<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { auth } from '../api/auth'

const router = useRouter()

// 当前在哪个 tab：'login' 或 'register'
const activeTab = ref('login')

// 表单数据（用 ref 包裹，输入框 v-model 双向绑定）
const form = ref({
  username: '',
  password: '',
  nickname: '',
  phone: '',
})

async function login() {
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
}

async function register() {
  await request.post('/user/register', {
    username: form.value.username,
    password: form.value.password,
    nickname: form.value.nickname,
    phone: form.value.phone,
  })
  ElMessage.success('注册成功，请登录')
  activeTab.value = 'login'
}
</script>

<template>
  <div class="login-box">
    <el-card class="card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="登录" name="login">
          <el-form label-width="60px">
            <el-form-item label="用户名">
              <el-input v-model="form.username" placeholder="请输入用户名" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
            <el-button type="primary" style="width:100%" @click="login">登录</el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form label-width="60px">
            <el-form-item label="用户名">
              <el-input v-model="form.username" placeholder="3-20 位" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" placeholder="6-30 位" show-password />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="form.nickname" placeholder="选填" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="选填" />
            </el-form-item>
            <el-button type="primary" style="width:100%" @click="register">注册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.login-box { display: flex; justify-content: center; margin-top: 60px; }
.card { width: 400px; }
</style>
