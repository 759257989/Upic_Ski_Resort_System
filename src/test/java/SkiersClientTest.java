import client.clientPART2.EventGenerateThread2;
import client.clientPART2.SendEventThread2;
import pojo.LiftRideEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkiersClientTest {
    private static final int TOTAL_EVENTS = 1000; // 只发送 1000 条请求
    private static final int INITIAL_THREAD_COUNT = 1; // 只用 1 个线程
    private static final int REQUESTS_PER_THREAD = 1000;
    private static final int THREAD_POOL_SIZE = 1;
    private static final String SERVER_URL = "http://34.216.236.14:8080/assignment1_war";
    private static final int QUEUE_SIZE = TOTAL_EVENTS;

    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);
    private static final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        Thread eventGenerateThread = new Thread(new EventGenerateThread2(eventQueue, TOTAL_EVENTS));
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
        System.out.println("Starting event sending with 1 thread.");

        executor.submit(new SendEventThread2(eventQueue, SERVER_URL, REQUESTS_PER_THREAD, successfulRequests, failedRequests, initialLatch, latencies));

        try {
            initialLatch.await();
            System.out.println("Single thread execution completed.");
        } catch (InterruptedException e) {
            System.out.println("Waiting for thread interrupted: " + e.getMessage());
        }

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("Thread pool termination interrupted: " + e.getMessage());
        }

        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;

        // latency calculation
        Collections.sort(latencies);
        double meanLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long medianLatency = latencies.get(latencies.size() / 2);
        long minLatency = latencies.get(0);
        long maxLatency = latencies.get(latencies.size() - 1);
        long p99Latency = latencies.get((int) (latencies.size() * 0.99));

        System.out.println("################# TEST RESULT #################");
        System.out.println("Total requests: " + TOTAL_EVENTS);
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());
        System.out.println("Total runtime: " + totalTime / 1_000_000 + " ms");

        // 吞吐量计算
        double totalTimeInSeconds = totalTime / 1_000_000_000.0;
        double throughput = successfulRequests.get() / totalTimeInSeconds;

        System.out.printf("Throughput: %.2f requests/sec\n", throughput);
        System.out.println();

        // Latency stats
        System.out.println("################# LATENCY #################");
        System.out.println("Mean Response Time: " + meanLatency + " ms");
        System.out.println("Median Response Time: " + medianLatency + " ms");
        System.out.println("P99 Response Time: " + p99Latency + " ms");
        System.out.println("Min Response Time: " + minLatency + " ms");
        System.out.println("Max Response Time: " + maxLatency + " ms");

        // Write latency stats to file
        try (FileWriter writer = new FileWriter("latencies_test.csv")) {
            writer.write("Start Time,Request Type,Latency,Response Code\n");
            for (Long latency : latencies) {
                writer.write(System.currentTimeMillis() + ",POST," + latency + ",201\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Test execution completed. Exiting...");
        System.exit(0);
    }
}