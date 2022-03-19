## 1 显示锁

协调共享对象的访问：synchronized、volatile 和 ReentrantLock。

其中 synchronized 是 Java 内置锁，ReentrantLock 的底层是基于 CAS 机制的显示锁。显示锁在于加锁、解锁的方法都是显示的。

### 1.1 ReentrantLock vs Synchronized
**相同点：**
1. 两种锁都提供互斥性、内存可见性。
2. 都提供了可重入的加锁语义，都是对应 state 的计数增减来实现的。

**不同点：**
1. Synchronized 是 Java 内置锁，依赖于监视器模式。ReentrantLock 是依赖于 AQS 类使用 CAS 机制实现的。
2. Synchronized 没有 ReentrantLock 灵活，程序无法中断一个正在等待获取锁的线程，或者无法在请求获取一个锁的时候无限地等待下去。ReentrantLock 支持响应中断、超时、尝试获取锁等高级功能。
3. Synchronized 可以自动释放监视器。ReentrantLock 需要使用 lock 加锁、unlock 解锁。
4. Synchronized 只有非公平锁。ReentrantLock 有公平锁和非公平锁两种情况。
5. Synchronized 只能关联一个条件队列。ReentrantLock 可以关联多个条件队列。

**什么时候用 Synchronized?**
1. 能使用 Synchronized 的时候就用 Synchronized。
2. ReentrantLock 需要显示地解锁，很容易忘记。
3. Synchronized 对于 ReentrantLock 来说，被许多开发人员所熟悉，并且简洁紧凑。
4. Synchronized 属于 Java 内置锁，在编译的时候，可以对锁进行优化，如不必要的时候进行锁消除。后续 JVM 可能仍会去优化内置锁。ReentrantLock 属于类库提供的锁，很难有优化的空间。

**什么时候用 ReentrantLock?**
1. 需要高级功能的时候使用 ReentrantLock。
2. 支持响应中断的锁、超时获取锁、尝试获取锁
3. 公平锁。

### 1.2 ReentrantLock 的使用

**使用时最好使用 `try-finally` 来进行加锁和解锁。**

`try-finally` 可以防止加锁后出现异常导致无法解锁情况的出现。

```java
try{
	lock.lock();
	...
}finally{
	lock.unlock();
}
```

**轮询操作避免死锁的发生**
```java
while (true) {
	if (fromAcct.lock.tryLock()) {
		try {
			if (toAcct.lock.tryLock()) {
				try {
					if (fromAcct.getBalance().compareTo(amount) < 0)
						throw new InsufficientFundsException();
					else {
						fromAcct.debit(amount);
						toAcct.credit(amount);
						return true;
					}
				} finally {
					toAcct.lock.unlock();
				}
			}
		} finally {
			fromAcct.lock.unlock();
		}
	}
	if (System.nanoTime() < stopTime)
		return false;
	NANOSECONDS.sleep(fixedDelay + rnd.nextLong() % randMod);
}
```

**定时锁避免死锁的发生**

`tryLock(long timeout, TimeUnit unit)`。成功返回 true，并获得锁；否则返回 false。

**可中断的锁获取操作**
`public void lockInterruptibly() throws InterruptedException` ：在获取锁的过程中仍然能够响应中断。
- 如果已经是中断状态，抛出异常；
- tryAcquire 方法尝试获取锁，获得成功，直接结束；
- 获得失败，则调用 doAcquireInterruptibly 方法，Acquires in exclusive interruptible mode。

```java
private Lock lock = new ReentrantLock();
public boolean sendOnSharedLine(String message)
		throws InterruptedException {
	lock.lockInterruptibly();
	try {
		return cancellableSendOnSharedLine(message);
	} finally {
		lock.unlock();
	}
}
```

### 1.3 公平性
在激烈竞争的情况下，非公平锁的性能高于公平锁的性能的一个原因是：在恢复一个被挂起的线程与该线程真正开始运行之间存在着严重的延迟。

