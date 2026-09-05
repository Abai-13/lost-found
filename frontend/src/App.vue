<script setup>
import { useRouter } from 'vue-router'
import { auth } from './api/auth'

const router = useRouter()

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app">
    <header class="nav">
      <router-link to="/items" class="logo">校园失物招领</router-link>
      <nav class="links">
        <router-link to="/items">物品列表</router-link>
        <router-link to="/publish">发布物品</router-link>
        <router-link to="/ai">AI 助手</router-link>
        <span v-if="auth.isLogin" class="nickname">{{ auth.nickname || '已登录' }}</span>
        <el-button v-if="!auth.isLogin" size="small" @click="router.push('/login')">登录 / 注册</el-button>
        <el-button v-else size="small" @click="logout">退出</el-button>
      </nav>
    </header>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, "Microsoft YaHei", sans-serif; background: #f5f7fa; }

.app .nav {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; height: 56px; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,.08);
}
.app .logo { font-size: 18px; font-weight: bold; color: #409eff; text-decoration: none; }
.app .links { display: flex; align-items: center; gap: 20px; }
.app .links a { color: #333; text-decoration: none; }
.app .links a.router-link-active { color: #409eff; font-weight: bold; }
.app .nickname { color: #888; font-size: 14px; }
.app .content { max-width: 960px; margin: 24px auto; padding: 0 16px; }
</style>
