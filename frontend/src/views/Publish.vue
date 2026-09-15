<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const router = useRouter()

// 表单字段（跟后端 ItemCreateRequest 对应）
const form = ref({
  title: '',
  type: 'LOST',
  category: '',
  location: '',
  description: '',
  contact: '',
})

const categories = ['电子产品', '证件', '衣物', '书籍', '其他']

const submitting = ref(false)

// 用户选的图片文件（还没上传，等点「发布」时一起发）
const imageFile = ref(null)
// 本地预览地址（blob URL），只是给用户看，不发给后端
const previewUrl = ref('')

// el-upload 选中文件时触发，file.raw 才是真正的 File 对象
function onFileChange(file) {
  imageFile.value = file.raw
  // 选新图前先释放上一个 blob，否则反复换图会一直占着内存不回收
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file.raw)
}

function onFileRemove() {
  imageFile.value = null
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

async function publish() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!form.value.category) {
    ElMessage.warning('请选择分类')
    return
  }

  submitting.value = true
  try {
    // 构造 multipart/form-data
    const fd = new FormData()
    // 「data」部分：把表单对象转成 JSON 字符串（后端 @RequestPart("data") 按 JSON 解析）
    fd.append('data', new Blob([JSON.stringify(form.value)], { type: 'application/json' }))
    // 「image」部分：可选的图片文件（后端 @RequestPart("image") 接收）
    if (imageFile.value) {
      fd.append('image', imageFile.value)
    }
    await request.post('/item', fd)
    ElMessage.success('发布成功')
    router.push('/items')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="lf-page">
    <div class="lf-page-head">
      <h1 class="lf-page-title">发布信息</h1>
      <p class="lf-page-sub">
        信息填得越具体，越容易被对方搜到 —— 尤其是<b>颜色、特征和地点</b>
      </p>
    </div>

    <div class="lf-panel form-panel">
      <!-- 第一步：先说清是丢了还是捡到了，这是整条信息最重要的分类 -->
      <section class="step">
        <div class="step-head">
          <span class="step-no">1</span>
          <span class="step-title">你是丢了东西，还是捡到了东西？</span>
        </div>
        <el-radio-group v-model="form.type" class="type-group">
          <el-radio value="LOST" border>
            <div class="type-item">
              <el-icon class="type-icon lost"><Search /></el-icon>
              <div>
                <div class="type-name">寻物</div>
                <div class="type-desc">我丢了东西，想找回来</div>
              </div>
            </div>
          </el-radio>
          <el-radio value="FOUND" border>
            <div class="type-item">
              <el-icon class="type-icon found"><Select /></el-icon>
              <div>
                <div class="type-name">招领</div>
                <div class="type-desc">我捡到东西，想找失主</div>
              </div>
            </div>
          </el-radio>
        </el-radio-group>
      </section>

      <!-- 第二步：物品信息 -->
      <section class="step">
        <div class="step-head">
          <span class="step-no">2</span>
          <span class="step-title">物品信息</span>
        </div>

        <el-form label-position="top">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" size="large" placeholder="比如：黑色钱包 / 蓝色笔记本" maxlength="100" show-word-limit />
          </el-form-item>

          <div class="row-2">
            <el-form-item label="分类" required>
              <el-select v-model="form.category" size="large" placeholder="选择分类" style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>

            <el-form-item label="地点">
              <el-input v-model="form.location" size="large" placeholder="丢失 / 拾获的地点">
                <template #prefix><el-icon><LocationInformation /></el-icon></template>
              </el-input>
            </el-form-item>
          </div>

          <el-form-item label="详细描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              placeholder="颜色、品牌、特征、丢失或拾获的大致时间……写得越具体，越容易匹配到"
            />
          </el-form-item>

          <el-form-item label="物品照片（选填）">
            <!-- 换成拖拽上传 + 本地预览：选完图立刻能看到，不用先传上去 -->
            <el-upload
              class="uploader"
              drag
              :auto-upload="false"
              :limit="1"
              accept="image/*"
              :show-file-list="false"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
            >
              <div v-if="previewUrl" class="preview">
                <img :src="previewUrl" alt="预览" />
                <el-button size="small" class="remove-btn" @click.stop="onFileRemove">
                  <el-icon><Delete /></el-icon>移除
                </el-button>
              </div>
              <div v-else class="drop">
                <el-icon class="drop-icon"><PictureRounded /></el-icon>
                <div class="drop-title">点击或拖拽图片到这里</div>
                <div class="drop-sub">支持 jpg / png，单张不超过 10MB</div>
              </div>
            </el-upload>
          </el-form-item>
        </el-form>
      </section>

      <!-- 第三步：联系方式 -->
      <section class="step">
        <div class="step-head">
          <span class="step-no">3</span>
          <span class="step-title">联系方式</span>
        </div>

        <el-form label-position="top">
          <el-form-item label="留个联系方式，对方才能找到你">
            <el-input
              v-model="form.contact"
              size="large"
              placeholder="电话 / 微信 / QQ"
              maxlength="100"
            >
              <template #prefix><el-icon><Phone /></el-icon></template>
            </el-input>
          </el-form-item>
        </el-form>

        <div class="notice">
          <el-icon><InfoFilled /></el-icon>
          <span>联系方式会公开展示在详情页，请自行判断是否填写真实号码</span>
        </div>
      </section>

      <div class="actions">
        <el-button size="large" @click="router.back()">取消</el-button>
        <el-button type="primary" size="large" :loading="submitting" @click="publish">
          <el-icon><Promotion /></el-icon>发布
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.form-panel {
  padding: 8px 24px 24px;
}

/* ── 分步区块 ─────────────────────────────────────────────── */
.step {
  padding: 22px 0;
  border-bottom: 1px solid var(--lf-border);
}

.step:last-of-type {
  border-bottom: none;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: var(--el-color-primary);
}

.step-title {
  font-size: 15px;
  font-weight: 600;
}

/* ── 类型选择 ─────────────────────────────────────────────── */
.type-group {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

/* el-radio 的 border 模式默认是行内元素，这里要它撑满格子 */
.type-group :deep(.el-radio) {
  width: 100%;
  height: auto;
  margin: 0;
  padding: 14px 16px;
  border-radius: var(--lf-radius-sm);
}

.type-group :deep(.el-radio__label) {
  width: 100%;
  padding-left: 10px;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.type-icon {
  font-size: 22px;
}

.type-icon.lost {
  color: var(--lf-danger);
}

.type-icon.found {
  color: var(--lf-success);
}

.type-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--lf-text);
}

.type-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--lf-text-sub);
  font-weight: 400;
}

