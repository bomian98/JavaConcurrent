package wangsc.thread;

public class ThreadAPI {
    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(()->{
            while(true){

            }
        });
        // ThreadStartAndRun
        t.start();
        t.run();
        // ThreadJoin
        t.join();
        t.join(10);
        // ThreadState
        t.getState();
        // 1-10的优先级，优先级越高越容易进行CPU调度
        t.setPriority(Thread.NORM_PRIORITY);
        t.getPriority();
        // ThreadInterupt
        t.isInterrupted();
        t.interrupt();
        // ThreadDaemon
        t.setDaemon(false);
        t.isDaemon();
        // Other
        t.getId();
        t.getName();
        t.setName("thread-0");
        t.isAlive();
        t.currentThread();
        //
        t.sleep(10);
        t.yield();
    }
}
