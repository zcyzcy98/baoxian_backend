import { useEffect, useRef, useState } from 'react'
import './AgentForm.css'

const DURATION_OPTIONS = [
  { value: '5秒', label: '5 秒' },
  { value: '10秒', label: '10 秒' },
  { value: '15秒', label: '15 秒' },
  { value: '自定义', label: '自定义' },
]

export default function AgentForm({ agent, onSubmit, disabled, prefill, onPrefillConsumed }) {
  const [values, setValues] = useState({})
  const [showCustomDuration, setShowCustomDuration] = useState(false)
  const [uploadingFields, setUploadingFields] = useState({})
  const [uploadPreviews, setUploadPreviews] = useState({})
  const fileInputRefs = useRef({})

  useEffect(() => {
    const initial = {}
    ;(agent.fields || []).forEach((f) => {
      if (f.options && f.options.length > 0) {
        // 处理布尔选项
        const firstOption = f.options[0]
        if (typeof firstOption.value === 'boolean') {
          initial[f.name] = firstOption.value
        } else {
          initial[f.name] = f.defaultValue || firstOption.value || ''
        }
      } else if (f.name === 'wordCount') {
        initial[f.name] = f.defaultValue || 2000
      } else {
        initial[f.name] = f.defaultValue || ''
      }
    })
    if (prefill && typeof prefill === 'object') {
      for (const [k, v] of Object.entries(prefill)) {
        if (k in initial && v != null) {
          initial[k] = v
        }
      }
    }
    setValues(initial)
    setShowCustomDuration(false)
    if (prefill && onPrefillConsumed) onPrefillConsumed()
  }, [agent.id, prefill])

  const isValid = (agent.fields || [])
    .filter((f) => f.required)
    .every((f) => {
      const val = values[f.name]
      if (typeof val === 'string') {
        return val.trim()
      }
      return val !== undefined && val !== null
    })
  const isLibTv = agent.endpoint === 'video-generate'
  const isVideoCover = agent.endpoint === 'video-cover'
  const isComplianceCheck = agent.endpoint === 'text-compliance-check'
  const isMediaWorkflow = agent.endpoint === 'video-to-script' || agent.endpoint === 'media-to-doc'

  const submit = (model, extraValues = {}) => {
    if (!isValid || disabled) return
    onSubmit({ ...values, ...extraValues }, model)
  }

  const handleDurationSelect = (duration) => {
    if (duration === '自定义') {
      setShowCustomDuration(true)
      setValues((v) => ({ ...v, duration: '' }))
    } else {
      setShowCustomDuration(false)
      setValues((v) => ({ ...v, duration }))
    }
  }

  const isDurationField = (field) => {
    return isLibTv && field.name === 'duration'
  }

  const handleFileUpload = async (field, file) => {
    if (!file) return
    const previewUrl = URL.createObjectURL(file)
    setUploadPreviews((p) => ({ ...p, [field.name]: previewUrl }))
    setUploadingFields((u) => ({ ...u, [field.name]: true }))
    try {
      const form = new FormData()
      form.append('file', file)
      const res = await fetch('/api/avatar/upload', { method: 'POST', body: form })
      const data = await res.json()
      if (!res.ok || data.error) throw new Error(data.error || '上传失败')
      setValues((v) => ({ ...v, [field.name]: data.url }))
    } catch (err) {
      alert('上传失败：' + err.message)
      setUploadPreviews((p) => ({ ...p, [field.name]: null }))
      setValues((v) => ({ ...v, [field.name]: '' }))
    } finally {
      setUploadingFields((u) => ({ ...u, [field.name]: false }))
    }
  }

  return (
    <form
      className="agent-form"
      onSubmit={(e) => {
        e.preventDefault()
        submit('chat')
      }}
    >
      <div className="form-fields">
        {(agent.fields || []).map((field) => {
          // 检查是否是布尔选项
          const hasBooleanOptions = field.options && field.options.length > 0 && typeof field.options[0].value === 'boolean'
          
          return (
            <div className="form-row" key={field.name}>
              <label className="form-label">
                {field.label}
                {field.required && <span className="required">*</span>}
              </label>
              {field.upload ? (
                <div className="upload-field">
                  <input
                    ref={(el) => { fileInputRefs.current[field.name] = el }}
                    type="file"
                    accept={field.accept || 'image/*'}
                    style={{ display: 'none' }}
                    disabled={disabled || uploadingFields[field.name]}
                    onChange={(e) => handleFileUpload(field, e.target.files?.[0])}
                  />
                  {uploadPreviews[field.name] ? (
                    <div className="upload-preview">
                      <img src={uploadPreviews[field.name]} alt="预览" className="upload-thumb" />
                      <div className="upload-preview-actions">
                        {uploadingFields[field.name] ? (
                          <span className="upload-status">上传中...</span>
                        ) : values[field.name] ? (
                          <span className="upload-status upload-ok">✓ 已上传</span>
                        ) : null}
                        <button
                          type="button"
                          className="btn-upload-change"
                          onClick={() => fileInputRefs.current[field.name]?.click()}
                          disabled={disabled || uploadingFields[field.name]}
                        >
                          重新选择
                        </button>
                        <button
                          type="button"
                          className="btn-upload-remove"
                          onClick={() => {
                            setUploadPreviews((p) => ({ ...p, [field.name]: null }))
                            setValues((v) => ({ ...v, [field.name]: '' }))
                            if (fileInputRefs.current[field.name]) fileInputRefs.current[field.name].value = ''
                          }}
                          disabled={disabled}
                        >
                          移除
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      className="btn-upload-trigger"
                      onClick={() => fileInputRefs.current[field.name]?.click()}
                      disabled={disabled}
                    >
                      点击上传图片
                    </button>
                  )}
                  {field.hint && <p className="upload-hint">{field.hint}</p>}
                </div>
              ) : hasBooleanOptions ? (
                <div className="boolean-selector">
                  {field.options.map((opt) => (
                    <label key={String(opt.value)} className="radio-label">
                      <input
                        type="radio"
                        name={field.name}
                        checked={values[field.name] === opt.value}
                        onChange={(e) =>
                          setValues((v) => ({ ...v, [field.name]: opt.value }))
                        }
                        disabled={disabled}
                      />
                      <span>{opt.label}</span>
                    </label>
                  ))}
                </div>
              ) : field.textarea ? (
                <textarea
                  className="form-input form-textarea"
                  rows={field.rows || 4}
                  placeholder={field.placeholder}
                  value={values[field.name] || ''}
                  onChange={(e) =>
                    setValues((v) => ({ ...v, [field.name]: e.target.value }))
                  }
                  disabled={disabled}
                />
              ) : field.options ? (
                <select
                  className="form-input"
                  value={values[field.name] || ''}
                  onChange={(e) =>
                    setValues((v) => ({ ...v, [field.name]: e.target.value }))
                  }
                  disabled={disabled}
                >
                  {field.options.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              ) : field.name === 'wordCount' ? (
                <input
                  className="form-input"
                  type="number"
                  min={1500}
                  max={4000}
                  placeholder={field.placeholder}
                  value={values[field.name] || ''}
                  onChange={(e) => {
                    const val = e.target.value
                    setValues((v) => ({ 
                      ...v, 
                      [field.name]: val ? Number(val) : '' 
                    }))
                  }}
                  disabled={disabled}
                />
              ) : isDurationField(field) ? (
              <div className="duration-selector">
                <div className="duration-buttons">
                  {DURATION_OPTIONS.map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      className={`duration-btn ${
                        values[field.name] === opt.value ? 'is-selected' : ''
                      }`}
                      onClick={() => handleDurationSelect(opt.value)}
                      disabled={disabled}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
                {showCustomDuration && (
                  <input
                    className="form-input duration-custom"
                    type="text"
                    placeholder="输入时长，如: 20秒 / 1分钟"
                    value={values[field.name] || ''}
                    onChange={(e) =>
                      setValues((v) => ({ ...v, [field.name]: e.target.value }))
                    }
                    disabled={disabled}
                    autoFocus
                  />
                )}
              </div>
            ) : (
              <input
                className="form-input"
                type="text"
                placeholder={field.placeholder}
                value={values[field.name] || ''}
                onChange={(e) =>
                  setValues((v) => ({ ...v, [field.name]: e.target.value }))
                }
                disabled={disabled}
              />
            )}
          </div>
        ))}
      </div>
      <div className="form-actions">
        {isLibTv ? (
          <button
            type="button"
            className="btn btn-accent"
            onClick={() => submit('libtv')}
            disabled={!isValid || disabled}
          >
            发送到 LibTV
          </button>
        ) : isComplianceCheck ? (
          <button
            type="button"
            className="btn btn-accent"
            onClick={() => submit('chat')}
            disabled={!isValid || disabled}
          >
            开始检测
          </button>
        ) : isMediaWorkflow ? (
          <button
            type="button"
            className="btn btn-accent"
            onClick={() => submit('chat')}
            disabled={!isValid || disabled}
          >
            {agent.id === 'video-rip' ? '解析链接并生成脚本' : '生成文档与脚本'}
          </button>
        ) : isVideoCover ? (
          <>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => submit('chat', { imageProvider: 'hiapi' })}
              disabled={!isValid || disabled}
            >
              HiAPI 生成封面
            </button>
            <button
              type="button"
              className="btn btn-dark"
              onClick={() => submit('chat', { imageProvider: 'seedream' })}
              disabled={!isValid || disabled}
            >
              Seedream 生成封面
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => submit('chat')}
              disabled={!isValid || disabled}
            >
              DeepSeek 原版
            </button>
            <button
              type="button"
              className="btn btn-accent"
              onClick={() => submit('rag-xhs')}
              disabled={!isValid || disabled}
            >
              DeepSeek (增强·RAG)
            </button>
          </>
        )}
      </div>
    </form>
  )
}
