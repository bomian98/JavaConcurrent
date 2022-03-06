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
1 - 普通方法, 锁住的是当前执行该方法的实例对象
2 - 静态方法, 锁住的是类对象。

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

 > 在程序清单 3-1 (P27)中的 NoVisibility 说明了当多个线程在没有同步的情况下共享数据时出现的错误。
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

 >  如果在对象构造完成之前就发布该对象, 就会破坏线程安全性。当某个不应该发布的对象被发布时, 这种情况就被称为逸出(Escape)

 >  发布对象的最简单方法是将对象的引用保存到一个公有的静态变量中, 以便任何类和线程都能看见该对象, 

 >  当发布某个对象时, 可能会间接地发布其他对象。如果将一个 Secret 对象添加到集合 knownSecrets 中, 那么同样会发布这个对象, 因为任何代码都可以遍历这个集合, 并获得对这个新 Secret 对象的引用。同样, 如果从非私有方法中返回一个引用, 那么同样会发布返回的对象。

 >  最后一种发布对象或其内部状态的机制就是发布一个内部的类实例, 

 >  当从对象的构造函数中发布对象时, 只是发布了一个尚未构造完成的对象。即使发布对象的语句位于构造函数的最后一行也是如此。如果 this 引用在构造过程中逸出, 那么这种对象就被认为是不正确构造

 >  不要在构造过程中使 this 引用逸出。

 >  在构造过程中使 this 引用逸出的一个常见错误是, 在构造函数中启动一个线程。当对象在其构造函数中创建一个线程时, 无论是显式创建(通过将它传给构造函数)还是隐式创建(由于 Thread 或 Runnable 是该对象的一个内部类), this 引用都会被新创建的线程共享。在对象尚未完全构造之前, 新的线程就可以看见它。在构造函数中创建线程并没有错误, 但最好不要立即启动它, 而是通过一个 start 或 initialize 方法来启动(请参见第 7 章了解更多关于服务生命周期的内容)。在构造函数中调用一个可改写的实例方法时(既不是私有方法, 也不是终结方法), 同样会导致 this 引用在构造过程中逸出。

 >  程序清单 3-8　使用工厂方法来防止 this 引用在构造过程中逸出

### 3.3 线程封闭

#### 3.3.1 Ad-hoc 线程封闭

 >  Ad-hoc 线程封闭是指, 维护线程封闭性的职责完全由程序实现来承担。Ad-hoc 线程封闭是非常脆弱的, 因为没有任何一种语言特性, 例如可见性修饰符或局部变量, 能将对象封闭到目标线程上。

 >  在 volatile 变量上存在一种特殊的线程封闭。只要你能确保只有单个线程对共享的 volatile 变量执行写入操作, 那么就可以安全地在这些共享的 volatile 变量上执行“读取-修改-写入”的操作。在这种情况下, 相当于将修改操作封闭在单个线程中以防止发生竞态条件, 并且 volatile 变量的可见性保证还确保了其他线程能看到最新的值。

 >  由于 Ad-hoc 线程封闭技术的脆弱性, 因此在程序中尽量少用它, 在可能的情况下, 应该使用更强的线程封闭技术(例如, 栈封闭或 ThreadLocal 类)。

#### 3.3.2 栈封闭

 >  栈封闭是线程封闭的一种特例, 在栈封闭中, 只能通过局部变量才能访问对象。

