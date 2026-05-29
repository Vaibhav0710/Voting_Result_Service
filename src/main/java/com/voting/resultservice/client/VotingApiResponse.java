package com.voting.resultservice.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic wrapper matching the Voting Service's {@code ApiResponse<T>} structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotingApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
}
