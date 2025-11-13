package org.raincityvoices.ttrack.service.util;

import java.util.concurrent.locks.Lock;

/**
 * An AutoCloseable wrapper that allows locking a lock for a specific scope of code
 * using try-with-resources, e.g.:
 * 
 * @{code
 *   ReentrantLock myLock;
 * 
 *   try(AutoLock al = new AutoLock(myLock)) {
 *     ... do some work under the lock
 *   } // lock is automatically released on exiting the scope
 * }
 */
public class AutoLock implements AutoCloseable {

    private final Lock lock;
    public AutoLock(Lock lock) { this.lock = lock; lock.lock(); }
    @Override
    public void close() {
        lock.unlock();
    }
}
