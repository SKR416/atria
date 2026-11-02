package io.github.skr416.atria.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Unified API response envelope for all Atria REST endpoints.
 *
 * @param <T> payload type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Business status code (200 for success, or specific ErrorCode value).
     */
    private int code;

    /**
     * Localized human-readable message resolved via Spring MessageSource.
     */
    private String message;

    /**
     * Response payload.
     */
    private T data;

    /**
     * Server UTC timestamp in ISO-8601 format.
     */
    private Instant timestamp;

    public static <T> ApiResponse<T> ok(T data, String localizedMessage) {
        return ApiResponse.<T>builder()
                .code(ErrorCode.SUCCESS.getCode())
                .message(localizedMessage)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "OK");
    }

    public static <T> ApiResponse<T> error(int code, String localizedMessage) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(localizedMessage)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String localizedMessage, T errorDetails) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(localizedMessage)
                .data(errorDetails)
                .timestamp(Instant.now())
                .build();
    }
}
