import client.clientPART1.EventGenerateThread;
import client.clientPART1.SendEventThread;
import pojo.LiftRideEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkiersClientTest {
    // 总事件数 1000
    private static final int TOTAL_EVENTS = 1000;
    // 只有1个线程发送所有请求
    private static final int THREAD_COUNT = 1;
    // 每个任务（此处仅有1个任务）处理 1000 个请求
    private static final int REQUESTS_PER_THREAD = 1000;
    // 服务地址
    private static final String SERVER_URL = "http://34.213.76.93:8080/assignment1_war";
    // 队列大小与总事件数一致
    private static final int QUEUE_SIZE = TOTAL_EVENTS;

    // 用于统计成功和失败的请求数
    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        // 创建事件队列，并生成所有事件（依赖 EventGenerateThread 实现）
        BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        Thread eventGenerateThread = new Thread(new EventGenerateThread(eventQueue, TOTAL_EVENTS));
        System.out.println("Generating events...");
        eventGenerateThread.start();
        try {
            eventGenerateThread.join();
        } catch (InterruptedException e) {
            System.out.println("Event generation thread interrupted: " + e.getMessage());
        }

        // 创建只有1个线程的线程池
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        long startTime = System.nanoTime();
        System.out.println("Starting sending events with " + THREAD_COUNT + " thread.");

        // 提交任务，使用 SendEventThread 发送 1000 个请求
        executor.submit(new SendEventThread(
                eventQueue,
                SERVER_URL,
                REQUESTS_PER_THREAD,
                successfulRequests,
                failedRequests,
                null,  // 不需要初始阶段的 CountDownLatch
                latch,
                TOTAL_EVENTS
        ));

        // 等待该任务完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            System.out.println("Waiting for task interrupted: " + e.getMessage());
        }

        long endTime = System.nanoTime();
        executor.shutdown();

        // 用 double 精度计算总执行时间（单位：秒）
        double totalTimeSeconds = (endTime - startTime) / 1e9;
        System.out.printf("Total execution time: %.3f seconds\n", totalTimeSeconds);

        // 计算实际吞吐量（请求/秒）
        double measuredThroughput = (successfulRequests.get() + failedRequests.get()) / totalTimeSeconds;
        double successThroughput = successfulRequests.get() / totalTimeSeconds;
        System.out.println("Test Little's Law");
        System.out.printf("Measured Throughput (including failures): %.2f requests/sec\n", measuredThroughput);
        System.out.printf("Measured Successful Throughput: %.2f requests/sec\n", successThroughput);
        System.out.println("Successful requests: " + successfulRequests.get());
        System.out.println("Failed requests: " + failedRequests.get());

        // 根据 Little's Law计算预测吞吐量
        // 对于单线程测试，系统中的平均并发请求 L=1，
        // 每个请求的平均响应时间 W = totalTimeSeconds / TOTAL_EVENTS (单位：秒)
        double avgResponseTime = totalTimeSeconds / TOTAL_EVENTS;
        double predictedThroughput = 1.0 / avgResponseTime;
        // 注意：此处 predictedThroughput 实际上等于 TOTAL_EVENTS / totalTimeSeconds，与 measuredThroughput 一致
        System.out.printf("Predicted Throughput via Little's Law: %.2f requests/sec\n", predictedThroughput);

        System.out.println("Test execution completed. Exiting...");

        // 为了观察输出，可适当等待后退出
        Thread.sleep(1000);
        System.exit(0);
    }
}