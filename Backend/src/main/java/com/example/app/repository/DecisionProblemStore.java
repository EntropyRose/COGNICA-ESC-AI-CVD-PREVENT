package com.example.app.repository;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DecisionProblemStore extends AbstractDecisionProblemStore {

    public DecisionProblemStore(
            @Value("classpath:data/decision-problems.json") Resource resource,
            ObjectMapper objectMapper) throws IOException {
        super(resource, objectMapper);
    }
}
