# P.I.E. Demo Dataset

This directory contains curated business process descriptions for testing and showcasing the Process Intelligence Ecosystem (P.I.E.).

## Scenarios

### 1. Employee Travel Request Process (`travel-request.txt`)
- **Focus**: Standard happy path with multiple handoffs.
- **Features**: Demonstrates extraction, decision gateways (Manager, Finance), and clean BPMN layout.

### 2. Leave Approval Process (`leave-approval.txt`)
- **Focus**: Validation and error detection.
- **Features**: Intentionally incomplete. Lacks rejection branches and end states, demonstrating the Intelligence Agent's ability to detect process gaps and modeling issues.

### 3. Purchase Request Approval Process (`purchase-request.txt`)
- **Focus**: Complex branching and multiple conditions.
- **Features**: Contains value thresholds (under/over $5000), multiple approval layers (Department Head, CFO), and feedback loops (revision). Suitable for advanced BPMN modeling and demo fallback mode.

## Usage
These text files can be uploaded or pasted into the P.I.E. frontend `UploadModal.tsx` or `ProcessEntry.tsx` to automatically trigger the 4-agent workflow. The `expected-output` directory contains reference JSONs and summaries for validation.

