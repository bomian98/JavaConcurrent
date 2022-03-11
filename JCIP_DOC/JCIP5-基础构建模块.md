
## 1 同步容器类

同步容器类都是线程安全的, 但在某些情况下可能需要额外的客户端加锁来保护复合操作。容器上常见的复合操作包括：**迭代**(反复访问元素, 直到遍历完容器中所有元素)、**跳转**(根据指定顺序找到当前元素的下一个元素)以及**条件运算**, 例如“若没有则添加”。

下面主要讨论迭代器的问题。

### 1.1 迭代器

无论在直接迭代还是在 Java 5.0 引入的 for-each 循环语法中, 对容器类进行迭代的标准方式都是使用 Iterator。然而, 如果有其他线程并发地修改容器, 那么即使是使用迭代器也无法避免在迭代期间对容器加锁。在设计同步容器类的迭代器时并没有考虑到并发修改的问题, 并且它们表现出的行为是“快速失败”(fail-fast)的。这意味着, 当它们发现容器在迭代过程中被修改时, 就会抛出一个 ConcurrentModificationException 异常。

> 快速失败（fail—fast）：
> 在用迭代器遍历一个集合对象时，如果遍历过程中对集合对象的内容进行了修改（增加、删除、修改），则会抛出 Concurrent Modification Exception。
> 
> 安全失败（fail—safe）：
> 采用安全失败机制的集合容器，在遍历时不是直接在集合内容上访问的，而是先复制原有集合内容，在拷贝的集合上进行遍历。

如果保证迭代时不出错，(1)加锁，但是加锁会导致长期占用锁资源，导致饥饿。(2)克隆容器，但是消耗时间。

集合容器的 toString、hashCode、equals、containsAll、removeAll、retainAll 等方法都会进行隐藏迭代, 因此需要注意出现 ConcurrentModificationException 异常。

## 2 并发容器类
### 2.1 ConcurrentHashMap

Concurrent-HashMap, 用来替代同步且基于散列的 Map。

> Java 6 也引入了 Concurrent-SkipListMap 和 ConcurrentSkipListSet, 分别作为同步的 SortedMap 和 SortedSet 的并发替代品(例如用 synchronizedMap 包装的 TreeMap 或 TreeSet)。

**ConcurrentHashMap 迭代器**

ConcurrentHashMap 与其他并发容器一起增强了同步容器类：它们提供的迭代器不会抛出 ConcurrentModificationException, 因此不需要在迭代过程中对容器加锁。ConcurrentHashMap 返回的迭代器具有弱一致性(Weakly Consistent), 而并非“及时失败”。弱一致性的迭代器可以容忍并发的修改, 当创建迭代器时会遍历已有的元素, 并可以(但是不保证)在迭代器被构造后将修改操作反映给容器。

Iterator 对象的使用，不一定是和其它更新线程同步，获得的对象可能是更新前的对象，ConcurrentHashMap 允许一边更新、一边遍历，也就是说在 Iterator 对象遍历的时候，ConcurrentHashMap 也可以进行 remove,put 操作，且遍历的数据会随着 remove,put 操作产出变化，所以希望遍历到当前全部数据的话，要么以 ConcurrentHashMap 变量为锁进行同步(synchronized 该变量)，要么使用 CopiedIterator 包装 iterator，使其拷贝当前集合的全部数据，但是这样生成的 iterator 不可以进行 remove 操作。

keySet 返回的 iterator 是弱一直和 fail-safe 的，可能不会返回某些最近的改变，并且在遍历中，如果已经遍历的数组上的内容发生了变化，是不会抛出 ConcurrentModificationException 的异常。

需要注意的是，并发程序下，Map 上的 size 和 isEmpty 等方法可能存在结果过期的情况。但一般来说，这些方法在并发场景下用处也不是很大，因为总是在变化。

**与 HashTable 区别(迭代器方面)**

Hashtable 在使用 iterator 遍历的时候，如果其他线程，包括本线程对 Hashtable 进行了 put，remove 等更新操作的话，就会抛出 ConcurrentModificationException 异常，但如果使用 ConcurrentHashMap 的话，就不用考虑这方面的问题了。

**ConcurrentHashMap 新增原子操作**

