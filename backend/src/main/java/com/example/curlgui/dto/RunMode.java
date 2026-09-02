package com.example.curlgui.dto;

/** How a "run multiple" loop executes its requests. */
public enum RunMode {
    /** One request at a time; the next starts only after the previous finishes. */
    SEQUENTIAL,
    /** Requests start according to the delay and run concurrently (bounded). */
    PARALLEL
}
