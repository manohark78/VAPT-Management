package com.company.vapt.ingestion.domain;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized state machine defining permitted lifecycle transitions for report import batches.
 * 
 * Permitted Lifecycle:
 * RECEIVED -> VALIDATING -> STAGING -> ENRICHING -> RECONCILING -> COMMITTED
 * Any non-terminal state -> FAILED
 * Terminal states: COMMITTED, FAILED
 */
@Component
public class ImportStateMachine {

    private static final Map<ImportStatus, Set<ImportStatus>> TRANSITIONS;

    static {
        Map<ImportStatus, Set<ImportStatus>> map = new EnumMap<>(ImportStatus.class);

        map.put(ImportStatus.RECEIVED, EnumSet.of(ImportStatus.VALIDATING, ImportStatus.FAILED));
        map.put(ImportStatus.VALIDATING, EnumSet.of(ImportStatus.STAGING, ImportStatus.FAILED));
        map.put(ImportStatus.STAGING, EnumSet.of(ImportStatus.ENRICHING, ImportStatus.FAILED));
        map.put(ImportStatus.ENRICHING, EnumSet.of(ImportStatus.RECONCILING, ImportStatus.FAILED));
        map.put(ImportStatus.RECONCILING, EnumSet.of(ImportStatus.COMMITTED, ImportStatus.FAILED));

        // Terminal states cannot transition to any other state
        map.put(ImportStatus.COMMITTED, Collections.emptySet());
        map.put(ImportStatus.FAILED, Collections.emptySet());

        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    /**
     * Checks whether transitioning from current to target is permitted.
     */
    public boolean isValidTransition(ImportStatus current, ImportStatus target) {
        if (current == null || target == null) {
            return false;
        }
        Set<ImportStatus> allowed = TRANSITIONS.getOrDefault(current, Collections.emptySet());
        return allowed.contains(target);
    }

    /**
     * Validates a proposed state transition, throwing an InvalidStateTransitionException if illegal.
     */
    public void validateTransition(ImportStatus current, ImportStatus target) {
        if (current == null) {
            throw new InvalidStateTransitionException("Current import status must not be null");
        }
        if (target == null) {
            throw new InvalidStateTransitionException("Target import status must not be null");
        }
        if (current == target) {
            throw new InvalidStateTransitionException(
                    String.format("Batch is already in status [%s]; repeated transition rejected", current)
            );
        }
        if (current.isTerminal()) {
            throw new InvalidStateTransitionException(
                    String.format("Cannot transition from terminal status [%s] to [%s]", current, target)
            );
        }
        if (!isValidTransition(current, target)) {
            throw new InvalidStateTransitionException(current, target);
        }
    }

    /**
     * Returns the set of valid next target statuses for a given current status.
     */
    public Set<ImportStatus> getNextPermittedStatuses(ImportStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        return TRANSITIONS.getOrDefault(current, Collections.emptySet());
    }

    /**
     * Returns the standard forward happy-path status for a non-terminal status.
     */
    public ImportStatus getNextHappyPathStatus(ImportStatus current) {
        return switch (current) {
            case RECEIVED -> ImportStatus.VALIDATING;
            case VALIDATING -> ImportStatus.STAGING;
            case STAGING -> ImportStatus.ENRICHING;
            case ENRICHING -> ImportStatus.RECONCILING;
            case RECONCILING -> ImportStatus.COMMITTED;
            case COMMITTED, FAILED -> null;
        };
    }
}
