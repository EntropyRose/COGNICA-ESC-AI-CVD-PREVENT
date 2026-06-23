-- ============================================================
-- 02-guidelines-patch.sql
-- Fixes policy text errors and missing facts across all DPs.
-- Run against cognica_db, then repeat against cognica_clinical_db.
-- Safe to run multiple times (every statement is idempotent).
-- ============================================================

-- ---------------------------------------------------------------
-- FIX 1 (DP3): typo 'mellit0s' (digit zero) → 'mellitus'
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    policy_in_cognica_dp_cnl          = REPLACE(policy_in_cognica_dp_cnl,          'mellit0s', 'mellitus'),
    original_policy_in_cognica_dp_cnl = REPLACE(original_policy_in_cognica_dp_cnl, 'mellit0s', 'mellitus'),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%mellit0s%';

-- ---------------------------------------------------------------
-- FIX 2 (DP6): 'age_above_70' → 'age_70_or_over'
--   Rule text used a different name than the listed external factor.
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    policy_in_cognica_dp_cnl          = REPLACE(policy_in_cognica_dp_cnl,          'age_above_70', 'age_70_or_over'),
    original_policy_in_cognica_dp_cnl = REPLACE(original_policy_in_cognica_dp_cnl, 'age_above_70', 'age_70_or_over'),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%age_above_70%';

-- ---------------------------------------------------------------
-- FIX 3 (DP14): combined range typos in the high-normal BP rule
--   'SBP_<130-139' → 'SBP_130-139'
--   'DBP_<85-89'   → 'DBP_85-89'
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    policy_in_cognica_dp_cnl = REPLACE(
        REPLACE(policy_in_cognica_dp_cnl, 'SBP_<130-139', 'SBP_130-139'),
        'DBP_<85-89', 'DBP_85-89'
    ),
    original_policy_in_cognica_dp_cnl = REPLACE(
        REPLACE(original_policy_in_cognica_dp_cnl, 'SBP_<130-139', 'SBP_130-139'),
        'DBP_<85-89', 'DBP_85-89'
    ),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%SBP_<130-139%'
   OR policy_in_cognica_dp_cnl LIKE '%DBP_<85-89%';

-- ---------------------------------------------------------------
-- FIX 4 (DP6): add missing external fact 'secondary_prevention'
--   Used in PCSK9 rule: ONLY IF (established_ASCVD OR secondary_prevention) ...
--   Identified by: policy contains 'secondary_prevention'
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    facts_json = JSON_ARRAY_APPEND(
        facts_json,
        '$',
        CAST('{"fact":"secondary_prevention","factHold":false,"factStatus":"unknown","factType":"external"}' AS JSON)
    ),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%secondary_prevention%'
  AND JSON_SEARCH(facts_json, 'one', 'secondary_prevention', NULL, '$[*].fact') IS NULL;

-- ---------------------------------------------------------------
-- FIX 5 (DP6): add missing external fact 'ezetimibe_used'
--   Used in both PCSK9 rules: ... AND ezetimibe_used THEN Add_PCSK9_inhibitor
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    facts_json = JSON_ARRAY_APPEND(
        facts_json,
        '$',
        CAST('{"fact":"ezetimibe_used","factHold":false,"factStatus":"unknown","factType":"external"}' AS JSON)
    ),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%ezetimibe_used%'
  AND JSON_SEARCH(facts_json, 'one', 'ezetimibe_used', NULL, '$[*].fact') IS NULL;

-- ---------------------------------------------------------------
-- FIX 6 (DP8): add missing internal fact 'high_risk' (sourced from DP3)
--   Rule 2 checks: IF type_2_diabetes_mellitus AND age>40 AND high_risk ...
--   Identified by: policy contains 'LDL_reduction_gte_50' (DP8's conclusion)
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    facts_json = JSON_ARRAY_APPEND(
        facts_json,
        '$',
        CAST('{"fact":"high_risk","factHold":false,"factStatus":"unknown","factType":"internal"}' AS JSON)
    ),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%LDL_reduction_gte_50%'
  AND JSON_SEARCH(facts_json, 'one', 'high_risk', NULL, '$[*].fact') IS NULL;

-- ---------------------------------------------------------------
-- FIX 7 (DP11): add missing external fact 'TIA' (bare form)
--   Rule 5: IF (stroke OR TIA) AND BP>=140/90 THEN BP_lowering
--   The listed facts only had cardioembolic_TIA and non_cardioembolic_TIA.
--   Identified by: policy contains 'cerebrovascular_event' (DP11's trigger fact)
-- ---------------------------------------------------------------
UPDATE stored_cognica_requests
SET
    facts_json = JSON_ARRAY_APPEND(
        facts_json,
        '$',
        CAST('{"fact":"TIA","factHold":false,"factStatus":"unknown","factType":"external"}' AS JSON)
    ),
    updated_at = NOW()
WHERE policy_in_cognica_dp_cnl LIKE '%cerebrovascular_event%'
  AND JSON_SEARCH(facts_json, 'one', 'TIA', NULL, '$[*].fact') IS NULL;


-- ============================================================
-- To apply the same fixes to the clinical database, run:
--
--   USE cognica_clinical_db;
--   <paste all statements above>
--
-- ============================================================