#### 3.3.3 ThreadLocal 类

 >  维持线程封闭性的一种更规范方法是使用 ThreadLocal, 这个类能使线程中的某个值与保存值的对象关联起来。ThreadLocal 提供了 get 与 set 等访问接口或方法, 这些方法为每个使用该变量的线程都存有一份独立的副本, 因此 get 总是返回由当前执行线程在调用 set 时设置的最新值。

 >  ThreadLocal 对象通常用于防止对可变的单实例变量(Singleton)或全局变量进行共享。

 >  由于 JDBC 的连接对象不一定是线程安全的, 因此, 当多线程应用程序在没有协同的情况下使用全局变量时, 就不是线程安全的。通过将 JDBC 的连接保存到 ThreadLocal 对象中, 每个线程都会拥有属于自己的连接

 >  当某个线程初次调用 ThreadLocal.get 方法时, 就会调用 initialValue 来获取初始值。从概念上看, 你可以将 ThreadLocal < T > 视为包含了 Map < Thread,  T > 对象, 其中保存了特定于该线程的值, 但 ThreadLocal 的实现并非如此。这些特定于线程的值保存在 Thread 对象中, 当线程终止后, 这些值会作为垃圾回收。

 >  假设你需要将一个单线程应用程序移植到多线程环境中, 通过将共享的全局变量转换为 ThreadLocal 对象(如果全局变量的语义允许), 可以维持线程安全性。然而, 如果将应用程序范围内的缓存转换为线程局部的缓存, 就不会有太大作用。

### 3.4 不变性

 >  不可变对象一定是线程安全的。

 >  不可变性并不等于将对象中所有的域都声明为 final 类型, 即使对象中所有的域都是 final 类型的, 这个对象也仍然是可变的, 因为在 final 类型的域中可以保存对可变对象的引用。

 >  当满足以下条件时, 对象才是不可变的：
 >
 >  - 对象创建以后其状态就不能修改。
 >  - 对象的所有域都是 final 类型。
 >  - 对象是正确创建的(在对象的创建期间, this 引用没有逸出)。

#### 3.4.1 Final 域

 >  关键字 final 可以视为 C++中 const 机制的一种受限版本, 用于构造不可变性对象。final 类型的域是不能修改的(但如果 final 域所引用的对象是可变的, 那么这些被引用的对象是可以修改的)。然而, 在 Java 内存模型中, final 域还有着特殊的语义。final 域能确保初始化过程的安全性, 从而可以不受限制地访问不可变对象, 并在共享这些对象时无须同步。

 >  正如“除非需要更高的可见性, 否则应将所有的域都声明为私有域”是一个良好的编程习惯, “除非需要某个域是可变的, 否则应将其声明为 final 域”也是一个良好的编程习惯。

#### 3.4.2 使用 Volatile 类型来发布不可变对象

 >  每当需要对一组相关数据以原子方式执行某个操作时, 就可以考虑创建一个不可变的类来包含这些数据, 例如程序清单 3-12 (P40)中的 OneValueCache.

 > 程序清单 3-13 中的 VolatileCachedFactorizer 使用了 OneValueCache 来保存缓存的数值及其因数。当一个线程将 volatile 类型的 cache 设置为引用一个新的 OneValueCache 时, 其他线程就会立即看到新缓存的数据。

 >  因为 OneValueCache 是不可变的, 并且在每条相应的代码路径中只会访问它一次。通过使用包含多个状态变量的容器对象来维持不变性条件, 并使用一个 volatile 类型的引用来确保可见性, 使得 Volatile CachedFactorizer 在没有显式地使用锁的情况下仍然是线程安全的。

秒啊！

 > 如果在 OneValueCache 和构造函数中没有调用 copyOf, 那么 OneValueCache 就不是不可变的。

