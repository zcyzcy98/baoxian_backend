const PROXY_HOSTS = [
  'mmbiz.qpic.cn',
  'mmbiz.qlogo.cn',
  'wx.qlogo.cn',
  'res.wx.qq.com',
]

export function proxyImageUrl(url) {
  if (!url) return url
  try {
    const host = new URL(url).hostname
    if (PROXY_HOSTS.some((h) => host.endsWith(h))) {
      return `/api/image-proxy?url=${encodeURIComponent(url)}`
    }
  } catch (_) {
    // invalid URL, return as-is
  }
  return url
}
