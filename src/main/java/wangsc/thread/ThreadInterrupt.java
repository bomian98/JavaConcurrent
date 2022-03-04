package wangsc.thread;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * private native boolean isInterrupted(boolean ClearInterrupted);
 * <p>
 * current.isInterrupted() 判断是否被打断
 * 源码参数传递中使用 false，表示不清除打断标记
 * <p>
 * Thread.interrupted() 同样会判断是否被打断，但是判断后会清除打断标记
 * 源码参数传递中使用true，表示清除标记
 */

/**
 * sleep、wait、join会让线程进入阻塞状态
 * 打断sleep线程，会清空打断状态，同时触发打断异常
 * 打断正常运行的程序，不会清空打断状态，但线程并不会结束运行
 * 打断 park 线程, 不会清空打断状态
 */
public class ThreadInterrupt {


}





// 一个线程如何优雅的终止另一个线程

/**
 * 使用 isInterrupted 函数判断是否被打断
 * 非 sleep 阶段，即 running 阶段通过 isInterrupted 是否被打断
 * sleep 阶段，不再监控。此时，若有打断会触发 InterruptedException 异常
 * 通过捕捉异常，然后将当前线程打断，则就可以在监控期间去处理这个问题
 */
@Slf4j(topic = "TwoInterrupt")
class IsInterrupted {
    private static Thread thread;

    public static void main(String[] args) throws InterruptedException {
        IsInterrupted t = new IsInterrupted();
        t.start();
        TimeUnit.SECONDS.sleep(2);
        log.info("stop");
        t.stop();
    }

    public void start() {
        thread = new Thread(() -> {
            while (true) {
                Thread current = Thread.currentThread();
                if (current.isInterrupted()) {
                    log.info("执行善后的事情");
                    break;
                } else {
                    log.info("执行something");
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        current.interrupt();
                        log.info("sleep 中被打断");
                    }
                }
            }
        });
        thread.start();
    }

    public void stop() {
        thread.interrupt();
    }
}


/**
 * 使用 volatile 使得打断标志变量 interrupted 在线程之间可见性
 */
@Slf4j()
class InterruptVolatile {
    private Thread thread;
    private volatile boolean interrupted = false;

    public static void main(String[] args) throws InterruptedException {
        InterruptVolatile t = new InterruptVolatile();
        t.start();
        TimeUnit.SECONDS.sleep(1);
        t.stop();
    }

    public void start() {
        thread = new Thread(() -> {
            while (true) {
                if (interrupted) {
                    log.info("执行善后的事情");
                    break;
                } else {
                    log.info("do something");
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e) {

                    }
                }
            }
        });
        thread.start();
    }

    public void stop() {
        interrupted = true;
        thread.interrupt();
    }
}


