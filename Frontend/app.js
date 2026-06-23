/**
 * Cognica API Integration
 * Frontend JavaScript for interacting with the Cognica Decision Support System
 */

const API_BASE_URL = 'http://localhost:8080/api';

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

// Export functions for use in HTML page
window.cognicaAPI = {
    getPrediction: getPrediction,
    getSellerDecision: getSellerDecision,
    createCognicaRequest: createCognicaRequest,
    displayPrediction: displayPrediction
};
