import { useEffect, useState } from 'react'
import { api, type CardDto } from '../lib/api'
import { useAuth } from '../lib/auth'
import CardGrid from '../components/CardGrid'

export default function Market() {
  const { user } = useAuth()
  const [sets, setSets] = useState<string[]>([])
  const [selected, setSelected] = useState('')
  const [cards, setCards] = useState<CardDto[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    api.marketSets().then((s) => {
      setSets(s)
      if (s.length) setSelected(s[0])
    }).catch(() => setSets([]))
  }, [])

  useEffect(() => {
    if (!selected) { setCards([]); return }
    setLoading(true)
    api.market(selected).then(setCards).catch(() => setCards([])).finally(() => setLoading(false))
  }, [selected])

  const onAdd = user ? async (card: CardDto) => { await api.addFromSearch(card) } : undefined

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
        <h1 style={{ margin: 0 }}>Browse by set</h1>
        <select className="set-select" style={{ borderRight: 'none', maxWidth: 320 }}
                value={selected} onChange={(e) => setSelected(e.target.value)}>
          {sets.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>

      {loading && <div className="empty-state">Loading…</div>}
      {!loading && cards.length === 0 && <div className="empty-state">No cards in this set.</div>}
      {!loading && cards.length > 0 && <CardGrid cards={cards} onAdd={onAdd} />}
    </>
  )
}
