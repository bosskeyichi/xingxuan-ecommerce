/** 分类配图地址（jpg 实拍图优先，svg 海报兜底） */
export const catImgUrl = cid => (cid ? `/img/cat-${cid}.jpg` : '')

/**
 * 商品封面地址归一化：
 * - 空值 / http(s) / data: / 已带前导斜杠 → 原样返回
 * - 相对路径 `img/products/1.jpg` → 补成 `/img/products/1.jpg`，别让多级路由下解析到错误目录
 */
export function coverUrl(v) {
  const s = String(v || '').trim()
  if (!s) return ''
  if (/^(https?:)?\/\//i.test(s) || s.startsWith('data:') || s.startsWith('blob:') || s.startsWith('/')) return s
  return '/' + s.replace(/^\.\//, '')
}

/**
 * <img> 加载失败兜底：.jpg 失败先换同名 .svg，再失败交由调用方显示文字占位
 * 用法：@error="onImgFallback"
 */
export function onImgFallback(e) {
  const src = e?.target?.src || ''
  if (src.endsWith('.jpg')) {
    e.target.src = src.replace(/\.jpg$/, '.svg')
  } else {
    e.target.style.display = 'none'
  }
}
