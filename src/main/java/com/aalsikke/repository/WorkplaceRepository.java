package com.aalsikke.repository;

import com.aalsikke.model.Workplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface WorkplaceRepository extends JpaRepository<Workplace, UUID> {
    List<Workplace> findByUserId(UUID userId);
    Optional<Workplace> findByIdAndUserId(UUID id, UUID userId);
}