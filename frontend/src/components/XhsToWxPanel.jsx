import { useEffect, useRef, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import {
  extractAutoNote,
  rewriteXhsNote,
  createWechatDraft,
  fetchRewriteModes,
} from '../api'
import {
  loadWorkflowState,
  saveWorkflowState,
  clearWorkflowState,
} from '../storage'
import { stripMarkdown, copyToClipboard } from '../utils/markdown'
import './XhsToWxPanel.css'

const DEFAULT_STATE = {
  url: '',
  cookie: '',
  extractedNote: null,
  rewriteMode: 'copy',
  requirements: '不改变原始文案',
  imageBorder: 'none',
  rewriteResult: null,
  wechatTheme: 'default',
  coverMediaId: '',
  publishToWechat: false,
}

const IMAGE_BORDER_OPTIONS = [
  { id: 'none', label: '无边框' },
  { id: 'thin', label: '细边框', disabled: true },
  { id: 'thick', label: '粗边框', disabled: true },
  { id: 'shadow', label: '阴影', disabled: true },
]

export default function XhsToWxPanel({ agent }) {
  const persisted = loadWorkflowState(agent.id) || {}
  const [url, setUrl] = useState(persisted.url ?? DEFAULT_STATE.url)
  const [cookie, setCookie] = useState(DEFAULT_STATE.cookie)
  const [extractedNote, setExtractedNote] = useState(
    persisted.extractedNote ?? DEFAULT_STATE.extractedNote,
  )
  const [extracting, setExtracting] = useState(false)
  const [extractError, setExtractError] = useState(null)

  const [rewriteMode, setRewriteMode] = useState(
    persisted.rewriteMode ?? DEFAULT_STATE.rewriteMode,
  )
  const [requirements, setRequirements] = useState(
    persisted.requirements ?? DEFAULT_STATE.requirements,
  )
  const [imageBorder, setImageBorder] = useState(
    persisted.imageBorder ?? DEFAULT_STATE.imageBorder,
  )
  const [model, setModel] = useState('chat')
  const [rewriting, setRewriting] = useState(false)
  const [rewriteResult, setRewriteResult] = useState(
    persisted.rewriteResult ?? DEFAULT_STATE.rewriteResult,
  )
  const [rewriteError, setRewriteError] = useState(null)
  const [wechatAppId, setWechatAppId] = useState('')
  const [wechatAppSecret, setWechatAppSecret] = useState('')
  const [md2wechatApiKey, setMd2wechatApiKey] = useState('')
  const [coverImagePath, setCoverImagePath] = useState('')
  const [coverMediaId, setCoverMediaId] = useState(
    persisted.coverMediaId ?? DEFAULT_STATE.coverMediaId,
  )
  const [wechatTheme, setWechatTheme] = useState(
    persisted.wechatTheme ?? DEFAULT_STATE.wechatTheme,
  )
  const [publishToWechat, setPublishToWechat] = useState(
    persisted.publishToWechat ?? DEFAULT_STATE.publishToWechat,
  )
  const [publishing, setPublishing] = useState(false)
  const [publishError, setPublishError] = useState(null)
  const [publishResult, setPublishResult] = useState(null)

  const [modes, setModes] = useState([])
  const [modesLoading, setModesLoading] = useState(false)

  // load modes once
  useEffect(() => {
    let cancelled = false
    setModesLoading(true)
    fetchRewriteModes()
      .then((data) => {
        if (!cancelled) setModes(data || [])
      })
      .catch(() => {
        if (!cancelled)
          setModes([
            { id: 'copy', label: '复制原文案' },
            { id: 'synonym', label: '同义改写' },
            { id: 'mp_style', label: '转公众号风格(扩写)' },
          ])
      })
      .finally(() => !cancelled && setModesLoading(false))
    return () => {
      cancelled = true
    }
  }, [])

  // persist
  useEffect(() => {
    saveWorkflowState(agent.id, {
      url,
      extractedNote,
      rewriteMode,
      requirements,
      imageBorder,
      rewriteResult,
      coverMediaId,
      wechatTheme,
      publishToWechat,
    })
  }, [
    agent.id,
    url,
    extractedNote,
    rewriteMode,
    requirements,
    imageBorder,
    rewriteResult,
    coverMediaId,
    wechatTheme,
    publishToWechat,
  ])

  const handleExtract = async () => {
    if (!url.trim() || extracting) return
    setExtractError(null)
    setExtracting(true)
    try {
      // 自动识别公众号/小红书, 返回 { source, note }
      const result = await extractAutoNote(url.trim(), cookie.trim())
      const source = result?.source || 'xhs'
      const raw = result?.note || result
      // 把公众号字段适配到原来 XhsNote-shape, 让后续 rewrite/preview 不动
      const adapted = source === 'wechat' ? wechatToNoteShape(raw) : raw
      setExtractedNote({ ...adapted, _source: source })
      setRewriteResult(null)
    } catch (err) {
      setExtractError(err.message)
    } finally {
      setExtracting(false)
    }
  }

  const handleRewrite = async () => {
    if (!extractedNote || rewriting) return
    setRewriteError(null)
    setRewriting(true)
    try {
      const result = await rewriteXhsNote({
        originalTitle: extractedNote.title,
        originalContent: extractedNote.content,
        mode: rewriteMode,
        requirements,
        model,
      })
      setRewriteResult(result)
      setPublishResult(null)
    } catch (err) {
      setRewriteError(err.message)
    } finally {
      setRewriting(false)
    }
  }

  const handlePublish = async () => {
    if (!rewriteResult || publishing) return
    setPublishError(null)
    setPublishing(true)
    try {
      const result = await createWechatDraft({
        title: rewriteResult.title,
        content: rewriteResult.content,
        author: '保险助手',
        appId: wechatAppId.trim(),
        appSecret: wechatAppSecret.trim(),
        md2wechatApiKey: md2wechatApiKey.trim(),
        coverImagePath: coverImagePath.trim(),
        coverMediaId: coverMediaId.trim(),
        theme: wechatTheme,
        publish: publishToWechat,
      })
      setPublishResult(result)
    } catch (err) {
      setPublishError(err.message)
    } finally {
      setPublishing(false)
    }
  }

  const handleResetAll = () => {
    if (extracting || rewriting || publishing) return
    if (!confirm('确认清空当前工作流的所有内容吗?(链接、提取结果、改写结果都会被清掉)')) return
    clearWorkflowState(agent.id)
    setUrl('')
    setCookie('')
    setExtractedNote(null)
    setExtractError(null)
    setRewriteMode('copy')
    setRequirements('不改变原始文案')
    setImageBorder('none')
    setRewriteResult(null)
    setRewriteError(null)
    setWechatAppId('')
    setWechatAppSecret('')
    setMd2wechatApiKey('')
    setCoverImagePath('')
    setCoverMediaId('')
    setWechatTheme('default')
    setPublishToWechat(false)
    setPublishError(null)
    setPublishResult(null)
  }

  return (
    <main className="wf-panel">
      <header className="wf-header">
        <h1 className="wf-title">{agent.name}</h1>
        <button
          type="button"
          className="wf-action"
          onClick={handleResetAll}
          disabled={extracting || rewriting || publishing}
          title="清空当前工作流"
        >
          清空工作流
        </button>
      </header>

      <div className="wf-columns">
        <StepExtract
          url={url}
          cookie={cookie}
          onUrlChange={setUrl}
          onCookieChange={setCookie}
          extracting={extracting}
          extractError={extractError}
          extractedNote={extractedNote}
          onExtract={handleExtract}
        />

        <StepRewrite
          extractedNote={extractedNote}
          modes={modes}
          modesLoading={modesLoading}
          rewriteMode={rewriteMode}
          onModeChange={setRewriteMode}
          requirements={requirements}
          onRequirementsChange={setRequirements}
          imageBorder={imageBorder}
          onImageBorderChange={setImageBorder}
          model={model}
          onModelChange={setModel}
          rewriting={rewriting}
          rewriteError={rewriteError}
          rewriteResult={rewriteResult}
          onRewrite={handleRewrite}
        />

        <StepPublish
          rewriteResult={rewriteResult}
          appId={wechatAppId}
          onAppIdChange={setWechatAppId}
          appSecret={wechatAppSecret}
          onAppSecretChange={setWechatAppSecret}
          apiKey={md2wechatApiKey}
          onApiKeyChange={setMd2wechatApiKey}
          coverImagePath={coverImagePath}
          onCoverImagePathChange={setCoverImagePath}
          coverMediaId={coverMediaId}
          onCoverMediaIdChange={setCoverMediaId}
          theme={wechatTheme}
          onThemeChange={setWechatTheme}
          publishToWechat={publishToWechat}
          onPublishToWechatChange={setPublishToWechat}
          publishing={publishing}
          publishError={publishError}
          publishResult={publishResult}
          onPublish={handlePublish}
        />
      </div>
    </main>
  )
}

/**
 * 把后端 WechatArticle 的字段名映射成前端 XhsNote 形状,
 * 这样 StepRewrite / StepPublish 不用改, 直接复用.
 */
function wechatToNoteShape(a) {
  if (!a) return null
  return {
    noteId: a.msgBiz && a.msgMid ? `${a.msgBiz}-${a.msgMid}-${a.msgIdx || ''}` : '',
    url: a.url,
    type: a.type === 'image' ? '图片型' : '图文',
    title: a.title || '',
    content: a.content || '',
    tags: a.tags || [],
    imageUrls: a.imageUrls || (a.cover ? [a.cover] : []),
    videoUrl: '',
    publishTime: a.publishTime || '',
    authorName: a.accountName || '',
    authorId: a.accountId || '',
    likedCount: '',
    collectedCount: '',
    commentCount: '',
    shareCount: '',
  }
}

function detectSource(url) {
  const u = (url || '').toLowerCase()
  if (u.includes('mp.weixin.qq.com') || u.includes('weixin.qq.com')) return 'wechat'
  if (u.includes('xiaohongshu.com') || u.includes('xhslink.com')) return 'xhs'
  return ''
}

function StepExtract({
  url,
  cookie,
  onUrlChange,
  onCookieChange,
  extracting,
  extractError,
  extractedNote,
  onExtract,
}) {
  const detected = detectSource(url)
  const isWechat = (extractedNote?._source || detected) === 'wechat'
  return (
    <section className="wf-col">
      <h2 className="wf-step-title">1、提取笔记</h2>
      <div className="wf-field">
        <label className="wf-label">
          笔记链接<span className="wf-req">*</span>
          {detected && (
            <span className="wf-source-badge">
              {detected === 'wechat' ? '公众号' : '小红书'}
            </span>
          )}
        </label>
        <textarea
          className="wf-input"
          rows={3}
          placeholder="支持: 小红书 (xhslink/explore) 或 公众号 (mp.weixin.qq.com/s)"
          value={url}
          onChange={(e) => onUrlChange(e.target.value)}
          disabled={extracting}
        />
      </div>
      {!isWechat && (
        <div className="wf-field">
          <label className="wf-label">小红书 Cookie</label>
          <textarea
            className="wf-input wf-cookie-input"
            rows={4}
            placeholder="可选。遇到 403 时，从已登录小红书的浏览器请求里复制 Cookie 后粘贴到这里"
            value={cookie}
            onChange={(e) => onCookieChange(e.target.value)}
            disabled={extracting}
            spellCheck={false}
          />
          <div className="wf-help">Cookie 只在当前页面临时使用，不会写入本地工作流缓存。</div>
        </div>
      )}
      {!isWechat && (
        <div className="wf-field">
          <label className="wf-label">
            提取图片文字 (根据笔记类型选择)<span className="wf-req">*</span>
          </label>
          <select className="wf-input" disabled value="no">
            <option value="no">否(暂不支持 OCR)</option>
          </select>
        </div>
      )}
      {isWechat && (
        <div className="wf-help">
          ✅ 公众号链接自动识别。无需 Cookie。仅抓取标题/正文/封面/摘要 (不含阅读量)。
        </div>
      )}

      <button
        type="button"
        className="wf-btn wf-btn-primary"
        onClick={onExtract}
        disabled={!url.trim() || extracting}
      >
        {extracting ? '提取中...' : '提取笔记'}
      </button>

      {extractError && <div className="wf-error">{extractError}</div>}

      {extractedNote && (
        <div className="wf-preview">
          <div className="wf-preview-head">
            <span className="wf-preview-tag">
              已提取{extractedNote._source === 'wechat' ? ' (公众号)' : ''}
            </span>
            <span className="wf-preview-meta">
              {extractedNote.type} · {extractedNote.authorName} ·{' '}
              {extractedNote.imageUrls?.length ?? 0} 图
            </span>
          </div>
          <div className="wf-preview-title">{extractedNote.title}</div>
          <div className="wf-preview-tags">
            {(extractedNote.tags || []).map((t, i) => (
              <span key={i} className="wf-tag">#{t}</span>
            ))}
          </div>
          <div className="wf-preview-body">{extractedNote.content}</div>
          {extractedNote.imageUrls?.length > 0 && (
            <div className="wf-preview-images">
              {extractedNote.imageUrls.slice(0, 6).map((src, i) => (
                <img
                  key={i}
                  src={src}
                  alt={`图${i + 1}`}
                  loading="lazy"
                  referrerPolicy="no-referrer"
                />
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

function StepRewrite({
  extractedNote,
  modes,
  modesLoading,
  rewriteMode,
  onModeChange,
  requirements,
  onRequirementsChange,
  imageBorder,
  onImageBorderChange,
  model,
  onModelChange,
  rewriting,
  rewriteError,
  rewriteResult,
  onRewrite,
}) {
  const ready = !!extractedNote
  return (
    <section className={'wf-col' + (ready ? '' : ' is-locked')}>
      <h2 className="wf-step-title">2、复刻笔记</h2>
      {!ready && <div className="wf-locked-tip">请先在左侧提取笔记</div>}

      <div className="wf-field">
        <label className="wf-label">
          改写要求<span className="wf-req">*</span>
        </label>
        <textarea
          className="wf-input"
          rows={3}
          placeholder="自由文本: 例如 把语气更年轻化 / 强化数据点 / 去掉 emoji"
          value={requirements}
          onChange={(e) => onRequirementsChange(e.target.value)}
          disabled={!ready || rewriting}
        />
      </div>

      <div className="wf-field">
        <label className="wf-label">
          复刻方式<span className="wf-req">*</span>
        </label>
        <select
          className="wf-input"
          value={rewriteMode}
          onChange={(e) => onModeChange(e.target.value)}
          disabled={!ready || rewriting || modesLoading}
        >
          {modes.map((m) => (
            <option key={m.id} value={m.id}>{m.label}</option>
          ))}
        </select>
      </div>

      <div className="wf-field">
        <label className="wf-label">
          添加图片边框<span className="wf-req">*</span>
        </label>
        <select
          className="wf-input"
          value={imageBorder}
          onChange={(e) => onImageBorderChange(e.target.value)}
          disabled={!ready || rewriting}
        >
          {IMAGE_BORDER_OPTIONS.map((opt) => (
            <option key={opt.id} value={opt.id} disabled={opt.disabled}>
              {opt.label}{opt.disabled ? ' (暂不支持)' : ''}
            </option>
          ))}
        </select>
      </div>

      <div className="wf-field wf-field-row">
        <label className="wf-label">使用模型</label>
        <div className="wf-model-toggle">
          <button
            type="button"
            className={'wf-pill' + (model === 'chat' ? ' is-active' : '')}
            onClick={() => onModelChange('chat')}
            disabled={!ready || rewriting}
          >
            DeepSeek 原版
          </button>
          <button
            type="button"
            className={'wf-pill' + (model === 'reasoner' ? ' is-active' : '')}
            onClick={() => onModelChange('reasoner')}
            disabled={!ready || rewriting}
          >
            DeepSeek (投喂)
          </button>
        </div>
      </div>

      <button
        type="button"
        className="wf-btn wf-btn-accent"
        onClick={onRewrite}
        disabled={!ready || rewriting}
      >
        {rewriting ? '改写中...' : '一键复刻'}
      </button>

      {rewriteError && <div className="wf-error">{rewriteError}</div>}

      {rewriteResult && (
        <div className="wf-preview">
          <div className="wf-preview-head">
            <span className="wf-preview-tag">改写结果</span>
            <span className="wf-preview-meta">via {rewriteResult.model}</span>
            <CopyButton text={rewriteResult.content} />
          </div>
          <div className="wf-preview-title">{rewriteResult.title || '(无标题)'}</div>
          <div className="wf-preview-md">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{rewriteResult.content}</ReactMarkdown>
          </div>
        </div>
      )}
    </section>
  )
}

function StepPublish({
  rewriteResult,
  appId,
  onAppIdChange,
  appSecret,
  onAppSecretChange,
  apiKey,
  onApiKeyChange,
  coverImagePath,
  onCoverImagePathChange,
  coverMediaId,
  onCoverMediaIdChange,
  theme,
  onThemeChange,
  publishToWechat,
  onPublishToWechatChange,
  publishing,
  publishError,
  publishResult,
  onPublish,
}) {
  const ready = !!rewriteResult
  const canSubmit = Boolean(ready && !publishing && (!publishToWechat || (
    appId.trim() &&
    appSecret.trim() &&
    (coverImagePath.trim() || coverMediaId.trim())
  )))

  return (
    <section className={'wf-col' + (ready ? '' : ' is-locked')}>
      <h2 className="wf-step-title">3、转公众号小绿书</h2>
      {!ready && <div className="wf-locked-tip">请先完成公众号风格改写</div>}
      <div className="wf-tips">默认只生成 Markdown 和命令预览；打开提交开关后才会调用微信草稿箱。</div>

      <div className="wf-field">
        <label className="wf-label">
          AppID{publishToWechat && <span className="wf-req">*</span>}
        </label>
        <input
          className="wf-input"
          value={appId}
          onChange={(e) => onAppIdChange(e.target.value)}
          placeholder="wx..."
          disabled={!ready || publishing}
          spellCheck={false}
        />
      </div>

      <div className="wf-field">
        <label className="wf-label">
          AppSecret{publishToWechat && <span className="wf-req">*</span>}
        </label>
        <input
          className="wf-input"
          type="password"
          value={appSecret}
          onChange={(e) => onAppSecretChange(e.target.value)}
          placeholder="只用于本次请求，不保存"
          disabled={!ready || publishing}
          spellCheck={false}
        />
      </div>

      <div className="wf-field">
        <label className="wf-label">md2wechat API Key</label>
        <input
          className="wf-input"
          type="password"
          value={apiKey}
          onChange={(e) => onApiKeyChange(e.target.value)}
          placeholder="可选；使用 API 模式排版时填写"
          disabled={!ready || publishing}
          spellCheck={false}
        />
      </div>

      <div className="wf-field">
        <label className="wf-label">
          封面图片路径{publishToWechat && !coverMediaId.trim() && <span className="wf-req">*</span>}
        </label>
        <input
          className="wf-input"
          value={coverImagePath}
          onChange={(e) => onCoverImagePathChange(e.target.value)}
          placeholder="/Users/.../cover.jpg；本机 CLI 2.0.4 推荐用这个"
          disabled={!ready || publishing}
          spellCheck={false}
        />
      </div>

      <div className="wf-field">
        <label className="wf-label">
          封面 media_id{publishToWechat && !coverImagePath.trim() && <span className="wf-req">*</span>}
        </label>
        <input
          className="wf-input"
          value={coverMediaId}
          onChange={(e) => onCoverMediaIdChange(e.target.value)}
          placeholder="新版 md2wechat 支持；本机 2.0.4 暂不支持"
          disabled={!ready || publishing}
          spellCheck={false}
        />
      </div>

      <div className="wf-field">
        <label className="wf-label">排版主题</label>
        <select
          className="wf-input"
          value={theme}
          onChange={(e) => onThemeChange(e.target.value)}
          disabled={!ready || publishing}
        >
          <option value="default">default</option>
          <option value="chinese">chinese</option>
          <option value="apple">apple</option>
          <option value="bytedance">bytedance</option>
          <option value="elegant-gold">elegant-gold</option>
          <option value="minimal-blue">minimal-blue</option>
        </select>
      </div>

      <label className="wf-check">
        <input
          type="checkbox"
          checked={publishToWechat}
          onChange={(e) => onPublishToWechatChange(e.target.checked)}
          disabled={!ready || publishing}
        />
        <span>提交到微信草稿箱</span>
      </label>

      <button
        type="button"
        className="wf-btn wf-btn-accent"
        onClick={onPublish}
        disabled={!canSubmit}
      >
        {publishing ? '处理中...' : publishToWechat ? '生成公众号草稿' : '生成预览'}
      </button>

      {publishError && <div className="wf-error">{publishError}</div>}

      {publishResult && (
        <div className="wf-preview">
          <div className="wf-preview-head">
            <span className="wf-preview-tag">{publishResult.published ? '已提交' : '预览'}</span>
            {publishResult.mediaId && <span className="wf-preview-meta">media_id: {publishResult.mediaId}</span>}
          </div>
          <div className="wf-result-line">{publishResult.message}</div>
          <div className="wf-result-label">Markdown 文件</div>
          <div className="wf-code-line">{publishResult.markdownPath}</div>
          <div className="wf-result-label">命令预览</div>
          <div className="wf-code-block">{publishResult.commandPreview}</div>
          {publishResult.stdout && (
            <>
              <div className="wf-result-label">stdout</div>
              <div className="wf-code-block">{publishResult.stdout}</div>
            </>
          )}
        </div>
      )}
    </section>
  )
}

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)
  const timeoutRef = useRef(null)
  const handleClick = async () => {
    try {
      await copyToClipboard(stripMarkdown(text))
      setCopied(true)
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      timeoutRef.current = setTimeout(() => setCopied(false), 1500)
    } catch {
      /* ignore */
    }
  }
  return (
    <button
      type="button"
      className={'wf-copy-btn' + (copied ? ' is-copied' : '')}
      onClick={handleClick}
      title="复制为纯文本"
    >
      {copied ? '已复制' : '复制纯文本'}
    </button>
  )
}
