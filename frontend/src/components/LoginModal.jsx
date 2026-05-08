import { useState, useEffect, useRef } from 'react'
import { sendCode, verifyCode, setToken } from '../auth'
import './LoginModal.css'

export default function LoginModal({ onSuccess, onClose }) {
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [step, setStep] = useState('phone') // 'phone' | 'code'
  const [countdown, setCountdown] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [devCode, setDevCode] = useState('')
  const timerRef = useRef(null)

  useEffect(() => {
    return () => clearInterval(timerRef.current)
  }, [])

  function startCountdown() {
    setCountdown(60)
    timerRef.current = setInterval(() => {
      setCountdown(n => {
        if (n <= 1) {
          clearInterval(timerRef.current)
          return 0
        }
        return n - 1
      })
    }, 1000)
  }

  async function handleSendCode() {
    setError('')
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      setError('请输入正确的手机号')
      return
    }
    setLoading(true)
    try {
      const data = await sendCode(phone)
      setStep('code')
      startCountdown()
      if (data._devCode) setDevCode(data._devCode)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleVerify() {
    setError('')
    if (code.length !== 6) {
      setError('请输入6位验证码')
      return
    }
    setLoading(true)
    try {
      const data = await verifyCode(phone, code)
      setToken(data.token)
      onSuccess({ phone: data.phone, hasAccess: data.hasAccess })
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function handleResend() {
    if (countdown > 0) return
    setError('')
    setDevCode('')
    setLoading(true)
    try {
      const data = await sendCode(phone)
      startCountdown()
      if (data._devCode) setDevCode(data._devCode)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="lm-overlay" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="lm-card">
        <button className="lm-close" onClick={onClose}>✕</button>

        <div className="lm-logo">承</div>
        <h2 className="lm-title">登录 / 注册</h2>
        <p className="lm-sub">保险经纪人专属 · 全域 AI 获客系统</p>

        {step === 'phone' ? (
          <>
            <div className="lm-field">
              <span className="lm-prefix">+86</span>
              <input
                className="lm-input"
                type="tel"
                placeholder="请输入手机号"
                value={phone}
                maxLength={11}
                onChange={e => setPhone(e.target.value.replace(/\D/g, ''))}
                onKeyDown={e => e.key === 'Enter' && handleSendCode()}
              />
            </div>
            {error && <p className="lm-error">{error}</p>}
            <button className="lm-btn" onClick={handleSendCode} disabled={loading}>
              {loading ? '发送中…' : '获取验证码'}
            </button>
          </>
        ) : (
          <>
            <p className="lm-phone-hint">验证码已发送至 {phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')}</p>
            {devCode && (
              <div className="lm-dev-hint">🛠 开发模式验证码：<strong>{devCode}</strong></div>
            )}
            <div className="lm-field">
              <input
                className="lm-input lm-input-full"
                type="text"
                placeholder="请输入6位验证码"
                value={code}
                maxLength={6}
                onChange={e => setCode(e.target.value.replace(/\D/g, ''))}
                onKeyDown={e => e.key === 'Enter' && handleVerify()}
                autoFocus
              />
            </div>
            {error && <p className="lm-error">{error}</p>}
            <button className="lm-btn" onClick={handleVerify} disabled={loading}>
              {loading ? '验证中…' : '登录'}
            </button>
            <button
              className="lm-resend"
              onClick={handleResend}
              disabled={countdown > 0 || loading}
            >
              {countdown > 0 ? `重新发送 (${countdown}s)` : '重新发送验证码'}
            </button>
          </>
        )}

        <p className="lm-terms">登录即表示同意《服务协议》和《隐私政策》</p>
      </div>
    </div>
  )
}
