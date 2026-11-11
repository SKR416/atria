package io.github.skr416.atria.workflow.service;

import io.github.skr416.atria.workflow.model.ConditionOperator;
import io.github.skr416.atria.workflow.model.FlowNodeCondition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pure Java structured condition evaluator for workflow node activation (ADR-0008 Decision 10).
 * Implements numeric and string comparisons with AND/OR logical grouping without Spring Expression Language (SpEL).
 */
@Component
public class StructuredConditionEvaluator {

    /**
     * Evaluates a list of node conditions against the provided form data.
     * <ul>
     *     <li>If {@code conditions} is null or empty, returns {@code true} (unconditional step).</li>
     *     <li>Conditions sharing the same {@code logicalGroup} are combined with AND.</li>
     *     <li>Different {@code logicalGroup}s are combined with OR.</li>
     *     <li>Missing or null fields in {@code formData} evaluate to {@code false}.</li>
     * </ul>
     */
    public boolean evaluateNodeConditions(List<FlowNodeCondition> conditions, Map<String, Object> formData) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        if (formData == null || formData.isEmpty()) {
            return false;
        }

        Map<Integer, List<FlowNodeCondition>> groups = conditions.stream()
                .collect(Collectors.groupingBy(FlowNodeCondition::getLogicalGroup));

        for (List<FlowNodeCondition> groupConds : groups.values()) {
            boolean groupMatch = true;
            for (FlowNodeCondition cond : groupConds) {
                if (!evaluateSingleCondition(cond, formData)) {
                    groupMatch = false;
                    break;
                }
            }
            if (groupMatch) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateSingleCondition(FlowNodeCondition cond, Map<String, Object> formData) {
        if (cond == null || cond.getFieldKey() == null) {
            return false;
        }
        Object actualVal = formData.get(cond.getFieldKey());
        if (actualVal == null) {
            return false;
        }
        String expectedStr = cond.getExpectedValue() != null ? cond.getExpectedValue().trim() : "";
        ConditionOperator op = cond.getOperator();
        if (op == null) {
            return false;
        }

        if (op == ConditionOperator.IN) {
            String actualStr = actualVal.toString().trim();
            BigDecimal actualBd = tryParseBigDecimal(actualStr);
            String[] parts = expectedStr.split(",");
            for (String part : parts) {
                String item = part.trim();
                if (actualBd != null) {
                    BigDecimal itemBd = tryParseBigDecimal(item);
                    if (itemBd != null && actualBd.compareTo(itemBd) == 0) {
                        return true;
                    }
                }
                if (actualStr.equalsIgnoreCase(item)) {
                    return true;
                }
            }
            return false;
        }

        String actualStr = actualVal.toString().trim();
        BigDecimal actualBd = tryParseBigDecimal(actualStr);
        BigDecimal expectedBd = tryParseBigDecimal(expectedStr);

        int cmp;
        if (actualBd != null && expectedBd != null) {
            cmp = actualBd.compareTo(expectedBd);
        } else {
            cmp = actualStr.compareTo(expectedStr);
        }

        return switch (op) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case IN -> false;
        };
    }

    private BigDecimal tryParseBigDecimal(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return null;
        }
    }
}
