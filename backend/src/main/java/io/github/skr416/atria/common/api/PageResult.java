package io.github.skr416.atria.common.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Standardized pagination envelope for tabular queries (PrimeVue DataTable compatible).
 *
 * @param <T> row item type
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /**
     * Current page records.
     */
    private List<T> list;

    /**
     * Total record count across all pages.
     */
    private long total;

    /**
     * 1-based page index.
     */
    private int page;

    /**
     * Page size.
     */
    private int size;

    public static <T> PageResult<T> from(Page<T> springPage) {
        return PageResult.<T>builder()
                .list(springPage.getContent())
                .total(springPage.getTotalElements())
                .page(springPage.getNumber() + 1)
                .size(springPage.getSize())
                .build();
    }
}
