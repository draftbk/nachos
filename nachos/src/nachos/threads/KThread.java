package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an
 * argument when creating <tt>KThread</tt>, and forked. For example, a thread
 * that computes pi could be written as follows:
 *
 * <p><blockquote><pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre></blockquote>
 * <p>The following code would then create a thread and start it running:
 *
 * <p><blockquote><pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre></blockquote>
 */
public class KThread {
    /**
     * Get the current thread.
     *
     * @return	the current thread.
     */
    public static KThread currentThread() {
	Lib.assertTrue(currentThread != null);
	return currentThread;
    }
    
    /**
     * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
     * create an idle thread as well.
     */
    public KThread() {
    	waitQueue=ThreadedKernel.scheduler.newThreadQueue(false);
    	
    	
	if (currentThread != null) {
	    tcb = new TCB();
	}	    
	else {
	    readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
	    readyQueue.acquire(this);	    

	    currentThread = this;
	    tcb = TCB.currentTCB();
	    name = "main";
	    restoreState();

	    createIdleThread();
	}
    }

    /**
     * Allocate a new KThread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     */
    public KThread(Runnable target) {
	this();
	this.target = target;
    }

    /**
     * Set the target of this thread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     * @return	this thread.
     */
    public KThread setTarget(Runnable target) {
	Lib.assertTrue(status == statusNew);
	
	this.target = target;
	return this;
    }

    /**
     * Set the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @param	name	the name to give to this thread.
     * @return	this thread.
     */
    public KThread setName(String name) {
	this.name = name;
	return this;
    }

    /**
     * Get the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @return	the name given to this thread.
     */     
    public String getName() {
	return name;
    }

    /**
     * Get the full name of this thread. This includes its name along with its
     * numerical ID. This name is used for debugging purposes only.
     *
     * @return	the full name given to this thread.
     */
    public String toString() {
	return (name + " (#" + id + ")");
    }

    /**
     * Deterministically and consistently compare this thread to another
     * thread.
     */
    public int compareTo(Object o) {
	KThread thread = (KThread) o;

	if (id < thread.id)
	    return -1;
	else if (id > thread.id)
	    return 1;
	else
	    return 0;
    }

    /**
     * Causes this thread to begin execution. The result is that two threads
     * are running concurrently: the current thread (which returns from the
     * call to the <tt>fork</tt> method) and the other thread (which executes
     * its target's <tt>run</tt> method).
     */
    public void fork() {
	Lib.assertTrue(status == statusNew);
	Lib.assertTrue(target != null);
	
	Lib.debug(dbgThread,
		  "Forking thread: " + toString() + " Runnable: " + target);

	boolean intStatus = Machine.interrupt().disable();

	tcb.start(new Runnable() {
		public void run() {
		    runThread();
		}
	    });

	ready();
	
	Machine.interrupt().restore(intStatus);
    }

    private void runThread() {
	begin();
	target.run();
	finish();
    }

    private void begin() {
	Lib.debug(dbgThread, "Beginning thread: " + toString());
	
	Lib.assertTrue(this == currentThread);

	restoreState();

	Machine.interrupt().enable();
    }

    /**
     * Finish the current thread and schedule it to be destroyed when it is
     * safe to do so. This method is automatically called when a thread's
     * <tt>run</tt> method returns, but it may also be called directly.
     *
     * The current thread cannot be immediately destroyed because its stack and
     * other execution state are still in use. Instead, this thread will be
     * destroyed automatically by the next thread to run, when it is safe to
     * delete this thread.
     */
    public static void finish() {
	Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());
	
	Machine.interrupt().disable();

	Machine.autoGrader().finishingCurrentThread();

	Lib.assertTrue(toBeDestroyed == null);
	toBeDestroyed = currentThread;


	currentThread.status = statusFinished;
	
	KThread thread = currentThread.waitQueue.nextThread();//鍙栧嚭浜咥绾跨▼锛宑urrentThread鐜板湪鎸囩殑鏄璋冪敤鐨凚绾跨▼
	
	while(thread!=null){
		thread.ready();
		thread = currentThread.waitQueue.nextThread();
	}
	
