package com.aalsikke.service;

import com.aalsikke.dto.DashboardSummaryDTO;
import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.model.User;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;

    public DashboardService(ScheduleItemRepository scheduleItemRepository, UserRepository userRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
    }

    public List<ScheduleItemResponse> getTodaysTasks(String email) {
        User user = findUser(email);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime startOfDay = today.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endOfDay = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime().minusNanos(1);

        return toResponses(scheduleItemRepository.findByUserIdAndEndTimeBetweenOrderByEndTimeAsc(
                user.getId(), startOfDay, endOfDay));
    }

    public List<ScheduleItemResponse> getUpcomingDeadlines(String email) {
        User user = findUser(email);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysOut = now.plusDays(7);

        return toResponses(scheduleItemRepository.findByUserIdAndEndTimeBetweenOrderByEndTimeAsc(
                user.getId(), now, sevenDaysOut));
    }

    public List<ScheduleItemResponse> getOverdueTasks(String email) {
        User user = findUser(email);

        return toResponses(scheduleItemRepository.findByUserIdAndStatusAndEndTimeBeforeOrderByEndTimeAsc(
                user.getId(), "PENDING", OffsetDateTime.now()));
    }

    public DashboardSummaryDTO getSummary(String email) {
        User user = findUser(email);
        UUID userId = user.getId();

        long total = scheduleItemRepository.countByUserId(userId);
        long completed = scheduleItemRepository.countByUserIdAndStatus(userId, "COMPLETED");
        long overdue = scheduleItemRepository.countByUserIdAndStatusAndEndTimeBefore(
                userId, "PENDING", OffsetDateTime.now());
        long pending = total - completed - overdue;

        return new DashboardSummaryDTO(total, pending, completed, overdue);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private List<ScheduleItemResponse> toResponses(List<ScheduleItem> items) {
        return items.stream().map(ScheduleItemResponse::from).toList();
    }
}