import { useCallback, useEffect, useRef, useState } from 'react'
import HTMLFlipBook from 'react-pageflip'
import { api, type BinderResponse, type CollectionCardDto, cardImageUrl } from '../lib/api'

const SLOTS_PER_PAGE = 9

// react-pageflip's prop types mark almost everything required; relax it.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const FlipBook: any = HTMLFlipBook

export default function Binder({ ownerName }: { ownerName?: string }) {
  const [binder, setBinder] = useState<BinderResponse | null>(null)
  const [picker, setPicker] = useState<{ page: number; slot: number } | null>(null)
  const [owned, setOwned] = useState<CollectionCardDto[]>([])
  const [busy, setBusy] = useState(false)
  // Cover is its own screen (NOT a flip-book page) — using react-pageflip's
  // showCover forces hard cover leaves that render a stray page outside the binder.
  const [coverOpen, setCoverOpen] = useState(false)
  const [transitioning, setTransitioning] = useState<'open' | 'close' | null>(null)
  const [currentPage, setCurrentPage] = useState(0)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const bookRef = useRef<any>(null)

  const load = useCallback(() => {
    api.binder().then(setBinder).catch(() => setBinder(null))
  }, [])

  useEffect(() => { load() }, [load])

  const mutate = async (fn: () => Promise<unknown>) => {
    setBusy(true)
    try { await fn() } catch { /* ignore (e.g. no available copy) */ }
    await new Promise((r) => setTimeout(r, 900))
    load()
    setBusy(false)
  }

  const placedCount = (cardId: string) => binder?.slots.filter((s) => s.cardId === cardId).length ?? 0
  const availableOf = (c: CollectionCardDto) => c.count - placedCount(c.cardId)

  const openPicker = (page: number, slot: number) => {
    setPicker({ page, slot })
    api.collection().then((c) => setOwned(c.cards)).catch(() => setOwned([]))
  }

  const placeCard = (cardId: string) => {
    const target = picker
    setPicker(null)
    if (!target) return
    mutate(() => api.placeCard(cardId, target.page, target.slot))
  }

  const slotAt = (page: number, i: number) =>
    binder?.slots.find((s) => s.pageNumber === page && s.slotIndex === i) ?? null

  // Open: mount the book immediately and crossfade it in while the cover flips
  // away on top (no empty gap, no pop).
  const openCover = () => {
    setCoverOpen(true)
    setTransitioning('open')
    setTimeout(() => setTransitioning(null), 560)
  }
  // Going back past the first spread closes the binder: the book fades out while
  // the cover flips back in over it (mirror of opening).
  const flipPrev = () => {
    if (currentPage <= 0) {
      setTransitioning('close')
      setTimeout(() => { setCoverOpen(false); setTransitioning(null) }, 560)
    } else {
      bookRef.current?.pageFlip()?.flipPrev()
    }
  }
  const flipNext = () => bookRef.current?.pageFlip()?.flipNext()

  // Render enough content pages to cover what's used plus one blank page to fill.
  const usedPages = binder?.pageCount ?? 0
  const pageCount = Math.max(usedPages + 1, 1)

  const renderGrid = (page: number) => (
    <div className="page-grid">
      {Array.from({ length: SLOTS_PER_PAGE }, (_, i) => {
        const slot = slotAt(page, i)
        if (slot) {
          return (
            <div className="pocket" key={i}>
              <img src={cardImageUrl(slot.cardId)} alt={slot.cardName} loading="lazy"
                   onError={(e) => { (e.target as HTMLImageElement).style.visibility = 'hidden' }} />
              <button className="slot-remove" title="Remove from binder" disabled={busy}
                      onClick={(e) => { e.stopPropagation(); mutate(() => api.removeSlot(page, i)) }}>×</button>
            </div>
          )
        }
        return (
          <div className="pocket empty" key={i} title="Add a card"
               onClick={(e) => { e.stopPropagation(); if (!busy) openPicker(page, i) }}>+</div>
        )
      })}
    </div>
  )

  // No cover/back-cover leaves and no showCover — only soft pages. First spread is
  // the blank inside (left) + page 0 (right), then content pages pair up.
  const pages = [
    <div className="page page-inside" key="inside"><span className="page-corner" /></div>,
    ...Array.from({ length: pageCount }, (_, p) => (
      <div className="page" key={p}>{renderGrid(p)}<span className="page-corner" /></div>
    )),
  ]

  const coverFace = (
    <div className="cover-inner">
      <div className="pokeball"><div className="pokeball-center" /></div>
      <div className="cover-title">{ownerName ? `${ownerName}'s Binder` : 'My Binder'}</div>
      <div className="cover-hint">Click to open →</div>
    </div>
  )

  // ---- Closed cover screen (only when fully closed, not mid-transition) ----
  if (!coverOpen && !transitioning) {
    return (
      <div className="binder-wrap">
        <div className="cover-stage">
          <div className="closed-cover" onClick={openCover}>{coverFace}</div>
        </div>
      </div>
    )
  }

  // ---- Open binder (flip book) with the cover crossfading over it on transitions ----
  const bookAnim = transitioning === 'open' ? 'revealing' : transitioning === 'close' ? 'hiding' : ''
  return (
    <div className="binder-wrap">
      <div className={`binder-flip ${bookAnim}`}>
        {binder ? (
          <FlipBook
            ref={bookRef}
            width={340}
            height={470}
            size="fixed"
            showCover={false}
            usePortrait={false}
            drawShadow={false}
            maxShadowOpacity={0}
            flippingTime={700}
            useMouseEvents={true}
            clickEventForward={true}
            disableFlipByClick={true}
            showPageCorners={false}
            mobileScrollSupport={false}
            className="binder-book"
            onFlip={(e: { data: number }) => setCurrentPage(e.data)}
          >
            {pages}
          </FlipBook>
        ) : (
          <div className="empty-state">Loading binder…</div>
        )}

        {transitioning && (
          <div className="cover-overlay">
            <div className={`closed-cover ${transitioning === 'open' ? 'flipping' : 'closing'}`}>{coverFace}</div>
          </div>
        )}
      </div>

      <div className="binder-nav">
        <button className="btn btn-secondary" onClick={flipPrev}>←</button>
        <button className="btn btn-secondary" onClick={flipNext}>→</button>
      </div>

      {picker !== null && (
        <div className="picker-overlay" onClick={(e) => { if (e.target === e.currentTarget) setPicker(null) }}>
          <div className="picker-dialog">
            <h2>Choose a card</h2>
            {(() => {
              const avail = owned.filter((c) => availableOf(c) > 0)
              if (owned.length === 0) return <div className="empty-state">No cards in your collection yet.</div>
              if (avail.length === 0) return <div className="empty-state">All your copies are already in the binder.</div>
              return (
                <div className="picker-grid">
                  {avail.map((c) => (
                    <div className="picker-card" key={c.cardId} onClick={() => placeCard(c.cardId)}>
                      <img src={cardImageUrl(c.cardId)} alt={c.cardName} loading="lazy"
                           onError={(e) => { (e.target as HTMLImageElement).style.visibility = 'hidden' }} />
                      <div className="nm">{c.cardName}</div>
                      <div className="avail">{availableOf(c)} available</div>
                    </div>
                  ))}
                </div>
              )
            })()}
          </div>
        </div>
      )}
    </div>
  )
}