假设线程 A 持有一个锁，并且线程 B 请求这个锁。由于这个锁已被线程 A 持有，因此 B 将被挂起。当 A 释放锁时，B 将被唤醒，因此会再次尝试获取锁。与此同时，如果 C 也请求这个锁，那么 C 很可能会在 B 被完全唤醒之前获得、使用以及释放这个锁。这样的情况是一种“双赢”的局面：B 获得锁的时刻并没有推迟，C 更早地获得了锁，并且吞吐量也获得了提高。当持有锁的时间相对较长，或者请求锁的平均时间间隔较长，那么应该使用公平锁。在这些情况下，“插队”带来的吞吐量提升（当锁处于可用状态时，线程却还处于被唤醒的过程中）则可能不会出现。

即使对于公平锁而言，可轮询的 tryLock 仍然会“插队”。

### 1.4 读-写锁

ReadWriteLock 接口。在读-写锁实现的加锁策略中，允许多个读操作同时进行，但每次只允许一个写操作。

对于在多处理器系统上被频繁读取的数据结构，读-写锁能够提高性能。而在其他情况下，读-写锁的性能比独占锁的性能要略差一些，这是因为它们的复杂性更高。

在读取锁和写入锁之间的交互可以采用多种实现方式。ReadWriteLock 中的一些可选实现包括：
- **释放优先**。当一个写入操作释放写入锁时，并且队列中同时存在读线程和写线程，那么应该优先选择读线程，写线程，还是最先发出请求的线程？
- **读线程插队**。如果锁是由读线程持有，但有写线程正在等待，那么新到达的读线程能否立即获得访问权，还是应该在写线程后面等待？如果允许读线程插队到写线程之前，那么将提高并发性，但却可能造成写线程发生饥饿问题。
- **重入性**。读取锁和写入锁是否是可重入的？
- **降级**。如果一个线程持有写入锁，那么它能否在不释放该锁的情况下获得读取锁？这可能会使得写入锁被“降级”为读取锁，同时不允许其他写线程修改被保护的资源。
- **升级**。读取锁能否优先于其他正在等待的读线程和写线程而升级为一个写入锁？在大多数的读-写锁实现中并不支持升级，因为如果没有显式的升级操作，那么很容易造成死锁。（如果两个读线程试图同时升级为写入锁，那么二者都不会释放读取锁。）

## 2 条件队列

### 2.1 条件队列的概念
“条件队列”这个名字来源于：使一组线程(称之为等待线程集合)通过某种方式来等待某个条件成真。传统队列的元素是一个个数据，条件队列的元素是一个个等待某个条件的线程。

### 2.2 示例：有界缓存的 put 操作

**实现一：**
方法使用 synchronized 修饰；若有界缓存已满，则抛出异常；否则正常处理。
```java
public synchronized void put(V v) throws BufferFullException {
	if (isFull())
		throw new BufferFullException();
	doPut(v);
}
```

**实现二：**
轮询+休眠的方式来实现简单的阻塞。
```java
public void put(V v) throws InterruptedException {
	while (true) {
		synchronized (this) {
			if (!isFull()) {
				doPut(v);
				return;
			}
		}
		Thread.sleep(SLEEP_GRANULARITY);
	}
}
```

**实现三：**
使用条件队列来实现简单的阻塞。
```java
public synchronized void put(V v) throws InterruptedException {
	while (isFull())
		wait();
	doPut(v);
	notifyAll();
}
```

**总结：**
1. 实现一抛出异常的话，对用户不友好。用户需要进行异常捕获，并使用循环重试来获取数据。
2. 实现二需要容忍自旋导致 CPU 时钟周期浪费(忙等待)，也要忍受休眠导致的低响应性(休眠)。除此之外，还可以 Thread.yield，这相当于给调度器一个提示：现在需要让出一定的时间使另一个线程运行。假如正在等待另一个线程执行工作，那么如果选择让出处理器而不是消耗完整个 CPU 调度时间片，那么可以使整体的执行过程变快。
3. 实现三中使用 wait 操作，操作系统会将线程挂起同时释放锁，直到有其他线程调用 notify 或 notifyAll 方法。效果更好。

