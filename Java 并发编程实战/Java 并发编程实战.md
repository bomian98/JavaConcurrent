---
number headings: 1
---

## 第 2 章 - 线程安全性

 >  要编写线程安全的代码, 其核心在于要对状态访问操作进行管理, 特别是对共享的(Shared)和可变的(Mutable)状态的访问。

 >  “共享”意味着变量可以由多个线程同时访问, 而“可变”则意味着变量的值在其生命周期内可以发生变化。

 > 线程安全性的实现：
 > 
 > 不在线程之间共享该状态变量。(ThreadLocal 或方法的局部变量)
 > 
 > 将状态变量修改为不可变的变量。
 > 
 > 在访问状态变量时使用同步。

 > 编写并发应用程序时, 一种正确的编程方法就是：首先使代码正确运行, 然后再提高代码的速度。
 > 即便如此, 最好也只是当性能测试结果和应用需求告诉你必须提高性能, 以及测量结果表明这种优化在实际环境中确实能带来性能提升时, 才进行优化。

确实, 之前在做选排课系统时, 我了解操作 Redis 是直接操作内存, 相较于操作 MySQL 会更快一些。但是, 我并没有实际的去测试使用 Redis 前后性能提升有多少。

 > 线程安全的程序是否完全由线程安全类构成？答案是否定的, 完全由线程安全类构成的程序并不一定就是线程安全的, 而在线程安全类中也可以包含非线程安全的类。

例如：判断在 ConcurrentHashMap 中是否存在某个 key, 不存在, 则插入数据。这就是不安全的指令, 属于竞态条件的一种。

### 2.1 什么是线程安全性

 >  当多个线程访问某个类时, 这个类始终都能表现出正确的行为, 那么就称这个类是线程安全的。

 >  当多个线程访问某个类时, 不管运行时环境采用何种调度方式或者这些线程将如何交替执行, 并且在主调代码中不需要任何额外的同步或协同, 这个类都能表现出正确的行为, 那么就称这个类是线程安全的。

 >  无状态对象一定是线程安全的。

 >  大多数 Servlet 都是无状态的, 从而极大地降低了在实现 Servlet 线程安全性时的复杂性。只有当 Servlet 在处理请求时需要保存一些信息, 线程安全性才会成为一个问题。

### 2.2 原子性

 > 虽然递增操作++count 是一种紧凑的语法, 使其看上去只是一个操作, 但这个操作并非原子的, 因而它并不会作为一个不可分割的操作来执行。实际上, 它包含了三个独立的操作：读取 count 的值, 将值加 1, 然后将计算结果写入 count。这是一个“读取-修改-写入”的操作序列, 并且其结果状态依赖于之前的状态。

自增操作不是一个原子操作！！

 >  在并发编程中, 这种由于不恰当的执行时序而出现不正确的结果是一种非常重要的情况, 它有一个正式的名字：竞态条件(Race Condition)。

#### 2.2.1 竞态条件

 >  最常见的竞态条件类型就是“先检查后执行(Check-Then-Act)”操作, 即通过一个可能失效的观测结果来决定下一步的动作。

#### 2.2.2 延迟初始化中的竞态条件

 > 使用“先检查后执行”的一种常见情况就是延迟初始化。延迟初始化的目的是将对象的初始化操作推迟到实际被使用时才进行, 同时要确保只被初始化一次。

懒加载的单例模式, 线程不安全。
可能存在两个线程都判空, 并都创建了对象、赋值和返回, 从而导致使用了地址不同的对象。

#### 2.2.3 复合操作

 >  在 java.util.concurrent.atomic 包中包含了一些原子变量类, 用于实现在数值和对象引用上的原子状态转换。

AtomicInteger 的自增操作调用了下面的函数。
``` Java
@HotSpotIntrinsicCandidate
public final int getAndSetInt(Object o,  long offset,  int newValue) {
	int v;
	do {
		v = getIntVolatile(o,  offset);
	} while (!weakCompareAndSetInt(o,  offset,  v,  newValue));
	return v;
}
```

### 2.3 加锁机制

 >  要保持状态的一致性, 就需要在单个原子操作中更新所有相关的状态变量。

Redis 同理, 如果某个操作需要更新两个数据, 那么调用两次 Redis 的 API 是不行的。我当前使用的是借助 Lua 来操作

#### 2.3.1 内置锁

 > Java 提供了一种内置的锁机制来支持原子性：同步代码块(Synchronized Block)。(第 3 章将介绍加锁机制以及其他同步机制的另一个重要方面：可见性)
 > 
 > 同步代码块包括两部分：一个作为锁的对象引用, 一个作为由这个锁保护的代码块。
 > 
 > 以关键字 synchronized 来修饰的方法就是一种横跨整个方法体的同步代码块, 其中该同步代码块的锁就是方法调用所在的对象。静态的 synchronized 方法以 Class 对象作为锁。

锁住方法：
1 - 普通方法, 锁住的是当前执行该方法的实例对象，等价于 synchronized(this){...}
2 - 静态方法, 锁住的是类对象，等价于 synchronized(xx.class){...}

#### 2.3.2 重入

 >  当某个线程请求一个由其他线程持有的锁时, 发出请求的线程就会阻塞。然而, 由于内置锁是可重入的, 因此如果某个线程试图获得一个已经由它自己持有的锁, 那么这个请求就会成功。“重入”意味着获取锁的操作的粒度是“线程”, 而不是“调用”

 >  重入的一种实现方法是, 为每个锁关联一个获取计数值和一个所有者线程。
 >  当计数值为 0 时, 这个锁就被认为是没有被任何线程持有。当线程请求一个未被持有的锁时, JVM 将记下锁的持有者, 并且将获取计数值置为 1。
 >  如果同一个线程再次获取这个锁, 计数值将递增, 而当线程退出同步代码块时, 计数器会相应地递减。当计数值为 0 时, 这个锁将被释放。

 >  重入进一步提升了加锁行为的封装性, 因此简化了面向对象并发代码的开发。在程序清单 2-7 (P21)的代码中, 子类改写了父类的 synchronized 方法, 然后调用父类中的方法, 此时如果没有可重入的锁, 那么这段代码将产生死锁。由于 Widget 和 LoggingWidget 中 doSomething 方法都是 synchronized 方法, 因此每个 doSomething 方法在执行前都会获取 Widget 上的锁。然而, 如果内置锁不是可重入的, 那么在调用 super.doSomething 时将无法获得 Widget 上的锁, 因为这个锁已经被持有, 从而线程将永远停顿下去, 等待一个永远也无法获得的锁。重入则避免了这种死锁情况的发生。

### 2.4 用锁来保护状态

 >  一种常见的错误是认为, 只有在写入共享变量时才需要使用同步, 然而事实并非如此(3.1 节将进一步解释其中的原因)。

 >  并非所有数据都需要锁的保护, 只有被多个线程同时访问的可变数据才需要通过锁来保护。

### 2.5 活跃性与性能

 >  当执行时间较长的计算或者可能无法快速完成的操作时(例如, 网络 I/O 或控制台 I/O), 一定不要持有锁。

## 第 3 章 - 对象的共享

 >  同步还有另一个重要的方面：内存可见性(Memory Visibility)。我们不仅希望防止某个线程正在使用对象状态而另一个线程在同时修改该状态, 而且希望确保当一个线程修改了对象状态后, 其他线程能够看到发生的状态变化。

