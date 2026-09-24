package com.aalsikke.repository;

import com.aalsikke.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
    List<Course> findByUserId(UUID userId);
    Optional<Course> findByIdAndUserId(UUID id, UUID userId);
}