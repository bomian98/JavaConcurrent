## 1 ReentrantLock
`ReentrantLock` 类实现了 `Lock` 接口，并提供公平锁、非公平锁、独占锁多种类型的锁，同时支持条件队列机制。

`ReentrantLock` 是一个显示加锁解锁的、可重入的、独占锁，提供了比 `synchronized` 更灵活的加锁的方法。

### 1.1 大体结构

```java
public class ReentrantLock implements Lock, java.io.Serializable {
	private final Sync sync;
	abstract static class Sync extends AbstractQueuedSynchronizer{ ... } 
	static final class NonfairSync extends Sync { ... }
	static final class FairSync extends Sync { ... }

	public ReentrantLock() {  sync = new NonfairSync(); }
	public ReentrantLock(boolean fair) { sync = fair ? new FairSync() : new NonfairSync(); }

    public int getHoldCount() { return sync.getHoldCount(); }
    public boolean isHeldByCurrentThread() { return sync.isHeldExclusively(); }
    public boolean isLocked() { return sync.isLocked(); }
    public final boolean isFair() { return sync instanceof FairSync; }
    ....
```

### 1.2 Lock 接口

- `void lock();` 获取锁
- `void lockInterruptibly() throws InterruptedException;` 获取中断锁
- `boolean tryLock();` 尝试获取锁
- `boolean tryLock(long time, TimeUnit unit) throws InterruptedException;` 尝试获取含时限锁
- `void unlock();` 释放锁
- `Condition newCondition();` 创建 Condition 示例

`ReentrantLock` 对于 `Lock` 接口的实现如下，比较容易理解，调用相应的接口就好了。不过需要注意的是 `Lock` 接口中 `tryLock()` 方法要求当调用该函数时若锁是空闲的那么就获取锁，因此这是一个非公平获取锁，所以 `ReentrantLock` 在实现时调用了 `nonfairTryAcquire` 方法。

```java
public void lock() { sync.acquire(1); }
public void lockInterruptibly() throws InterruptedException {  
	sync.acquireInterruptibly(1); }
public boolean tryLock() { return sync.nonfairTryAcquire(1); } 
public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
	return sync.tryAcquireNanos(1, unit.toNanos(timeout)); }
public void unlock() { sync.release(1); }
public Condition newCondition() { return sync.newCondition(); }
```

### 1.3 Sync 抽象类

为了实现锁，`ReentrantLock` 采用了 AQS 来帮助管理线程的同步、阻塞、唤醒。具体来说，`ReentrantLock` 内部包含了一个 `Sync` 抽象类和一个 `Sync` 对象。`Sync` 抽象类实现 `AbstractQueuedSynchronizer` 中提供的一些模板方法。为了进一步实现公平锁和非公平锁，又创建了两个子类 `FairSync` 和 `NonfairSync` 去对 `tryAcquire` 方法进行不同的实现。

这里由于 `ReentrantLock` 设计为独占锁的形式，因此对于 AQS 中的 `state` 的使用方法为：
- state=0，则表示没有线程占用锁；state!=0，则表示有线程占用锁。
- 通过 `exclusiveOwnerThread` 与当前线程比较来判断是否是当前线程获取锁。
- 每一次获取锁，使得 state+1，每一次释放锁，使得 state-1，从而实现可重入锁。

Sync 抽象类主要实现了 `tryRelease` 和 `isHeldExclusively` 方法，同时提供了 `nonfairTryAcquire` 方法来作为非公平锁获取锁方法。
```java
abstract static class Sync extends AbstractQueuedSynchronizer {
	// AQS 的父类 AbstractOwnableSynchronizer 中的成员变量，用来表示当前占用锁的线程
	private transient Thread exclusiveOwnerThread; 

	// 1. 如果state=0，则表示可以占用，CAS机制更新state，成功则设置占用锁的线程
	// 2. 如果state!=0，可能是本来就有线程，也可能CAS中有线程，此时判断占用锁的线程是否是自己线程
	// 3. 如果是自己线程，则更新state；否则直接返回false
	@ReservedStackAccess
	final boolean nonfairTryAcquire(int acquires) {
		final Thread current = Thread.currentThread();
		int c = getState();
		if (c == 0) {
			if (compareAndSetState(0, acquires)) {
				setExclusiveOwnerThread(current);
				return true;
			}
		}
		else if (current == getExclusiveOwnerThread()) {
			int nextc = c + acquires;
			if (nextc < 0) // overflow
				throw new Error("Maximum lock count exceeded");
			setState(nextc);
			return true;
		}
		return false;
	}
	
	// 1. 判断当前线程是否是占有锁的线程
	// 2. 计算出释放后 state 值，如果为0，则先将占有锁的线程设置为null，然后再更新state，返回释放成功
	// 3. 如果不为0，直接更新 state，返回false表示仍有线程(自己线程)占用锁
	@ReservedStackAccess
	protected final boolean tryRelease(int releases) {
		int c = getState() - releases;
		if (Thread.currentThread() != getExclusiveOwnerThread())
			throw new IllegalMonitorStateException();
		boolean free = false;
		if (c == 0) {
			free = true;
			setExclusiveOwnerThread(null);
		}
		setState(c);
		return free;
	}

	 // 1. 直接比较站有锁的线程是否是当前线程
	protected final boolean isHeldExclusively() {
		return getExclusiveOwnerThread() == Thread.currentThread();
	}
}
```