### 3.1 可见性

 > 在程序清单 3-1 (P27)中的 [NoVisibility.java](https://jcip.net/listings/NoVisibility.java) 说明了当多个线程在没有同步的情况下共享数据时出现的错误。
 > 在代码中, 主线程和读线程都将访问共享变量 ready 和 number。主线程启动读线程, 然后将 number 设为 42, 并将 ready 设为 true。读线程一直循环直到发现 ready 的值变为 true, 然后输出 number 的值。虽然 NoVisibility 看起来会输出 42, 但事实上很可能输出 0, 或者根本无法终止。这是因为在代码中没有使用足够的同步机制, 因此无法保证主线程写入的 ready 值和 number 值对于读线程来说是可见的。

Java 内存模型(JMM) 中可以概括为三种对象：线程、线程的局部变量池、主内存。
线程从主内存读取数据后, 会将其放到线程的局部变量池中(如寄存器等), 之后直接更改, 更改后地数据可能不会再放到主内存中。因此两个线程之间存在同一个数据不同值的可能性。

#### 3.1.1 失效数据

 > 程序清单 3-2 中的 Mutablelnteger 不是线程安全的, 因为 get 和 set 都是在没有同步的情况下访问 value 的。与其他问题相比, 失效值问题更容易出现：如果某个线程调用了 set, 那么另一个正在调用 get 的线程可能会看到更新后的 value 值, 也可能看不到。

方法 1 需要对 get 和 set 方法加上 synchronized
方法 2 对 value 变量增加 volatile 修饰符

#### 3.1.2 非原子的 64 位操作

 >  当线程在没有同步的情况下读取变量时, 可能会得到一个失效值, 但至少这个值是由之前某个线程设置的值, 而不是一个随机值。这种安全性保证也被称为最低安全性。

 >  最低安全性适用于绝大多数变量, 但是存在一个例外：非 volatile 类型的 64 位数值变量(double 和 long, 请参见 3.1.4 节)。
 >  
 >  Java 内存模型要求, 变量的读取操作和写入操作都必须是原子操作, 但对于非 volatile 类型的 long 和 double 变量, JVM 允许将 64 位的读操作或写操作分解为两个 32 位的操作。当读取一个非 volatile 类型的 long 变量时, 如果对该变量的读操作和写操作在不同的线程中执行, 那么很可能会读取到某个值的高 32 位和另一个值的低 32 位。

#### 3.1.3 加锁与可见性

 >  加锁的含义不仅仅局限于互斥行为, 还包括内存可见性。为了确保所有线程都能看到共享变量的最新值, 所有执行读操作或者写操作的线程都必须在同一个锁上同步。

#### 3.1.4 Volatile 变量

 >  Java 语言提供了一种稍弱的同步机制, 即 volatile 变量, 用来确保将变量的更新操作通知到其他线程。当把变量声明为 volatile 类型后, 编译器与运行时都会注意到这个变量是共享的, 因此不会将该变量上的操作与其他内存操作一起重排序。volatile 变量不会被缓存在寄存器或者对其他处理器不可见的地方, 因此在读取 volatile 类型的变量时总会返回最新写入的值。

 >  在访问 volatile 变量时不会执行加锁操作, 因此也就不会使执行线程阻塞, 因此 volatile 变量是一种比 sychronized 关键字更轻量级的同步机制。

 >  然而, 我们并不建议过度依赖 volatile 变量提供的可见性。如果在代码中依赖 volatile 变量来控制状态的可见性, 通常比使用锁的代码更脆弱, 也更难以理解。

 >  仅当 volatile 变量能简化代码的实现以及对同步策略的验证时, 才应该使用它们。如果在验证正确性时需要对可见性进行复杂的判断, 那么就不要使用 volatile 变量。volatile 变量的正确使用方式包括：确保它们自身状态的可见性, 确保它们所引用对象的状态的可见性, 以及标识一些重要的程序生命周期事件的发生(例如, 初始化或关闭)

 >  volatile 变量通常用做某个操作完成、发生中断或者状态的标志, 

 >  volatile 的语义不足以确保递增操作(count++)的原子性, 除非你能确保只有一个线程对变量执行写操作。

只能保证变量的可见性, 不能保证变量修改时的原子性。

 >  加锁机制既可以确保可见性又可以确保原子性, 而 volatile 变量只能确保可见性。

 >  当且仅当满足以下所有条件时, 才应该使用 volatile 变量：
 >  
 >  - 对变量的写入操作不依赖变量的当前值, 或者你能确保只有单个线程更新变量的值。
 >  - 该变量不会与其他状态变量一起纳入不变性条件中。
 >  - 在访问变量时不需要加锁。

 >  在当前大多数处理器架构上, 读取 volatile 变量的开销只比读取非 volatile 变量的开销略高一些。

 >  调试小提示：对于服务器应用程序, 无论在开发阶段还是在测试阶段, 当启动 JVM 时一定都要指定-server 命令行选项。server 模式的 JVM 将比 client 模式的 JVM 进行更多的优化, 例如将循环中未被修改的变量提升到循环外部, 因此在开发环境(client 模式的 JVM)中能正确运行的代码, 可能会在部署环境(server 模式的 JVM)中运行失败。

### 3.2 发布与逸出

**如果在对象构造完成之前就发布该对象, 就会破坏线程安全性。当某个不应该发布的对象被发布时, 这种情况就被称为逸出(Escape)。**

单例模式增加 volatile 修饰的原因也是这样，避免 JVM 指令重排导致先更新了对象的引用地址，导致发布了该对象。

 >  发布对象的最简单方法是将对象的引用保存到一个公有的静态变量中, 以便任何类和线程都能看见该对象, 

**发布某个对象可能会间接发布其他对象。** 发布一个 HashMap 对象，间接发布了其中的 K-V 对象。

**构造过程中，要防止 this 引用逸出。**

问题 1：构造器中直接或间接启动了一个新线程。新线程可以使用 this 引用，从而使用了未初始化完全的对象。
解决 1：(1)可以创建，但是尽量不要启动 (2)单独的方法进行创建与启动。

问题 2：调用一个可改写的实例方法(既不是私有方法, 也不是终结方法)。内部类、匿名内部类都可以访问外部类的对象的域，为什么会这样，实际上是因为内部类构造的时候，会把外部类的对象 this 隐式的作为一个参数传递给内部类的构造方法，这个工作是编译器做的，他会给你内部类所有的构造方法添加这个参数，所以你例子里的匿名内部类在你构造 ThisEscape 时就把 ThisEscape 创建的对象隐式的传给匿名内部类了。
解决 2：使用工厂方法来创建，私有的构造函数+公共的工厂方法。工厂方法中先根据构造函数创建对象，再进行后续一系列绑定、启动线程等处理。

### 3.3 线程封闭

#### 3.3.1 Ad-hoc 线程封闭

 >  Ad-hoc 线程封闭是指, 维护线程封闭性的职责完全由程序实现来承担。Ad-hoc 线程封闭是非常脆弱的, 因为没有任何一种语言特性, 例如可见性修饰符或局部变量, 能将对象封闭到目标线程上。

 >  在 volatile 变量上存在一种特殊的线程封闭。只要你能确保只有单个线程对共享的 volatile 变量执行写入操作, 那么就可以安全地在这些共享的 volatile 变量上执行“读取-修改-写入”的操作。在这种情况下, 相当于将修改操作封闭在单个线程中以防止发生竞态条件, 并且 volatile 变量的可见性保证还确保了其他线程能看到最新的值。

 >  由于 Ad-hoc 线程封闭技术的脆弱性, 因此在程序中尽量少用它, 在可能的情况下, 应该使用更强的线程封闭技术(例如, 栈封闭或 ThreadLocal 类)。

#### 3.3.2 栈封闭

栈封闭下，只有通过局部遍历才能访问对象。将可变与不可变对象当作局部变量，并且保证引用的对象不会逸出。

#### 3.3.3 ThreadLocal 类
每一个 Thread 下面有一个 ThreadLocal.ThreadLocalMap 对象。ThreadLocalMap 下有个 Entry 数组，每个 Entry 代表一个对象。Entry 构造函数包含两个数据，一个是当前的 ThreadLocal 对象，一个是 Object 对象。存对象时，是向当前线程的 ThreadLocalMap 中存储数据；读对象时，也是根据当前 ThreadLocal 对象来获取对应的结果。从而线程隔离。

需要注意的是，Extry 对象继承了弱引用，存在内存泄漏的问题。即，ThreadLocal 被垃圾回收后，ThreadLocalMap 属于 Thread 的成员变量，与 Thread 的生命周期相同，因此出现 key 不存在，value 仍存在的情况。解决方法是，使用完 ThreadLocal 后及时调用 remove 方法释放空间。
```java
class Thread implements Runnable {
    ThreadLocal.ThreadLocalMap threadLocals = null;
    ....
}
class ThreadLocal<T> {
    static class ThreadLocalMap {
        private Entry[] table;
        static class Entry extends WeakReference<ThreadLocal<?>> {
            Object value;
            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }
        private Entry getEntry(ThreadLocal<?> key) {...   }
        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) { ...    }
        private void set(ThreadLocal<?> key, Object value) {  ....   }
        private void remove(ThreadLocal<?> key) {  ...  }
    }
}
```

 >  维持线程封闭性的一种更规范方法是使用 ThreadLocal, 这个类能使线程中的某个值与保存值的对象关联起来。ThreadLocal 提供了 get 与 set 等访问接口或方法, 这些方法为每个使用该变量的线程都存有一份独立的副本, 因此 get 总是返回由当前执行线程在调用 set 时设置的最新值。

 >  ThreadLocal 对象通常用于防止对可变的单实例变量(Singleton)或全局变量进行共享。

 >  由于 JDBC 的连接对象不一定是线程安全的, 因此, 当多线程应用程序在没有协同的情况下使用全局变量时, 就不是线程安全的。通过将 JDBC 的连接保存到 ThreadLocal 对象中, 每个线程都会拥有属于自己的连接

 >  当某个线程初次调用 ThreadLocal.get 方法时, 就会调用 initialValue 来获取初始值。从概念上看, 你可以将 ThreadLocal < T > 视为包含了 Map < Thread,  T > 对象, 其中保存了特定于该线程的值, 但 ThreadLocal 的实现并非如此。这些特定于线程的值保存在 Thread 对象中, 当线程终止后, 这些值会作为垃圾回收。

 >  假设你需要将一个单线程应用程序移植到多线程环境中, 通过将共享的全局变量转换为 ThreadLocal 对象(如果全局变量的语义允许), 可以维持线程安全性。然而, 如果将应用程序范围内的缓存转换为线程局部的缓存, 就不会有太大作用。

### 3.4 不变性

不可变对象一定是线程安全的。


 >  当满足以下条件时, 对象才是不可变的：
 >  - 对象创建以后其状态就不能修改。
 >  - 对象的所有域都是 final 类型。
 >  - 对象是正确创建的(在对象的创建期间, this 引用没有逸出)。

#### 3.4.1 Final 域

被 final 修饰的对象并不代表不可变对象，仅仅是该对象的引用不可变而已。该对象内部如果存在可变量，对象还是可能改变的。final 域能确保初始化过程的安全性, 从而可以不受限制地访问不可变对象, 并在共享这些对象时无须同步。

良好的编程习惯：
- 除非需要更高的可见性, 否则应将所有的域都声明为私有域；除非需要某个域是可变的, 否则应将其声明为 final 域。

#### 3.4.2 使用 volatile 类型来发布不可变对象

**将需要原子执行的操作封装为一个不可变类，然后发布时使用 volatile 修饰。**

每当需要对一组相关数据以原子方式执行某个操作时, 就可以考虑创建一个不可变的类来包含这些数据, 例如程序清单 3-12 ([OneValueCache.java](https://jcip.net/listings/OneValueCache.java))中的 OneValueCache.

程序清单 3-13 中的 VolatileCachedFactorizer([VolatileCachedFactorizer.java](https://jcip.net/listings/VolatileCachedFactorizer.java)) 使用了 OneValueCache 来保存缓存的数值及其因数。当一个线程将 volatile 类型的 cache 设置为引用一个新的 OneValueCache 时, 其他线程就会立即看到新缓存的数据。

因为 OneValueCache 是不可变的, 并且在每条相应的代码路径中只会访问它一次。通过使用包含多个状态变量的容器对象来维持不变性条件, 并使用一个 volatile 类型的引用来确保可见性, 使得 volatile CachedFactorizer 在没有显式地使用锁的情况下仍然是线程安全的。

### 3.5 安全发布

#### 3.5.1 不正确的发布：正确的对象被破坏

```java
public class Holder{
	private int n;
	public Holder(int n) { this.n=n; }
	public void assertSanity() {
		if(n!=n) 	throw new AssertionError("This statement is false.");
	}
}

public class StuffIntoPublic {
    public Holder holder;
    public void initialize() {
        holder = new Holder(42);
    }
}
```

由于 n 没有使用 final 修饰，因此可能导致线程 A 调用 initialize 函数，线程 B 直接调用 holder 对象时，显示 holder 对象已经创建，但是 n 其实还没来得及赋值 42，仅保留 0 初始值。这是由于创建对象时，先调用 Object 的构造方法，将所有位置置为 0；然后调用对象本身的构造方法，将 n 进行赋值。

对象的引用地址和调用对象的构造方法，正常是先执行哪个呢？(之后系统看 JVM 的时候再进行了解，相关链接：[深入理解Java对象的创建过程：类的初始化与实例化](https://blog.csdn.net/justloveyou_/article/details/72466416))

疑问 1：这里是否需要 volatile 进行修饰？否则可能线程 A 初始化完成后，线程 B 不一定可见最新的对象。好吧，下面解释了。
疑问 2：这里可不可以只使用 volatile 修饰 StuffIntoPublic 的成员变量 holder 呢？

#### 3.5.3 安全发布的常用模式

**要安全地发布一个对象, 对象的引用以及对象的状态必须同时对其他线程可见。**

**一个正确构造的对象可以通过以下方式来安全地发布：**
- 在静态初始化函数中初始化一个对象引用。
- 将对象的引用保存到 volatile 类型的域或者 AtomicReferance 对象中。
- 将对象的引用保存到某个正确构造对象的 final 类型域中。
- 将对象的引用保存到一个由锁保护的域中。

**有锁保护的域中：**
- 通过将一个键或者值放入 Hashtable、synchronizedMap 或者 ConcurrentMap 中, 可以安全地将它发布给任何从这些容器中访问它的线程(无论是直接访问还是通过迭代器访问)。
- 通过将某个元素放入 Vector、CopyOnWriteArrayList、CopyOnWriteArraySet、synchronizedList 或 synchronizedSet 中, 可以将该元素安全地发布到任何从这些容器中访问该元素的线程。
- 通过将某个元素放入 BlockingQueue 或者 ConcurrentLinkedQueue 中, 可以将该元素安全地发布到任何从这些队列中访问该元素的线程。

要发布一个静态构造的对象, 最简单和最安全的方式是使用静态的初始化器：`public static Holder holder = new Holder(42);`。静态初始化器由 JVM 在类的初始化阶段执行。由于在 JVM 内部存在着同步机制, 因此通过这种方式初始化的任何对象都可以被安全地发布。

#### 3.5.4 事实不可变对象

**如果对象从技术上来看是可变的, 但其状态在发布后不会再改变, 那么把这种对象称为“事实不可变对象(Effectively Immutable Object)”。**

**在没有额外的同步的情况下, 任何线程都可以安全地使用被安全发布的事实不可变对象。**

#### 3.5.5 可变对象

**对象的发布需求取决于它的可变性：**
- 不可变对象可以通过任意机制来发布。
- 事实不可变对象必须通过安全方式来发布。
- 可变对象必须通过安全方式来发布, 并且必须是线程安全的或者由某个锁保护起来。

#### 3.5.6 安全地共享对象

**许多并发错误都是由于没有理解共享对象的这些“既定规则”而导致的。当发布一个对象时, 必须明确地说明对象的访问方式。** 也就是文章后面提到的写文档。

在并发程序中使用和共享对象时, 可以使用一些实用的策略, 包括：
- 线程封闭。线程封闭的对象只能由一个线程拥有, 对象被封闭在该线程中, 并且只能由这个线程修改。
- 只读共享。在没有额外同步的情况下, 共享的只读对象可以由多个线程并发访问, 但任何线程都不能修改它。共享的只读对象包括不可变对象和事实不可变对象。
- 线程安全共享。线程安全的对象在其内部实现同步, 因此多个线程可以通过对象的公有接口来进行访问而不需要进一步的同步。
- 保护对象。被保护的对象只能通过持有特定的锁来访问。保护对象包括封装在其他线程安全对象中的对象, 以及已发布的并且由某个特定锁保护的对象。

## 第 4 章 - 对象的组合

###  4.1 设计线程安全的类

 >  在设计线程安全类的过程中, 需要包含以下三个基本要素：
 > 
 >  - 找出构成对象状态的所有变量。
 >  - 找出约束状态变量的不变性条件。
 >  - 建立对象状态的并发访问管理策略。

 >  同步策略(Synchronization Policy)定义了如何在不违背对象不变条件或后验条件的情况下对其状态的访问操作进行协同。同步策略规定了如何将不可变性、线程封闭与加锁机制等结合起来以维护线程的安全性, 并且还规定了哪些变量由哪些锁来保护。要确保开发人员可以对这个类进行分析与维护, 就必须将同步策略写为正式文档。

####  4.1.1 收集同步需求(不变性条件、后验条件)

 >  要确保类的线程安全性, 就需要确保它的不变性条件不会在并发访问的情况下被破坏, 这就需要对其状态进行推断。

不变性条件：value 一定不为负。

 >  在操作中还会包含一些后验条件来判断状态迁移是否是有效的。如果 Counter 的当前状态为 17, 那么下一个有效状态只能是 18。当下一个状态需要依赖当前状态时, 这个操作就必须是一个复合操作。

后验条件：如果 value 为 16, 那么 value 下一次更改时必须变为 17.

当下一个状态依赖于当前状态时, 这个操作必须是复合操作, 需加锁完成。

 >  在类中也可以包含同时约束多个状态变量的不变性条件。
 > 
 >  在一个表示数值范围的类(例如程序清单 4-10 中的 NumberRange)中可以包含两个状态变量, 分别表示范围的上界和下界。这些变量必须遵循的约束是, 下界值应该小于或等于上界值。
 > 
 >  类似于这种包含多个变量的不变性条件将带来原子性需求：这些相关的变量必须在单个原子操作中进行读取或更新。不能首先更新一个变量, 然后释放锁并再次获得锁, 然后再更新其他的变量。因为释放锁后, 可能会使对象处于无效状态。如果在一个不变性条件中包含多个变量, 那么在执行任何访问相关变量的操作时, 都必须持有保护这些变量的锁。

多个状态变量的不变性条件：需要加锁完成。

 >  如果不了解对象的不变性条件与后验条件, 那么就不能确保线程安全性。要满足在状态变量的有效值或状态转换上的各种约束条件, 就需要借助于原子性与封装性。

了解了对象的不变性条件和后验条件, 才可以着手去设计与实现线程安全性。在此基础上实现线程安全性, 需要使用原子性和封装性。

#### 4.1.2 依赖状态的操作(先验条件)

 >  类的不变性条件与后验条件约束了在对象上有哪些状态和状态转换是有效的。在某些对象的方法中还包含一些基于状态的先验条件(Precondition)。例如, 不能从空队列中移除一个元素, 在删除元素前, 队列必须处于“非空的”状态。如果在某个操作中包含有基于状态的先验条件, 那么这个操作就称为依赖状态的操作。

先验条件：如队列不为空, 才能 poll 元素。属于依赖状态的操作。

 >  但在并发程序中, 先验条件可能会由于其他线程执行的操作而变成真。在并发程序中要一直等到先验条件为真, 然后再执行该操作。

AtomicInteger 自增操作的实现是 CAS+自旋机制, 是否属于先验条件呢？

 >  要想实现某个等待先验条件为真时才执行的操作, 一种更简单的方法是通过现有库中的类(例如阻塞队列[Blocking Queue]或信号量[Semaphore])来实现依赖状态的行为。第 5 章将介绍一些阻塞类, 例如 BlockingQueue、Semaphore 以及其他的同步工具类。第 14 章将介绍如何使用在平台与类库中提供的各种底层机制来创建依赖状态的类。

####  4.1.3 状态的所有权

 >  一个对象状态的概念：
 >  如果以某个对象为根节点构造一张对象图, 那么该对象的状态将是对象图中所有对象包含的域的一个子集。
 > 
 >  如果分配并填充了一个 HashMap 对象, 那么就相当于创建了多个对象：HashMap 对象, 在 HashMap 对象中包含的多个对象, 以及在 Map.Entry 中可能包含的内部对象。

 >  所有权与封装性总是相互关联的：
 >  对象封装它拥有的状态, 反之也成立, 即对它封装的状态拥有所有权。

 >  所有权意味着控制权。然而, 如果发布了某个可变对象的引用, 那么就不再拥有独占的控制权, 最多是“共享控制权”。
 > 
 >  对于从构造函数或者从方法中传递进来的对象, 类通常并不拥有这些对象, 除非这些方法是被专门设计为转移传递进来的对象的所有权(例如, 同步容器封装器的工厂方法)。

 >  容器类通常表现出一种“所有权分离”的形式, 其中容器类拥有其自身的状态, 而客户代码则拥有容器中各个对象的状态。
 > 
 >  Servlet 框架中的 ServletContext 就是其中一个示例。ServletContext 为 Servlet 提供了类似于 Map 形式的对象容器服务, 在 ServletContext 中可以通过名称来注册(setAttribute)或获取(getAttribute)应用程序对象。由 Servlet 容器实现的 ServletContext 对象必须是线程安全的, 因为它肯定会被多个线程同时访问。当调用 setAttribute 和 getAttribute 时, Servlet 不需要使用同步, 但当使用保存在 ServletContext 中的对象时, 则可能需要使用同步。这些对象由应用程序拥有, Servlet 容器只是替应用程序保管它们。

 >  为了防止多个线程在并发访问同一个对象时产生的相互干扰, 这些对象应该要么是线程安全的对象, 要么是事实不可变的对象, 或者由锁来保护的对象。

###  4.2 实例封闭

 > 如果某对象不是线程安全的, 那么可以通过多种技术使其在多线程程序中安全地使用。

 >  封装简化了线程安全类的实现过程, 它提供了一种实例封闭机制(InstanceConfinement), 通常也简称为“封闭”。
 > 
 >  将数据封装在对象内部, 可以将数据的访问限制在对象的方法上, 从而更容易确保线程在访问数据时总能持有正确的锁。

例如：将 Point 对象作为 ClassTmp 类的私有成员, 只能使用加锁的 get 或 set 来进行访问。

 >  被封闭对象一定不能超出它们既定的作用域。封装的方式有：
 > 
 >  - 可以封闭在类的一个实例(例如作为类的一个私有成员)中, 
 >  - 或者封闭在某个作用域内(例如作为一个局部变量), 
 >  - 或者封闭在线程内(例如在某个线程中将对象从一个方法传递到另一个方法, 而不是在多个线程之间共享该对象)。
 > 
 >  当然, 对象本身不会逸出——出现逸出情况的原因通常是由于开发人员在发布对象时超出了对象既定的作用域。

 >  在 Java 平台的类库中还有很多线程封闭的示例, 其中有些类的唯一用途就是将非线程安全的类转化为线程安全的类。一些基本的容器类并非线程安全的, 例如 ArrayList 和 HashMap, 但类库提供了包装器工厂方法(例如 Collections.synchronizedList 及其类似方法), 使得这些非线程安全的类可以在多线程环境中安全地使用。这些工厂方法通过“装饰器(Decorator)”模式(Gamma et al., 1995)将容器类封装在一个同步的包装器对象中, 而包装器能将接口中的每个方法都实现为同步方法, 并将调用请求转发到底层的容器对象上。

 >  封闭机制更易于构造线程安全的类, 因为当封闭类的状态时, 在分析类的线程安全性时就无须检查整个程序。

####  4.2.1 Java 监视器模式

 >  遵循 Java 监视器模式的对象会把对象的所有可变状态都封装起来, 并由对象自己的内置锁来保护。Java 监视器模式仅仅是一种编写代码的约定, 对于任何一种锁对象, 只要自始至终都使用该锁对象, 都可以用来保护对象的状态。

两种实现：

1. 使用对象的内置锁(或任何其他可通过公有方式访问的锁)。例如, get 和 set 方法加上 Synchronized 修饰。
2. 使用私有的锁对象, 例如, 对于封装的对象进行加锁, 如下面所示。或者也可以生成一个私有的其他对象, 获取该对象的锁才可以操作被封装的对象, 有些类似于 redis 使用 setnx 实现分布式锁的原理。

```java
public class PrivateLock{
    private final Object myLock=new Object(); 
    @GuardedBy("myLock")Widget widget; 
    void someMethod(){
        synchronized(myLock){
        //访问或修改Widget的状态}
        }
    }
}
```

 >  使用私有的锁对象而不是对象的内置锁(或任何其他可通过公有方式访问的锁), 有许多优点。
 > 
 >  私有的锁对象可以将锁封装起来, 使客户代码无法得到锁, 但客户代码可以通过公有方法来访问锁, 以便(正确或者不正确地)参与到它的同步策略中。如果客户代码错误地获得了另一个对象的锁, 那么可能会产生活跃性问题。此外, 要想验证某个公有访问的锁在程序中是否被正确地使用, 则需要检查整个程序, 而不是单个的类。

####  4.2.2 示例：车辆追踪

```java
@ThreadSafepublic 
class MonitorVehicleTracker{
	@GuardedBy("this")
	private final Map < String,  MutablePoint > locations; 
	public MonitorVehicleTracker(Map < String,  MutablePoint > locations){
	  this.locations=deepCopy(locations); 
	}
	public synchronized Map < String,  MutablePoint > getLocations(){
	  return deepCopy(locations); 
	}
	public synchronized MutablePoint getLocation(String id){
	  MutablePoint loc=locations.get(id); 
	  return loc==null?null：new MutablePoint(loc); 
	}
	public synchronized void setLocation(String id,  int x,  int y){
	  MutablePoint loc=locations.get(id); 
	  if(loc==null)
	      throw new IllegalArgumentException("No such ID："+id); loc.x=x; loc.y=y; 
	}
	private static Map < String,  MutablePoint > deepCopy(Map < String,  MutablePoint > m){
	  Map < String,  MutablePoint > result=new HashMap < String,  MutablePoint > (); 
	  for(String id：m.keySet())
	    	result.put(id,  new MutablePoint(m.get(id))); 
		return Collections.unmodifiableMap(result); 
	 }
}
 
@NotThreadSafepublic 
class MutablePoint{
  public int x,  y; 
  public MutablePoint(){x=0; y=0; }
  public MutablePoint(MutablePoint p){this.x=p.x; this.y=p.y; }
}
```

 >  上述代码的缺点：
 >  由于 deepCopy 是从一个 synchronized 方法中调用的, 因此在执行时间较长的复制操作中, tracker 的内置锁将一直被占有, 当有大量车辆需要追踪时, 会严重降低用户界面的响应灵敏度。

如果可以, 尽可能让锁的粒度下沉, 或者使用现有的线程安全对象。这里, 可以使用具有线程安全的 Map 实现类, 如 ConcurrentHashMap 等。如此, getLocation 方法只需要直接使用 ConcurrentHashMap 的接口就可以, 不需要再加锁, 也不用深拷贝。

###  4.3 线程安全性的委托

 >  大多数对象都是组合对象。当从头开始构建一个类, 或者将多个非线程安全的类组合为一个类时, Java 监视器模式是非常有用的。但是, 如果类中的各个组件都已经是线程安全的, 会是什么情况呢？我们是否需要再增加一个额外的线程安全层？答案是“视情况而定”。

之前提到的, 检查再操作, putIfAbsent。

#### 4.3.1 示例：基于委托的车辆追踪器

```java
@Immutablepublic 
class Point{
    public final int x,  y; 
    public Point(int x,  int y){
        this.x=x; this.y=y; 
    }
}
@ThreadSafepublic 
class DelegatingVehicleTracker{
    private fnal ConcurrentMap < String,  Point > locations;
    private fnal Map < String,  Point > unmodifiableMap;
    public DelegatingVehicleTracker(Map < String,  Point > points){
        locations=new ConcurrentHashMap < String,  Point > (points);
        unmodifiableMap=Collections.unmodifableMap(locations);
    }
    public Map < String,  Point > getLocations(){
        return unmodifiableMap;
    }
    public Point getLocation(String id){
        return locations.get(id);
    }                                             
    public void setLocation(String id,  int x,  int y){
        if(locations.replace(id,  new  Point(x,  y))==null)
            throw new IllegalArgumentException("invalid vehicle name："+id);
    }
}
```

 >  Point 类是不可变的, 因而它是线程安全的。不可变的值可以被自由地共享与发布, 因此在返回 location 时不需要复制。

 >  如果使用最初的 MutablePoint 类而不是 Point 类, 就会破坏封装性, 因为 getLocations 会发布一个指向可变状态的引用, 而这个引用不是线程安全的。需要注意的是, 我们稍微改变了车辆追踪器类的行为。在使用监视器模式的车辆追踪器中返回的是车辆位置的快照, 而在使用委托的车辆追踪器中返回的是一个不可修改但却实时的车辆位置视图。这意味着, 如果线程 A 调用 getLocations, 而线程 B 在随后修改了某些点的位置, 那么在返回给线程 A 的 Map 中将反映出这些变化。在前面提到过, 这可能是一种优点(更新的数据), 也可能是一种缺点(可能导致不一致的车辆位置视图), 具体情况取决于你的需求。

 >  如果需要一个不发生变化的车辆视图, 那么 getLocations 可以返回对 locations 这个 Map 对象的一个浅拷贝(Shallow Copy)
 > 
 >  public Map < String,  Point > getLocations(){
 >  	return Collections.unmodifiableMap(new HashMap < String,  Point > (locations)); 
 >  }

####  4.3.2 独立的状态变量

 >  可以将线程安全性委托给多个状态变量, 只要这些变量是彼此独立的, 即组合而成的类并不会在其包含的多个状态变量上增加任何不变性条件。

 >  CopyOnWriteArrayList 来保存各个监听器列表。它是一个线程安全的链表, 特别适用于管理监听器列表(参见 5.2.3 节)

 >  此外, 由于各个状态之间不存在耦合关系, 因此 VisualComponent 可以将它的线程安全性委托给 mouseListeners 和 keyListeners 等对象。

```java
public class VisualComponent{
    private final List < KeyListener > keyListeners=new CopyOnWriteArrayList < KeyListener > ();
    private final List < MouseListener > mouseListeners=new CopyOnWriteArrayList < MouseListener > ();
    public void addKeyListener(KeyListener listener){keyListeners.add(listener);}
}
```

#### 4.3.3 当委托失效时(状态变量之间不独立)

 >  由于状态变量 lower 和 upper 不是彼此独立的, 因此 NumberRange 不能将线程安全性委托给它的线程安全状态变量。

 >  如果一个类是由多个独立且线程安全的状态变量组成, 并且在所有的操作中都不包含无效状态转换, 那么可以将线程安全性委托给底层的状态变量。反之, 不可以。

#### 4.3.4 发布底层的状态变量

 >  当把线程安全性委托给某个对象的底层状态变量时, 在什么条件下才可以发布这些变量从而使其他类能修改它们？答案仍然取决于在类中对这些变量施加了哪些不变性条件。
 > 
 >  如果一个状态变量是线程安全的, 并且没有任何不变性条件来约束它的值, 在变量的操作上也不存在任何不允许的状态转换, 那么就可以安全地发布这个变量。

#### 4.3.5 示例：发布状态的车辆追踪器

 > 我们来构造车辆追踪器的另一个版本, 并在这个版本中发布底层的可变状态。我们需要修改接口以适应这种变化, 即使用可变且线程安全的 Point 类。

车辆追踪器将线程安全性委托给了 locations 变量, 而 locations 变量属于 ConcurrentHashMap, 也是线程安全的。因此, 若发布可变状态的 Point, 那么只需要保证 Point 是线程安全的就可以。

```java
@ThreadSafe
public class SafePoint{
    @GuardedBy("this")private int x,  y;
    private SafePoint(int[]a){this(a[0], a[1]);}
    public SafePoint(SafePoint p){this(p.get());}
    public SafePoint(int x,  int y){this.x=x;this.y=y;}
    public synchronized int[]get(){return new int[]{x,  y};}
    public synchronized void set(int x,  int y){this.x=x;this.y=y;}
}
```

 >  如果将拷贝构造函数实现为 this(p.x,  p.y), 那么会产生竞态条件, 而私有构造函数则可以避免这种竞态条件。这是私有构造函数捕获模式(PrivateConstructor Capture Idiom,  Bloch and Gafter, 2005)的一个实例。

需要注意！！！这里自己很容易忽略。

###  4.4 在现有的线程安全类中添加功能

总结：

1. 修改原来的类
2. 扩展该类, 如 extend 或 implement
3. 客户端加锁机制, 对被扩展的对象进行加锁
4. 组合/Java 监视器模式, 然后在扩展功能上对被监视的对象进行加锁。

 >  要添加一个新的原子操作, 最安全的方法是修改原始的类。
 > 
 >  但这通常无法做到, 因为你可能无法访问或修改类的源代码。要想修改原始的类, 就需要理解代码中的同步策略, 这样增加的功能才能与原有的设计保持一致。如果直接将新方法添加到类中, 那么意味着实现同步策略的所有代码仍然处于一个源代码文件中, 从而更容易理解与维护。

 >  另一种方法是扩展这个类, 假定在设计这个类时考虑了可扩展性。

```java
public class BetterVector < E > extends Vector < E > {
    public synchronized boolean putIfAbsent(E x){
        ...
    }
}
```

 >  “扩展”方法比直接将代码添加到类中更加脆弱, 因为现在的同步策略实现被分布到多个单独维护的源代码文件中。
 >  如果底层的类改变了同步策略并选择了不同的锁来保护它的状态变量, 那么子类会被破坏, 因为在同步策略改变后它无法再使用正确的锁来控制对基类状态的并发访问。(在 Vector 的规范中定义了它的同步策略, 因此 BetterVector 不存在这个问题。)

#### 4.4.1 客户端加锁机制

 >  对于由 Collections.synchronizedList 封装的 ArrayList, 这两种方法在原始类中添加一个方法或者对类进行扩展都行不通, 因为客户代码并不知道在同步封装器工厂方法中返回的 List 对象的类型。

 >  第三种策略是扩展类的功能, 但并不是扩展类本身, 而是将扩展代码放入一个“辅助类”中。

```java
@NotThreadSafepublic 
class ListHelper < E > {
    public List < E > list=Collections.synchronizedList(new ArrayList < E > ()); 
    public synchronized boolean putIfAbsent(E x){
    	boolean absent=！list.contains(x); 
        if(absent)list.add(x); return absent; 
    }
}
```

 >  为什么这种方式不能实现线程安全性？毕竟, putIfAbsent 已经声明为 synchronized 类型的变量, 对不对？问题在于在错误的锁上进行了同步。无论 List 使用哪一个锁来保护它的状态, 可以确定的是, 这个锁并不是 ListHelper 上的锁。ListHelper 只是带来了同步的假象, 尽管所有的链表操作都被声明为 synchronized, 但却使用了不同的锁, 这意味着 putIfAbsent 相对于 List 的其他操作来说并不是原子的, 因此就无法确保当 putIfAbsent 执行时另一个线程不会修改链表。

线程不安全的原因在于加锁的对象不同。putIfAbsent 方法申请对 ListHelper 对象加锁。直接调用 list 方法时, 申请对 list 对象或更小粒度对象加锁。两个锁不同, 因此 putIfAbsent 方法操作时, list 对象可能被其他对象操作。

解决方法如下, 直接对 list 对象进行加锁。

```java
public boolean putIfAbsent(E x){
    synchronized(list){
        boolean absent=！list.contains(x); 
        if(absent)list.add(x); return absent; 
    }
}
 >  ```

 >  通过添加一个原子操作来扩展类是脆弱的, 因为它将类的加锁代码分布到多个类中。然而, 客户端加锁却更加脆弱, 因为它将类C的加锁代码放到与C完全无关的其他类中。当在那些并不承诺遵循加锁策略的类上使用客户端加锁时, 要特别小心。

 >  客户端加锁机制与扩展类机制有许多共同点, 二者都是将派生类的行为与基类的实现耦合在一起。正如扩展会破坏实现的封装性[EJ Item 14], 客户端加锁同样会破坏同步策略的封装性。

#### 4.4.2 组合

 >  当为现有的类添加一个原子操作时, 有一种更好的方法：组合(Composition)。程序清单4-16中的ImprovedList通过将List对象的操作委托给底层的List实例来实现List的操作, 同时还添加了一个原子的putIfAbsent方法。(与Collections.synchronizedList和其他容器封装器一样, ImprovedList假设把某个链表对象传给构造函数以后, 客户代码不会再直接使用这个对象, 而只能通过ImprovedList来访问它。)

```java
public class ImprovedList < T > implements List < T > {
    private final List < T > list; 
    public ImprovedList(List < T > list){this.list=list; }
    ...
}
```

 >  事实上, 我们使用了 Java 监视器模式来封装现有的 List, 并且只要在类中拥有指向底层 List 的唯一外部引用, 就能确保线程安全性。

### 4.5 将同步策略文档化

 >  用户可以通过查阅文档来判断某个类是否是线程安全的, 而维护人员也可以通过查阅文档来理解其中的实现策略, 避免在维护过程中破坏安全性。

 >  在文档中说明客户代码需要了解的线程安全性保证, 以及代码维护人员需要了解的同步策略。

 >  synchronized、volatile 或者任何一个线程安全类都对应于某种同步策略, 用于在并发访问时确保数据的完整性。

 >  设计阶段是编写设计决策文档的最佳时间。

 >  在设计同步策略时需要考虑多个方面, 例如, 将哪些变量声明为 volatile 类型, 哪些变量用锁来保护, 哪些锁保护哪些变量, 哪些变量必须是不可变的或者被封闭在线程中的, 哪些操作必须是原子操作等。其中某些方面是严格的实现细节, 应该将它们文档化以便于日后的维护。还有一些方面会影响类中加锁行为的外在表现, 也应该将其作为规范的一部分写入文档。

 >  最起码, 应该保证将类中的线程安全性文档化。它是否是线程安全的？在执行回调时是否持有一个锁？是否有某些特定的锁会影响其行为？不要让客户冒着风险去猜测。如果你不想支持客户端加锁也是可以的, 但一定要明确地指出来。如果你希望客户代码能够在类中添加新的原子操作, 如 4.4 节所示, 那么就需要在文档中说明需要获得哪些锁才能实现安全的原子操作。如果使用锁来保护状态, 那么也要将其写入文档以便日后维护, 这很简单, 只需使用标注@GuardedBy 即可。如果要使用更复杂的方法来维护线程安全性, 那么一定要将它们写入文档, 因为维护者通常很难发现它们。

 >  我们是否应该因为某个对象看上去是线程安全的而就假设它是安全的？是否可以假设通过获取对象的锁来确保对象访问的线程安全性？(只有当我们能控制所有访问该对象的代码时, 才能使用这种带风险的技术, 否则, 这只能带来线程安全性的假象。)

 >  如果某个类没有明确地声明是线程安全的, 那么就不要假设它是线程安全的, 从而有效地避免类似于 SimpleDateFormat 的问题。而另一方面, 如果不对容器提供对象(例如 HttpSession)的线程安全性做某种有问题的假设, 也就不可能开发出一个基于 Servlet 的应用程序。

 >  一个提高猜测准确性的方法是, 从实现者(例如容器或数据库的供应商)的角度去解释规范, 而不是从使用者的角度去解释。Servlet 通常是在容器管理的(Container-Managed)线程中调用的, 因此可以安全地假设：如果有多个这种线程在运行, 那么容器是知道这种情况的。Servlet 容器能生成一些为多个 Servlet 提供服务的对象, 例如 HttpSession 或 ServletContext。因此, Servlet 容器应该预见到这些对象将被并发访问, 因为它创建了多个线程, 并且从这些线程中调用像 Servlet.service 这样的方法, 而这个方法很可能会访问 ServletContext。

如果文档没有注明, 那么从实现者的角度猜测他是否对 xxx 对象提供线程安全。

## 第 5 章-基础构建模块
### 5.1 同步容器类
#### 5.1.1 同步容器类的问题

 >  同步容器类都是线程安全的, 但在某些情况下可能需要额外的客户端加锁来保护复合操作。容器上常见的复合操作包括：迭代(反复访问元素, 直到遍历完容器中所有元素)、跳转(根据指定顺序找到当前元素的下一个元素)以及条件运算, 例如“若没有则添加”(检查在 Map 中是否存在键值 K, 如果没有, 就加入二元组(K, V))。在同步容器类中, 这些复合操作在没有客户端加锁的情况下仍然是线程安全的, 但当其他线程并发地修改容器时, 它们可能会表现出意料之外的行为。

 >  Vector 中定义的两个方法：getLast 和 deleteLast, 它们都会执行“先检查再运行”操作。每个方法首先都获得数组的大小, 然后通过结果来获取或删除最后一个元素。Vector 上可能导致混乱结果的复合操作。

```java
public static Object getLast(Vector list){
	synchronized(list){
	int lastIndex=list.size()-1;
	return list.get(lastIndex);
	}
}
public static void deleteLast(Vector list){
	synchronized(list){
	int lastIndex=list.size()-1;
	list.remove(lastIndex);
	}
}
```
除此之外, 还有加锁的迭代。

#### 5.1.2　迭代器与 ConcurrentModificationException

 >  无论在直接迭代还是在 Java 5.0 引入的 for-each 循环语法中, 对容器类进行迭代的标准方式都是使用 Iterator。然而, 如果有其他线程并发地修改容器, 那么即使是使用迭代器也无法避免在迭代期间对容器加锁。在设计同步容器类的迭代器时并没有考虑到并发修改的问题, 并且它们表现出的行为是“及时失败”(fail-fast)的。这意味着, 当它们发现容器在迭代过程中被修改时, 就会抛出一个 ConcurrentModificationException 异常。

迭代也需要加锁, 但是迭代期间加锁会导致长期占用锁资源, 导致饥饿。如果不希望在迭代期间对容器加锁, 那么一种替代方法就是“克隆”容器, 并在副本上进行迭代。不过, 克隆也会消耗一定的时间。

#### 5.1.3　隐藏迭代器

集合容器的 toString、hashCode、equals、containsAll、removeAll、retainAll 等方法都会进行隐藏迭代, 因此需要注意出现 ConcurrentModificationException 异常。

### 5.2　并发容器

 >  Java 5.0 提供了多种并发容器类来改进同步容器的性能。
 >  
 >  同步容器将所有对容器状态的访问都串行化, 以实现它们的线程安全性。这种方法的代价是严重降低并发性, 当多个线程竞争容器的锁时, 吞吐量将严重减低。
 >  
 >  另一方面, 并发容器是针对多个线程并发访问设计的。在 Java 5.0 中增加了 Concurrent-HashMap, 用来替代同步且基于散列的 Map, 以及 CopyOnWriteArrayList, 用于在遍历操作为主要操作的情况下代替同步的 List。
 > 
 >  Java 5.0 增加了两种新的容器类型：Queue 和 BlockingQueue。
 >  
 >  Queue 用来临时保存一组等待处理的元素。它提供了几种实现, 包括：ConcurrentLinkedQueue, 这是一个传统的先进先出队列, 以及 PriorityQueue, 这是一个(非并发的)优先队列。Queue 上的操作不会阻塞, 如果队列为空, 那么获取元素的操作将返回空值。虽然可以用 List 来模拟 Queue 的行为——事实上, 正是通过 LinkedList 来实现 Queue 的, 但还需要一个 Queue 的类, 因为它能去掉 List 的随机访问需求, 从而实现更高效的并发。
 >  
 >  BlockingQueue 扩展了 Queue, 增加了可阻塞的插入和获取等操作。如果队列为空, 那么获取元素的操作将一直阻塞, 直到队列中出现一个可用的元素。如果队列已满(对于有界队列来说), 那么插入元素的操作将一直阻塞, 直到队列中出现可用的空间。在“生产者-消费者”这种设计模式中, 阻塞队列是非常有用的, 5.3 节将会详细介绍。
 >  
 >  正如 ConcurrentHashMap 用于代替基于散列的同步 Map,  Java 6 也引入了 Concurrent-SkipListMap 和 ConcurrentSkipListSet, 分别作为同步的 SortedMap 和 SortedSet 的并发替代品(例如用 synchronizedMap 包装的 TreeMap 或 TreeSet)。

#### 5.2.1　ConcurrentHashMap

 >  ConcurrentHashMap 与其他并发容器一起增强了同步容器类：它们提供的迭代器不会抛出 ConcurrentModificationException, 因此不需要在迭代过程中对容器加锁。ConcurrentHashMap 返回的迭代器具有弱一致性(Weakly Consistent), 而并非“及时失败”。弱一致性的迭代器可以容忍并发的修改, 当创建迭代器时会遍历已有的元素, 并可以(但是不保证)在迭代器被构造后将修改操作反映给容器。
 >
 >  尽管有这些改进, 但仍然有一些需要权衡的因素。对于一些需要在整个 Map 上进行计算的方法, 例如 size 和 isEmpty, 这些方法的语义被略微减弱了以反映容器的并发特性。由于 size 返回的结果在计算时可能已经过期了, 它实际上只是一个估计值, 因此允许 size 返回一个近似值而不是一个精确值。虽然这看上去有些令人不安, 但事实上 size 和 isEmpty 这样的方法在并发环境下的用处很小, 因为它们的返回值总在不断变化 Hashtable 在使用 iterator 遍历的时候，如果其他线程，包括本线程对 Hashtable 进行了 put，remove 等更新操作的话，就会抛出 ConcurrentModificationException 异常，但如果使用 ConcurrentHashMap 的话，就不用考虑这方面的问题了。

Iterator 对象的使用，不一定是和其它更新线程同步，获得的对象可能是更新前的对象，ConcurrentHashMap 允许一边更新、一边遍历，也就是说在 Iterator 对象遍历的时候，ConcurrentHashMap 也可以进行 remove,put 操作，且遍历的数据会随着 remove,put 操作产出变化，所以希望遍历到当前全部数据的话，要么以 ConcurrentHashMap 变量为锁进行同步(synchronized 该变量)，要么使用 CopiedIterator 包装 iterator，使其拷贝当前集合的全部数据，但是这样生成的 iterator 不可以进行 remove 操作。

Hashtable 在使用 iterator 遍历的时候，如果其他线程，包括本线程对 Hashtable 进行了 put，remove 等更新操作的话，就会抛出 ConcurrentModificationException 异常，但如果使用 ConcurrentHashMap 的话，就不用考虑这方面的问题了。

keySet 返回的 iterator 是弱一直和 fail-safe 的，可能不会返回某些最近的改变，并且在遍历中，如果已经遍历的数组上的内容发生了变化，是不会抛出 ConcurrentModificationException 的异常。

#### 5.2.2　额外的原子 Map 操作

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

#### 5.2.3　CopyOnWriteArrayList

 >  CopyOnWriteArrayList 用于替代同步 List, 在某些情况下它提供了更好的并发性能, 并且在迭代期间不需要对容器进行加锁或复制。(类似地, CopyOnWriteArraySet 的作用是替代同步 Set。)“写入时复制(Copy-On-Write)”容器的线程安全性在于, 只要正确地发布一个事实不可变的对象, 那么在访问该对象时就不再需要进一步的同步。在每次修改时, 都会创建并重新发布一个新的容器副本, 从而实现可变性。“写入时复制”容器的迭代器保留一个指向底层基础数组的引用, 这个数组当前位于迭代器的起始位置, 由于它不会被修改, 因此在对其进行同步时只需确保数组内容的可见性。因此, 多个线程可以同时对这个容器进行迭代, 而不会彼此干扰或者与修改容器的线程相互干扰。“写入时复制”容器返回的迭代器不会抛出 ConcurrentModificationException, 并且返回的元素与迭代器创建时的元素完全一致, 而不必考虑之后修改操作所带来的影响。
 >  
 >  显然, 每当修改容器时都会复制底层数组, 这需要一定的开销, 特别是当容器的规模较大时。仅当迭代操作远远多于修改操作时, 才应该使用“写入时复制”容器。这个准则很好地描述了许多事件通知系统：在分发通知时需要迭代已注册监听器链表, 并调用每一个监听器, 在大多数情况下, 注册和注销事件监听器的操作远少于接收事件通知的操作。

CopyOnWriteArrayList 的添加元素方法。在修改数组对象时会进行赋值操作。通过申请 lock 对象的锁来保证线程安全性。
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
由于修改 CopyOnWriteArrayList 时会通过复制+更新引用的方式来完成的。因此当进行迭代的时候, 返回的是创建迭代器的一个快照版本。迭代中即使该对象被修改了, 那么修改后的数组和当前数组并非一个数组对象, 因此不会影响到迭代器。🐂啊。

### 5.3　阻塞队列和生产者-消费者模式

 >  BlockingQueue 简化了生产者-消费者设计的实现过程, 它支持任意数量的生产者和消费者。一种最常见的生产者-消费者设计模式就是线程池与工作队列的组合, 在 Executor 任务执行框架中就体现了这种模式, 这也是第 6 章和第 8 章的主题。

看到第 6、8 章的时候再回过来看看。

 >  阻塞队列简化了消费者程序的编码, 因为 take 操作会一直阻塞直到有可用的数据。如果生产者不能尽快地产生工作项使消费者保持忙碌, 那么消费者就只能一直等待, 直到有工作可做。在某些情况下, 这种方式是非常合适的(例如, 在服务器应用程序中, 没有任何客户请求服务), 而在其他一些情况下, 这也表示需要调整生产者线程数量和消费者线程数量之间的比率, 从而实现更高的资源利用率(例如, 在“网页爬虫[Web Crawler]”或其他应用程序中, 有无穷的工作需要完成)。

当阻塞队列满时, put 会阻塞, offer 会返回失败状态。根据需求灵活使用。

BlockingQueue 的多种实现, 包含 LinkedBlockingQueue 、 ArrayBlocking-Queue 、PriorityBlockingQueue、SynchronousQueue。

 >  最后一个 BlockingQueue 实现是 SynchronousQueue, 实际上它不是一个真正的队列, 因为它不会为队列中元素维护存储空间。与其他队列不同的是, 它维护一组线程, 这些线程在等待着把元素加入或移出队列。如果以洗盘子的比喻为例, 那么这就相当于没有盘架, 而是将洗好的盘子直接放入下一个空闲的烘干机中。这种实现队列的方式看似很奇怪, 但由于可以直接交付工作, 从而降低了将数据从生产者移动到消费者的延迟。
 >  
 >  直接交付方式还会将更多关于任务状态的信息反馈给生产者。当交付被接受时, 它就知道消费者已经得到了任务, 而不是简单地把任务放入一个队列——这种区别就好比将文件直接交给同事, 还是将文件放到她的邮箱中并希望她能尽快拿到文件。因为 SynchronousQueue 没有存储功能, 因此 put 和 take 会一直阻塞, 直到有另一个线程已经准备好参与到交付过程中。仅当有足够多的消费者, 并且总是有一个消费者准备好获取交付的工作时, 才适合使用同步队列。

Guess: SynchronousQueue 可以当作同步工具用, 两个线程之间的同步操作。

两个线程正在运行：
A 线程任务：放到 SynchronousQueue 中, 打印 B 线程已接受。
B 线程任务：从 SynchronousQueue 中取数据, 打印 A 线程已发送。

#### 5.3.1　示例：桌面搜索

#### 5.3.2　串行线程封闭

 >  对于可变对象, 生产者-消费者这种设计与阻塞队列一起, 促进了串行线程封闭, 从而将对象所有权从生产者交付给消费者。线程封闭对象只能由单个线程拥有, 但可以通过安全地发布该对象来“转移”所有权。在转移所有权后, 也只有另一个线程能获得这个对象的访问权限, 并且发布对象的线程不会再访问它。

#### 5.3.3　双端队列与工作密取

 >  正如阻塞队列适用于生产者-消费者模式, 双端队列同样适用于另一种相关模式, 即工作密取(Work Stealing)。在生产者-消费者设计中, 所有消费者有一个共享的工作队列, 而在工作密取设计中, 每个消费者都有各自的双端队列。如果一个消费者完成了自己双端队列中的全部工作, 那么它可以从其他消费者双端队列末尾秘密地获取工作。密取工作模式比传统的生产者-消费者模式具有更高的可伸缩性, 这是因为工作者线程不会在单个共享的任务队列上发生竞争。在大多数时候, 它们都只是访问自己的双端队列, 从而极大地减少了竞争。当工作者线程需要访问另一个队列时, 它会从队列的尾部而不是从头部获取工作, 因此进一步降低了队列上的竞争程度。
 >  
 >  工作密取非常适用于既是消费者也是生产者问题——当执行某个工作时可能导致出现更多的工作。例如, 在网页爬虫程序中处理一个页面时, 通常会发现有更多的页面需要处理。类似的还有许多搜索图的算法, 例如在垃圾回收阶段对堆进行标记, 都可以通过工作密取机制来实现高效并行。当一个工作线程找到新的任务单元时, 它会将其放到自己队列的末尾(或者在工作共享设计模式中, 放入其他工作者线程的队列中)。当双端队列为空时, 它会在另一个线程的队列队尾查找新的任务, 从而确保每个线程都保持忙碌状态。

上面的简单点说就是, 之前所有消费者去请求一个共享队列的锁, 现在每个消费者只需要使用自己的队列就好了, 竞争就小了。在自己的队列数据处理完后, 可以使用工作密取, 从其他队列中偷点数据。

### 5.4　阻塞方法与中断方法

 >  BlockingQueue 的 put 和 take 等方法会抛出受检查异常(Checked Exception)Interrupted-Exception, 这与类库中其他一些方法的做法相同, 例如 Thread.sleep。当某方法抛出 Interrupted-Exception 时, 表示该方法是一个阻塞方法, 如果这个方法被中断, 那么它将努力提前结束阻塞状态。

 >  Thread 提供了 interrupt 方法, 用于中断线程或者查询线程是否已经被中断。每个线程都有一个布尔类型的属性, 表示线程的中断状态, 当中断线程时将设置这个状态。

 >  中断是一种协作机制。一个线程不能强制其他线程停止正在执行的操作而去执行其他的操作。当线程 A 中断 B 时, A 仅仅是要求 B 在执行到某个可以暂停的地方停止正在执行的操作——前提是如果线程 B 愿意停止下来。

处理中断的响应：
1. 恢复中断。捕获中断异常, 并使用 Thread.currentThread().interrupt(); 恢复被中断的状态。
2. 传递中断异常。将异常传递给方法的调用者。

### 5.5　同步工具类

 >  同步工具类可以是任何一个对象, 只要它根据其自身的状态来协调线程的控制流。阻塞队列可以作为同步工具类, 其他类型的同步工具类还包括信号量(Semaphore)、栅栏(Barrier)以及闭锁(Latch)。在平台类库中还包含其他一些同步工具类的类, 如果这些类还无法满足需要, 那么可以按照第 14 章中给出的机制来创建自己的同步工具类。
 >  
 >  所有的同步工具类都包含一些特定的结构化属性：它们封装了一些状态, 这些状态将决定执行同步工具类的线程是继续执行还是等待, 此外还提供了一些方法对状态进行操作, 以及另一些方法用于高效地等待同步工具类进入到预期状态。

#### 5.5.1　闭锁

 >  闭锁是一种同步工具类, 可以延迟线程的进度直到其到达终止状态[CPJ 3.4.2]。闭锁的作用相当于一扇门：在闭锁到达结束状态之前, 这扇门一直是关闭的, 并且没有任何线程能通过, 当到达结束状态时, 这扇门会打开并允许所有的线程通过。当闭锁到达结束状态后, 将不会再改变状态, 因此这扇门将永远保持打开状态。
 >  
 >  闭锁可以用来确保某些活动直到其他活动都完成后才继续执行, 例如：
 >  - 确保某个计算在其需要的所有资源都被初始化之后才继续执行。二元闭锁(包括两个状态)可以用来表示“资源 R 已经被初始化”, 而所有需要 R 的操作都必须先在这个闭锁上等待。
 >  - 确保某个服务在其依赖的所有其他服务都已经启动之后才启动。每个服务都有一个相关的二元闭锁。当启动服务 S 时, 将首先在 S 依赖的其他服务的闭锁上等待, 在所有依赖的服务都启动后会释放闭锁 S, 这样其他依赖 S 的服务才能继续执行。
 >  - 等待直到某个操作的所有参与者(例如, 在多玩家游戏中的所有玩家)都就绪再继续执行。在这种情况中, 当所有玩家都准备就绪时, 闭锁将到达结束状态。

1 - 和第二个差不多？
2 - 开始时，闭锁=所依赖服务数。每个依赖的服务启动后，countDown。服务 S await 闭锁。
3 - 开始时，闭锁=nPlayer。每个 player 准备好后，countDown，然后 await 闭锁。

 >  CountDownLatch 是一种灵活的闭锁实现, 可以在上述各种情况中使用, 它可以使一个或多个线程等待一组事件发生。闭锁状态包括一个计数器, 该计数器被初始化为一个正数, 表示需要等待的事件数量。countDown 方法递减计数器, 表示有一个事件已经发生了, 而 await 方法等待计数器达到零, 这表示所有需要等待的事件都已经发生。如果计数器的值非零, 那么 await 会一直阻塞直到计数器为零, 或者等待中的线程中断, 或者等待超时。

 >  TestHarness 创建一定数量的线程, 利用它们并发地执行指定的任务。它使用两个闭锁, 分别表示“起始门(Starting Gate)”和“结束门(Ending Gate)”。起始门计数器的初始值为 1, 而结束门计数器的初始值为工作线程的数量。每个工作线程首先要做的值就是在启动门上等待, 从而确保所有线程都就绪后才开始执行。而每个线程要做的最后一件事情是将调用结束门的 countDown 方法减 1, 这能使主线程高效地等待直到所有工作线程都执行完成, 因此可以统计所消耗的时间。

下面实例中 CountDownLatch 两种使用方式：
1. 起始门。cnt = 1，所有线程启动后 await，主线程 countDown 来使所有线程一起开始执行任务。
2. 结束门。cnt = nthread，线程执行完后 countDown，主线程通过 await 来监测所有线程全部完成的最终时间。

```java
public class TestHarness{
	public long timeTasks(int nThreads,  final Runnable task)throws InterruptedException{
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

#### 5.5.2　FutureTask
 >  FutureTask 也可以用做闭锁。(FutureTask 实现了 Future 语义, 表示一种抽象的可生成结果的计算[CPJ 4.3.3])。FutureTask 表示的计算是通过 Callable 来实现的, 相当于一种可生成结果的 Runnable, 并且可以处于以下 3 种状态：等待运行(Waiting to run), 正在运行(Running)和运行完成(Completed)。“执行完成”表示计算的所有可能结束方式, 包括正常结束、由于取消而结束和由于异常而结束等。当 FutureTask 进入完成状态后, 它会永远停止在这个状态上。
 
FutureTask 类似闭锁，通过 get 等待任务处理结束并获得结果。

 >  Future.get 的行为取决于任务的状态。如果任务已经完成, 那么 get 会立即返回结果, 否则 get 将阻塞直到任务进入完成状态, 然后返回结果或者抛出异常。FutureTask 将计算结果从执行计算的线程传递到获取这个结果的线程, 而 FutureTask 的规范确保了这种传递过程能实现结果的安全发布。
 > 
 >  FutureTask 在 Executor 框架中表示异步任务, 此外还可以用来表示一些时间较长的计算, 这些计算可以在使用计算结果之前启动。
 >  
 >  数据处理后, 在需要的时候, 再通过 get 方法获取需要的结果。
 >  
 >  Callable 表示的任务可以抛出受检查的或未受检查的异常, 并且任何代码都可能抛出一个 Error。无论任务代码抛出什么异常, 都会被封装到一个 ExecutionException 中, 并在 Future.get 中被重新抛出。这将使调用 get 的代码变得复杂, 因为它不仅需要处理可能出现的 ExecutionException(以及未检查的 CancellationException), 而且还由于 ExecutionException 是作为一个 Throwable 类返回的, 因此处理起来并不容易。 

使用 instanceof 来判断是什么类型的异常, 然后进行强制类型转换与后续的处理。

#### 5.5.3　信号量

 >  计数信号量(Counting Semaphore)用来控制同时访问某个特定资源的操作数量, 或者同时执行某个指定操作的数量[CPJ 3.4.1]。计数信号量还可以用来实现某种资源池, 或者对容器施加边界。
 >  
 >  Semaphore 中管理着一组虚拟的许可(permit), 许可的初始数量可通过构造函数来指定。在执行操作时可以首先获得许可(只要还有剩余的许可), 并在使用以后释放许可。如果没有许可, 那么 acquire 将阻塞直到有许可(或者直到被中断或者操作超时)。release 方法将返回一个许可给信号量。[插图]计算信号量的一种简化形式是二值信号量, 即初始值为 1 的 Semaphore。二值信号量可以用做互斥体(mutex), 并具备不可重入的加锁语义：谁拥有这个唯一的许可, 谁就拥有了互斥锁。
 >  
 >  Semaphore 可以用于实现资源池, 例如数据库连接池。我们可以构造一个固定长度的资源池, 当池为空时, 请求资源将会失败, 但你真正希望看到的行为是阻塞而不是失败, 并且当池非空时解除阻塞。如果将 Semaphore 的计数值初始化为池的大小, 并在从池中获取一个资源之前首先调用 acquire 方法获取一个许可, 在将资源返回给池之后调用 release 释放许可, 那么 acquire 将一直阻塞直到资源池不为空。在第 12 章的有界缓冲类中将使用这项技术。(在构造阻塞对象池时, 一种更简单的方法是使用 BlockingQueue 来保存池的资源。)
 >  
 >  同样, 你也可以使用 Semaphore 将任何一种容器变成有界阻塞容器, 如程序清单 5-14 中的 BoundedHashSet 所示。信号量的计数值会初始化为容器容量的最大值。add 操作在向底层容器中添加一个元素之前, 首先要获取一个许可。如果 add 操作没有添加任何元素, 那么会立刻释放许可。同样, remove 操作释放一个许可, 使更多的元素能够添加到容器中。底层的 Set 实现并不知道关于边界的任何信息, 这是由 BoundedHashSet 来处理的。

通过信号量给容器设置边界：
这里的 set 是先请求一个位置，之后根据是否有数据来释放该位置。

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

#### 5.5.4　栅栏

 >  闭锁是一次性对象, 一旦进入终止状态, 就不能被重置。栅栏(Barrier)类似于闭锁, 它能阻塞一组线程直到某个事件发生[CPJ 4, 4.3]。
 >  
 >  栅栏与闭锁的关键区别在于, 所有线程必须同时到达栅栏位置, 才能继续执行。闭锁用于等待事件, 而栅栏用于等待其他线程。栅栏用于实现一些协议, 例如几个家庭决定在某个地方集合：“所有人 6：00 在麦当劳碰头, 到了以后要等其他人, 之后再讨论下一步要做的事情。”

 >  CyclicBarrier 可以使一定数量的参与方反复地在栅栏位置汇集, 它在并行迭代算法中非常有用：这种算法通常将一个问题拆分成一系列相互独立的子问题。当线程到达栅栏位置时将调用 await 方法, 这个方法将阻塞直到所有线程都到达栅栏位置。如果所有线程都到达了栅栏位置, 那么栅栏将打开, 此时所有线程都被释放, 而栅栏将被重置以便下次使用。如果对 await 的调用超时, 或者 await 阻塞的线程被中断, 那么栅栏就被认为是打破了, 所有阻塞的 await 调用都将终止并抛出 BrokenBarrierException。如果成功地通过栅栏, 那么 await 将为每个线程返回一个唯一的到达索引号, 我们可以利用这些索引来“选举”产生一个领导线程, 并在下一次迭代中由该领导线程执行一些特殊的工作。CyclicBarrier 还可以使你将一个栅栏操作传递给构造函数, 这是一个 Runnable, 当成功通过栅栏时会(在一个子任务线程中)执行它, 但在阻塞线程被释放之前是不能执行的。

 >  在模拟程序中通常需要使用栅栏, 例如某个步骤中的计算可以并行执行, 但必须等到该步骤中的所有计算都执行完毕才能进入下一个步骤。例如, 在 n-body 粒子模拟系统中, 每个步骤都根据其他粒子的位置和属性来计算各个粒子的新位置。通过在每两次更新之间等待栅栏, 能够确保在第 k 步中的所有更新操作都已经计算完毕, 才进入第 k+l 步。

```java
public class CellularAutomata {
    private final Board mainBoard;
    private final CyclicBarrier barrier;
    private final Worker[] workers;

    public CellularAutomata(Board board) {
        this.mainBoard = board;
        int count = Runtime.getRuntime().availableProcessors();
        this.barrier = new CyclicBarrier(count,
                new Runnable() {
                    public void run() {
                        mainBoard.commitNewValues();
                    }});
        this.workers = new Worker[count];
        for (int i = 0; i < count; i++)
            workers[i] = new Worker(mainBoard.getSubBoard(count, i));
    }

    private class Worker implements Runnable {
        private final Board board;

        public Worker(Board board) { this.board = board; }
        public void run() {
            while (!board.hasConverged()) {
                for (int x = 0; x < board.getMaxX(); x++)
                    for (int y = 0; y < board.getMaxY(); y++)
                        board.setNewValue(x, y, computeValue(x, y));
                try {
                    barrier.await();
                } catch (InterruptedException ex) {
                    return;
                } catch (BrokenBarrierException ex) {
                    return;
                }
            }
        }

        private int computeValue(int x, int y) {
            // Compute the new value that goes in (x,y)
            return 0;
        }
    }

    public void start() {
        for (int i = 0; i < workers.length; i++)
            new Thread(workers[i]).start();
        mainBoard.waitForConvergence();
    }

    interface Board {
        int getMaxX();
        int getMaxY();
        int getValue(int x, int y);
        int setNewValue(int x, int y, int value);
        void commitNewValues();
        boolean hasConverged();
        void waitForConvergence();
        Board getSubBoard(int numPartitions, int index);
    }
}
```

> 另一种形式的栅栏是 Exchanger，它是一种两方（Two-Party）栅栏，各方在栅栏位置上交换数据[CPJ 3.4.3]。当两方执行不对称的操作时，Exchanger 会非常有用，例如当一个线程向缓冲区写入数据，而另一个线程从缓冲区中读取数据。这些线程可以使用 Exchanger 来汇合，并将满的缓冲区与空的缓冲区交换。当两个线程通过 Exchanger 交换对象时，这种交换就把这两个对象安全地发布给另一方。数据交换的时机取决于应用程序的响应需求。最简单的方案是，当缓冲区被填满时，由填充任务进行交换，当缓冲区为空时，由清空任务进行交换。这样会把需要交换的次数降至最低，但如果新数据的到达率不可预测，那么一些数据的处理过程就将延迟。另一个方法是，不仅当缓冲被填满时进行交换，并且当缓冲被填充到一定程度并保持一定时间后，也进行交换。

有点难以理解。

### 5.6　构建高效且可伸缩的结果缓存

> 本节我们将开发一个高效且可伸缩的缓存，用于改进一个高计算开销的函数。

将一些非常耗时操作(这里是计算操作)的结果缓存下来，同时尽可能地减少计算次数和等待时间。

提升步骤：
1. HashMap 来存储缓存数据。对于计算操作使用 Synchronized 来加锁。计算操作先判断缓存中是否存在，不存在则计算并加入缓存。【这种显然不好，为了保证 HashMap 不会同时访问，使用对计算操作进行加锁。导致一整个计算操作都在一个锁中了，锁占用了太长时间】
2. ConcurrentHashMap 来存储缓存数据。计算操作不加锁，若缓存中不存在，则计算后 put 到缓存中。【相较于方法 1，避免了锁占用太长时间。但是，若缓存中不存在 key=1 对应的 value，那么一个线程在 compute(1)的时候，另一个线程也会 compute(1)。即对应 value 没有计算出来时，其他线程也会去计算这个数据，导致重复计算。】
3. ConcurrentHashMap 来存储缓存 FutureTask 对象。计算操作不加锁，若缓存中不存在 key，则生成 FutureTask 对象加入其中，然后 compute。其他线程发现存在 FutureTask，则使用 get 方法获取数据。若计算完成直接获取，没有计算完成先阻塞。【使用 FutureTask 对象避免了重复计算的问题，但是编写代码时很容易忘记，先检查后执行是属于竞态条件。】
4. 在上述的基础上，使用 putIfAbsent 函数，避免了竞态条件。不过，官方上使用了 while 循环，还没有了解是什么作用。可能是防止计算失败，若计算失败，则删除该 FutureTask，然后重新加入新的 FutureTask。

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

> 当缓存的是 Future 而不是值时，将导致缓存污染（Cache Pollution）问题：如果某个计算被取消或者失败，那么在计算这个结果时将指明计算过程被取消或者失败。为了避免这种情况，如果 Memoizer 发现计算被取消，那么将把 Future 从缓存中移除。如果检测到 RuntimeException，那么也会移除 Future，这样将来的计算才可能成功。
> 
> Memoizer 同样没有解决缓存逾期的问题，但它可以通过使用 FutureTask 的子类来解决，在子类中为每个结果指定一个逾期时间，并定期扫描缓存中逾期的元素。（同样，它也没有解决缓存清理的问题，即移除旧的计算结果以便为新的计算结果腾出空间，从而使缓存不会消耗过多的内存。）

## 第一部分总结

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

## 第 6 章 - 任务执行
### 6.1　在线程中执行任务
串行执行指令
```java
public class SingleThreadWebServer {
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            Socket connection = socket.accept();
            handleRequest(connection);
        }
    }

    private static void handleRequest(Socket connection) {
        // request-handling logic here
    }
}
```

创建线程异步执行指令
```java
public class ThreadPerTaskWebServer {
    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            final Socket connection = socket.accept();
            Runnable task = new Runnable() {
                public void run() {
                    handleRequest(connection);
                }
            };
            new Thread(task).start();
        }
    }

    private static void handleRequest(Socket connection) {
        // request-handling logic here
    }
}
```

> 对于每个连接，主循环都将创建一个新线程来处理请求，而不是在主循环中进行处理。由此可得出 3 个主要结论：
> - 任务处理过程从主线程中分离出来，使得主循环能够更快地重新等待下一个到来的连接。这使得程序在完成前面的请求之前可以接受新的请求，从而提高响应性。
> - 任务可以并行处理，从而能同时服务多个请求。如果有多个处理器，或者任务由于某种原因被阻塞，例如等待 I/O 完成、获取锁或者资源可用性等，程序的吞吐量将得到提高。
> - 任务处理代码必须是线程安全的，因为当有多个任务时会并发地调用这段代码。

当然，如果一直创建线程也可能会出现错误。如线程创建太多，导致内存爆掉或者超线程上限。同时，线程创建销毁也消耗时间。

### 6.2　Executor 框架

> 在第 5 章中，我们介绍了如何通过有界队列来防止高负荷的应用程序耗尽内存。线程池简化了线程的管理工作，并且 java.util.concurrent 提供了一种灵活的线程池实现作为 Executor 框架的一部分。在 Java 类库中，任务执行的主要抽象不是 Thread，而是 Executor.

> 虽然 Executor 是个简单的接口，但它却为灵活且强大的异步任务执行框架提供了基础，该框架能支持多种不同类型的任务执行策略。它提供了一种标准的方法将任务的提交过程与执行过程解耦开来，并用 Runnable 来表示任务。Executor 的实现还提供了对生命周期的支持，以及统计信息收集、应用程序管理机制和性能监视等机制。Executor 基于生产者-消费者模式，提交任务的操作相当于生产者（生成待完成的工作单元），执行任务的线程则相当于消费者（执行完这些工作单元）。如果要在程序中实现一个生产者-消费者的设计，那么最简单的方式通常就是使用 Executor。

```java
public interface Executor{void execute（Runnable command）；}
```

[Executor (Java 2 Platform SE 5.0)](https://docs.oracle.com/javase/1.5.0/docs/api/java/util/concurrent/Executor.html#method_summary)

#### 6.2.1　示例：基于 Executor 的 Web 服务器

```java
public class TaskExecutionWebServer {
    private static final int NTHREADS = 100;
    private static final Executor exec
            = Executors.newFixedThreadPool(NTHREADS);

    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            final Socket connection = socket.accept();
            Runnable task = new Runnable() {
                public void run() {
                    handleRequest(connection);
                }
            };
            exec.execute(task);
        }
    }

    private static void handleRequest(Socket connection) {
        // request-handling logic here
    }
}
```

每个请求启动一个新线程的 Executor
```java
public class ThreadPerTaskExecutor implements Executor {
    public void execute(Runnable r) {
        new Thread(r).start();
    };
}
```

在调用线程中以同步方式执行所有任务的 Executor
```java
public class WithinThreadExecutor implements Executor {
    public void execute(Runnable r) {
        r.run();
    };
}
```

#### 6.2.2　执行策略

> 通过将任务的提交与执行解耦开来，从而无须太大的困难就可以为某种类型的任务指定和修改执行策略。在执行策略中定义了任务执行的“What、Where、When、How”等方面，包括：
> - 在什么（What）线程中执行任务？
> - 任务按照什么（What）顺序执行（FIFO、LIFO、优先级）?
> - 有多少个（How Many）任务能并发执行？
> - 在队列中有多少个（How Many）任务在等待执行？
> - 如果系统由于过载而需要拒绝一个任务，那么应该选择哪一个（Which）任务？
> - 另外，如何（How）通知应用程序有任务被拒绝？
> - 在执行一个任务之前或之后，应该进行哪些（What）动作？

#### 6.2.3　线程池

> 类库提供了一个灵活的线程池以及一些有用的默认配置。可以通过调用 Executors 中的静态工厂方法之一来创建一个线程池：
> - newFixedThreadPool。newFixedThreadPool 将创建一个固定长度的线程池，每当提交一个任务时就创建一个线程，直到达到线程池的最大数量，这时线程池的规模将不再变化（如果某个线程由于发生了未预期的 Exception 而结束，那么线程池会补充一个新的线程）。
> - newCachedThreadPool。newCachedThreadPool 将创建一个可缓存的线程池，如果线程池的当前规模超过了处理需求时，那么将回收空闲的线程，而当需求增加时，则可以添加新的线程，线程池的规模不存在任何限制。
> - newSingleThreadExecutor。newSingleThreadExecutor 是一个单线程的 Executor，它创建单个工作者线程来执行任务，如果这个线程异常结束，会创建另一个线程来替代。newSingleThreadExecutor 能确保依照任务在队列中的顺序来串行执行（例如 FIFO、LIFO、优先级）。
>   单线程的 Executor 还提供了大量的内部同步机制，从而确保了任务执行的任何内存写入操作对于后续任务来说都是可见的。这意味着，即使这个线程会不时地被另一个线程替代，但对象总是可以安全地封闭在“任务线程”中。
> - newScheduledThreadPool。newScheduledThreadPool 创建了一个固定长度的线程池，而且以延迟或定时的方式来执行任务，类似于 Timer（参见 6.2.5 节）。
>
> newFixedThreadPool 和 newCachedThreadPool 这两个工厂方法返回通用的 ThreadPool-Executor 实例，这些实例可以直接用来构造专门用途的 executor。我们将在第 8 章中深入讨论线程池的各个配置选项。
> 
> TaskExecutionWebServer 中的 Web 服务器使用了一个带有有界线程池的 Executor。通过 execute 方法将任务提交到工作队列中，工作线程反复地从工作队列中取出任务并执行它们。

> 尽管服务器不会因为创建了过多的线程而失败，但在足够长的时间内，如果任务到达的速度总是超过任务执行的速度，那么服务器仍有可能（只是更不易）耗尽内存，因为等待执行的 Runnable 队列将不断增长。可以通过使用一个有界工作队列在 Executor 框架内部解决这个问题（参见 8.3.2 节）。

#### 6.2.4　Executor 的生命周期

> Executor 的实现通常会创建线程来执行任务。但 JVM 只有在所有（非守护）线程全部终止后才会退出。因此，如果无法正确地关闭 Executor，那么 JVM 将无法结束。由于 Executor 以异步方式来执行任务，因此在任何时刻，之前提交任务的状态不是立即可见的。有些任务可能已经完成，有些可能正在运行，而其他的任务可能在队列中等待执行。

为了解决执行服务的生命周期问题，Executor 扩展了 ExecutorService 接口，添加了一些用于生命周期管理的方法（同时还有一些用于任务提交的便利方法）。

```java
public interface ExecutorService extends Executor{
void shutdown（）；
List＜Runnable＞shutdownNow（）；
boolean isShutdown（）；
boolean isTerminated（）；
boolean awaitTermination（long timeout, TimeUnit unit）throws InterruptedException；
//……其他用于任务提交的便利方法
}
```

> ExecutorService 的生命周期有 3 种状态：运行、关闭和已终止。
> - ExecutorService 在初始创建时处于运行状态。
> - shutdown 方法将执行平缓的关闭过程：不再接受新的任务，同时等待已经提交的任务执行完成——包括那些还未开始执行的任务。
> - shutdownNow 方法将执行粗暴的关闭过程：它将尝试取消所有运行中的任务，并且不再启动队列中尚未开始执行的任务。

> 在 ExecutorService 关闭后提交的任务将由“拒绝执行处理器（RejectedExecution Handler）”来处理（请参见 8.3.3 节），它会抛弃任务，或者使得 execute 方法抛出一个未检查的 Rejected-ExecutionException。等所有任务都完成后，ExecutorService 将转入终止状态。
> 
> 可以调用 awaitTermination 来等待 ExecutorService 到达终止状态，或者通过调用 isTerminated 来轮询 ExecutorService 是否已经终止。
> 
> 通常在调用 awaitTermination 之后会立即调用 shutdown，从而产生同步地关闭 ExecutorService 的效果。

> 通过增加生命周期支持来扩展 Web 服务器的功能。可以通过两种方法来关闭 Web 服务器：在程序中调用 stop，或者以客户端请求形式向 Web 服务器发送一个特定格式的 HTTP 请求。
```java
public class LifecycleWebServer {
    private final ExecutorService exec = Executors.newCachedThreadPool();

    public void start() throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (!exec.isShutdown()) {
            try {
                final Socket conn = socket.accept();
                exec.execute(new Runnable() {
                    public void run() {
                        handleRequest(conn);
                    }
                });
            } catch (RejectedExecutionException e) {
                if (!exec.isShutdown())
                    log("task submission rejected", e);
            }
        }
    }

    public void stop() {
        exec.shutdown();
    }

    private void log(String msg, Exception e) {
        Logger.getAnonymousLogger().log(Level.WARNING, msg, e);
    }

    void handleRequest(Socket connection) {
        Request req = readRequest(connection);
        if (isShutdownRequest(req))
            stop();
        else
            dispatchRequest(req);
    }
}
```


#### 6.2.5　延迟任务与周期任务

> Timer 类负责管理延迟任务（“在 100ms 后执行该任务”）以及周期任务（“每 l0ms 执行一次该任务”）。然而，Timer 存在一些缺陷，因此应该考虑使用 ScheduledThreadPoolExecutor 来代替它[插图]。可以通过 ScheduledThreadPoolExecutor 的构造函数或 newScheduledThreadPool 工厂方法来创建该类的对象。

> Timer 支持基于绝对时间而不是相对时间的调度机制，因此任务的执行对系统时钟变化很敏感，而 ScheduledThreadPoolExecutor 只支持基于相对时间的调度。
> 
> Timer 在执行所有定时任务时只会创建一个线程。如果某个任务的执行时间过长，那么将破坏其他 TimerTask 的定时精确性。例如某个周期 TimerTask 需要每 10ms 执行一次，而另一个 TimerTask 需要执行 40ms，那么这个周期任务或者在 40ms 任务执行完成后快速连续地调用 4 次，或者彻底“丢失”4 次调用（取决于它是基于固定速率来调度还是基于固定延时来调度）。线程池能弥补这个缺陷，它可以提供多个线程来执行延时任务和周期任务。
> 
> Timer 的另一个问题是，如果 TimerTask 抛出了一个未检查的异常，那么 Timer 将表现出糟糕的行为。Timer 线程并不捕获异常，因此当 TimerTask 抛出未检查的异常时将终止定时线程。这种情况下，Timer 也不会恢复线程的执行，而是会错误地认为整个 Timer 都被取消了。因此，已经被调度但尚未执行的 TimerTask 将不会再执行，新的任务也不能被调度。（这个问题称之为“线程泄漏[Thread Leakage]”，7.3 节将介绍该问题以及如何避免它。）

> Timer 中为什么会出现这种问题，以及如何使得试图提交 TimerTask 的调用者也出现问题。你可能认为程序会运行 6 秒后退出，但实际情况是运行 1 秒就结束了，并抛出了一个异常消息“Timer alreadycancelled”。ScheduledThreadPoolExecutor 能正确处理这些表现出错误行为的任务。
```java
public class OutOfTime {
    public static void main(String[] args) throws Exception {
        Timer timer = new Timer();
        timer.schedule(new ThrowTask(), 1);
        SECONDS.sleep(1);
        timer.schedule(new ThrowTask(), 1);
        SECONDS.sleep(5);
    }

