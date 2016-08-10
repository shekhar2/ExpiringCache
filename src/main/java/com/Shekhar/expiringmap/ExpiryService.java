package com.shekhar.expiringmap;

import com.shekhar.expiringmap.util.Clock;
import com.shekhar.expiringmap.util.ExpiryEntry;
import com.shekhar.expiringmap.util.WaitService;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Class responsible for the expiry logic.
 *
 * @param <K>
 */
public class ExpiryService<K> {

    public void attemptExpiry(Clock clock, WaitService waitService, BlockingQueue<ExpiryEntry<K>> queue, Map<K, ?> backingMap) throws InterruptedException {
        ExpiryEntry head = queue.take();

        if (head.expiry() <= clock.now()) {
            backingMap.remove(head.key());
        } else {
            waitForItToExpire(clock, waitService, queue, head);
        }
    }

    private void waitForItToExpire(Clock clock, WaitService waitService, BlockingQueue<ExpiryEntry<K>> queue, ExpiryEntry head) throws InterruptedException {
        long waitTime = head.expiry() - clock.now();
        queue.offer(head);
        synchronized (WaitService.class) {
            if (waitTime > 0 && queue.peek().key().equals(head.key())) //ensure head has not been replaced
                waitService.doWait(ms(waitTime), ns(waitTime));
        }
    }

    private int ns(long waitTime) {
        return (int) Math.floor(waitTime % 1000000);
    }

    private long ms(long waitTime) {
        return (long) Math.floor(waitTime / 1000000);
    }
}