### 1.4 NonfairSync 类

`tryAcquire` 方法的实现直接调用父类的 `nonfairTryAcquire` 方法。
```java
static final class NonfairSync extends Sync {
	private static final long serialVersionUID = 7316153563782823691L;
	protected final boolean tryAcquire(int acquires) {
		return nonfairTryAcquire(acquires);
	}
}
```

### 1.5 FairSync 类

公平锁的 `tryAcquire` 方法不同在于，它会先去判断等待锁的线程队列是否是空了。如果没有等待锁的线程，才会去再去尝试获取锁。

```java
static final class FairSync extends Sync {
	private static final long serialVersionUID = -3000897897090466540L;
	@ReservedStackAccess
	protected final boolean tryAcquire(int acquires) {
		final Thread current = Thread.currentThread();
		int c = getState();
		if (c == 0) {
			if (!hasQueuedPredecessors() &&
				compareAndSetState(0, acquires)) {
				setExclusiveOwnerThread(current);
				return true;
			}
		}
		else if (current == getExclusiveOwnerThread()) {
			int nextc = c + acquires;
			if (nextc < 0)
				throw new Error("Maximum lock count exceeded");
			setState(nextc);
			return true;
		}
		return false;
	}
}
```


### 1.6 ReentrantLock vs Synchronized
**相同点：**
1. 两种锁都提供互斥性、内存可见性。
2. 都提供了可重入的加锁语义，都是对应 state 的计数增减来实现的。

**不同点：**
1. `Synchronized` 是 Java 内置锁，依赖于监视器模式。`ReentrantLock` 是依赖于 AQS 类使用 CAS 机制实现的。
2. `Synchronized` 没有 `ReentrantLock` 灵活，程序无法中断一个正在等待获取锁的线程，或者无法在请求获取一个锁的时候无限地等待下去。`ReentrantLock` 支持响应中断、超时、尝试获取锁等高级功能。
3. `Synchronized` 可以自动释放监视器。`ReentrantLock` 需要使用 lock 加锁、unlock 解锁。
4. `Synchronized` 只有非公平锁。`ReentrantLock` 有公平锁和非公平锁两种情况。
5. `Synchronized` 只能关联一个条件队列。`ReentrantLock` 可以关联多个条件队列。

**什么时候用 Synchronized?**
1. 能使用 `Synchronized` 的时候就用 `Synchronized`。
2. `ReentrantLock` 需要显示地解锁，很容易忘记。
3. `Synchronized` 对于 `ReentrantLock` 来说，被许多开发人员所熟悉，并且简洁紧凑。
4. `Synchronized` 属于 Java 内置锁，在编译的时候，可以对锁进行优化，如不必要的时候进行锁消除。后续 JVM 可能仍会去优化内置锁。`ReentrantLock` 属于类库提供的锁，很难有优化的空间。

**什么时候用 ReentrantLock?**
1. 需要高级功能的时候使用 `ReentrantLock`。
2. 支持响应中断的锁、超时获取锁、尝试获取锁
3. 公平锁。

### 1.7 ReentrantLock 的使用

**使用时最好使用 `try-finally` 来进行加锁和解锁。**
`try-finally` 可以防止加锁后出现异常导致无法解锁情况的出现。

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

## 2 Semaphone
`Semaphone` 是信号量，一个支持 `permits` 个资源同时共享的共享锁。内部实现比较简单，是通过 CAS+自旋的方式通过加减法来完成申请、释放资源的。由于 `ReentranLock` 是独占锁，因此要多一个占据锁的线程是否是当前线程的判断，加锁成功了还要设置占据锁的线程，解锁成功了还要设置线程为 null。

### 2.1 大体结构

```java
public class Semaphore implements java.io.Serializable {
	private final Sync sync;
	abstract static class Sync extends AbstractQueuedSynchronizer{ ... } 
	static final class NonfairSync extends Sync { ... }
	static final class FairSync extends Sync { ... }
	
    public Semaphore(int permits) { sync = new NonfairSync(permits);    }
    public Semaphore(int permits, boolean fair) { sync = fair ? new FairSync(permits) : new NonfairSync(permits);    }
    
    public void acquire() throws InterruptedException { sync.acquireSharedInterruptibly(1); }
    public void acquireUninterruptibly() { sync.acquireShared(1); }
    public boolean tryAcquire() { return sync.nonfairTryAcquireShared(1) >= 0; }
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout)); }
    public void release() { sync.releaseShared(1); }
    
    public void acquire(int permits) throws InterruptedException { ... }
    public void acquireUninterruptibly(int permits) { ... }
    public boolean tryAcquire(int permits) { ... }
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException { ... }
    public void release(int permits) { ...  }
    ....
```

