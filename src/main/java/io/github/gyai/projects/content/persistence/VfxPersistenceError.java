package io.github.gyai.projects.content.persistence;

import java.util.Objects;

/** Stable, bounded error information returned by the VFX persistence boundary. */
public record VfxPersistenceError(String code, String path, String detail) {
    public static final String INVALID_UTF8 = "INVALID_UTF8";
    public static final String BOM_REJECTED = "BOM_REJECTED";
    public static final String DOCUMENT_TOO_LARGE = "DOCUMENT_TOO_LARGE";
    public static final String INVALID_JSON = "INVALID_JSON";
    public static final String TRAILING_DATA = "TRAILING_DATA";
    public static final String DUPLICATE_KEY = "DUPLICATE_KEY";
    public static final String UNKNOWN_KEY = "UNKNOWN_KEY";
    public static final String MISSING_VALUE = "MISSING_VALUE";
    public static final String NULL_REQUIRED_FIELD = "NULL_REQUIRED_FIELD";
    public static final String INVALID_VALUE = "INVALID_VALUE";
    public static final String WRONG_FORMAT = "WRONG_FORMAT";
    public static final String UNSUPPORTED_SCHEMA = "UNSUPPORTED_SCHEMA";
    public static final String WRONG_KIND = "WRONG_KIND";
    public static final String NON_INTEGRAL_NUMBER = "NON_INTEGRAL_NUMBER";
    public static final String REVISION_OVERFLOW = "REVISION_OVERFLOW";
    public static final String NEGATIVE_REVISION = "NEGATIVE_REVISION";
    public static final String NON_FINITE_NUMBER = "NON_FINITE_NUMBER";
    public static final String UNSUPPORTED_ENUM = "UNSUPPORTED_ENUM";
    public static final String UNKNOWN_VARIANT = "UNKNOWN_VARIANT";
    public static final String VARIANT_MISMATCH = "VARIANT_MISMATCH";
    public static final String NESTING_TOO_DEEP = "NESTING_TOO_DEEP";
    public static final String COLLECTION_TOO_LARGE = "COLLECTION_TOO_LARGE";
    public static final String STRING_TOO_LONG = "STRING_TOO_LONG";
    public static final String INVALID_NAMESPACED_ID = "INVALID_NAMESPACED_ID";
    public static final String INVALID_LOCAL_ID = "INVALID_LOCAL_ID";
    public static final String ID_MISMATCH = "ID_MISMATCH";
    public static final String DUPLICATE_ID = "DUPLICATE_ID";
    public static final String DUPLICATE_LOCAL_ID = "DUPLICATE_LOCAL_ID";
    public static final String DUPLICATE_EMISSION_ID = "DUPLICATE_EMISSION_ID";
    public static final String DUPLICATE_PRIMITIVE_ID = "DUPLICATE_PRIMITIVE_ID";
    public static final String DUPLICATE_HOOK = "DUPLICATE_HOOK";
    public static final String RESERVED_HOOK = "RESERVED_HOOK";
    public static final String NUMBER_OUT_OF_RANGE = "NUMBER_OUT_OF_RANGE";
    public static final String CONTRADICTORY_DEFINITION = "CONTRADICTORY_DEFINITION";
    public static final String INVALID_DEFINITION = "INVALID_DEFINITION";
    public static final String INVALID_BASE_REVISION = "INVALID_BASE_REVISION";
    public static final String CONFLICT = "CONFLICT";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String TARGET_EXISTS = "TARGET_EXISTS";
    public static final String MALFORMED_DOCUMENT = "MALFORMED_DOCUMENT";
    public static final String UNSAFE_PATH = "UNSAFE_PATH";
    public static final String HISTORY_NOT_FOUND = "HISTORY_NOT_FOUND";
    public static final String HISTORY_COLLISION = "HISTORY_COLLISION";
    public static final String HISTORY_ID_MISMATCH = "HISTORY_ID_MISMATCH";
    public static final String HISTORY_REVISION_MISMATCH = "HISTORY_REVISION_MISMATCH";
    public static final String IO_FAILURE = "IO_FAILURE";
    public static final String CLOSED = "CLOSED";
    public static final String LOCK_UNAVAILABLE = "LOCK_UNAVAILABLE";

    public VfxPersistenceError {
        code = Objects.requireNonNull(code, "code");
        path = Objects.requireNonNull(path, "path");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public String message() {
        return detail;
    }
}
