package com.pie.backend.service;

import com.pie.shared.dto.CanonicalProcessGraph;
import com.pie.shared.dto.ProcessKnowledgeDTO;

public interface ProcessGraphBuilder {

    CanonicalProcessGraph build(ProcessKnowledgeDTO knowledge);

    CanonicalProcessGraph build(String graphId, ProcessKnowledgeDTO knowledge);
}
