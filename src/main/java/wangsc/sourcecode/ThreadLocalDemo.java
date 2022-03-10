package wangsc.sourcecode;


import java.lang.ref.WeakReference;

public class ThreadLocalDemo {

    public static void main(String[] args) {

    }
}

class Thread implements Runnable {
    /* ThreadLocal values pertaining to this thread. This map is maintained
     * by the ThreadLocal class. */
    ThreadLocal.ThreadLocalMap threadLocals = null;

    /*
     * InheritableThreadLocal values pertaining to this thread. This map is
     * maintained by the InheritableThreadLocal class.
     */
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;

    @Override
    public void run() { }
}

class ThreadLocal<T> {
    static class ThreadLocalMap {

        private Entry[] table;
        /**
         * The entries in this hash map extend WeakReference, using
         * its main ref field as the key (which is always a
         * ThreadLocal object).  Note that null keys (i.e. entry.get()
         * == null) mean that the key is no longer referenced, so the
         * entry can be expunged from table.  Such entries are referred to
         * as "stale entries" in the code that follows.
         */
        static class Entry extends WeakReference<ThreadLocal<?>> {
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }

        private Entry getEntry(ThreadLocal<?> key) {
            return null;
        }

        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
            return null;
        }

        private void set(ThreadLocal<?> key, Object value) {

        }

        private void remove(ThreadLocal<?> key) {

        }
    }
}
