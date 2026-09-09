package com.skilltracker.json;

import java.util.Objects;

/**
 * Distinguishes "field absent from the JSON body" from "field explicitly set to null".
 *
 * <p>PATCH endpoints inherited the {@code model_dump(exclude_unset=True)} semantics of the FastAPI
 * backend: an omitted field keeps its stored value while an explicit {@code null} clears it.
 */
public final class Patch<T> {

    private static final Patch<?> ABSENT = new Patch<>(false, null);

    private final boolean present;
    private final T value;

    private Patch(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    public static <T> Patch<T> absent() {
        return (Patch<T>) ABSENT;
    }

    public static <T> Patch<T> of(T value) {
        return new Patch<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }

    /** The submitted value, which is null when the client explicitly sent null. */
    public T value() {
        return value;
    }

    public T orElse(T fallback) {
        return present ? value : fallback;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Patch<?> patch && patch.present == present && Objects.equals(patch.value, value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(present, value);
    }

    @Override
    public String toString() {
        return present ? "Patch[" + value + "]" : "Patch.absent";
    }
}
