package wangsc.thread;


import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 守护线程
 *
 * 默认情况下，Java 进程需要等待所有线程都运行结束，才会结束。
 * 但当所有非守护线程都运行结束时，守护线程(即使没有运行完)会被强制结束。
 * t.setDaemon(true);
 * t.isDaemon();
 *
 * 20:39:28.528 [Thread-0] INFO wangsc.thread.ThreadDaemon - 执行开始
 * 20:39:28.528 [main] INFO wangsc.thread.ThreadDaemon - main 执行完啦
 *
 * 注意：main 所在的线程不能设置为 守护线程
 */
@Slf4j
public class ThreadDaemon {

    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(()->{
            log.info("执行开始");
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("执行完啦");
        });
        t.setDaemon(true);
        t.start();
        log.info("main 执行完啦");
    }
}