```java
public interface ConcurrentMap < K,  V > extends Map < K,  V > {
	//仅当K没有相应的映射值时才插入
	V putIfAbsent(K key,  V value);
	//仅当K被映射到V时才移除
	boolean remove(K key,  V value);
	//仅当K被映射到oldValue时才替换为newValue
	boolean replace(K key,  V oldValue,  V newValue);
	//仅当K被映射到某个值时才替换为newValue
	V replace(K key,  V newValue);
}
```

### 2.2 CopyOnWriteArrayList
CopyOnWriteArrayList, 用于在遍历操作为主要操作的情况下代替同步的 List。

> 类似地, CopyOnWriteArraySet 的作用是替代同步 Set。

**迭代器原理**

CopyOnWriteArrayList 在迭代期间不需要对容器进行加锁或复制。其原因在于，对于 list 的每次修改时，都会进行一次数组的复制与更新数组引用。

list 最开始指向数组 A 的位置。此时，创建了一个迭代器，指向数组 A 的位置。修改 list 时，对数组 A 进行深拷贝与相应修改得到数组 B，将 list 的引用指向数组 B。如此修改后，数组 A 内部数据是没有发生变化的，而之前创建的迭代器也和数组 A 进行绑定，因此对 list 修改操作并不会影响到迭代器。

**修改数组 & 迭代器的底层源码**

```java
final transient Object lock = new Object();
public boolean add(E e) {
	synchronized (lock) {
		Object[] es = getArray();
		int len = es.length;
		es = Arrays.copyOf(es,  len + 1);
		es[len] = e;
		setArray(es);
		return true;
	}
}

public Iterator < E >  iterator() {  
    return new COWIterator < E > (getArray(),  0);  
}
```

### 2.3 BlockingQueue

BlockingQueue 简化了生产者-消费者设计的实现过程, 它支持任意数量的生产者和消费者。一种最常见的生产者-消费者设计模式就是线程池与工作队列的组合, 在 Executor 任务执行框架中就体现了这种模式, 这也是第 6 章和第 8 章的主题。

当阻塞队列满时, put 会阻塞, offer 会返回失败状态。根据需求灵活使用。

BlockingQueue 的多种实现, 包含 LinkedBlockingQueue 、 ArrayBlockingQueue 、PriorityBlockingQueue、SynchronousQueue。

**SynchronousQueue**

最后一个 BlockingQueue 实现是 SynchronousQueue, 实际上它不是一个真正的队列, 因为它不会为队列中元素维护存储空间。与其他队列不同的是, 它维护一组线程, 这些线程在等待着把元素加入或移出队列。如果以洗盘子的比喻为例, 那么这就相当于没有盘架, 而是将洗好的盘子直接放入下一个空闲的烘干机中。这种实现队列的方式看似很奇怪, 但由于可以直接交付工作, 从而降低了将数据从生产者移动到消费者的延迟。

直接交付方式还会将更多关于任务状态的信息反馈给生产者。当交付被接受时, 它就知道消费者已经得到了任务, 而不是简单地把任务放入一个队列——这种区别就好比将文件直接交给同事, 还是将文件放到她的邮箱中并希望她能尽快拿到文件。因为 SynchronousQueue 没有存储功能, 因此 put 和 take 会一直阻塞, 直到有另一个线程已经准备好参与到交付过程中。仅当有足够多的消费者, 并且总是有一个消费者准备好获取交付的工作时, 才适合使用同步队列。

**串行线程封闭**

对于可变对象, 生产者-消费者这种设计与阻塞队列一起, 促进了串行线程封闭, 从而将对象所有权从生产者交付给消费者。线程封闭对象只能由单个线程拥有, 但可以通过安全地发布该对象来“转移”所有权。

### 2.4 BlockingDeque

双端队列适用于工作密取(Work Stealing)。在这种设计中，每个消费者都有各自的双端队列。如果一个消费者完成了自己双端队列中的全部工作, 那么它可以从其他消费者双端队列末尾秘密地获取工作。

密取工作模式比传统的生产者-消费者模式具有更高的可伸缩性，发生竞争可能性更低。Before，多个消费者等待一个队列；After，每个消费者使用自己的队列，偶尔访问他人的队列，而且访问他人的队列也是从尾部而非头部，竞争 down。

工作密切适合既是消费者又是生产者的问题，执行时可能出现更多工作。例如，扫描文件目录的情况。一个线程在扫描 A 目录时，该目录下还存在着 A1, A2, A3 ... 等子目录。这些子目录也需要扫描，因此将其加入到自己队列的尾部。其他线程如果执行完了，那么可以去另一个线程队列的尾部查找新任务，从而保证每个线程都处于忙碌状态。

