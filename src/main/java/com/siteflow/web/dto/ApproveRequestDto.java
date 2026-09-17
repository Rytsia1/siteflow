package com.siteflow.web.dto;

public record ApproveRequestDto(String note) {

    /** dto is null when the request body was omitted (@RequestBody(required = false)). */
    public static String noteOf(ApproveRequestDto dto) {
        return dto != null ? dto.note() : null;
    }
}
