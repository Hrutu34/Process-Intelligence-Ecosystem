import React from 'react';
import type { UserSession } from '../services/types';
import { SettingsIcon, PieChartIcon, ZapIcon, DocumentIcon, BrainIcon, SearchIcon, UserIcon } from '../services/icons';
import './AppLayout.css';

export type AppPage = 'dashboard' | 'processes' | 'documents' | 'knowledge' | 'settings';

interface Props {
  currentPage: AppPage;
  onNavigate: (page: AppPage) => void;
  user: UserSession | null;
  onLogout: () => void;
  onOpenUploadModal: () => void;
  children: React.ReactNode;
}

export const AppLayout: React.FC<Props> = ({
  currentPage,
  onNavigate,
  user,
  onLogout,
  onOpenUploadModal,
  children,
}) => {
  return (
    <div className="layout-shell">
      {/* Left Navigation Sidebar */}
      <aside className="layout-sidebar">
        <button
          type="button"
          className="sidebar-brand"
          onClick={() => onNavigate('dashboard')}
          aria-label="Go to dashboard"
        >
          <div className="sidebar-logo-mark">π</div>
          <div>
            <div className="sidebar-brand-title">P.I.E. Workspace</div>
            <span style={{ fontSize: 11, color: 'var(--muted)', fontFamily: 'var(--mono)' }}>v0.2 HACKATHON</span>
          </div>
        </button>

        <nav className="sidebar-nav">
          <button
            type="button"
            className={`nav-item ${currentPage === 'dashboard' ? 'active' : ''}`}
            onClick={() => onNavigate('dashboard')}
          >
            <div className="nav-icon"><PieChartIcon /></div>
            <span>Dashboard</span>
          </button>

          <button
            type="button"
            className={`nav-item ${currentPage === 'processes' ? 'active' : ''}`}
            onClick={() => onNavigate('processes')}
          >
            <div className="nav-icon"><ZapIcon /></div>
            <span>Processes</span>
          </button>

          <button
            type="button"
            className={`nav-item ${currentPage === 'documents' ? 'active' : ''}`}
            onClick={() => onNavigate('documents')}
          >
            <div className="nav-icon"><DocumentIcon /></div>
            <span>Documents</span>
          </button>

          <button
            type="button"
            className={`nav-item ${currentPage === 'knowledge' ? 'active' : ''}`}
            onClick={() => onNavigate('knowledge')}
          >
            <div className="nav-icon"><BrainIcon /></div>
            <span>Knowledge Base</span>
          </button>

          <div className="sidebar-divider" />

          <div className="sidebar-section-title">Recent Activity</div>
          <div className="recent-activity-snippet">
            <strong>Travel_Process_SOP.pdf</strong>
            <span>Knowledge extracted & 12 entities validated</span>
          </div>
          <div className="recent-activity-snippet">
            <strong>EV_Assembly_Parallel_SOP.txt</strong>
            <span>Parallel fork/join BPMN generated</span>
          </div>

          <div className="sidebar-divider" />

           <button
             type="button"
             className={`nav-item ${currentPage === 'settings' ? 'active' : ''}`}
             onClick={() => onNavigate('settings')}
           >
             <div className="nav-icon"><SettingsIcon /></div>
             <span>Settings</span>
           </button>
        </nav>

        <div className="sidebar-footer">
          <button
            type="button"
            className="yellow-button"
            style={{ width: '100%', justifyContent: 'center' }}
            onClick={onOpenUploadModal}
          >
            + INGEST DOCUMENT
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="layout-main-content">
        {/* Top Header */}
         <header className="layout-top-bar">
           <div className="top-bar-search">
             <div className="search-icon"><SearchIcon /></div>
             <input type="text" placeholder="Search processes, documents, actors..." />
           </div>

           <div className="top-bar-actions">
             <div className="status-pill">
               <span className="status-dot" />
               BACKEND CONNECTED
             </div>

             {user && (
               <div className="user-profile-badge">
                 <div className="user-avatar"><UserIcon /></div>
                 <span className="user-name">{user.name}</span>
                 <button
                   type="button"
                   style={{ background: 'transparent', border: 'none', color: 'var(--muted)', fontSize: 11, cursor: 'pointer', marginLeft: 6 }}
                   onClick={onLogout}
                   title="Sign out"
                 >
                   (Sign Out)
                 </button>
               </div>
             )}
           </div>
         </header>

        {/* Dynamic Page Content */}
        <main className="page-body-container">{children}</main>
      </div>
    </div>
  );
};
