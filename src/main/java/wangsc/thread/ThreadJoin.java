package wangsc.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * join 方法，等待某个线程执行完成，底层实现是native wait方法
 */

@Slf4j
public class ThreadJoin {
    static int r;
    void joinTest() throws InterruptedException {
        log.debug("开始");
        Thread t1 = new Thread(() -> {
            log.debug("开始");
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.debug("结束");
            r = 10;
        });
        t1.start();
        t1.join(); // 若没有join方法，输出的r值为0，因为还没有被thread t 改变
        log.debug("结果为:{}", r);
        log.debug("结束");
    }

    public static void main(String[] args) {
    }
}