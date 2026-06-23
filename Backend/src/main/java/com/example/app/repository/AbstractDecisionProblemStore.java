package com.example.app.repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;

import com.example.app.model.StoredCognicaRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractDecisionProblemStore {

    protected final CopyOnWriteArrayList<StoredCognicaRequest> records = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId;
    protected final ObjectMapper objectMapper;

    protected AbstractDecisionProblemStore(Resource resource, ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        JsonNode array = objectMapper.readTree(resource.getInputStream());
        long maxId = 0;
        for (JsonNode node : array) {
            StoredCognicaRequest req = mapNode(node);
            records.add(req);
            if (req.getId() != null && req.getId() > maxId) {
                maxId = req.getId();
            }
        }
        this.nextId = new AtomicLong(maxId + 1);
    }

    private StoredCognicaRequest mapNode(JsonNode node) throws IOException {
        StoredCognicaRequest req = new StoredCognicaRequest();
        if (node.has("id")) req.setId(node.get("id").asLong());
        if (node.has("name")) req.setName(node.get("name").asText(null));
        if (node.has("naturalLanguage")) req.setNaturalLanguage(node.get("naturalLanguage").asText(null));
        if (node.has("actionType")) req.setActionType(node.get("actionType").asText("getPrediction"));
        if (node.has("policyInCognicaDpCnl")) req.setPolicyInCognicaDPCNL(node.get("policyInCognicaDpCnl").asText(null));
        if (node.has("originalPolicyInCognicaDpCnl")) req.setOriginalPolicyInCognicaDPCNL(node.get("originalPolicyInCognicaDpCnl").asText(null));
        if (node.has("query")) req.setQuery(node.get("query").asText(""));
        if (node.has("dpVersion")) req.setVersion(node.get("dpVersion").asText("default"));
        if (node.has("lastResponseJson") && !node.get("lastResponseJson").isNull()) {
            req.setLastResponseJson(node.get("lastResponseJson").asText(null));
        }
        req.setFactsJson(node.has("factsJson")
            ? objectMapper.writeValueAsString(node.get("factsJson")) : "[]");
        req.setComplementaryOptionsJson(node.has("complementaryOptionsJson")
            ? objectMapper.writeValueAsString(node.get("complementaryOptionsJson")) : "[]");
        return req;
    }

    public List<StoredCognicaRequest> findAllByOrderByUpdatedAtDesc() {
        List<StoredCognicaRequest> copy = new ArrayList<>(records);
        copy.sort((a, b) -> {
            if (a.getUpdatedAt() == null && b.getUpdatedAt() == null) return 0;
            if (a.getUpdatedAt() == null) return 1;
            if (b.getUpdatedAt() == null) return -1;
            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
        });
        return copy;
    }

    public List<StoredCognicaRequest> findByVersionOrderByUpdatedAtDesc(String version) {
        return findAllByOrderByUpdatedAtDesc().stream()
            .filter(r -> version.equals(r.getVersion()))
            .collect(Collectors.toList());
    }

    public Optional<StoredCognicaRequest> findById(Long id) {
        return records.stream().filter(r -> id.equals(r.getId())).findFirst();
    }

    public List<StoredCognicaRequest> findByNameContainingIgnoreCase(String name) {
        String lower = name.toLowerCase();
        return records.stream()
            .filter(r -> r.getName() != null && r.getName().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    public List<StoredCognicaRequest> findByNameContainingIgnoreCaseAndVersion(String name, String version) {
        String lower = name.toLowerCase();
        return records.stream()
            .filter(r -> r.getName() != null && r.getName().toLowerCase().contains(lower)
                && version.equals(r.getVersion()))
            .collect(Collectors.toList());
    }

    public List<StoredCognicaRequest> findByNameIgnoreCase(String name) {
        String lower = name.toLowerCase();
        return records.stream()
            .filter(r -> r.getName() != null && r.getName().equalsIgnoreCase(lower))
            .collect(Collectors.toList());
    }

    public List<StoredCognicaRequest> searchByComplementaryOptionKeyword(String keyword) {
        String lower = keyword.toLowerCase();
        return records.stream()
            .filter(r -> r.getComplementaryOptionsJson() != null
                && r.getComplementaryOptionsJson().toLowerCase().contains(lower))
            .collect(Collectors.toList());
    }

    public List<StoredCognicaRequest> searchByComplementaryOptionKeywordAndVersion(String keyword, String version) {
        String lower = keyword.toLowerCase();
        return records.stream()
            .filter(r -> r.getComplementaryOptionsJson() != null
                && r.getComplementaryOptionsJson().toLowerCase().contains(lower)
                && version.equals(r.getVersion()))
            .collect(Collectors.toList());
    }

    public StoredCognicaRequest save(StoredCognicaRequest request) {
        if (request.getId() == null) {
            request.setId(nextId.getAndIncrement());
            records.add(request);
            return request;
        }
        for (int i = 0; i < records.size(); i++) {
            if (request.getId().equals(records.get(i).getId())) {
                records.set(i, request);
                return request;
            }
        }
        records.add(request);
        return request;
    }

    public void deleteById(Long id) {
        records.removeIf(r -> id.equals(r.getId()));
    }
}
