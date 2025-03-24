//package client.clientPART1;
//
//import pojo.LiftRideEvent;
//
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class SkiersClient {
//    // 总事件数 200,000
//    private static final int TOTAL_EVENTS = 200000;
//    // 第一阶段任务数量：32 个，每个任务处理 1000 个事件
//    private static final int INITIAL_THREAD_COUNT = 32;
//    // 每个任务处理的事件数（第一阶段固定为 1000，第二阶段可能为最后一批不足 1000）
//    private static final int REQUESTS_PER_THREAD = 1000;
//    // 线程池最大并发线程数（不影响任务总数，只影响同时执行的任务数）
//    private static final int THREAD_POOL_SIZE = 150;
//    // 服务地址
//    private static final String SERVER_URL = "http://34.212.106.75:8080/assignment1_war";
//    // 队列大小与总事件数一致
//    private static final int QUEUE_SIZE = TOTAL_EVENTS;
//
//    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
//    private static final AtomicInteger failedRequests = new AtomicInteger(0);
//
//    public static void main(String[] args) throws InterruptedException {
//        // 创建事件队列，并生成所有事件
//        BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
//        Thread eventGenerateThread = new Thread(new EventGenerateThread(eventQueue, TOTAL_EVENTS));
//        eventGenerateThread.start();
//        System.out.println("Generating events...");
//
//        try {
//            eventGenerateThread.join();
//        } catch (InterruptedException e) {
//            System.out.println("Event generation thread interrupted: " + e.getMessage());
//        }
//
//        // 创建固定大小线程池，限制最大并发线程数
//        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
//
//        // 第一阶段：32 个任务，每个任务固定处理 1000 个事件
//        CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREAD_COUNT);
//        int initialEvents = INITIAL_THREAD_COUNT * REQUESTS_PER_THREAD;
//
//        // 计算剩余需要处理的事件数
//        int remainEvents = TOTAL_EVENTS - initialEvents;
//        // 计算第二阶段需要的任务数（不足 1000 的余数作为一个任务）
//        int additionalTasks = (int) Math.ceil(remainEvents / (double) REQUESTS_PER_THREAD);
//        // 总任务数
//        int totalTasks = INITIAL_THREAD_COUNT + additionalTasks;
//        // 用于等待所有任务结束
//        CountDownLatch finalLatch = new CountDownLatch(totalTasks);
//
//        long startTime = System.nanoTime();
//        System.out.println("Starting event sending with " + INITIAL_THREAD_COUNT + " initial tasks.");
//
//        // 提交第一阶段任务
//        for (int i = 0; i < INITIAL_THREAD_COUNT; i++) {
//            executor.submit(new SendEventThread(eventQueue, SERVER_URL, REQUESTS_PER_THREAD,
//                    successfulRequests, failedRequests, initialLatch, finalLatch, TOTAL_EVENTS));
//        }
//
//        // 等待第一阶段任务完成
//        try {
//            initialLatch.await();
//            System.out.println("Initial " + INITIAL_THREAD_COUNT + " tasks completed.");
//        } catch (InterruptedException e) {
//            System.out.println("Waiting for initial tasks interrupted: " + e.getMessage());
//        }
//
//        System.out.println("Starting additional tasks for remaining events");
//        System.out.println("Remaining events: " + remainEvents);
//
//        // 提交第二阶段任务
//        for (int i = 0; i < additionalTasks; i++) {
//            int requestsForTask = REQUESTS_PER_THREAD;
//            // 如果是最后一个任务且有余数，则只处理余下的事件
//            if (i == additionalTasks - 1) {
//                int leftover = remainEvents % REQUESTS_PER_THREAD;
//                if (leftover > 0) {
//                    requestsForTask = leftover;
//                }
//            }
//            executor.submit(new SendEventThread(eventQueue, SERVER_URL, requestsForTask,
//                    successfulRequests, failedRequests, initialLatch, finalLatch, TOTAL_EVENTS));
//        }
//
//        // 等待所有任务完成
//        try {
//            System.out.println("Waiting for all tasks to finish...");
//            finalLatch.await();
//            System.out.println("All tasks completed.");
//        } catch (InterruptedException e) {
//            System.out.println("Waiting for all tasks interrupted: " + e.getMessage());
//        }
//
//
//
//        // 关闭线程池
//        executor.shutdown();
//        try {
//            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
//                System.out.println("Forcing shutdown...");
//                executor.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            System.out.println("Thread pool termination interrupted: " + e.getMessage());
//        }
//        // 记录结束时间
//        long endTime = System.nanoTime();
//
//        // 计算总执行时间（单位：秒）
//        long totalTimeInSeconds = (endTime - startTime) / 1_000_000_000;
//        System.out.println("Remaining events in queue: " + eventQueue.size());
//
//        System.out.println("Total execution time: " + totalTimeInSeconds + " seconds");
//
//        // 计算吞吐量
//        double totalThroughput = (successfulRequests.get() + failedRequests.get()) / (double) totalTimeInSeconds;
//        double successThroughput = successfulRequests.get() / (double) totalTimeInSeconds;
//
//        System.out.printf("Total Throughput (including failures): %.2f requests/sec\n", totalThroughput);
//        System.out.printf("Successful Throughput: %.2f requests/sec\n", successThroughput);
//
//        System.out.println("Total tasks: " + totalTasks);
//        System.out.println("Successful requests: " + successfulRequests.get());
//        System.out.println("Failed requests: " + failedRequests.get());
//
//        System.out.println("Client execution completed. Exiting...");
//        Thread.sleep(1000);
//        System.exit(0);
//    }
//}
//
//
//

package client.clientPART1;

import pojo.LiftRideEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkiersClient {
    // Total events: 200,000
    private static final int TOTAL_EVENTS = 40000;
    // First-phase tasks: 32 tasks, each processing 1000 events
//    private static final int INITIAL_THREAD_COUNT = 32;
    private static final int INITIAL_THREAD_COUNT = 32;
    // Requests per thread
    private static final int REQUESTS_PER_THREAD = 1000;
    // Maximum concurrent threads in the thread pool
    private static final int THREAD_POOL_SIZE = 150;
    // Server URL
//    private static final String SERVER_URL = "http://34.221.211.187:8080/assignment1_war";
//    private static final String SERVER_URL = "http://localhost:8080/assignment1_war_exploded";
    private static final String SERVER_URL = "http://Sevlet-ALB-1615193705.us-west-2.elb.amazonaws.com/assignment1_war";
    // Queue size equals total events
    private static final int QUEUE_SIZE = TOTAL_EVENTS;

    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        // Create event queue and generate events.
        BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
        Thread eventGenerateThread = new Thread(new EventGenerateThread(eventQueue, TOTAL_EVENTS));
        System.out.println("Generating events...");
        eventGenerateThread.start();
        try {
            eventGenerateThread.join();
        } catch (InterruptedException e) {
            System.out.println("Event generation thread interrupted: " + e.getMessage());
        }

        // Create fixed thread pool.
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Create a latch to trigger the additional tasks when one first-phase task finishes.
        CountDownLatch triggerAdditionalLatch = new CountDownLatch(1);

        // First-phase: 32 tasks, each processing 1000 events.
        int initialEvents = INITIAL_THREAD_COUNT * REQUESTS_PER_THREAD;

        // Calculate remaining events and the number of additional tasks.
        int remainEvents = TOTAL_EVENTS - initialEvents;
        int additionalTasks = (int) Math.ceil(remainEvents / (double) REQUESTS_PER_THREAD);
        int totalTasks = INITIAL_THREAD_COUNT + additionalTasks;
        // Latch to wait for all tasks to finish.
        CountDownLatch finalLatch = new CountDownLatch(totalTasks);

        long startTime = System.nanoTime();
        System.out.println("Submitting initial tasks...");

        // Submit first-phase tasks. (Pass triggerAdditional=true and the latch.)
        for (int i = 0; i < INITIAL_THREAD_COUNT; i++) {
            executor.submit(new SendEventThread(eventQueue, SERVER_URL, REQUESTS_PER_THREAD,
                    successfulRequests, failedRequests, null, finalLatch, TOTAL_EVENTS,
                    true, triggerAdditionalLatch));
        }

        // Wait for any one first-phase task to finish.
        try {
            triggerAdditionalLatch.await();
            System.out.println("One of the initial tasks completed. Submitting additional tasks...");
        } catch (InterruptedException e) {
            System.out.println("Waiting for first-phase tasks interrupted: " + e.getMessage());
        }

        // Submit second-phase tasks for the remaining events.
        for (int i = 0; i < additionalTasks; i++) {
            int requestsForTask = REQUESTS_PER_THREAD;
            if (i == additionalTasks - 1) {
                int leftover = remainEvents % REQUESTS_PER_THREAD;
                if (leftover > 0) {
                    requestsForTask = leftover;
                }
            }
            executor.submit(new SendEventThread(eventQueue, SERVER_URL, requestsForTask,
                    successfulRequests, failedRequests, null, finalLatch, TOTAL_EVENTS,
                    false, null));
        }

        // Wait for all tasks to finish.
        try {
            System.out.println("Waiting for all tasks to finish...");
            finalLatch.await();
            System.out.println("All tasks completed.");
        } catch (InterruptedException e) {
            System.out.println("Waiting for all tasks interrupted: " + e.getMessage());
        }

        // Shutdown the executor.
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("Forcing shutdown...");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.out.println("Thread pool termination interrupted: " + e.getMessage());
        }

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

        System.out.println("Client execution completed. Exiting...");
        Thread.sleep(1000);
        System.exit(0);
    }
}



