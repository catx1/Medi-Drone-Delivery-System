package org.example.cw3ilp.api.model;
import jakarta.validation.constraints.NotNull;

public record UserID(@NotNull String uid) {

    public UserID(String uid) {
        this.uid = uid;
    }

    @Override
    public String uid() {
        return uid;
    }

}
