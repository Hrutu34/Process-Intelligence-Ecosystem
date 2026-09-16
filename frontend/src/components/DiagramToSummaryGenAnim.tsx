export default function DiagramToSummaryGenAnim({ statusText }: { statusText?: string }) {
  const DUR = "3s";
  const pulse = (delay: string) => (
    <animate attributeName="opacity" values="0.55;1;0.55" dur="1.9s" begin={delay} repeatCount="indefinite" />
  );
  const lineGrow = (delay: string) => (
    <animate attributeName="width" values="6;178;6" dur="2.4s" begin={delay} repeatCount="indefinite" />
  );
  return (
    <div className="gen-anim">
      <svg viewBox="0 0 660 220" className="gen-anim-svg" preserveAspectRatio="xMidYMid meet">
        <defs>
          <marker id="dttArrow" markerWidth="9" markerHeight="9" refX="7" refY="3" orient="auto" markerUnits="strokeWidth">
            <path d="M0,0 L0,6 L8,3 z" fill="#c084fc" />
          </marker>
        </defs>

        {/* Source diagram (pulsing) */}
        <circle cx="40" cy="110" r="16" fill="#180f2a" stroke="#c084fc" strokeWidth="2.5">{pulse("0s")}</circle>
        <text x="40" y="152" textAnchor="middle" fontSize="10" fill="#d9b3fb">Start</text>

        <line x1="56" y1="110" x2="90" y2="110" stroke="#c084fc" strokeWidth="2" markerEnd="url(#dttArrow)">{pulse("0.1s")}</line>

        <rect x="90" y="86" width="90" height="48" rx="9" fill="#180f2a" stroke="#c084fc" strokeWidth="2">{pulse("0.2s")}</rect>

        <line x1="180" y1="110" x2="214" y2="110" stroke="#c084fc" strokeWidth="2" markerEnd="url(#dttArrow)">{pulse("0.3s")}</line>

        <polygon points="214,110 240,84 266,110 240,136" fill="#180f2a" stroke="#c084fc" strokeWidth="2">{pulse("0.4s")}</polygon>
        <text x="240" y="164" textAnchor="middle" fontSize="10" fill="#d9b3fb">Decision</text>

        <line x1="266" y1="110" x2="300" y2="110" stroke="#c084fc" strokeWidth="2" markerEnd="url(#dttArrow)">{pulse("0.5s")}</line>

        <circle cx="316" cy="110" r="16" fill="#180f2a" stroke="#c084fc" strokeWidth="3">{pulse("0.6s")}</circle>
        <text x="316" y="152" textAnchor="middle" fontSize="10" fill="#d9b3fb">Done</text>

        {/* Particles flowing to summary */}
        <circle r="5" fill="#c084fc" cy="110">
          <animate attributeName="cx" from="336" to="440" dur={DUR} repeatCount="indefinite" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.1;0.9;1" dur={DUR} repeatCount="indefinite" />
        </circle>
        <circle r="5" fill="#c084fc" cy="110">
          <animate attributeName="cx" from="336" to="440" dur={DUR} begin="0.5s" repeatCount="indefinite" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.1;0.9;1" dur={DUR} begin="0.5s" repeatCount="indefinite" />
        </circle>
        <circle r="5" fill="#c084fc" cy="110">
          <animate attributeName="cx" from="336" to="440" dur={DUR} begin="1s" repeatCount="indefinite" />
          <animate attributeName="opacity" values="0;1;1;0" keyTimes="0;0.1;0.9;1" dur={DUR} begin="1s" repeatCount="indefinite" />
        </circle>

        {/* Summary panel — growing lines */}
        <g transform="translate(430,20)">
          <rect width="210" height="180" rx="10" fill="#180f2a" stroke="#c084fc" strokeWidth="2" />
          <rect x="16" y="22"  height="9" rx="4.5" fill="#c084fc">{lineGrow("0s")}</rect>
          <rect x="16" y="46"  height="9" rx="4.5" fill="#c084fc">{lineGrow("0.15s")}</rect>
          <rect x="16" y="70"  height="9" rx="4.5" fill="#c084fc">{lineGrow("0.3s")}</rect>
          <rect x="16" y="94"  height="9" rx="4.5" fill="#c084fc">{lineGrow("0.45s")}</rect>
          <rect x="16" y="118" height="9" rx="4.5" fill="#c084fc">{lineGrow("0.6s")}</rect>
          <rect x="16" y="142" height="9" rx="4.5" fill="#c084fc">{lineGrow("0.75s")}</rect>
        </g>
      </svg>
      {statusText && <p className="loading-status">{statusText}</p>}
    </div>
  );
}
