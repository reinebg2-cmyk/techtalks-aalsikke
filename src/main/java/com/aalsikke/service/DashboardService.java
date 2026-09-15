package com.aalsikke.service;

import com.aalsikke.dto.DashboardSummaryDTO;
import com.aalsikke.model.User;
import com.aalsikke.repository.ScheduleItemRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.aalsikke.model.ScheduleItem;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class DashboardService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final UserRepository userRepository;

    public DashboardService(ScheduleItemRepository scheduleItemRepository, UserRepository userRepository) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.userRepository = userRepository;
    }
    public List<ScheduleItem> getTodaysTasks(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime startOfDay = today.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endOfDay = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime().minusNanos(1);

        return scheduleItemRepository.findByUserIdAndEndTimeBetweenOrderByEndTimeAsc(
                user.getId(), startOfDay, endOfDay);
    }

    public List<ScheduleItem> getUpcomingDeadlines(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime sevenDaysOut = now.plusDays(7);

        return scheduleItemRepository.findByUserIdAndEndTimeBetweenOrderByEndTimeAsc(
                user.getId(), now, sevenDaysOut);
    }

    public List<ScheduleItem> getOverdueTasks(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return scheduleItemRepository.findByUserIdAndStatusAndEndTimeBeforeOrderByEndTimeAsc(
                user.getId(), "PENDING", OffsetDateTime.now());
    }

    public DashboardSummaryDTO getSummary(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UUID userId = user.getId();

        long total = scheduleItemRepository.countByUserId(userId);
        long completed = scheduleItemRepository.countByUserIdAndStatus(userId, "COMPLETED");
        long overdue = scheduleItemRepository.countByUserIdAndStatusAndEndTimeBefore(
                userId, "PENDING", OffsetDateTime.now());
        long pending = total - completed - overdue;

        return new DashboardSummaryDTO(total, pending, completed, overdue);
    }
}