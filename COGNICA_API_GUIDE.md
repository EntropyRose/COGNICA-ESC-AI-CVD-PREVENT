# Cognica API Integration Guide

This document explains how to use the Cognica Decision Support System API in your application.

## Overview

The Cognica API provides decision support by evaluating policies and facts to generate predictions and explanations. This integration includes:

- **Backend**: Spring Boot REST controller with type-safe DTOs
- **Frontend**: JavaScript utilities for API calls and UI components

## Backend Setup

### Required Dependencies

Whether you use Gradle or Maven, you need the Spring Web starter and Jackson databind.  

*Gradle* (in `build.gradle`):

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    // plus test dependencies
}
```

*If you still prefer Maven*, add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

### Configuration

1. **Update Credentials** in `CognicaController.java`:
   ```java
   headers.setBasicAuth("YOUR_USERNAME", "YOUR_PASSWORD");
   ```

2. **Server Configuration** (already set in `application.properties`):
   ```properties
   server.port=8080
   ```

### Backend Classes

#### `Fact.java`
Represents a single fact with:
- `fact` (String): Description of the fact
- `factHold` (boolean): Whether the fact is true

#### `CognicaRequest.java`
The complete request object containing:
- `actionType`: "getPrediction"
- `policyInCognicaDPCNL`: Policy in Cognica DP CNL format
- `query`: The query to evaluate
- `complementaryOptions`: List of possible outcomes
- `facts`: List of Fact objects

#### `CognicaResponse.java`
The response from Cognica containing:
- `hasError`: Whether an error occurred
- `cognicaPrediction`: The prediction result ("Yes", "No", "Maybe", etc.)
- `cognicaSummaryExplanation`: Explanation of the prediction
- `errorMessage`: Error details if applicable

#### `CognicaController.java`
REST endpoint:
- **Method**: POST `/api/predict`
- **Input**: CognicaRequest JSON
- **Output**: CognicaResponse JSON
- **Error Handling**: Returns appropriate HTTP status codes

## Frontend Usage

### Basic Usage

Include `app.js` in your HTML:
```html
<script src="app.js"></script>
```

### API Functions

#### 1. `getPrediction(cognicaRequest)`
Make a prediction request to the backend.

```javascript
const request = {
    actionType: 'getPrediction',
    policyInCognicaDPCNL: 'NORMALLY sell the product at its high price...',
    query: 'sell the product at the lower price',
    complementaryOptions: ['sell high', 'sell low'],
    facts: [
        { fact: 'payment is in cash', factHold: true },
        { fact: 'buyer is regular', factHold: true }
    ]
};

const response = await window.cognicaAPI.getPrediction(request);
```

#### 2. `createCognicaRequest(policy, query, options, facts)`
Helper to create a properly formatted request object.

```javascript
const request = window.cognicaAPI.createCognicaRequest(
    'IF X THEN Y...',
    'Y should happen',
    ['Y happens', 'Y does not happen'],
    [{fact: 'X is true', factHold: true}]
);
```

#### 3. `displayPrediction(elementId, response)`
Display prediction result in a DOM element.

```javascript
window.cognicaAPI.displayPrediction('resultDiv', response);
```

#### 4. `getSellerDecision()`
Pre-built example for seller assistant scenario.

```javascript
const response = await window.cognicaAPI.getSellerDecision();
```

## Example: Seller Assistant

**Scenario**: Determine optimal pricing strategy based on customer and market conditions.

**Policy**:
```
NORMALLY sell the product at its high price.
ONLY IF payment is in cash THEN MAYBE sell the product at the lower price.
IF the buyer is a regular customer THEN sell the product at the lower price.
IF the buyer can have late delivery THEN sell the product at the lower price.
IF it is high season THEN ALWAYS sell the product at its high price.
```

**Facts**:
- Payment is in cash: TRUE
- Buyer is regular customer: TRUE
- High season: FALSE

**Query**: "sell the product at the lower price"

**Expected Response**:
```json
{
    "hasError": false,
    "cognicaPrediction": "Yes",
    "cognicaSummaryExplanation": "The given fact(s), \"The buyer is a regular customer\" supports the conclusion, \"Sell the product at the lower price\"."
}
```

## HTML Interface

The `DecisionProblems.html` file provides:

1. **Seller Assistant Section**: Quick test with pre-configured example
2. **Custom Decision Section**: 
   - Policy input (textarea)
   - Query input
   - Complementary options
   - Dynamic fact management
   - Results display

## Error Handling

### Backend Errors

- **SERVICE_UNAVAILABLE (503)**: Cannot connect to Cognica API
- **INTERNAL_SERVER_ERROR (500)**: Other processing errors

Response:
```json
{
    "hasError": true,
    "errorMessage": "Failed to connect to Cognica API: ..."
}
```

### Frontend Handling

The JavaScript functions automatically catch errors and return error responses:

```javascript
const response = await window.cognicaAPI.getPrediction(request);
if (response.hasError) {
    console.error('Error:', response.errorMessage);
}
```

## API Specifications

**API Endpoint**: `https://cognica.cs.ucy.ac.cy/COGNICAd/api.php`

**Authentication**: HTTP Basic Authentication

**Method**: POST

**Content-Type**: application/json

## Policy Language (Cognica DP CNL)

Valid modifiers:
- **NORMALLY**: Default behavior
- **ONLY IF**: Conditional (restrictive)
- **IF...THEN**: Standard conditional
- **ALWAYS**: Mandatory
- **MAYBE**: Optional

Example constructs:
```
NORMALLY action1.
IF condition THEN action2.
ONLY IF condition THEN MAYBE action3.
IF condition THEN ALWAYS action4.
```

## Running the Application

1. **Start Backend** (using Gradle):
   ```bash
   cd Backend
   ./gradlew bootRun   # on Windows use `gradlew.bat bootRun`
   ```

2. **Open Frontend**:
   Open `Frontend/DecisionProblems.html` in a web browser

3. **Test**:
   - Click "Get Pricing Recommendation" for the seller assistant example
   - Or fill in custom policy and facts to test other scenarios

## Notes

- Update Cognica API credentials in `CognicaController.java`
- Frontend assumes backend runs on `http://localhost:8080`
- Adjust `API_BASE_URL` in `app.js` if backend is on a different host/port
- Keep policies clear and well-defined for best results
- Facts must match the decision context/policy language
