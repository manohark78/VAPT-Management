package com.company.vapt.ingestion;

import com.company.vapt.ingestion.domain.ImportStateMachine;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImportStateMachine - Domain State Machine Unit Tests")
class ImportStateMachineTest {

    private ImportStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ImportStateMachine();
    }

    @Test
    @DisplayName("1. Every permitted forward transition succeeds")
    void testPermittedForwardTransitions() {
        assertTrue(stateMachine.isValidTransition(ImportStatus.RECEIVED, ImportStatus.VALIDATING));
        assertTrue(stateMachine.isValidTransition(ImportStatus.VALIDATING, ImportStatus.STAGING));
        assertTrue(stateMachine.isValidTransition(ImportStatus.STAGING, ImportStatus.ENRICHING));
        assertTrue(stateMachine.isValidTransition(ImportStatus.ENRICHING, ImportStatus.RECONCILING));
        assertTrue(stateMachine.isValidTransition(ImportStatus.RECONCILING, ImportStatus.COMMITTED));

        assertDoesNotThrow(() -> stateMachine.validateTransition(ImportStatus.RECEIVED, ImportStatus.VALIDATING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(ImportStatus.VALIDATING, ImportStatus.STAGING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(ImportStatus.STAGING, ImportStatus.ENRICHING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(ImportStatus.ENRICHING, ImportStatus.RECONCILING));
        assertDoesNotThrow(() -> stateMachine.validateTransition(ImportStatus.RECONCILING, ImportStatus.COMMITTED));
    }

    @Test
    @DisplayName("2. Every invalid skipped transition is rejected")
    void testInvalidSkippedTransitions() {
        assertFalse(stateMachine.isValidTransition(ImportStatus.RECEIVED, ImportStatus.STAGING));
        assertFalse(stateMachine.isValidTransition(ImportStatus.RECEIVED, ImportStatus.ENRICHING));
        assertFalse(stateMachine.isValidTransition(ImportStatus.RECEIVED, ImportStatus.RECONCILING));
        assertFalse(stateMachine.isValidTransition(ImportStatus.RECEIVED, ImportStatus.COMMITTED));

        assertFalse(stateMachine.isValidTransition(ImportStatus.VALIDATING, ImportStatus.COMMITTED));
        assertFalse(stateMachine.isValidTransition(ImportStatus.STAGING, ImportStatus.COMMITTED));

        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.validateTransition(ImportStatus.RECEIVED, ImportStatus.COMMITTED));
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.validateTransition(ImportStatus.VALIDATING, ImportStatus.RECONCILING));
    }

    @Test
    @DisplayName("3. Backward transitions are rejected")
    void testBackwardTransitionsRejected() {
        assertFalse(stateMachine.isValidTransition(ImportStatus.STAGING, ImportStatus.VALIDATING));
        assertFalse(stateMachine.isValidTransition(ImportStatus.RECONCILING, ImportStatus.RECEIVED));
        assertFalse(stateMachine.isValidTransition(ImportStatus.ENRICHING, ImportStatus.STAGING));

        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.validateTransition(ImportStatus.STAGING, ImportStatus.VALIDATING));
    }

    @Test
    @DisplayName("4. Rejection of transitions from terminal COMMITTED")
    void testTransitionsFromCommittedRejected() {
        assertTrue(ImportStatus.COMMITTED.isTerminal());
        for (ImportStatus target : ImportStatus.values()) {
            assertFalse(stateMachine.isValidTransition(ImportStatus.COMMITTED, target));
            assertThrows(InvalidStateTransitionException.class, () ->
                    stateMachine.validateTransition(ImportStatus.COMMITTED, target));
        }
    }

    @Test
    @DisplayName("5. Rejection of transitions from terminal FAILED")
    void testTransitionsFromFailedRejected() {
        assertTrue(ImportStatus.FAILED.isTerminal());
        for (ImportStatus target : ImportStatus.values()) {
            assertFalse(stateMachine.isValidTransition(ImportStatus.FAILED, target));
            assertThrows(InvalidStateTransitionException.class, () ->
                    stateMachine.validateTransition(ImportStatus.FAILED, target));
        }
    }

    @ParameterizedTest
    @EnumSource(value = ImportStatus.class, names = {"RECEIVED", "VALIDATING", "STAGING", "ENRICHING", "RECONCILING"})
    @DisplayName("6. Valid failure transitions from all non-terminal states")
    void testFailureTransitionsFromNonTerminalStates(ImportStatus nonTerminal) {
        assertTrue(stateMachine.isValidTransition(nonTerminal, ImportStatus.FAILED));
        assertDoesNotThrow(() -> stateMachine.validateTransition(nonTerminal, ImportStatus.FAILED));
    }

    @Test
    @DisplayName("7. Rejection of transition to same status (duplicate transition)")
    void testDuplicateTransitionRejected() {
        assertThrows(InvalidStateTransitionException.class, () ->
                stateMachine.validateTransition(ImportStatus.VALIDATING, ImportStatus.VALIDATING));
    }
}
