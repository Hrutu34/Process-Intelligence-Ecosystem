export interface DescriptionSample { id: string; title: string; domain: string; clarity: string; description: string; }

export const DESCRIPTION_BANK: DescriptionSample[] = [
  {
    "id": "D01",
    "title": "Purchase Requisition Approval",
    "domain": "Procurement",
    "clarity": "Clear",
    "description": "An employee creates a purchase requisition. The manager reviews it. If approved, a purchase order is created and the requester is notified. If rejected, the requester is informed and the process ends."
  },
  {
    "id": "D02",
    "title": "Employee Onboarding",
    "domain": "HR",
    "clarity": "Clear",
    "description": "When a new hire accepts the offer, HR collects documents. Then, in parallel, IT accounts are created, a workstation is assigned, and payroll enrollment happens. Once all three are done, induction is conducted and onboarding is complete."
  },
  {
    "id": "D03",
    "title": "Expense Reimbursement",
    "domain": "Finance",
    "clarity": "Clear",
    "description": "An employee submits an expense claim. Finance verifies the receipts. If the receipts are valid, the employee is reimbursed; otherwise the claim is returned to the employee for correction."
  },
  {
    "id": "D04",
    "title": "Customer Complaint Handling",
    "domain": "Service",
    "clarity": "Medium",
    "description": "A complaint comes in and is logged. Depending on severity it may be escalated to a specialist. A resolution is sent to the customer and the complaint is closed."
  },
  {
    "id": "D05",
    "title": "Order Fulfillment",
    "domain": "Logistics",
    "clarity": "Medium",
    "description": "After an order is confirmed, the warehouse picks and packs the goods while finance generates the invoice at the same time. When both are done, the order is shipped."
  },
  {
    "id": "D06",
    "title": "Loan Application Review",
    "domain": "Banking",
    "clarity": "Medium",
    "description": "A customer applies for a loan. The bank checks the credit score. If the score is high enough, the loan is approved and funds are disbursed. If not, the application is declined. Approved loans also trigger a welcome letter."
  },
  {
    "id": "D07",
    "title": "Restaurant Table Booking",
    "domain": "Hospitality",
    "clarity": "Ambiguous",
    "description": "A guest asks for a table. Staff check availability. Sometimes they call back, sometimes they email. If a table is free it is reserved, otherwise the guest is put on a waitlist."
  },
  {
    "id": "D08",
    "title": "Software Bug Triage",
    "domain": "IT",
    "clarity": "Medium",
    "description": "A bug is reported and logged. It is assessed for severity. Critical bugs are assigned immediately to a developer; minor bugs are added to the backlog. Assigned bugs are fixed, tested, and closed."
  },
  {
    "id": "D09",
    "title": "Insurance Claim Intake",
    "domain": "Insurance",
    "clarity": "Clear",
    "description": "A policyholder files a claim. The claim is registered and documents are validated. If documents are complete, the claim moves to assessment; if not, the claimant is asked to resubmit. After assessment the claim is either paid or rejected."
  },
  {
    "id": "D10",
    "title": "Conference Talk Submission",
    "domain": "Events",
    "clarity": "Ambiguous",
    "description": "Someone submits a talk. Reviewers look at it. Good ones get accepted and scheduled; others are rejected. Accepted speakers confirm attendance."
  }
];