/* 两列并排的表单项 */
.row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* ── 上传区 ───────────────────────────────────────────────── */
.uploader {
  width: 100%;
}

.uploader :deep(.el-upload),
.uploader :deep(.el-upload-dragger) {
  width: 100%;
}

.uploader :deep(.el-upload-dragger) {
  padding: 0;
  border-radius: var(--lf-radius-sm);
  background: #fafbfc;
  border: 1px dashed #d5d9e2;
  transition: all 0.15s;
}

.uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.drop {
  padding: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.drop-icon {
  font-size: 34px;
  color: var(--lf-text-muted);
}

.drop-title {
  font-size: 13.5px;
  color: var(--lf-text-sub);
}

.drop-sub {
  font-size: 12px;
  color: var(--lf-text-muted);
}

.preview {
  position: relative;
  padding: 12px;
}

.preview img {
  max-height: 220px;
  border-radius: var(--lf-radius-sm);
  display: block;
  margin: 0 auto;
}

.remove-btn {
  position: absolute;
  top: 20px;
  right: 20px;
}

/* ── 提示条 ───────────────────────────────────────────────── */
.notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 14px;
  border-radius: var(--lf-radius-sm);
  background: #fffbeb;
  border: 1px solid #fde68a;
  font-size: 12.5px;
  color: #92400e;
}

/* ── 底部操作 ─────────────────────────────────────────────── */
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 20px;
}

/* ── 窄屏 ─────────────────────────────────────────────────── */
@media (max-width: 640px) {
  .form-panel {
    padding: 4px 16px 20px;
  }

  .type-group,
  .row-2 {
    grid-template-columns: 1fr;
  }
}
</style>
