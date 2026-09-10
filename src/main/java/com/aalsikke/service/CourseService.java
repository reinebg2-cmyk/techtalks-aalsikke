package com.aalsikke.service;

import com.aalsikke.model.Course;
import com.aalsikke.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CourseService {
    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public List<Course> getCoursesByUser(UUID userId) {
        return courseRepository.findByUserId(userId);
    }

    public Course createCourse(Course course) {
        return courseRepository.save(course);
    }
}