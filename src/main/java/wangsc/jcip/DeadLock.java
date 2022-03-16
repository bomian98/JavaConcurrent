package wangsc.jcip;

import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

/**
 * 尝试使用jstack进行命令分析
 */
public class DeadLock {
    static Object ob1 = new Object();
    static Object ob2 = new Object();
    static class Thread1 extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            System.out.println("Thread1 尝试获取ob1锁");
            synchronized (ob1) {
                System.out.println("Thread1 获取ob1锁成功");
                TimeUnit.SECONDS.sleep(2);
                System.out.println("Thread1 尝试获取ob2锁");
                synchronized (ob2) {
                    System.out.println("Thread2 获取ob2锁成功");
                }
            }
        }
    }
    static class Thread2 extends Thread {
        @SneakyThrows
        @Override
        public void run() {
            System.out.println("Thread2 尝试获取ob2锁");
            synchronized (ob2) {
                System.out.println("Thread2 获取ob2锁成功");
                TimeUnit.SECONDS.sleep(2);
                System.out.println("Thread2 尝试获取ob1锁");
                synchronized (ob1) {
                    System.out.println("Thread2 获取ob1锁成功");
                }
            }
        }
    }

    public static void main(String[] args) {
        Thread thread = new Thread1();
        Thread thread1 = new Thread2();
        thread.start();
        thread1.start();
    }
}
