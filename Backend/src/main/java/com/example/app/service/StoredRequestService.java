package com.example.app.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.app.model.StoredCognicaRequest;
import com.example.app.repository.StoredCognicaRequestRepository;

@Service
public class StoredRequestService {
    
    @Autowired
    private StoredCognicaRequestRepository repository;
    
    /**
     * Save a new stored request or update an existing one
     */
    public StoredCognicaRequest saveRequest(StoredCognicaRequest request) {
        return repository.save(request);
    }
    
    /**
     * Get all stored requests, ordered by most recently updated
     */
    public List<StoredCognicaRequest> getAllRequests() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public List<StoredCognicaRequest> getAllRequests(String version) {
        if (version == null || version.isBlank()) {
            return getAllRequests();
        }
        return repository.findByVersionOrderByUpdatedAtDesc(version);
    }
    
    /**
     * Get a specific stored request by ID
     */
    public Optional<StoredCognicaRequest> getRequestById(Long id) {
        return repository.findById(id);
    }
    
    /**
     * Search stored requests by name
     */
    public List<StoredCognicaRequest> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    public List<StoredCognicaRequest> searchByName(String name, String version) {
        if (version == null || version.isBlank()) {
            return searchByName(name);
        }
        return repository.findByNameContainingIgnoreCaseAndVersion(name, version);
    }
    
    /**
     * Delete a stored request
     */
    public void deleteRequest(Long id) {
        repository.deleteById(id);
    }

    /**
     * Search for decision problems by query keywords using complementary options only.
     */
    public List<StoredCognicaRequest> searchByQueryKeywords(String query) {
        return searchByQueryKeywords(query, null);
    }

    public List<StoredCognicaRequest> searchByQueryKeywords(String query, String version) {
        if (query == null || query.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Extract keywords: split by spaces and punctuation, filter common words
        String[] tokens = query.toLowerCase().split("[\\s\\p{Punct}]+");
        java.util.List<String> nonBlankTokens = new java.util.ArrayList<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                nonBlankTokens.add(token);
            }
        }

        boolean singleTokenQuery = nonBlankTokens.size() == 1;
        java.util.List<StoredCognicaRequest> relevantRequests = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            // Do not drop stopwords when the full query is a single token (e.g., "A").
            if (!singleTokenQuery && isCommonWord(token)) {
                continue;
            }
            
            List<StoredCognicaRequest> results;
            if (version == null || version.isBlank()) {
                results = repository.searchByComplementaryOptionKeyword(token);
            } else {
                results = repository.searchByComplementaryOptionKeywordAndVersion(token, version);
            }
            for (StoredCognicaRequest req : results) {
                if (seen.add(req.getId())) {
                    relevantRequests.add(req);
                }
            }
        }

        return relevantRequests;
    }

    private boolean isCommonWord(String word) {
        String[] common = {"is", "a", "an", "the", "and", "or", "in", "of", "to", "for", 
                          "on", "at", "by", "with", "from", "as", "if", "then", "which",
                          "that", "what", "when", "where", "why", "how"};
        for (String c : common) {
            if (word.equals(c)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Update the last response of a request
     */
    public void updateLastResponse(Long id, String responseJson) {
        Optional<StoredCognicaRequest> request = repository.findById(id);
        if (request.isPresent()) {
            StoredCognicaRequest sr = request.get();
            sr.setLastResponseJson(responseJson);
            repository.save(sr);
        }
    }
}
