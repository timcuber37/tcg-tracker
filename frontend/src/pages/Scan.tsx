import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api, cardImageUrl, onCardImageError, type CardDto, type ScanResponse } from '../lib/api'
import { useAuth } from '../lib/auth'

type Status = 'starting' | 'live' | 'scanning' | 'results' | 'error'

function scanErrorMessage(e: unknown): string {
  const msg = e instanceof Error ? e.message : ''
  if (msg.includes('502')) return "Scanning isn't available yet — the server's Vision API key isn't configured."
  if (msg.includes('401')) return 'Your session expired. Please sign in again.'
  if (msg.includes('400')) return 'No image was captured — try again.'
  return 'Scan failed. Check your connection and try again.'
}

export default function Scan() {
  const { user, loading: authLoading } = useAuth()
  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [status, setStatus] = useState<Status>('starting')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<ScanResponse | null>(null)
  const [added, setAdded] = useState<Record<string, 'busy' | 'done'>>({})

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
  }, [])

  const startCamera = useCallback(async () => {
    setError(null)
    setStatus('starting')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 1280 }, height: { ideal: 720 } },
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play().catch(() => {})
      }
      setStatus('live')
    } catch {
      setError('Could not access the webcam. Allow camera access in your browser and make sure one is connected.')
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    if (!user) return
    startCamera()
    return () => stopCamera()
  }, [user, startCamera, stopCamera])

  const capture = useCallback(async () => {
    const video = videoRef.current
    if (!video || !video.videoWidth) return
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, 'image/jpeg', 0.92))
    if (!blob) return

    setStatus('scanning')
    setError(null)
    setResult(null)
    setAdded({})
    try {
      const resp = await api.scan(blob)
      setResult(resp)
      setStatus('results')
    } catch (e) {
      setError(scanErrorMessage(e))
      setStatus('error')
    }
  }, [])

  const addCard = useCallback(async (card: CardDto) => {
    setAdded((a) => ({ ...a, [card.pokewalletId]: 'busy' }))
    try {
      await api.addFromSearch(card)
      setAdded((a) => ({ ...a, [card.pokewalletId]: 'done' }))
    } catch {
      setAdded((a) => {
        const next = { ...a }
        delete next[card.pokewalletId]
        return next
      })
    }
  }, [])

  const scanAnother = () => {
    setResult(null)
    setError(null)
    setStatus('live')
  }

  if (authLoading) return <div className="empty-state">…</div>
  if (!user) return <Navigate to="/" replace />

  const cameraFailed = status === 'error' && !result && !streamRef.current
  const parsed = result?.parsed

  return (
    <div className="scan-page">
      <div className="scan-head">
        <h1 style={{ margin: 0 }}>Scan a Card</h1>
        <Link to="/collection" className="btn btn-secondary">← Back to collection</Link>
      </div>
      <p className="scan-hint">Hold the card flat inside the frame, fill it as much as possible, and avoid glare. Then capture.</p>

      <div className="scan-stage">
        <video ref={videoRef} className="scan-video" autoPlay muted playsInline />
        {(status === 'live' || status === 'scanning') && <div className="scan-guide" />}
        {status === 'scanning' && <div className="scan-overlay-msg">Scanning…</div>}
        {cameraFailed && <div className="scan-overlay-msg error">Camera unavailable</div>}
      </div>

      <div className="scan-actions">
        {cameraFailed ? (
          <button className="btn" onClick={startCamera}>Retry camera</button>
        ) : status === 'results' || (status === 'error' && result) ? (
          <button className="btn" onClick={scanAnother}>Scan another</button>
        ) : (
          <button className="btn" onClick={capture} disabled={status !== 'live'}>
            {status === 'scanning' ? 'Scanning…' : 'Capture & Scan'}
          </button>
        )}
      </div>

      {error && <div className="empty-state scan-error">{error}</div>}

      {result && (
        <div className="scan-results">
          <div className="scan-parsed">
            Read: <strong>{parsed?.name || '—'}</strong>
            {parsed?.collectorNumber ? <span> · #{parsed.collectorNumber}</span> : null}
          </div>

          {result.candidates.length === 0 ? (
            <div className="empty-state">No match found — reposition the card, improve lighting, and scan again.</div>
          ) : (
            <div className="scan-candidates">
              {result.candidates.map(({ card, confidence }) => {
                const state = added[card.pokewalletId]
                return (
                  <div className="scan-candidate" key={card.pokewalletId}>
                    <span className="conf">{Math.round(confidence * 100)}% match</span>
                    <img src={cardImageUrl(card.pokewalletId)} alt={card.cardName} loading="lazy" onError={onCardImageError} />
                    <div className="nm">{card.cardName}</div>
                    <div className="set">{card.setName}</div>
                    <button
                      className={`btn ${state === 'done' ? 'btn-secondary' : ''}`}
                      disabled={state === 'busy' || state === 'done'}
                      onClick={() => addCard(card)}
                    >
                      {state === 'done' ? 'Added ✓' : state === 'busy' ? 'Adding…' : 'Add to collection'}
                    </button>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
