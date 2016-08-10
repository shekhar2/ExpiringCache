package com.shekhar.expiringmap;

import com.shekhar.expiringmap.util.Clock;
import com.shekhar.expiringmap.util.ExpiryEntry;
import com.shekhar.expiringmap.util.WaitService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 *  HashMap backed cache that provides configurable expiry.
 *  Items are written along with an expiry duration.
 *  <p>
 *
 *  Expiry times are added into a priority blocking queue so the next
 *  entry to expire will be at the head.
 *  <p>
 *  A separate thread reads these, waits for the expiry time and then
 *  removes them from the map.
 *  <p>
 *  Finally new writes will notify the potentially waiting expiry
 *  thread if something more imminent turns up.
 *
 *  @param <K> the type of keys maintained by this map
 *  @param <V> the type of mapped values
 */

public class ExpiringMap<K, V> implements ExpireMap<K, V> {
    private Map<K, V> backingMap = new ConcurrentHashMap<>();
    private Clock clock;
    private WaitService waitService;
    private PriorityBlockingQueue<ExpiryEntry<K>> queue = new PriorityBlockingQueue<>(
            10, (e1, e2) -> e1.expiry().compareTo(e2.expiry()));


    public ExpiringMap() {
        this(System::nanoTime);
    }

    public ExpiringMap(Clock clock) {
        this(clock, WaitService.DEFAULT);
    }

    public ExpiringMap(Clock clock, WaitService waitService) {
        this.clock = clock;
        this.waitService = waitService;
        startExpiryService();
    }

    private void startExpiryService() {
        Thread thread = new Thread(() -> {
            ExpiryService service = new ExpiryService<K>();
            while (true) {
                try {
                    service.attemptExpiry(clock, waitService, queue, backingMap);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public synchronized void put(K key, V value, long timeoutMs) {
        validate(timeoutMs);

        long expiryTime = clock.now() + MILLISECONDS.toNanos(timeoutMs);

        queue.add(new ExpiryEntry<>(expiryTime, key));

        wakeEvictionIfEarlierEntry(expiryTime);

        backingMap.put(key, value);
    }

    private void wakeEvictionIfEarlierEntry(long expiryTime) {
        ExpiryEntry<K> head = queue.peek();
        if (head != null && expiryTime <= head.expiry()) {
            synchronized (WaitService.class) {
                waitService.doNotify();
            }
        }
    }

    private void validate(long timeoutMs) {
        if (timeoutMs < 0)
            throw new IllegalArgumentException("Timeout must be a positive value");
    }

    @Override
    public synchronized V get(K key) {
        return backingMap.get(key);
    }

    @Override
    public synchronized void remove(K key) {
        backingMap.remove(key);
    }

    public int size() {
        return backingMap.size();
    }
}
