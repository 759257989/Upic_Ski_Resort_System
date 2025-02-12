////package client;
//
////httpurl client版本 每次请求都 重新建立连接，导致 连接开销大
////
////import com.fasterxml.jackson.databind.ObjectMapper;
////import pojo.LiftRideEvent;
////
////import java.io.OutputStream;
////import java.net.HttpURLConnection;
////import java.net.URL;
////import java.util.HashMap;
////import java.util.Map;
////import java.util.concurrent.BlockingQueue;
////import java.util.concurrent.LinkedBlockingQueue;
////import java.util.concurrent.TimeUnit;
////import java.util.concurrent.atomic.AtomicInteger;
////import java.util.concurrent.CountDownLatch;
////
////public class SendEventThread implements Runnable {
////    private final BlockingQueue<LiftRideEvent> queue;
////    private final String serverUrl;
////    private final int requestsToSend;
////    private final AtomicInteger successfulRequests;
////    private final AtomicInteger failedRequests;
////    private final CountDownLatch latch;
////
////    public SendEventThread(BlockingQueue<LiftRideEvent> queue, String serverUrl, int sendAmount,
////                           AtomicInteger successfulRequests, AtomicInteger failedRequests, CountDownLatch latch) {
////        this.queue = queue;
////        this.serverUrl = serverUrl;
////        this.requestsToSend = sendAmount;
////        this.successfulRequests = successfulRequests;
////        this.failedRequests = failedRequests;
////        this.latch = latch;
////    }
////
////    @Override
////    public void run() {
////        BlockingQueue<LiftRideEvent> failedQueue = new LinkedBlockingQueue<>();
////        for (int i = 0; i < requestsToSend; i++) {
////            try {
////                LiftRideEvent event = queue.poll(10, TimeUnit.SECONDS);
////                if (event == null) continue;
////
////                boolean result = sendPostRequest(event);
////                if (result) {
////                    successfulRequests.incrementAndGet();
////                } else {
////                    failedRequests.incrementAndGet();
////                    failedQueue.offer(event);
////                }
////            } catch (InterruptedException e) {
////                e.printStackTrace();
////            }
////        }
////
////        while (!failedQueue.isEmpty()) {
////            LiftRideEvent failedEvent = failedQueue.poll();
////            if (failedEvent != null) {
////                boolean result = sendPostRequest(failedEvent);
////                if (result) {
////                    successfulRequests.incrementAndGet();
////                    failedRequests.decrementAndGet();
////                }
////            }
////        }
////
////        if (latch != null) {
////            latch.countDown();
////        }
////    }
////
////    private boolean sendPostRequest(LiftRideEvent event) {
////        int retryTimes = 0;
////        long backoff = 500;
////
////        while (retryTimes < 5) {
////            try {
////                URL url = new URL(serverUrl + "/skiers/" + event.getResortID() + "/seasons/" + event.getSeasonID() + "/days/" + event.getDayID() + "/skiers/" + event.getSkierID());
////                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
////                conn.setRequestMethod("POST");
////                conn.setRequestProperty("Content-Type", "application/json");
////                conn.setDoOutput(true);
////
////                ObjectMapper objectMapper = new ObjectMapper();
////                Map<String, Object> jsonMap = new HashMap<>();
////                jsonMap.put("time", event.getTime());
////                jsonMap.put("liftID", event.getLiftID());
////                byte[] jsonBytes = objectMapper.writeValueAsBytes(jsonMap);
////
////                try (OutputStream os = conn.getOutputStream()) {
////                    os.write(jsonBytes);
////                    os.flush();
////                }
////
////                int responseCode = conn.getResponseCode();
////                if (responseCode == 201) {
////                    return true;
////                } else {
////                    retryTimes++;
////                    Thread.sleep(backoff);
////                    backoff *= 2;
////                }
////            } catch (Exception e) {
////                retryTimes++;
////                try {
////                    Thread.sleep(backoff);
////                    backoff *= 2;
////                } catch (InterruptedException ex) {
////                    ex.printStackTrace();
////                }
////            }
////        }
////        return false;
////    }
////}
//
//
//package client.clientPART1;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import pojo.LiftRideEvent;
//
//import javax.servlet.http.HttpServletResponse;
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.time.Duration;
//import java.util.Map;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class SendEventThread implements Runnable {
//    private final BlockingQueue<LiftRideEvent> queue;
//    private final String serverUrl;
//    private final int requestsToSend;
//    private final AtomicInteger successfulRequests;
//    private final AtomicInteger failedRequests;
//    private final CountDownLatch latch;
//    private final CountDownLatch finalLatch;
//    private final int totalEvents;
//
//    private static final int HTTPCLIENT_TIMEOUT = 10;
//    private static final int HTTPCLIENT_THREADS_SIZE = 100;
//    private static final int QUEUE_POLL_TIMEOUT = 2; // 单位：秒
//
//    // 使用全局 HttpClient 复用连接
//    private static final HttpClient httpClient = HttpClient.newBuilder()
//            .version(HttpClient.Version.HTTP_1_1)
//            .connectTimeout(Duration.ofSeconds(HTTPCLIENT_TIMEOUT))
//            .executor(Executors.newFixedThreadPool(HTTPCLIENT_THREADS_SIZE))
//            .build();
//
//    public SendEventThread(BlockingQueue<LiftRideEvent> queue, String serverUrl, int sendAmount,
//                           AtomicInteger successfulRequests, AtomicInteger failedRequests,
//                           CountDownLatch latch, CountDownLatch finalLatch, int totalEvents) {
//        this.queue = queue;
//        this.serverUrl = serverUrl;
//        this.requestsToSend = sendAmount;
//        this.successfulRequests = successfulRequests;
//        this.failedRequests = failedRequests;
//        this.latch = latch;
//        this.finalLatch = finalLatch;
//        this.totalEvents = totalEvents;
//    }
//
//    @Override
//    public void run() {
//        System.out.println(Thread.currentThread().getName() + " - Started sending requests...");
//        BlockingQueue<LiftRideEvent> failedQueue = new LinkedBlockingQueue<>();
//        int processedRequests = 0; // 记录已处理请求数量
//
//        while (processedRequests < requestsToSend) { // 达到分配请求数后退出
//            try {
//                System.out.println(Thread.currentThread().getName() + " - Checking queue...");
//                LiftRideEvent event = queue.poll(QUEUE_POLL_TIMEOUT, TimeUnit.SECONDS);
//                if (event == null) {
//                    System.out.println(Thread.currentThread().getName() + " - Queue empty, waiting...");
//                    Thread.sleep(500); // 队列为空时稍作等待
//                    continue;
//                }
//
//                boolean result = sendPostRequest(event);
//                if (result) {
//                    successfulRequests.incrementAndGet();
//                } else {
//                    failedRequests.incrementAndGet();
//                    failedQueue.offer(event);
//                }
//
//                processedRequests++;
//                if (processedRequests % 100 == 0) {
//                    System.out.println(Thread.currentThread().getName() + " - Sent " + processedRequests + " requests...");
//                }
//            } catch (InterruptedException e) {
//                System.err.println(Thread.currentThread().getName() + " - Interrupted: " + e.getMessage());
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        // 同步重试失败的请求
//        retryFailedRequests(failedQueue);
//
//        // 标记当前任务完成
//        if (latch != null) {
//            latch.countDown();
//        }
//        if (finalLatch != null) {
//            synchronized (finalLatch) {
//                if (finalLatch.getCount() > 0) {
//                    finalLatch.countDown();
//                }
//            }
//        }
//
//        System.out.println(Thread.currentThread().getName() + " - Completed. Success: " +
//                successfulRequests.get() + ", Failed: " + failedRequests.get());
//    }
//
//    /**
//     * 使用 HttpClient 发送 POST 请求
//     */
//    private boolean sendPostRequest(LiftRideEvent event) {
//        int retryTimes = 0;
//        long backoff = 100; // 初始退避时间（毫秒）
//        ObjectMapper objectMapper = new ObjectMapper();
//
//        while (retryTimes < 5) { // 最多重试 5 次
//            try {
//                String jsonBody = objectMapper.writeValueAsString(Map.of(
//                        "time", event.getTime(),
//                        "liftID", event.getLiftID()
//                ));
//
//                HttpRequest request = HttpRequest.newBuilder()
//                        .uri(URI.create(serverUrl + "/skiers/" + event.getResortID()
//                                + "/seasons/" + event.getSeasonID()
//                                + "/days/" + event.getDayID()
//                                + "/skiers/" + event.getSkierID()))
//                        .header("Content-Type", "application/json")
//                        .timeout(Duration.ofSeconds(5))
//                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
//                        .build();
//
//                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//
//                if (response.statusCode() == HttpServletResponse.SC_CREATED) {
//                    System.out.println(Thread.currentThread().getName() + " - Request successful!");
//                    return true;
//                } else {
//                    System.out.println(Thread.currentThread().getName() + " - Retry " + (retryTimes + 1) +
//                            ", Response Code: " + response.statusCode());
//                    retryTimes++;
//                    Thread.sleep(backoff);
//                    backoff *= 2;
//                }
//            } catch (Exception e) {
//                System.err.println(Thread.currentThread().getName() + " - Exception during request: " + e.getMessage());
//                retryTimes++;
//                try {
//                    Thread.sleep(backoff);
//                    backoff *= 2;
//                } catch (InterruptedException ex) {
//                    System.err.println(Thread.currentThread().getName() +
//                            " - Interrupted during retry wait: " + ex.getMessage());
//                    Thread.currentThread().interrupt();
//                }
//            }
//        }
//        System.out.println(Thread.currentThread().getName() + " - Request failed after 5 retries.");
//        return false;
//    }
//
//    /**
//     * 同步重试失败的请求
//     */
//    private void retryFailedRequests(BlockingQueue<LiftRideEvent> failedQueue) {
//        while (!failedQueue.isEmpty()) {
//            LiftRideEvent failedEvent = failedQueue.poll();
//            if (failedEvent != null) {
//                boolean result = sendPostRequest(failedEvent);
//                if (result) {
//                    successfulRequests.incrementAndGet();
//                    failedRequests.decrementAndGet();
//                }
//            }
//        }
//    }
//}
//