	sleep();
    }

    /**
     * Relinquish the CPU if any other thread is ready to run. If so, put the
     * current thread on the ready queue, so that it will eventually be
     * rescheuled.
     *
     * <p>
     * Returns immediately if no other thread is ready to run. Otherwise
     * returns when the current thread is chosen to run again by
     * <tt>readyQueue.nextThread()</tt>.
     *
     * <p>
     * Interrupts are disabled, so that the current thread can atomically add
     * itself to the ready queue and switch to the next thread. On return,
     * restores interrupts to the previous state, in case <tt>yield()</tt> was
     * called with interrupts disabled.
     */
    public static void yield() {
	Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());
	
	Lib.assertTrue(currentThread.status == statusRunning);
	
	boolean intStatus = Machine.interrupt().disable();

	currentThread.ready();

	runNextThread();
	
	Machine.interrupt().restore(intStatus);
    }

    /**
     * Relinquish the CPU, because the current thread has either finished or it
     * is blocked. This thread must be the current thread.
     *
     * <p>
     * If the current thread is blocked (on a synchronization primitive, i.e.
     * a <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
     * some thread will wake this thread up, putting it back on the ready queue
     * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
     * scheduled this thread to be destroyed by the next thread to run.
     */
    public static void sleep() {
	Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());
	
	Lib.assertTrue(Machine.interrupt().disabled());

	if (currentThread.status != statusFinished)
	    currentThread.status = statusBlocked;

	runNextThread();
    }

    /**
     * Moves this thread to the ready state and adds this to the scheduler's
     * ready queue.
     */
    public void ready() {
	Lib.debug(dbgThread, "Ready thread: " + toString());
	
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(status != statusReady);
	
	status = statusReady;
	if (this != idleThread)
	    readyQueue.waitForAccess(this);
	
	Machine.autoGrader().readyThread(this);
    }

    /**
     * Waits for this thread to finish. If this thread is already finished,
     * return immediately. This method must only be called once; the second
     * call is not guaranteed to return. This thread must not be the current
     * thread.
     */
    public void join() {
	Lib.debug(dbgThread, "Joining to thread: " + toString());

	Lib.assertTrue(this != currentThread);//assertTrue鏂█涓虹湡銆傚鏋滃垽鏂唴瀹逛负鐪燂紝鎵嶈兘缁х画寰�笅鎵ц
	
	if(status==statusFinished){
		return;
	}//If this thread is already finished,return immediately
	
	boolean intStatus = Machine.interrupt().disable();//鍏充腑鏂紝闃叉鍏朵粬绾跨▼鎹ｄ贡

	
	waitQueue.waitForAccess(currentThread);
	currentThread.sleep();//璁╁綋鍓嶇嚎绋婣sleep锛岀劧鍚庢墠鑳借B鎵ц鍛�
	
	Machine.interrupt().restore(intStatus);//寮�腑鏂�
	
    }

    /**
     * Create the idle thread. Whenever there are no threads ready to be run,
     * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
     * idle thread must never block, and it will only be allowed to run when
     * all other threads are blocked.
     *
     * <p>
     * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
     */
    private static void createIdleThread() {
	Lib.assertTrue(idleThread == null);
	
	idleThread = new KThread(new Runnable() {
	    public void run() { while (true) yield(); }
	});
	idleThread.setName("idle");

	Machine.autoGrader().setIdleThread(idleThread);
	
	idleThread.fork();
    }
    
    /**
     * Determine the next thread to run, then dispatch the CPU to the thread
     * using <tt>run()</tt>.
     */
    private static void runNextThread() {
	KThread nextThread = readyQueue.nextThread();
	if (nextThread == null)
	    nextThread = idleThread;

	nextThread.run();
    }

    /**
     * Dispatch the CPU to this thread. Save the state of the current thread,
     * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
     * load the state of the new thread. The new thread becomes the current
     * thread.
     *
     * <p>
     * If the new thread and the old thread are the same, this method must
     * still call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
     * <tt>restoreState()</tt>.
     *
     * <p>
     * The state of the previously running thread must already have been
     * changed from running to blocked or ready (depending on whether the
     * thread is sleeping or yielding).
     *
     * @param	finishing	<tt>true</tt> if the current thread is
     *				finished, and should be destroyed by the new
     *				thread.
     */
    private void run() {
	Lib.assertTrue(Machine.interrupt().disabled());

	Machine.yield();

	currentThread.saveState();

	Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
		  + " to: " + toString());

	currentThread = this;

	tcb.contextSwitch();

	currentThread.restoreState();
    }

    /**
     * Prepare this thread to be run. Set <tt>status</tt> to
     * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
     */
    protected void restoreState() {
	Lib.debug(dbgThread, "Running thread: " + currentThread.toString());
	
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(this == currentThread);
	Lib.assertTrue(tcb == TCB.currentTCB());

	Machine.autoGrader().runningThread(this);
	
	status = statusRunning;

	if (toBeDestroyed != null) {
	    toBeDestroyed.tcb.destroy();
	    toBeDestroyed.tcb = null;
	    toBeDestroyed = null;
	}
    }

    /**
     * Prepare this thread to give up the processor. Kernel threads do not
     * need to do anything here.
     */
    protected void saveState() {
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(this == currentThread);
    }

    private static class PingTest implements Runnable {
	PingTest(int which) {
	    this.which = which;
	}
	public PingTest(int which,Lock lock,Condition2 condition2) {
		// TODO Auto-generated constructor stub
		this.which = which;
		this.lock=lock;
		this.condition2=condition2;
	}
//	test1
	public void run() {
    for (int i=0; i<5; i++) {
	System.out.println("*** thread " + which + " looped "
			   + i + " times");
	
	currentThread.yield();
	
    }
}
	
//	test2
//	public void run() {
//	    for (int i=0; i<5; i++) {
//		System.out.println("*** thread " + which + " looped "
//				   + i + " times");
//		//test2
//		if (i==2) {
//			lock.acquire();
//			condition2.wake();
//			lock.release();
//			currentThread.yield();
//		}
//		
//	    }
//	}
	
//	test3
//	public void run() {
//    for (int i=0; i<5; i++) {
//	System.out.println("*** thread " + which + " looped "
//			   + i + " times and current time: "+Machine.timer().getTime());
//	if (testAlarm) {
//		ThreadedKernel.alarm.waitUntil(350);
//	}
//	currentThread.yield();
//	
//    }
//}
//	test4--似乎run没有大用，用了和test1一样的run
//	public void run() {
//	    for (int i=0; i<5; i++) {
//		System.out.println("*** thread " + which + " looped "
//				   + i + " times");
//		
//		currentThread.yield();
//		
//	    }
//	}
	
	//鐢ㄦ潵娴嬭瘯test_1_2鐨勪笢瑗�
	public void run_t_2() {
	    for (int i=0; i<5; i++) {
		System.out.println("*** thread " + which + " looped "
				   + i + " times");
	

		if (i==2) {
			lock.acquire();
			condition2.sleep();;
			lock.release();
		}
		
	    }
	}

	private int which;
	private Lock lock;
	private Condition2 condition2;
    }

    /**
     * Tests whether this module is working.
     */
