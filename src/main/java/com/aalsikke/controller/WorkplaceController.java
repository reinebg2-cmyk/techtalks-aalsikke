package com.aalsikke.controller;

import com.aalsikke.model.Workplace;
import com.aalsikke.service.WorkplaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workplaces")
public class WorkplaceController {
    private final WorkplaceService workplaceService;

    public WorkplaceController(WorkplaceService workplaceService) {
        this.workplaceService = workplaceService;
    }

    @GetMapping("/me")
    public List<Workplace> getMyWorkplaces(Authentication authentication) {
        return workplaceService.getWorkplacesForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<Workplace> createWorkplace(@RequestBody Workplace workplace, Authentication authentication) {
        return ResponseEntity.ok(workplaceService.createWorkplace(workplace, authentication.getName()));
    }
}