package client.clientPART1;

import com.fasterxml.jackson.databind.ObjectMapper;
import pojo.LiftRideEvent;

import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SendEventThread implements Runnable {
    private final BlockingQueue<LiftRideEvent> queue;
    private final String serverUrl;
    private final int requestsToSend;
    private final AtomicInteger successfulRequests;
    private final AtomicInteger failedRequests;
    private final CountDownLatch latch;
    private final CountDownLatch finalLatch;
    private final int totalEvents;

    private static final int HTTPCLIENT_TIMEOUT = 10;
    private static final int HTTPCLIENT_THREADS_SIZE = 100;
    private static final int QUEUE_POLL_TIMEOUT = 2; // seconds

    // Global HttpClient for connection reuse.
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(HTTPCLIENT_TIMEOUT))
            .executor(Executors.newFixedThreadPool(HTTPCLIENT_THREADS_SIZE))
            .build();

    public SendEventThread(BlockingQueue<LiftRideEvent> queue, String serverUrl, int sendAmount,
                            AtomicInteger successfulRequests, AtomicInteger failedRequests,
                            CountDownLatch latch, CountDownLatch finalLatch, int totalEvents) {
        this.queue = queue;
        this.serverUrl = serverUrl;
        this.requestsToSend = sendAmount;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.latch = latch;
        this.finalLatch = finalLatch;
        this.totalEvents = totalEvents;
    }

    @Override
    public void run() {
        //System.out.println(Thread.currentThread().getName() + " - Started sending requests...");
        BlockingQueue<LiftRideEvent> failedQueue = new LinkedBlockingQueue<>();
        int processedRequests = 0; // Number of processed requests

        while (processedRequests < requestsToSend) {
            try {
                // every event is processed once
                //System.out.println(Thread.currentThread().getName() + " - Checking queue...");
                LiftRideEvent event = queue.poll(QUEUE_POLL_TIMEOUT, TimeUnit.SECONDS);
                if (event == null) {
                    System.out.println(Thread.currentThread().getName() + " - Queue empty, waiting...");
                    Thread.sleep(500);
                    continue;
                }

                boolean result = sendPostRequest(event);
                if (result) {
                    successfulRequests.incrementAndGet();
                } else {
                    failedRequests.incrementAndGet();
                    System.out.println("Failed: " + failedRequests.get());
                    //failedQueue.offer(event);
                }
                processedRequests++;
                if (processedRequests % 100 == 0) {
                    //System.out.println(Thread.currentThread().getName() + " - Sent " + processedRequests + " requests...");
                }
            } catch (InterruptedException e) {
                System.err.println(Thread.currentThread().getName() + " - Interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        // Synchronously retry any failed requests.
        // after all events processed, retry the failed requests during while loop
//        retryFailedRequests(failedQueue);

        // Mark task as completed.
        if (latch != null) {
            latch.countDown();
        }
        if (finalLatch != null) {
            finalLatch.countDown();
        }

        //System.out.println(Thread.currentThread().getName() + " - Completed. Success: " + successfulRequests.get() + ", Failed: " + failedRequests.get());
    }

    /**
     * Sends a POST request using HttpClient and records latency.
     */
    private boolean sendPostRequest(LiftRideEvent event) {
        int retryTimes = 0;
        long backoff = 10; // initial backoff in ms 100
        ObjectMapper objectMapper = new ObjectMapper();

        while (retryTimes < 5) {
            try {
                String jsonBody = objectMapper.writeValueAsString(Map.of(
                        "time", event.getTime(),
                        "liftID", event.getLiftID()
                ));

                // Take a timestamp before sending the request.
                long requestStartNano = System.nanoTime();
                long requestStartMillis = System.currentTimeMillis();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/skiers/" + event.getResortID()
                                + "/seasons/" + event.getSeasonID()
                                + "/days/" + event.getDayID()
                                + "/skiers/" + event.getSkierID()))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == HttpServletResponse.SC_CREATED) {
                    //System.out.println(Thread.currentThread().getName() + " - Request successful!");
                    return true;
                } else {
                    System.out.println(Thread.currentThread().getName() + " - Retry " + (retryTimes + 1) +
                            ", Response Code: " + response.statusCode());
                    retryTimes++;
                    Thread.sleep(backoff);
                    backoff *= 2;
                }
            } catch (Exception e) {
                System.err.println(Thread.currentThread().getName() + " - Exception during request: " + e.getMessage());
                retryTimes++;
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException ex) {
                    System.err.println(Thread.currentThread().getName() +
                            " - Interrupted during retry wait: " + ex.getMessage());
                    Thread.currentThread().interrupt();
                }
            }
        }
        System.out.println(Thread.currentThread().getName() + " - Request failed after 5 retries.");
        return false;
    }

    /**
     * Synchronously retries failed requests.
     */
    private void retryFailedRequests(BlockingQueue<LiftRideEvent> failedQueue) {
        while (!failedQueue.isEmpty()) {
            LiftRideEvent failedEvent = failedQueue.poll();
            if (failedEvent != null) {
                boolean result = sendPostRequest(failedEvent);
                if (result) {
                    successfulRequests.incrementAndGet();
                    failedRequests.decrementAndGet();
                }
            }
        }
    }
}

