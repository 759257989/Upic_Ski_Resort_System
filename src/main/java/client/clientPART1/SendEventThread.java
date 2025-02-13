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
//    private static final int QUEUE_POLL_TIMEOUT = 2; // seconds
//
//    // Global HttpClient for connection reuse.
//    private static final HttpClient httpClient = HttpClient.newBuilder()
//            .version(HttpClient.Version.HTTP_1_1)
//            .connectTimeout(Duration.ofSeconds(HTTPCLIENT_TIMEOUT))
//            .executor(Executors.newFixedThreadPool(HTTPCLIENT_THREADS_SIZE))
//            .build();
//
//    public SendEventThread(BlockingQueue<LiftRideEvent> queue, String serverUrl, int sendAmount,
//                            AtomicInteger successfulRequests, AtomicInteger failedRequests,
//                            CountDownLatch latch, CountDownLatch finalLatch, int totalEvents) {
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
//        //System.out.println(Thread.currentThread().getName() + " - Started sending requests...");
//        BlockingQueue<LiftRideEvent> failedQueue = new LinkedBlockingQueue<>();
//        int processedRequests = 0; // Number of processed requests
//
//        while (processedRequests < requestsToSend) {
//            try {
//                // every event is processed once
//                //System.out.println(Thread.currentThread().getName() + " - Checking queue...");
//                LiftRideEvent event = queue.poll(QUEUE_POLL_TIMEOUT, TimeUnit.SECONDS);
//                if (event == null) {
//                    System.out.println(Thread.currentThread().getName() + " - Queue empty, waiting...");
//                    Thread.sleep(500);
//                    continue;
//                }
//
//                boolean result = sendPostRequest(event);
//                if (result) {
//                    successfulRequests.incrementAndGet();
//                } else {
//                    failedRequests.incrementAndGet();
//                    System.out.println("Failed: " + failedRequests.get());
//                    //failedQueue.offer(event);
//                }
//                processedRequests++;
//                if (processedRequests % 100 == 0) {
//                    //System.out.println(Thread.currentThread().getName() + " - Sent " + processedRequests + " requests...");
//                }
//            } catch (InterruptedException e) {
//                System.err.println(Thread.currentThread().getName() + " - Interrupted: " + e.getMessage());
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        // Synchronously retry any failed requests.
//        // after all events processed, retry the failed requests during while loop
////        retryFailedRequests(failedQueue);
//
//        // Mark task as completed.
//        if (latch != null) {
//            latch.countDown();
//        }
//        if (finalLatch != null) {
//            finalLatch.countDown();
//        }
//
//        //System.out.println(Thread.currentThread().getName() + " - Completed. Success: " + successfulRequests.get() + ", Failed: " + failedRequests.get());
//    }
//
//    /**
//     * Sends a POST request using HttpClient and records latency.
//     */
//    private boolean sendPostRequest(LiftRideEvent event) {
//        int retryTimes = 0;
//        long backoff = 10; // initial backoff in ms 100
//        ObjectMapper objectMapper = new ObjectMapper();
//
//        while (retryTimes < 5) {
//            try {
//                String jsonBody = objectMapper.writeValueAsString(Map.of(
//                        "time", event.getTime(),
//                        "liftID", event.getLiftID()
//                ));
//
//                // Take a timestamp before sending the request.
//                long requestStartNano = System.nanoTime();
//                long requestStartMillis = System.currentTimeMillis();
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
//                    //System.out.println(Thread.currentThread().getName() + " - Request successful!");
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
//     * Synchronously retries failed requests.
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
    // The latch (if any) for first-phase tasks is not used here; only finalLatch is used to wait for all tasks.
    private final CountDownLatch latch;
    private final CountDownLatch finalLatch;
    private final int totalEvents;

    // New parameters:
    // If triggerAdditional is true, this is a first-phase task that should trigger the additional tasks once finished.
    private final boolean triggerAdditional;
    // The latch that is counted down once by the first finished first-phase task.
    private final CountDownLatch triggerAdditionalLatch;

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
                           CountDownLatch latch, CountDownLatch finalLatch, int totalEvents,
                           boolean triggerAdditional, CountDownLatch triggerAdditionalLatch) {
        this.queue = queue;
        this.serverUrl = serverUrl;
        this.requestsToSend = sendAmount;
        this.successfulRequests = successfulRequests;
        this.failedRequests = failedRequests;
        this.latch = latch;
        this.finalLatch = finalLatch;
        this.totalEvents = totalEvents;
        this.triggerAdditional = triggerAdditional;
        this.triggerAdditionalLatch = triggerAdditionalLatch;
    }

    @Override
    public void run() {
        int processedRequests = 0;
        while (processedRequests < requestsToSend) {
            try {
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
                }
                processedRequests++;
            } catch (InterruptedException e) {
                System.err.println(Thread.currentThread().getName() + " - Interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        // If this is a first-phase task that should trigger the additional tasks, count down the latch.
        if (triggerAdditional && triggerAdditionalLatch != null) {
            triggerAdditionalLatch.countDown();
        }
        // Mark task as completed.
        if (finalLatch != null) {
            finalLatch.countDown();
        }
    }

    /**
     * Sends a POST request using HttpClient and records latency.
     */
    private boolean sendPostRequest(LiftRideEvent event) {
        int retryTimes = 0;
        long backoff = 10; // initial backoff in milliseconds
        ObjectMapper objectMapper = new ObjectMapper();

        while (retryTimes < 5) {
            try {
                String jsonBody = objectMapper.writeValueAsString(Map.of(
                        "time", event.getTime(),
                        "liftID", event.getLiftID()
                ));

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
}
