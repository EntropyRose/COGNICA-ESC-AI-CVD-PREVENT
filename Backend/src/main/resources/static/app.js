/**
 * Cognica API Integration
 * Frontend JavaScript for interacting with the Cognica Decision Support System
 */

const API_BASE_URL = '/api';

/**
 * Send a prediction request to the Cognica backend API
 * @param {Object} cognicaRequest - The request object containing policy, facts, and query
 * @returns {Promise<Object>} The Cognica prediction response
 */
async function getPrediction(cognicaRequest) {
    try {
        const response = await fetch(`${API_BASE_URL}/predict`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(cognicaRequest)
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        return data;
    } catch (error) {
        console.error('Error calling Cognica API:', error);
        const friendlyMessage = error.message === 'Failed to fetch'
            ? 'Failed to fetch. Check that the backend is running and open the app from http://localhost:8080.'
            : error.message;
        return {
            hasError: true,
            errorMessage: friendlyMessage
        };
    }
}

/**
 * Example: Seller Assistant Decision Making
 * Determines the best price to offer based on policies and facts
 */
async function getSellerDecision() {
    const cognicaRequest = {
        actionType: 'getPrediction',
        policyInCognicaDPCNL: `NORMALLY sell the product at its high price.
ONLY IF payment is in cash THEN MAYBE sell the product at the lower price.
IF the buyer is a regular customer THEN sell the product at the lower price.
IF the buyer can have late delivery THEN sell the product at the lower price.
IF it is high season THEN ALWAYS sell the product at its high price.`,
        query: 'sell the product at the lower price',
        complementaryOptions: [
            'sell the product at its high price',
            'sell the product at the lower price'
        ],
        facts: [
            { fact: 'payment is in cash', factHold: true, factType: 'external' },
            { fact: 'the buyer is a regular customer', factHold: true, factType: 'external' },
            { fact: 'it is high season', factHold: false, factType: 'external' },
            { fact: 'the buyer can have late delivery', factHold: true, factType: 'internal' }
        ]
    };

    const response = await getPrediction(cognicaRequest);
    
    if (response.hasError) {
        console.error('Error:', response.errorMessage);
        return null;
    }

    console.log('Prediction:', response.cognicaPrediction);
    console.log('Explanation:', response.cognicaSummaryExplanation);
    
    return response;
}

/**
 * Helper function to create a Cognica request object
 * @param {string} policy - The policy in Cognica DP CNL format
 * @param {string} query - The query to evaluate
 * @param {Array<string>} options - The complementary options
 * @param {Array<{fact: string, factHold: boolean, factType?: string}>} facts - The facts about the scenario
 * @returns {Object} A properly formatted CognicaRequest object
 */
function createCognicaRequest(policy, query, options, facts) {
    return {
        actionType: 'getPrediction',
        policyInCognicaDPCNL: policy,
        query: query,
        complementaryOptions: options,
        facts: facts
    };
}

/**
 * DOM Helper: Display Cognica prediction result on the page
 * @param {string} elementId - The ID of the element to update
 * @param {Object} response - The Cognica API response
 */
function displayPrediction(elementId, response) {
    const element = document.getElementById(elementId);
    if (!element) {
        console.warn(`Element with ID "${elementId}" not found`);
        return;
    }

    if (response.hasError) {
        element.innerHTML = `
            <div class="error">
                <strong>Error:</strong> ${response.errorMessage}
            </div>
        `;
        return;
    }

    element.innerHTML = `
        <div class="prediction-result">
            <div class="prediction">
                <strong>Prediction:</strong> ${response.cognicaPrediction}
            </div>
            <div class="explanation">
                <strong>Explanation:</strong> ${response.cognicaSummaryExplanation}
            </div>
        </div>
    `;
}

/**
 * Save a stored request to the database
 * @param {string} name - Name of the request
 * @param {string} naturalLanguage - Natural language description of the request
 * @param {string} policy - The policy in Cognica DP CNL format
 * @param {string} query - The query to evaluate
 * @param {Array<string>} options - The complementary options
 * @param {Array<{fact: string, factHold: boolean}>} facts - The facts about the scenario
 * @returns {Promise<Object>} The saved request from the server
 */
async function saveStoredRequest(name, naturalLanguage, policy, query, options, facts) {
    try {
        const request = {
            name: name,
            naturalLanguage: naturalLanguage,
            policyInCognicaDPCNL: policy,
            query: query,
            complementaryOptionsJson: JSON.stringify(options),
            factsJson: JSON.stringify(facts)
        };

        const response = await fetch(`${API_BASE_URL}/stored-requests/save`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            let reason = `HTTP error! status: ${response.status}`;
            try {
                const body = await response.json();
                if (body && body.error) reason = body.error;
            } catch {}
            throw new Error(reason);
        }

        const data = await response.json();
        return {
            success: true,
            data: data
        };
    } catch (error) {
        console.error('Error saving request:', error);
        return {
            success: false,
            error: error.message
        };
    }
}

/**
 * Update an existing stored request in the database
 * @param {number} id - Database ID of the request
 * @param {string} name - Name of the request
 * @param {string} naturalLanguage - Natural language description of the request
 * @param {string} policy - The policy in Cognica DP CNL format
 * @param {string} query - The query to evaluate
 * @param {Array<string>} options - The complementary options
 * @param {Array<{fact: string, factHold: boolean}>} facts - The facts about the scenario
 * @returns {Promise<Object>} The updated request from the server
 */
async function updateStoredRequest(id, name, naturalLanguage, policy, query, options, facts) {
    try {
        const request = {
            name: name,
            naturalLanguage: naturalLanguage,
            policyInCognicaDPCNL: policy,
            query: query,
            complementaryOptionsJson: JSON.stringify(options),
            factsJson: JSON.stringify(facts)
        };

        const response = await fetch(`${API_BASE_URL}/stored-requests/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(request)
        });

        if (!response.ok) {
            let reason = `HTTP error! status: ${response.status}`;
            try {
                const body = await response.json();
                if (body && body.error) reason = body.error;
            } catch {}
            throw new Error(reason);
        }

        const data = await response.json();
        return {
            success: true,
            data: data
        };
    } catch (error) {
        console.error('Error updating request:', error);
        return {
            success: false,
            error: error.message
        };
    }
}

/**
 * Get all stored requests from the database
 * @returns {Promise<Array>} Array of stored decision problems
 */
async function getAllStoredRequests() {
    try {
        const response = await fetch(`${API_BASE_URL}/stored-requests`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error('Error fetching stored requests:', error);
        return [];
    }
}

/**
 * Delete a stored request from the database
 * @param {number} id - Database ID of the request to delete
 * @returns {Promise<Object>} Success/error result
 */
async function deleteStoredRequest(id) {
    try {
        const response = await fetch(`${API_BASE_URL}/stored-requests/${id}`, {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json',
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        return {
            success: true
        };
    } catch (error) {
        console.error('Error deleting request:', error);
        return {
            success: false,
            error: error.message
        };
    }
}

function escapeHtmlText(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
}

function parseStoredJson(jsonText, fallbackValue) {
        if (!jsonText) {
                return fallbackValue;
        }

        try {
                return JSON.parse(jsonText);
        } catch (error) {
                return fallbackValue;
        }
}

function buildDecisionProblemDetailsHtml(request, interactive) {
        const complementaryOptions = parseStoredJson(request?.complementaryOptionsJson, []);
        const facts = parseStoredJson(request?.factsJson, []);
        const isInteractive = !!interactive;

        const factsRows = facts.map((factItem, index) => {
            const status = factItem.factStatus || (factItem.factHold ? 'true' : 'unknown');
            const statusCell = isInteractive
                ? `<td>
                    <div class="fact-toggle-group">
                        <button class="fact-toggle-btn ftog-true${status === 'true' ? ' ftog-active' : ''}"
                                onclick="setFactToggle(${index},'true')" title="Set True">T</button>
                        <button class="fact-toggle-btn ftog-unknown${status === 'unknown' ? ' ftog-active' : ''}"
                                onclick="setFactToggle(${index},'unknown')" title="Set Unknown">?</button>
                        <button class="fact-toggle-btn ftog-false${status === 'false' ? ' ftog-active' : ''}"
                                onclick="setFactToggle(${index},'false')" title="Set False">F</button>
                    </div>
                   </td>`
                : `<td class="${status === 'true' ? 'fact-true' : status === 'false' ? 'fact-false' : 'fact-unknown'}">
                       ${escapeHtmlText(status)}
                   </td>`;

            return `<tr class="fact-row" data-fact-index="${index}" data-fact-name="${escapeHtmlText(factItem.fact || '')}">
                        <td>${escapeHtmlText(factItem.fact || '')}</td>
                        <td>${escapeHtmlText(factItem.factType || 'external')}</td>
                        ${statusCell}
                        <td>${escapeHtmlText(factItem.internalSourceDp || factItem.rpSource || '')}</td>
                    </tr>`;
        }).join('');

        const factsHtml = facts.length > 0
            ? `<table class="facts-table">
                   <thead><tr>
                       <th>Fact</th><th>Type</th>
                       <th>Status${isInteractive ? ' <span style="font-weight:400;font-size:0.78rem;">(T / ? / F)</span>' : ''}</th>
                       <th>Source DP</th>
                   </tr></thead>
                   <tbody>${factsRows}</tbody>
               </table>
               ${isInteractive ? `<div class="fact-editor-controls">
                   <button class="fact-reset-btn" onclick="resetAllFacts()">Reset all to Unknown</button>
                   <span id="pendingEditsNotice" class="pending-edits-notice" style="display:none;">&#9679; Unsaved &mdash; auto-saved on Run Query</span>
               </div>` : ''}`
            : '<p style="color: var(--muted);">No facts set.</p>';

        return `
            <div class="detail-section">
                <div class="detail-label">Natural Language</div>
                <div class="detail-content">${escapeHtmlText(request?.naturalLanguage || request?.description || 'No natural language text provided.')}</div>
            </div>

            <div class="detail-section">
                <div class="detail-label">Original Policy</div>
                <div class="detail-content">${escapeHtmlText(request?.originalPolicyInCognicaDPCNL || request?.policyInCognicaDPCNL || '')}</div>
            </div>

            <div class="detail-section">
                <div class="detail-label">Transformed Policy</div>
                <div class="detail-content">${escapeHtmlText(request?.policyInCognicaDPCNL || '')}</div>
            </div>

            <div class="detail-section">
                <div class="detail-label">Complementary Options</div>
                <div class="options-grid">
                    ${complementaryOptions.length > 0
                        ? complementaryOptions.map(opt => `<span class="option-badge clickable-option" data-option="${escapeHtmlText(opt)}">${escapeHtmlText(opt)}</span>`).join('')
                        : '<span style="color: var(--muted);">No complementary options set.</span>'}
                </div>
            </div>

            <div class="detail-section">
                <div class="detail-label">Facts</div>
                ${factsHtml}
            </div>
        `;
}

// Export functions for use in HTML page
window.cognicaAPI = {
    getPrediction: getPrediction,
    getSellerDecision: getSellerDecision,
    createCognicaRequest: createCognicaRequest,
    displayPrediction: displayPrediction,
    saveStoredRequest: saveStoredRequest,
    updateStoredRequest: updateStoredRequest,
    getAllStoredRequests: getAllStoredRequests,
    deleteStoredRequest: deleteStoredRequest
};

window.cognicaUI = {
    buildDecisionProblemDetailsHtml: buildDecisionProblemDetailsHtml
};
