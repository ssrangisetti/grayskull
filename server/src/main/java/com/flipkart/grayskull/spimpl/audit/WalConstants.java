package com.flipkart.grayskull.spimpl.audit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class WalConstants {
    public static final String AUDIT_ERROR_METRIC = "audit-log-error";
    public static final String ACTION_TAG = "action";
    public static final String EXCEPTION_TAG = "exception";
    public static final String LOG_ACTION = "log";
    public static final String CLEAN_ACTION = "clean";
}