> 之前考虑过，队列的消费者如果也生产数据，将其加入到自己的队列中，那么可能存在自己队列满了，导致无法将数据放入队列中，进而导致阻塞。但由于消费者阻塞，那么该队列的数据将无法消费，从而死锁。
> 工作密取应该算是极大的降低了这种情况的发生。其他队列工作完成后，去周围偷点数据，那么被阻塞的队列就可以恢复正常运行了？那么锁的情况呢？

LinkedBlockingDeque 源码，ReentrantLock 不了解，之后再回看这个问题。

```java
public class LinkedBlockingDeque<E>  
    extends AbstractQueue<E>  
    implements BlockingDeque<E>, java.io.Serializable {
    public boolean offerFirst(E e) {
        if (e == null) throw new NullPointerException();
        Node<E> node = new Node<E>(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return linkFirst(node);
        } finally {
            lock.unlock();
        }
    }
}
```

## 3 同步工具类

所有的同步工具类都包含一些特定的结构化属性：它们封装了一些状态, 这些状态将决定执行同步工具类的线程是继续执行还是等待, 此外还提供了一些方法对状态进行操作, 以及另一些方法用于高效地等待同步工具类进入到预期状态。

### 3.1 闭锁

闭锁是一种同步工具类, 可以延迟线程的进度直到其到达终止状态。闭锁的作用相当于一扇门：在闭锁到达结束状态之前, 这扇门一直是关闭的, 并且没有任何线程能通过, 当到达结束状态时, 这扇门会打开并允许所有的线程通过。当闭锁到达结束状态后, 将不会再改变状态, 因此这扇门将永远保持打开状态。

闭锁可以用来确保某些活动直到其他活动都完成后才继续执行, 例如：
- 确保某个计算在其需要的所有资源都被初始化之后才继续执行。二元闭锁(包括两个状态)可以用来表示“资源 R 已经被初始化”, 而所有需要 R 的操作都必须先在这个闭锁上等待。
- 确保某个服务在其依赖的所有其他服务都已经启动之后才启动。每个服务都有一个相关的二元闭锁。当启动服务 S 时, 将首先在 S 依赖的其他服务的闭锁上等待, 在所有依赖的服务都启动后会释放闭锁 S, 这样其他依赖 S 的服务才能继续执行。
- 等待直到某个操作的所有参与者(例如, 在多玩家游戏中的所有玩家)都就绪再继续执行。在这种情况中, 当所有玩家都准备就绪时, 闭锁将到达结束状态。

> 1 - 和第二个差不多？
> 2 - 开始时，闭锁=所依赖服务数。每个依赖的服务启动后，countDown。服务 S await 闭锁。
> 3 - 开始时，闭锁=nPlayer。每个 player 准备好后，countDown。每个 player await 闭锁。

CountDownLatch 是一种灵活的闭锁实现, 可以在上述各种情况中使用, 它可以使一个或多个线程等待一组事件发生。闭锁状态包括一个计数器, 该计数器被初始化为一个正数, 表示需要等待的事件数量。

countDown 方法递减计数器, 表示有一个事件已经发生了, 而 await 方法等待计数器达到零, 这表示所有需要等待的事件都已经发生。如果计数器的值非零, 那么 await 会一直阻塞直到计数器为零, 或者等待中的线程中断, 或者等待超时。

获得所有线程执行所需要的时间，可以使用闭锁，操作如下：
1. 起始门。cnt = 1，所有线程启动后 await，主线程 countDown 来使所有线程一起开始执行任务。
2. 结束门。cnt = nthread，线程执行完后 countDown，主线程通过 await 来监测所有线程全部完成的最终时间。

```java
public class TestHarness{
	public long timeTasks(int nThreads,  final Runnable task) throws InterruptedException{
		final CountDownLatch startGate=new CountDownLatch(1);
		final CountDownLatch endGate=new CountDownLatch(nThreads);
		for(int i=0;i < nThreads;i++){
			Thread t=new Thread(){
				public void run(){
					try{
						startGate.await();
						try{
							task.run();
						}finally{
							endGate.countDown();
						}
					}catch(InterruptedException ignored){
					}
				}
			};
			t.start();
		}
		long start=System.nanoTime();
		startGate.countDown();
		endGate.await();
		long end=System.nanoTime();
		return end-start;
	}
}
```

