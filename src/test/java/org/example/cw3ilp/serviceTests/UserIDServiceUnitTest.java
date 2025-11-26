package org.example.cw3ilp.serviceTests;

import org.example.cw3ilp.api.model.UserID;
import org.example.cw3ilp.service.UserIDService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserIDServiceUnitTest {

    private UserIDService userIDService;

    @BeforeEach
    void setUp() {
        userIDService = new UserIDService();
    }

    // Basic null check (method is not null)
    @Test
    @DisplayName("getUserID should return non-null UserID")
    void getUserID_returnsNonNull() {
        UserID userID = userIDService.getUserID();
        assertNotNull(userID);
    }

    // Returns correct user id
    @Test
    @DisplayName("getUserID should return UserID with correct uid")
    void getUserID_returnsCorrectUid() {
        UserID userID = userIDService.getUserID();
        assertEquals("s2524237", userID.uid());
    }

    // ensures string is not null
    @Test
    @DisplayName("getUserID should return UserID with non-null uid string")
    void getUserID_uidStringIsNotNull() {
        UserID userID = userIDService.getUserID();
        assertNotNull(userID.uid());
    }

    // ensures string is not empty
    @Test
    @DisplayName("getUserID should return UserID with non-empty uid string")
    void getUserID_uidStringIsNotEmpty() {
        UserID userID = userIDService.getUserID();
        assertFalse(userID.uid().isEmpty());
    }

    // test consistency
    @Test
    @DisplayName("getUserID should return consistent UserID across multiple calls")
    void getUserID_consistentAcrossMultipleCalls() {
        UserID userID1 = userIDService.getUserID();
        UserID userID2 = userIDService.getUserID();
        assertEquals(userID1.uid(), userID2.uid());
    }

    // Verify string format
    @Test
    @DisplayName("getUserID should return UserID matching expected format")
    void getUserID_matchesExpectedFormat() {
        UserID userID = userIDService.getUserID();
        assertTrue(userID.uid().matches("^s\\d{7}$"),
                "UserID should be 's' followed by 7 digits");
    }

}
