package com.example.app.repository.clinical;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.example.app.repository.AbstractDecisionProblemStore;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ClinicalDecisionProblemStore extends AbstractDecisionProblemStore {

    public ClinicalDecisionProblemStore(
            @Value("classpath:data/clinical-decision-problems.json") Resource resource,
            ObjectMapper objectMapper) throws IOException {
        super(resource, objectMapper);
    }
}
