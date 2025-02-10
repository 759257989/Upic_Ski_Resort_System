package client.clientPART2;

import pojo.LiftRideEvent;

import java.util.Random;
import java.util.concurrent.BlockingQueue;

// thread used to generate random events
public class EventGenerateThread2 implements Runnable{
    private final BlockingQueue<LiftRideEvent> queue;
    private final int totalEvents;
    private final Random random = new Random();

    public EventGenerateThread2(BlockingQueue<LiftRideEvent> queue, int totalEvents) {
        this.queue = queue;
        this.totalEvents = totalEvents;
    }

    @Override
    public void run() {
        int generatedCount = 0;
        // generate assigned amount of ride events
        for(int i = 0; i < totalEvents; i++) {
            LiftRideEvent event = new LiftRideEvent(
                    random.nextInt(100000) + 1,
                    random.nextInt(10) + 1,
                    random.nextInt(40) + 1,
                    "2025",
                    "1",
                    random.nextInt(360) + 1
            );
            try{
                queue.put(event);
                generatedCount++; // 事件成功放入队列后计数
                if (generatedCount % 10000 == 0) { // 每 1000 个事件打印一次
                    System.out.println("Generated events: " + generatedCount);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 生产完成后打印最终计数
        System.out.println("Event generation completed. Total events generated: " + generatedCount);
    }

}
