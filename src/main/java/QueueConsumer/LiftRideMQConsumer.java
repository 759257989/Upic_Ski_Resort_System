package QueueConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dto.ConsumerLiftRideDto;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class LiftRideMQConsumer {
    private static final String QUEUE_NAME = "skiQueue";
    private static final String DLX_NAME = "skiQueue.dlx";
    private static final String DLQ_NAME = "skiQueue.DLQ";
//    private static final String RMQ_HOST_ADDRESS = "localhost";
    private static final String RMQ_HOST_ADDRESS = "35.91.221.214";
    private static final int THREAD_COUNT = 10;
    private static final String MQ_USERNAME = "admin";
    private static final String MQ_PASSWORD = "admin";

    // 线程安全的 ConcurrentHashMap 存储 SkierID -> LiftRide 记录
    private static final ConcurrentHashMap<Integer, List<ConsumerLiftRideDto>> skierRidesHashMap = new ConcurrentHashMap<>();
    public static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        // 确保队列存在
        setupRabbitMQQueues();

        // 创建线程池
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(new WorkerThread(RMQ_HOST_ADDRESS, QUEUE_NAME, skierRidesHashMap, objectMapper));
        }

        // 优雅关闭线程池**
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down consumer...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
            System.out.println("Consumer shutdown complete.");
        }));
    }

    private static void setupRabbitMQQueues() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RMQ_HOST_ADDRESS);
            factory.setUsername(MQ_USERNAME);  // 默认用户名
            factory.setPassword(MQ_PASSWORD);  // 默认密码
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // 声明死信交换机（DLX）
            channel.exchangeDeclare(DLX_NAME, "fanout", true);

            // 声明死信队列（DLQ）
            channel.queueDeclare(DLQ_NAME, true, false, false, null);
            channel.queueBind(DLQ_NAME, DLX_NAME, "");

            // 声明正常队列，并绑定 DLX
            channel.queueDeclare(QUEUE_NAME, true, false, false, Map.of(
                    "x-dead-letter-exchange", DLX_NAME
            ));

            channel.close();
            connection.close();
            System.out.println("RabbitMQ Queues setup complete.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to set up RabbitMQ Queues.");
        }
    }
}
