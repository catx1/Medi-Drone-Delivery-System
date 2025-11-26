package org.example.cw3ilp.controllerTests;

import org.example.cw3ilp.api.controller.UserIDController;
import org.example.cw3ilp.api.model.UserID;
import org.example.cw3ilp.service.UserIDService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;


@WebMvcTest(UserIDController.class)
public class UserIDControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserIDService userIDService;

    @Test
    @DisplayName("GET /uid should return user ID")
    void uid_returnsUserId() throws Exception {
        when(userIDService.getUserID()).thenReturn(new UserID("s2524237"));

        mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().string("s2524237"));
    }

    @Test
    @DisplayName("GET /uid should return content type text/plain")
    void uid_returnsTextPlain() throws Exception {
        when(userIDService.getUserID()).thenReturn(new UserID("s2524237"));

        mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"));
    }

    @Test
    @DisplayName("GET /uid should not accept POST method")
    void uid_postMethod_returns405() throws Exception {
        mockMvc.perform(post("/api/v1/uid"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /uid should return non-empty string")
    void uid_returnsNonEmptyString() throws Exception {
        when(userIDService.getUserID()).thenReturn(new UserID("s2524237"));

        mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(emptyString())));
    }

    @Test
    @DisplayName("GET /uid should always return the same ID")
    void uid_returnsConsistentId() throws Exception {
        when(userIDService.getUserID()).thenReturn(new UserID("s2524237"));

        // Call twice and verify that the result is the same
        String firstCall = mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondCall = mockMvc.perform(get("/api/v1/uid"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(firstCall, secondCall);
    }

    @Test
    @DisplayName("GET /uid with wrong HTTP methods should return 405")
    void uid_wrongMethods_returns405() throws Exception {
        mockMvc.perform(post("/api/v1/uid"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put("/api/v1/uid"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/v1/uid"))
                .andExpect(status().isMethodNotAllowed());
    }
}