> 如果某个功能无法通过“轮询和休眠”来实现，那么使用条件队列也无法实现，但条件队列使得在表达和管理状态依赖性时更加简单和高效。

### 2.3 条件谓词 & 条件等待的三元关系
条件谓词是使某个操作成为状态依赖操作的前提条件。要想正确地使用条件队列，关键是找出对象在哪个条件谓词上等待。例如，上面例子中，put 操作需要等待 `!isFull()` 条件成立。

条件谓词将在等待与通知等过程中导致许多困惑，因为在 API 中没有对条件谓词进行实例化的方法，并且在 Java 语言规范或 JVM 实现中也没有任何信息可以确保正确地使用它们。将与条件队列相关联的条件谓词以及在这些条件谓词上等待的操作都写入文档。

条件等待中存在一种重要的三元关系，包括加锁、wait 方法和条件谓词。锁的原因在于，对于多个状态变量由锁来保证安全性，同时先判断条件谓词后操作属于竞态条件，需要加锁。

wait 方法将释放锁，阻塞当前线程，并等待直到超时，然后线程被中断或者通过一个通知被唤醒。在唤醒进程后，wait 在返回前还要重新获取锁。当线程从 wait 方法中被唤醒时，它在重新请求锁时不具有任何特殊的优先级，而要与任何其他尝试进入同步代码块的线程一起正常地在锁上进行竞争。

### 2.4 过早唤醒

内置条件队列可以与多个条件谓词一起使用。当有线程调用 `notifyAll` 的时候，可能会将所有线程唤醒。对于一些线程来说，可能对应的条件谓词还没有满足，仅仅是其中一部分的线程的条件谓词满足了。那么对于没有满足的线程来说，这就属于过早唤醒。

对于这种情况，一般采用锁和 while 循环一起参与的方式，具体可看下述内容。

当使用条件等待时（例如 `Object.wait` 或 `Condition.await` ）：
- 通常都有一个条件谓词——包括一些对象状态的测试，线程在执行前必须首先通过这些测试。
- 在调用 `wait` 之前测试条件谓词，并且从 `wait` 中返回时再次进行测试。
- 在一个循环中调用 `wait`。
- 确保使用与条件队列相关的锁来保护构成条件谓词的各个状态变量。
- 当调用 `wait` 、 `notify` 或 `notifyAll` 等方法时，一定要持有与条件队列相关的锁。
- 在检查条件谓词之后以及开始执行相应的操作之前，不要释放锁。

### 2.5 通知
由于多个线程可以基于不同的条件谓词在同一个条件队列上等待，因此如果使用 `notify` 而不是 `notifyAll`，那么将是一种危险的操作，因为单一的通知很容易导致类似于信号丢失的问题。

只有同时满足以下两个条件时，才能用单一的 `notify` 而不是 `notifyAll` ：
- 所有等待线程的类型都相同。只有一个条件谓词与条件队列相关，并且每个线程在从 wait 返回后将执行相同的操作。
- 单进单出。在条件变量上的每次通知，最多只能唤醒一个线程来执行。

由于不同的条件谓词在一个条件队列上等待必须使用 notifyAll，那么需要唤醒 n 个线程。如果唤醒次数多，耗时还是很严重的。后面引入 Condition 显示条件队列，每个谓词设置一个条件队列，这样就可以只使用 notify 唤醒一个线程就可以了。

### 2.6 示例：二元可打开闭锁
```java
public class ThreadGate {
    // CONDITION-PREDICATE: opened-since(n) (isOpen || generation>n)
    @GuardedBy("this") private boolean isOpen;
    @GuardedBy("this") private int generation;

    public synchronized void close() {
        isOpen = false;
    }

    public synchronized void open() {
        ++generation;
        isOpen = true;
        notifyAll();
    }

    // BLOCKS-UNTIL: opened-since(generation on entry)
    public synchronized void await() throws InterruptedException {
        int arrivalGeneration = generation;
        while (!isOpen && arrivalGeneration == generation)
            wait();
    }
}
```
### 2.7 子类的安全问题

