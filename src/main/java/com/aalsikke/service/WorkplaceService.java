package com.aalsikke.service;

import com.aalsikke.model.Workplace;
import com.aalsikke.model.User;
import com.aalsikke.repository.WorkplaceRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkplaceService {
    private final WorkplaceRepository workplaceRepository;
    private final UserRepository userRepository;

    public WorkplaceService(WorkplaceRepository workplaceRepository, UserRepository userRepository) {
        this.workplaceRepository = workplaceRepository;
        this.userRepository = userRepository;
    }

    public List<Workplace> getWorkplacesForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return workplaceRepository.findByUserId(user.getId());
    }

    public Workplace createWorkplace(Workplace workplace, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        workplace.setUser(user);
        return workplaceRepository.save(workplace);
    }
}