### 2.2 Sync 抽象类
由于 `Semaphore` 是共享锁，因此该类与其子类实现了 `tryAcquireShared` 和 `tryReleaseShared` 两个方法。

```java
abstract static class Sync extends AbstractQueuedSynchronizer {
	Sync(int permits) { setState(permits); }
	final int getPermits() { return getState(); }

	// 1. CAS+自旋的方式，通过减法比较，尝试获取一定数目的资源
	// 2. 返回值小于0表示获取失败，大于等于0表示获取成功，代表剩余资源数
	final int nonfairTryAcquireShared(int acquires) {
		for (;;) {
			int available = getState();
			int remaining = available - acquires;
			if (remaining < 0 ||
				compareAndSetState(available, remaining))
				return remaining;
		}
	}

	// 1. CAS + 自旋的机制更新剩余资源数
	protected final boolean tryReleaseShared(int releases) {
		for (;;) {
			int current = getState();
			int next = current + releases;
			if (next < current) // overflow
				throw new Error("Maximum permit count exceeded");
			if (compareAndSetState(current, next))
				return true;
		}
	}

	// 1. CAS+自旋的方式申请一定资源，直到申请成功or出异常
	final void reducePermits(int reductions) {
		for (;;) {
			int current = getState();
			int next = current - reductions;
			if (next > current) // underflow
				throw new Error("Permit count underflow");
			if (compareAndSetState(current, next))
				return;
		}
	}

	// 1. CAS+自旋的方式，申请剩余所有资源，返回能申请到的资源数
	final int drainPermits() {
		for (;;) {
			int current = getState();
			if (current == 0 || compareAndSetState(current, 0))
				return current;
		}
	}
}
```


### 2.3 NonfairSync 类

没有什么补充的。
```java
static final class NonfairSync extends Sync {
	NonfairSync(int permits) { super(permits);   }
	protected int tryAcquireShared(int acquires) { return nonfairTryAcquireShared(acquires); }
}
```

### 2.4 FairSync 类

和 ReentranLock 中的公平锁一样，CAS+自旋过程中，判断是否等待队列为空。等待线程为 0，才会获取锁。
```java
static final class FairSync extends Sync {
	FairSync(int permits) { super(permits);	}
	protected int tryAcquireShared(int acquires) {
		for (;;) {
			if (hasQueuedPredecessors())
				return -1;
			int available = getState();
			int remaining = available - acquires;
			if (remaining < 0 ||
				compareAndSetState(available, remaining))
				return remaining;
		}
	}
}
```

## 3 CountDownLatch

`CountDownLatch` 被称为闭锁，一般用于让多个线程同时等待某个事情的发生。通过构造函数设置需要 count 个线程进行等待，线程通过 countDown 方法去释放锁，调用 await 方法去等待 count 个释放锁的线程。

### 3.1 大体结构
```java
public class CountDownLatch {
    private static final class Sync extends AbstractQueuedSynchronizer { ... }

    private final Sync sync;
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }

    public void await() throws InterruptedException { sync.acquireSharedInterruptibly(1); }
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout)); }
    public void countDown() { sync.releaseShared(1); }
    public long getCount() { return sync.getCount(); }
	...
```

### 3.2 Sync 类

为了实现多个线程同时等待，直到等待线程数大于等于设定的 count 数目，因此对于 AQS 中的 state 的设计为：
- AQS 中的 state 设置为需要等待的线程数
- 当有一个线程等待后，state-1
- 当 state=0 的时候，就达到了等待条件，那么就可以直接释放锁了
- `countDown` 方法对应释放锁的操作，让 state-1
- `await` 方法对应申请锁的操作，判断 state=0.

由于多个线程等待、等待成功后多个线程同时运行，因此设计为共享锁，实现 `tryAcquireShared` 和 `tryReleaseShared` 方法。

```java
private static final class Sync extends AbstractQueuedSynchronizer {
	Sync(int count) { setState(count); }
	int getCount() { return getState(); }
	protected int tryAcquireShared(int acquires) { return (getState() == 0) ? 1 : -1; }
	protected boolean tryReleaseShared(int releases) {
		for (;;) {
			int c = getState();
			if (c == 0)
				return false;
			int nextc = c - 1;
			if (compareAndSetState(c, nextc))
				return nextc == 0;
		}
	}
}
```

## 4 CyclicBarrier

## 5 ReentrantReadWriteLock