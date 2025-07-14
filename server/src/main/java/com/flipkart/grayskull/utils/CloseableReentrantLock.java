package com.flipkart.grayskull.utils;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation copied from <a href="https://stackoverflow.com/a/46248923">https://stackoverflow.com/a/46248923</a>
 */
public class CloseableReentrantLock extends ReentrantLock {

    public ResourceLock lockAsResource() {
        lock();
        return this::unlock;
    }

    public interface ResourceLock extends AutoCloseable {
        @Override
        void close();
    }
}
