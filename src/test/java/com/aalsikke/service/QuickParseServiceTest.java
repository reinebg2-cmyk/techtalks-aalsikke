package com.aalsikke.service;

import com.aalsikke.dto.QuickParseResponse;
import com.aalsikke.exception.QuickParseException;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.Priority;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KAN-372: turns the AI's JSON reply into a pre-filled New task form. Pure, no Spring; the AI is faked. */
class QuickParseServiceTest {

    // Sun Oct 18 2026, 15:00 Beirut
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 18, 15, 0, 0, 0, ZoneId.of("Asia/Beirut"));

    private static QuickParseService replying(String reply) {
        return new QuickParseService(prompt -> reply);
    }

    @Test
    void validReply_fillsTheForm() {
        QuickParseResponse r = replying("""
                {"understood":true,"title":"Math exam","start":"2026-10-19T10:00","due":"2026-10-19T12:00",
                 "category":"EXAM","priority":"HIGH","repeatDays":[]}
                """).parse("3andi exam math bukra se3a 10", NOW);

        assertEquals("Math exam", r.title());
        assertEquals(LocalDateTime.of(2026, 10, 19, 10, 0), r.start());
        assertEquals(LocalDateTime.of(2026, 10, 19, 12, 0), r.due());
        assertEquals(ItemType.EXAM, r.category());
        assertEquals(Priority.HIGH, r.priority());
        assertTrue(r.repeatDays().isEmpty());
    }

    @Test
    void weeklyReply_keepsTheRepeatDays() {
        QuickParseResponse r = replying("""
                {"understood":true,"title":"Gym","start":"2026-10-19T18:00","due":"2026-10-19T19:00",
                 "category":"PERSONAL","priority":"MEDIUM","repeatDays":["MONDAY","THURSDAY"]}
                """).parse("gym kel tanen w khamis 6 l masa", NOW);

        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), r.repeatDays());
    }

    @Test
    void unknownCategoryAndPriority_fallBackToDefaults() {
        QuickParseResponse r = replying("""
                {"understood":true,"title":"Gym","category":"GYM","priority":"SUPER","repeatDays":[]}
                """).parse("gym", NOW);

        assertEquals(ItemType.PERSONAL, r.category());
        assertEquals(Priority.MEDIUM, r.priority());
    }

    @Test
    void missingDates_stayEmptyForTheUserToFill() {
        QuickParseResponse r = replying("""
                {"understood":true,"title":"Call the bank","category":"ERRAND","priority":"LOW","repeatDays":[]}
                """).parse("call the bank", NOW);

        assertNull(r.start());
        assertNull(r.due());
    }

    @Test
    void replyWrappedInCodeFences_isStillRead() {
        // Models sometimes wrap JSON in ```json ... ``` even when asked not to
        QuickParseResponse r = replying("""
```json
                {"understood":true,"title":"Lab report","category":"LAB","priority":"MEDIUM","repeatDays":[]}
```
                """).parse("lab report", NOW);

        assertEquals("Lab report", r.title());
    }

    @Test
    void notUnderstood_throws() {
        assertThrows(QuickParseException.class,
                () -> replying("{\"understood\":false}").parse("asdkjh qwe", NOW));
    }

    @Test
    void garbageReply_throws() {
        assertThrows(QuickParseException.class,
                () -> replying("Sorry, I can't help with that.").parse("3andi exam", NOW));
    }

    @Test
    void aiDown_throws() {
        QuickParseService service = new QuickParseService(prompt -> {
            throw new RuntimeException("timeout");
        });
        assertThrows(QuickParseException.class, () -> service.parse("3andi exam", NOW));
    }

    @Test
    void prompt_includesTodayAndTheUsersText() {
        // "bukra" (tomorrow) only works if the AI knows what today is
        AtomicReference<String> sent = new AtomicReference<>();
        new QuickParseService(prompt -> {
            sent.set(prompt);
            return "{\"understood\":true,\"title\":\"x\",\"repeatDays\":[]}";
        }).parse("3andi exam bukra", NOW);

        assertTrue(sent.get().contains("2026-10-18"));
        assertTrue(sent.get().contains("SUNDAY"));
        assertTrue(sent.get().contains("3andi exam bukra"));
    }
}