//    test1
    public static void selfTest() {
    	Lib.debug(dbgThread, "Enter KThread.selfTest");
    	
    	
    	//t1_1
    	KThread th = new KThread(new PingTest(1)).setName("forked thread");
    	th.fork();
    	th.join();
    	new PingTest(0).run();
      	

        }


//    test2
//    public static void selfTest() {
//	Lib.debug(dbgThread, "Enter KThread.selfTest");
//
//	//t1_2
//	Lock conLock=new Lock();
//	Condition2 condition2=new Condition2(conLock);
//	KThread th = new KThread(new PingTest(1,conLock,condition2)).setName("forked thread");
//	th.fork();
//	new PingTest(0,conLock,condition2).run_t_2();
//
//    }
    
//  test3
//  public static boolean testAlarm =false;
//  
//  public static void selfTest()
//  {
//  	testAlarm=true;
//  	final KThread kt = new KThread(new PingTest(1));
//  	kt.setName("forked thread").fork(); Runnable runner = new Runnable(){
//  	@Override
//  	public void run() { 
//  		System.out.println("thread waiting"); 
//  		ThreadedKernel.alarm.waitUntil(1100); 
//  	    System.out.println("thread finish");
//  	    }
//  	};
//  	KThread kt2=new KThread(runner); kt2.fork();
//  	new PingTest(0).run(); kt.join();
//  	        kt2.join();
//
//  }
// test4
//  public static void selfTest() {
//	  Communicator com = new Communicator(); 
//	  Speaker s = new Speaker(com); 
//	  Listener l = new Listener(com);
//	  KThread ks = new KThread(s);
//	  KThread kl = new KThread(l); 
//	  ks.fork();
//	  kl.fork();
//	  ThreadedKernel.alarm.waitUntil(1000); 
//	  }

    private static final char dbgThread = 't';

    /**
     * Additional state used by schedulers.
     *
     * @see	nachos.threads.PriorityScheduler.ThreadState
     */
    public Object schedulingState = null;

    private static final int statusNew = 0;
    private static final int statusReady = 1;
    private static final int statusRunning = 2;
    private static final int statusBlocked = 3;
    private static final int statusFinished = 4;

    /**
     * The status of this thread. A thread can either be new (not yet forked),
     * ready (on the ready queue but not running), running, or blocked (not
     * on the ready queue and not running).
     */
    private int status = statusNew;
    private String name = "(unnamed thread)";
    private Runnable target;
    private TCB tcb;

    /**
     * Unique identifer for this thread. Used to deterministically compare
     * threads.
     */
    private int id = numCreated++;
    /** Number of times the KThread constructor was called. */
    private static int numCreated = 0;

    private static ThreadQueue readyQueue = null;
    private static KThread currentThread = null;
    private static KThread toBeDestroyed = null;
    private static KThread idleThread = null;
    //test1
    private ThreadQueue waitQueue=null;
