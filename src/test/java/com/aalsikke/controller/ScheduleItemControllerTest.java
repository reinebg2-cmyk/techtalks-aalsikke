package com.aalsikke.controller;

import com.aalsikke.dto.ScheduleItemResponse;
import com.aalsikke.model.ItemType;
import com.aalsikke.model.ScheduleItem;
import com.aalsikke.security.CustomUserDetailsService;
import com.aalsikke.security.JwtService;
import com.aalsikke.service.ScheduleItemService;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KAN-335: HTTP layer of /api/schedule-items. The real JwtAuthFilter and security rules run;
 * JwtService is mocked so "Bearer good-token" means owner@example.com.
 */
@WebMvcTest(ScheduleItemController.class)
@Import(WebSecurityTestConfig.class)
class ScheduleItemControllerTest {

    private static final String OWNER = "owner@example.com";
    private static final String GOOD_TOKEN = "Bearer good-token";
    private static final String TASK_JSON = "{\"title\":\"Read chapter 3\",\"itemType\":\"PERSONAL\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduleItemService scheduleItemService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void goodTokenBelongsToOwner() {
        UserDetails owner = User.withUsername(OWNER).password("unused").authorities("ROLE_USER").build();
        when(jwtService.extractUsername("good-token")).thenReturn(OWNER);
        when(userDetailsService.loadUserByUsername(OWNER)).thenReturn(owner);
        when(jwtService.isTokenValid("good-token", owner)).thenReturn(true);
        when(jwtService.extractUsername("bad-token")).thenThrow(new MalformedJwtException("tampered"));
    }

    // ---------------- no token / bad token: 401 ----------------

    @Test
    void listingTasksWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/schedule-items/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    void creatingTaskWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/schedule-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASK_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    void editingTaskWithoutTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/schedule-items/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASK_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    void deletingTaskWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/schedule-items/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scheduleItemService);
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/schedule-items/me").header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(scheduleItemService);
    }

    // ---------------- someone else's task: 403 ----------------

    @Test
    void editingAnotherUsersTaskReturns403() throws Exception {
        UUID someoneElsesTask = UUID.randomUUID();
        when(scheduleItemService.updateItem(eq(someoneElsesTask), any(), eq(OWNER))).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/schedule-items/{id}", someoneElsesTask)
                        .header("Authorization", GOOD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASK_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingAnotherUsersTaskReturns403() throws Exception {
        UUID someoneElsesTask = UUID.randomUUID();
        when(scheduleItemService.deleteItem(someoneElsesTask, OWNER)).thenReturn(false);

        mockMvc.perform(delete("/api/schedule-items/{id}", someoneElsesTask).header("Authorization", GOOD_TOKEN))
                .andExpect(status().isForbidden());
    }

    // ---------------- the owner: allowed ----------------

    @Test
    void editingOwnTaskReturns200WithTheTask() throws Exception {
        UUID ownTask = UUID.randomUUID();
        ScheduleItem item = new ScheduleItem();
        item.setId(ownTask);
        item.setTitle("Read chapter 3");
        item.setItemType(ItemType.PERSONAL);
        when(scheduleItemService.updateItem(eq(ownTask), any(), eq(OWNER)))
                .thenReturn(Optional.of(ScheduleItemResponse.from(item)));

        mockMvc.perform(put("/api/schedule-items/{id}", ownTask)
                        .header("Authorization", GOOD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TASK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownTask.toString()))
                .andExpect(jsonPath("$.title").value("Read chapter 3"));
    }

    @Test
    void listingTasksUsesTheEmailFromTheToken() throws Exception {
        when(scheduleItemService.getItemsForCurrentUser(OWNER)).thenReturn(List.of());

        mockMvc.perform(get("/api/schedule-items/me").header("Authorization", GOOD_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(scheduleItemService).getItemsForCurrentUser(OWNER);
    }

    @Test
    void creatingTaskWithInvalidInputReturns400WithFieldList() throws Exception {
        mockMvc.perform(post("/api/schedule-items")
                        .header("Authorization", GOOD_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$[*].field", containsInAnyOrder("title", "itemType")));

        verifyNoInteractions(scheduleItemService);
    }
}