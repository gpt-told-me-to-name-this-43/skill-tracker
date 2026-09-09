package com.skilltracker.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} with a free-standing offset. The public API pages with {@code limit}/{@code
 * offset} rather than page numbers, so page-number arithmetic would not round-trip.
 */
public record OffsetLimit(long offset, int limit, Sort sort) implements Pageable {

    public static OffsetLimit of(int limit, int offset, Sort sort) {
        return new OffsetLimit(offset, limit, sort);
    }

    @Override
    public int getPageNumber() {
        return (int) (offset / Math.max(limit, 1));
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetLimit(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetLimit(offset - limit, limit, sort) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetLimit(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetLimit((long) pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset > 0;
    }

    @Override
    public boolean isPaged() {
        return true;
    }
}
