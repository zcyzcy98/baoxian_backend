import { useState, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { callAgent } from '../api'
import { copyToClipboard, stripMarkdown } from '../utils/markdown'
import './ViralPage.css'

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

export default function ViralXhsPage(props) {
  return <ViralAnalysisPage platform="xhs" {...props} />
}

export function ViralAnalysisPage({
  platform = 'xhs',
  contentPrefill, onContentPrefillConsumed,
  onNavigate, onNavigateWithContentPrefill,
}) {
  const isXhs = platform === 'xhs'
  const isDouyin = platform === 'douyin'
  const meta = isXhs
    ? {
        title: '拆解小红书爆款',
        platformLabel: '小红书',
        platformClass: 'xhs',
        endpoint: 'viral-xhs',
        placeholder: '粘贴 xhslink.cn 短链或 www.xiaohongshu.com/explore/... 链接',
        useTextarea: false,
      }
    : {
        title: '拆解抖音爆款',
        platformLabel: '抖音',
        platformClass: 'douyin',
        endpoint: 'viral-douyin',
        placeholder: '粘贴 v.douyin.com/... 短链, 或粘贴 App 复制的完整分享文字 (含链接)',
        useTextarea: true,
      }

  const ST = {
    url: '',
    loading: false,
    result: null,
    error: '',
    showCookiePanel: false,
    cookieInput: '',
    hasCookie: false,
    cookieSaving: false,
    cookieMsg: '',
  }
  const [url, setUrl] = useState(ST.url)
  const [loading, setLoading] = useState(ST.loading)
  const [result, setResult] = useState(ST.result)
  const [error, setError] = useState(ST.error)
  const [showCookiePanel, setShowCookiePanel] = useState(ST.showCookiePanel)
  const [cookieInput, setCookieInput] = useState(ST.cookieInput)
  const [hasCookie, setHasCookie] = useState(ST.hasCookie)
  const [cookieSaving, setCookieSaving] = useState(ST.cookieSaving)
  const [cookieMsg, setCookieMsg] = useState(ST.cookieMsg)

  const resetState = () => {
    setUrl(ST.url)
    setLoading(ST.loading)
    setResult(ST.result)
    setError(ST.error)
    setShowCookiePanel(ST.showCookiePanel)
  }

  useEffect(() => {
    if (contentPrefill) {
      if (contentPrefill.url) setUrl(contentPrefill.url)
      if (contentPrefill.result) setResult(contentPrefill.result)
      onContentPrefillConsumed?.()
    }
  }, [contentPrefill])

  useEffect(() => {
    if (!isDouyin) return
    fetch(`${API_BASE}/api/douyin/cookie/status`)
      .then(r => r.json())
      .then(d => setHasCookie(!!d.hasCookie))
      .catch(() => {})
  }, [isDouyin])

  const saveCookie = async () => {
    setCookieSaving(true)
    setCookieMsg('')
    try {
      const r = await fetch(`${API_BASE}/api/douyin/cookie`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ cookie: cookieInput }),
      })
      const d = await r.json()
      setHasCookie(!!d.hasCookie)
      setCookieMsg('保存成功')
      if (d.hasCookie) setTimeout(() => setShowCookiePanel(false), 800)
    } catch {
      setCookieMsg('保存失败，请重试')
    } finally {
      setCookieSaving(false)
    }
  }

  const clearCookie = async () => {
    await fetch(`${API_BASE}/api/douyin/cookie`, { method: 'DELETE' })
    setHasCookie(false)
    setCookieInput('')
    setCookieMsg('已清除')
  }

  const handleAnalyze = async () => {
    if (!url.trim() || loading) return
    setLoading(true)
    setError('')
    setResult(null)
    try {
      const r = await callAgent(meta.endpoint, { videoUrl: url.trim() })
      setResult(r)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="viral-page">
      <header className="viral-header">
        <div>
          <h2>
            <span className={`platform-tag ${meta.platformClass}`}>{meta.platformLabel}</span>
            {meta.title}
          </h2>
          <p className="page-sub">
            粘贴链接 → AI 自动提取笔记/视频内容并拆解爆款结构, 给出可复用的保险选题建议
          </p>
        </div>
        {isDouyin && (
          <button
            className={`btn-ghost dy-cookie-btn ${hasCookie ? 'has-cookie' : ''}`}
            onClick={() => { setShowCookiePanel(v => !v); setCookieMsg('') }}
            title={hasCookie ? 'Cookie 已设置（点击更新）' : '设置抖音 Cookie 以获取更好效果'}
          >
            {hasCookie ? '🔑 Cookie 已设置' : '⚙ 设置 Cookie'}
          </button>
        )}
        <button className="btn-ghost reset-btn" onClick={resetState} title="清除所有内容重新开始">
          重新开始
        </button>
      </header>

      {isDouyin && showCookiePanel && (
        <section className="dy-cookie-panel">
          <h4>抖音 Cookie 设置</h4>
          <p className="dy-cookie-tip">
            在浏览器中登录 <strong>douyin.com</strong>，打开 DevTools（F12）→ Network 标签 → 随便点一个请求 →
            找到 <code>Cookie</code> 请求头 → 复制整行内容粘贴到下方。Cookie 仅保存在服务器内存中，重启后失效。
          </p>
          <textarea
            className="form-input dy-cookie-input"
            rows={4}
            placeholder="ttwid=xxx; msToken=xxx; ..."
            value={cookieInput}
            onChange={e => setCookieInput(e.target.value)}
          />
          <div className="dy-cookie-actions">
            {cookieMsg && <span className="dy-cookie-msg">{cookieMsg}</span>}
            {hasCookie && (
              <button className="btn-ghost" onClick={clearCookie}>清除</button>
            )}
            <button className="btn-primary" onClick={saveCookie} disabled={!cookieInput.trim() || cookieSaving}>
              {cookieSaving ? '保存中…' : '保存'}
            </button>
          </div>
        </section>
      )}

      <section className="viral-input-card">
        <label className="form-label">链接 <span className="required">*</span></label>
        {meta.useTextarea ? (
          <textarea
            className="form-input"
            rows={3}
            placeholder={meta.placeholder}
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            disabled={loading}
          />
        ) : (
          <input
            className="form-input"
            placeholder={meta.placeholder}
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            disabled={loading}
          />
        )}
        <div className="viral-input-foot">
          <span className="hint">消耗 5 积分</span>
          <button className="btn-primary" onClick={handleAnalyze} disabled={!url.trim() || loading}>
            {loading ? '拆解中…' : '开始拆解'}
          </button>
        </div>
        {error && <div className="kb-error">{error}</div>}
      </section>

      {loading && (
        <div className="viral-loading">
          <div className="spinner" />
          <div>AI 正在拆解爆款结构, 大约 20-30 秒</div>
        </div>
      )}

      {result && (
        <section className="viral-result-card">
          <div className="result-head">
            <h3>拆解结果</h3>
            <div className="result-actions">
              {result.model && <span className="hint">via {result.model}</span>}
              <button className="btn-ghost" onClick={() => copyToClipboard(stripMarkdown(result.content || ''))}>复制纯文本</button>
              <button className="btn-ghost" onClick={() => { setResult(null); setUrl('') }}>重置</button>
            </div>
          </div>

          {result.imageUrl && (
            <div className="viral-cover">
              <img src={result.imageUrl} alt="封面" referrerPolicy="no-referrer" />
            </div>
          )}

          <div className="viral-content">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{result.content || '(空)'}</ReactMarkdown>
          </div>
        </section>
      )}
    </div>
  )
}