### 3.2 FutureTask

**FutureTask 概念**

FutureTask 也可以用做闭锁。(FutureTask 实现了 Future 语义, 表示一种抽象的可生成结果的计算)。FutureTask 表示的计算是通过 Callable 来实现的, 相当于一种可生成结果的 Runnable, 并且可以处于以下 3 种状态：等待运行(Waiting to run), 正在运行(Running)和运行完成(Completed)。“执行完成”表示计算的所有可能结束方式, 包括正常结束、由于取消而结束和由于异常而结束等。当 FutureTask 进入完成状态后, 它会永远停止在这个状态上。

> 之前尝试创建 Thread 执行 FutureTask，使用 get 获取结果。再创建 Thread 传入同一个 FutureTask，该 Thread 并不执行(没有 log 打印)。

**FutureTask 使用**

Future.get 的行为取决于任务的状态。如果任务已经完成, 那么 get 会立即返回结果, 否则 get 将阻塞直到任务进入完成状态, 然后返回结果或者抛出异常。FutureTask 将计算结果从执行计算的线程传递到获取这个结果的线程, 而 FutureTask 的规范确保了这种传递过程能实现结果的安全发布。

FutureTask 在 Executor 框架中表示异步任务, 此外还可以用来表示一些时间较长的计算, 这些计算可以在使用计算结果之前启动。

**FutureTask 异常**

Callable 表示的任务可以抛出受检查的或未受检查的异常, 并且任何代码都可能抛出一个 Error。无论任务代码抛出什么异常, 都会被封装到一个 ExecutionException 中, 并在 Future.get 中被重新抛出。这将使调用 get 的代码变得复杂, 因为它不仅需要处理可能出现的 ExecutionException(以及未检查的 CancellationException), 而且还由于 ExecutionException 是作为一个 Throwable 类返回的, 因此处理起来并不容易。

使用 instanceof 来判断是什么类型的异常, 然后进行强制类型转换与后续的处理。

### 3.3 信号量

计数信号量(Counting Semaphore)用来控制同时访问某个特定资源的操作数量, 或者同时执行某个指定操作的数量。计数信号量还可以用来实现某种资源池, 或者对容器施加边界。

**Semaphore 使用**

**Semaphore** 中管理着一组虚拟的许可(permit), 许可的初始数量可通过构造函数来指定。在执行操作时可以首先获得许可(只要还有剩余的许可), 并在使用以后释放许可。

如果没有许可, 那么 **acquire** 将阻塞直到有许可(或者直到被中断或者操作超时)。**release** 方法将返回一个许可给信号量。计算信号量的一种简化形式是二值信号量, 即初始值为 1 的 Semaphore。二值信号量可以用做互斥体(mutex), 并具备不可重入的加锁语义：谁拥有这个唯一的许可, 谁就拥有了互斥锁。

**Semaphore 应用**

实现资源池，例如数据库连接池。我们可以构造一个固定长度的资源池, 当池为空时阻塞，并且当池非空时解除阻塞。在第 12 章的有界缓冲类中将使用这项技术。(在构造阻塞对象池时, 一种更简单的方法是使用 BlockingQueue 来保存池的资源。)

将任何一种容器变成有界阻塞容器。实现方法为，添加元素时，先 acquire。如果最后没有添加成功，则释放 permit；反之不需要释放 permit。删除元素时，若删除元素成功，则释放 permit。

```java
public class BoundedHashSet < T > {
	private final Set < T > set;
	private final Semaphore sem;
	public BoundedHashSet(int bound){
		this.set=Collections.synchronizedSet(new HashSet < T > ());
		sem=new Semaphore(bound);
	}
	public boolean add(T o)throws InterruptedException{
		sem.acquire(); // 请求占用一个位置
		boolean wasAdded=false;
		try{
			wasAdded=set.add(o); // 里面没有，不释放
			return wasAdded;
		}finally{
			if(!wasAdded) sem.release();  // 里面有，则释放一个数量
		}
	}
	public boolean remove(Object o){
		boolean wasRemoved=set.remove(o);
		if(wasRemoved) sem.release();
		return wasRemoved;
	}
}
```

### 3.4 栅栏

