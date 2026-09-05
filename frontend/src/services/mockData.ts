import type {
  ProcessEntity,
  DocumentItem,
  KnowledgeActorItem,
  KnowledgeActivityItem,
  KnowledgeDecisionItem
} from './types';

export const INITIAL_DOCUMENTS: DocumentItem[] = [
  {
    id: 'doc_travel_req_pdf',
    name: 'Travel_Process_SOP.pdf',
    size: '1.4 MB',
    type: 'PDF',
    uploadedAt: 'Today, 4:32 PM',
    status: 'Completed',
    extractedEntitiesCount: 16,
    linkedProcessId: 'proc_travel_request',
    linkedProcessName: 'Travel Request Process',
    fileSnippet: 'Corporate Travel and Expense Policy (SOP-FIN-04). Outlines submission, managerial review, budget validation by Finance, and travel desk ticketing.',
  },
  {
    id: 'doc_onboarding_docx',
    name: 'Employee_Onboarding_Policy.docx',
    size: '850 KB',
    type: 'DOCX',
    uploadedAt: 'Today, 2:15 PM',
    status: 'Completed',
    extractedEntitiesCount: 22,
    linkedProcessId: 'proc_employee_onboarding',
    linkedProcessName: 'Employee Onboarding Process',
    fileSnippet: 'Standard guidelines for new hires: contract signature, IT provisioning, facilities security badge setup, and orientation.',
  },
  {
    id: 'doc_purchase_txt',
    name: 'Procurement_Approval_Matrix.txt',
    size: '120 KB',
    type: 'TXT',
    uploadedAt: 'Yesterday, 11:40 AM',
    status: 'Completed',
    extractedEntitiesCount: 14,
    linkedProcessId: 'proc_purchase_approval',
    linkedProcessName: 'Purchase Approval Process',
    fileSnippet: 'Purchase requisition lifecycle: PR creation, department manager endorsement, procurement RFQ, and purchase order dispatch.',
  },
  {
    id: 'doc_invoice_pdf',
    name: 'Accounts_Payable_Invoice_Policy.pdf',
    size: '2.1 MB',
    type: 'PDF',
    uploadedAt: '2 days ago',
    status: 'Completed',
    extractedEntitiesCount: 18,
    linkedProcessId: 'proc_invoice_processing',
    linkedProcessName: 'Invoice Processing Process',
    fileSnippet: 'AP 3-way matching protocol, invoice extraction, ledger posting in SAP ERP, and electronic bank remittance.',
  },
  {
    id: 'doc_vehicle_txt',
    name: 'EV_Assembly_Parallel_SOP.txt',
    size: '340 KB',
    type: 'TXT',
    uploadedAt: '3 days ago',
    status: 'Completed',
    extractedEntitiesCount: 28,
    linkedProcessId: 'proc_vehicle_assembly',
    linkedProcessName: 'Vehicle Assembly & Release Process',
    fileSnippet: 'Electric vehicle manufacturing stages: concurrent chassis build, telematics firmware flash, high-voltage battery test, and final QA gate.',
  }
];

