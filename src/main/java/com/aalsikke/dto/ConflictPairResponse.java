package com.aalsikke.dto;

import com.aalsikke.model.ScheduleItem;

/** Two of the caller's items whose time slots overlap (S3-07). itemOne always starts first. */
public record ConflictPairResponse(ConflictSummary itemOne, ConflictSummary itemTwo) {

    public static ConflictPairResponse of(ScheduleItem first, ScheduleItem second) {
        return new ConflictPairResponse(ConflictSummary.from(first), ConflictSummary.from(second));
    }
}