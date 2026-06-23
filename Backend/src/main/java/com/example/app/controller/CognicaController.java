package com.example.app.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import com.example.app.model.CognicaResponse;
import com.example.app.model.Fact;
import com.example.app.model.StoredCognicaRequest;
import com.example.app.service.StoredRequestService;
import com.example.app.util.RuleTransformer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class CognicaController {

    private static final int MAX_AUTO_RESOLUTION_ROUNDS = 5;

    @Value("${cognica.api.url}")
    private String COGNICA_URL;

    @Value("${cognica.api.username}")
    private String cognicaUsername;

    @Value("${cognica.api.password}")
    private String cognicaPassword;

    private WebClient webClient;

    @PostConstruct
    private void initWebClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(30));

        this.webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeaders(headers -> {
                headers.setBasicAuth(cognicaUsername, cognicaPassword);
                headers.setContentType(MediaType.APPLICATION_JSON);
            })
            .build();
    }

    @Autowired
    private StoredRequestService storedRequestService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/predict")
    public Mono<ResponseEntity<CognicaResponse>> getPrediction(@RequestBody StoredCognicaRequest request) {
        String policy;
        try {
            String originalPolicy = request.getPolicyInCognicaDPCNL();
            if (originalPolicy == null || originalPolicy.isBlank()) {
                CognicaResponse errorResponse = new CognicaResponse();
                errorResponse.setHasError(true);
                errorResponse.setErrorMessage("Policy text is required.");
                return Mono.just(ResponseEntity.badRequest().body(errorResponse));
            }
            policy = RuleTransformer.transformPolicyText(originalPolicy);
        } catch (IllegalArgumentException e) {
            CognicaResponse errorResponse = new CognicaResponse();
            errorResponse.setHasError(true);
            errorResponse.setErrorMessage("Invalid policy syntax: " + e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
        }

        // Build a clean minimal request — sending the full JPA entity causes Cognica errors
        // because of null fields (factsJson, complementaryOptionsJson) and JPA metadata.
        StoredCognicaRequest cleanRequest = new StoredCognicaRequest();
        cleanRequest.setActionType(request.getActionType() != null ? request.getActionType() : "getPrediction");
        cleanRequest.setPolicyInCognicaDPCNL(policy);
        cleanRequest.setQuery(request.getQuery());
        cleanRequest.setComplementaryOptions(request.getComplementaryOptions());
        cleanRequest.setFacts(request.getFacts());

        return webClient.post()
            .uri(COGNICA_URL)
            .bodyValue(cleanRequest)
            .retrieve()
            .toEntity(CognicaResponse.class)
            .onErrorResume(ex -> {
                CognicaResponse errorResponse = new CognicaResponse();
                errorResponse.setHasError(true);
                errorResponse.setErrorMessage("Cognica API unreachable: " + ex.getMessage());
                return Mono.just(ResponseEntity.status(503).body(errorResponse));
            });
    }

    /**
     * Save a new stored COGNICA request
     */
    @PostMapping("/stored-requests/save")
    public ResponseEntity<?> saveStoredRequest(@RequestBody StoredCognicaRequest request) {
        try {
            request.setQuery(normalizeStoredQuery(request.getQuery()));
            request.setComplementaryOptionsJson(normalizeJsonArray(request.getComplementaryOptionsJson()));
            request.setFactsJson(normalizeJsonArray(request.getFactsJson()));

            String originalPolicy = request.getPolicyInCognicaDPCNL();
            if (originalPolicy == null || originalPolicy.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Policy text is required."));
            }
            request.setOriginalPolicyInCognicaDPCNL(originalPolicy);
            request.setPolicyInCognicaDPCNL(RuleTransformer.transformPolicyText(originalPolicy));
            request.setVersion(request.getVersion());

            StoredCognicaRequest saved = storedRequestService.saveRequest(request);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Save failed: " + e.getMessage()));
        }
    }

    /**
     * Get all stored requests
     */
    @GetMapping("/stored-requests")
    public ResponseEntity<List<StoredCognicaRequest>> getAllStoredRequests(@RequestParam(required = false) String version) {
        List<StoredCognicaRequest> requests = storedRequestService.getAllRequests(version);
        return ResponseEntity.ok(requests);
    }

    /**
     * Get a specific stored request by ID
     */
    @GetMapping("/stored-requests/{id}")
    public ResponseEntity<StoredCognicaRequest> getStoredRequest(@PathVariable Long id) {
        Optional<StoredCognicaRequest> request = storedRequestService.getRequestById(id);
        return request.map(ResponseEntity::ok)
                      .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Search stored requests by name
     */
    @GetMapping("/stored-requests/search")
    public ResponseEntity<List<StoredCognicaRequest>> searchStoredRequests(
            @RequestParam String query,
            @RequestParam(required = false) String version
    ) {
        List<StoredCognicaRequest> requests = storedRequestService.searchByName(query, version);
        return ResponseEntity.ok(requests);
    }

    /**
     * Find decision problems relevant to a query based on keywords and facts
     */
    @GetMapping("/stored-requests/search-by-query-needs")
    public ResponseEntity<List<StoredCognicaRequest>> searchByQueryNeeds(
            @RequestParam String query,
            @RequestParam(required = false) String version
    ) {
        List<StoredCognicaRequest> requests = storedRequestService.searchByQueryKeywords(query, version);
        return ResponseEntity.ok(requests);
    }

    /**
     * Delete a stored request
     */
    @DeleteMapping("/stored-requests/{id}")
    public ResponseEntity<Void> deleteStoredRequest(@PathVariable Long id) {
        storedRequestService.deleteRequest(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Update a stored request
     */
    @PutMapping("/stored-requests/{id}")
    public ResponseEntity<?> updateStoredRequest(
            @PathVariable Long id,
            @RequestBody StoredCognicaRequest updatedRequest) {
        Optional<StoredCognicaRequest> existing = storedRequestService.getRequestById(id);
        if (existing.isPresent()) {
            try {
                StoredCognicaRequest request = existing.get();
                request.setName(updatedRequest.getName());
                request.setNaturalLanguage(updatedRequest.getNaturalLanguage());

                String originalPolicy = updatedRequest.getPolicyInCognicaDPCNL();
                if (originalPolicy == null || originalPolicy.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Policy text is required."));
                }
                request.setOriginalPolicyInCognicaDPCNL(originalPolicy);
                request.setPolicyInCognicaDPCNL(RuleTransformer.transformPolicyText(originalPolicy));
                request.setVersion(updatedRequest.getVersion());
                request.setQuery(normalizeStoredQuery(updatedRequest.getQuery()));
                request.setComplementaryOptionsJson(normalizeJsonArray(updatedRequest.getComplementaryOptionsJson()));
                request.setFactsJson(normalizeJsonArray(updatedRequest.getFactsJson()));
                StoredCognicaRequest saved = storedRequestService.saveRequest(request);
                return ResponseEntity.ok(saved);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Update failed: " + e.getMessage()));
            }
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Execute a stored request and update its last response
     */
    @PostMapping("/stored-requests/{id}/execute")
    public Mono<ResponseEntity<Map<String, Object>>> executeStoredRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) {
        Optional<StoredCognicaRequest> storedReq = storedRequestService.getRequestById(id);

        if (storedReq.isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        StoredCognicaRequest stored = storedReq.get();
        String executionQuery = resolveExecutionQuery(stored.getQuery(), payload == null ? null : payload.get("query"));

        if (executionQuery.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "error", "A query is required to execute this decision problem."
            )));
        }

        List<Fact> executionFacts = prepareExecutionFacts(stored);
        StoredCognicaRequest requestForExecution = buildExecutableRequest(stored, executionQuery, executionFacts);

        return executeAgainstCognica(requestForExecution)
            .flatMap(initialResponse -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", id);
                result.put("name", stored.getName());
                result.put("executedQuery", executionQuery);
                result.put("initialResponse", initialResponse);

                return resolveAssumptionsContinuously(
                    id,
                    stored,
                    executionQuery,
                    initialResponse,
                    result,
                    new ArrayList<>(),
                    new HashSet<>(),
                    0,
                    executionFacts
                );
            });
    }

    private Mono<ResponseEntity<Map<String, Object>>> resolveAssumptionsContinuously(
        Long id,
        StoredCognicaRequest originalStored,
        String executionQuery,
        CognicaResponse currentResponse,
        Map<String, Object> result,
        List<Map<String, Object>> resolutionSteps,
        Set<String> attemptedAssumptions,
        int round,
        List<Fact> executionFacts
    ) {
        String explanationForAssumption = resolveMostPrevalentExplanation(currentResponse, originalStored);
        if (explanationForAssumption != null && !explanationForAssumption.isBlank()) {
            result.put("prevalentExplanation", explanationForAssumption);
        }

        if (explanationForAssumption != null
            && currentResponse != null
            && (currentResponse.getCognicaSummaryExplanation() == null
                || currentResponse.getCognicaSummaryExplanation().isBlank())) {
            currentResponse.setCognicaSummaryExplanation(explanationForAssumption);
            result.put("explanationFallbackApplied", true);
        }

        if (!isMaybe(currentResponse)) {
            saveLastResponse(id, originalStored, currentResponse);
            result.put("response", currentResponse);
            result.put("autoResolutionApplied", !resolutionSteps.isEmpty());
            if (!resolutionSteps.isEmpty()) {
                result.put("autoResolutionSteps", resolutionSteps);
                result.put("autoResolutionRounds", round);
            }
            return Mono.just(ResponseEntity.ok(result));
        }

        if (round >= MAX_AUTO_RESOLUTION_ROUNDS) {
            saveLastResponse(id, originalStored, currentResponse);
            result.put("response", currentResponse);
            result.put("autoResolutionApplied", !resolutionSteps.isEmpty());
            result.put("autoResolutionReason", "Reached maximum auto-resolution rounds.");
            if (!resolutionSteps.isEmpty()) {
                result.put("autoResolutionSteps", resolutionSteps);
                result.put("autoResolutionRounds", round);
            }
            return Mono.just(ResponseEntity.ok(result));
        }

        List<String> assumptions = extractAssumptionQueries(explanationForAssumption, attemptedAssumptions);

        if (assumptions.isEmpty()) {
            List<String> policyBasedMissing = extractMissingFactsFromPolicy(
                originalStored.getPolicyInCognicaDPCNL(),
                executionQuery,
                executionFacts
            );

            for (String missing : policyBasedMissing) {
                String missingLower = missing.toLowerCase();
                if (!attemptedAssumptions.contains(missingLower)) {
                    assumptions.add(missing);
                    attemptedAssumptions.add(missingLower);
                }
            }
        }

        if (assumptions.isEmpty()) {
            saveLastResponse(id, originalStored, currentResponse);
            result.put("response", currentResponse);
            result.put("autoResolutionApplied", !resolutionSteps.isEmpty());
            result.put("autoResolutionReason", "No new assumption variables found to query.");
            if (!resolutionSteps.isEmpty()) {
                result.put("autoResolutionSteps", resolutionSteps);
                result.put("autoResolutionRounds", round);
            }
            return Mono.just(ResponseEntity.ok(result));
        }

        return queryAndApplyAssumptions(originalStored, assumptions, result, resolutionSteps, attemptedAssumptions, executionFacts)
            .flatMap(appliedFactCount -> {
                if (appliedFactCount == 0) {
                    saveLastResponse(id, originalStored, currentResponse);
                    result.put("response", currentResponse);
                    result.put("autoResolutionApplied", !resolutionSteps.isEmpty());
                    result.put("autoResolutionReason", "Assumption queries did not produce definite Yes/No facts.");
                    if (!resolutionSteps.isEmpty()) {
                        result.put("autoResolutionSteps", resolutionSteps);
                        result.put("autoResolutionRounds", round + 1);
                    }
                    return Mono.just(ResponseEntity.ok(result));
                }

                StoredCognicaRequest rerunRequest = buildExecutableRequest(originalStored, executionQuery, executionFacts);
                return executeAgainstCognica(rerunRequest)
                    .flatMap(rerunResponse -> {
                        result.put("rerunResponse", rerunResponse);
                        result.put("autoResolutionApplied", true);
                        result.put("autoResolutionSteps", resolutionSteps);
                        return resolveAssumptionsContinuously(
                            id,
                            originalStored,
                            executionQuery,
                            rerunResponse,
                            result,
                            resolutionSteps,
                            attemptedAssumptions,
                            round + 1,
                            executionFacts
                        );
                    });
            });
    }

    private Mono<Integer> queryAndApplyAssumptions(
        StoredCognicaRequest originalStored,
        List<String> assumptions,
        Map<String, Object> result,
        List<Map<String, Object>> resolutionSteps,
        Set<String> attemptedAssumptions,
        List<Fact> executionFacts
    ) {
        Mono<Integer> chain = Mono.just(0);

        for (String assumptionQuery : assumptions) {
            chain = chain.flatMap(appliedCount -> {
                attemptedAssumptions.add(assumptionQuery.trim().toLowerCase());

                Map<String, Object> step = new LinkedHashMap<>();
                step.put("assumptionQuery", assumptionQuery);
                step.put("requesterDpId", originalStored.getId());
                step.put("requesterDpName", originalStored.getName());

                // Skip auto-resolution when the user has already explicitly set this fact.
                boolean alreadySet = executionFacts != null && executionFacts.stream().anyMatch(f ->
                    f.getFact() != null
                    && f.getFact().trim().equalsIgnoreCase(assumptionQuery.trim())
                    && ("true".equalsIgnoreCase(f.getFactStatus()) || "false".equalsIgnoreCase(f.getFactStatus())));
                if (alreadySet) {
                    step.put("status", "skipped");
                    step.put("reason", "Fact already set explicitly — user override respected.");
                    resolutionSteps.add(step);
                    return Mono.just(appliedCount);
                }

                List<StoredCognicaRequest> candidateDps = findRelevantDecisionProblemsForAssumption(originalStored, assumptionQuery, executionFacts);
                if (candidateDps.isEmpty()) {
                    step.put("status", "skipped");
                    step.put("reason", "No different relevant decision problem found.");
                    resolutionSteps.add(step);
                    return Mono.just(appliedCount);
                }

                return queryAssumptionAcrossCandidates(candidateDps, assumptionQuery, 0, new ArrayList<>())
                    .flatMap(assumptionAttempt -> {
                        if (assumptionAttempt == null || assumptionAttempt.dp == null || assumptionAttempt.response == null) {
                            step.put("status", "no-definite-answer");
                            step.put("reason", "All relevant DPs returned unusable responses.");
                            if (assumptionAttempt != null) {
                                step.put("candidateAttempts", assumptionAttempt.attemptLogs);
                            }
                            resolutionSteps.add(step);
                            return Mono.just(appliedCount);
                        }

                        StoredCognicaRequest relevantAssumptionDp = assumptionAttempt.dp;
                        CognicaResponse assumptionResponse = assumptionAttempt.response;

                        step.put("assumptionDpId", relevantAssumptionDp.getId());
                        step.put("assumptionDpName", relevantAssumptionDp.getName());
                        if (!assumptionAttempt.attemptLogs.isEmpty()) {
                            step.put("candidateAttempts", assumptionAttempt.attemptLogs);
                        }

                        result.put("assumptionDpId", relevantAssumptionDp.getId());
                        result.put("assumptionDpName", relevantAssumptionDp.getName());
                        result.put("queryingMessage", String.format(
                            "Querying (%s) on (%s)",
                            assumptionQuery,
                            relevantAssumptionDp.getName() == null || relevantAssumptionDp.getName().isBlank()
                                ? "relevant DP"
                                : relevantAssumptionDp.getName().trim()
                        ));

                        step.put("assumptionResponse", assumptionResponse);
                        result.put("assumptionQuery", assumptionQuery);
                        result.put("assumptionResponse", assumptionResponse);
                        result.put("queryingResult", assumptionResponse);

                        Boolean inferredFactValue = inferFactValueFromPrediction(assumptionResponse);
                        if (inferredFactValue != null) {
                            boolean updated = updateFactValue(executionFacts, assumptionQuery, inferredFactValue);
                            if (!updated) {
                                step.put("status", "update-failed");
                                resolutionSteps.add(step);
                                return Mono.just(appliedCount);
                            }

                            step.put("status", "applied");
                            step.put("updatedFact", assumptionQuery);
                            step.put("updatedFactValue", inferredFactValue);
                            result.put("updatedFact", assumptionQuery);
                            result.put("updatedFactValue", inferredFactValue);
                            resolutionSteps.add(step);
                            return Mono.just(appliedCount + 1);
                        }

                        if (!isMaybe(assumptionResponse)) {
                            step.put("status", "no-definite-answer");
                            resolutionSteps.add(step);
                            return Mono.just(appliedCount);
                        }

                        // The assumption itself is Maybe. Chase its own assumptions on the relevant DP,
                        // then rerun the current assumption query on that same DP.
                        String nestedExplanation = resolveMostPrevalentExplanation(assumptionResponse, relevantAssumptionDp);
                        List<String> nestedAssumptions = extractAssumptionQueries(nestedExplanation, attemptedAssumptions);

                        if (nestedAssumptions.isEmpty()) {
                            List<Fact> nestedFacts = parseFacts(relevantAssumptionDp.getFactsJson());
                            List<String> nestedPolicyBasedMissing = extractMissingFactsFromPolicy(
                                relevantAssumptionDp.getPolicyInCognicaDPCNL(),
                                assumptionQuery,
                                nestedFacts
                            );

                            for (String missing : nestedPolicyBasedMissing) {
                                String missingLower = missing.toLowerCase();
                                if (!attemptedAssumptions.contains(missingLower)) {
                                    nestedAssumptions.add(missing);
                                    attemptedAssumptions.add(missingLower);
                                }
                            }
                        }

                        if (nestedAssumptions.isEmpty()) {
                            step.put("status", "no-definite-answer");
                            step.put("reason", "No nested assumptions found to continue resolution.");
                            resolutionSteps.add(step);
                            return Mono.just(appliedCount);
                        }

                        step.put("status", "resolving-nested");
                        step.put("nestedAssumptions", nestedAssumptions);
                        resolutionSteps.add(step);

                        return queryAndApplyAssumptions(
                                relevantAssumptionDp,
                                nestedAssumptions,
                                result,
                                resolutionSteps,
                                attemptedAssumptions,
                                executionFacts
                            )
                            .flatMap(nestedAppliedCount -> {
                                if (nestedAppliedCount == 0) {
                                    Map<String, Object> nestedStep = new LinkedHashMap<>();
                                    nestedStep.put("assumptionQuery", assumptionQuery);
                                    nestedStep.put("requesterDpId", originalStored.getId());
                                    nestedStep.put("requesterDpName", originalStored.getName());
                                    nestedStep.put("assumptionDpId", relevantAssumptionDp.getId());
                                    nestedStep.put("assumptionDpName", relevantAssumptionDp.getName());
                                    nestedStep.put("status", "no-definite-answer");
                                    nestedStep.put("reason", "Nested assumptions could not be resolved.");
                                    resolutionSteps.add(nestedStep);
                                    return Mono.just(appliedCount);
                                }

                                StoredCognicaRequest rerunAssumptionRequest = buildExecutableRequest(relevantAssumptionDp, assumptionQuery);
                                return executeAgainstCognica(rerunAssumptionRequest)
                                    .map(rerunAssumptionResponse -> {
                                        Boolean rerunInferredFactValue = inferFactValueFromPrediction(rerunAssumptionResponse);

                                        Map<String, Object> rerunStep = new LinkedHashMap<>();
                                        rerunStep.put("assumptionQuery", assumptionQuery);
                                        rerunStep.put("requesterDpId", originalStored.getId());
                                        rerunStep.put("requesterDpName", originalStored.getName());
                                        rerunStep.put("assumptionDpId", relevantAssumptionDp.getId());
                                        rerunStep.put("assumptionDpName", relevantAssumptionDp.getName());
                                        rerunStep.put("assumptionResponse", rerunAssumptionResponse);

                                        if (rerunInferredFactValue == null) {
                                            rerunStep.put("status", "no-definite-answer");
                                            rerunStep.put("reason", "Still Maybe after nested resolution rerun.");
                                            resolutionSteps.add(rerunStep);
                                            return appliedCount;
                                        }

                                        boolean updatedOriginal = updateFactValue(executionFacts, assumptionQuery, rerunInferredFactValue);
                                        if (!updatedOriginal) {
                                            rerunStep.put("status", "update-failed");
                                            resolutionSteps.add(rerunStep);
                                            return appliedCount;
                                        }

                                        rerunStep.put("status", "applied");
                                        rerunStep.put("updatedFact", assumptionQuery);
                                        rerunStep.put("updatedFactValue", rerunInferredFactValue);
                                        resolutionSteps.add(rerunStep);

                                        result.put("updatedFact", assumptionQuery);
                                        result.put("updatedFactValue", rerunInferredFactValue);
                                        return appliedCount + 1;
                                    });
                            });
                    });
            });
        }

        return chain;
    }

    private Mono<AssumptionAttempt> queryAssumptionAcrossCandidates(
        List<StoredCognicaRequest> candidates,
        String assumptionQuery,
        int index,
        List<Map<String, Object>> attemptLogs
    ) {
        if (index >= candidates.size()) {
            return Mono.just(new AssumptionAttempt(null, null, attemptLogs));
        }

        StoredCognicaRequest candidate = candidates.get(index);
        StoredCognicaRequest assumptionRequest = buildExecutableRequest(candidate, assumptionQuery);

        return executeAgainstCognica(assumptionRequest)
            .flatMap(response -> {
                Map<String, Object> attempt = new LinkedHashMap<>();
                attempt.put("dpId", candidate.getId());
                attempt.put("dpName", candidate.getName());

                if (isUsableAssumptionResponse(response)) {
                    attempt.put("status", "selected");
                    attemptLogs.add(attempt);
                    return Mono.just(new AssumptionAttempt(candidate, response, attemptLogs));
                }

                attempt.put("status", "skipped-unusable-response");
                if (response != null) {
                    attempt.put("hasError", response.isHasError());
                    attempt.put("prediction", response.getCognicaPrediction());
                }
                attemptLogs.add(attempt);
                return queryAssumptionAcrossCandidates(candidates, assumptionQuery, index + 1, attemptLogs);
            })
            .onErrorResume(error -> {
                Map<String, Object> attempt = new LinkedHashMap<>();
                attempt.put("dpId", candidate.getId());
                attempt.put("dpName", candidate.getName());
                attempt.put("status", "skipped-error");
                attempt.put("reason", error.getMessage());
                attemptLogs.add(attempt);
                return queryAssumptionAcrossCandidates(candidates, assumptionQuery, index + 1, attemptLogs);
            });
    }

    private boolean isUsableAssumptionResponse(CognicaResponse response) {
        if (response == null || response.isHasError()) {
            return false;
        }

        boolean hasPrediction = response.getCognicaPrediction() != null && !response.getCognicaPrediction().isBlank();
        boolean hasExplanation = response.getCognicaSummaryExplanation() != null && !response.getCognicaSummaryExplanation().isBlank();
        return hasPrediction || hasExplanation;
    }

    private Mono<CognicaResponse> executeAgainstCognica(StoredCognicaRequest request) {
        return webClient.post()
            .uri(COGNICA_URL)
            .bodyValue(request)
            .retrieve()
            .toEntity(CognicaResponse.class)
            .map(response -> response.getBody());
    }

    private boolean isMaybe(CognicaResponse response) {
        return response != null
            && response.getCognicaPrediction() != null
            && "maybe".equalsIgnoreCase(response.getCognicaPrediction().trim());
    }

    private List<String> extractAssumptionQueries(String explanation, Set<String> attemptedAssumptions) {
        List<String> extracted = extractAssumptionQueries(explanation);
        List<String> filtered = new ArrayList<>();

        for (String assumption : extracted) {
            String key = assumption.trim().toLowerCase();
            if (!attemptedAssumptions.contains(key)) {
                filtered.add(assumption);
            }
        }

        return filtered;
    }

    private List<String> extractAssumptionQueries(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return java.util.Collections.emptyList();
        }

        LinkedHashSet<String> assumptions = new LinkedHashSet<>();

        Pattern multiQuotedPattern = Pattern.compile("(?i)assumption(?:\\(s\\))?.*?holds");
        Matcher multiQuotedMatcher = multiQuotedPattern.matcher(explanation);
        if (multiQuotedMatcher.find()) {
            Matcher quoteMatcher = Pattern.compile("[\\\"']([^\\\"']+)[\\\"']").matcher(explanation);
            while (quoteMatcher.find()) {
                String token = quoteMatcher.group(1).trim();
                if (!token.isBlank()) {
                    assumptions.add(token);
                }
            }
        }

        Pattern conditionalQuotedPattern = Pattern.compile("(?i)assumption(?:\\(s\\))?\\s+[\\\"']([^\\\"']+)[\\\"']\\s+holds");
        Matcher conditionalQuotedMatcher = conditionalQuotedPattern.matcher(explanation);
        while (conditionalQuotedMatcher.find()) {
            assumptions.add(conditionalQuotedMatcher.group(1).trim());
        }

        Pattern conditionalPlainPattern = Pattern.compile("(?i)assumption(?:\\(s\\))?\\s+([A-Za-z0-9_\\- ,]+?)\\s+holds");
        Matcher conditionalPlainMatcher = conditionalPlainPattern.matcher(explanation);
        while (conditionalPlainMatcher.find()) {
            addSplitAssumptions(assumptions, conditionalPlainMatcher.group(1));
        }

        Pattern quotedPattern = Pattern.compile("(?i)assumption\\s+of\\s+['\"]([^'\"]+)['\"]");
        Matcher quotedMatcher = quotedPattern.matcher(explanation);
        while (quotedMatcher.find()) {
            assumptions.add(quotedMatcher.group(1).trim());
        }

        Pattern plainPattern = Pattern.compile("(?i)assumption\\s+of\\s+([A-Za-z0-9_\\- ]+)");
        Matcher plainMatcher = plainPattern.matcher(explanation);
        while (plainMatcher.find()) {
            addSplitAssumptions(assumptions, plainMatcher.group(1));
        }

        return new ArrayList<>(assumptions);
    }

    private void addSplitAssumptions(Set<String> assumptions, String rawSegment) {
        if (rawSegment == null || rawSegment.isBlank()) {
            return;
        }

        String[] parts = rawSegment.split("(?i),|\\band\\b");
        for (String part : parts) {
            String normalized = part.replaceAll("^[\\s'\"]+|[\\s'\"]+$", "").trim();
            if (!normalized.isBlank()) {
                assumptions.add(normalized);
            }
        }
    }

    private Boolean inferFactValueFromPrediction(CognicaResponse response) {
        if (response == null || response.getCognicaPrediction() == null) {
            return null;
        }

        String prediction = response.getCognicaPrediction().trim().toLowerCase();
        if ("yes".equals(prediction)) {
            return true;
        }
        if ("no".equals(prediction)) {
            return false;
        }
        return null;
    }

    private String resolveMostPrevalentExplanation(CognicaResponse response, StoredCognicaRequest storedRequest) {
        List<String> candidates = new ArrayList<>();

        if (response != null && response.getCognicaSummaryExplanation() != null
            && !response.getCognicaSummaryExplanation().isBlank()) {
            candidates.add(response.getCognicaSummaryExplanation().trim());
        }

        String previousExplanation = getLastValidExplanation(storedRequest);
        if (previousExplanation != null && !previousExplanation.isBlank()) {
            candidates.add(previousExplanation.trim());
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String candidate : candidates) {
            counts.put(candidate, counts.getOrDefault(candidate, 0) + 1);
        }

        String best = null;
        int bestCount = -1;
        for (String candidate : new LinkedHashSet<>(candidates)) {
            int count = counts.getOrDefault(candidate, 0);
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }

        return best;
    }

    private List<StoredCognicaRequest> findRelevantDecisionProblemsForAssumption(
        StoredCognicaRequest original,
        String assumptionQuery,
        List<Fact> executionFacts
    ) {
        if (assumptionQuery == null || assumptionQuery.isBlank()) {
            return new ArrayList<>();
        }

        LinkedHashMap<Long, StoredCognicaRequest> ordered = new LinkedHashMap<>();

        // Highest priority: the DP explicitly linked via internalSourceDp on the matching fact.
        if (executionFacts != null) {
            for (Fact fact : executionFacts) {
                if (fact.getFact() != null
                        && fact.getFact().trim().equalsIgnoreCase(assumptionQuery.trim())
                        && fact.hasInternalSource()) {
                    try {
                        Long linkedId = Long.parseLong(fact.getInternalSourceDp().trim());
                        if (!linkedId.equals(original.getId())) {
                            storedRequestService.getRequestById(linkedId).ifPresent(dp -> {
                                if (dp.getId() != null) {
                                    ordered.put(dp.getId(), dp);
                                }
                            });
                        }
                    } catch (NumberFormatException ignored) {
                        // non-numeric internalSourceDp — skip direct lookup, fall through to keyword search
                    }
                    break;
                }
            }
        }

        List<StoredCognicaRequest> relevant = storedRequestService.searchByQueryKeywords(assumptionQuery);
        for (StoredCognicaRequest candidate : relevant) {
            if (candidate.getId() != null
                && original.getId() != null
                && !candidate.getId().equals(original.getId())) {
                ordered.putIfAbsent(candidate.getId(), candidate);
            }
        }

        // Fallback candidates: any other stored DP, preserving existing update recency ordering.
        List<StoredCognicaRequest> all = storedRequestService.getAllRequests();
        for (StoredCognicaRequest candidate : all) {
            if (candidate.getId() != null
                && original.getId() != null
                && !candidate.getId().equals(original.getId())) {
                ordered.putIfAbsent(candidate.getId(), candidate);
            }
        }

        return new ArrayList<>(ordered.values());
    }

    private static class AssumptionAttempt {
        private final StoredCognicaRequest dp;
        private final CognicaResponse response;
        private final List<Map<String, Object>> attemptLogs;

        private AssumptionAttempt(StoredCognicaRequest dp, CognicaResponse response, List<Map<String, Object>> attemptLogs) {
            this.dp = dp;
            this.response = response;
            this.attemptLogs = attemptLogs;
        }
    }

    private String getLastValidExplanation(StoredCognicaRequest storedRequest) {
        if (storedRequest == null || storedRequest.getLastResponseJson() == null || storedRequest.getLastResponseJson().isBlank()) {
            return null;
        }

        try {
            CognicaResponse previousResponse = objectMapper.readValue(storedRequest.getLastResponseJson(), CognicaResponse.class);
            if (previousResponse.getCognicaSummaryExplanation() == null || previousResponse.getCognicaSummaryExplanation().isBlank()) {
                return null;
            }
            return previousResponse.getCognicaSummaryExplanation();
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private boolean updateFactValue(List<Fact> executionFacts, String assumptionFact, boolean factValue) {
        if (executionFacts == null || assumptionFact == null) {
            return false;
        }

        String normalizedFact = assumptionFact.trim();
        if (normalizedFact.isBlank()) {
            return false;
        }

        boolean found = false;
        for (Fact fact : executionFacts) {
            if (fact.getFact() != null && fact.getFact().trim().equalsIgnoreCase(normalizedFact)) {
                fact.setFactStatus(factValue ? "true" : "false");
                fact.setFactHold(factValue);
                found = true;
                break;
            }
        }

        if (!found) {
            executionFacts.add(new Fact(normalizedFact, factValue, factValue ? "true" : "false", "external"));
        }

        return true;
    }

    private void saveLastResponse(Long id, StoredCognicaRequest storedRequest, CognicaResponse response) {
        if (response == null) {
            return;
        }

        String fallbackExplanation = getLastValidExplanation(storedRequest);
        if ((response.getCognicaSummaryExplanation() == null || response.getCognicaSummaryExplanation().isBlank())
            && fallbackExplanation != null) {
            response.setCognicaSummaryExplanation(fallbackExplanation);
        }

        try {
            storedRequestService.updateLastResponse(id, objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException exception) {
            storedRequestService.updateLastResponse(id, String.valueOf(response));
        }
    }

    private StoredCognicaRequest buildExecutableRequest(StoredCognicaRequest storedRequest, String executionQuery) {
        return buildExecutableRequest(storedRequest, executionQuery, prepareExecutionFacts(storedRequest));
    }

    private StoredCognicaRequest buildExecutableRequest(
        StoredCognicaRequest storedRequest,
        String executionQuery,
        List<Fact> executionFacts
    ) {
        StoredCognicaRequest executableRequest = new StoredCognicaRequest();
        executableRequest.setActionType(storedRequest.getActionType());
        executableRequest.setPolicyInCognicaDPCNL(storedRequest.getPolicyInCognicaDPCNL());
        executableRequest.setQuery(executionQuery);
        executableRequest.setComplementaryOptions(parseOptions(storedRequest.getComplementaryOptionsJson()));

        List<Fact> factsCopy = new ArrayList<>();
        if (executionFacts != null) {
            for (Fact fact : executionFacts) {
                Fact copy = new Fact(
                    fact.getFact(),
                    fact.isFactHold(),
                    fact.getFactStatus(),
                    fact.getFactType()
                );
                // Preserve source tracking fields
                if (fact.hasRpSource()) {
                    copy.setRpSource(fact.getRpSource());
                }
                if (fact.hasInternalSource()) {
                    copy.setInternalSourceDp(fact.getInternalSourceDp());
                }
                factsCopy.add(copy);
            }
        }
        executableRequest.setFacts(factsCopy);
        return executableRequest;
    }

    private List<Fact> prepareExecutionFacts(StoredCognicaRequest storedRequest) {
        List<Fact> originalFacts = parseFacts(storedRequest.getFactsJson());
        List<Fact> executionFacts = new ArrayList<>();

        for (Fact originalFact : originalFacts) {
            boolean isInternal = "internal".equalsIgnoreCase(originalFact.getFactType());
            String storedStatus = originalFact.getFactStatus() != null ? originalFact.getFactStatus() : "unknown";
            // If the user explicitly set an internal fact to true/false, honour it — don't auto-reset.
            boolean userOverridden = isInternal
                && ("true".equalsIgnoreCase(storedStatus) || "false".equalsIgnoreCase(storedStatus));

            Fact execFact = new Fact(
                originalFact.getFact(),
                userOverridden ? originalFact.isFactHold() : (isInternal ? false : originalFact.isFactHold()),
                userOverridden ? storedStatus : (isInternal ? "unknown" : storedStatus),
                originalFact.getFactType()
            );
            if (originalFact.hasRpSource()) {
                execFact.setRpSource(originalFact.getRpSource());
            }
            if (originalFact.hasInternalSource()) {
                execFact.setInternalSourceDp(originalFact.getInternalSourceDp());
            }
            executionFacts.add(execFact);
        }

        return executionFacts;
    }

    private String normalizeStoredQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private String resolveExecutionQuery(String storedQuery, String overrideQuery) {
        if (overrideQuery != null && !overrideQuery.trim().isEmpty()) {
            return overrideQuery.trim();
        }
        return normalizeStoredQuery(storedQuery);
    }

    private String normalizeJsonArray(String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return "[]";
        }
        return jsonText;
    }

    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                optionsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid complementary options JSON", exception);
        }
    }

    private List<Fact> parseFacts(String factsJson) {
        if (factsJson == null || factsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                factsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Fact.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid facts JSON", exception);
        }
    }

    private List<String> extractMissingFactsFromPolicy(String policy, String query, List<Fact> providedFacts) {
        if (policy == null || policy.isBlank() || query == null || query.isBlank()) {
            return new ArrayList<>();
        }

        Set<String> providedFactNames = new HashSet<>();
        if (providedFacts != null) {
            for (Fact fact : providedFacts) {
                if (fact.getFact() != null && !fact.getFact().isBlank()) {
                    providedFactNames.add(normalizeFactName(fact.getFact()).toLowerCase());
                }
            }
        }

        Set<String> requiredFacts = new HashSet<>();
        String normalizedQuery = normalizeFactName(query).toLowerCase();

        String[] rules = policy.split("\\n");
        for (String rule : rules) {
            String trimmedRule = rule.trim();
            if (trimmedRule.isEmpty()) {
                continue;
            }

            String upperRule = trimmedRule.toUpperCase();
            int thenIndex = upperRule.indexOf(" THEN ");
            if (thenIndex < 0) {
                continue;
            }

            String conclusion = trimmedRule.substring(thenIndex + 6).trim();
            String normalizedConclusion = normalizeFactName(conclusion).toLowerCase();

            if (normalizedConclusion.equals(normalizedQuery)) {
                String condition = trimmedRule.substring(0, thenIndex).trim();
                if (condition.toUpperCase().startsWith("IF ")) {
                    condition = condition.substring(3).trim();
                }

                extractFactsFromCondition(condition, requiredFacts);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String required : requiredFacts) {
            String requiredLower = required.toLowerCase();
            boolean found = false;
            for (String provided : providedFactNames) {
                if (provided.equals(requiredLower)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                missing.add(required);
            }
        }

        return missing;
    }

    private void extractFactsFromCondition(String condition, Set<String> factNames) {
        if (condition == null || condition.isBlank()) {
            return;
        }

        String working = condition;
        working = working.replaceAll("(?i)\\bAND\\b", " ");
        working = working.replaceAll("(?i)\\bOR\\b", " ");
        working = working.replaceAll("(?i)\\bNOT\\b", " ");
        working = working.replaceAll("[()]+", " ");

        String[] tokens = working.split("\\s+");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && !isKeyword(trimmed)) {
                factNames.add(trimmed);
            }
        }
    }

    private boolean isKeyword(String token) {
        String upper = token.toUpperCase();
        return upper.equals("TRUE") || upper.equals("FALSE") ||
               upper.equals("IF") || upper.equals("THEN") ||
               upper.equals("AND") || upper.equals("OR") ||
               upper.equals("NOT");
    }

    private String normalizeFactName(String fact) {
        if (fact == null) {
            return "";
        }
        return fact.trim()
            .replaceAll("['\"]", "")
            .replaceAll("\\s+", "_");
    }
}