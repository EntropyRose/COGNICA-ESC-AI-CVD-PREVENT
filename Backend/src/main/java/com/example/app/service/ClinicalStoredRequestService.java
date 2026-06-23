package com.example.app.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.app.model.StoredCognicaRequest;
import com.example.app.repository.clinical.ClinicalDecisionProblemStore;

@Service
public class ClinicalStoredRequestService {

    @Autowired
    private ClinicalDecisionProblemStore store;

    public StoredCognicaRequest saveRequest(StoredCognicaRequest request) {
        return store.save(request);
    }

    public List<StoredCognicaRequest> getAllRequests() {
        return store.findAllByOrderByUpdatedAtDesc();
    }

    public Optional<StoredCognicaRequest> getRequestById(Long id) {
        return store.findById(id);
    }

    public List<StoredCognicaRequest> searchByName(String name) {
        return store.findByNameContainingIgnoreCase(name);
    }

    public List<StoredCognicaRequest> findByExactName(String name) {
        return store.findByNameIgnoreCase(name);
    }

    public void deleteRequest(Long id) {
        store.deleteById(id);
    }

    public void updateLastResponse(Long id, String responseJson) {
        store.findById(id).ifPresent(sr -> {
            sr.setLastResponseJson(responseJson);
            store.save(sr);
        });
    }
}
