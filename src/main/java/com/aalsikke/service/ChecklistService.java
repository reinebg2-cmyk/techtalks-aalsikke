package com.aalsikke.service;

import com.aalsikke.dto.ChecklistItemRequest;
import com.aalsikke.dto.ChecklistItemResponse;
import com.aalsikke.model.ChecklistItem;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.repository.ChecklistItemRepository;
import com.aalsikke.repository.ScheduleItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KAN-342: checklist (sub-steps) of a task.
 * Every method first checks that the task belongs to the caller (email from the JWT);
 * an empty result means "not yours / not found" and the controller answers 403.
 */
@Service
public class ChecklistService {

    static final int MAX_ITEMS = 50;
    static final int MAX_TEXT = 200;

    private final ChecklistItemRepository checklistItemRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    public ChecklistService(ChecklistItemRepository checklistItemRepository,
                            ScheduleItemRepository scheduleItemRepository) {
        this.checklistItemRepository = checklistItemRepository;
        this.scheduleItemRepository = scheduleItemRepository;
    }

    public Optional<List<ChecklistItemResponse>> list(UUID taskId, String email) {
        return ownedTask(taskId, email).map(task -> toResponses(taskId));
    }

    @Transactional
    public Optional<ChecklistItemResponse> add(UUID taskId, ChecklistItemRequest request, String email) {
        return ownedTask(taskId, email).map(task -> {
            String text = cleanText(request == null ? null : request.text());
            List<ChecklistItem> items = checklistItemRepository.findByScheduleItemIdOrderByPositionAsc(taskId);
            checkRoomForOneMore(items.size());

            ChecklistItem item = new ChecklistItem();
            item.setScheduleItem(task);
            item.setText(text);
            item.setDone(false);
            item.setPosition(items.isEmpty() ? 0 : items.get(items.size() - 1).getPosition() + 1);
            return ChecklistItemResponse.from(checklistItemRepository.save(item));
        });
    }

    @Transactional
    public Optional<ChecklistItemResponse> update(UUID taskId, UUID itemId, ChecklistItemRequest request, String email) {
        return ownedTask(taskId, email)
                .flatMap(task -> checklistItemRepository.findByIdAndScheduleItemId(itemId, taskId))
                .map(item -> {
                    if (request != null && request.text() != null) item.setText(cleanText(request.text()));
                    if (request != null && request.done() != null) item.setDone(request.done());
                    return ChecklistItemResponse.from(checklistItemRepository.save(item));
                });
    }

    @Transactional
    public boolean delete(UUID taskId, UUID itemId, String email) {
        return ownedTask(taskId, email)
                .flatMap(task -> checklistItemRepository.findByIdAndScheduleItemId(itemId, taskId))
                .map(item -> {
                    checklistItemRepository.delete(item);
                    return true;
                })
                .orElse(false);
    }

    /** New order = the full list of the task's item ids; positions become 0, 1, 2, ... */
    @Transactional
    public Optional<List<ChecklistItemResponse>> reorder(UUID taskId, List<UUID> orderedIds, String email) {
        return ownedTask(taskId, email).map(task -> {
            List<ChecklistItem> items = checklistItemRepository.findByScheduleItemIdOrderByPositionAsc(taskId);
            checkSameItems(items.stream().map(ChecklistItem::getId).toList(), orderedIds);
            for (ChecklistItem item : items) {
                item.setPosition(orderedIds.indexOf(item.getId()));
            }
            checklistItemRepository.saveAll(items);
            return toResponses(taskId);
        });
    }

    // ---------------- rules (static, unit-tested) ----------------

    /** Trimmed text, 1-200 characters. */
    static String cleanText(String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("A checklist item needs some text.");
        }
        if (clean.length() > MAX_TEXT) {
            throw new IllegalArgumentException("A checklist item can be at most " + MAX_TEXT + " characters.");
        }
        return clean;
    }

    static void checkRoomForOneMore(int currentCount) {
        if (currentCount >= MAX_ITEMS) {
            throw new IllegalArgumentException("A task can have at most " + MAX_ITEMS + " checklist items.");
        }
    }

    /** The new order must contain exactly the task's items, each once. */
    static void checkSameItems(List<UUID> current, List<UUID> ordered) {
        if (ordered == null || ordered.size() != current.size()
                || new HashSet<>(ordered).size() != ordered.size()
                || !new HashSet<>(ordered).equals(new HashSet<>(current))) {
            throw new IllegalArgumentException("The new order must list each checklist item of the task exactly once.");
        }
    }

    // ---------------- helpers ----------------

    private Optional<ScheduleItem> ownedTask(UUID taskId, String email) {
        return scheduleItemRepository.findById(taskId)
                .filter(task -> task.getUser().getEmail().equals(email));   // ownership check
    }

    private List<ChecklistItemResponse> toResponses(UUID taskId) {
        return checklistItemRepository.findByScheduleItemIdOrderByPositionAsc(taskId)
                .stream()
                .map(ChecklistItemResponse::from)
                .toList();
    }
}