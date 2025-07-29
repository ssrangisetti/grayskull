package com.flipkart.grayskull.spimpl.audit;

import com.flipkart.grayskull.models.db.AuditEntry;

public sealed interface AuditOrTick {

    record Tick() implements AuditOrTick {
    }

    record Audit(AuditEntry entry, long counter) implements AuditOrTick {
    }
}
