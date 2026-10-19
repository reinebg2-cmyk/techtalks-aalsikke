package com.aalsikke.service;

import com.aalsikke.ai.AiClient;
import com.aalsikke.config.AppTime;
import com.aalsikke.dto.QuickParseResponse;
import com.aalsikke.exception.QuickParseException;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.Priority;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * KAN-372: turns a sentence in Lebanese Arabic, Arabizi or English into a pre-filled New task form.
 * The AI only suggests; the user reviews the form and saves it. Nothing is created here.
 */
@Service
public class QuickParseService {

    static final int MAX_TEXT_LENGTH = 300;
    private static final ItemType DEFAULT_CATEGORY = ItemType.PERSONAL;
    private static final Priority DEFAULT_PRIORITY = Priority.MEDIUM;

    private final AiClient aiClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public QuickParseService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public QuickParseResponse parse(String text) {
        return parse(text, ZonedDateTime.now(AppTime.ZONE));
    }

    QuickParseResponse parse(String text, ZonedDateTime now) {
        if (text == null || text.isBlank()) {
            throw new QuickParseException("Type something to add");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new QuickParseException("Text is too long");
        }

        String reply;
        try {
            reply = aiClient.complete(prompt(text.trim(), now));
        } catch (RuntimeException e) {
            throw new QuickParseException("AI is unavailable", e);
        }

        AiReply ai = read(reply);
        if (!Boolean.TRUE.equals(ai.understood()) || ai.title() == null || ai.title().isBlank()) {
            throw new QuickParseException("Could not understand the text");
        }

        return new QuickParseResponse(
                ai.title().trim(),
                dateTime(ai.start()),
                dateTime(ai.due()),
                category(ai.category()),
                priority(ai.priority()),
                days(ai.repeatDays()));
    }

    /** The JSON object the prompt asks for. Unknown fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiReply(Boolean understood, String title, String start, String due,
                   String category, String priority, List<String> repeatDays) {
    }

    private AiReply read(String reply) {
        if (reply == null) {
            throw new QuickParseException("Empty AI reply");
        }
        // Keep only the JSON object, even if the model wrapped it in ```json fences or added words around it
        int first = reply.indexOf('{');
        int last = reply.lastIndexOf('}');
        if (first < 0 || last < first) {
            throw new QuickParseException("AI reply is not JSON");
        }
        try {
            return jsonMapper.readValue(reply.substring(first, last + 1), AiReply.class);
        } catch (RuntimeException e) {
            throw new QuickParseException("AI reply is not valid JSON", e);
        }
    }

    private static LocalDateTime dateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (RuntimeException e) {
            return null; // a bad date is left empty for the user to fill
        }
    }

    private static ItemType category(String value) {
        try {
            return value == null ? DEFAULT_CATEGORY : ItemType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEFAULT_CATEGORY;
        }
    }

    private static Priority priority(String value) {
        try {
            return value == null ? DEFAULT_PRIORITY : Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEFAULT_PRIORITY;
        }
    }

    private static List<DayOfWeek> days(List<String> values) {
        List<DayOfWeek> days = new ArrayList<>();
        if (values == null) {
            return days;
        }
        for (String value : values) {
            try {
                DayOfWeek day = DayOfWeek.valueOf(value.trim().toUpperCase());
                if (!days.contains(day)) {
                    days.add(day);
                }
            } catch (RuntimeException e) {
                // skip anything that is not a day name
            }
        }
        return days;
    }

    static String prompt(String text, ZonedDateTime now) {
        String categories = Arrays.stream(ItemType.values()).map(Enum::name).collect(Collectors.joining(", "));
        String priorities = Arrays.stream(Priority.values()).map(Enum::name).collect(Collectors.joining(", "));
        return """
                You turn a short note into a task for a student planner app in Lebanon.
                The note may be in Lebanese Arabic (Arabic script or Arabizi like "3andi", "bukra", "se3a"), or English.

                Now: %s, %s, %s (Asia/Beirut).

                Reply with ONLY one JSON object, no other text:
                {"understood": true or false,
                 "title": short English title, capitalised,
                 "start": "YYYY-MM-DDTHH:MM" or null,
                 "due": "YYYY-MM-DDTHH:MM" or null,
                 "category": one of [%s],
                 "priority": one of [%s],
                 "repeatDays": list of MONDAY..SUNDAY, empty if it does not repeat weekly}

                Rules:
                - Times are Beirut local time. Resolve "bukra" (tomorrow), "ba3d bukra", day names, "l masa" (evening) using Now.
                - A time with no date means the next time it happens, never in the past.
                - Something at a time (class, gym, meeting): set start, and due = start + 1 hour unless a length is given.
                - Something due by a time (homework, payment): set due only.
                - "kel" + day names = repeats weekly on those days; start is the first upcoming one.
                - Use priority MEDIUM unless the note says urgent/important (HIGH or URGENT).
                - If the note is not a task or you are unsure, reply {"understood": false}.

                Note: %s
                """.formatted(now.toLocalDate(), now.getDayOfWeek(),
                now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                categories, priorities, text);
    }
}