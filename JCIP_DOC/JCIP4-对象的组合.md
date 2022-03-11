> 本章讨论的是对于可变对象，如何设计一个线程安全类。

## 1 BackGround

### 1.1 设计线程安全类的步骤

在设计线程安全类的过程中, 需要包含以下三个基本要素：
- 找出构成对象状态的所有变量。
- 找出约束状态变量的不变性条件。
- 建立对象状态的并发访问管理策略。

同步策略(Synchronization Policy)定义了如何在不违背对象不变条件或后验条件的情况下对其状态的访问操作进行协同。同步策略规定了如何将不可变性、线程封闭与加锁机制等结合起来以维护线程的安全性, 并且还规定了哪些变量由哪些锁来保护。要确保开发人员可以对这个类进行分析与维护, 就必须将同步策略写为正式文档。

### 1.2 三种条件

1. 不变性条件：不变式表达了对状态的约束，这些状态是应该符合这个约束的值的组合。不变式可以代表某种业务规则。例如，学生 ID 不能为负值。
2. 先验条件：针对方法，规定了在调用方法之前必须为真的条件。例如，队列不为空，才能 poll。
3. 后验条件：针对方法，规定了在调用方法之后必须为真的条件。例如，当前状态为 16，那么下一个状态必须为 17.

当下一个状态依赖于当前状态时, 这个操作必须是复合操作, 需加锁完成。

单个变量的不变性条件，要判断是否在并发访问情况下被破坏。

