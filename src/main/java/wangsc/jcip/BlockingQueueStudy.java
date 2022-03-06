package wangsc.jcip;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


public class BlockingQueueStudy {
}

/**
 * 一对一的生产者和消费者。
 * 一对一的生产者和消费者，用不用阻塞队列应该意义不大？
 */
@Slf4j
class ProducerAndConsumer1{

    private final ArrayBlockingQueue<Integer> arr_queue = new ArrayBlockingQueue<Integer>(100);

    public static void main(String[] args) {
        ProducerAndConsumer1 pc = new ProducerAndConsumer1();
        pc.start();
    }

    public void start(){
        ProducerThread producer = new ProducerThread();
        ConsumerThread consumer = new ConsumerThread();
        consumer.start();
        producer.start();
    }

    // 生产者每次生成随机数字
    class ProducerThread extends Thread {
        private final Random r = new Random();

        @Override
        public void run() {
            try {
                while (true) {
                    int value = r.nextInt(100);
                    arr_queue.put(value); // put操作：队列满了的话，会阻塞。offer操作：队列满了的话，会返回失败状态。
                    log.info("生产者：放入数据"+value);
                    TimeUnit.MILLISECONDS.sleep(500);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // 消费者读取数字并进行翻倍
    class ConsumerThread extends Thread {

        @Override
        public void run() {
            try {
                while (true) {
                    int value = arr_queue.take() * 2;
                    log.info("消费者：数据处理后为:"+value);
                    TimeUnit.MILLISECONDS.sleep(800);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


/**
 * n对n的生产者和消费者(数据由任意一个消费者消费都可以时)
 *
 * 如果采用多个队列，则通过 System.currentTimeMillis()%thread_cnt 来设置分配给哪个消费者队列中。
 * 不过这样好像也不需要阻塞队列。
 * 多个生产者的话，也可以增加偏移量，如生产者的索引，避免同一时间多个生产者对同一个队列增加数据的情况，减少征用锁的等待时间。
 *
 * 当只有一个队列，那么必须使用阻塞队列，因为同一时间多个线程读或写应该会出现问题。
 */
@Slf4j
class ProducerAndConsumer2{
    private final ArrayBlockingQueue<Integer> queue[];
    private final ProducerThread pt;
    private final ConsumerThread ct[];
    private final int thread_cnt;

    ProducerAndConsumer2(int thread_cnt){
        this.thread_cnt = thread_cnt;
        queue = new ArrayBlockingQueue[thread_cnt];
        pt = new ProducerThread();
        ct = new ConsumerThread[thread_cnt];
        for(int i = 0; i < thread_cnt; i++){
            queue[i] = new ArrayBlockingQueue<Integer>(10);
            ct[i] = new ConsumerThread(i);
        }
    }

    public static void main(String[] args) {
        ProducerAndConsumer2 pc = new ProducerAndConsumer2(10);
        pc.start();
    }

    public void start(){
        pt.start();
        for(int i = 0; i < thread_cnt; i++){
            ct[i].start();
        }
    }

    class ConsumerThread extends Thread {
        private final int idx; // 对应队列的第i项

        ConsumerThread(int idx) {
            this.idx = idx;
        }

        @SneakyThrows
        @Override
        public void run() {
            while(true){
                int v = queue[idx].take();
                log.info("消费者"+idx+": 取到数据"+v);
                TimeUnit.MILLISECONDS.sleep(1000);
            }
        }
    }

    class ProducerThread extends Thread{
        private final Random r = new Random();
        @SneakyThrows
        @Override
        public void run() {
            while(true){
                int v = r.nextInt(200);
                int idx = (int) (System.currentTimeMillis()%thread_cnt);
                queue[idx].put(v);
                log.info("生产者: 数据放入"+idx+"队列中");
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
    }
}