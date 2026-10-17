package com.aalsikke.service;

import com.aalsikke.config.AppTime;
import com.aalsikke.dto.DailyReviewEntry;
import com.aalsikke.dto.WeeklyReviewResponse;
import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.model.DailyReview;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.DailyReviewRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import com.aalsikke.dto.DailyReviewRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

@Service
public class DailyReviewService {
    private static final long MAX_RANGE_DAYS = 31;

    private final DailyReviewRepository dailyReviewRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;
    private final ScheduleItemService scheduleItemService;

    public DailyReviewService(DailyReviewRepository dailyReviewRepository,
                              ScheduleItemRepository scheduleItemRepository,
                              UserRepository userRepository,
                              ScheduleItemService scheduleItemService) {
        this.dailyReviewRepository = dailyReviewRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
        this.scheduleItemService = scheduleItemService;
    }

    public List<DailyReviewEntry> getWeeklyReviewsForCurrentUser(String email, LocalDate start, LocalDate end) {
        User user = findUser(email);
        return dailyReviewRepository.findByUserIdAndReviewDateBetween(user.getId(), start, end)
                .stream()
                .sorted(Comparator.comparing(DailyReview::getReviewDate))
                .map(DailyReviewEntry::from)
                .toList();
    }

    /**
     * Always creates a NEW review for the caller from the allowed fields only.
     * The request has no id, so a client cannot overwrite (or take over) someone else's review.
     */
    public DailyReviewEntry createReview(DailyReviewRequest request, String email) {
        User user = findUser(email);

        DailyReview review = new DailyReview();
        review.setUser(user);
        review.setReviewDate(request.reviewDate());
        review.setProductivityScore(request.productivityScore());
        review.setNotes(request.notes());

        return DailyReviewEntry.from(dailyReviewRepository.save(review));
    }

    /**
     * KAN-248 / ADR-002: "weekly review" is an aggregation over the existing daily rows for a
     * date range (start and end inclusive), not a stored entity. Also counts the caller's tasks
     * that were due in the same dates, for the completed-vs-missed card (KAN-249).
     */
    public WeeklyReviewResponse getWeeklySummary(String email, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("'start' must be on or before 'end'.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("The range cannot be longer than " + MAX_RANGE_DAYS + " days.");
        }

        User user = findUser(email);

        // --- Daily reviews (ADR-002 aggregation) ---
        List<DailyReview> reviews = dailyReviewRepository
                .findByUserIdAndReviewDateBetween(user.getId(), start, end)
                .stream()
                .sorted(Comparator.comparing(DailyReview::getReviewDate))
                .toList();

        int daysReviewed = (int) reviews.stream().map(DailyReview::getReviewDate).distinct().count();

        OptionalDouble average = reviews.stream()
                .map(DailyReview::getProductivityScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        Double averageScore = average.isPresent()
                ? Math.round(average.getAsDouble() * 10) / 10.0   // one decimal, e.g. 3.7
                : null;

        // --- Tasks due in the same dates (due time = endTime, or deadline if no slot) ---
        ZoneId zone = AppTime.ZONE;   // same convention as DashboardService
        OffsetDateTime from = start.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime().minusNanos(1);
        List<ScheduleItem> due = scheduleItemRepository.findDueBetween(user.getId(), from, to);

        // Same items as the calendar (weekly repeats included), loaded once. KAN-363: repeats due in
        // the range count too, each with its own done state; the real items come from findDueBetween.
        OffsetDateTime toExclusive = end.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        List<ScheduleItemResponse> inRange = scheduleItemService.getItemsInRange(email, from, toExclusive);
        List<ScheduleItemResponse> repeatsDue = inRange.stream()
                .filter(ScheduleItemResponse::isOccurrence)
                .filter(r -> r.getEndTime() != null
                        && !r.getEndTime().isBefore(from) && r.getEndTime().isBefore(toExclusive))
                .toList();

        OffsetDateTime now = OffsetDateTime.now();
        long completed = due.stream().filter(item -> "COMPLETED".equals(item.getStatus())).count()
                + repeatsDue.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        long missed = due.stream()
                .filter(item -> !"COMPLETED".equals(item.getStatus()))
                .filter(item -> dueTime(item).isBefore(now))
                .count()
                + repeatsDue.stream()
                .filter(r -> !"COMPLETED".equals(r.getStatus()))
                .filter(r -> r.getEndTime().isBefore(now))
                .count();
        long upcoming = due.size() + repeatsDue.size() - completed - missed;

        // KAN-341: busiest day, from what happens each day (same items as the calendar, weekly
        // class repeats included). KAN-356: a block counts on every day from its start to its end
        // (like the calendar's day load line), a deadline-only task on its deadline.
        List<BusiestDay.Span> happenings = inRange
                .stream()
                .map(item -> item.getStartTime() != null
                        ? new BusiestDay.Span(item.getStartTime(), item.getEndTime())
                        : new BusiestDay.Span(item.getDeadline(), null))
                .toList();
        BusiestDay.Result busiest = BusiestDay.ofSpans(happenings, zone, start, end);

        return new WeeklyReviewResponse(
                start, end, daysReviewed, averageScore,
                reviews.stream().map(DailyReviewEntry::from).toList(),
                completed, missed, upcoming,
                busiest != null ? busiest.date() : null,
                busiest != null ? busiest.count() : 0);
    }

    private static OffsetDateTime dueTime(ScheduleItem item) {
        return item.getEndTime() != null ? item.getEndTime() : item.getDeadline();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}