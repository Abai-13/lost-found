/**
 * 展示层的小工具。
 *
 * 抽成单独文件的理由很实际：「多久之前」这个逻辑列表页和详情页都要用，
 * 各写一份的后果不是多几行代码，而是两处的措辞会慢慢不一样
 * （一个显示「3 小时前」另一个显示「3小时前」）。
 */

/** 后端返回的是 ISO 字符串（2026-09-15T01:44:48），要变成人能读的相对时间 */
export function relativeTime(iso) {
  if (!iso) return '-'
  // 后端存的是本地时间且不带时区，直接 new Date(iso) 在 Safari 上会 NaN，
  // 所以手动解析，不依赖各浏览器对非标准格式的容忍度
  const m = String(iso).match(/(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/)
  if (!m) return String(iso).replace('T', ' ')
  const time = new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])

  const diff = Date.now() - time.getTime()
  const min = 60 * 1000
  const hour = 60 * min
  const day = 24 * hour

  if (diff < 0) return '刚刚'
  if (diff < min) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / min)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 30 * day) return `${Math.floor(diff / day)} 天前`
  // 超过 30 天就不用相对时间了，直接给日期更清楚
  return `${m[1]}-${m[2]}-${m[3]}`
}

/** 完整时间，详情页用 */
export function fullTime(iso) {
  return iso ? String(iso).replace('T', ' ').slice(0, 16) : '-'
}

/**
 * 分类 → 图标 + 背景。
 *
 * 为什么用 emoji 而不是 Element 的图标组件：
 * Element Plus 的图标**全是 1px 描边的线性图标**，单独看还行，
 * 放大到 40px 以上去撑一个色块就显得又细又灰 —— 实测第一版就是这样，
 * 用户的原话是「像没加载出来的图片」。
 * emoji 是彩色实心的，一眼就看出「这是故意放的图标」而不是「图挂了」，
 * 而且零依赖、离线可用。校园场景里这种风格也更亲切。
 *
 * bg 直接给现成的 CSS 渐变字符串，调用方 :style 一贴就行，
 * 不用在每个页面里再拼一次 linear-gradient。
 */
const CATEGORY_STYLE = {
  电子产品: { emoji: '📱', bg: 'linear-gradient(135deg, #e8edff, #cdd8ff)' },
  证件: { emoji: '💳', bg: 'linear-gradient(135deg, #fff4e0, #ffe3b0)' },
  衣物: { emoji: '👕', bg: 'linear-gradient(135deg, #fdeef6, #fbd8e9)' },
  书籍: { emoji: '📚', bg: 'linear-gradient(135deg, #e8fbf1, #c9f2de)' },
  其他: { emoji: '📦', bg: 'linear-gradient(135deg, #f1f4f9, #dfe5ef)' },
}

export function categoryStyle(category) {
  return CATEGORY_STYLE[category] || CATEGORY_STYLE['其他']
}