    static class ThrowTask extends TimerTask {
        public void run() {
            throw new RuntimeException();
        }
    }
}
```

> 如果要构建自己的调度服务，那么可以使用 DelayQueue，它实现了 BlockingQueue，并为 ScheduledThreadPoolExecutor 提供调度功能。DelayQueue 管理着一组 Delayed 对象。每个 Delayed 对象都有一个相应的延迟时间：在 DelayQueue 中，只有某个元素逾期后，才能从 DelayQueue 中执行 take 操作。从 DelayQueue 中返回的对象将根据它们的延迟时间进行排序。


### 6.3　找出可利用的并行性

使用渲染包含标签文本、预定大小图片和 URL 的页面。

#### 6.3.1　示例：串行的页面渲染器

> 最简单的方法就是对 HTML 文档进行串行处理。当遇到文本标签时，将其绘制到图像缓存中。当遇到图像引用时，先通过网络获取它，然后再将其绘制到图像缓存中。
> 另一种串行执行方法更好一些，它先绘制文本元素，同时为图像预留出矩形的占位空间，在处理完了第一遍文本后，程序再开始下载图像，并将它们绘制到相应的占位空间中。

```java
public abstract class SingleThreadRenderer {
    void renderPage(CharSequence source) {
        renderText(source);
        List<ImageData> imageData = new ArrayList<ImageData>();
        for (ImageInfo imageInfo : scanForImageInfo(source))
            imageData.add(imageInfo.downloadImage());
        for (ImageData data : imageData)
            renderImage(data);
    }

