//package client;

//httpurl client版本 每次请求都 重新建立连接，导致 连接开销大
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import pojo.LiftRideEvent;
//
//import java.io.OutputStream;
//import java.net.HttpURLConnection;
//import java.net.URL;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.concurrent.CountDownLatch;
//
//public class SendEventThread implements Runnable {
//    private final BlockingQueue<LiftRideEvent> queue;
//    private final String serverUrl;
//    private final int requestsToSend;
//    private final AtomicInteger successfulRequests;
//    private final AtomicInteger failedRequests;
//    private final CountDownLatch latch;
//
//    public SendEventThread(BlockingQueue<LiftRideEvent> queue, String serverUrl, int sendAmount,
//                           AtomicInteger successfulRequests, AtomicInteger failedRequests, CountDownLatch latch) {
//        this.queue = queue;
//        this.serverUrl = serverUrl;
//        this.requestsToSend = sendAmount;
//        this.successfulRequests = successfulRequests;
//        this.failedRequests = failedRequests;
//        this.latch = latch;
//    }
//
//    @Override
//    public void run() {
//        BlockingQueue<LiftRideEvent> failedQueue = new LinkedBlockingQueue<>();
//        for (int i = 0; i < requestsToSend; i++) {
//            try {
//                LiftRideEvent event = queue.poll(10, TimeUnit.SECONDS);
//                if (event == null) continue;
//
//                boolean result = sendPostRequest(event);
//                if (result) {
//                    successfulRequests.incrementAndGet();
//                } else {
//                    failedRequests.incrementAndGet();
//                    failedQueue.offer(event);
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
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
//
//        if (latch != null) {
//            latch.countDown();
//        }
//    }
//
//    private boolean sendPostRequest(LiftRideEvent event) {
//        int retryTimes = 0;
//        long backoff = 500;
//
//        while (retryTimes < 5) {
//            try {
//                URL url = new URL(serverUrl + "/skiers/" + event.getResortID() + "/seasons/" + event.getSeasonID() + "/days/" + event.getDayID() + "/skiers/" + event.getSkierID());
//                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//                conn.setRequestMethod("POST");
//                conn.setRequestProperty("Content-Type", "application/json");
//                conn.setDoOutput(true);
//
//                ObjectMapper objectMapper = new ObjectMapper();
//                Map<String, Object> jsonMap = new HashMap<>();
//                jsonMap.put("time", event.getTime());
//                jsonMap.put("liftID", event.getLiftID());
//                byte[] jsonBytes = objectMapper.writeValueAsBytes(jsonMap);
//
//                try (OutputStream os = conn.getOutputStream()) {
//                    os.write(jsonBytes);
//                    os.flush();
//                }
//
//                int responseCode = conn.getResponseCode();
//                if (responseCode == 201) {
//                    return true;
//                } else {
//                    retryTimes++;
//                    Thread.sleep(backoff);
//                    backoff *= 2;
//                }
//            } catch (Exception e) {
//                retryTimes++;
//                try {
//                    Thread.sleep(backoff);
//                    backoff *= 2;
//                } catch (InterruptedException ex) {
//                    ex.printStackTrace();
//                }
//            }
//        }
//        return false;
//    }
//}



package client.clientPART2;

//HttpClient版本避免重复创建，  改用 sendAsync() 让请求 异步执行

import com.fasterxml.jackson.databind.ObjectMapper;
import pojo.LiftRideEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SendEventThread2 implements Runnable {
    private final BlockingQueue<LiftRideEvent> queue;
    private final String serverUrl;
    private final int requestsToSend;
    private final AtomicInteger successfulRequests;
    private final AtomicInteger failedRequests;
    private final CountDownLatch latch;
    private final List<Long> latencies;
    private final static int HTTPCLIENT_TIMEOUT = 10;
    private final static int HTTPCLIENT_THREADS_SIZE = 100;


    // **创建一个全局 `HttpClient`**
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)   // 使用 HTTP/1.1
            .connectTimeout(Duration.ofSeconds(HTTPCLIENT_TIMEOUT)) // 连接超时 10s
            .executor(Executors.newFixedThreadPool(HTTPCLIENT_THREADS_SIZE)) // 使用 50 线程的线程池
            .build();

    public SendEventThread2(BlockingQueue<LiftRideEvent> queue, String serverUrl, int sendAmount,
                           AtomicInteger successfulRequests, AtomicInteger failedRequests
            , CountDownLatch latch, List<Long> latencies) {
        this.queue = queue;
        this.serverUrl = serverUrl;
        this.requestsToSend = sendAmount;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.latch = latch;
        this.latencies = latencies;
    }

    @Override
    public void run() {
        BlockingQueue<LiftRideEvent> failedQueue = new LinkedBlockingQueue<>();
        for (int i = 0; i < requestsToSend; i++) {
            try {
                LiftRideEvent event = queue.poll(10, TimeUnit.SECONDS);
                if (event == null) continue;

                // track each thread send post request time cost
                long startTime = System.nanoTime();
                boolean result = sendPostRequest(event);
                long endTime = System.nanoTime();
                long latency = (endTime - startTime) / 1_000_000; // Convert to milliseconds

                synchronized (latencies) {
                    latencies.add(latency);
                }

                if (result) {
                    successfulRequests.incrementAndGet();
                } else {
                    failedRequests.incrementAndGet();
                    failedQueue.offer(event);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // 处理失败请求
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

        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * **使用 `HttpClient` 发送 POST 请求 避免重复创建连接**
     */
    private boolean sendPostRequest(LiftRideEvent event) {
        int retryTimes = 0;
        long backoff = 250;  // 初始退避时间 500ms
        ObjectMapper objectMapper = new ObjectMapper();

        while (retryTimes < 5) { // 最多重试 5 次
            try {
                String jsonBody = objectMapper.writeValueAsString(Map.of(
                        "time", event.getTime(),
                        "liftID", event.getLiftID()
                ));

                //  **使用 `HttpRequest` 构造 JSON 请求**
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + "/skiers/" + event.getResortID()
                                + "/seasons/" + event.getSeasonID()
                                + "/days/" + event.getDayID()
                                + "/skiers/" + event.getSkierID()))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))  // 请求超时 5s
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                //  **发送异步请求**
                CompletableFuture<HttpResponse<String>> responseFuture =
                        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

                //  **获取异步结果（带超时）**
                HttpResponse<String> response = responseFuture.get(5, TimeUnit.SECONDS);

                //  **检查 HTTP 状态码**
                if (response.statusCode() == 201) {
                    return true;
                } else {
                    System.out.println("Retrying... Attempt " + (retryTimes + 1) + " Response Code: " + response.statusCode());
                    retryTimes++;
                    Thread.sleep(backoff);
                    backoff *= 2; // 指数退避
                }
            } catch (Exception e) {
                System.out.println("Exception during request: " + e.getMessage());
                retryTimes++;
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
        return false;
    }
}