闭锁是一次性对象, 一旦进入终止状态, 就不能被重置。栅栏(Barrier)类似于闭锁, 它能阻塞一组线程直到某个事件发生。

栅栏与闭锁的关键区别在于, 所有线程必须同时到达栅栏位置, 才能继续执行。闭锁用于等待事件, 而栅栏用于等待其他线程。栅栏用于实现一些协议, 例如几个家庭决定在某个地方集合：“所有人 6：00 在麦当劳碰头, 到了以后要等其他人, 之后再讨论下一步要做的事情。”

> 闭锁：有五个人，一个裁判。这五个人同时跑，裁判开始计时，五个人都到终点了，裁判喊停，然后统计这五个人从开始跑到最后一个撞线用了多长时间。
> 
> 栅栏：还是这五个人，这次没裁判。规定五个人只要都跑到终点了，大家可以喝啤酒。但是，只要有一个人没到终点，就不能喝。这里也没有要求大家要同时起跑(当然也可以，加 latch)。

**CyclicBarrier**

 >  CyclicBarrier 可以使一定数量的参与方反复地在栅栏位置汇集, 它在并行迭代算法中非常有用：这种算法通常将一个问题拆分成一系列相互独立的子问题。当线程到达栅栏位置时将调用 await 方法, 这个方法将阻塞直到所有线程都到达栅栏位置。如果所有线程都到达了栅栏位置, 那么栅栏将打开, 此时所有线程都被释放, 而栅栏将被重置以便下次使用。如果对 await 的调用超时, 或者 await 阻塞的线程被中断, 那么栅栏就被认为是打破了, 所有阻塞的 await 调用都将终止并抛出 BrokenBarrierException。如果成功地通过栅栏, 那么 await 将为每个线程返回一个唯一的到达索引号, 我们可以利用这些索引来“选举”产生一个领导线程, 并在下一次迭代中由该领导线程执行一些特殊的工作。CyclicBarrier 还可以使你将一个栅栏操作传递给构造函数, 这是一个 Runnable, 当成功通过栅栏时会(在一个子任务线程中)执行它, 但在阻塞线程被释放之前是不能执行的。

 >  在模拟程序中通常需要使用栅栏, 例如某个步骤中的计算可以并行执行, 但必须等到该步骤中的所有计算都执行完毕才能进入下一个步骤。例如, 在 n-body 粒子模拟系统中, 每个步骤都根据其他粒子的位置和属性来计算各个粒子的新位置。通过在每两次更新之间等待栅栏, 能够确保在第 k 步中的所有更新操作都已经计算完毕, 才进入第 k+l 步。

```java
// 两个参数
// 一个是初始值多少，需要多少个thread执行await操作，才能打开
// 一个是当栅栏被打开时执行的命令，如果没有动作则为 null
public CyclicBarrier(int parties, Runnable barrierAction) {  
    if (parties <= 0) throw new IllegalArgumentException();  
	 this.parties = parties;  
	 this.count = parties;  
	 this.barrierCommand = barrierAction;  
}
```

**Exchanger**

另一种形式的栅栏是 Exchanger，它是一种两方（Two-Party）栅栏，各方在栅栏位置上交换数据。当两方执行不对称的操作时，Exchanger 会非常有用，例如当一个线程向缓冲区写入数据，而另一个线程从缓冲区中读取数据。这些线程可以使用 Exchanger 来汇合，并将满的缓冲区与空的缓冲区交换。当两个线程通过 Exchanger 交换对象时，这种交换就把这两个对象安全地发布给另一方。数据交换的时机取决于应用程序的响应需求。最简单的方案是，当缓冲区被填满时，由填充任务进行交换，当缓冲区为空时，由清空任务进行交换。这样会把需要交换的次数降至最低，但如果新数据的到达率不可预测，那么一些数据的处理过程就将延迟。另一个方法是，不仅当缓冲被填满时进行交换，并且当缓冲被填充到一定程度并保持一定时间后，也进行交换。

## 4 构建高效且可伸缩的结果缓存

目的：开发一个高效且可伸缩的缓存，将一些非常耗时操作(比如 1 小时才能运行完)的结果缓存下来，同时尽可能地减少计算次数和等待时间。