    interface ImageData {
    }

    interface ImageInfo {
        ImageData downloadImage();
    }

    abstract void renderText(CharSequence s);
    abstract List<ImageInfo> scanForImageInfo(CharSequence s);
    abstract void renderImage(ImageData i);
```

#### 6.3.2　携带结果的任务 Callable 与 Future

[Callable (Java 2 Platform SE 5.0)](https://docs.oracle.com/javase/1.5.0/docs/api/java/util/concurrent/Callable.html#method_summary)  [Future (Java 2 Platform SE 5.0)](https://docs.oracle.com/javase/1.5.0/docs/api/java/util/concurrent/Future.html#method_summary)

> Executor 框架使用 Runnable 作为其基本的任务表示形式。Runnable 是一种有很大局限的抽象，虽然 run 能写入到日志文件或者将结果放入某个共享的数据结构，但它不能返回一个值或抛出一个受检查的异常。

> 许多任务实际上都是存在延迟的计算——执行数据库查询，从网络上获取资源，或者计算某个复杂的功能。对于这些任务，Callable 是一种更好的抽象：它认为主入口点（即 call）将返回一个值，并可能抛出一个异常。[插图]在 Executor 中包含了一些辅助方法能将其他类型的任务封装为一个 Callable，例如 Runnable 和 java.security.PrivilegedAction。
> 
> Executor 执行的任务有 4 个生命周期阶段：创建、提交、开始和完成。由于有些任务可能要执行很长的时间，因此通常希望能够取消这些任务。在 Executor 框架中，已提交但尚未开始的任务可以取消，但对于那些已经开始执行的任务，只有当它们能响应中断时，才能取消。取消一个已经完成的任务不会有任何影响。（第 7 章将进一步介绍取消操作。）
> 
> Future 表示一个任务的生命周期，并提供了相应的方法来判断是否已经完成或取消，以及获取任务的结果和取消任务等。在程序清单 6-11 中给出了 Callable 和 Future。在 Future 规范中包含的隐含意义是，任务的生命周期只能前进，不能后退，就像 ExecutorService 的生命周期一样。当某个任务完成后，它就永远停留在“完成”状态上。
> 
> get 方法的行为取决于任务的状态（尚未开始、正在运行、已完成）。如果任务已经完成，那么 get 会立即返回或者抛出一个 Exception，如果任务没有完成，那么 get 将阻塞并直到任务完成。如果任务抛出了异常，那么 get 将该异常封装为 ExecutionException 并重新抛出。如果任务被取消，那么 get 将抛出 CancellationException。如果 get 抛出了 ExecutionException，那么可以通过 getCause 来获得被封装的初始异常。
> 
> 可以通过许多种方法创建一个 Future 来描述任务。ExecutorService 中的所有 submit 方法都将返回一个 Future，从而将一个 Runnable 或 Callable 提交给 Executor，并得到一个 Future 用来获得任务的执行结果或者取消任务。还可以显式地为某个指定的 Runnable 或 Callable 实例化一个 FutureTask。（由于 FutureTask 实现了 Runnable，因此可以将它提交给 Executor 来执行，或者直接调用它的 run 方法。）从 Java 6 开始，ExecutorService 实现可以改写 AbstractExecutorService 中的 newTaskFor 方法，从而根据已提交的 Runnable 或 Callable 来控制 Future 的实例化过程。
> 
> 在将 Runnable 或 Callable 提交到 Executor 的过程中，包含了一个安全发布过程（请参见 3.5 节），即将 Runnable 或 Callable 从提交线程发布到最终执行任务的线程。类似地，在设置 Future 结果的过程中也包含了一个安全发布，即将这个结果从计算它的线程发布到任何通过 get 获得它的线程。

Runnable 和 Callable 区别 (均是要执行的内容)
(1)Runnable 是自从 java1.1 就有了，而 Callable 是 1.5 之后才加上去的  
(2)Callable 规定的方法是 call(), Runnable 规定的方法是 run()  
(3)Callable 的任务执行后可返回值，而 Runnable 的任务是不能返回值(是 void)  
(4)call 方法可以抛出异常，run 方法不可以  
(5)运行 Callable 任务可以拿到一个 Future 对象，表示异步计算的结果。它提供了检查计算是否完成的方法，以等待计算的完成，并检索计算的结果。通过 Future 对象可以了解任务执行情况，可取消任务的执行，还可获取执行结果。  
(6)加入线程池运行，Runnable 使用 ExecutorService 的 execute 方法，Callable 使用 submit 方法。

#### 6.3.3　示例：使用 Future 实现页面渲染器

> 为了使页面渲染器实现更高的并发性，首先将渲染过程分解为两个任务，一个是渲染所有的文本，另一个是下载所有的图像。（因为其中一个任务是 CPU 密集型，而另一个任务是 I/O 密集型，因此这种方法即使在单 CPU 系统上也能提升性能。）

这里先 submit IO 密集型任务，然后执行 CPU 密集型任务，最后回过头来获得 IO 任务的结果。尽力避免了当前线程一直等待。

```java
public abstract class FutureRenderer {
    private final ExecutorService executor = Executors.newCachedThreadPool();

