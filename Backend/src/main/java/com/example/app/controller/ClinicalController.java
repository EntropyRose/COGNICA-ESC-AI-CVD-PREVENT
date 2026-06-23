package com.example.app.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.app.model.Fact;
import com.example.app.model.StoredCognicaRequest;
import com.example.app.service.ClinicalStoredRequestService;
import com.example.app.service.StoredRequestService;
import com.example.app.util.RuleTransformer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/clinical")
@CrossOrigin(origins = "*")
public class ClinicalController {

    @Autowired
    private ClinicalStoredRequestService clinicalService;

    @Autowired
    private StoredRequestService mainService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/stored-requests")
    public ResponseEntity<List<StoredCognicaRequest>> getAllStoredRequests() {
        return ResponseEntity.ok(clinicalService.getAllRequests());
    }

    @GetMapping("/stored-requests/{id}")
    public ResponseEntity<StoredCognicaRequest> getStoredRequest(@PathVariable Long id) {
        Optional<StoredCognicaRequest> request = clinicalService.getRequestById(id);
        return request.map(ResponseEntity::ok)
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/stored-requests/search")
    public ResponseEntity<List<StoredCognicaRequest>> searchStoredRequests(@RequestParam String query) {
        return ResponseEntity.ok(clinicalService.searchByName(query));
    }

    @PostMapping("/stored-requests/save")
    public ResponseEntity<StoredCognicaRequest> saveStoredRequest(@RequestBody StoredCognicaRequest request) {
        try {
            String originalPolicy = request.getPolicyInCognicaDPCNL();
            if (originalPolicy == null || originalPolicy.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            if (originalPolicy.toUpperCase().contains("ELSE")) {
                return ResponseEntity.badRequest().build();
            }
            request.setOriginalPolicyInCognicaDPCNL(originalPolicy);
            request.setPolicyInCognicaDPCNL(RuleTransformer.transformPolicyText(originalPolicy));
            request.setFactsJson("[]");
            StoredCognicaRequest saved = clinicalService.saveRequest(request);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/stored-requests/{id}")
    public ResponseEntity<StoredCognicaRequest> updateStoredRequest(
            @PathVariable Long id,
            @RequestBody StoredCognicaRequest updatedRequest) {
        Optional<StoredCognicaRequest> existing = clinicalService.getRequestById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            StoredCognicaRequest request = existing.get();
            request.setName(updatedRequest.getName());
            request.setNaturalLanguage(updatedRequest.getNaturalLanguage());

            String originalPolicy = updatedRequest.getPolicyInCognicaDPCNL();
            if (originalPolicy == null || originalPolicy.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            if (originalPolicy.toUpperCase().contains("ELSE")) {
                return ResponseEntity.badRequest().build();
            }
            request.setOriginalPolicyInCognicaDPCNL(originalPolicy);
            request.setPolicyInCognicaDPCNL(RuleTransformer.transformPolicyText(originalPolicy));
            request.setQuery(updatedRequest.getQuery() == null ? "" : updatedRequest.getQuery().trim());
            request.setComplementaryOptionsJson(
                updatedRequest.getComplementaryOptionsJson() == null ? "[]" : updatedRequest.getComplementaryOptionsJson()
            );
            // Clinical DPs are always fact-free
            request.setFactsJson("[]");
            StoredCognicaRequest saved = clinicalService.saveRequest(request);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/stored-requests/{id}")
    public ResponseEntity<Void> deleteStoredRequest(@PathVariable Long id) {
        clinicalService.deleteRequest(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Copies every DP from the main database into the clinical database.
     * All fact values are reset to unknown so the patient fills them in.
     * Safe to re-run: existing clinical DPs with the same name are updated in place;
     * new ones are inserted.
     */
    @PostMapping("/import-from-main")
    public ResponseEntity<?> importFromMain() {
        List<StoredCognicaRequest> mainDps = mainService.getAllRequests();
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();

        for (StoredCognicaRequest source : mainDps) {
            try {
                String factsJson = resetFactsToUnknown(source.getFactsJson());

                // Find existing clinical DP by exact name so re-runs are idempotent
                List<StoredCognicaRequest> existing = clinicalService.findByExactName(source.getName());
                StoredCognicaRequest target = existing.isEmpty() ? new StoredCognicaRequest() : existing.get(0);

                target.setName(source.getName());
                target.setNaturalLanguage(source.getNaturalLanguage());
                target.setActionType(source.getActionType() != null ? source.getActionType() : "getPrediction");
                target.setOriginalPolicyInCognicaDPCNL(source.getOriginalPolicyInCognicaDPCNL() != null
                    ? source.getOriginalPolicyInCognicaDPCNL()
                    : source.getPolicyInCognicaDPCNL());
                target.setPolicyInCognicaDPCNL(source.getPolicyInCognicaDPCNL());
                target.setQuery(source.getQuery() != null ? source.getQuery() : "");
                target.setComplementaryOptionsJson(source.getComplementaryOptionsJson() != null
                    ? source.getComplementaryOptionsJson() : "[]");
                target.setFactsJson(factsJson);
                target.setLastResponseJson(null);

                clinicalService.saveRequest(target);
                if (existing.isEmpty()) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception e) {
                errors.add(source.getName() + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(Map.of(
            "created", created,
            "updated", updated,
            "total", mainDps.size(),
            "errors", errors
        ));
    }

    private String resetFactsToUnknown(String factsJson) {
        if (factsJson == null || factsJson.isBlank() || factsJson.trim().equals("[]")) {
            return "[]";
        }
        try {
            List<Fact> facts = objectMapper.readValue(factsJson, new TypeReference<List<Fact>>() {});
            List<Fact> reset = new ArrayList<>();
            for (Fact f : facts) {
                Fact copy = new Fact(f.getFact(), false, "unknown", f.getFactType());
                if (f.hasInternalSource()) {
                    copy.setInternalSourceDp(f.getInternalSourceDp());
                }
                if (f.hasRpSource()) {
                    copy.setRpSource(f.getRpSource());
                }
                reset.add(copy);
            }
            return objectMapper.writeValueAsString(reset);
        } catch (Exception e) {
            return "[]";
        }
    }
}
