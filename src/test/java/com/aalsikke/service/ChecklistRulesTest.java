package com.aalsikke.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** KAN-342: checklist text, size limit and reorder rules. */
class ChecklistRulesTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    @Test
    void textIsTrimmed() {
        assertEquals("Write intro", ChecklistService.cleanText("  Write intro  "));
    }

    @Test
    void emptyOrBlankTextIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChecklistService.cleanText(null));
        assertThrows(IllegalArgumentException.class, () -> ChecklistService.cleanText("   "));
    }

    @Test
    void textUpTo200CharactersIsAllowed() {
        assertDoesNotThrow(() -> ChecklistService.cleanText("x".repeat(200)));
        assertThrows(IllegalArgumentException.class, () -> ChecklistService.cleanText("x".repeat(201)));
    }

    @Test
    void fiftyItemsIsTheLimit() {
        assertDoesNotThrow(() -> ChecklistService.checkRoomForOneMore(49));
        assertThrows(IllegalArgumentException.class, () -> ChecklistService.checkRoomForOneMore(50));
    }

    @Test
    void reorderWithTheSameItemsIsAllowed() {
        assertDoesNotThrow(() -> ChecklistService.checkSameItems(List.of(A, B, C), List.of(C, A, B)));
    }

    @Test
    void reorderMissingAnItemIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChecklistService.checkSameItems(List.of(A, B, C), List.of(A, B)));
    }

    @Test
    void reorderWithAForeignItemIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChecklistService.checkSameItems(List.of(A, B), List.of(A, UUID.randomUUID())));
    }

    @Test
    void reorderWithADuplicateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChecklistService.checkSameItems(List.of(A, B), List.of(A, A)));
    }

    @Test
    void reorderWithoutAListIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ChecklistService.checkSameItems(List.of(A), null));
    }
}