    void renderPage(CharSequence source) {
        final List<ImageInfo> imageInfos = scanForImageInfo(source);
        Callable<List<ImageData>> task =
                new Callable<List<ImageData>>() {
                    public List<ImageData> call() {
                        List<ImageData> result = new ArrayList<ImageData>();
                        for (ImageInfo imageInfo : imageInfos)
                            result.add(imageInfo.downloadImage());
                        return result;
                    }
                };

        Future<List<ImageData>> future = executor.submit(task); 
        renderText(source);

        try {
            List<ImageData> imageData = future.get();
            for (ImageData data : imageData)
                renderImage(data);
        } catch (InterruptedException e) {
            // Re-assert the thread's interrupted status
            Thread.currentThread().interrupt();
            // We don't need the result, so cancel the task too
            future.cancel(true);
        } catch (ExecutionException e) {
            throw launderThrowable(e.getCause());
        }
    }

    interface ImageData {    }
    interface ImageInfo {
        ImageData downloadImage();
    }
    abstract void renderText(CharSequence s);
    abstract List<ImageInfo> scanForImageInfo(CharSequence s);
    abstract void renderImage(ImageData i);
}
```

> get 方法拥有“状态依赖”的内在特性，因而调用者不需要知道任务的状态，此外在任务提交和获得结果中包含的安全发布属性也确保了这个方法是线程安全的。
> 
> Future.get 的异常处理代码将处理两个可能的问题：任务遇到一个 Exception，或者调用 get 的线程在获得结果之前被中断.

> 还可以做得更好。用户不必等到所有的图像都下载完成，而希望看到每当下载完一幅图像时就立即显示出来。

#### 6.3.4　在异构任务并行化中存在的局限

不同的任务进行最佳分配是困难的，例如上面的处理文本和处理图片。当可以使用的线程数增加，或任务的数量增加，进行最佳分配也会是困难的。同时，在多个线程中分解任务也需要一定的任务协调开销。总之，这是很难去解决的事。

上面的处理文本和处理图片，处理文本的速度应该比处理图片快很多。那么其实存在，串行执行和并行执行其实时间销毁差别并不是很大，但是代码非常复杂！！！！

综上，并行执行任务，获得的并发性也是有限的。

> 只有当大量相互独立且同构的任务可以并发进行处理时，才能体现出将程序的工作负载分配到多个任务中带来的真正性能提升。

#### 6.3.5　CompletionService：Executor 与 BlockingQueue

为了更快的获得一组计算的处理结果，可以保留每个任务相关联的 Future，然后使用 get(timeout=0)方法轮询任务是否完成。但仍有更好的处理方法：CompletionService。

> CompletionService 将 Executor 和 BlockingQueue 的功能融合在一起。你可以将 Callable 任务提交给它来执行，然后使用类似于队列操作的 take 和 poll 等方法来获得已完成的结果，而这些结果会在完成时将被封装为 Future。ExecutorCompletionService 实现了 CompletionService，并将计算部分委托给一个 Executor。
> 
> ExecutorCompletionService 的实现非常简单。在构造函数中创建一个 BlockingQueue 来保存计算完成的结果。当计算完成时，调用 Future-Task 中的 done 方法。当提交某个任务时，该任务将首先包装为一个 QueueingFuture，这是 FutureTask 的一个子类，然后再改写子类的 done 方法，并将结果放入 BlockingQueue 中，如程序清单 6-14 所示。take 和 poll 方法委托给了 BlockingQueue，这些方法会在得出结果之前阻塞。
> 
> `protected void done（）{completionQueue.add（this）；}`

#### 6.3.6　示例：使用 CompletionService 实现页面渲染器

为每幅图像创建一个线程去处理，从而将串行任务变为并行任务。使用 CompletionService，使得每个图像处理好后就可以直接显示。

```java
public abstract class Renderer {
    private final ExecutorService executor;
    Renderer(ExecutorService executor) {
        this.executor = executor;
    }

