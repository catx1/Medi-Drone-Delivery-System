package org.example.cw3ilp.service;

import org.example.cw3ilp.api.model.UserID;
import org.springframework.stereotype.Service;

/**
 * Service class responsible for providing a student's user ID (UID)
 * <p>
 *     Returned as a {@link  UserID} object.
 * </p>
 */
@Service
public class UserIDService {

    /**
     * Returns the student's user ID as a {@link  UserID} object.
     *
     * @return a {@link  UserID} containing the student's unique identifier (e.g. "s2524237")
     *
     */
    private static final String USER_ID = "s2524237";
    public UserID getUserID(){
        return new UserID(USER_ID);
        }
}
