import { reactive } from 'vue'

// 一个全局共享的登录状态。
// 登录后把 token / nickname / userId 存这里 + localStorage，刷新页面也不丢。
export const auth = reactive({
  token: localStorage.getItem('token') || '',
  nickname: localStorage.getItem('nickname') || '',
  userId: localStorage.getItem('userId') || '',

  get isLogin() {
    return !!this.token
  },

  setLogin(token, nickname, userId) {
    this.token = token
    this.nickname = nickname || ''
    this.userId = userId || ''
    localStorage.setItem('token', token)
    localStorage.setItem('nickname', this.nickname)
    localStorage.setItem('userId', this.userId)
  },

  logout() {
    this.token = ''
    this.nickname = ''
    this.userId = ''
    localStorage.removeItem('token')
    localStorage.removeItem('nickname')
    localStorage.removeItem('userId')
  },
})