提升步骤：
1. HashMap 来存储缓存数据。对于计算操作使用 Synchronized 来加锁。计算操作先判断缓存中是否存在，不存在则计算并加入缓存。【这种显然不好，为了保证 HashMap 不会同时访问，使用对计算操作进行加锁。导致一整个计算操作都在一个锁中了，锁占用了太长时间】
2. ConcurrentHashMap 来存储缓存数据。计算操作不加锁，若缓存中不存在，则计算后 put 到缓存中。【相较于方法 1，避免了锁占用太长时间。若缓存中不存在 key=1 对应的 value，那么一个线程在 compute(1)的时候，另一个线程也会 compute(1)。即对应 value 没有计算出来时，其他线程也会去计算这个数据，导致重复计算。】
3. ConcurrentHashMap 来存储缓存 FutureTask 对象。计算操作不加锁，若缓存中不存在 key，则生成 FutureTask 对象加入其中，然后 compute。其他线程发现存在 FutureTask，则使用 get 方法获取数据。若计算完成直接获取，没有计算完成先阻塞。【使用 FutureTask 对象避免了重复计算的问题，但是编写代码时很容易忘记，先检查后执行是属于竞态条件。】
4. 在上述的基础上，使用 putIfAbsent 函数，避免了竞态条件。不过，官方上使用了 while 循环，还没有了解是什么作用。可能是防止计算失败，如中断等情况(?)，若计算失败，则删除该 FutureTask，然后重新加入新的 FutureTask。

> 当缓存的是 Future 而不是值时，将导致缓存污染（Cache Pollution）问题：如果某个计算被取消或者失败，那么在计算这个结果时将指明计算过程被取消或者失败。为了避免这种情况，如果 Memoizer 发现计算被取消，那么将把 Future 从缓存中移除。如果检测到 RuntimeException，那么也会移除 Future，这样将来的计算才可能成功。
> 
> Memoizer 同样没有解决缓存逾期的问题，但它可以通过使用 FutureTask 的子类来解决，在子类中为每个结果指定一个逾期时间，并定期扫描缓存中逾期的元素。（同样，它也没有解决缓存清理的问题，即移除旧的计算结果以便为新的计算结果腾出空间，从而使缓存不会消耗过多的内存。）

```java
public class Memoizer <A, V> implements Computable<A, V> {
    private final ConcurrentMap<A, Future<V>> cache  = new ConcurrentHashMap<A, Future<V>>();
    private final Computable<A, V> c;

    public Memoizer(Computable<A, V> c) {
        this.c = c;
    }

    public V compute(final A arg) throws InterruptedException {
        while (true) {
            Future<V> f = cache.get(arg);
            if (f == null) {
                Callable<V> eval = new Callable<V>() {
                    public V call() throws InterruptedException {
                        return c.compute(arg);
                    }
                };
                FutureTask<V> ft = new FutureTask<V>(eval);
                f = cache.putIfAbsent(arg, ft);
                if (f == null) {
                    f = ft;
                    ft.run();
                }
            }
            try {
                return f.get();
            } catch (CancellationException e) {
                cache.remove(arg, f);
            } catch (ExecutionException e) {
                throw LaunderThrowable.launderThrowable(e.getCause());
            }
        }
    }
}
```

## 5 第一部分总结

> 到目前为止，我们已经介绍了许多基础知识。下面这个“并发技巧清单”列举了在第一部分中介绍的主要概念和规则。
> - 可变状态是至关重要的（It’s the mutable state, stupid）。
	所有的并发问题都可以归结为如何协调对并发状态的访问。可变状态越少，就越容易确保线程安全性。
> - 尽量将域声明为 final 类型，除非需要它们是可变的。
> - 不可变对象一定是线程安全的。
	不可变对象能极大地降低并发编程的复杂性。它们更为简单而且安全，可以任意共享而无须使用加锁或保护性复制等机制。
> - 封装有助于管理复杂性。
	在编写线程安全的程序时，虽然可以将所有数据都保存在全局变量中，但为什么要这样做？将数据封装在对象中，更易于维持不变性条件：将同步机制封装在对象中，更易于遵循同步策略。
> - 用锁来保护每个可变变量。
> - 当保护同一个不变性条件中的所有变量时，要使用同一个锁。
> - 在执行复合操作期间，要持有锁。
> - 如果从多个线程中访问同一个可变变量时没有同步机制，那么程序会出现问题。
> - 不要故作聪明地推断出不需要使用同步。
> - 在设计过程中考虑线程安全，或者在文档中明确地指出它不是线程安全的。
> - 将同步策略文档化。