export const INITIAL_PROCESSES: ProcessEntity[] = [
  {
    id: 'proc_travel_request',
    name: 'Travel Request Process',
    description: 'Corporate travel authorization workflow from employee submission to manager review, finance budget checks, and travel desk ticketing.',
    sourceDocument: 'Travel_Process_SOP.pdf',
    sourceType: 'PDF',
    rawText: `Employee submits a travel request.
Manager reviews the request.
If approved, Finance validates the budget.
If finance approves, Travel Desk books the travel.
If rejected at any step, Employee is notified.`,
    createdAt: 'Today, 4:32 PM',
    lastUpdated: 'Today, 5:18 PM',
    status: 'Validated',
    qualityScore: 87,
    currentVersion: 'v3',
    versions: [
      {
        version: 'v3',
        timestamp: 'Today, 5:18 PM',
        summary: 'Added Finance rejection notification and standardized role references',
        qualityScore: 87,
        author: 'Admin (AI Assisted)',
      },
      {
        version: 'v2',
        timestamp: 'Today, 4:52 PM',
        summary: 'Integrated Finance budget validation gateway and conditional edge',
        qualityScore: 79,
        author: 'Admin (AI Assisted)',
      },
      {
        version: 'v1',
        timestamp: 'Today, 4:32 PM',
        summary: 'Initial automated extraction from Travel_Process_SOP.pdf',
        qualityScore: 68,
        author: 'P.I.E. Knowledge Extraction Agent',
      }
    ],
    knowledge: {
      activities: [
        'Submit Travel Request',
        'Review Request',
        'Validate Budget',
        'Book Travel',
        'Notify Employee'
      ],
      actors: [
        'Employee',
        'Manager',
        'Finance',
        'Travel Desk'
      ],
      roles: [
        'Requestor',
        'Direct Manager',
        'Budget Controller',
        'Travel Specialist'
      ],
      gateways: [
        'Manager Approval',
        'Finance Budget Approval'
      ],
      systems: [
        'Concur Travel Portal',
        'SAP ERP Finance'
      ],
      events: [
        'Travel Needs Identified',
        'Travel Booked and Confirmed'
      ],
      inputs: [
        'Travel Request Form PDF',
        'Cost Estimate Sheet'
      ],
      outputs: [
        'Airline Itinerary',
        'Hotel Booking Voucher'
      ],
      businessRules: [
        'Travel requests must be submitted at least 7 days before departure',
        'Manager review is required for all domestic trips over $500',
        'If approved, Finance validates the departmental budget',
        'If rejected at any step, Employee is notified with reason'
      ],
      risks: [
        'No defined SLA for Manager approval may cause booking delays',
        'Manual budget verification in ERP poses throughput bottleneck'
      ],
      conflicts: []
    },
    insights: [
      {
        type: 'bottleneck',
        title: 'Manager Approval Latency',
        description: 'No response time limit is specified. Unattended requests delay advance booking discounts.',
        impact: 'High',
        suggestion: 'Introduce a 48-hour auto-escalation timer event.'
      },
      {
        type: 'automation',
        title: 'Automate Budget Check via SAP API',
        description: 'Finance validation can be automated for requests under $2,000 using real-time ERP budget queries.',
        impact: 'High',
        suggestion: 'Replace manual Finance task with an automated Service Task.'
      },
      {
        type: 'risk',
        title: 'Missing Rejection Routing for Travel Desk',
        description: 'Process assumes booking always succeeds; flight unavailability scenario is unhandled.',
        impact: 'Medium',
        suggestion: 'Add an exception boundary event for booking failure.'
      }
    ],
    validationIssues: [
      {
        id: 'val_1',
        category: 'Gateways',
        severity: 'Warning',
        title: 'Manager Approval Rejection Branch Incomplete',
        description: 'The exclusive gateway "Manager Approval" defines an approved branch, but rejection path needs clarification.',
        affectedNodeId: 'gateway-manager-approval',
        suggestedFix: 'Connect rejection flow to "Notify Employee" task.',
        isApplied: false
      },
      {
        id: 'val_2',
        category: 'Events',
        severity: 'Info',
        title: 'Missing Boundary Timer Event',
        description: 'Adding a 48-hour timer to "Review Request" ensures high process velocity.',
        affectedNodeId: 'activity-review-request',
        suggestedFix: 'Attach non-interrupting timer boundary event.',
        isApplied: false
      },
      {
        id: 'val_3',
        category: 'Ownership',
        severity: 'Info',
        title: 'Explicit Role Assignment Recommended',
        description: 'Activity "Submit Travel Request" has been associated with Employee role.',
        affectedNodeId: 'activity-submit-travel-request',
        suggestedFix: 'Validated and mapped to role-employee.',
        isApplied: true
      }
    ],
    sourceTraces: [
      {
        entityName: 'Submit Travel Request',
        entityType: 'Activity',
        sourceText: 'Employee submits travel request through the online portal.',
        documentName: 'Travel_Process_SOP.pdf',
        pageOrSection: 'Page 2, Section 3.1'
      },
      {
        entityName: 'Manager Review',
        entityType: 'Activity',
        sourceText: 'The direct Manager reviews request for business justification within 2 business days.',
        documentName: 'Travel_Process_SOP.pdf',
        pageOrSection: 'Page 3, Section 3.2'
      },
      {
        entityName: 'Validate Budget',
        entityType: 'Activity',
        sourceText: 'If approved by Manager, Finance validates budget against department allocation.',
        documentName: 'Travel_Process_SOP.pdf',
        pageOrSection: 'Page 3, Section 3.4'
      },
      {
        entityName: 'Travel Desk',
        entityType: 'Actor',
        sourceText: 'Travel desk books travel tickets and issues itinerary to the traveler.',
        documentName: 'Travel_Process_SOP.pdf',
        pageOrSection: 'Page 4, Section 4.1'
      }
    ],
    aiSummary: {
      executiveSummary: 'This process governs standard corporate travel authorization. It incorporates multi-tier governance (Direct Management & Finance) to ensure policy compliance before commercial commitments are executed by the Travel Desk.',
      auditReadinessScore: 87,
      auditStatus: 'Audit Ready',
      recommendations: [
        'Establish automated SLA tracking with a 48-hour managerial escalation rule.',
        'Implement automated API verification for budget allocations under $2,000.',
        'Define a structured fallback procedure when designated airline fares expire.'
      ],
      complianceNotes: [
        'Complies with ISO 9001 quality documentation standards.',
        'Meets corporate financial delegation of authority (DoA) mandates.'
      ]
    }
  },
  {
    id: 'proc_employee_onboarding',
    name: 'Employee Onboarding Process',
    description: 'Lifecycle of onboarding new hires covering HR contract verification, IT equipment provisioning, facilities badge issuing, and first-day orientation.',
    sourceDocument: 'Employee_Onboarding_Policy.docx',
    sourceType: 'DOCX',
    createdAt: 'Today, 2:15 PM',
    lastUpdated: 'Today, 3:00 PM',
    status: 'Validated',
    qualityScore: 92,
    currentVersion: 'v2',
    versions: [
      {
        version: 'v2',
        timestamp: 'Today, 3:00 PM',
        summary: 'Synchronized parallel IT and Facilities provisioning tracks',
        qualityScore: 92,
        author: 'HR Operations Lead',
      },
      {
        version: 'v1',
        timestamp: 'Today, 2:15 PM',
        summary: 'Initial extraction from Employee_Onboarding_Policy.docx',
        qualityScore: 78,
        author: 'P.I.E. Extraction Agent',
      }
    ],
    knowledge: {
      activities: [
        'Sign Employment Contract',
        'Provision Workstation and Credentials',
        'Issue Security Access Badge',
        'Setup Payroll and Benefits',
        'Conduct Welcome Orientation'
      ],
      actors: [
        'New Hire',
        'HR Specialist',
        'IT Support',
        'Facilities Team',
        'Hiring Manager'
      ],
      roles: ['Candidate', 'HR Admin', 'SysAdmin', 'Security Guard', 'Manager'],
      gateways: ['Contract Verification Gate', 'Parallel Provisioning Fork'],
      systems: ['Workday HCM', 'Okta Identity', 'Active Directory'],
      events: ['Offer Accepted Trigger', 'Onboarding Completed'],
      inputs: ['Signed Offer Letter', 'Tax Declaration Form'],
      outputs: ['Active Employee Profile', 'Configured Laptop'],
      businessRules: ['IT setup must complete 48h prior to join date'],
      risks: ['Hardware shipment delays for remote employees'],
      conflicts: []
    },
    insights: [
      {
        type: 'automation',
        title: 'Automate Okta Account Creation',
        description: 'Single sign-on accounts can be provisioned automatically via Workday webhook.',
        impact: 'High'
      }
    ],
    validationIssues: [],
    sourceTraces: [],
    aiSummary: {
      executiveSummary: 'Covers the end-to-end operational preparation for new employee readiness across HR, IT, and Facilities.',
      auditReadinessScore: 92,
      auditStatus: 'Audit Ready',
      recommendations: ['Integrate automated background check webhook into the start event.'],
      complianceNotes: ['GDPR and PII compliant employee data handling.']
    }
  },
  {
    id: 'proc_purchase_approval',
    name: 'Purchase Approval Process',
    description: 'Procurement cycle from purchase requisition submission to managerial review, supplier RFQ quotation, and PO issuance.',
    sourceDocument: 'Procurement_Approval_Matrix.txt',
    sourceType: 'TXT',
    createdAt: 'Yesterday, 11:40 AM',
    lastUpdated: 'Yesterday, 1:20 PM',
    status: 'Needs Review',
    qualityScore: 74,
    currentVersion: 'v1',
    versions: [
      {
        version: 'v1',
        timestamp: 'Yesterday, 11:40 AM',
        summary: 'Initial draft extracted from Procurement_Approval_Matrix.txt',
        qualityScore: 74,
        author: 'Procurement Officer',
      }
    ],
    knowledge: {
      activities: [
        'Create Purchase Requisition',
        'Department Head Endorsement',
        'Procurement Solicits Quotations',
        'Approve Purchase Order',
        'Dispatch PO to Supplier'
      ],
      actors: ['Requester', 'Department Head', 'Procurement Buyer', 'Finance Director'],
      roles: ['Staff', 'Approver', 'Buyer', 'CFO'],
      gateways: ['Threshold Check (> $10k)', 'Quotation Evaluation'],
      systems: ['Coupa Procurement', 'Oracle NetSuite'],
      events: ['PR Submitted', 'PO Acknowledged by Vendor'],
      inputs: ['Bill of Quantities', 'Vendor Quotes'],
      outputs: ['Approved PO PDF'],
      businessRules: ['Purchases exceeding $10,000 require CFO co-signature'],
      risks: ['Supplier quotation turnaround time variance'],
      conflicts: []
    },
    insights: [],
    validationIssues: [
      {
        id: 'val_po_1',
        category: 'Gateways',
        severity: 'Warning',
        title: 'Missing Dual Approval Branch for High-Value PR',
        description: 'PR above $10,000 threshold requires secondary executive approval flow.',
        isApplied: false
      }
    ],
    sourceTraces: [],
    aiSummary: {
      executiveSummary: 'Controls organization expenditure through structured approval limits and vendor price verification.',
      auditReadinessScore: 74,
      auditStatus: 'Action Required',
      recommendations: ['Explicitly model CFO dual approval gateway.'],
      complianceNotes: ['SOX compliance requires segregation of requester and buyer duties.']
    }
  },
  {
    id: 'proc_vehicle_assembly',
    name: 'Vehicle Assembly & Release Process',
    description: 'High-throughput electric vehicle assembly line featuring concurrent powertrain mounting, telematics firmware flash, battery tests, and QA gate.',
    sourceDocument: 'EV_Assembly_Parallel_SOP.txt',
    sourceType: 'TXT',
    createdAt: '3 days ago',
    lastUpdated: '3 days ago',
    status: 'Validated',
    qualityScore: 94,
    currentVersion: 'v1',
    versions: [
      {
        version: 'v1',
        timestamp: '3 days ago',
        summary: 'Initial parallel assembly model verified',
        qualityScore: 94,
        author: 'Chief Industrial Engineer',
      }
    ],
    knowledge: {
      activities: [
        'Receive Customized Vehicle Order',
        'Schedule Assembly Line Batch',
        'Build Chassis and Mount Powertrain',
        'Flash ECU Firmware and Telematics',
        'Perform Battery Cell Balancing Tests',
        'Deliver Interior Trim Modules',
        'Conduct Integrated Diagnostic Testing',
        'Release Vehicle for Global Shipping'
      ],
      actors: [
        'Production Planner',
        'Mechanical Engineering',
        'Software Engineering',
        'Battery Team',
        'Logistics Team',
        'Quality Assurance',
        'Warehouse Supervisor'
      ],
      roles: ['Planner', 'Mechanic', 'Firmware Engineer', 'Battery Tech', 'QA Inspector'],
      gateways: ['Parallel Assembly Fork', 'Parallel Assembly Synchronization Join', 'Quality Inspection Gate'],
      systems: ['MES System', 'CAN Bus Tool', 'Battery Rig'],
      events: ['Order Released', 'Vehicle Dispatched'],
      inputs: ['Build Sheet', 'Firmware Binary'],
      outputs: ['Diagnostic Log', 'Shipping Invoice'],
      businessRules: ['Integrated test begins only after all parallel tracks finish'],
      risks: ['High-voltage cell test timeouts'],
      conflicts: []
    },
    insights: [],
    validationIssues: [],
    sourceTraces: [],
    aiSummary: {
      executiveSummary: 'Coordinates multi-discipline manufacturing operations with strict parallel synchronization and final quality assurance release.',
      auditReadinessScore: 94,
      auditStatus: 'Audit Ready',
      recommendations: ['Incorporate automated line-stoppage telemetry.'],
      complianceNotes: ['Automotive SPICE Level 3 & ISO 26262 functional safety aligned.']
    }
  }
];

