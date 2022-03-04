package wangsc.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

public class ThreadStartAndRun {


}

/**
 * start 是新开一个线程执行，main 线程和 thread 线程并不影响
 * 20:22:30.533 [main] INFO start - main 线程结束
 * 20:22:40.546 [Thread-0] INFO start - 线程睡眠结束
 */
@Slf4j(topic="start")
class ThreadStart{
    public static void main(String[] args) {
        new Thread(()->{
            try {
                TimeUnit.SECONDS.sleep(10);
                log.info("线程睡眠结束");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        log.info("main 线程结束");
    }
}


/**
 * run 是将当前线程的内容加入到主线程中执行的
 * 这里，先睡眠了10秒种，然后thread结束睡眠，main结束
 * 20:21:18.608 [main] INFO run - 线程睡眠结束
 * 20:21:18.611 [main] INFO run - main 线程结束
 */
@Slf4j(topic="run")
class ThreadRun{

    public static void main(String[] args) {
        new Thread(()->{
            try {
                TimeUnit.SECONDS.sleep(10);
                log.info("线程睡眠结束");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).run();

        log.info("main 线程结束");
    }
}