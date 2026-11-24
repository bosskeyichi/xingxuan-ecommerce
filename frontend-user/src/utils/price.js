// 金额格式化：统一保留两位小数，规避 JS 浮点误差（如 199.1 - 30 = 169.09999999999997）
export function formatPrice(v) {
  const n = Number(v)
  if (!isFinite(n)) return '0.00'
  return n.toFixed(2)
}

// 金额减法：先转数字相减，再格式化为两位小数，别让一长串浮点尾巴
export function moneyMinus(a, b) {
  return formatPrice(Number(a || 0) - Number(b || 0))
}
