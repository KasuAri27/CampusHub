/**
 * 上传文件地址归一化（K6）。
 * 后端上传接口返回 /api/uploads/xxx 形式的相对路径，开发环境由 Vite 代理 /api，
 * 生产环境须保证同路径映射，因此前端直接使用相对路径，不再拼接写死的后端地址。
 * 历史数据里可能存有 http://localhost:8080/api/uploads/... 的绝对地址（旧版资料页保存所致），
 * 这里剥掉本机开发 origin 转回相对路径；其他外部绝对地址保持不变。
 */
const LEGACY_DEV_ORIGIN = /^https?:\/\/(localhost|127\.0\.0\.1):8080(?=\/)/i

export function assetUrl(url) {
  if (!url) return ''
  const text = String(url).trim()
  if (LEGACY_DEV_ORIGIN.test(text)) return text.replace(LEGACY_DEV_ORIGIN, '')
  if (/^(https?:|data:|blob:)/i.test(text)) return text
  if (text.startsWith('/')) return text
  // 兼容只存了 uploads/xxx 的极旧数据
  return text.startsWith('uploads/') ? `/api/${text}` : `/${text}`
}
