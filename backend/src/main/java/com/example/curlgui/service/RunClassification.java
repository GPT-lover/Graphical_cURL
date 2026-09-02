package com.example.curlgui.service;

/**
 * One consistent status classification for loop results:
 * <ul>
 *   <li>2xx -> {@code SUCCESS}</li>
 *   <li>3xx -> {@code REDIRECT} (a response, but not a 2xx success)</li>
 *   <li>1xx / 4xx / 5xx / network error / no response -> {@code FAILED}</li>
 * </ul>
 */
final class RunClassification {

    static final String SUCCESS = "SUCCESS";
    static final String REDIRECT = "REDIRECT";
    static final String FAILED = "FAILED";

    private RunClassification() {
    }

    static String classify(RunOutcome outcome) {
        Integer status = outcome.status();
        if (status == null) {
            return FAILED; // network error / timeout / other
        }
        if (status >= 200 && status < 300) {
            return SUCCESS;
        }
        if (status >= 300 && status < 400) {
            return REDIRECT;
        }
        return FAILED;
    }
}
