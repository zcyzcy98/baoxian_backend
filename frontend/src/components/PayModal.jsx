import { useState, useEffect, useRef } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { getToken, fetchMe } from '../auth'
import './PayModal.css'

export default function PayModal({ onClose, onSuccess }) {
  const [state, setState] = useState('loading') // loading | qr | paid | error
  const [codeUrl, setCodeUrl] = useState('')
  const [amountYuan, setAmountYuan] = useState('')
  const [errorMsg, setErrorMsg] = useState('')
  const pollRef = useRef(null)

  useEffect(() => {
    createOrder()
    return () => clearInterval(pollRef.current)
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function createOrder() {
    setState('loading')
    setErrorMsg('')
    const token = getToken()
    try {
      const res = await fetch('/api/pay/membership-order', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || '下单失败')
      setCodeUrl(data.codeUrl)
      setAmountYuan((parseInt(data.amountFen) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2 }))
      setState('qr')
      startPolling(data.outTradeNo)
    } catch (e) {
      setErrorMsg(e.message)
      setState('error')
    }
  }

  function startPolling(tradeNo) {
    const token = getToken()
    pollRef.current = setInterval(async () => {
      try {
        const res = await fetch(`/api/pay/status/${tradeNo}`, {
          headers: { Authorization: `Bearer ${token}` },
        })
        const data = await res.json()
        if (data.status === 'PAID') {
          clearInterval(pollRef.current)
          setState('paid')
          const me = await fetchMe()
          if (me) setTimeout(() => onSuccess(me), 1200)
        }
      } catch (_) { /* ignore poll errors */ }
    }, 2000)
  }

  return (
    <div className="pay-backdrop" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="pay-modal">
        <button className="pay-close" onClick={onClose} aria-label="关闭">×</button>

        {state === 'loading' && (
          <div className="pay-center">
            <div className="pay-spinner" />
            <p>正在生成支付码…</p>
          </div>
        )}

        {state === 'qr' && (
          <>
            <div className="pay-header">
              <h3>开通年度会员</h3>
              <p className="pay-sub">扫码后自动跳转工作台</p>
            </div>
            <div className="pay-amount">
              <span className="pay-currency">¥</span>
              <span className="pay-price">{amountYuan}</span>
              <span className="pay-unit">/ 年</span>
            </div>
            <div className="pay-qr-wrap">
              <QRCodeSVG value={codeUrl} size={188} bgColor="#FAF9F5" fgColor="#1F1F1E" />
            </div>
            <p className="pay-hint">请用微信扫描上方二维码</p>
          </>
        )}

        {state === 'paid' && (
          <div className="pay-center pay-success">
            <div className="pay-check">✓</div>
            <h3>支付成功</h3>
            <p>会员权限已开通，正在跳转…</p>
          </div>
        )}

        {state === 'error' && (
          <div className="pay-center">
            <p className="pay-error">{errorMsg}</p>
            <button className="btn btn-primary" onClick={createOrder}>重新生成</button>
          </div>
        )}
      </div>
    </div>
  )
}
