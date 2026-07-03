import './LoginPage.css'

export default function LoginPage() {
    return (
        <main className='login-page'>
            <form action="" className="login-form">
                <header className="form-header">
                    <h1>Skill Tracker</h1>
                    <p>Войдите, чтобы продолжить</p>
                </header>
                <label htmlFor="email">Электронная почта</label>
                <input id="email" name="email" type="email" placeholder='name@example.com' autoComplete="email" />
                <label htmlFor="password">Пароль</label>
                <input id="password" name="password" type="password" placeholder='Введите пароль' autoComplete="current-password" />
                <button className="submit-button" type="submit">Продолжить</button>
            </form>
        </main>
    )
}

