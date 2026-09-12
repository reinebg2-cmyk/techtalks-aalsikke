package com.aalsikke.controller;

import com.aalsikke.model.Course;
import com.aalsikke.service.CourseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {
    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping("/me")
    public List<Course> getMyCourses(Authentication authentication) {
        return courseService.getCoursesForCurrentUser(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(@RequestBody Course course, Authentication authentication) {
        return ResponseEntity.ok(courseService.createCourse(course, authentication.getName()));
    }
}