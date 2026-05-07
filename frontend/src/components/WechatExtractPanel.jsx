import { useRef, useState } from 'react'
import { extractWechatArticle } from '../api'
import { copyToClipboard } from '../utils/markdown'
import './WechatExtractPanel.css'

export default function WechatExtractPanel({ agent }) {
  const [url, setUrl] = useState('')
  const [cookie, setCookie] = useState('')
  const [extracting, setExtracting] = useState(false)
  const [error, setError] = useState(null)
  const [article, setArticle] = useState(null)

  const handleExtract = async () => {
    if (!url.trim() || extracting) return
    setError(null)
    setExtracting(true)
    setArticle(null)
    try {
      const a = await extractWechatArticle(url.trim(), cookie.trim() || undefined)
      setArticle(a)
    } catch (err) {
      setError(err?.message || '提取失败')
    } finally {
      setExtracting(false)
    }
  }

  const handleClear = () => {
    if (extracting) return
    setUrl('')
    setCookie('')
    setError(null)
    setArticle(null)
  }

  return (
    <main className="wx-panel">
      <header className="wx-header">
        <div>
          <h1>{agent.name}</h1>
          <p className="wx-intro">{agent.intro}</p>
        </div>
        <button
          type="button"
          className="wx-action"
          onClick={handleClear}
          disabled={extracting}
        >
          清空
        </button>
      </header>

      <div className="wx-body">
        <section className="wx-form">
          <div className="wx-field">
            <label className="wx-label">
              公众号文章链接<span className="wx-req">*</span>
            </label>
            <textarea
              className="wx-input"
              rows={3}
              placeholder="粘贴 mp.weixin.qq.com/s?__biz=...&mid=...&idx=... 链接"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              disabled={extracting}
              spellCheck={false}
            />
          </div>

          <details className="wx-advanced">
            <summary>高级 (一般用不到)</summary>
            <div className="wx-field">
              <label className="wx-label">Cookie</label>
              <textarea
                className="wx-input"
                rows={2}
                placeholder="可选。链接被判定为异常环境时, 从微信内打开后的请求里拷过来"
                value={cookie}
                onChange={(e) => setCookie(e.target.value)}
                disabled={extracting}
                spellCheck={false}
              />
            </div>
          </details>

          <button
            type="button"
            className="wx-btn wx-btn-primary"
            onClick={handleExtract}
            disabled={!url.trim() || extracting}
          >
            {extracting ? '提取中…' : '提取文章'}
          </button>

          {error && <div className="wx-error">{error}</div>}
        </section>

        <section className="wx-result">
          {!article && !extracting && !error && (
            <div className="wx-empty">
              <span className="wx-empty-icon">📰</span>
              <div>粘贴一个公众号文章链接, 点上面的"提取文章"</div>
              <div className="wx-empty-hint">
                返回字段: 标题 / 正文 / 摘要 / 封面 / 账号信息 / 全部图片 / 阅读原文链接
              </div>
            </div>
          )}

          {extracting && (
            <div className="wx-empty">
              <span className="wx-empty-icon">⏳</span>
              <div>正在抓取 mp.weixin SSR 页面…</div>
            </div>
          )}

          {article && <ArticlePreview article={article} />}
        </section>
      </div>
    </main>
  )
}

function ArticlePreview({ article }) {
  const isFail = article.type === 'fail'
  const tags = article.tags || []

  return (
    <div className="wx-article">
      <div className="wx-article-head">
        <span className={`wx-source-tag ${isFail ? 'is-fail' : ''}`}>
          {isFail ? '已失效' : article.type === 'image' ? '图片型' : '图文'}
        </span>
        {article.accountName && (
          <span className="wx-account">
            {article.accountAvatar && (
              <img
                src={article.accountAvatar}
                alt="头像"
                className="wx-avatar"
                referrerPolicy="no-referrer"
              />
            )}
            <span>{article.accountName}</span>
            {article.accountId && (
              <span className="wx-account-id">@{article.accountId}</span>
            )}
          </span>
        )}
        {article.publishTime && (
          <span className="wx-time">{article.publishTime}</span>
        )}
      </div>

      <h2 className="wx-article-title">{article.title || '(无标题)'}</h2>

      {article.digest && (
        <div className="wx-article-digest">
          <span className="wx-mini-label">摘要</span>
          {article.digest}
        </div>
      )}

      {article.cover && (
        <div className="wx-article-cover">
          <img
            src={article.cover}
            alt="封面"
            loading="lazy"
            referrerPolicy="no-referrer"
          />
          <div className="wx-mini-label">封面</div>
        </div>
      )}

      {tags.length > 0 && (
        <div className="wx-article-tags">
          {tags.map((t, i) => (
            <span key={i} className="wx-tag">#{t}</span>
          ))}
        </div>
      )}

      <div className="wx-section-head">
        <span className="wx-mini-label">正文 ({(article.content || '').length} 字)</span>
        <CopyButton label="复制纯文本" text={article.content || ''} />
        <CopyButton label="复制 Markdown" text={buildMarkdown(article)} />
      </div>
      <div className="wx-article-content">{article.content || '(无正文)'}</div>

      {(article.imageUrls || []).length > 0 && (
        <>
          <div className="wx-section-head">
            <span className="wx-mini-label">
              正文图片 ({article.imageUrls.length} 张)
            </span>
          </div>
          <div className="wx-article-images">
            {article.imageUrls.map((src, i) => (
              <a key={i} href={src} target="_blank" rel="noreferrer">
                <img
                  src={src}
                  alt={`图${i + 1}`}
                  loading="lazy"
                  referrerPolicy="no-referrer"
                />
              </a>
            ))}
          </div>
        </>
      )}

      {(article.url || article.sourceUrl) && (
        <div className="wx-links">
          {article.url && (
            <a href={article.url} target="_blank" rel="noreferrer">
              ↗ 打开原文
            </a>
          )}
          {article.sourceUrl && (
            <a href={article.sourceUrl} target="_blank" rel="noreferrer">
              ↗ 阅读原文链接
            </a>
          )}
        </div>
      )}

      <details className="wx-raw">
        <summary>原始 JSON</summary>
        <pre>{JSON.stringify(article, null, 2)}</pre>
      </details>
    </div>
  )
}

function CopyButton({ label, text }) {
  const [copied, setCopied] = useState(false)
  const timeoutRef = useRef(null)
  const handleClick = async () => {
    try {
      await copyToClipboard(text)
      setCopied(true)
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      timeoutRef.current = setTimeout(() => setCopied(false), 1500)
    } catch {
      // ignore
    }
  }
  return (
    <button
      type="button"
      className={'wx-copy' + (copied ? ' is-copied' : '')}
      onClick={handleClick}
    >
      {copied ? '已复制' : label}
    </button>
  )
}

function buildMarkdown(a) {
  const lines = []
  if (a.title) lines.push(`# ${a.title}`, '')
  if (a.accountName) lines.push(`> 来源: ${a.accountName}${a.publishTime ? ' · ' + a.publishTime : ''}`, '')
  if (a.cover) lines.push(`![封面](${a.cover})`, '')
  if (a.digest) lines.push(`**摘要**: ${a.digest}`, '')
  if (a.content) lines.push(a.content)
  return lines.join('\n')
}