    void renderPage(CharSequence source) {
        final List<ImageInfo> info = scanForImageInfo(source);
        CompletionService<ImageData> completionService =
                new ExecutorCompletionService<ImageData>(executor);
        for (final ImageInfo imageInfo : info)
            completionService.submit(new Callable<ImageData>() {
                public ImageData call() {
                    return imageInfo.downloadImage();
                }
            });

        renderText(source);

        try {
            for (int t = 0, n = info.size(); t < n; t++) {
                Future<ImageData> f = completionService.take();  // 取一个已经处理完的任务关联的 Future
                ImageData imageData = f.get(); // 使用get获得任务结果
                renderImage(imageData);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw launderThrowable(e.getCause());
        }
    }

    interface ImageData {    }
    interface ImageInfo {        ImageData downloadImage();    }
    abstract void renderText(CharSequence s);
    abstract List<ImageInfo> scanForImageInfo(CharSequence s);
    abstract void renderImage(ImageData i);
}
```

> 多个 ExecutorCompletionService 可以共享一个 Executor，因此可以创建一个对于特定计算私有，又能共享一个公共 Executor 的 ExecutorCompletionService。因此，CompletionService 的作用就相当于一组计算的句柄，这与 Future 作为单个计算的句柄是非常类似的。通过记录提交给 CompletionService 的任务数量，并计算出已经获得的已完成结果的数量，即使使用一个共享的 Executor，也能知道已经获得了所有任务结果的时间。

#### 6.3.7　为任务设置时限

超时则不再执行，使用默认数据替代，并且降低网站的响应性能。

> 持时间限制的 Future.get 中支持这种需求：当结果可用时，它将立即返回，如果在指定时限内没有计算出结果，那么将抛出 TimeoutException。

```java
public class RenderWithTimeBudget {
    private static final Ad DEFAULT_AD = new Ad();
    private static final long TIME_BUDGET = 1000;
    private static final ExecutorService exec = Executors.newCachedThreadPool();

    Page renderPageWithAd() throws InterruptedException {
        long endNanos = System.nanoTime() + TIME_BUDGET;
        Future<Ad> f = exec.submit(new FetchAdTask());
        // Render the page while waiting for the ad
        Page page = renderPageBody();
        Ad ad;
        try {
            // Only wait for the remaining time budget
            long timeLeft = endNanos - System.nanoTime();
            ad = f.get(timeLeft, NANOSECONDS);
        } catch (ExecutionException e) {
            ad = DEFAULT_AD;
        } catch (TimeoutException e) {
            ad = DEFAULT_AD;
            f.cancel(true);
        }
        page.setAd(ad);
        return page;
    }

    Page renderPageBody() { return new Page(); }
    static class Ad {    }
    static class Page {        public void setAd(Ad ad) { }    }

    static class FetchAdTask implements Callable<Ad> {
        public Ad call() {
            return new Ad();
        }
    }

}
```

#### 6.3.8　示例：旅行预定门户网站

> 创建 n 个任务，将其提交到一个线程池，保留 n 个 Future，并使用限时的 get 方法通过 Future 串行地获取每一个结果，这一切都很简单，但还有一个更简单的方法——invokeAll。
> 
> InvokeAll 方法的参数为一组任务，并返回一组 Future。这两个集合有着相同的结构。invokeAll 按照任务集合中迭代器的顺序将所有的 Future 添加到返回的集合中，从而使调用者能将各个 Future 与其表示的 Callable 关联起来。
> 
> 当所有任务都执行完毕时，或者调用线程被中断时，又或者超过指定时限时，invokeAll 将返回。当超过指定时限后，任何还未完成的任务都会取消。当 invokeAll 返回后，每个任务要么正常地完成，要么被取消，而客户端代码可以调用 get 或 isCancelled 来判断究竟是何种情况。

> 在预定时间内请求旅游报价
```java
public class TimeBudget {
    private static ExecutorService exec = Executors.newCachedThreadPool();

    public List<TravelQuote> getRankedTravelQuotes(TravelInfo travelInfo, Set<TravelCompany> companies,
                                                   Comparator<TravelQuote> ranking, long time, TimeUnit unit)
            throws InterruptedException {
        List<QuoteTask> tasks = new ArrayList<QuoteTask>();
        for (TravelCompany company : companies)
            tasks.add(new QuoteTask(company, travelInfo));

        List<Future<TravelQuote>> futures = exec.invokeAll(tasks, time, unit);

        List<TravelQuote> quotes =
                new ArrayList<TravelQuote>(tasks.size());
        Iterator<QuoteTask> taskIter = tasks.iterator();
        for (Future<TravelQuote> f : futures) {
            QuoteTask task = taskIter.next();
            try {
                quotes.add(f.get());
            } catch (ExecutionException e) {
                quotes.add(task.getFailureQuote(e.getCause()));
            } catch (CancellationException e) {
                quotes.add(task.getTimeoutQuote(e));
            }
        }

        Collections.sort(quotes, ranking);
        return quotes;
    }

}

class QuoteTask implements Callable<TravelQuote> {
    private final TravelCompany company;
    private final TravelInfo travelInfo;

    public QuoteTask(TravelCompany company, TravelInfo travelInfo) {
        this.company = company;
        this.travelInfo = travelInfo;
    }

    TravelQuote getFailureQuote(Throwable t) {
        return null;
    }

    TravelQuote getTimeoutQuote(CancellationException e) {
        return null;
    }

    public TravelQuote call() throws Exception {
        return company.solicitQuote(travelInfo);
    }
}

interface TravelCompany {
    TravelQuote solicitQuote(TravelInfo travelInfo) throws Exception;
}

interface TravelQuote {
}

interface TravelInfo {
}
```

## 第 7 章 - 取消与关闭

### 7.1　任务取消

1. 一个行为良好的软件应该完善地处理失败、关闭和取消过程。
2. Java 没有安全的抢占式方法立刻停止线程，只有一些协作机制。
3. 一个可取消的任务需要有取消策略（Cancellation Policy），定义如何取消(How)，何时取消(When)，响应取消应该做什么(What)。

这一小结，提供了一个设置 boolean 标志位的方式，执行期间检查标志位判断是否应该取消。但是，这种方法存在问题。若执行期间线程阻塞了，那么可能就无法一直判断标志位，从而无法结束线程。例如，生产者-消费者模式中，消费者线程 GG 了，那么若队列满了，生产者就阻塞并无法判断标志位。

下一节，引入了中断 Interrupt 的概念。

#### 7.1.1　中断

> 每个线程都有一个 boolean 类型的中断状态。当中断线程时，这个线程的中断状态将被设置为 true。在 Thread 中包含了中断线程以及查询线程中断状态的方法，如程序清单 7-4 所示。
> - interrupt 方法能中断目标线程，
> - isInterrupted 方法能返回目标线程的中断状态。
> - 静态的 interrupted 方法将清除当前线程的中断状态，并返回它之前的值，这也是清除中断状态的唯一方法。

> 阻塞库方法，例如 Thread.sleep 和 Object.wait 等，都会检查线程何时中断，并且在发现中断时提前返回。它们在响应中断时执行的操作包括：清除中断状态，抛出 InterruptedException，表示阻塞操作由于中断而提前结束。JVM 并不能保证阻塞方法检测到中断的速度，但在实际情况中响应速度还是非常快的。
> 
> 当线程在非阻塞状态下中断时，它的中断状态将被设置，然后根据将被取消的操作来检查中断状态以判断发生了中断。通过这样的方法，中断操作将变得“有黏性”——如果不触发 InterruptedException，那么中断状态将一直保持，直到明确地清除中断状态。
> 
> 调用 interrupt 并不意味着立即停止目标线程正在进行的工作，而只是传递了请求中断的消息。
> 
> 对中断操作的正确理解是：它并不会真正地中断一个正在运行的线程，而只是发出中断请求，然后由线程在下一个合适的时刻中断自己。（这些时刻也被称为取消点）。有些方法，例如 wait、sleep 和 join 等，将严格地处理这种请求，当它们收到中断请求或者在开始执行时发现某个已被设置好的中断状态时，将抛出一个异常。设计良好的方法可以完全忽略这种请求，只要它们能使调用代码对中断请求进行某种处理。设计糟糕的方法可能会屏蔽中断请求，从而导致调用栈中的其他代码无法对中断请求作出响应。
> 
> 在使用静态的 interrupted 时应该小心，因为它会清除当前线程的中断状态。如果在调用 interrupted 时返回了 true，那么除非你想屏蔽这个中断，否则必须对它进行处理——可以抛出 InterruptedException，或者通过再次调用 interrupt 来恢复中断状态，如程序清单 5-10 所示。
> 
> 中断是实现取消的最合理方式。

```java
public class PrimeProducer extends Thread {
    private final BlockingQueue<BigInteger> queue;

    PrimeProducer(BlockingQueue<BigInteger> queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            BigInteger p = BigInteger.ONE;
            while (!Thread.currentThread().isInterrupted())
                queue.put(p = p.nextProbablePrime());
        } catch (InterruptedException consumed) {
            /* Allow thread to exit */
        }
    }

    public void cancel() {
        interrupt();
    }
}
```

#### 7.1.2　中断策略



#### 7.1.4 示例：计时运行

方法一：另开一个线程，计时，计时结束调用 interrupt。
这种破坏了规则：在中断线程之前，应该了解它的中断策略。问题一，线程提前完成了，那么此时再发送一个 interrupt，不一定出现什么问题。问题二，线程不响应中断，那么就会超时，带来一定问题。

> 在程序清单 7-9 中解决了 aSecondOfPrimes 的异常处理问题以及之前解决方案中的问题。执行任务的线程拥有自己的执行策略，即使任务不响应中断，限时运行的方法仍能返回到它的调用者。在启动任务线程之后，timedRun 将执行一个限时的 join 方法。在 join 返回后，它将检查任务中是否有异常抛出，如果有的话，则会在调用 timedRun 的线程中再次抛出该异常。由于 Throwable 将在两个线程之间共享，因此该变量被声明为 volatile 类型，从而确保安全地将其从任务线程发布到 timedRun 线程。

```java
public class TimedRun2 {
    private static final ScheduledExecutorService cancelExec = newScheduledThreadPool(1);

    public static void timedRun(final Runnable r,
                                long timeout, TimeUnit unit)
            throws InterruptedException {
        class RethrowableTask implements Runnable {
            private volatile Throwable t;

            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    this.t = t;
                }
            }

            void rethrow() {
                if (t != null)
                    throw launderThrowable(t);
            }
        }

        RethrowableTask task = new RethrowableTask();
        final Thread taskThread = new Thread(task);
        taskThread.start();
        cancelExec.schedule(new Runnable() {
            public void run() {
                taskThread.interrupt();
            }
        }, timeout, unit);
        taskThread.join(unit.toMillis(timeout));
        task.rethrow();
    }
}
```

在这个示例的代码中解决了前面示例中的问题，但由于它依赖于一个限时的 join，因此存在着 join 的不足：无法知道执行控制是因为线程正常退出而返回还是因为 join 超时而返回。

#### 7.1.5　通过 Future 来实现取消

使用 Future 来完成限时任务。

> ExecutorService.submit 将返回一个 Future 来描述任务。Future 拥有一个 cancel 方法，该方法带有一个 boolean 类型的参数 mayInterruptIfRunning，表示取消操作是否成功。（这只是表示任务是否能够接收中断，而不是表示任务是否能检测并处理中断。）如果 mayInterruptIfRunning 为 true 并且任务当前正在某个线程中运行，那么这个线程能被中断。如果这个参数为 false，那么意味着“若任务还没有启动，就不要运行它”，这种方式应该用于那些不处理中断的任务中。

```java
public class TimedRun {
    private static final ExecutorService taskExec = Executors.newCachedThreadPool();

    public static void timedRun(Runnable r, long timeout, TimeUnit unit)
            throws InterruptedException {
        Future<?> task = taskExec.submit(r);
        try {
            task.get(timeout, unit);
        } catch (TimeoutException e) {
            // task will be cancelled below
        } catch (ExecutionException e) {
            // exception thrown in task; rethrow
            throw launderThrowable(e.getCause());
        } finally {
            // Harmless if task already completed
            task.cancel(true); // interrupt if running
        }
    }
```

#### 7.1.6　处理不可中断的阻塞

Java 库中，有些可以通过提前返回或者抛出 InterruptedException 来响应中断请求的。但，仍存在有些任务处于不可中断的阻塞状态。

Java.io 包中的同步 Socket I/O。在服务器应用程序中，最常见的阻塞 I/O 形式就是对套接字进行读取和写入。虽然 InputStream 和 OutputStream 中的 read 和 write 等方法都不会响应中断，但通过关闭底层的套接字，可以使得由于执行 read 或 write 等方法而被阻塞的线程抛出一个 SocketException。

Java.io 包中的同步 I/O。当中断一个正在 InterruptibleChannel 上等待的线程时，将抛出 ClosedByInterruptException 并关闭链路（这还会使得其他在这条链路上阻塞的线程同样抛出 ClosedByInterruptException）。当关闭一个 InterruptibleChannel 时，将导致所有在链路操作上阻塞的线程都抛出 AsynchronousCloseException。大多数标准的 Channel 都实现了 InterruptibleChannel。

Selector 的异步 I/O。如果一个线程在调用 Selector.select 方法（在 java.nio.channels 中）时阻塞了，那么调用 close 或 wakeup 方法会使线程抛出 ClosedSelectorException 并提前返回。获取某个锁。如果一个线程由于等待某个内置锁而阻塞，那么将无法响应中断，因为线程认为它肯定会获得锁，所以将不会理会中断请求。但是，在 Lock 类中提供了 lockInterruptibly 方法，该方法允许在等待一个锁的同时仍能响应中断。

获取某个锁。如果一个线程由于等待某个内置锁而阻塞，那么将无法响应中断，因为线程认为它肯定会获得锁，所以将不会理会中断请求。但是，在 Lock 类中提供了 lockInterruptibly 方法，该方法允许在等待一个锁的同时仍能响应中断。

```java
public class ReaderThread extends Thread {
    private static final int BUFSZ = 512;
    private final Socket socket;
    private final InputStream in;

    public ReaderThread(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
    }

    public void interrupt() {
        try {
            socket.close();
        } catch (IOException ignored) {
        } finally {
            super.interrupt();
        }
    }

    public void run() {
        try {
            byte[] buf = new byte[BUFSZ];
            while (true) {
                int count = in.read(buf);
                if (count < 0)
                    break;
                else if (count > 0)
                    processBuffer(buf, count);
            }
        } catch (IOException e) { /* Allow thread to exit */
        }
    }

    public void processBuffer(byte[] buf, int count) {    }
}
```

#### 7.1.7　采用 newTaskFor 来封装非标准的取消

> 我们可以通过 newTaskFor 方法来进一步优化 ReaderThread 中封装非标准取消的技术，这是 Java 6 在 ThreadPoolExecutor 中的新增功能。当把一个 Callable 提交给 ExecutorService 时，submit 方法会返回一个 Future，我们可以通过这个 Future 来取消任务。newTaskFor 是一个工厂方法，它将创建 Future 来代表任务。newTaskFor 还能返回一个 RunnableFuture 接口，该接口扩展了 Future 和 Runnable（并由 FutureTask 实现）。

> 通过定制表示任务的 Future 可以改变 Future.cancel 的行为。例如，定制的取消代码可以实现日志记录或者收集取消操作的统计信息，以及取消一些不响应中断的操作。通过改写 interrupt 方法，ReaderThread 可以取消基于套接字的线程。同样，通过改写任务的 Future.cancel 方法也可以实现类似的功能。

```java
public abstract class SocketUsingTask <T> implements CancellableTask<T> {
    @GuardedBy("this") private Socket socket;

    protected synchronized void setSocket(Socket s) {
        socket = s;
    }

    public synchronized void cancel() {
        try {
            if (socket != null)
                socket.close();
        } catch (IOException ignored) {
        }
    }

    public RunnableFuture<T> newTask() {
        return new FutureTask<T>(this) {
            public boolean cancel(boolean mayInterruptIfRunning) {
                try {
                    SocketUsingTask.this.cancel();
                } finally {
                    return super.cancel(mayInterruptIfRunning);
                }
            }
        };
    }
}


interface CancellableTask <T> extends Callable<T> {
    void cancel();

    RunnableFuture<T> newTask();
}


@ThreadSafe
class CancellingExecutor extends ThreadPoolExecutor {
    public CancellingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public CancellingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public CancellingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public CancellingExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof CancellableTask)
            return ((CancellableTask<T>) callable).newTask();
        else
            return super.newTaskFor(callable);
    }
}
```

> SocketUsingTask 实现了 CancellableTask，并定义了 Future.cancel 来关闭套接字和调用 super.cancel。如果 SocketUsingTask 通过其自己的 Future 来取消，那么底层的套接字将被关闭并且线程将被中断。因此它提高了任务对取消操作的响应性：不仅能够在调用可中断方法的同时确保响应取消操作，而且还能调用可阻调的套接字 I/O 方法。

### 7.2 停止基于线程的服务
#### 7.2.1　示例：日志服务

日志单独拿出来，创建日志线程，使用 BlockingQueue，多生产者一消费者。

为了发挥作用，需要实现一种终止日志线程的方法。其中需要注意：
1. 不能只关闭消费者，否则生产者阻塞。
2. 中断生产者的时候，因为队列已满而被阻塞，那么可能无法响应中断。
3. 消费者设置关闭状态时，生产者先判断再操作，会出现竞态条件。
4. 不希望在加入队列的时候持有锁，因为 put 队列的时候就可以阻塞。

解决方法(3、4)，通过原子方式来检查关闭请求，并且有条件地递增一个计数器来“保持”提交消息的权利。

那第二点怎么解决呢？

```java
public class LogService {
    private final BlockingQueue<String> queue;
    private final LoggerThread loggerThread;
    private final PrintWriter writer;
    @GuardedBy("this") private boolean isShutdown;
    @GuardedBy("this") private int reservations;

    public LogService(Writer writer) {
        this.queue = new LinkedBlockingQueue<String>();
        this.loggerThread = new LoggerThread();
        this.writer = new PrintWriter(writer);
    }

    public void start() {
        loggerThread.start();
    }

    public void stop() {
        synchronized (this) {
            isShutdown = true;
        }
        loggerThread.interrupt();
    }

    public void log(String msg) throws InterruptedException {
        synchronized (this) {
            if (isShutdown)
                throw new IllegalStateException(/*...*/);
            ++reservations;
        }
        queue.put(msg);
    }

    private class LoggerThread extends Thread {
        public void run() {
            try {
                while (true) {
                    try {
                        synchronized (LogService.this) {
                            if (isShutdown && reservations == 0)
                                break;
                        }
                        String msg = queue.take();
                        synchronized (LogService.this) {
                            --reservations;
                        }
                        writer.println(msg);
                    } catch (InterruptedException e) { /* retry */
                    }
                }
            } finally {
                writer.close();
            }
        }
    }
}
```

#### 7.2.2　关闭 ExecutorService (线程池关闭)

> ExecutorService 提供了两种关闭方法：使用 shutdown 正常关闭，以及使用 shutdownNow 强行关闭。在进行强行关闭时，shutdownNow 首先关闭当前正在执行的任务，然后返回所有尚未启动的任务清单。
> 
> 这两种关闭方式的差别在于各自的安全性和响应性：强行关闭的速度更快，但风险也更大，因为任务很可能在执行到一半时被结束；而正常关闭虽然速度慢，但却更安全，因为 ExecutorService 会一直等到队列中的所有任务都执行完成后才关闭。

分配线程给写操作。

```java
public class LogService{
	private final ExecutorService exec=newSingleThreadExecutor（）；
	……
	public void start（）{}
	public void stop（）throws InterruptedException{
		try{
			exec.shutdown（）；
			exec.awaitTermination（TIMEOUT, UNIT）；
		}finally{
			writer.close（）；
		}
	}
	public void log（String msg）{
		try{
			exec.execute（new WriteTask（msg））；
		}catch（RejectedExecutionException ignored）{
		}
	}
}
```

#### 7.2.3　“毒丸”对象

> 另一种关闭生产者-消费者服务的方式就是使用“毒丸（Poison Pill）”对象：“毒丸”是指一个放在队列上的对象，其含义是：“当得到这个对象时，立即停止。”在 FIFO（先进先出）队列中，“毒丸”对象将确保消费者在关闭之前首先完成队列中的所有工作，在提交“毒丸”对象之前提交的所有工作都会被处理，而生产者在提交了“毒丸”对象后，将不会再提交任何工作。

一个桌面搜索的程序，一生产者对应一消费者。当搜索完成或捕捉异常时(这里直接放到 finally，妙)，生产者生成一个毒丸对象放到队列中，消费者读到毒丸对象后就结束运行。多个生产者时，生产 n 个毒丸就好。但若数量较庞大，则使用不佳。

```java
public class IndexingService {
    private static final int CAPACITY = 1000;
    private static final File POISON = new File("");
    private final IndexerThread consumer = new IndexerThread();
    private final CrawlerThread producer = new CrawlerThread();
    private final BlockingQueue<File> queue;
    private final FileFilter fileFilter;
    private final File root;

