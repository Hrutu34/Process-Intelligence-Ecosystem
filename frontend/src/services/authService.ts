import type { UserSession } from './types';

const STORAGE_KEY = 'pie_user_session';

const DEFAULT_DEMO_USER: UserSession = {
  id: 'user_admin_01',
  name: 'Alex Vance',
  email: 'admin@pie-ecosystem.io',
  role: 'Lead Enterprise Process Architect',
  isLoggedIn: true,
  avatarUrl: '👤',
};

class AuthService {
  private session: UserSession | null = null;

  constructor() {
    this.loadSession();
  }

  private loadSession() {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        this.session = JSON.parse(stored);
      }
    } catch {
      this.session = null;
    }
  }

  public getCurrentUser(): UserSession | null {
    if (!this.session) {
      this.loadSession();
    }
    return this.session && this.session.isLoggedIn ? this.session : null;
  }

  public isAuthenticated(): boolean {
    return this.getCurrentUser() !== null;
  }

  public login(identifier: string, password: string): Promise<UserSession> {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        const idClean = identifier.trim().toLowerCase();
        const pwClean = password.trim();

        // Support admin / admin or standard email logins
        if ((idClean === 'admin' && pwClean === 'admin') ||
            (idClean === 'admin@pie-ecosystem.io' && pwClean === 'admin') ||
            (idClean === 'demo' && pwClean === 'demo')) {
          const user: UserSession = {
            ...DEFAULT_DEMO_USER,
            email: idClean.includes('@') ? idClean : 'admin@pie-ecosystem.io',
            isLoggedIn: true,
          };
          this.session = user;
          localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
          resolve(user);
        } else {
          reject(new Error('Invalid credentials. Use ID: admin, Password: admin or click "Login as Demo User"'));
        }
      }, 300);
    });
  }

  public loginAsDemoUser(): UserSession {
    this.session = { ...DEFAULT_DEMO_USER, isLoggedIn: true };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(this.session));
    return this.session;
  }

  public logout(): void {
    this.session = null;
    localStorage.removeItem(STORAGE_KEY);
  }
}

export const authService = new AuthService();