export const GLOBAL_KNOWLEDGE_ACTORS: KnowledgeActorItem[] = [
  { name: 'Employee', role: 'Staff / Requestor', processes: ['Travel Request Process', 'Employee Onboarding Process'], documents: ['Travel_Process_SOP.pdf', 'Employee_Onboarding_Policy.docx'], activityCount: 4 },
  { name: 'Manager', role: 'Department Head / Approver', processes: ['Travel Request Process', 'Purchase Approval Process'], documents: ['Travel_Process_SOP.pdf', 'Procurement_Approval_Matrix.txt'], activityCount: 6 },
  { name: 'Finance', role: 'Budget Controller', processes: ['Travel Request Process', 'Purchase Approval Process', 'Invoice Processing Process'], documents: ['Travel_Process_SOP.pdf', 'Accounts_Payable_Invoice_Policy.pdf'], activityCount: 5 },
  { name: 'Travel Desk', role: 'Fulfillment Specialist', processes: ['Travel Request Process'], documents: ['Travel_Process_SOP.pdf'], activityCount: 2 },
  { name: 'HR Specialist', role: 'HR Admin', processes: ['Employee Onboarding Process'], documents: ['Employee_Onboarding_Policy.docx'], activityCount: 4 },
  { name: 'IT Support', role: 'SysAdmin', processes: ['Employee Onboarding Process'], documents: ['Employee_Onboarding_Policy.docx'], activityCount: 3 },
  { name: 'Quality Assurance', role: 'QA Inspector', processes: ['Vehicle Assembly & Release Process'], documents: ['EV_Assembly_Parallel_SOP.txt'], activityCount: 3 }
];

