package QueueConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import dto.ConsumerLiftRideDto;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

public class WorkerThread implements Runnable{
    private final String rmqHostAddress;
    private final String queueName;
    private final ConcurrentHashMap<Integer, List<ConsumerLiftRideDto>> skierRidesHashMap;
    private final ObjectMapper objectMapper;
    private static final Integer WorkCONSUMEAMOUNT = 1;
    private static final String RABBITMQ_USERNAME = "admin";
    private static final String RABBITMQ_PASSWORD = "admin";

    public WorkerThread(String rmqHostAddress, String queueName, ConcurrentHashMap<Integer, List<ConsumerLiftRideDto>> skierRidesHashMap, ObjectMapper objectMapper) {
        this.rmqHostAddress = rmqHostAddress;
        this.queueName = queueName;
        this.objectMapper = objectMapper;
        this.skierRidesHashMap = skierRidesHashMap;
    }
    /**
     * Runs this operation.
     */
    @Override
    public void run() {
        try{
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(rmqHostAddress);
            factory.setUsername(RABBITMQ_USERNAME);
            factory.setPassword(RABBITMQ_PASSWORD);
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            // 声明死信交换机（DLX）
            String DLX_NAME = "skiQueue.dlx";
            //检查是否存在 不会重复创建
            channel.exchangeDeclare(DLX_NAME, "fanout", true);

            // 声明死信队列（DLQ）
            String DLQ_NAME = "skiQueue.DLQ";
            channel.queueDeclare(DLQ_NAME, true, false, false, null);
            channel.queueBind(DLQ_NAME, DLX_NAME, "");

            //RabbitMQ 中声明一个队列，如果队列已经存在，则使用已有的队列
            // queuename, 持久化，是否排他（可供多个连接共享），是否自动删除， 其他参数
            channel.queueDeclare(queueName, true, false, false, Map.of(
                    "x-dead-letter-exchange", DLX_NAME
            ));
            //每个消费者一次只接收 1 条 消息，处理完毕后才会再接收下一条
            channel.basicQos(WorkCONSUMEAMOUNT);

            //Consumer异步监听队列消息，并手动确认消息已处理

            //DeliverCallback 处理从 RabbitMQ 队列接收到的消息
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("Received '" + message + "'");
                try {
                    processMessage(message, channel, delivery.getEnvelope().getDeliveryTag());
                    //ack 确认
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                } catch (Exception e) {
                    // 处理失败，将消息发送到死信队列
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);

                }
            };
            // 监听队列， 手动确认（ACK）让消费者一直监听队列，当有新消息到达时，RabbitMQ 就会调用 deliverCallback 处理消息。
            channel.basicConsume(this.queueName, false, deliverCallback, consumerTag -> { });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //存入线程安全的 HashMap
    private void processMessage(String message, Channel channel, long deliveryTag) {
        try {
            // 解析 JSON
            ConsumerLiftRideDto consumerLiftRideDto = objectMapper.readValue(message, ConsumerLiftRideDto.class);
            // 存入线程安全的 HashMap
            skierRidesHashMap.computeIfAbsent(consumerLiftRideDto.getSkierID(), k->new CopyOnWriteArrayList<>())
                    .add(consumerLiftRideDto);
            // 打印 HashMap 内容
            System.out.println("当前 HashMap 数据: ");
            skierRidesHashMap.forEach((skierID, rides) -> {
                System.out.println("SkierID: " + skierID + ", Rides: " + rides);
            });
        } catch (Exception e) {
            e.printStackTrace();
            try {
                System.err.println("JSON parse failed，send message to DeadLeet Queue");
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
