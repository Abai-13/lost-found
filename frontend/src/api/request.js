import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { auth } from './auth'

// 创建一个 axios 实例，所有请求都以 /api 开头
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器：每次发请求前，自动把 token 塞进请求头
request.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// 响应拦截器：统一处理后端返回
request.interceptors.response.use(
  (response) => {
    const res = response.data // 后端统一返回 { code, message, data }
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(res)
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      // JWT 拦截器拒绝写操作时返回 HTTP 401
      ElMessage.error('未登录或登录已过期')
      auth.logout()
      router.push('/login')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
