package com.aalsikke.service;

import com.aalsikke.model.ScheduleConflict;
import com.aalsikke.model.User;
import com.aalsikke.repository.ScheduleConflictRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduleConflictService {
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final UserRepository userRepository;

    public ScheduleConflictService(ScheduleConflictRepository scheduleConflictRepository, UserRepository userRepository) {
        this.scheduleConflictRepository = scheduleConflictRepository;
        this.userRepository = userRepository;
    }

    public List<ScheduleConflict> getConflictsForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return scheduleConflictRepository.findByUserId(user.getId());
    }

    public ScheduleConflict createConflict(ScheduleConflict conflict, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        conflict.setUser(user);
        return scheduleConflictRepository.save(conflict);
    }
}