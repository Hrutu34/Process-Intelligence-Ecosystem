export default function TextToDiagramGenAnim({ statusText }: { statusText?: string }) {
  const DUR = "3s";
  const lineOpacity = (delay: string) => (
    <animate attributeName="opacity" values="0.25;1;0.25" dur="1.6s" begin={delay} repeatCount="indefinite" />
  );
  return (
    <div className="gen-anim">
      <svg viewBox="0 0 660 220" className="gen-anim-svg" preserveAspectRatio="xMidYMid meet">
        <defs>
          <linearGradient id="ttdScan" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%"   stopColor="#38bdf8" stopOpacity="0" />
            <stop offset="50%"  stopColor="#38bdf8" stopOpacity="0.85" />
            <stop offset="100%" stopColor="#38bdf8" stopOpacity="0" />
          </linearGradient>
          <marker id="ttdArrow" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto" markerUnits="strokeWidth">
            <path d="M0,0 L0,6 L8,3 z" fill="#38bdf8" />
          </marker>
        </defs>

        {/* Document */}
        <g transform="translate(20,30)">
          <rect width="110" height="160" rx="10" fill="#0f1a2a" stroke="#38bdf8" strokeWidth="2" />
          <rect x="14" y="18"  width="82" height="7" rx="3.5" fill="#38bdf8">{lineOpacity("0s")}</rect>
          <rect x="14" y="38"  width="82" height="7" rx="3.5" fill="#38bdf8">{lineOpacity("0.15s")}</rect>
          <rect x="14" y="58"  width="60" height="7" rx="3.5" fill="#38bdf8">{lineOpacity("0.3s")}</rect>
          <rect x="14" y="78"  width="82" height="7" rx="3.5" fill="#38bdf8">{lineOpacity("0.45s")}</rect>
          <rect x="14" y="98"  width="46" height="7" rx="3.5" fill="#38bdf8">{lineOpacity("0.6s")}</rect>
          <rect x="14" y="118" width="70" height="7" rx="3.5" fill="#38bdf8">{lineOpacity("0.75s")}</rect>

          {/* Scanning bar */}
          <rect x="0" width="110" height="14" fill="url(#ttdScan)">
            <animate attributeName="y" from="0" to="146" dur={DUR} repeatCount="indefinite" />
          </rect>
        </g>

        {/* Particles flowing right */}
        <circle r="5" fill="#38bdf8" cy="110">
          <animate attributeName="cx" from="145" to="335" dur={DUR} repeatCount="indefinite" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.1;0.9;1" dur={DUR} repeatCount="indefinite" />
        </circle>
        <circle r="5" fill="#38bdf8" cy="110">
          <animate attributeName="cx" from="145" to="335" dur={DUR} begin="0.5s" repeatCount="indefinite" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.1;0.9;1" dur={DUR} begin="0.5s" repeatCount="indefinite" />
        </circle>
        <circle r="5" fill="#38bdf8" cy="110">
          <animate attributeName="cx" from="145" to="335" dur={DUR} begin="1s" repeatCount="indefinite" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.1;0.9;1" dur={DUR} begin="1s" repeatCount="indefinite" />
        </circle>

        {/* Diagram nodes with pulse */}
        <g>
          <circle cx="360" cy="110" r="18" fill="#0f1a2a" stroke="#38bdf8" strokeWidth="2.5">
            <animate attributeName="r" values="16;20;16" dur="1.8s" repeatCount="indefinite" />
          </circle>
          <text x="360" y="152" textAnchor="middle" fontSize="10" fill="#7fb8e0">Start</text>
        </g>

        <line x1="378" y1="110" x2="415" y2="110" stroke="#38bdf8" strokeWidth="2" markerEnd="url(#ttdArrow)" strokeDasharray="6 4">
          <animate attributeName="stroke-dashoffset" from="20" to="0" dur="1.2s" repeatCount="indefinite" />
        </line>

        <g>
          <rect x="415" y="85" width="110" height="50" rx="10" fill="#0f1a2a" stroke="#38bdf8" strokeWidth="2">
            <animate attributeName="opacity" values="0.7;1;0.7" dur="2s" begin="0.3s" repeatCount="indefinite" />
          </rect>
          <text x="470" y="115" textAnchor="middle" fontSize="12" fill="#bde6fb">Process step</text>
        </g>

        <line x1="525" y1="110" x2="555" y2="110" stroke="#38bdf8" strokeWidth="2" markerEnd="url(#ttdArrow)" strokeDasharray="6 4">
          <animate attributeName="stroke-dashoffset" from="20" to="0" dur="1.2s" begin="0.3s" repeatCount="indefinite" />
        </line>

        <g>
          <polygon points="555,110 582,83 609,110 582,137" fill="#0f1a2a" stroke="#38bdf8" strokeWidth="2">
            <animate attributeName="opacity" values="0.7;1;0.7" dur="2s" begin="0.6s" repeatCount="indefinite" />
          </polygon>
          <text x="582" y="164" textAnchor="middle" fontSize="10" fill="#7fb8e0">Decision</text>
        </g>

        <line x1="609" y1="110" x2="628" y2="110" stroke="#38bdf8" strokeWidth="2" markerEnd="url(#ttdArrow)" strokeDasharray="6 4">
          <animate attributeName="stroke-dashoffset" from="20" to="0" dur="1.2s" begin="0.6s" repeatCount="indefinite" />
        </line>

        <g>
          <circle cx="644" cy="110" r="18" fill="#0f1a2a" stroke="#38bdf8" strokeWidth="3">
            <animate attributeName="r" values="16;20;16" dur="1.8s" begin="0.9s" repeatCount="indefinite" />
          </circle>
          <text x="644" y="152" textAnchor="middle" fontSize="10" fill="#7fb8e0">Done</text>
        </g>
      </svg>
      {statusText && <p className="loading-status">{statusText}</p>}
    </div>
  );
}
