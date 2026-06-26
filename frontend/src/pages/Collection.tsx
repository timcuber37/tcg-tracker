import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api, type CollectionResponse, cardImageUrl, onCardImageError } from '../lib/api'
import { useAuth } from '../lib/auth'
import Binder from '../components/Binder'

export default function Collection() {
  const { user, username, loading: authLoading } = useAuth()
  const [view, setView] = useState<'grid' | 'binder'>('grid')
  const [sortKey, setSortKey] = useState<'name' | 'value'>('name')
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')
  const [data, setData] = useState<CollectionResponse | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api.collection().then(setData).catch(() => setData(null))
  }, [])

  useEffect(() => { if (user) load() }, [user, load])

  // Writes propagate through Kafka → Cassandra, so refetch after a short delay.
  const mutate = async (fn: () => Promise<unknown>) => {
    setBusy(true)
    try {
      await fn()
      await new Promise((r) => setTimeout(r, 900))
      load()
    } finally {
      setBusy(false)
    }
  }

  if (authLoading) return <div className="empty-state">…</div>
  if (!user) return <Navigate to="/" replace />

  const cards = data?.cards ?? []

  // Clicking the active field flips direction; switching fields picks a sensible
  // default (Name A→Z, Value high→low).
  const setSort = (key: 'name' | 'value') => {
    if (sortKey === key) setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    else { setSortKey(key); setSortDir(key === 'value' ? 'desc' : 'asc') }
  }

  const sortedCards = [...cards].sort((a, b) => {
    if (sortKey === 'value') {
      const av = a.marketPriceUsd, bv = b.marketPriceUsd
      // Unpriced cards always sort to the end, regardless of direction.
      if (av == null && bv == null) return a.cardName.localeCompare(b.cardName)
      if (av == null) return 1
      if (bv == null) return -1
      return sortDir === 'asc' ? av - bv : bv - av
    }
    const cmp = a.cardName.localeCompare(b.cardName)
    return sortDir === 'asc' ? cmp : -cmp
  })

  const arrow = (key: 'name' | 'value') => (sortKey === key ? (sortDir === 'asc' ? ' ↑' : ' ↓') : '')

  return (
    <>
      <div className="collection-head">
        <h1 style={{ margin: 0 }}>{username}'s Collection</h1>
        {view === 'grid' && data && cards.length > 0 && (
          <div className="collection-total">
            <div className="label">Total value</div>
            <div className="value">${data.totalValue.toFixed(2)}</div>
            <div className="sub">
              {data.pricedCopies} of {data.totalCopies} cop{data.totalCopies === 1 ? 'y' : 'ies'} priced · {cards.length} unique
            </div>
          </div>
        )}
      </div>

      <div className="collection-controls">
        <div className="controls-left">
          <div className="view-toggle">
            <button className={view === 'grid' ? 'active' : ''} onClick={() => setView('grid')}>Collection</button>
            <button className={view === 'binder' ? 'active' : ''} onClick={() => setView('binder')}>Binder</button>
          </div>
          <Link to="/scan" className="btn btn-secondary scan-link">Scan a card</Link>
        </div>

        {view === 'grid' && cards.length > 1 && (
          <div className="sort-group">
            <span className="sort-label">Sort</span>
            <div className="view-toggle">
              <button className={sortKey === 'name' ? 'active' : ''} onClick={() => setSort('name')}>Name{arrow('name')}</button>
              <button className={sortKey === 'value' ? 'active' : ''} onClick={() => setSort('value')}>Value{arrow('value')}</button>
            </div>
          </div>
        )}
      </div>

      {view === 'binder' && <Binder />}

      {view === 'grid' && !data && <div className="empty-state">Loading…</div>}
      {view === 'grid' && data && cards.length === 0 && (
        <div className="empty-state">Your collection is empty. Search for cards to add.</div>
      )}

      {view === 'grid' && cards.length > 0 && (
        <div className="card-grid">
          {sortedCards.map((c) => (
            <div className="card" key={c.cardId}>
              <img src={cardImageUrl(c.cardId)} alt={c.cardName} loading="lazy"
                   onError={onCardImageError} />
              <div className="card-name">{c.cardName}</div>
              <div className="card-meta">{c.setName}</div>
              <span className="badge">{c.rarity}</span>
              {c.marketPriceUsd != null && (
                <div className="price">
                  ${c.marketPriceUsd.toFixed(2)}
                  {c.count > 1 && (
                    <span style={{ color: '#888', fontWeight: 400, fontSize: '0.85rem' }}>
                      {' '}× {c.count} = ${(c.marketPriceUsd * c.count).toFixed(2)}
                    </span>
                  )}
                </div>
              )}
              <div className="card-meta" style={{ marginTop: 6 }}>{c.condition}</div>

              <div className="qty-row">
                <button className="btn btn-secondary qty-btn" title="Remove one copy" disabled={busy}
                        onClick={() => mutate(() => api.removeCard(c.collectionIds[0]))}>−</button>
                <div className="qty">{c.count}</div>
                <button className="btn qty-btn" title="Add another copy" disabled={busy}
                        onClick={() => mutate(() => api.addCopy(c.cardId, c.condition))}>+</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  )
}
