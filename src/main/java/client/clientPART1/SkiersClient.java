

package client.clientPART1;

import pojo.LiftRideEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkiersClient {
    private static final int TOTAL_EVENTS = 200000;
    private static final int INITIAL_THREAD_COUNT = 32;
    private static final int REQUESTS_PER_THREAD = 1000;
    private static final int THREAD_POOL_SIZE = 150;
    private static final String SERVER_URL = "http://44.243.212.143:8080/assignment1_war";
    private static final int QUEUE_SIZE = TOTAL_EVENTS;

    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    public static void main(String[] args) {
        BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        Thread eventGenerateThread = new Thread(new EventGenerateThread(eventQueue, TOTAL_EVENTS));
        eventGenerateThread.start();
        System.out.println("Generating events...");

        try {
            eventGenerateThread.join();
        } catch (InterruptedException e) {
            System.out.println("Event generation thread interrupted: " + e.getMessage());
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREAD_COUNT);

        long startTime = System.nanoTime();
        System.out.println("Starting event sending with " + INITIAL_THREAD_COUNT + " initial threads.");

        for (int i = 0; i < INITIAL_THREAD_COUNT; i++) {
            executor.submit(new SendEventThread(eventQueue, SERVER_URL, REQUESTS_PER_THREAD, successfulRequests, failedRequests, initialLatch));
        }

        try {
            initialLatch.await();
            System.out.println("Initial 32 threads completed.");
        } catch (InterruptedException e) {
            System.out.println("Waiting for initial threads interrupted: " + e.getMessage());
        }

        System.out.println("Starting rest of events");
        int remainEvents = TOTAL_EVENTS - (INITIAL_THREAD_COUNT * REQUESTS_PER_THREAD);
        System.out.println("Remaining events: " + remainEvents);

        List<Future<?>> futures = new ArrayList<>();
        while (remainEvents > 0) {
            int threadRequests = Math.min(remainEvents, REQUESTS_PER_THREAD);
            Future<?> future = executor.submit(new SendEventThread(eventQueue, SERVER_URL, threadRequests, successfulRequests, failedRequests, null));
            futures.add(future);
            remainEvents -= threadRequests;
        }

        // 等待所有任务完成
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("Thread pool termination interrupted: " + e.getMessage());
        }

        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        // 转换时间单位：纳秒 → 秒
        double totalTimeInSeconds = totalTime / 1_000_000_000.0;
        // 计算吞吐量
        double totalThroughput = (successfulRequests.get() + failedRequests.get()) / totalTimeInSeconds;
        double successThroughput = successfulRequests.get() / totalTimeInSeconds;
        System.out.printf("Total Throughput (including failures): %.2f requests/sec\n", totalThroughput);
        System.out.printf("Successful Throughput: %.2f requests/sec\n", successThroughput);


        System.out.println("Total client threads: " + THREAD_POOL_SIZE);
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());
        System.out.println("Total runtime: " + totalTime / 1_000_000 + " ms");

        System.out.println("Total Throughput (including failures): " + totalThroughput + " requests/sec");
        System.out.println("Successful Throughput: " + successThroughput + " requests/sec");
        System.out.println("Client execution completed. Exiting...");
        System.exit(0);
    }
}

