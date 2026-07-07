import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import './LoginPage.css'

export default function LoginPage() {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)
    const { login, isAuthenticated } = useAuth()
    const navigate = useNavigate()

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        setError('')
        setLoading(true)

        try {
            await login(email, password)
            navigate('/tasks')
        } catch {
            setError('Не удалось войти. Проверьте email и пароль.')
        } finally {
            setLoading(false)
        }
    }

    if (isAuthenticated) {
        return <Navigate to="/tasks" replace />
    }

    return (
        <main className='login-page'>
            <form className="login-form" onSubmit={handleSubmit}>
                <header className="form-header">
                    <h1>Skill Tracker</h1>
                    <p>Войдите, чтобы продолжить</p>
                </header>
                <label htmlFor="email">Электронная почта</label>
                <input
                    id="email"
                    name="email"
                    type="email"
                    placeholder='name@example.com'
                    autoComplete="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                />
                <label htmlFor="password">Пароль</label>
                <input
                    id="password"
                    name="password"
                    type="password"
                    placeholder='Введите пароль'
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                />
                {error && <p className="form-error">{error}</p>}
                <button className="submit-button" type="submit" disabled={loading}>
                    {loading ? 'Входим...' : 'Продолжить'}
                </button>
            </form>
        </main>
    )
}
