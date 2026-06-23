package com.example.app.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleTransformer {

    private static final Pattern THEN_PATTERN = Pattern.compile("(?i)\\bTHEN\\b");
    private static final Pattern OPERATOR_PATTERN = Pattern.compile("\\(|\\)|\\bAND\\b|\\bOR\\b|\\bNOT\\b", Pattern.CASE_INSENSITIVE);
    private RuleTransformer() {
        // Utility class
    }

    public static String transformPolicyText(String policyText) {
        if (policyText == null || policyText.isBlank()) {
            throw new IllegalArgumentException("Policy text is required.");
        }
        List<String> lines = transformPolicyLines(policyText);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Policy text is required.");
        }
        return String.join("\n", lines);
    }

    public static List<String> transformPolicyLines(String policyText) {
        if (policyText == null || policyText.isBlank()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        String[] rawLines = policyText.split("\\r?\\n");

        for (String rawLine : rawLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.endsWith(".")) {
                line = line.substring(0, line.length() - 1).trim();
            }
            result.addAll(transformRuleLine(line));
        }

        return result;
    }

    private static List<String> transformRuleLine(String line) {
        String upper = line.toUpperCase();
        if (upper.startsWith("NORMALLY ")) {
            String conclusion = line.substring("NORMALLY ".length()).trim();
            return transformRule("true", conclusion, false);
        }

        if (upper.startsWith("ONLY IF ") || upper.startsWith("IF ")) {
            int thenIndex = findThenIndex(line);
            if (thenIndex < 0) {
                throw new IllegalArgumentException("Missing THEN clause in rule: " + line);
            }

            String conditionPart = line.substring(0, thenIndex).trim();
            String conclusionPart = line.substring(thenIndex + 4).trim();

            boolean isOnlyIf;
            if (conditionPart.toUpperCase().startsWith("ONLY IF ")) {
                conditionPart = conditionPart.substring("ONLY IF ".length()).trim();
                isOnlyIf = true;
            } else {
                conditionPart = conditionPart.substring("IF ".length()).trim();
                isOnlyIf = false;
            }

            return transformRule(conditionPart, conclusionPart, isOnlyIf);
        }

        throw new IllegalArgumentException("Unsupported rule format: " + line);
    }

    private static int findThenIndex(String line) {
        Matcher matcher = THEN_PATTERN.matcher(line);
        while (matcher.find()) {
            int index = matcher.start();
            String before = line.substring(0, index).trim();
            String after = line.substring(matcher.end()).trim();
            if (!before.isEmpty() && !after.isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> transformRule(String conditionText, String conclusionText, boolean isOnlyIf) {
        String normalizedCondition = normalizeCondition(conditionText);
        Expr conditionExpr = ConditionParser.parse(normalizedCondition);
        Expr conditionNnf = conditionExpr.toNNF();
        List<List<String>> conditionGroups = conditionNnf.toDNF();

        List<String> conclusions = splitConclusions(conclusionText);
        List<String> rules = new ArrayList<>();
        String prefix = isOnlyIf ? "ONLY IF " : "IF ";

        for (List<String> conditionGroup : conditionGroups) {
            String conditionClause = conditionGroup.isEmpty() ? "true" : String.join(" AND ", conditionGroup);
            for (String conclusion : conclusions) {
                rules.add(prefix + conditionClause + " THEN " + conclusion);
            }
        }

        return rules;
    }

    private static String normalizeCondition(String conditionText) {
        if (conditionText == null || conditionText.isBlank()) {
            return "true";
        }
        String result = conditionText.trim();
        result = result.replaceAll("(?i)\\bUNLESS\\b", "AND NOT");
        return result;
    }

    private static List<String> splitConclusions(String conclusionText) {
        if (conclusionText == null || conclusionText.isBlank()) {
            throw new IllegalArgumentException("Empty conclusion in rule.");
        }

        List<String> branches = splitTopLevelOperators(conclusionText, "OR");
        List<String> normalized = new ArrayList<>();
        for (String branch : branches) {
            String outcome = branch.trim();
            if (outcome.isEmpty()) {
                continue;
            }
            normalized.add(normalizeOutcome(outcome));
        }
        return normalized;
    }

    private static String normalizeOutcome(String outcomeText) {
        List<String> pieces = splitTopLevelOperators(outcomeText, "AND");
        if (pieces.size() <= 1) {
            return outcomeText.trim();
        }

        List<String> normalizedPieces = new ArrayList<>();
        for (String piece : pieces) {
            normalizedPieces.add(piece.trim());
        }
        return String.join(" AND ", normalizedPieces);
    }

    private static List<String> splitTopLevelOperators(String text, String operator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        String upper = text.toUpperCase();
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0) {
                String token = operator.equals("OR") ? " OR " : " AND ";
                if (upper.startsWith(token, i)) {
                    parts.add(text.substring(start, i));
                    start = i + token.length();
                    i += token.length() - 1;
                }
            }
        }
        parts.add(text.substring(start));
        return parts;
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = OPERATOR_PATTERN.matcher(input);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String literal = input.substring(lastEnd, matcher.start()).trim();
                if (!literal.isEmpty()) {
                    tokens.add(literal);
                }
            }
            String operator = matcher.group().toUpperCase().trim();
            tokens.add(operator);
            lastEnd = matcher.end();
        }
        if (lastEnd < input.length()) {
            String literal = input.substring(lastEnd).trim();
            if (!literal.isEmpty()) {
                tokens.add(literal);
            }
        }
        return tokens;
    }

    private interface Expr {
        Expr toNNF();
        Expr negate();
        List<List<String>> toDNF();
    }

    private static final class Literal implements Expr {
        private final String text;
        private final boolean negated;

        private Literal(String text, boolean negated) {
            this.text = text.trim();
            this.negated = negated;
        }

        @Override
        public Expr toNNF() {
            return this;
        }

        @Override
        public Expr negate() {
            return new Literal(text, !negated);
        }

        @Override
        public List<List<String>> toDNF() {
            String value = negated ? "NOT " + text : text;
            return Collections.singletonList(Collections.singletonList(value));
        }
    }

    private static final class Not implements Expr {
        private final Expr inner;

        private Not(Expr inner) {
            this.inner = inner;
        }

        @Override
        public Expr toNNF() {
            return inner.negate();
        }

        @Override
        public Expr negate() {
            return inner.toNNF();
        }

        @Override
        public List<List<String>> toDNF() {
            return toNNF().toDNF();
        }
    }

    private static final class And implements Expr {
        private final List<Expr> children;

        private And(List<Expr> children) {
            this.children = children;
        }

        @Override
        public Expr toNNF() {
            List<Expr> normalized = new ArrayList<>();
            for (Expr child : children) {
                normalized.add(child.toNNF());
            }
            return new And(normalized);
        }

        @Override
        public Expr negate() {
            List<Expr> negatedChildren = new ArrayList<>();
            for (Expr child : children) {
                negatedChildren.add(child.negate());
            }
            return new Or(negatedChildren);
        }

        @Override
        public List<List<String>> toDNF() {
            List<List<String>> result = new ArrayList<>();
            result.add(new ArrayList<>());
            for (Expr child : children) {
                List<List<String>> childDNF = child.toDNF();
                List<List<String>> combined = new ArrayList<>();
                for (List<String> base : result) {
                    for (List<String> addition : childDNF) {
                        List<String> merged = new ArrayList<>(base);
                        merged.addAll(addition);
                        combined.add(merged);
                    }
                }
                result = combined;
            }
            return result;
        }
    }

    private static final class Or implements Expr {
        private final List<Expr> children;

        private Or(List<Expr> children) {
            this.children = children;
        }

        @Override
        public Expr toNNF() {
            List<Expr> normalized = new ArrayList<>();
            for (Expr child : children) {
                normalized.add(child.toNNF());
            }
            return new Or(normalized);
        }

        @Override
        public Expr negate() {
            List<Expr> negatedChildren = new ArrayList<>();
            for (Expr child : children) {
                negatedChildren.add(child.negate());
            }
            return new And(negatedChildren);
        }

        @Override
        public List<List<String>> toDNF() {
            List<List<String>> result = new ArrayList<>();
            for (Expr child : children) {
                result.addAll(child.toDNF());
            }
            return result;
        }
    }

    private static final class ConditionParser {
        private final List<String> tokens;
        private int index;

        private ConditionParser(List<String> tokens) {
            this.tokens = tokens;
            this.index = 0;
        }

        public static Expr parse(String expression) {
            List<String> tokens = tokenize(expression);
            ConditionParser parser = new ConditionParser(tokens);
            Expr parsed = parser.parseOr();
            if (parser.hasNext()) {
                throw new IllegalArgumentException("Unexpected token in condition: " + parser.peek());
            }
            return parsed;
        }

        private Expr parseOr() {
            Expr left = parseAnd();
            while (match("OR")) {
                Expr right = parseAnd();
                left = new Or(List.of(left, right));
            }
            return left;
        }

        private Expr parseAnd() {
            Expr left = parseNot();
            while (match("AND")) {
                Expr right = parseNot();
                left = new And(List.of(left, right));
            }
            return left;
        }

        private Expr parseNot() {
            if (match("NOT")) {
                return new Not(parseNot());
            }
            if (match("(")) {
                Expr expr = parseOr();
                if (!match(")")) {
                    throw new IllegalArgumentException("Missing closing parenthesis in condition.");
                }
                return expr;
            }
            return parseLiteral();
        }

        private Expr parseLiteral() {
            if (!hasNext()) {
                throw new IllegalArgumentException("Expected literal in condition.");
            }
            String token = next();
            if (token.isBlank()) {
                throw new IllegalArgumentException("Empty literal in condition.");
            }
            return new Literal(token.trim(), false);
        }

        private boolean match(String expected) {
            if (!hasNext()) {
                return false;
            }
            String token = peek();
            if (token.equalsIgnoreCase(expected)) {
                index++;
                return true;
            }
            return false;
        }

        private String peek() {
            return tokens.get(index);
        }

        private String next() {
            return tokens.get(index++);
        }

        private boolean hasNext() {
            return index < tokens.size();
        }
    }
}
