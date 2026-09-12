package com.aalsikke.service;

import com.aalsikke.model.Course;
import com.aalsikke.model.User;
import com.aalsikke.repository.CourseRepository;
import com.aalsikke.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseService(CourseRepository courseRepository, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    public List<Course> getCoursesForCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return courseRepository.findByUserId(user.getId());
    }

    public Course createCourse(Course course, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        course.setUser(user);
        return courseRepository.save(course);
    }
}