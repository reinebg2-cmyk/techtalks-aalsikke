package com.aalsikke.repository;

import com.aalsikke.model.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
}