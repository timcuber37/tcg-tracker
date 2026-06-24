import { useCallback, useEffect, useRef, useState } from 'react'
import HTMLFlipBook from 'react-pageflip'
import { api, type BinderResponse, type CollectionCardDto, cardImageUrl, onCardImageError } from '../lib/api'

const SLOTS_PER_PAGE = 9

// react-pageflip's prop types mark almost everything required; relax it.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const FlipBook: any = HTMLFlipBook

export default function Binder() {
  const [binder, setBinder] = useState<BinderResponse | null>(null)
  const [picker, setPicker] = useState<{ page: number; slot: number } | null>(null)
  const [owned, setOwned] = useState<CollectionCardDto[]>([])
  const [busy, setBusy] = useState(false)
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

  // The cover is the first leaf of the flip book (showCover), so it opens/closes
  // by dragging exactly like the inner pages; the nav buttons just delegate.
  const flipPrev = () => bookRef.current?.pageFlip()?.flipPrev()
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
                   onError={onCardImageError} />
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

  // No front cover — the binder opens straight to the first spread: a blank inside
  // cover (left) + page 1 (right), then 3x3 content pages, ending on a rigid back
  // cover. The back cover keeps data-density="hard" (page-flip reads rigidity from
  // that attribute on every (re)load, so it survives the React wrapper's
  // updateFromHtml on each re-render).
  const pages = [
    <div className="page page-inside" key="inside"><span className="page-corner" /></div>,
    ...Array.from({ length: pageCount }, (_, p) => (
      <div className="page" key={p}>{renderGrid(p)}<span className="page-corner" /></div>
    )),
    <div className="page page-back" data-density="hard" key="back">
      <div className="cover-inner"><div className="pokeball"><div className="pokeball-center" /></div></div>
    </div>,
  ]

  return (
    <div className="binder-wrap">
      <div className="binder-flip">
        {binder ? (
          <FlipBook
            ref={bookRef}
            width={420}
            height={580}
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
          >
            {pages}
          </FlipBook>
        ) : (
          <div className="empty-state">Loading binder…</div>
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
                           onError={onCardImageError} />
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
