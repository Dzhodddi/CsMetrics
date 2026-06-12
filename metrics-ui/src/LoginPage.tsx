import React, { useState } from 'react';
import { api } from './api';

interface LoginPageProps {
    onLogin: (token: string) => void;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onLogin }) => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        try {
            const token = await api.login(username, password);
            onLogin(token);
        } catch (error) {
            alert("Помилка авторизації");
        }
        setLoading(false);
    };

    return (
        <div className="login-container">
            <form onSubmit={handleSubmit} className="login-form">
                <h2>Вхід у систему</h2>
                <input
                    type="text"
                    placeholder="Логін"
                    value={username}
                    onChange={e => setUsername(e.target.value)}
                    required
                />
                <input
                    type="password"
                    placeholder="Пароль"
                    value={password}
                    onChange={e => setPassword(e.target.value)}
                    required
                />
                <button type="submit" disabled={loading}>
                    {loading ? 'Завантаження...' : 'Увійти'}
                </button>
            </form>
        </div>
    );
};