export default function WhatIsBpmn() {
  return (
    <section className="bpmn-section">
      <h2 className="section-title">What is BPMN?</h2>
      <div className="bpmn-grid">
        <div className="bpmn-illustration">
          <svg viewBox="0 0 860 340" xmlns="http://www.w3.org/2000/svg" className="wib-svg">
            <defs>
              <marker id="wibArrow" markerWidth="11" markerHeight="11" refX="8" refY="3.5" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,7 L10,3.5 z" fill="#7fa88f" />
              </marker>
              <marker id="wibArrowYes" markerWidth="11" markerHeight="11" refX="8" refY="3.5" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,7 L10,3.5 z" fill="#22c55e" />
              </marker>
              <marker id="wibArrowNo" markerWidth="11" markerHeight="11" refX="8" refY="3.5" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,7 L10,3.5 z" fill="#f59e0b" />
              </marker>
            </defs>
            <path d="M81,170 L125,170" fill="none" stroke="#7fa88f" strokeWidth="2.2" markerEnd="url(#wibArrow)" />
            <path d="M295,170 L364,170" fill="none" stroke="#7fa88f" strokeWidth="2.2" markerEnd="url(#wibArrow)" />
            <path d="M436,170 C480,170 480,80 505,80" fill="none" stroke="#22c55e" strokeWidth="2.2" markerEnd="url(#wibArrowYes)" />
            <path d="M436,170 C480,170 480,260 505,260" fill="none" stroke="#f59e0b" strokeWidth="2.2" markerEnd="url(#wibArrowNo)" />
            <path d="M675,80 L764,80" fill="none" stroke="#22c55e" strokeWidth="2.2" markerEnd="url(#wibArrowYes)" />
            <path d="M675,260 L764,260" fill="none" stroke="#f59e0b" strokeWidth="2.2" markerEnd="url(#wibArrowNo)" />
            <rect x="452" y="112" width="40" height="20" rx="5" fill="#0a120e" stroke="#22c55e" />
            <text x="472" y="126.5" textAnchor="middle" fontSize="12" fontWeight="700" fill="#22c55e">Yes</text>
            <rect x="452" y="208" width="40" height="20" rx="5" fill="#0a120e" stroke="#f59e0b" />
            <text x="472" y="222.5" textAnchor="middle" fontSize="12" fontWeight="700" fill="#f59e0b">No</text>
            <circle className="wib-particle wib-particle--seg1" cx="81" cy="170" r="6" fill="#38bdf8" />
            <circle className="wib-particle wib-particle--seg2" cx="295" cy="170" r="6" fill="#38bdf8" />
            <circle className="wib-particle wib-particle--seg3yes" cx="436" cy="170" r="6" fill="#38bdf8" />
            <circle className="wib-particle wib-particle--seg3no" cx="436" cy="170" r="6" fill="#38bdf8" />
            <circle className="wib-particle wib-particle--seg4yes" cx="675" cy="80" r="6" fill="#38bdf8" />
            <circle className="wib-particle wib-particle--seg4no" cx="675" cy="260" r="6" fill="#38bdf8" />
            <g>
              <circle className="wib-pulse--start" cx="55" cy="170" r="26" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <circle cx="55" cy="170" r="26" fill="#12201a" stroke="#22c55e" strokeWidth="2.6" />
              <text x="55" y="216" textAnchor="middle" fontSize="14" fill="#9fc2ac">Start</text>
            </g>
            <g>
              <rect className="wib-pulse--task1" x="125" y="138" width="170" height="64" rx="14" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <rect x="125" y="138" width="170" height="64" rx="14" fill="#16281f" stroke="#22c55e" strokeWidth="2.4" />
              <text x="210" y="164" textAnchor="middle" fontSize="14.5" fill="#eafff0">Check payment</text>
              <text x="210" y="184" textAnchor="middle" fontSize="14.5" fill="#eafff0">details</text>
            </g>
            <g>
              <polygon className="wib-pulse--gw" points="400,124 444,170 400,216 356,170" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <polygon points="400,134 434,170 400,206 366,170" fill="#16281f" stroke="#f59e0b" strokeWidth="2.4" />
              <line x1="388" y1="158" x2="412" y2="182" stroke="#f59e0b" strokeWidth="2.2" strokeLinecap="round" />
              <line x1="412" y1="158" x2="388" y2="182" stroke="#f59e0b" strokeWidth="2.2" strokeLinecap="round" />
              <text x="400" y="238" textAnchor="middle" fontSize="14.5" fill="#eafff0" fontWeight="600">Payment</text>
              <text x="400" y="256" textAnchor="middle" fontSize="14.5" fill="#eafff0" fontWeight="600">valid?</text>
            </g>
            <g>
              <rect className="wib-pulse--branch" x="505" y="48" width="170" height="64" rx="14" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <rect x="505" y="48" width="170" height="64" rx="14" fill="#16281f" stroke="#22c55e" strokeWidth="2.4" />
              <text x="590" y="74" textAnchor="middle" fontSize="14.5" fill="#eafff0">Ship the</text>
              <text x="590" y="94" textAnchor="middle" fontSize="14.5" fill="#eafff0">order</text>
            </g>
            <g>
              <rect className="wib-pulse--branch" x="505" y="228" width="170" height="64" rx="14" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <rect x="505" y="228" width="170" height="64" rx="14" fill="#16281f" stroke="#22c55e" strokeWidth="2.4" />
              <text x="590" y="254" textAnchor="middle" fontSize="14.5" fill="#eafff0">Request new</text>
              <text x="590" y="274" textAnchor="middle" fontSize="14.5" fill="#eafff0">payment</text>
            </g>
            <g>
              <circle className="wib-pulse--end" cx="790" cy="80" r="26" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <circle cx="790" cy="80" r="26" fill="#12201a" stroke="#84cc16" strokeWidth="3.4" />
              <text x="790" y="35" textAnchor="middle" fontSize="14" fill="#9fc2ac">Order fulfilled</text>
            </g>
            <g>
              <circle className="wib-pulse--end" cx="790" cy="260" r="26" fill="none" stroke="#38bdf8" strokeWidth="2.5" />
              <circle cx="790" cy="260" r="26" fill="#12201a" stroke="#84cc16" strokeWidth="3.4" />
              <text x="790" y="305" textAnchor="middle" fontSize="14" fill="#9fc2ac">Order on hold</text>
            </g>
          </svg>
        </div>
        <div className="bpmn-copy">
          <h3>Business Process Model and Notation 2.0</h3>
          <p>
            BPMN 2.0 is a globally recognized, standardized visual language for describing business processes.
            Instead of long paragraphs of procedure documentation, a process is drawn as a flowchart using a small,
            consistent set of shapes — so anyone in an organization, from business analysts to developers to
            executives, can read the same diagram and understand exactly how work flows from start to finish.
          </p>
          <p>
            P.I.E. reads and writes real BPMN 2.0 XML under the hood, and renders/edits it with the real{" "}
            <strong>bpmn-js</strong> engine — the same open-source library used by Camunda Modeler and bpmn.io — so
            every diagram you generate or upload opens directly in standard BPMN tooling.
          </p>
          <ul className="bpmn-legend">
            <li>
              <span className="bpmn-legend-swatch bpmn-legend-swatch--event"></span>
              <span><b>Events</b> (circles) mark something that happens — a thin circle for a <b>start event</b> and a thick circle for an <b>end event</b>.</span>
            </li>
            <li>
              <span className="bpmn-legend-swatch bpmn-legend-swatch--task"></span>
              <span><b>Tasks</b> (rounded rectangles) represent a single unit of work — like "Check payment details" or "Ship the order".</span>
            </li>
            <li>
              <span className="bpmn-legend-swatch bpmn-legend-swatch--gateway"></span>
              <span><b>Gateways</b> (diamonds) are decision points where the flow branches — such as "Payment valid?" splitting into Yes/No.</span>
            </li>
            <li>
              <span className="bpmn-legend-swatch" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>→</span>
              <span><b>Sequence flows</b> (arrows) connect these elements to show the order work moves through the process.</span>
            </li>
          </ul>
        </div>
      </div>
    </section>
  );
}
