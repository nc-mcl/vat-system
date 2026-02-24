package com.netcompany.vat.coredomain;

import java.util.function.Function;

/**
 * A discriminated union representing either a successful value ({@link Ok}) or
 * a domain error ({@link Err}).
 *
 * <p>Modelled on the functional {@code Result} / {@code Either} pattern.
 * Use this instead of throwing exceptions for expected domain rule violations.
 * Exceptions are still appropriate for truly unexpected failures (I/O, bugs).
 *
 * <p>Example usage:
 * <pre>{@code
 * Result<MonetaryAmount> result = engine.calculateVat(transaction);
 * switch (result) {
 *     case Result.Ok<MonetaryAmount> ok  -> process(ok.value());
 *     case Result.Err<MonetaryAmount> err -> handleError(err.error());
 * }
 * }</pre>
 *
 * @param <T> the type of the success value
 */
public sealed interface Result<T> permits Result.Ok, Result.Err {

    /** A successful result containing a value. */
    record Ok<T>(T value) implements Result<T> {}

    /** A failed result containing a domain error. */
    record Err<T>(VatRuleError error) implements Result<T> {}

    // --- Static factories ---

    /** Wraps {@code value} in a successful result. */
    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    /** Wraps {@code error} in a failed result. */
    static <T> Result<T> err(VatRuleError error) {
        return new Err<>(error);
    }

    // --- Query methods ---

    /** Returns true if this result is a success. */
    default boolean isOk() {
        return this instanceof Ok<T>;
    }

    /** Returns true if this result is a failure. */
    default boolean isErr() {
        return this instanceof Err<T>;
    }

    // --- Transformation methods ---

    /**
     * Applies {@code mapper} to the success value, returning a new result.
     * If this is an {@link Err}, returns it unchanged.
     */
    default <U> Result<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Ok<T> ok   -> Result.ok(mapper.apply(ok.value()));
            case Err<T> err -> Result.err(err.error());
        };
    }

    /**
     * Applies {@code mapper} to the success value, where the mapper itself returns a {@link Result}.
     * Flattens the nested result. If this is an {@link Err}, returns it unchanged.
     */
    default <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
        return switch (this) {
            case Ok<T> ok   -> mapper.apply(ok.value());
            case Err<T> err -> Result.err(err.error());
        };
    }
}
