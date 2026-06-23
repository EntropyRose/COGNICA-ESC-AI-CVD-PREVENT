package com.example.app.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.app.model.StoredCognicaRequest;
import com.example.app.repository.DecisionProblemStore;

@Service
public class StoredRequestService {

    @Autowired
    private DecisionProblemStore store;

    public StoredCognicaRequest saveRequest(StoredCognicaRequest request) {
        return store.save(request);
    }

    public List<StoredCognicaRequest> getAllRequests() {
        return store.findAllByOrderByUpdatedAtDesc();
    }

    public List<StoredCognicaRequest> getAllRequests(String version) {
        if (version == null || version.isBlank()) {
            return getAllRequests();
        }
        return store.findByVersionOrderByUpdatedAtDesc(version);
    }

    public Optional<StoredCognicaRequest> getRequestById(Long id) {
        return store.findById(id);
    }

    public List<StoredCognicaRequest> searchByName(String name) {
        return store.findByNameContainingIgnoreCase(name);
    }

    public List<StoredCognicaRequest> searchByName(String name, String version) {
        if (version == null || version.isBlank()) {
            return searchByName(name);
        }
        return store.findByNameContainingIgnoreCaseAndVersion(name, version);
    }

    public void deleteRequest(Long id) {
        store.deleteById(id);
    }

    public List<StoredCognicaRequest> searchByQueryKeywords(String query) {
        return searchByQueryKeywords(query, null);
    }

    public List<StoredCognicaRequest> searchByQueryKeywords(String query, String version) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = query.toLowerCase().split("[\\s\\p{Punct}]+");
        List<String> nonBlankTokens = new ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) nonBlankTokens.add(token);
        }

        boolean singleTokenQuery = nonBlankTokens.size() == 1;
        List<StoredCognicaRequest> relevantRequests = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();

        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (!singleTokenQuery && isCommonWord(token)) continue;

            List<StoredCognicaRequest> results;
            if (version == null || version.isBlank()) {
                results = store.searchByComplementaryOptionKeyword(token);
            } else {
                results = store.searchByComplementaryOptionKeywordAndVersion(token, version);
            }
            for (StoredCognicaRequest req : results) {
                if (seen.add(req.getId())) {
                    relevantRequests.add(req);
                }
            }
        }

        return relevantRequests;
    }

    public void updateLastResponse(Long id, String responseJson) {
        store.findById(id).ifPresent(sr -> {
            sr.setLastResponseJson(responseJson);
            store.save(sr);
        });
    }

    private boolean isCommonWord(String word) {
        String[] common = {"is", "a", "an", "the", "and", "or", "in", "of", "to", "for",
                           "on", "at", "by", "with", "from", "as", "if", "then", "which",
                           "that", "what", "when", "where", "why", "how"};
        for (String c : common) {
            if (word.equals(c)) return true;
        }
        return false;
    }
}