这里不懂.

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
```

 >  由于没有使用同步来确保 Holder 对象对其他线程可见, 因此将 Holder 称为“未被正确发布”。在未被正确发布的对象中存在两个问题。
 >
 >  首先, 除了发布对象的线程外, 其他线程可以看到的 Holder 域是一个失效值, 因此将看到一个空引用或者之前的旧值。
 >
 >  然而, 更糟糕的情况是, 线程看到 Holder 引用的值是最新的, 但 Holder 状态的值却是失效的。情况变得更加不可预测的是, 某个线程在第一次读取域时得到失效值, 而再次读取这个域时会得到一个更新值, 这也是 assertSainty 抛出 AssertionError 的原因。

 > 问题并不在于 Holder 类本身, 而是在于 Holder 类未被正确地发布。然而, 如果将 n 声明为 final 类型, 那么 Holder 将不可变, 从而避免出现不正确发布的问题。

 > 尽管在构造函数中设置的域值似乎是第一次向这些域中写入的值, 因此不会有“更旧的”值被视为失效值, 但 Object 的构造函数会在子类构造函数运行之前先将默认值写入所有的域。因此, 某个域的默认值可能被视为失效值。

JVM 创建对象的时候, 先全部赋值为默认值, 然后再使用构造函数初始化。
当设置为 final 时, 则 JVM 创建对象时, 不会先使用默认值赋值再赋予设置的值。

#### 3.5.3 安全发布的常用模式

 >  要安全地发布一个对象, 对象的引用以及对象的状态必须同时对其他线程可见。
 >
 >  一个正确构造的对象可以通过以下方式来安全地发布：
 >  - 在静态初始化函数中初始化一个对象引用。
 >  - 将对象的引用保存到 volatile 类型的域或者 AtomicReferance 对象中。
 >  - 将对象的引用保存到某个正确构造对象的 final 类型域中。
 >  - 将对象的引用保存到一个由锁保护的域中。

 >  有锁保护的域中：
 >  - 通过将一个键或者值放入 Hashtable、synchronizedMap 或者 ConcurrentMap 中, 可以安全地将它发布给任何从这些容器中访问它的线程(无论是直接访问还是通过迭代器访问)。
 >  - 通过将某个元素放入 Vector、CopyOnWriteArrayList、CopyOnWriteArraySet、synchronizedList 或 synchronizedSet 中, 可以将该元素安全地发布到任何从这些容器中访问该元素的线程。
 >  - 通过将某个元素放入 BlockingQueue 或者 ConcurrentLinkedQueue 中, 可以将该元素安全地发布到任何从这些队列中访问该元素的线程。

 >  要发布一个静态构造的对象, 最简单和最安全的方式是使用静态的初始化器：
 >  public static Holder holder = new Holder(42);
 >  静态初始化器由 JVM 在类的初始化阶段执行。由于在 JVM 内部存在着同步机制, 因此通过这种方式初始化的任何对象都可以被安全地发布

#### 3.5.4 事实不可变对象

 >  如果对象从技术上来看是可变的, 但其状态在发布后不会再改变, 那么把这种对象称为“事实不可变对象(Effectively Immutable Object)”。

 >  在没有额外的同步的情况下, 任何线程都可以安全地使用被安全发布的事实不可变对象。

需要注意的是, 事实不可变对象发布时需要进行安全发布。例如, 将对象的引用保存到一个由锁保护的域中。否则, 例如仅仅使用一个 HashMap < Integer,  Object >  对象来存储数据, 保证 value 不会被修改也是不可以的。因为 HashMap 本身不是线程安全的, 可能 get 数据时正处于扩容阶段, 导致数据获取不到。

是这样理解的吗？

#### 3.5.5 可变对象

 >  对象的发布需求取决于它的可变性：
 >
 >  - 不可变对象可以通过任意机制来发布。
 >  - 事实不可变对象必须通过安全方式来发布。
 >  - 可变对象必须通过安全方式来发布, 并且必须是线程安全的或者由某个锁保护起来。

#### 3.5.6 安全地共享对象

 >  许多并发错误都是由于没有理解共享对象的这些“既定规则”而导致的。当发布一个对象时, 必须明确地说明对象的访问方式。

 >  在并发程序中使用和共享对象时, 可以使用一些实用的策略, 包括：
 >
 >  - 线程封闭。线程封闭的对象只能由一个线程拥有, 对象被封闭在该线程中, 并且只能由这个线程修改。
 >  - 只读共享。在没有额外同步的情况下, 共享的只读对象可以由多个线程并发访问, 但任何线程都不能修改它。共享的只读对象包括不可变对象和事实不可变对象。
 >  - 线程安全共享。线程安全的对象在其内部实现同步, 因此多个线程可以通过对象的公有接口来进行访问而不需要进一步的同步。
 >  - 保护对象。被保护的对象只能通过持有特定的锁来访问。保护对象包括封装在其他线程安全对象中的对象, 以及已发布的并且由某个特定锁保护的对象。

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

1 加锁

2 volatile 可见性

3 线程封闭

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

####  4.3.1 示例：基于委托的车辆追踪器

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
### 5.1.1 同步容器类的问题

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
 >  尽管有这些改进, 但仍然有一些需要权衡的因素。对于一些需要在整个 Map 上进行计算的方法, 例如 size 和 isEmpty, 这些方法的语义被略微减弱了以反映容器的并发特性。由于 size 返回的结果在计算时可能已经过期了, 它实际上只是一个估计值, 因此允许 size 返回一个近似值而不是一个精确值。虽然这看上去有些令人不安, 但事实上 size 和 isEmpty 这样的方法在并发环境下的用处很小, 因为它们的返回值总在不断变化。

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

 >  CountDownLatch 是一种灵活的闭锁实现, 可以在上述各种情况中使用, 它可以使一个或多个线程等待一组事件发生。闭锁状态包括一个计数器, 该计数器被初始化为一个正数, 表示需要等待的事件数量。countDown 方法递减计数器, 表示有一个事件已经发生了, 而 await 方法等待计数器达到零, 这表示所有需要等待的事件都已经发生。如果计数器的值非零, 那么 await 会一直阻塞直到计数器为零, 或者等待中的线程中断, 或者等待超时。

 >  TestHarness 创建一定数量的线程, 利用它们并发地执行指定的任务。它使用两个闭锁, 分别表示“起始门(Starting Gate)”和“结束门(Ending Gate)”。起始门计数器的初始值为 1, 而结束门计数器的初始值为工作线程的数量。每个工作线程首先要做的值就是在启动门上等待, 从而确保所有线程都就绪后才开始执行。而每个线程要做的最后一件事情是将调用结束门的 countDown 方法减 1, 这能使主线程高效地等待直到所有工作线程都执行完成, 因此可以统计所消耗的时间。

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
 >
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

```java
public class BoundedHashSet < T > {
	private final Set < T > set;
	private final Semaphore sem;
	public BoundedHashSet(int bound){
		this.set=Collections.synchronizedSet(new HashSet < T > ());
		sem=new Semaphore(bound);
	}
	public boolean add(T o)throws InterruptedException{
		sem.acquire();
		boolean wasAdded=false;
		try{
			wasAdded=set.add(o);
			return wasAdded;
		}finally{
			if(！wasAdded)sem.release();
		}
	}
	public boolean remove(Object o){
		boolean wasRemoved=set.remove(o);
		if(wasRemoved)sem.release();
			return wasRemoved;
		}
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
> 所有的并发问题都可以归结为如何协调对并发状态的访问。可变状态越少，就越容易确保线程安全性。
> - 尽量将域声明为final类型，除非需要它们是可变的。
> - 不可变对象一定是线程安全的。
> 不可变对象能极大地降低并发编程的复杂性。它们更为简单而且安全，可以任意共享而无须使用加锁或保护性复制等机制。
> - 封装有助于管理复杂性。
> 在编写线程安全的程序时，虽然可以将所有数据都保存在全局变量中，但为什么要这样做？将数据封装在对象中，更易于维持不变性条件：将同步机制封装在对象中，更易于遵循同步策略。
> - 用锁来保护每个可变变量。
> - 当保护同一个不变性条件中的所有变量时，要使用同一个锁。
> - 在执行复合操作期间，要持有锁。
> - 如果从多个线程中访问同一个可变变量时没有同步机制，那么程序会出现问题。
> - 不要故作聪明地推断出不需要使用同步。
> - 在设计过程中考虑线程安全，或者在文档中明确地指出它不是线程安全的。
> - 将同步策略文档化。
