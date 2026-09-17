import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

// --- Fallback Mock Pipeline Interceptor ---
const originalFetch = window.fetch;

function showFallbackToast(reason: string) {
    const toast = document.createElement('div');
    toast.className = 'fallback-toast';
    toast.innerHTML = `
      <div style="display: flex; align-items: center; gap: 12px;">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fbd437" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink: 0;">
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3.04h16.94a2 2 0 0 0 1.71-3.04l-8.47-14.14a2 2 0 0 0-3.42 0z"/>
          <line x1="12" y1="9" x2="12" y2="13"/>
          <line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        <div>
          <strong style="color: #fbd437; font-size: 14px; display: block; margin-bottom: 4px;">DEMO MODE FALLBACK</strong>
          <span style="font-size: 13px; color: var(--soft-white);">AI Service failed (${reason}). Loaded pre-defined sample.</span>
        </div>
      </div>
    `;
    document.body.appendChild(toast);
    
    // Add CSS for toast dynamically if it doesn't exist
    if (!document.getElementById('toast-styles')) {
        const style = document.createElement('style');
        style.id = 'toast-styles';
        style.innerHTML = `
          .fallback-toast {
            position: fixed;
            bottom: 24px;
            right: 24px;
            background: var(--card-bg, #1a1a1a);
            border: 1px solid rgba(251, 212, 55, 0.3);
            border-left: 4px solid #fbd437;
            padding: 16px 20px;
            border-radius: 8px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            z-index: 999999;
            animation: slideIn 0.3s ease-out forwards;
            transition: opacity 0.5s, transform 0.5s;
          }
          .fallback-toast.fade-out {
            opacity: 0;
            transform: translateY(20px);
          }
          @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
          }
        `;
        document.head.appendChild(style);
    }

    setTimeout(() => {
        toast.classList.add('fade-out');
        setTimeout(() => toast.remove(), 500);
    }, 5000);
}

window.fetch = async (...args) => {
    const res = await originalFetch(...args);
    
    // Check if response is ok and we can clone it
    if (res.ok) {
        const contentType = res.headers.get("content-type") || "";
        
        // Handle JSON fallbacks
        if (contentType.includes("application/json")) {
            const cloned = res.clone();
            try {
                const data = await cloned.json();
                if (data && data.fallbackUsed) {
                    showFallbackToast(data.reason || 'UNKNOWN_ERROR');
                    // Return a fake response containing just the fallback data payload
                    return new Response(JSON.stringify(data.data), {
                        status: 200,
                        headers: res.headers,
                        statusText: res.statusText
                    });
                }
            } catch (e) {
                // Ignore parse errors, just return original
            }
        }
        
        // Handle XML (BPMN) fallbacks
        if (contentType.includes("application/xml") || contentType.includes("text/xml")) {
            const cloned = res.clone();
            try {
                const text = await cloned.text();
                if (text.includes("<!-- FALLBACK_USED:")) {
                    const match = text.match(/<!-- FALLBACK_USED: (.*?) -->/);
                    const reason = match ? match[1] : 'BPMN_GENERATION_FAILED';
                    showFallbackToast(reason);
                    return new Response(text, {
                        status: 200,
                        headers: res.headers,
                        statusText: res.statusText
                    });
                }
            } catch (e) {
                // Ignore parse errors
            }
        }
    }
    return res;
};
// -------------------------------------------

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
