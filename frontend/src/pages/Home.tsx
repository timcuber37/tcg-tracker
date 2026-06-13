import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api, type CardDto, type SearchResponse } from '../lib/api'
import { useAuth } from '../lib/auth'
import CardGrid from '../components/CardGrid'
import Pagination from '../components/Pagination'
import CardRain from '../components/CardRain'

export default function Home({ onSignIn }: { onSignIn: () => void }) {
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()
  const query = params.get('q') ?? ''
  const setName = params.get('set') ?? ''
  const page = Math.max(1, parseInt(params.get('page') ?? '1', 10) || 1)

  const [data, setData] = useState<SearchResponse | null>(null)
  const [loading, setLoading] = useState(false)

  const active = query !== '' || setName !== ''

  useEffect(() => {
    if (!active) { setData(null); return }
    setLoading(true)
    api.search(query, setName, page)
      .then(setData)
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [query, setName, page, active])

  const setPage = (p: number) => {
    const sp = new URLSearchParams(params)
    sp.set('page', String(p))
    setParams(sp)
  }

  const onAdd = user
    ? async (card: CardDto) => { await api.addFromSearch(card) }
    : undefined

  if (!active) {
    return (
      <>
        <CardRain />
        <div className="hero">
          <h1>Find any Pokémon card.</h1>
          <p>Search the modern catalog and build your collection.</p>
          {!user && (
            <button className="btn" style={{ marginTop: 20 }} onClick={onSignIn}>Sign in to start collecting</button>
          )}
        </div>
      </>
    )
  }

  return (
    <>
      <h1 style={{ fontSize: '1.2rem', fontWeight: 500, color: '#aaa' }}>
        {data ? data.total : '…'} result{data?.total === 1 ? '' : 's'}
        {query && ` for "${query}"`}
        {setName && ` in ${setName}`}
        {data && data.totalPages > 1 && <span style={{ color: '#666', fontSize: '0.9rem' }}> · page {data.page} of {data.totalPages}</span>}
      </h1>

      {loading && !data && <div className="empty-state">Searching…</div>}
      {data && data.results.length === 0 && <div className="empty-state">No cards found.</div>}
      {data && data.results.length > 0 && (
        <>
          <CardGrid cards={data.results} onAdd={onAdd} />
          <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />
        </>
      )}
    </>
  )
}
