package com.voting.resultservice.client;

import java.util.List;

/**
 * Spring Data page as serialised by Voting Service ({@code VIA_DTO} mode).
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {

    public record PageMetadata(int size, int number, long totalElements, int totalPages) {
    }

    public boolean isLast() {
        return page == null || page.number() + 1 >= page.totalPages();
    }
}
