import React, { useState } from 'react';
import { LoginPage } from './LoginPage';
import { Dashboard } from './Dashboard';
import './index.css';

export const App: React.FC = () => {
    const [token, setToken] = useState<string | null>(null);

    return (
        <div className="app-root">
            {!token ? (
                <LoginPage onLogin={setToken} />
            ) : (
                <Dashboard token={token} onLogout={() => setToken(null)} />
            )}
        </div>
    );
};

export default App;