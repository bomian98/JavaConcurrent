package wangsc.juc.tool;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CountDownLatchDemo {

    static CountDownLatch startLatch = new CountDownLatch(1);
    static CountDownLatch stopLatch = new CountDownLatch(5);

    public static void main(String[] args) throws InterruptedException {
        for (int i = 0; i < 5; i++){
            new Thread(new ThreadDemo(startLatch, stopLatch)).start();
        }
        TimeUnit.SECONDS.sleep(1);
        log.info("预备，开始");
        startLatch.countDown();
        log.info("选手已经动起来了");
        stopLatch.await();
        log.info("选手们都已经结束啦"+System.currentTimeMillis());
        // 由于log打印可能出现不公平的情况，因此加上锁会明显些
    }
}

@Slf4j
class ThreadDemo implements Runnable {

    CountDownLatch startLatch, endLatch;
    public ThreadDemo(CountDownLatch startLatch, CountDownLatch endLatch) {
        this.startLatch = startLatch;
        this.endLatch = endLatch;
    }

    @SneakyThrows
    @Override
    public void run() {
        log.info("等待开始");
        startLatch.await();
        log.info("开始运行啦");
        TimeUnit.SECONDS.sleep(1);
        endLatch.countDown();
        log.info("运行完啦"+System.currentTimeMillis());
    }
}