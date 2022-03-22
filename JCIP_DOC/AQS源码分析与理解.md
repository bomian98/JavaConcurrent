## 1 AQS 背景与原理

synchronized 加锁方法比较单一，AQS 提供了丰富的同步功能和同步器的通用机制。

那么要完成一个加锁的操作时，需要考虑(1)同步状态问题，(2)线程等待问题，等等。

AQS 核心思想是，如果被请求的共享资源空闲，那么就将当前请求资源的线程设置为有效的工作线程，将共享资源设置为锁定状态；如果共享资源被占用，就需要一定的阻塞等待唤醒机制来保证锁分配。这个机制主要用的是 CLH 队列的变体实现的，将暂时获取不到锁的线程加入到队列中。

CLH：Craig、Landin and Hagersten 队列，是单向链表，AQS 中的队列是 CLH 变体的虚拟双向队列（FIFO），AQS 是通过将每条请求共享资源的线程封装成一个节点来实现锁的分配。

主要原理图如下：

![](https://p0.meituan.net/travelcube/7132e4cef44c26f62835b197b239147b18062.png)

AQS 使用一个 Volatile 的 int 类型的成员变量来表示同步状态，通过内置的 FIFO 队列来完成资源获取的排队工作，通过 CAS 完成对 State 值的修改。

## 2 AQS 源码的整体框架

以当前使用的 JDK11 为例。

394-575 行定义了 AQS 的队列节点 Node。

580-613 行定义了 AQS 的同步状态 state 及相关方法。

615-780 行定义了 AQS 的排队方法(615 行进行了"Queuing utilities"注释)

782-887 行定义了 AQS 获取锁时的辅助方法(782 行进行了"Utilities for various versions of acquire"注释)

889-1087 行定义了 AQS 的多种获取锁的方法(889 行进行了"Various flavors of acquire"注释)

1089-1387 行定义了 AQS 的主要的开放 API 方法，包含获取锁和释放锁(1089 行进行了"Main exported methods"注释)

1389-1565 行定义了 AQS 的队列相关判断方法(1389 行进行了"Queue inspection methods"注释)

1567-1646 行定义了 AQS 的队列监测方法(1567 行进行了"Instrumentation and monitoring methods"注释)

1664-1772 行定义了 AQS 的对于 Condition 的内部支持方法(1664 行进行了"Internal support methods for Conditions"注释)

1774-1852 行定义了 AQS 的对于 Condition 的测量方法("Instrumentation methods for conditions"注释)

最后还有个静态代码块和两个 Node 相关的方法。

可以参考美团技术团队整理的框架图：

![](https://p1.meituan.net/travelcube/82077ccf14127a87b77cefd1ccf562d3253591.png)


## 3 队列节点 Node 

```java
static final class Node {
	// nextWaiter 的枚举数据，创建Node时直接使用该静态量
	static final Node SHARED = new Node();
	static final Node EXCLUSIVE = null;
	// waitStatus 的枚举数据，创建Node时直接使用该静态量
	static final int CANCELLED =  1;
	static final int SIGNAL    = -1;
	static final int CONDITION = -2;
	static final int PROPAGATE = -3;
	
	volatile int waitStatus;
	volatile Node prev;
	volatile Node next;
	volatile Thread thread;
	Node nextWaiter;
	 
	final boolean isShared() {
	    return nextWaiter == SHARED;
	}
	final Node predecessor() throws NullPointerException {
	    Node p = prev;
	    if (p == null)
	        throw new NullPointerException();
	    else
	        return p;
	}
}
// CLH变体队列的头和尾指针
private transient volatile Node head;
private transient volatile Node tail;
```

**Node 的属性值和方法的含义：**

| 属性/方法名称                         | 描述                                                                                                                                                                                                                  |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `volatile int waitStatus;`            | 当前节点在队列中的状态                                                                                                                                                                                                |
| `volatile Thread thread;`             | 表示处于该节点的线程。<br>如果一个锁获取成功则直接把这个存的设置成 null，<br>因为获取成功了，这个记录已经没用了；<br>如果获取失败则可能会被挂起，也就是存储 thread 意义。<br>AQS 不控制 Thread 的执行，只控制 Thread 的挂起。 | 
| `volatile Node prev;`                 | 前驱指针                                                                                                                                                                                                              |
| `volatile Node next`                  | 后继指针                                                                                                                                                                                                              |
| `Node nextWaiter;`                    | 指向下一个处于 CONDITION 状态的节点                                                                                                                                                                                   |
| `final boolean isShared()`            | 判断锁模式是否是共享模式                                                                                                                                                                                              |
| `final Node predecessor() throws NPE` | 返回前驱节点，没有的话抛出 npe                                                                                                                                                                                        |

**waitStatus 有下面几个枚举值：**
- `0` : 当一个 Node 被初始化的时候的默认值。
- `CANCELLED` : 为 1，请求已经取消，可能线程被中断或超时，需要从同步队列中取消等待，节点进入该状态后将不会变化。
- `SIGNAL` : 为-1，表示线程已经准备好了，就等资源释放了。后续节点的线程处于等待状态，而当前节点的线程如果释放了同步状态或者被取消，将会通知后续节点，使后续节点的线程得以运行。
- `CONDITION` : 为-2，节点在条件队列中，节点线程等待在 Condition 上，当其他线程对 Condition 调用了 `signal()` 方法后，该节点将会从条件队列中转移到同步队列中，加入到对同步状态的获取中。
- `PROPAGATE` : 为-3，当前线程处在 SHARED 情况下，该字段才会使用。表示下一次共享式同步状态获取将会无条件地传播下去。releaseShared 应该传播到其他节点。这是在 doReleaseShared 中设置的（仅针对头部节点），以确保传播继续进行，即使其他操作已经介入。

**锁模式包含两种**：
- `SHARED` : 表示线程以共享的模式等待锁
- `EXCLUSIVE` : 表示线程正在以独占的方式等待锁

## 4 同步状态 state
AQS 中维护了一个名为 state 的字段，意为同步状态，是由 Volatile 修饰的，用于展示当前临界资源的获锁情况：`private volatile int state;`

同时提供了几个访问这个字段的方法：
- `protected final int getState()` 获取 State 的值
- `protected final void setState(int newState)` 设置 State 的值
- `protected final boolean compareAndSetState(int expect, int update)` 使用 CAS 方式更新 State

通过对 state 字段的不同的逻辑设计可以实现多线程的独占模式和共享模式。
- `独占模式(不可重入)` ：state 不为 0，则阻塞；state 为 0，则尝试 CAS 自旋更新 state。
- `独占模式(可重入)` ：state 为 0，则尝试 CAS 自旋更新 state，并设置当前线程访问锁。state 不为 0，判断占有锁的线程是否是当前线程，是则 state+1；反之，阻塞等待。具体参考 ReentrantLock。
- `共享模式` ：state 表示剩余容量，剩余容量大于需要的容量，则 CAS 自旋更新；反之失败阻塞。

具体如何去实现不同的需求，就涉及到架构图中的第一层 API 层。

## 5 同步状态配置方法
AQS 提供了 8 个 `public final` 方法用于处理独占/共享、忽略/不忽略中断、有时限/没有时限的锁的请求。同时为了实现不同的同步器类，采用设计模式中的模板模式，提供了 5 个 `protected` 模板方法，由子类根据同步器的需求实现对应的设计。

该部分对应源码的 1089-1387 行，包含了 13 个方法。其中，5 个 `protected` 方法，由子类去实现(设计模式中的模板方法)；8 个 `public final` 方法，属于 AQS 提供的公开 API。除去 `isHeldExclusively` 方法，剩下的方法基本上都是相互配对。

> 有利于理解 AQS 的 API 的功能；理解浅层的设计逻辑；学习应用模板模式进行设计的思路；慢慢过渡到之后请求锁与释放锁的等待线程与 CLH 队列的处理关系上。

### 5.1 子类需要实现的方法

5 个必须实现的方法，因为 AQS 中函数默认的处理是直接抛出异常。

- `protected boolean tryAcquire(int arg)` 独占方式。arg 为获取锁的次数，尝试获取资源，成功则返回 True，失败则返回 False。
- `protected boolean tryRelease(int arg)` 独占方式。arg 为释放锁的次数，尝试释放资源，成功则返回 True，失败则返回 False。
- `protected int tryAcquireShared(int arg)` 共享方式。arg 为获取锁的次数，尝试获取资源。负数表示失败；0 表示成功，但没有剩余可用资源；正数表示成功，且有剩余资源。
- `protected boolean tryReleaseShared(int arg)` 共享方式。arg 为释放锁的次数，尝试释放资源，如果释放后允许唤醒后续等待结点返回 True，否则返回 False。
- `protected boolean isHeldExclusively()` 该线程是否正在独占资源。只有用到 Condition 才需要去实现它。

### 5.2 AQS 的公开 API
- `public final void acquire(int arg)` 独占方式获取锁。
- `public final void acquireInterruptibly(int arg) throws InterruptedException` 独占方式获取锁，若中断，则取消。
- `public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException` 独占方式获取锁，若中断则取消，若超时则失败。
- `public final boolean release(int arg)` 释放独占锁。
- `public final void acquireShared(int arg)` 共享方式获取锁。
- `public final void acquireSharedInterruptibly(int arg)` 共享方式获取锁，若中断，则取消。
- `public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException` 共享方式获取锁，若中断则取消，若超时则失败。
- `public final boolean releaseShared(int arg)` 释放共享锁。

### 5.3 公开 API 的实现

```java
// 独占方式获取锁
public final void acquire(int arg) {
	if (!tryAcquire(arg) &&                                                                  // 尝试获取锁
		acquireQueued(addWaiter(Node.EXCLUSIVE), arg))  // 创建Node，插入队列，等待直到成功
		selfInterrupt();
}

// 独占方式获取锁，若中断，则取消
public final void acquireInterruptibly(int arg)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	if (!tryAcquire(arg))                          // 尝试获取锁
		doAcquireInterruptibly(arg);  // 创建Node，插入队列，等待直到成功、中断
}

// 独占方式获取锁，若中断则取消，若超时则失败
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	return tryAcquire(arg) ||                                    // 尝试获取锁
		doAcquireNanos(arg, nanosTimeout);  // 创建Node，插入队列，等待直到成功、中断、超时
}

// 释放独占锁
public final boolean release(int arg) {
	if (tryRelease(arg)) {                                  // 尝试释放锁
		Node h = head;                                        // 判断队列头指针
		if (h != null && h.waitStatus != 0)
			unparkSuccessor(h);
		return true;
	}
	return false;
}

// 共享方式获取锁
public final void acquireShared(int arg) {
	if (tryAcquireShared(arg) < 0)              // 尝试获取锁
		doAcquireShared(arg);
}

// 共享方式获取锁，若中断，则取消
public final void acquireSharedInterruptibly(int arg)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	if (tryAcquireShared(arg) < 0)
		doAcquireSharedInterruptibly(arg);
}

// 共享方式获取锁，若中断则取消，若超时则失败
public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	return tryAcquireShared(arg) >= 0 ||
		doAcquireSharedNanos(arg, nanosTimeout);
}

// 释放共享锁
public final boolean releaseShared(int arg) {
	if (tryReleaseShared(arg)) {
		doReleaseShared();
		return true;
	}
	return false;
}
```

### 5.4 公开 API 的调用链逻辑
使用设计模式的模板模式，AQS 使用子类实现的模板方法去处理。

- `AQS#acquire` —— `子类#tryAcquire` —— `AQS#addWaiter` 独占模式-忽略中断
- `AQS#acquireInterruptibly` —— `子类#tryAcquire` —— `AQS#doAcquireInterruptibly` —— `AQS#addWaiter`  独占模式-中断即中止
- `AQS#tryAcquireNanos` —— `子类#tryAcquire` —— `AQS#doAcquireNanos` —— `AQS#addWaiter` 独占模式-中断即中止-含有效时间
- `AQS#release` —— `子类#tryRelease` 独占模式-释放锁
- `AQS#acquireShared` —— `子类#tryAcquireShared` —— `AQS#doAcquireShared` —— `AQS#addWaiter` 共享模式-忽略中断
- `AQS#acquireSharedInterruptibly` —— `子类#tryAcquireShared` —— `AQS#doAcquireSharedInterruptibly` —— `AQS#addWaiter`  共享模式-中断即中止
- `AQS#tryAcquireSharedNanos` —— `子类#tryAcquireShared` —— `AQS#doAcquireSharedNanos` —— `AQS#addWaiter` 共享模式-中断即中止-含有效时间
- `AQS#releaseShared` —— `子类#tryReleaseShared` —— `AQS#doReleaseShared` 独占模式-中断即中止

对于获取锁或释放锁后，可能还会有一些 CLH 变体队列节点的处理，有的是专门定义函数来处理，有的是直接在当前函数处理。专门定义函数就属于"锁获取方法层"，一般以 do 开头的函数，对应源码中 782-887 行内容。不过需要先了解 CLH 变体队列的内容。

## 6 CLH 变体队列

> 在了解 AQS 关于同步状态配置方法后，再往深去了解深层的实现方法，例如：尝试获取锁失败后后续如何在队列中进行处理，释放锁底层的设计。
>
> 队列中排队方法设计到 "Queuing utilities" 部分的内容，一些辅助方法涉及到 "Utilities for various versions of acquire" 部分的内容。
> 
> 这里将这两部分源码与前面的源码放在一起，根据独占、共享、中断、超时锁的情况进行依次分析，有利于理解的连贯性。

### 6.1 获取独占锁

```java
public final void acquire(int arg) {
	if (!tryAcquire(arg) &&                                                                  // 尝试获取锁
		acquireQueued(addWaiter(Node.EXCLUSIVE), arg))  // 创建Node，插入队列，等待直到成功
		selfInterrupt();
}

private Node addWaiter(Node mode) {
	Node node = new Node(mode);
	for (;;) {                                                                  // CAS+自旋的机制去更新队列尾节点并更新指针引用。
		Node oldTail = tail;
		if (oldTail != null) {
			node.setPrevRelaxed(oldTail);
			if (compareAndSetTail(oldTail, node)) {
				oldTail.next = node;
				return node;
			}
		} else {
			initializeSyncQueue();                           // 空的，初始化队列
		}
	}
}

// 头节点获取到锁；非头节点等待前面为head 并自旋tryAcquire
// 返回获取中是否被中断
final boolean acquireQueued(final Node node, int arg) {
	boolean interrupted = false;
	try {
		for (;;) {
			final Node p = node.predecessor();
			if (p == head && tryAcquire(arg)) {   // 前面为头节点，并自己成功获取锁
				setHead(node);                                   // 设置自己为头节点
				p.next = null;                                         // help GC
				return interrupted;
			}
			if (shouldParkAfterFailedAcquire(p, node))   // 判断能否将节点挂起
				interrupted |= parkAndCheckInterrupt();   // 执行挂起并返回线程中断状态
		}
	} catch (Throwable t) {
		cancelAcquire(node);  // 自旋出现异常，取消获取锁
		if (interrupted)                // 执行中断
			selfInterrupt();
		throw t;
	}
}

// 根据node和前缀节点pred，判断能否将node节点挂起
// 判断过程中将一些CANCELLED类型的节点从队列中移除
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL)             // 前置节点为SIGNAL类型
        return true;                                // 则可以挂起 (SIGNAL节点释放/取消时会去唤醒后面节点)
    if (ws > 0) {                                      // 前置节点为CANCELLED类型
        do {                                                // 则找非CANCELLED类型的节点，循环往前将CANCELLED类型的节点从队列中移除
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;                    // 更新前置节点的后置指针
    } else {                                               // 其他情况，设置前置节点为SIGNAL类型
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);  
    }
    return false;
}

// 将 node 设置为 CANCELLED 状态，寻找非CANCELLED类型的前缀节点，更新前置节点的后置指针
private void cancelAcquire(Node node) {
    if (node == null)
        return;
    node.thread = null;
    Node pred = node.prev;            
    while (pred.waitStatus > 0)                           // 获取前面一个非CANCELLED类型的节点作为前置节点
        node.prev = pred = pred.prev;                // 循环往前将CANCELLED类型的节点从队列中移除
    Node predNext = pred.next;
    node.waitStatus = Node.CANCELLED;    // 将本节点设置为CANCELLED类型
    // 更新前置节点的后置指针
    if (node == tail && compareAndSetTail(node, pred)) {  // 本节点是尾节点，则CAS更新尾节点
        compareAndSetNext(pred, predNext, null);                 // 同时更新 predNext 指针
    } else {                                               // pred不是头结点 && pred的线程不为空 && pred.ws = singal
        int ws;
        if (pred != head &&              
            ((ws = pred.waitStatus) == Node.SIGNAL ||
             (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
            pred.thread != null) {
            Node next = node.next;                  // CAS 机制更新 predNext 指针
            if (next != null && next.waitStatus <= 0)
                compareAndSetNext(pred, predNext, next);
        } else {                                                        // 本节点是头
            unparkSuccessor(node);              // 唤醒自己的后继结点，见释放独占锁中
        } 
        node.next = node;                                // help GC
    }
}
```

### 6.2 获取独占、抛出中断锁

```java
// 独占方式获取锁，若中断，则取消
public final void acquireInterruptibly(int arg)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	if (!tryAcquire(arg))                          // 尝试获取锁
		doAcquireInterruptibly(arg);  // 创建Node，插入队列，等待直到成功、中断
}
```

`doAcquireInterruptibly` 方法的实现与上面的 `acquireQueued` 方法类似。当循环判断时若有异常，则通过 catch 捕捉后抛出；若挂起时出现异常，则直接抛出。

### 6.3 释放独占锁

```java
// 释放独占锁
public final boolean release(int arg) {
	if (tryRelease(arg)) {                                  // 尝试释放锁
		Node h = head;                                        // 判断队列头指针
		if (h != null && h.waitStatus != 0)
			unparkSuccessor(h);
		return true;
	}
	return false;
}

// 唤醒自己的后继结点
private void unparkSuccessor(Node node) {
	// 更新节点状态
	int ws = node.waitStatus;
	if (ws < 0)
		node.compareAndSetWaitStatus(ws, 0);  

	// 寻找符合条件的后续节点-下一个节点
	Node s = node.next; 
	if (s == null || s.waitStatus > 0) {
		s = null;
		// 从尾部到头部遍历找到最前面满足条件的
		for (Node p = tail; p != node && p != null; p = p.prev)  
			if (p.waitStatus <= 0)
				s = p;
	}

	//唤醒后续节点
	if (s != null) 
		LockSupport.unpark(s.thread);
}
```

### 6.4 获取共享锁

```java
// 共享方式获取锁
public final void acquireShared(int arg) {
	if (tryAcquireShared(arg) < 0)              // 尝试获取锁
		doAcquireShared(arg);
}

// 头节点获取到锁；非头节点等待前面为 head 并自旋 tryAcquireShared
// 若有中断，则直接中断
private void doAcquireShared(int arg) {
	final Node node = addWaiter(Node.SHARED);   // 先创建节点 Node
	boolean interrupted = false;
	try {
		for (;;) {
			final Node p = node.predecessor();
			if (p == head) {                                              // 前面为头节点
				int r = tryAcquireShared(arg);           // 尝试获取锁
				if (r >= 0) {                                                   // 若 >= 0，则获取成功
					setHeadAndPropagate(node, r);  // 设置自己为头节点，并唤醒后续共享锁
					p.next = null;                                         // help GC
					return;
				}
			}
			if (shouldParkAfterFailedAcquire(p, node))   // 判断能否将节点挂起
				interrupted |= parkAndCheckInterrupt();   // 执行挂起并返回线程中断状态
		}
	} catch (Throwable t) {
		cancelAcquire(node);  // 自旋出现异常，取消获取锁
		throw t;
	} finally {
		if (interrupted)                // 执行中断
			selfInterrupt();
	}
}

// 设置自己为头节点，并唤醒后续共享锁
private void setHeadAndPropagate(Node node, int propagate) {
    Node h = head; 
    setHead(node);
    /** 如果自定义实现的是>0也就是说后续节点也可以获取到锁，
    * 或者头节点是null，或者头节点是失效的，或者新的头是null，
    * 或者新的头是失效的 
    */
    if (propagate > 0 || h == null || h.waitStatus < 0 ||
        (h = head) == null || h.waitStatus < 0) {
        Node s = node.next;
        if (s == null || s.isShared())  // 下个节点是空的，或者需要获取的是共享锁
            doReleaseShared();          // 后面的所有共享锁都唤醒
    }
}

// 释放共享锁
private void doReleaseShared() {
    for (;;) {                                             /** 死循环是为了查看是否有队列结构的变更 */
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {  // signal状态，则先通过CAS设置为0
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;            
                unparkSuccessor(h);    // 释放head的下一个节点
            } 
            else if (ws == 0 &&   // head已经是0，则设置头告诉现在是共享模式下，对应 node.waitStatus < 0嘛
                     !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;                // 一直循环下去，直到后面获取到锁的节点把现在的head给替换掉
        }
        /** 跳到这里只可能有两种情况，
        * 1、已经释放了该释放的 
        * 2、上面的if直接跳过了，根本没走。所以break跳出循环 
        */
        if (h == head)          
            break;
    }
}
```


### 6.5 获取共享、抛出中断锁

```java
// 共享方式获取锁，若中断，则取消
public final void acquireSharedInterruptibly(int arg)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	if (tryAcquireShared(arg) < 0)
		doAcquireSharedInterruptibly(arg);
}
```

`doAcquireSharedInterruptibly` 方法的实现与上面的 `doAcquireShared` 方法类似。当循环判断时若有异常，则通过 catch 捕捉后抛出；若挂起时出现异常，则直接抛出。

### 6.6 释放共享锁

```java
// 释放共享锁
public final boolean releaseShared(int arg) {
	if (tryReleaseShared(arg)) {
		doReleaseShared();
		return true;
	}
	return false;
}
```

### 6.7 获取含超时独占锁
相较于前面，多了一些时间上的判断。超时就 `cancelAcquire`，中断就抛出 `InterruptedException`。

```java
// 独占方式获取锁，若中断则取消，若超时则失败
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	return tryAcquire(arg) ||                                    // 尝试获取锁
		doAcquireNanos(arg, nanosTimeout);  // 创建Node，插入队列，等待直到成功、中断、超时
}

private boolean doAcquireNanos(int arg, long nanosTimeout)
		throws InterruptedException {
	if (nanosTimeout <= 0L)
		return false;
	final long deadline = System.nanoTime() + nanosTimeout;
	final Node node = addWaiter(Node.EXCLUSIVE);
	try {
		for (;;) {
			final Node p = node.predecessor();
			if (p == head && tryAcquire(arg)) {
				setHead(node);
				p.next = null; // help GC
				return true;
			}
			nanosTimeout = deadline - System.nanoTime();
			if (nanosTimeout <= 0L) {
				cancelAcquire(node);
				return false;
			}
			if (shouldParkAfterFailedAcquire(p, node) &&
				nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
				LockSupport.parkNanos(this, nanosTimeout);
			if (Thread.interrupted())
				throw new InterruptedException();
		}
	} catch (Throwable t) {
		cancelAcquire(node);
		throw t;
	}
}
```

### 6.8 获取含超时共享锁
同样，相较于前面，多了一些时间上的判断。超时就 `cancelAcquire`，中断就抛出 `InterruptedException`。
```java
// 共享方式获取锁，若中断，则取消
public final void acquireSharedInterruptibly(int arg)
		throws InterruptedException {
	if (Thread.interrupted())
		throw new InterruptedException();
	if (tryAcquireShared(arg) < 0)
		doAcquireSharedInterruptibly(arg);
}

private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
		throws InterruptedException {
	if (nanosTimeout <= 0L)
		return false;
	final long deadline = System.nanoTime() + nanosTimeout;
	final Node node = addWaiter(Node.SHARED);
	try {
		for (;;) {
			final Node p = node.predecessor();
			if (p == head) {
				int r = tryAcquireShared(arg);
				if (r >= 0) {
					setHeadAndPropagate(node, r);
					p.next = null; // help GC
					return true;
				}
			}
			nanosTimeout = deadline - System.nanoTime();
			if (nanosTimeout <= 0L) {
				cancelAcquire(node);
				return false;
			}
			if (shouldParkAfterFailedAcquire(p, node) &&
				nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
				LockSupport.parkNanos(this, nanosTimeout);
			if (Thread.interrupted())
				throw new InterruptedException();
		}
	} catch (Throwable t) {
		cancelAcquire(node);
		throw t;
	}
}
```


## 7 后续

1. 思考，为什么复杂的操作，没有内置锁，仅仅依靠 CAS，如何实现线程安全的？
2. 状态的转换还是有些乱

## 8 Reference
- [从ReentrantLock的实现看AQS的原理及应用 - 美团技术团队](https://tech.meituan.com/2019/12/05/aqs-theory-and-apply.html)
- [给Doug Lea大神跪了！AbstractQueuedSynchronizer（AQS）是如何做到多线程同步的？ - 掘金](https://juejin.cn/post/7038873661310238734#heading-6)