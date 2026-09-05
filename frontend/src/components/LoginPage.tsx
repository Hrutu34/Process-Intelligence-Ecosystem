import React, { useState } from 'react';
import { authService } from '../services/authService';
import type { UserSession } from '../services/types';
import './LoginPage.css';

interface Props {
  onLoginSuccess: (user: UserSession) => void;
  onBackToLanding: () => void;
}

export const LoginPage: React.FC<Props> = ({ onLoginSuccess, onBackToLanding }) => {
  const [identifier, setIdentifier] = useState('admin');
  const [password, setPassword] = useState('admin');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const user = await authService.login(identifier, password);
      onLoginSuccess(user);
    } catch (err: any) {
      setError(err.message || 'Login failed. Please check credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleDemoLogin = () => {
    const user = authService.loginAsDemoUser();
    onLoginSuccess(user);
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-brand">
          <div className="login-logo-mark">π</div>
          <div>
            <h2>Sign In to P.I.E.</h2>
          </div>
        </div>

        <p className="login-tagline">
          Enter your credentials or use instant demo mode to access your Process Intelligence workspace.
        </p>

        {error && <div className="login-error">{error}</div>}

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="login-field">
            <label>Email or Username</label>
            <input
              type="text"
              className="login-input"
              value={identifier}
              onChange={(e) => setIdentifier(e.target.value)}
              placeholder="e.g. admin"
              required
            />
          </div>

          <div className="login-field">
            <label>Password</label>
            <input
              type="password"
              className="login-input"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
            />
          </div>

          <button
            type="submit"
            className="yellow-button"
            style={{ width: '100%', justifyContent: 'center', marginTop: 4 }}
            disabled={loading}
          >
            {loading ? 'SIGNING IN...' : 'SIGN IN'} <span>↗</span>
          </button>
        </form>

        <div className="login-divider">OR</div>

        <button
          type="button"
          className="btn-ghost"
          style={{ width: '100%', padding: '12px', fontSize: '13px', color: 'var(--aqua)' }}
          onClick={handleDemoLogin}
        >
          🚀 Login as Demo User (One-Click)
        </button>

        <div className="login-demo-box">
          Hackathon Quick Access: ID: <strong>admin</strong> | PW: <strong>admin</strong>
        </div>

        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <button
            type="button"
            style={{ background: 'transparent', border: 'none', color: 'var(--muted)', fontSize: 12, cursor: 'pointer' }}
            onClick={onBackToLanding}
          >
            ← Back to Landing Page
          </button>
        </div>
      </div>
    </div>
  );
};
