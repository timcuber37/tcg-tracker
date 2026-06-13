export default function Pagination({
  page, totalPages, onPage,
}: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null

  const start = Math.max(1, page - 2)
  const end = Math.min(totalPages, page + 2)
  const window: number[] = []
  for (let p = start; p <= end; p++) window.push(p)

  return (
    <nav className="pagination">
      <button className="btn btn-secondary" disabled={page <= 1} onClick={() => onPage(page - 1)}>← Prev</button>

      {start > 1 && (
        <>
          <button className="btn btn-secondary" onClick={() => onPage(1)}>1</button>
          {start > 2 && <span style={{ color: '#666' }}>…</span>}
        </>
      )}
      {window.map((p) =>
        p === page
          ? <span key={p} className="btn">{p}</span>
          : <button key={p} className="btn btn-secondary" onClick={() => onPage(p)}>{p}</button>
      )}
      {end < totalPages && (
        <>
          {end < totalPages - 1 && <span style={{ color: '#666' }}>…</span>}
          <button className="btn btn-secondary" onClick={() => onPage(totalPages)}>{totalPages}</button>
        </>
      )}

      <button className="btn btn-secondary" disabled={page >= totalPages} onClick={() => onPage(page + 1)}>Next →</button>
    </nav>
  )
}
