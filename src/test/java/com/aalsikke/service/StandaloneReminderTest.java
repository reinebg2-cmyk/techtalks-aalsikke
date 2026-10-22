package com.aalsikke.service;

import com.aalsikke.dto.CreateReminderRequest;
import com.aalsikke.dto.ReminderResponseDTO;
import com.aalsikke.dto.UpdateReminderRequest;
import com.aalsikke.mail.EmailSender;
import com.aalsikke.model.Reminder;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.OccurrenceCompletionRepository;
import com.aalsikke.repository.RecurrenceRuleRepository;
import com.aalsikke.repository.ReminderRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** KAN-381: reminders that don't belong to a task ("22/12 Coco's birthday, buy a gift"). */
class StandaloneReminderTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 20, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String EMAIL = "lara@test.com";

    private ReminderRepository reminders;
    private ScheduleItemRepository items;
    private UserRepository users;
    private EmailSender emailSender;
    private ReminderService service;
    private ReminderEmailJob job;
    private User lara;

    @BeforeEach
    void setUp() {
        reminders = mock(ReminderRepository.class);
        items = mock(ScheduleItemRepository.class);
        users = mock(UserRepository.class);
        emailSender = mock(EmailSender.class);
        service = new ReminderService(reminders, items, users);
        job = new ReminderEmailJob(reminders, emailSender,
                mock(RecurrenceRuleRepository.class), mock(OccurrenceCompletionRepository.class));

        lara = new User();
        lara.setEmail(EMAIL);
        lara.setFullName("Lara Haddad");
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(lara));
        when(reminders.save(any(Reminder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Reminder standalone(String title, OffsetDateTime at) {
        Reminder r = new Reminder();
        r.setId(UUID.randomUUID());
        r.setUser(lara);
        r.setTitle(title);
        r.setRemindAt(at);
        return r;
    }

    // ---------- create ----------

    @Test
    void create_withTitleAndNoTask_savesIt() {
        OffsetDateTime at = OffsetDateTime.of(2026, 12, 22, 9, 0, 0, 0, ZoneOffset.ofHours(2));

        ReminderResponseDTO result = service.createReminder(
                new CreateReminderRequest(null, at, "Coco's birthday, buy a gift"), EMAIL).orElseThrow();

        assertNull(result.scheduleItemId());
        assertEquals("Coco's birthday, buy a gift", result.scheduleItemTitle());
        assertEquals(at, result.remindAt());

        ArgumentCaptor<Reminder> saved = ArgumentCaptor.forClass(Reminder.class);
        verify(reminders).save(saved.capture());
        assertSame(lara, saved.getValue().getUser());
        assertNull(saved.getValue().getScheduleItem());
        verifyNoInteractions(items);
    }

    @Test
    void create_titleIsTrimmed() {
        ReminderResponseDTO result = service.createReminder(
                new CreateReminderRequest(null, NOW.plusDays(1), "   Gym   "), EMAIL).orElseThrow();
        assertEquals("Gym", result.scheduleItemTitle());
    }

    @Test
    void create_noTaskAndNoTitle_isRejected() {
        for (String title : new String[]{null, "", "   "}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                    service.createReminder(new CreateReminderRequest(null, NOW.plusDays(1), title), EMAIL));
            assertEquals("A reminder needs a title.", e.getMessage());
        }
        verify(reminders, never()).save(any());
    }

    @Test
    void create_titleOver200Characters_isRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.createReminder(new CreateReminderRequest(null, NOW.plusDays(1), "a".repeat(201)), EMAIL));
        assertEquals("A reminder title can be at most 200 characters.", e.getMessage());
    }

    @Test
    void create_noTime_isRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                service.createReminder(new CreateReminderRequest(null, null, "Gym"), EMAIL));
        assertEquals("remindAt is required.", e.getMessage());
    }

    @Test
    void create_unknownUser_isEmpty() {
        when(users.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
        assertTrue(service.createReminder(
                new CreateReminderRequest(null, NOW.plusDays(1), "Gym"), "ghost@test.com").isEmpty());
    }

    // ---------- edit / snooze ----------

    @Test
    void edit_standalone_movesTimeAndRenames_andFiresAgain() {
        Reminder r = standalone("Coco's birthday", NOW.plusDays(1));
        r.setTriggered(true);
        r.setEmailedAt(NOW);
        when(reminders.findByIdAndOwner(r.getId(), EMAIL)).thenReturn(Optional.of(r));

        ReminderResponseDTO result = service.updateReminder(r.getId(),
                new UpdateReminderRequest(NOW.plusDays(2), "Coco's birthday, buy a gift"), EMAIL).orElseThrow();

        assertEquals(NOW.plusDays(2), result.remindAt());
        assertEquals("Coco's birthday, buy a gift", result.scheduleItemTitle());
        assertFalse(r.isTriggered());
        assertNull(r.getEmailedAt());
        verify(reminders, never()).findByScheduleItemId(any());   // no task, so no 10-minute gap check
    }

    @Test
    void edit_withoutTitle_keepsTheOldTitle() {
        Reminder r = standalone("Gym", NOW.plusDays(1));
        when(reminders.findByIdAndOwner(r.getId(), EMAIL)).thenReturn(Optional.of(r));

        service.updateReminder(r.getId(), new UpdateReminderRequest(NOW.plusDays(2)), EMAIL);

        assertEquals("Gym", r.getTitle());
    }

    @Test
    void snooze_standalone_works() {
        Reminder r = standalone("Gym", NOW);
        when(reminders.findByIdAndOwner(r.getId(), EMAIL)).thenReturn(Optional.of(r));

        assertTrue(service.snoozeReminder(r.getId(), 10, EMAIL).isPresent());
        assertTrue(r.getRemindAt().isAfter(OffsetDateTime.now().plusMinutes(9)));
    }

    // ---------- bell list ----------

    @Test
    void list_includesStandaloneReminders() {
        Reminder r = standalone("Gym", NOW.plusDays(1));
        when(reminders.findAllForUser(eq(EMAIL), any(OffsetDateTime.class))).thenReturn(List.of(r));

        List<ReminderResponseDTO> all = service.getAllRemindersForCurrentUser(EMAIL);

        assertEquals(1, all.size());
        assertNull(all.get(0).scheduleItemId());
        assertEquals("Gym", all.get(0).scheduleItemTitle());
    }

    // ---------- email ----------

    @Test
    void email_standalone_usesItsOwnTitle_andNoDueTime() {
        Reminder r = standalone("Coco's birthday, buy a gift", NOW.minusMinutes(1));
        when(reminders.findDueForEmail(NOW)).thenReturn(List.of(r));

        assertEquals(1, job.sendDueReminderEmails(NOW));
        verify(emailSender).sendReminderEmail(EMAIL, "Lara Haddad", "Coco's birthday, buy a gift", null, "en");
        verify(reminders).markEmailed(eq(r.getId()), any(OffsetDateTime.class));
    }


    @Test
    void email_standalone_usesTheUsersLanguage() {
        lara.setPreferredLanguage("ar");
        Reminder r = standalone("Coco's birthday, buy a gift", NOW.minusMinutes(1));
        when(reminders.findDueForEmail(NOW)).thenReturn(List.of(r));

        assertEquals(1, job.sendDueReminderEmails(NOW));
        verify(emailSender).sendReminderEmail(EMAIL, "Lara Haddad", "Coco's birthday, buy a gift", null, "ar");
    }

    // ---------- owner filled from the task (covers DefaultReminders) ----------

    @Test
    void ownerIsFilledFromTheTask_whenMissing() {
        ScheduleItem item = new ScheduleItem();
        item.setUser(lara);
        Reminder r = new Reminder();
        r.setScheduleItem(item);

        r.fillOwnerFromTask();

        assertSame(lara, r.getUser());
    }
}