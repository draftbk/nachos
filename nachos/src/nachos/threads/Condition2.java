package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
	//初始化Queue
	this.waitQueue=ThreadedKernel.scheduler.newThreadQueue(false);
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	conditionLock.release();
	KThread thread=KThread.currentThread();
	boolean intStatus = Machine.interrupt().disable();//关中断
	waitQueue.waitForAccess(thread);
	thread.sleep();
	Machine.interrupt().restore(intStatus);//开中断
	conditionLock.acquire();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	boolean intState =Machine.interrupt().disable();
//	拿出一个Thread
    KThread thread = waitQueue.nextThread();//鍙栧嚭浜咥绾跨▼锛宑urrentThread鐜板湪鎸囩殑鏄璋冪敤鐨凚绾跨▼
	
	if(thread!=null){
		thread.ready();
	}
	Machine.interrupt().restore(intState);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	boolean intState =Machine.interrupt().disable();
    KThread thread = waitQueue.nextThread();
	while(thread!=null){
		thread.ready();
		thread = waitQueue.nextThread();
	}
	Machine.interrupt().restore(intState);
    }

    private Lock conditionLock;
    //
    private ThreadQueue waitQueue=null;
}