多个变量的不变性条件，要判断变量之间是否独立。如果是相互独立的，那么每个变量的不变性条件各自进行判断与保证线程安全。如果不是独立的，那么就需要操作复合。例如，[NumberRange.java](https://jcip.net/listings/NumberRange.java)中变量 lower 和 upper 是不独立的变量，修改其中一个变量时必须进行操作复合，否则可能变成无效状态。如两个线程分别修改 lower 和 upper，导致 lower>upper。

### 1.3 状态的所有权

一个对象状态的概念：如果以某个对象为根节点构造一张对象图, 那么该对象的状态将是对象图中所有对象包含的域的一个子集。例如，有一个 HashMap 对象，那么还会包含 HashMap 对象中，key 和 value 对象的状态。

所有权与封装性总是相互关联的：对象封装它拥有的状态, 反之也成立, 即对它封装的状态拥有所有权。

容器类通常表现出一种“所有权分离”的形式, 其中容器类拥有其自身的状态, 而客户代码则拥有容器中各个对象的状态。

Servlet 框架中的 ServletContext 就是其中一个示例。ServletContext 为 Servlet 提供了类似于 Map 形式的对象容器服务, 在 ServletContext 中可以通过名称来注册(setAttribute)或获取(getAttribute)应用程序对象。由 Servlet 容器实现的 ServletContext 对象必须是线程安全的, 因为它肯定会被多个线程同时访问。当调用 setAttribute 和 getAttribute 时, Servlet 不需要使用同步, 但当使用保存在 ServletContext 中的对象时, 则可能需要使用同步。这些对象由应用程序拥有, Servlet 容器只是替应用程序保管它们。

## 2 线程安全类的实现
### 2.1 单个对象的线程安全
封装简化了线程安全类的实现过程, 它提供了一种实例封闭机制(InstanceConfinement), 通常也简称为“封闭”。将数据封装在对象内部, 可以将数据的访问限制在对象的方法上, 从而更容易确保线程在访问数据时总能持有正确的锁。

被封闭对象一定不能超出它们既定的作用域。封装的方式有： 
- 可以封闭在类的一个实例(例如作为类的一个私有成员)中, 
- 或者封闭在某个作用域内(例如作为一个局部变量), 
- 或者封闭在线程内(例如在某个线程中将对象从一个方法传递到另一个方法, 而不是在多个线程之间共享该对象)。

Java 针对 ArrayList、HashMap 等容器类提供了一种包装器工厂方法，例如 Collections.synchronizedList 及其类似方法，使得这些非线程安全的类可以在多线程环境中安全地使用。这种机制属于“装饰器模式”。

#### 2.1.1 Java 监视器模式

遵循 Java 监视器模式的对象会把对象的所有可变状态都封装起来, 并由对象自己的内置锁来保护。Java 监视器模式仅仅是一种编写代码的约定, 对于任何一种锁对象, 只要自始至终都使用该锁对象, 都可以用来保护对象的状态。

两种实现：
1. 内置锁。使用对象的内置锁(或任何其他可通过公有方式访问的锁)。例如, get 和 set 方法加上 Synchronized 修饰。
2. 私有锁。使用私有的锁对象, 例如, 对于封装的对象进行加锁, 如下面所示。或者也可以生成一个私有的其他对象, 获取该对象的锁才可以操作被封装的对象, 有些类似于 redis 使用 setnx 实现分布式锁的原理。

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

私有锁优于内置锁的地方在于：(1)客户端代码无法获取锁，但可以通过暴露的方法访问锁，从而参与同步策略中。(2)客户端若获取了内置锁，可能有活跃性问题。(3)验证内置锁是否正确使用，需要检查整个程序(因为可以被客户获取)，而非仅仅单个类。

### 2.2 多个对象的线程安全

如果多个线程安全的类进行组合，是否需要增加额外的线程安全层？视情况而定。例如 putIfAbsent 操作，如果不使用直接提供的函数，不加锁，那么就属于先检查再判断，即存在竞态条件。

#### 2.2.1 (状态变量独立)线程安全性的委托

多个状态变量之间彼此独立，那么组合而成的类的线程安全性可以委托给各个状态变量去实现。只要各个状态变量都是线程安全的，那么组合类就是线程安全的。如下面的监听鼠标、监听键盘对象是彼此独立的，因此 VisualComponent 类的线程安全性可以交给 keyListeners 和 mouseListeners 分别去实现。

```java
public class VisualComponent{
    private final List < KeyListener > keyListeners=new CopyOnWriteArrayList < KeyListener > ();
    private final List < MouseListener > mouseListeners=new CopyOnWriteArrayList < MouseListener > ();
    public void addKeyListener(KeyListener listener){keyListeners.add(listener);}
}
```

#### 2.2.2 (状态变量不独立)委托失效

当状态变量不独立的时候，那么组合类的线程安全性不能委托给各个状态变量去实现。此时就需要复合操作来完成。

### 2.3 线程安全的示例 —— 车辆追踪

#### 2.3.1 示例1：车辆追踪

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

#### 2.3.2 示例：基于委托的车辆追踪器

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

#### 2.3.3 示例：发布状态的车辆追踪器

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

## 3 线程安全类基础上增加功能

方法总结：
1. 修改原有类(一般不现实)
2. 扩展该类，通过 extend 或 implement
3. 客户端加锁机制，对被扩展的对象进行加锁
4. 组合/Java 监视器模式, 然后在扩展功能上对被监视的对象进行加锁。

### 3.1 扩展该类

扩展该类是有一定脆弱性：
1. 同步策略被分配到多个源代码文件中
2. 底层改变了同步策略并使用不同的锁来保护状态变量，那么子类就会被破坏。因为在同步策略改变后它无法再使用正确的锁来控制对基类状态的并发访问。(在 Vector 的规范中定义了它的同步策略, 因此 BetterVector 不存在这个问题。)

```java
public class BetterVector < E > extends Vector < E > {
    public synchronized boolean putIfAbsent(E x){
        ...
    }
}
```

### 3.2 客户端加锁

对于由 Collections.synchronizedList 封装的 ArrayList, 这两种方法在原始类中添加一个方法或者对类进行扩展都行不通, 因为客户代码并不知道在同步封装器工厂方法中返回的 List 对象的类型。

下面这个是线程不安全的，原因在于 list 是 public 对象。那么线程可以调用 list 对象的函数，也可以调用 ListHelper 对象的函数。而由于两个对象的锁并不是同一把锁，因此仍然存在线程不安全的情况。

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

合适的解决方法：直接对 list 对象进行加锁，如下所示。

```java
public boolean putIfAbsent(E x){
    synchronized(list){
        boolean absent=！list.contains(x); 
        if(absent)list.add(x); return absent; 
    }
}
 ```

> 将 list 变成 private 对象也是可以的，也就是第四种方法，具体见后文。

#### 3.2.1 Collections.synchronizedList 原理

这里补充上述解决方法的正确性。

1. Collections.synchronizedList 方法如下，其中 ArrayList 实现了 RandomAccess 接口，因此返回 SynchronizedRandomAccessList 对象。
```java
public static <T> List<T> synchronizedList(List<T> list) { 
	return (list instanceof RandomAccess ?  
                new SynchronizedRandomAccessList<T>(list) : new SynchronizedList<T>(list));  
}
```

2. SynchronizedRandomAccessList 的实现机制是申请 mutex 对象的锁来进行同步，mutex 对象继承自 SynchronizedList 父类。
```java
static class SynchronizedRandomAccessList<E>  extends SynchronizedList<E>  implements RandomAccess {  
        SynchronizedRandomAccessList(List<E> list) {  
            super(list);  
        }  
    SynchronizedRandomAccessList(List<E> list, Object mutex) {  
            super(list, mutex);  
        }  
    public List<E> subList(int fromIndex, int toIndex) {  
        synchronized(mutex) {  
                return new SynchronizedRandomAccessList<E>(  
                    list.subList(fromIndex, toIndex), mutex);  
            }  
        }  
    }  
```

3. 查看 SynchronizedList 可以看到，没有传递 mutex 进行构造时，mutex 默认是该实例对象本身。mutex 是 SynchronizedCollection 对象本身，SynchronizedRandomAccessList 继承 SynchronizedCollection，所以也是 SynchronizedRandomAccessList 对象本身，... 也是 Collections.synchronizedList 对象本身。
```java
static class SynchronizedCollection<E> implements Collection<E>, Serializable { 
    final Collection<E> c;  // Backing Collection 
    final Object mutex; // Object on which to synchronize 
	 SynchronizedCollection(Collection<E> c) { 
		 if (c==null) throw new NullPointerException(); 
		 this.c = c;  
		mutex = this;  
	}  
    SynchronizedCollection(Collection<E> c, Object mutex) { 
		this.c = c; 
		this.mutex = mutex;  
    }  
}
```
4. 因此，对 list 加锁，和 list 自身各个方法中的锁是同一个锁，所以是线程安全的。

> 疑问：如果使用了 ConcurrentHashMap 的话，那么这样做可以吗？上述例子可以成立是由于 Collections.synchronizedList 的同步策略是对整个对象进行加锁。而 ConcurrentHashMap(1.8)的同步策略是乐观锁的 CAS 机制。

### 3.3 组合

这里直接将 list 对象设置为私有的，同时需要实现 List 的所有方法。

```java
@ThreadSafe
public class ImprovedList<T> implements List<T> {
    private final List<T> list;
    public ImprovedList(List<T> list) { this.list = list; }
    public synchronized boolean putIfAbsent(T x) {
        boolean contains = list.contains(x);
        if (!contains)
            list.add(x);
        return !contains;
    }
    public int size() {        return list.size();    }
    ...
}
```

## 4 同步策略文档化

 >  在文档中说明客户代码需要了解的线程安全性保证, 以及代码维护人员需要了解的同步策略。

 >  synchronized、volatile 或者任何一个线程安全类都对应于某种同步策略, 用于在并发访问时确保数据的完整性。

 >  设计阶段是编写设计决策文档的最佳时间。

 >  在设计同步策略时需要考虑多个方面, 例如, 将哪些变量声明为 volatile 类型, 哪些变量用锁来保护, 哪些锁保护哪些变量, 哪些变量必须是不可变的或者被封闭在线程中的, 哪些操作必须是原子操作等。其中某些方面是严格的实现细节, 应该将它们文档化以便于日后的维护。还有一些方面会影响类中加锁行为的外在表现, 也应该将其作为规范的一部分写入文档。

 >  最起码, 应该保证将类中的线程安全性文档化。它是否是线程安全的？在执行回调时是否持有一个锁？是否有某些特定的锁会影响其行为？不要让客户冒着风险去猜测。如果你不想支持客户端加锁也是可以的, 但一定要明确地指出来。如果你希望客户代码能够在类中添加新的原子操作, 如 4.4 节所示, 那么就需要在文档中说明需要获得哪些锁才能实现安全的原子操作。如果使用锁来保护状态, 那么也要将其写入文档以便日后维护, 这很简单, 只需使用标注@GuardedBy 即可。如果要使用更复杂的方法来维护线程安全性, 那么一定要将它们写入文档, 因为维护者通常很难发现它们。

 >  我们是否应该因为某个对象看上去是线程安全的而就假设它是安全的？是否可以假设通过获取对象的锁来确保对象访问的线程安全性？(只有当我们能控制所有访问该对象的代码时, 才能使用这种带风险的技术, 否则, 这只能带来线程安全性的假象。)

 >  如果某个类没有明确地声明是线程安全的, 那么就不要假设它是线程安全的, 从而有效地避免类似于 SimpleDateFormat 的问题。而另一方面, 如果不对容器提供对象(例如 HttpSession)的线程安全性做某种有问题的假设, 也就不可能开发出一个基于 Servlet 的应用程序。

 >  一个提高猜测准确性的方法是, 从实现者(例如容器或数据库的供应商)的角度去解释规范, 而不是从使用者的角度去解释。Servlet 通常是在容器管理的(Container-Managed)线程中调用的, 因此可以安全地假设：如果有多个这种线程在运行, 那么容器是知道这种情况的。Servlet 容器能生成一些为多个 Servlet 提供服务的对象, 例如 HttpSession 或 ServletContext。因此, Servlet 容器应该预见到这些对象将被并发访问, 因为它创建了多个线程, 并且从这些线程中调用像 Servlet.service 这样的方法, 而这个方法很可能会访问 ServletContext。