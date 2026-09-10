package com.aalsikke.controller;

import com.aalsikke.model.Workplace;
import com.aalsikke.service.WorkplaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workplaces")
public class WorkplaceController {
    private final WorkplaceService workplaceService;

    public WorkplaceController(WorkplaceService workplaceService) {
        this.workplaceService = workplaceService;
    }

    @GetMapping("/user/{userId}")
    public List<Workplace> getWorkplacesByUser(@PathVariable UUID userId) {
        return workplaceService.getWorkplacesByUser(userId);
    }

    @PostMapping
    public ResponseEntity<Workplace> createWorkplace(@RequestBody Workplace workplace) {
        return ResponseEntity.ok(workplaceService.createWorkplace(workplace));
    }
}