package org.example.cw3ilp.api.controller;
import org.example.cw3ilp.service.UserIDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * REST controller that handles requests related ot user IDs
 * <p>
 * Exposes GET endpoint <code>/uid/</code>, returning a user ID provided by
 * {@link UserIDService}
 * </p>
 */
@RestController
@RequestMapping("/api/v1")
public class UserIDController {

    private final UserIDService userIDService;

    @Autowired
    public UserIDController(UserIDService userIDService) {
        this.userIDService = userIDService;
    }

    /**
     * Handles HTTP GET requests to the <code>/uid</code> endpoint.
     * <p>
     * Returns the user ID obtained from the {@link UserIDService}.
     * </p>
     * @return the user ID string
     */
    @GetMapping("/uid")
    public String getUid() {
        return userIDService.getUserID().uid();
    }

}