要想支持子类化，那么在设计类时需要保证：如果在实施子类化时违背了条件通知或单次通知的某个需求，那么在子类中可以增加合适的通知机制来代表基类。对于状态依赖的类，要么将其等待和通知等协议完全向子类公开（并且写入正式文档），要么完全阻止子类参与到等待和通知等过程中。

另一种选择是完全禁止子类化。如使用 final 修饰。

### 2.8 封装条件队列

通常，我们应该把条件队列封装起来，因而除了使用条件队列的类，就不能在其他地方访问它。否则，调用者会自以为理解了在等待和通知上使用的协议，并且采用一种违背设计的方式来使用条件队列。

### 2.9 入口协议和出口协议

对于每个依赖状态的操作，以及每个修改其他操作依赖状态的操作，都应该定义一个入口协议和出口协议。入口协议就是该操作的条件谓词，出口协议则包括，检查被该操作修改的所有状态变量，并确认它们是否使某个其他的条件谓词变为真，如果是，则通知相关的条件队列。

### 2.10 显示的 Condition 对象
正如 Lock 是一种广义的内置锁，Condition 也是一种广义的内置条件队列。内置条件队列存在一些缺陷。每个内置锁都只能有一个相关联的条件队列。如果想编写一个带有多个条件谓词的并发对象，或者想获得除了条件队列可见性之外的更多控制权，就可以使用显式的 Lock 和 Condition 而不是内置锁和条件队列，这是一种更灵活的选择。

一个 Condition 和一个 Lock 关联在一起，就像一个条件队列和一个内置锁相关联一样。要创建一个 Condition，可以在相关联的 Lock 上调用 `Lock.newCondition` 方法。正如 Lock 比内置加锁提供了更为丰富的功能，Condition 同样比内置条件队列提供了更丰富的功能：在每个锁上可存在多个等待、条件等待可以是可中断的或不可中断的、基于时限的等待，以及公平的或非公平的队列操作。

与内置条件队列不同的是，对于每个 Lock，可以有任意数量的 Condition 对象。Condition 对象继承了相关的 Lock 对象的公平性，对于公平的锁，线程会依照 FIFO 顺序从 Condition.await 中释放。

特别注意：在 Condition 对象中，与 wait、notify 和 notifyAll 方法对应的分别是 await、signal 和 signalAll。但是，Condition 对 Object 进行了扩展，因而它也包含 wait 和 notify 方法。一定要确保使用正确的版本——await 和 signal。

线程安全的有界缓存，使用了两个 Condition。同时只需要使用 signal，不需要使用 signalAll，降低需要发生的上下文切换与锁请求的次数。
```java
public class ConditionBoundedBuffer <T> {
    protected final Lock lock = new ReentrantLock();
    // CONDITION PREDICATE: notFull (count < items.length)
    private final Condition notFull = lock.newCondition();
    // CONDITION PREDICATE: notEmpty (count > 0)
    private final Condition notEmpty = lock.newCondition();
    private static final int BUFFER_SIZE = 100;
    @GuardedBy("lock") private final T[] items = (T[]) new Object[BUFFER_SIZE];
    @GuardedBy("lock") private int tail, head, count;

    // BLOCKS-UNTIL: notFull
    public void put(T x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length)
                notFull.await();
            items[tail] = x;
            if (++tail == items.length)
                tail = 0;
            ++count;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    // BLOCKS-UNTIL: notEmpty
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0)
                notEmpty.await();
            T x = items[head];
            items[head] = null;
            if (++head == items.length)
                head = 0;
            --count;
            notFull.signal();
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```

## 3 AbstractQueuedSynchronizer
### 3.1 AQS

