package wangsc.thread;

import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "multi.thread")
public class MonitorThread {
    public static void main(String[] args) {
        new Thread(()->{
           while(true){
               log.debug("11");
           }
        }).start();
        new Thread(()->{
            while(true){
                log.debug("22");
            }
        }).start();
    }

    /**
     * jps 可以查看所有Java进程
     * jstack <PID> 查看某个 Java 进程（PID）的所有线程状态
     * jconsole 来查看某个 Java 进程中线程的运行情况（图形界面），如堆、类等信息
     */
}
