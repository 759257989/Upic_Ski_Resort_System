package client.clientPART2;

import pojo.LiftRideEvent;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkiersClient2 {
    // Total number of events (200,000 plus a few extras to test remainder handling)
    private static final int TOTAL_EVENTS = 200000;
    // First phase: 32 tasks, each processing 1000 events
    private static final int INITIAL_THREAD_COUNT = 32;
    // Number of requests per task (phase one fixed to 1000; phase two may handle a remainder)
    private static final int REQUESTS_PER_THREAD = 1000;
    // Maximum number of threads concurrently running
    private static final int THREAD_POOL_SIZE = 150;
    // Server URL
    private static final String SERVER_URL = "http://34.212.106.75:8080/assignment1_war";
    // Queue size equals total events
    private static final int QUEUE_SIZE = TOTAL_EVENTS;

    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    // Thread-safe collection for latency records.
    public static List<LatencyRecord> latencyRecords = Collections.synchronizedList(new ArrayList<>());

    // New: record the experiment start time (in ms)
    public static long experimentStartTime;

    public static void main(String[] args) throws InterruptedException {
        // Create the event queue and generate all events.
        BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        Thread eventGenerateThread = new Thread(new EventGenerateThread(eventQueue, TOTAL_EVENTS));
        System.out.println("Generating events...");
        eventGenerateThread.start();
        try {
            eventGenerateThread.join();
        } catch (InterruptedException e) {
            System.out.println("Event generation thread interrupted: " + e.getMessage());
        }

        // Create a fixed thread pool.
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // First phase: 32 tasks, each processing 1000 events.
        CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREAD_COUNT);
        int initialEvents = INITIAL_THREAD_COUNT * REQUESTS_PER_THREAD;

        // Compute remaining events and additional tasks.
        int remainEvents = TOTAL_EVENTS - initialEvents;
        int additionalTasks = (int) Math.ceil(remainEvents / (double) REQUESTS_PER_THREAD);
        int totalTasks = INITIAL_THREAD_COUNT + additionalTasks;
        CountDownLatch finalLatch = new CountDownLatch(totalTasks);

        // Set the experiment start time (in ms) right before sending requests.
        experimentStartTime = System.currentTimeMillis();
        long startTime = System.nanoTime();
        System.out.println("Starting event sending with " + INITIAL_THREAD_COUNT + " initial tasks.");

        // Submit first-phase tasks.
        for (int i = 0; i < INITIAL_THREAD_COUNT; i++) {
            executor.submit(new SendEventThread2(eventQueue, SERVER_URL, REQUESTS_PER_THREAD,
                    successfulRequests, failedRequests, initialLatch, finalLatch, TOTAL_EVENTS));
        }

        // Wait for first-phase tasks to complete.
        try {
            initialLatch.await();
            System.out.println("Initial " + INITIAL_THREAD_COUNT + " tasks completed.");
        } catch (InterruptedException e) {
            System.out.println("Waiting for initial tasks interrupted: " + e.getMessage());
        }

        System.out.println("Starting additional tasks for remaining events");
        System.out.println("Remaining events: " + remainEvents);

        // Submit second-phase tasks.
        for (int i = 0; i < additionalTasks; i++) {
            int requestsForTask = REQUESTS_PER_THREAD;
            if (i == additionalTasks - 1) {
                int leftover = remainEvents % REQUESTS_PER_THREAD;
                if (leftover > 0) {
                    requestsForTask = leftover;
                }
            }
            executor.submit(new SendEventThread2(eventQueue, SERVER_URL, requestsForTask,
                    successfulRequests, failedRequests, initialLatch, finalLatch, TOTAL_EVENTS));
        }

        // Wait for all tasks to complete.
        try {
            System.out.println("Waiting for all tasks to finish...");
            finalLatch.await();
            System.out.println("All tasks completed.");
        } catch (InterruptedException e) {
            System.out.println("Waiting for all tasks interrupted: " + e.getMessage());
        }



        // Shutdown executor.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("Forcing shutdown...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.out.println("Thread pool termination interrupted: " + e.getMessage());
        }
        // Record end time.
        long endTime = System.nanoTime();

        long totalTimeInSeconds = (endTime - startTime) / 1_000_000_000;
        System.out.println("Remaining events in queue: " + eventQueue.size());
        System.out.println("Total execution time: " + totalTimeInSeconds + " seconds");

        double totalThroughput = (successfulRequests.get() + failedRequests.get()) / (double) totalTimeInSeconds;
        double successThroughput = successfulRequests.get() / (double) totalTimeInSeconds;

        System.out.printf("Total Throughput (including failures): %.2f requests/sec\n", totalThroughput);
        System.out.printf("Successful Throughput: %.2f requests/sec\n", successThroughput);

        System.out.println("Total tasks: " + totalTasks);
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());

        // --- Process latency records for performance metrics ---
        // First, sort the latency records by start time.
        List<LatencyRecord> sortedRecords = new ArrayList<>(latencyRecords);
        sortedRecords.sort(Comparator.comparingLong(LatencyRecord::getStartTimeMillis));

        List<Long> latencies = new ArrayList<>();
        for (LatencyRecord r : sortedRecords) {
            latencies.add(r.getLatencyMillis());
        }
        double sum = 0;
        for (long lat : latencies) {
            sum += lat;
        }
        double mean = sum / latencies.size();
        long median;
        int size = latencies.size();
        if (size % 2 == 0) {
            median = (latencies.get(size / 2 - 1) + latencies.get(size / 2)) / 2;
        } else {
            median = latencies.get(size / 2);
        }
        // max and min latency
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (LatencyRecord r : latencyRecords) {
            long lat = r.getLatencyMillis();
            if(lat < min) {
                min = lat;
            }
            if(lat > max) {
                max = lat;
            }
        }
        int p99Index = (int) Math.ceil(0.99 * latencies.size()) - 1;
        long p99 = latencies.get(p99Index);

        System.out.println("Performance metrics (latency in ms):");
        System.out.printf("Mean response time: %.2f ms\n", mean);
        System.out.println("Median response time: " + median + " ms");
        System.out.println("Min response time: " + min + " ms");
        System.out.println("Max response time: " + max + " ms");
        System.out.println("99th percentile response time: " + p99 + " ms");
        System.out.println("Total latency records: " + sortedRecords.size());

        // --- Write latency records to CSV, sorted by relative start time.
        // compute the relative start time = record.getStartTimeMillis() - experimentStartTime.
        String csvFile = "client_part2_latency.csv";
        try (PrintWriter pw = new PrintWriter(new File(csvFile))) {
            // CSV header.
            pw.println("relativeStartMillis,requestType,latencyMillis,responseCode");
            for (LatencyRecord record : sortedRecords) {
                long relativeStart = record.getStartTimeMillis() - experimentStartTime;
                pw.println(relativeStart + "," + record.getRequestType() + "," +
                        record.getLatencyMillis() + "," + record.getResponseCode());
            }
            System.out.println("Latency records written to " + csvFile);
        } catch (FileNotFoundException e) {
            System.err.println("Error writing latency CSV file: " + e.getMessage());
        }

        System.out.println("Client execution completed. Exiting...");
        Thread.sleep(1000);
        System.exit(0);
    }
}