    public IndexingService(File root, final FileFilter fileFilter) {
        this.root = root;
        this.queue = new LinkedBlockingQueue<File>(CAPACITY);
        this.fileFilter = new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || fileFilter.accept(f);
            }
        };
    }

    private boolean alreadyIndexed(File f) {
        return false;
    }

    class CrawlerThread extends Thread {
        public void run() {
            try {
                crawl(root);
            } catch (InterruptedException e) { /* fall through */
            } finally {
                while (true) {
                    try {
                        queue.put(POISON);
                        break;
                    } catch (InterruptedException e1) { /* retry */
                    }
                }
            }
        }

        private void crawl(File root) throws InterruptedException {
            File[] entries = root.listFiles(fileFilter);
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory())
                        crawl(entry);
                    else if (!alreadyIndexed(entry))
                        queue.put(entry);
                }
            }
        }
    }

    class IndexerThread extends Thread {
        public void run() {
            try {
                while (true) {
                    File file = queue.take();
                    if (file == POISON)
                        break;
                    else
                        indexFile(file);
                }
            } catch (InterruptedException consumed) {
            }
        }

        public void indexFile(File file) {
            /*...*/
        };
    }

    public void start() {
        producer.start();
        consumer.start();
    }

    public void stop() {
        producer.interrupt();
    }

    public void awaitTermination() throws InterruptedException {
        consumer.join();
    }
}
```

#### 7.2.4　示例：只执行一次的服务

> 如果某个方法需要处理一批任务，并且当所有任务都处理完成后才返回，那么可以通过一个私有的 Executor 来简化服务的生命周期管理，其中该 Executor 的生命周期是由这个方法来控制的。（在这种情况下，invokeAll 和 invokeAny 等方法通常会起较大的作用。）

想到了闭锁，闭锁应该也可以实现当所有任务处理完后，才返回。

并行检查邮件。
```java
    public boolean checkMail(Set<String> hosts, long timeout, TimeUnit unit)
            throws InterruptedException {
        ExecutorService exec = Executors.newCachedThreadPool();
        final AtomicBoolean hasNewMail = new AtomicBoolean(false);
        try {
            for (final String host : hosts)
                exec.execute(new Runnable() {
                    public void run() {
                        if (checkMail(host))
                            hasNewMail.set(true);
                    }
                });
        } finally {
            exec.shutdown();
            exec.awaitTermination(timeout, unit);
        }
        return hasNewMail.get();
    }
```

#### 7.2.5　shutdownNow 的局限性
> 当通过 shutdownNow 来强行关闭 ExecutorService 时，它会尝试取消正在执行的任务，并返回所有已提交但尚未开始的任务，从而将这些任务写入日志或者保存起来以便之后进行处理。(shutdownNow 返回的 Runnable 对象可能与提交给 ExecutorService 的 Runnable 对象并不相同：它们可能是被封装过的已提交任务。)
>
> 然而，我们无法通过常规方法来找出哪些任务已经开始但尚未结束。这意味着我们无法在关闭过程中知道正在执行的任务的状态，除非任务本身会执行某种检查。要知道哪些任务还没有完成，你不仅需要知道哪些任务还没有开始，而且还需要知道当 Executor 关闭时哪些任务正在执行。
> 
> 通过封装 ExecutorService 并使得 execute（类似地还有 submit，在这里没有给出）记录哪些任务是在关闭后取消的，TrackingExecutor 可以找出哪些任务已经开始但还没有正常完成。在 Executor 结束后，getCancelledTasks 返回被取消的任务清单。要使这项技术能发挥作用，任务在返回时必须维持线程的中断状态，在所有设计良好的任务中都会实现这个功能。

```java
public class TrackingExecutor extends AbstractExecutorService {
    private final ExecutorService exec;
    private final Set<Runnable> tasksCancelledAtShutdown =
            Collections.synchronizedSet(new HashSet<Runnable>());

    public TrackingExecutor(ExecutorService exec) {
        this.exec = exec;
    }

    public void shutdown() {
        exec.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return exec.shutdownNow();
    }

    public boolean isShutdown() {
        return exec.isShutdown();
    }

    public boolean isTerminated() {
        return exec.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return exec.awaitTermination(timeout, unit);
    }

    public List<Runnable> getCancelledTasks() {
        if (!exec.isTerminated())
            throw new IllegalStateException(/*...*/);
        return new ArrayList<Runnable>(tasksCancelledAtShutdown);
    }

    public void execute(final Runnable runnable) {
        exec.execute(new Runnable() {
            public void run() {
                try {
                    runnable.run();
                } finally {
                    if (isShutdown()
                            && Thread.currentThread().isInterrupted())
                        tasksCancelledAtShutdown.add(runnable);
                }
            }
        });
    }
}
```

```java
public abstract class WebCrawler {
    private volatile TrackingExecutor exec;
    @GuardedBy("this") private final Set<URL> urlsToCrawl = new HashSet<URL>();

    private final ConcurrentMap<URL, Boolean> seen = new ConcurrentHashMap<URL, Boolean>();
    private static final long TIMEOUT = 500;
    private static final TimeUnit UNIT = MILLISECONDS;

    public WebCrawler(URL startUrl) {
        urlsToCrawl.add(startUrl);
    }

    public synchronized void start() {
        exec = new TrackingExecutor(Executors.newCachedThreadPool());
        for (URL url : urlsToCrawl) submitCrawlTask(url);
        urlsToCrawl.clear();
    }

    public synchronized void stop() throws InterruptedException {
        try {
            saveUncrawled(exec.shutdownNow());
            if (exec.awaitTermination(TIMEOUT, UNIT))
                saveUncrawled(exec.getCancelledTasks());
        } finally {
            exec = null;
        }
    }

    protected abstract List<URL> processPage(URL url);

    private void saveUncrawled(List<Runnable> uncrawled) {
        for (Runnable task : uncrawled)
            urlsToCrawl.add(((CrawlTask) task).getPage());
    }

    private void submitCrawlTask(URL u) {
        exec.execute(new CrawlTask(u));
    }

    private class CrawlTask implements Runnable {
        private final URL url;
        CrawlTask(URL url) {       this.url = url;        }
        private int count = 1;

        boolean alreadyCrawled() {
            return seen.putIfAbsent(url, true) != null;
        }

        void markUncrawled() {
            seen.remove(url);
            System.out.printf("marking %s uncrawled%n", url);
        }

        public void run() {
            for (URL link : processPage(url)) {
                if (Thread.currentThread().isInterrupted())
                    return;
                submitCrawlTask(link);
            }
        }

        public URL getPage() {            return url;        }
    }
}
```

> 在 TrackingExecutor 中存在一个不可避免的竞态条件，从而产生“误报”问题：一些被认为已取消的任务实际上已经执行完成。这个问题的原因在于，在任务执行最后一条指令以及线程池将任务记录为“结束”的两个时刻之间，线程池可能被关闭。如果任务是幂等的（Idempotent，即将任务执行两次与执行一次会得到相同的结果），那么这不会存在问题，在网页爬虫程序中就是这种情况。否则，在应用程序中必须考虑这种风险，并对“误报”问题做好准备。

### 7.3　处理非正常的线程终止

> 线程池可能出现某个线程因为异常突然终止的情况。导致线程提前死亡的最主要原因就是 RuntimeException。由于这些异常表示出现了某种编程错误或者其他不可修复的错误，因此它们通常不会被捕获。它们不会在调用栈中逐层传递，而是默认地在控制台中输出栈追踪信息，并终止线程。
> 
> 在 Thread API 中同样提供了 Uncaught-ExceptionHandler，它能检测出某个线程由于未捕获的异常而终结的情况。当一个线程由于未捕获异常而退出时，JVM 会把这个事件报告给应用程序提供的 UncaughtExceptionHandler 异常处理器（见程序清单 7-24）。如果没有提供任何异常处理器，那么默认的行为是将栈追踪信息输出到 System.err。

```java
public interface UncaughtExceptionHandler{void uncaughtException（Thread t, Throwable e）；}
```

> 异常处理器如何处理未捕获异常，取决于对服务质量的需求。最常见的响应方式是将一个错误信息以及相应的栈追踪信息写入应用程序日志中，如程序清单 7-25 所示。异常处理器还可以采取更直接的响应，例如尝试重新启动线程，关闭应用程序，或者执行其他修复或诊断等操作。
> 
> 要为线程池中的所有线程设置一个 UncaughtExceptionHandler，需要为 ThreadPool-Executor 的构造函数提供一个 ThreadFactory。（与所有的线程操控一样，只有线程的所有者能够改变线程的 UncaughtExceptionHandler。）标准线程池允许当发生未捕获异常时结束线程，但由于使用了一个 try-finally 代码块来接收通知，因此当线程结束时，将有新的线程来代替它。如果没有提供捕获异常处理器或者其他的故障通知机制，那么任务会悄悄失败，从而导致极大的混乱。如果你希望在任务由于发生异常而失败时获得通知，并且执行一些特定于任务的恢复操作，那么可以将任务封装在能捕获异常的 Runnable 或 Callable 中，或者改写 ThreadPoolExecutor 的 afterExecute 方法。
> 
> 令人困惑的是，只有通过 execute 提交的任务，才能将它抛出的异常交给未捕获异常处理器，而通过 submit 提交的任务，无论是抛出的未检查异常还是已检查异常，都将被认为是任务返回状态的一部分。如果一个由 submit 提交的任务由于抛出了异常而结束，那么这个异常将被 Future.get 封装在 ExecutionException 中重新抛出。

### 7.4　JVM 关闭

> JVM 既可以正常关闭，也可以强行关闭。正常关闭的触发方式有多种，包括：当最后一个“正常（非守护）”线程结束时，或者当调用了 System.exit 时，或者通过其他特定于平台的方法关闭时（例如发送了 SIGINT 信号或键入 Ctrl-C）。虽然可以通过这些标准方法来正常关闭 JVM，但也可以通过调用 Runtime.halt 或者在操作系统中“杀死”JVM 进程（例如发送 SIGKILL）来强行关闭 JVM。

#### 7.4.1　关闭钩子
> 在正常关闭中，JVM 首先调用所有已注册的关闭钩子（Shutdown Hook）。关闭钩子是指通过 Runtime.addShutdownHook 注册的但尚未开始的线程。JVM 并不能保证关闭钩子的调用顺序。在关闭应用程序线程时，如果有（守护或非守护）线程仍然在运行，那么这些线程接下来将与关闭进程并发执行。当所有的关闭钩子都执行结束时，如果 runFinalizersOnExit 为 true，那么 JVM 将运行终结器，然后再停止。JVM 并不会停止或中断任何在关闭时仍然运行的应用程序线程。当 JVM 最终结束时，这些线程将被强行结束。如果关闭钩子或终结器没有执行完成，那么正常关闭进程“挂起”并且 JVM 必须被强行关闭。当被强行关闭时，只是关闭 JVM，而不会运行关闭钩子。
> 
> 关闭钩子应该是线程安全的：它们在访问共享数据时必须使用同步机制，并且小心地避免发生死锁，这与其他并发代码的要求相同。而且，关闭钩子不应该对应用程序的状态（例如，其他服务是否已经关闭，或者所有的正常线程是否已经执行完成）或者 JVM 的关闭原因做出任何假设，因此在编写关闭钩子的代码时必须考虑周全。最后，关闭钩子必须尽快退出，因为它们会延迟 JVM 的结束时间，而用户可能希望 JVM 能尽快终止。
> 
> 关闭钩子可以用于实现服务或应用程序的清理工作，例如删除临时文件，或者清除无法由操作系统自动清除的资源。在程序清单 7-26 中给出了如何使程序清单 7-16 中的 LogService 在其 start 方法中注册一个关闭钩子，从而确保在退出时关闭日志文件。
> 
> 由于关闭钩子将并发执行，因此在关闭日志文件时可能导致其他需要日志服务的关闭钩子产生问题。为了避免这种情况，关闭钩子不应该依赖那些可能被应用程序或其他关闭钩子关闭的服务。实现这种功能的一种方式是对所有服务使用同一个关闭钩子（而不是每个服务使用一个不同的关闭钩子），并且在该关闭钩子中执行一系列的关闭操作。这确保了关闭操作在单个线程中串行执行，从而避免了在关闭操作之间出现竞态条件或死锁等问题。无论是否使用关闭钩子，都可以使用这项技术，通过将各个关闭操作串行执行而不是并行执行，可以消除许多潜在的故障。当应用程序需要维护多个服务之间的显式依赖信息时，这项技术可以确保关闭操作按照正确的顺序执行。

注册一个钩子来停止日志服务
```java
public void start（）{
	Runtime.getRuntime（）.addShutdownHook（new Thread（）{
	public void run（）{
		try{LogService.this.stop（）；
		}catch（InterruptedException ignored）{}}
	}）；
}
```

#### 7.4.2　守护线程

> 线程可分为两种：普通线程和守护线程。在 JVM 启动时创建的所有线程中，除了主线程以外，其他的线程都是守护线程（例如垃圾回收器以及其他执行辅助工作的线程）。当创建一个新线程时，新线程将继承创建它的线程的守护状态，因此在默认情况下，主线程创建的所有线程都是普通线程。
> 
> 普通线程与守护线程之间的差异仅在于当线程退出时发生的操作。当一个线程退出时，JVM 会检查其他正在运行的线程，如果这些线程都是守护线程，那么 JVM 会正常退出操作。当 JVM 停止时，所有仍然存在的守护线程都将被抛弃——既不会执行 finally 代码块，也不会执行回卷栈，而 JVM 只是直接退出。

#### 7.4.3　终结器

> 当不再需要内存资源时，可以通过垃圾回收器来回收它们，但对于其他一些资源，例如文件句柄或套接字句柄，当不再需要它们时，必须显式地交还给操作系统。为了实现这个功能，垃圾回收器对那些定义了 finalize 方法的对象会进行特殊处理：在回收器释放它们后，调用它们的 finalize 方法，从而保证一些持久化的资源被释放。

原来如此！竟然解释了之前 JVM 中垃圾回收的知识了。

> 由于终结器可以在某个由 JVM 管理的线程中运行，因此终结器访问的任何状态都可能被多个线程访问，这样就必须对其访问操作进行同步。终结器并不能保证它们将在何时运行甚至是否会运行，并且复杂的终结器通常还会在对象上产生巨大的性能开销。要编写正确的终结器是非常困难的。在大多数情况下，通过使用 finally 代码块和显式的 close 方法，能够比使用终结器更好地管理资源。唯一的例外情况在于：当需要管理对象，并且该对象持有的资源是通过本地方法获得的。基于这些原因以及其他一些原因，我们要尽量避免编写或使用包含终结器的类（除非是平台库中的类）[EJ Item 6]。

## 第 8 章 - 线程池的使用

各章节内容：

> 第 6 章介绍了任务执行框架，它不仅能简化任务与线程的生命周期管理，而且还提供一种简单灵活的方式将任务的提交与任务的执行策略解耦开来。
> 
> 第 7 章介绍了在实际应用程序中使用任务执行框架时出现的一些与服务生命周期相关的细节问题。
> 
> 本章将介绍对线程池进行配置与调优的一些高级选项，并分析在使用任务执行框架时需要注意的各种危险，以及一些使用 Executor 的高级示例。

### 8.1　在任务与执行策略之间的隐性耦合

> 我们已经知道，Executor 框架可以将任务的提交与任务的执行策略解耦开来。就像许多对复杂过程的解耦操作那样，这种论断多少有些言过其实了。虽然 Executor 框架为制定和修改执行策略都提供了相当大的灵活性，但并非所有的任务都能适用所有的执行策略。有些类型的任务需要明确地指定执行策略，包括：
> 
> 依赖性任务。大多数行为正确的任务都是独立的：它们不依赖于其他任务的执行时序、执行结果或其他效果。当在线程池中执行独立的任务时，可以随意地改变线程池的大小和配置，这些修改只会对执行性能产生影响。然而，如果提交给线程池的任务需要依赖其他的任务，那么就隐含地给执行策略带来了约束，此时必须小心地维持这些执行策略以避免产生活跃性问题（请参见 8.1.1 节）。
> 
> 使用线程封闭机制的任务。与线程池相比，单线程的 Executor 能够对并发性做出更强的承诺。它们能确保任务不会并发地执行，使你能够放宽代码对线程安全的要求。对象可以封闭在任务线程中，使得在该线程中执行的任务在访问该对象时不需要同步，即使这些资源不是线程安全的也没有问题。这种情形将在任务与执行策略之间形成隐式的耦合——任务要求其执行所在的 Executor 是单线程的。如果将 Executor 从单线程环境改为线程池环境，那么将会失去线程安全性。
> 
> 对响应时间敏感的任务。GUI 应用程序对于响应时间是敏感的：如果用户在点击按钮后需要很长延迟才能得到可见的反馈，那么他们会感到不满。如果将一个运行时间较长的任务提交到单线程的 Executor 中，或者将多个运行时间较长的任务提交到一个只包含少量线程的线程池中，那么将降低由该 Executor 管理的服务的响应性。
> 
> 使用 ThreadLocal 的任务。ThreadLocal 使每个线程都可以拥有某个变量的一个私有“版本”。然而，只要条件允许，Executor 可以自由地重用这些线程。在标准的 Executor 实现中，当执行需求较低时将回收空闲线程，而当需求增加时将添加新的线程，并且如果从任务中抛出了一个未检查异常，那么将用一个新的工作者线程来替代抛出异常的线程。只有当线程本地值的生命周期受限于任务的生命周期时，在线程池的线程中使用 ThreadLocal 才有意义，而在线程池的线程中不应该使用 ThreadLocal 在任务之间传递值。
> 
> 只有当任务都是同类型的并且相互独立时，线程池的性能才能达到最佳。如果将运行时间较长的与运行时间较短的任务混合在一起，那么除非线程池很大，否则将可能造成“拥塞”。如果提交的任务依赖于其他任务，那么除非线程池无限大，否则将可能造成死锁。幸运的是，在基于网络的典型服务器应用程序中——网页服务器、邮件服务器以及文件服务器等，它们的请求通常都是同类型的并且相互独立的。
> 
> 在一些任务中，需要拥有或排除某种特定的执行策略。如果某些任务依赖于其他的任务，那么会要求线程池足够大，从而确保它们依赖任务不会被放入等待队列中或被拒绝，而采用线程封闭机制的任务需要串行执行。通过将这些需求写入文档，将来的代码维护人员就不会由于使用了某种不合适的执行策略而破坏安全性或活跃性。

#### 8.1.1　线程饥饿死锁

> 每当提交了一个有依赖性的 Executor 任务时，要清楚地知道可能会出现线程“饥饿”死锁，因此需要在代码或配置 Executor 的配置文件中记录线程池的大小限制或配置限制。

单线程执行池，下面会死锁。为什么最后一步 `header.get() + page + footer.get()` 会锁住呢？header.get()能获取结果，footer.get()也能获得结果，为什么返回不成功呢？
```java
public class ThreadDeadlock {
    ExecutorService exec = Executors.newSingleThreadExecutor();
    public class LoadFileTask implements Callable<String> {...    }
    public class RenderPageTask implements Callable<String> {
        public String call() throws Exception {
            Future<String> header, footer;
            header = exec.submit(new LoadFileTask("header.html"));
            footer = exec.submit(new LoadFileTask("footer.html"));
            String page = renderBody();
            // Will deadlock -- task waiting for result of subtask
            return header.get() + page + footer.get();
        }
        private String renderBody() {            return "";        }
    }
}
```

#### 8.1.2　运行时间较长的任务

占用线程&执行时间长的任务，会影响执行时间短的任务，影响整体响应。

> 有一项技术可以缓解执行时间较长任务造成的影响，即限定任务等待资源的时间，而不要无限制地等待。在平台类库的大多数可阻塞方法中，都同时定义了限时版本和无限时版本，例如 Thread.join、BlockingQueue.put、CountDownLatch.await 以及 Selector.select 等。如果等待超时，那么可以把任务标识为失败，然后中止任务或者将任务重新放回队列以便随后执行。这样，无论任务的最终结果是否成功，这种办法都能确保任务总能继续执行下去，并将线程释放出来以执行一些能更快完成的任务。

### 8.2　设置线程池的大小

> 在代码中通常不会固定线程池的大小，而应该通过某种配置机制来提供，或者根据 Runtime.availableProcessors 来动态计算。

线程池过小，执行速度慢。线程池过大，竞争 CPU 和内存资源，可能导致更高的内存使用量，可能耗尽资源。

> 要想正确地设置线程池的大小，必须分析计算环境、资源预算和任务的特性。在部署的系统中有多少个 CPU？多大的内存？任务是计算密集型、I/O 密集型还是二者皆可？它们是否需要像 JDBC 连接这样的稀缺资源？如果需要执行不同类别的任务，并且它们之间的行为相差很大，那么应该考虑使用多个线程池，从而使每个线程池可以根据各自的工作负载来调整。

计算密集型任务：Ncpu 的系统，设置线程池大小为 Ncpu+1 时，通常能实现最优的利用率。即使当计算密集型的线程偶尔由于页缺失故障或者其他原因而暂停时，这个“额外”的线程也能确保 CPU 的时钟周期不会被浪费。

IO 操作或其他阻塞操作任务：由于线程并非一直执行，因此规模可以更大。

> 正确的设置线程池的大小，需要估算任务的等待时间与计算时间的比值。可以通过分析或监控工具来获得。也可以在某个基准负载下，分别设置不同大小的线程池来运行应用程序，并观察 CPU 利用率的水平。

N_threads_ = N_cpu_ \* U_cpu_ \* (1+W/C). 其中 N_threads_ 表示线程池最佳大小，N_cpu_ 表示 CPU 个数，U_cpu_ 表示 CPU 利用率，W/C 表示等待时间与计算时间的比率。

Runtime.getRuntime().availableProcessors() 获取 CPU 个数。

> 当然，CPU 周期并不是唯一影响线程池大小的资源，还包括内存、文件句柄、套接字句柄和数据库连接等。计算这些资源对线程池的约束条件是更容易的：计算每个任务对该资源的需求量，然后用该资源的可用总量除以每个任务的需求量，所得结果就是线程池大小的上限。
> 
> 当任务需要某种通过资源池来管理的资源时，例如数据库连接，那么线程池和资源池的大小将会相互影响。如果每个任务都需要一个数据库连接，那么连接池的大小就限制了线程池的大小。同样，当线程池中的任务是数据库连接的唯一使用者时，那么线程池的大小又将限制连接池的大小。

### 8.3　配置 ThreadPoolExecutor

> ThreadPoolExecutor 为一些 Executor 提供了基本的实现，这些 Executor 是由 Executors 中的 newCachedThreadPool、newFixedThreadPool 和 newScheduledThreadExecutor 等工厂方法返回的。ThreadPoolExecutor 是一个灵活的、稳定的线程池，允许进行各种定制。

```java
public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler)
```

#### 8.3.1　线程的创建与销毁

> 线程池的基本大小（Core Pool Size）、最大大小（Maximum PoolSize）以及存活时间等因素共同负责线程的创建与销毁。基本大小也就是线程池的目标大小，即在没有任务执行时[插图]线程池的大小，并且只有在工作队列满了的情况下才会创建超出这个数量的线程[插图]。线程池的最大大小表示可同时活动的线程数量的上限。如果某个线程的空闲时间超过了存活时间，那么将被标记为可回收的，并且当线程池的当前大小超过了基本大小时，这个线程将被终止。

### 8.3.2　管理队列任务
> 在有限的线程池中会限制可并发执行的任务数量。（单线程的 Executor 是一种值得注意的特例：它们能确保不会有任务并发执行，因为它们通过线程封闭来实现线程安全性。）

> 在线程池中，这些请求会在一个由 Executor 管理的 Runnable 队列中等待，而不会像线程那样去竞争 CPU 资源。

> 即使请求的平均到达速率很稳定，也仍然会出现请求突增的情况。尽管队列有助于缓解任务的突增问题，但如果任务持续高速地到来，那么最终还是会抑制请求的到达率以避免耗尽内存。[插图]甚至在耗尽内存之前，响应性能也将随着任务队列的增长而变得越来越糟。

> ThreadPoolExecutor 允许提供一个 BlockingQueue 来保存等待执行的任务。基本的任务排队方法有 3 种：无界队列、有界队列和同步移交（Synchronous Handoff）。队列的选择与其他的配置参数有关，例如线程池的大小等。newFixedThreadPool 和 newSingleThreadExecutor 在默认情况下将使用一个无界的 LinkedBlockingQueue (其实，上届是 Integer.MAX_VALUE)。如果所有工作者线程都处于忙碌状态，那么任务将在队列中等候。

> 一种更稳妥的资源管理策略是使用有界队列，例如 ArrayBlockingQueue、有界的 LinkedBlockingQueue、PriorityBlockingQueue。有界队列有助于避免资源耗尽的情况发生，但它又带来了新的问题：当队列填满后，新的任务该怎么办？（有许多饱和策略[Saturation Policy]可以解决这个问题。请参见 8.3.3 节。）在使用有界的工作队列时，队列的大小与线程池的大小必须一起调节。如果线程池较小而队列较大，那么有助于减少内存使用量，降低 CPU 的使用率，同时还可以减少上下文切换，但付出的代价是可能会限制吞吐量。

> 对于非常大的或者无界的线程池，可以通过使用 SynchronousQueue 来避免任务排队，以及直接将任务从生产者移交给工作者线程。SynchronousQueue 不是一个真正的队列，而是一种在线程之间进行移交的机制。要将一个元素放入 SynchronousQueue 中，必须有另一个线程正在等待接受这个元素。如果没有线程正在等待，并且线程池的当前大小小于最大值，那么 ThreadPoolExecutor 将创建一个新的线程，否则根据饱和策略，这个任务将被拒绝。使用直接移交将更高效，因为任务会直接移交给执行它的线程，而不是被首先放在队列中，然后由工作者线程从队列中提取该任务。只有当线程池是无界的或者可以拒绝任务时，SynchronousQueue 才有实际价值。在 newCachedThreadPool 工厂方法中就使用了 SynchronousQueue。

> 如果想进一步控制任务执行顺序，还可以使用 PriorityBlockingQueue，这个队列将根据优先级来安排任务。任务的优先级是通过自然顺序或 Comparator（如果任务实现了 Comparable）来定义的。

> 对于 Executor, newCachedThreadPool 工厂方法是一种很好的默认选择，它能提供比固定大小的线程池更好的排队性能。(这种性能差异是由于使用了 SynchronousQueue 而不是 LinkedBlockingQueue。在 Java 6 中提供了一个新的非阻塞算法来替代 SynchronousQueue，与 Java 5.0 中的 SynchronousQueue 相比，该算法把 Executor 基准的吞吐量提高了 3 倍)

> 只有当任务相互独立时，为线程池或工作队列设置界限才是合理的。如果任务之间存在依赖性，那么有界的线程池或队列就可能导致线程“饥饿”死锁问题。此时应该使用无界的线程池，例如 newCachedThreadPool[插图]。

> 对于提交其他任务并等待其结果的任务来说，还有另一种配置方法，就是使用有界的线程池，并使用 SynchronousQueue 作为工作队列，以及“调用者运行（Caller-Runs）”饱和策略。

之后，回顾的时候，要把单个、固定、cached 等线程池进行区别下。

#### 8.3.3　饱和策略

> 当有界队列被填满后，饱和策略开始发挥作用。ThreadPoolExecutor 的饱和策略可以通过调用 setRejectedExecutionHandler 来修改。（如果某个任务被提交到一个已被关闭的 Executor 时，也会用到饱和策略。）JDK 提供了几种不同的 RejectedExecutionHandler 实现，每种实现都包含有不同的饱和策略：AbortPolicy、CallerRunsPolicy、DiscardPolicy 和 DiscardOldestPolicy。

> “中止（Abort）”策略是默认的饱和策略，该策略将抛出未检查的 RejectedExecution-Exception。调用者可以捕获这个异常，然后根据需求编写自己的处理代码。

> 当新提交的任务无法保存到队列中等待执行时，“抛弃（Discard）”策略会悄悄抛弃该任务。“抛弃最旧的（Discard-Oldest）”策略则会抛弃下一个将被执行的任务，然后尝试重新提交新的任务。（如果工作队列是一个优先队列，那么“抛弃最旧的”策略将导致抛弃优先级最高的任务，因此最好不要将“抛弃最旧的”饱和策略和优先级队列放在一起使用。）

> “调用者运行（Caller-Runs）”策略实现了一种调节机制，该策略既不会抛弃任务，也不会抛出异常，而是将某些任务回退到调用者，从而降低新任务的流量。它不会在线程池的某个线程中执行新提交的任务，而是在一个调用了 execute 的线程中执行该任务。我们可以将 WebServer 示例修改为使用有界队列和“调用者运行”饱和策略，当线程池中的所有线程都被占用，并且工作队列被填满后，下一个任务会在调用 execute 时在主线程中执行。由于执行任务需要一定的时间，因此主线程至少在一段时间内不能提交任何任务，从而使得工作者线程有时间来处理完正在执行的任务。在这期间，主线程不会调用 accept，因此到达的请求将被保存在 TCP 层的队列中而不是在应用程序的队列中。如果持续过载，那么 TCP 层将最终发现它的请求队列被填满，因此同样会开始抛弃请求。当服务器过载时，这种过载情况会逐渐向外蔓延开来——从线程池到工作队列到应用程序再到 TCP 层，最终达到客户端，导致服务器在高负载下实现一种平缓的性能降低。

```java
@ThreadSafe
public class BoundedExecutor {
    private final Executor exec;
    private final Semaphore semaphore;

