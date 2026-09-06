import type { ChatMessage, ProcessEntity } from './types';

class AiReviewService {
  public async sendChatMessage(process: ProcessEntity, userMessage: string): Promise<ChatMessage> {
    // Simulate slight natural latency
    await new Promise((resolve) => setTimeout(resolve, 400));

    const q = userMessage.toLowerCase();
    let text = '';
    let suggestedActions: string[] = [];

    if (q.includes('explain') || q.includes('overview') || q.includes('how does')) {
      text = `This process (${process.name}) manages the sequential workflow from initiation to fulfillment.\n\n` +
        `• **Participants**: ${(process.knowledge.actors || []).join(', ')}.\n` +
        `• **Key Tasks**: ${(process.knowledge.activities || []).slice(0, 4).join(' → ')}.\n` +
        `• **Decision Points**: ${(process.knowledge.gateways || []).join(', ') || 'Standard sequence flow'}.\n\n` +
        `The current model has a process health quality score of **${process.qualityScore}%**.`;
      suggestedActions = ['Check automation opportunities', 'Review audit compliance', 'View BPMN 2.0 model'];
    } else if (q.includes('reject') || q.includes('fail') || q.includes('what happens if')) {
      text = `When a decision is rejected (such as at **${process.knowledge.gateways?.[0] || 'Manager Approval'}**):\n\n` +
        `1. The workflow triggers a notification back to the **${process.knowledge.actors?.[0] || 'Employee'}**.\n` +
        `2. The request status is set to *Rejected* with audit justification logged in ERP.\n` +
        `3. No downstream fulfillment or booking is executed.`;
      suggestedActions = ['View rejection branch in Graph', 'Add SLA timer'];
    } else if (q.includes('manual') || q.includes('automatic') || q.includes('automated')) {
      text = `**Activity Automation Analysis**:\n\n` +
        `• **Manual Steps**: ${process.knowledge.activities?.slice(0, 2).join(', ') || 'Manager Review'}.\n` +
        `• **High Potential for Automation**: Budget validation and database record creation can be automated via SAP/ERP webhooks.\n` +
        `• **Recommended Tech**: RPA or Direct REST Service Task integration.`;
      suggestedActions = ['Convert Budget Check to Service Task', 'Export BPMN for Camunda'];
    } else if (q.includes('missing') || q.includes('gap') || q.includes('issue')) {
      text = `**Process Quality & Gap Analysis** (${process.validationIssues.length} issues identified):\n\n` +
        (process.validationIssues.length > 0
          ? process.validationIssues.map((iss, i) => `${i + 1}. **[${iss.severity}]** ${iss.title}: ${iss.description}`).join('\n\n')
          : '✓ No critical structural gaps detected. Process flow is fully connected.');
      suggestedActions = ['Apply recommended fixes', 'Inspect Process Graph'];
    } else if (q.includes('automation') || q.includes('ready')) {
      text = `**Automation Readiness Score**: **${process.qualityScore}%**.\n\n` +
        `• **BPMN 2.0 Compatibility**: Ready for execution engines (Camunda / Zeebe).\n` +
        `• **Data Schemas**: Inputs and outputs (${(process.knowledge.inputs || []).join(', ') || 'Documents'}) are defined.\n` +
        `• **Recommendation**: Add boundary timer events for SLA management before production deployment.`;
      suggestedActions = ['Download .bpmn file', 'Generate Executive PDF'];
    } else {
      text = `Regarding **${process.name}**: The workflow currently coordinates ${process.knowledge.activities?.length || 0} activities across ${(process.knowledge.actors || []).length} actors. ` +
        `All sequence flows and decision gateways have been verified against canonical BPMN 2.0 standards.`;
      suggestedActions = ['Explain this process', 'What happens if rejected?', 'Check automation opportunities'];
    }

    return {
      id: 'msg_' + Date.now().toString(36),
      sender: 'ai',
      text,
      timestamp: 'Just now',
      suggestedActions,
    };
  }
}

export const aiReviewService = new AiReviewService();
