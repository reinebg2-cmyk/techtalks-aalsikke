package com.aalsikke.service;

import com.aalsikke.dto.NotificationPreferencesRequest;
import com.aalsikke.dto.NotificationPreferencesResponse;
import com.aalsikke.model.NotificationPreference;
import com.aalsikke.model.User;
import com.aalsikke.repository.NotificationPreferenceRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository,
                                         UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    // Every user gets sensible defaults (all four toggles on) the first time
    // preferences are requested, rather than needing a row created at signup.
    private NotificationPreference getOrCreate(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    NotificationPreference preference = new NotificationPreference();
                    preference.setUser(user);
                    return preferenceRepository.save(preference);
                });
    }

    public NotificationPreferencesResponse getPreferences(String email) {
        return toResponse(getOrCreate(email));
    }

    public NotificationPreferencesResponse updatePreferences(String email, NotificationPreferencesRequest request) {
        NotificationPreference preference = getOrCreate(email);

        preference.setDeadlineReminders(request.getDeadlineReminders());
        preference.setOverdueAlerts(request.getOverdueAlerts());
        preference.setScheduleConflicts(request.getScheduleConflicts());
        preference.setWeeklyReviewReady(request.getWeeklyReviewReady());

        return toResponse(preferenceRepository.save(preference));
    }

    private NotificationPreferencesResponse toResponse(NotificationPreference preference) {
        return new NotificationPreferencesResponse(
                preference.isDeadlineReminders(),
                preference.isOverdueAlerts(),
                preference.isScheduleConflicts(),
                preference.isWeeklyReviewReady()
        );
    }
}