AQS 负责管理同步器类中的状态，它管理了一个整数状态信息，可以通过 `getState`, `setState` 以及 `compareAndSetState` 等 protected 类型方法来进行操作。这个整数可以用于表示任意状态。例如，`ReentrantLock` 用它来表示所有者线程已经重复获取该锁的次数，`Semaphore` 用它来表示剩余的许可数量，`FutureTask` 用它来表示任务的状态（尚未开始、正在运行、已完成以及已取消）。在同步器类中还可以自行管理一些额外的状态变量，例如，`ReentrantLock` 保存了锁的当前所有者的信息，这样就能区分某个获取操作是重入的还是竞争的。

### 3.2 ReentrantLock

`ReentrantLock` 将同步状态用于保存锁获取操作的次数，并且还维护一个 owner 变量来保存当前所有者线程的标识符，只有在当前线程刚刚获取到锁，或者正要释放锁的时候，才会修改这个变量。

### 3.3 Semaphone 与 CountDownLatch

`Semaphore` 将 AQS 的同步状态用于保存当前可用许可的数量。`tryAcquireShared` 方法首先计算剩余许可的数量，如果没有足够的许可，那么会返回一个值表示获取操作失败。如果还有剩余的许可，那么 `tryAcquireShared` 会通过 `compareAndSetState` 以原子方式来降低许可的计数。如果这个操作成功，那么将返回一个值表示获取操作成功。在返回值中还包含了表示其他共享获取操作能否成功的信息，如果成功，那么其他等待的线程同样会解除阻塞。

`CountDownLatch` 使用 AQS 的方式与 `Semaphore` 很相似：在同步状态中保存的是当前的计数值。`countDown` 方法调用 `release`，从而导致计数值递减，并且当计数值为零时，解除所有等待线程的阻塞。`await` 调用 `acquire`，当计数器为零时，`acquire` 将立即返回，否则将阻塞。

### 3.4 FutureTask

在 `FutureTask` 中，AQS 同步状态被用来保存任务的状态，例如，正在运行、已完成或已取消。

`FutureTask` 还维护一些额外的状态变量，用来保存计算结果或者抛出的异常。此外，它还维护了一个引用，指向正在执行计算任务的线程（如果它当前处于运行状态），因而如果任务取消，该线程就会中断。

`Future.get` 的语义非常类似于闭锁的语义——如果发生了某个事件（由 `FutureTask` 表示的任务执行完成或被取消），那么线程就可以恢复执行，否则这些线程将停留在队列中并直到该事件发生。

### 3.5 ReentrantReadWriteLock

`ReadWriteLock` 接口表示存在两个锁：一个读取锁和一个写入锁，但在基于 AQS 实现的 `ReentrantReadWriteLock` 中，单个 AQS 子类将同时管理读取加锁和写入加锁。`ReentrantReadWriteLock` 使用了一个 16 位的状态来表示写入锁的计数，并且使用了另一个 16 位的状态来表示读取锁的计数。在读取锁上的操作将使用共享的获取方法与释放方法，在写入锁上的操作将使用独占的获取方法与释放方法。

AQS 在内部维护一个等待线程队列，其中记录了某个线程请求的是独占访问还是共享访问。在 `ReentrantReadWriteLock` 中，当锁可用时，如果位于队列头部的线程执行写入操作，那么线程会得到这个锁，如果位于队列头部的线程执行读取访问，那么队列中在第一个写入线程之前的所有线程都将获得这个锁。

这种机制并不允许选择读取线程优先或写入线程优先等策略，在某些读写锁实现中也采用了这种方式。因此，要么 AQS 的等待队列不能是一个 FIFO 队列，要么使用两个队列。然而，在实际中很少需要这么严格的排序策略。如果非公平版本的 `ReentrantReadWriteLock` 无法提供足够的活跃性，那么公平版本的 `ReentrantReadWriteLock` 通常会提供令人满意的排序保证，并且能确保读取线程和写入线程不会发生饥饿问题。


## 4 后续
1. 查看了解 AQS 的模板方法，了解 AQS 的运行机制(私有/final 方法与模板方法之间的调用关系)。
2. 在 AQS 模板方法下，各个同步器的实现思路。
3. Condition 再找几个例子看一下，不大熟悉。