//    test4
    public static  class Message
    {
    	int type;
    	String msg;
    	
    	public Message(){}
    	public Message(int t,String m){type=t;msg=m;}
    	public String toString()
    	{
    		return "********************** type "+type+" and msg is "+msg+"****************** ";
    	}
    }
//    test4
    private static class Speaker implements Runnable
    {
    	Communicator cator = null;
    	public Speaker(Communicator com)
    	{
    		cator = com;
    	}
		@Override
		public void run() {
			cator.speak(new Message(1,"hello"));
		//	ThreadedKernel.alarm.waitUntil(400);
			cator.speak(new Message(2,"ha"));
			cator.speak(new Message(3,"p"));
			//ThreadedKernel.alarm.waitUntil(1400);
			cator.speak(new Message(4,"pok"));
			//cator.speak.sleep();
		}
    	
    }
//    test4
    private static class Listener implements Runnable
    {
    	Communicator cator = null;
    	public Listener(Communicator com)
    	{
    		cator = com;
    	}
		@Override
		public void run() {
			ThreadedKernel.alarm.waitUntil(400);
			Message msg = cator.listen();
		//
			msg = cator.listen();
		//	ThreadedKernel.alarm.waitUntil(1000);
			msg = cator.listen();
			msg=cator.listen();
			
		}
    	
    }
//    test4
    private static class Communicator
    {
    	Lock  lock = null;
    	Condition2 speak=null;
    	Condition2 listen =null;
    	private LinkedList<Message> list = null;
    	private boolean first = true;
    	public Communicator()
    	{
    		lock = new Lock();
    		new Lock();
    		speak = new Condition2(lock);
    		listen = new Condition2(lock);
    		list = new LinkedList<Message>();
    	}
    	
    	public void speak(Message msg)
    	{
    		boolean status = Machine.interrupt().disable();
    		System.out.print("speak init with msg:"+msg.toString()+" current time is "+Machine.timer().getTime()+"\n");
  //  		lock2.acquire();
    		lock.acquire();
    		System.out.print("speak will sleep"+" current time is "+Machine.timer().getTime()+"\n");
    		speak.sleep();
    		System.out.println("speak is awake");
    		list.add(msg);
    		listen.wake();
    		System.out.print("speak will awake listen"+" current time is "+Machine.timer().getTime()+"\n");
    		lock.release();
    //		lock2.release();
    		Machine.interrupt().restore(status);
    	}
    	public Message listen()
    	{

    		
    		boolean status = Machine.interrupt().disable();
    		Message msg = null;
    		System.out.print("listenning start"+" current time is "+Machine.timer().getTime()+"\n");
    //		lock2.acquire();
    		lock.acquire();
    		System.out.print("listen will sleep"+" current time is "+Machine.timer().getTime()+"\n");
    		if(first)
    		{
    			System.out.println("listen will awake speak");
    			speak.wake();
    			first=false;
    		}
    		listen.sleep();
    		System.out.println("listen is woken");
    		if(!list.isEmpty())
    		{
    			System.out.println("queue is not empty");
    		 msg = list.removeFirst();
    		}
    		else
    		{
    			System.out.println("queue is empty");
    		}
    		System.out.print("listen the msg "+msg.toString());
    		System.out.print("listen will awake speak"+" current time is "+Machine.timer().getTime()+"\n");
    		speak.wake();
    		lock.release();
    	//	lock2.release();
    		Machine.interrupt().restore(status);
    		return msg;
    	}
    }
}
