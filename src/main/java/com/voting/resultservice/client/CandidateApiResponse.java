package com.voting.resultservice.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic wrapper matching the Candidate Service's {@code ApiResponse<T>} structure.
 * <p>
 * Required for correct Feign deserialization — the Candidate Service
 * wraps all responses in this envelope format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
}
