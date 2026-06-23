package com.example.app.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.app.model.StoredCognicaRequest;
import com.example.app.repository.clinical.ClinicalStoredCognicaRequestRepository;

@Service
public class ClinicalStoredRequestService {

    @Autowired
    private ClinicalStoredCognicaRequestRepository repository;

    public StoredCognicaRequest saveRequest(StoredCognicaRequest request) {
        return repository.save(request);
    }

    public List<StoredCognicaRequest> getAllRequests() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    public Optional<StoredCognicaRequest> getRequestById(Long id) {
        return repository.findById(id);
    }

    public List<StoredCognicaRequest> searchByName(String name) {
        return repository.findByNameContainingIgnoreCase(name);
    }

    public List<StoredCognicaRequest> findByExactName(String name) {
        return repository.findByNameIgnoreCase(name);
    }

    public void deleteRequest(Long id) {
        repository.deleteById(id);
    }

    public void updateLastResponse(Long id, String responseJson) {
        repository.findById(id).ifPresent(sr -> {
            sr.setLastResponseJson(responseJson);
            repository.save(sr);
        });
    }
}