export const GLOBAL_KNOWLEDGE_ACTIVITIES: KnowledgeActivityItem[] = [
  { name: 'Submit Travel Request', assignedRole: 'Employee', processName: 'Travel Request Process', documentName: 'Travel_Process_SOP.pdf', isAutomated: false },
  { name: 'Review Request', assignedRole: 'Manager', processName: 'Travel Request Process', documentName: 'Travel_Process_SOP.pdf', isAutomated: false },
  { name: 'Validate Budget', assignedRole: 'Finance', processName: 'Travel Request Process', documentName: 'Travel_Process_SOP.pdf', isAutomated: false },
  { name: 'Book Travel', assignedRole: 'Travel Desk', processName: 'Travel Request Process', documentName: 'Travel_Process_SOP.pdf', isAutomated: false },
  { name: 'Provision Workstation', assignedRole: 'IT Support', processName: 'Employee Onboarding Process', documentName: 'Employee_Onboarding_Policy.docx', isAutomated: true },
  { name: 'Flash ECU Firmware', assignedRole: 'Software Engineering', processName: 'Vehicle Assembly & Release Process', documentName: 'EV_Assembly_Parallel_SOP.txt', isAutomated: true },
  { name: 'Conduct Integrated Testing', assignedRole: 'Quality Assurance', processName: 'Vehicle Assembly & Release Process', documentName: 'EV_Assembly_Parallel_SOP.txt', isAutomated: false }
];

export const GLOBAL_KNOWLEDGE_DECISIONS: KnowledgeDecisionItem[] = [
  { name: 'Manager Approval', decisionType: 'Exclusive', processName: 'Travel Request Process', conditionCount: 2 },
  { name: 'Finance Budget Approval', decisionType: 'Exclusive', processName: 'Travel Request Process', conditionCount: 2 },
  { name: 'Parallel Provisioning Fork', decisionType: 'Parallel', processName: 'Employee Onboarding Process', conditionCount: 3 },
  { name: 'Threshold Check (> $10k)', decisionType: 'Exclusive', processName: 'Purchase Approval Process', conditionCount: 2 },
  { name: 'Quality Inspection Gate', decisionType: 'Exclusive', processName: 'Vehicle Assembly & Release Process', conditionCount: 2 }
];
