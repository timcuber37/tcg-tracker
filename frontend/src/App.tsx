import { useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './lib/auth'
import TopBar from './components/TopBar'
import AuthModal from './components/AuthModal'
import Home from './pages/Home'
import Collection from './pages/Collection'
import Market from './pages/Market'

export default function App() {
  const [authOpen, setAuthOpen] = useState(false)

  return (
    <BrowserRouter>
      <AuthProvider>
        <TopBar onSignIn={() => setAuthOpen(true)} />
        <main className="container">
          <Routes>
            <Route path="/" element={<Home onSignIn={() => setAuthOpen(true)} />} />
            <Route path="/collection" element={<Collection />} />
            <Route path="/market" element={<Market />} />
          </Routes>
        </main>
        {authOpen && <AuthModal onClose={() => setAuthOpen(false)} />}
      </AuthProvider>
    </BrowserRouter>
  )
}