    public BoundedExecutor(Executor exec, int bound) {
        this.exec = exec;
        this.semaphore = new Semaphore(bound);
    }

    public void submitTask(final Runnable command)
            throws InterruptedException {
        semaphore.acquire();
        try {
            exec.execute(new Runnable() {
                public void run() {
                    try {
                        command.run();
                    } finally {
                        semaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            semaphore.release();
        }
    }
}
```

#### 8.3.4　线程工厂
> 每当线程池需要创建一个线程时，都是通过线程工厂方法（请参见程序清单 8-5）来完成的。默认的线程工厂方法将创建一个新的、非守护的线程，并且不包含特殊的配置信息。通过指定一个线程工厂方法，可以定制线程池的配置信息。在 ThreadFactory 中只定义了一个方法 newThread，每当线程池需要创建一个新线程时都会调用这个方法。

> 然而，在许多情况下都需要使用定制的线程工厂方法。例如，你希望为线程池中的线程指定一个 UncaughtExceptionHandler，或者实例化一个定制的 Thread 类用于执行调试信息的记录。你还可能希望修改线程的优先级（这通常并不是一个好主意。请参见 10.3.1 节）或者守护状态（同样，这也不是一个好主意。请参见 7.4.2 节）。或许你只是希望给线程取一个更有意义的名称，用来解释线程的转储信息和错误日志。

```java
public interface ThreadFactory{Thread newThread（Runnable r）；}
```

自定义线程工厂

```java
public class MyThreadFactory implements ThreadFactory {
    private final String poolName;

    public MyThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    public Thread newThread(Runnable runnable) {
        return new MyAppThread(runnable, poolName);
    }
}

public class MyAppThread extends Thread {
    public static final String DEFAULT_NAME = "MyAppThread";
    private static volatile boolean debugLifecycle = false;
    private static final AtomicInteger created = new AtomicInteger();
    private static final AtomicInteger alive = new AtomicInteger();
    private static final Logger log = Logger.getAnonymousLogger();

    public MyAppThread(Runnable r) {
        this(r, DEFAULT_NAME);
    }

    public MyAppThread(Runnable runnable, String name) {
        super(runnable, name + "-" + created.incrementAndGet());
        setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t,
                                          Throwable e) {
                log.log(Level.SEVERE,
                        "UNCAUGHT in thread " + t.getName(), e);
            }
        });
    }

    public void run() {
        // Copy debug flag to ensure consistent value throughout.
        boolean debug = debugLifecycle;
        if (debug) log.log(Level.FINE, "Created " + getName());
        try {
            alive.incrementAndGet();
            super.run();
        } finally {
            alive.decrementAndGet();
            if (debug) log.log(Level.FINE, "Exiting " + getName());
        }
    }

    public static int getThreadsCreated() {
        return created.get();
    }

    public static int getThreadsAlive() {
        return alive.get();
    }

    public static boolean getDebug() {
        return debugLifecycle;
    }

    public static void setDebug(boolean b) {
        debugLifecycle = b;
    }
}
```

> 如果在应用程序中需要利用安全策略来控制对某些特殊代码库的访问权限，那么可以通过 Executor 中的 privilegedThreadFactory 工厂来定制自己的线程工厂。通过这种方式创建出来的线程，将与创建 privilegedThreadFactory 的线程拥有相同的访问权限、AccessControlContext 和 contextClassLoader。如果不使用 privilegedThreadFactory，线程池创建的线程将从在需要新线程时调用 execute 或 submit 的客户程序中继承访问权限，从而导致令人困惑的安全性异常。

#### 8.3.5　在调用构造函数后再定制 ThreadPoolExecutor

> 在调用完 ThreadPoolExecutor 的构造函数后，仍然可以通过设置函数（Setter）来修改大多数传递给它的构造函数的参数（例如线程池的基本大小、最大大小、存活时间、线程工厂以及拒绝执行处理器（RejectedExecution Handler））。如果 Executor 是通过 Executors 中的某个（newSingleThreadExecutor 除外）工厂方法创建的，那么可以将结果的类型转换为 ThreadPoolExecutor 以访问设置器

```java
ExecutorService exec=Executors.newCachedThreadPool（）；
if（exec instanceof ThreadPoolExecutor）（（
ThreadPoolExecutor）exec）.setCorePoolSize（10）；
else
throw new AssertionError（"Oops, bad assumption"）；
```

> 在 Executors 中包含一个 unconfigurableExecutorService 工厂方法，该方法对一个现有的 ExecutorService 进行包装，使其只暴露出 ExecutorService 的方法，因此不能对它进行配置。newSingleThreadExecutor 返回按这种方式封装的 ExecutorService，而不是最初的 ThreadPoolExecutor。虽然单线程的 Executor 实际上被实现为一个只包含唯一线程的线程池，但它同样确保了不会并发地执行任务。如果在代码中增加单线程 Executor 的线程池大小，那么将破坏它的执行语义。
> 
> 你可以在自己的 Executor 中使用这项技术以防止执行策略被修改。如果将 ExecutorService 暴露给不信任的代码，又不希望对其进行修改，就可以通过 unconfigurableExecutorService 来包装它。

后续看源码了解去

### 8.4　扩展 ThreadPoolExecutor
ThreadPoolExecutor 是可扩展的，它提供了几个可以在子类化中改写的方法：beforeExecute、afterExecute 和 terminated，这些方法可以用于扩展 ThreadPoolExecutor 的行为。

在执行任务的线程中将调用 beforeExecute 和 afterExecute 等方法，在这些方法中还可以添加日志、计时、监视或统计信息收集的功能。无论任务是从 run 中正常返回，还是抛出一个异常而返回，afterExecute 都会被调用。（如果任务在完成后带有一个 Error，那么就不会调用 afterExecute。）如果 beforeExecute 抛出一个 RuntimeException，那么任务将不被执行，并且 afterExecute 也不会被调用。

在线程池完成关闭操作时调用 terminated，也就是在所有任务都已经完成并且所有工作者线程也已经关闭后。terminated 可以用来释放 Executor 在其生命周期里分配的各种资源，此外还可以执行发送通知、记录日志或者收集 finalize 统计信息等操作。

```java
public class TimingThreadPool extends ThreadPoolExecutor {

    public TimingThreadPool() {
        super(1, 1, 0L, TimeUnit.SECONDS, null);
    }

    private final ThreadLocal<Long> startTime = new ThreadLocal<Long>();
    private final Logger log = Logger.getLogger("TimingThreadPool");
    private final AtomicLong numTasks = new AtomicLong();
    private final AtomicLong totalTime = new AtomicLong();

    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);  // 先执行父类的方法
        log.fine(String.format("Thread %s: start %s", t, r));
        startTime.set(System.nanoTime());
    }

    protected void afterExecute(Runnable r, Throwable t) {
        try {
            long endTime = System.nanoTime();
            long taskTime = endTime - startTime.get();
            numTasks.incrementAndGet();
            totalTime.addAndGet(taskTime);
            log.fine(String.format("Thread %s: end %s, time=%dns", t, r, taskTime));
        } finally {
            super.afterExecute(r, t);
        }
    }

    protected void terminated() {
        try {
            log.info(String.format("Terminated: avg time=%dns", totalTime.get() / numTasks.get()));
        } finally {
            super.terminated();
        }
    }
}
```

### 8.5　递归算法的并行化

一个对 DFS 进行并行化的示例。在了解 Thread 等相关 API 后，可以自己写一个试一试。

```java
@ThreadSafe
public class ValueLatch <T> {
    @GuardedBy("this") private T value = null;
    private final CountDownLatch done = new CountDownLatch(1);

    public boolean isSet() {
        return (done.getCount() == 0);
    }

    public synchronized void setValue(T newValue) {
        if (!isSet()) {
            value = newValue;
            done.countDown();
        }
    }

    public T getValue() throws InterruptedException {
        done.await();
        synchronized (this) {
            return value;
        }
    }
}

public class ConcurrentPuzzleSolver <P, M> {
    private final Puzzle<P, M> puzzle;
    private final ExecutorService exec;
    private final ConcurrentMap<P, Boolean> seen;
    protected final ValueLatch<PuzzleNode<P, M>> solution = new ValueLatch<PuzzleNode<P, M>>();

    public ConcurrentPuzzleSolver(Puzzle<P, M> puzzle) {
        this.puzzle = puzzle;
        this.exec = initThreadPool();
        this.seen = new ConcurrentHashMap<P, Boolean>();
        if (exec instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) exec;
            tpe.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        }
    }

    private ExecutorService initThreadPool() {
        return Executors.newCachedThreadPool();
    }

    public List<M> solve() throws InterruptedException {
        try {
            P p = puzzle.initialPosition();
            exec.execute(newTask(p, null, null));
            // block until solution found
            PuzzleNode<P, M> solnPuzzleNode = solution.getValue();
            return (solnPuzzleNode == null) ? null : solnPuzzleNode.asMoveList();
        } finally {
            exec.shutdown();
        }
    }

    protected Runnable newTask(P p, M m, PuzzleNode<P, M> n) {
        return new SolverTask(p, m, n);
    }

    protected class SolverTask extends PuzzleNode<P, M> implements Runnable {
        SolverTask(P pos, M move, PuzzleNode<P, M> prev) {
            super(pos, move, prev);
        }

        public void run() {
            if (solution.isSet() || seen.putIfAbsent(pos, true) != null)
                return; // already solved or seen this position
            if (puzzle.isGoal(pos))
                solution.setValue(this);
            else
                for (M m : puzzle.legalMoves(pos))
                    exec.execute(newTask(puzzle.move(pos, m), m, this));
        }
    }
}

public class PuzzleSolver <P,M> extends ConcurrentPuzzleSolver<P, M> {
    PuzzleSolver(Puzzle<P, M> puzzle) {
        super(puzzle);
    }

    private final AtomicInteger taskCount = new AtomicInteger(0);

    protected Runnable newTask(P p, M m, PuzzleNode<P, M> n) {
        return new CountingSolverTask(p, m, n);
    }

    class CountingSolverTask extends SolverTask {
        CountingSolverTask(P pos, M move, PuzzleNode<P, M> prev) {
            super(pos, move, prev);
            taskCount.incrementAndGet();
        }

        public void run() {
            try {
                super.run();
            } finally {
                if (taskCount.decrementAndGet() == 0)
                    solution.setValue(null);
            }
        }
    }
}
```













