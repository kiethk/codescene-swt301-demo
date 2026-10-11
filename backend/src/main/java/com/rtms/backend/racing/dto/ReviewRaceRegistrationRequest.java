package com.rtms.backend.racing.dto;

import com.rtms.backend.racing.enums.RaceRegistrationStatus;

public class ReviewRaceRegistrationRequest {
    private RaceRegistrationStatus status;
    private String managerFeedback;

    public RaceRegistrationStatus getStatus() {
        return status;
    }

    public void setStatus(RaceRegistrationStatus status) {
        this.status = status;
    }

    public String getManagerFeedback() {
        return managerFeedback;
    }

    public void setManagerFeedback(String managerFeedback) {
        this.managerFeedback = managerFeedback;
    }
}
