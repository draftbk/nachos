package nachos.threads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
//	slf
	map = new HashMap<KThread,Long>();
    }
//    slf
    private HashMap<KThread,Long> map=null ;
    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
//    	以下都是test3
    	boolean status = Machine.interrupt().disable();
    	Set<KThread>  set = map.keySet();
    	Iterator<KThread> iter = set.iterator();
    	KThread kt = null;
    	List<KThread> list = new ArrayList<KThread>();
    	Long time = Machine.timer().getTime();
    	while(iter.hasNext())
    	{
    		kt = iter.next();
    		long times = map.get(kt);
    		if(times<=time)
    		{
    			list.add(kt);
    		}
    	//	kt = iter.next();
    	}
    	if(!list.isEmpty())
    	{
    		int i=0;
    		for(;i<list.size();i++)
    		{
    			map.remove(list.get(i));
    			list.get(i).ready();
    		}
    	}
        /*******************************/
    	KThread.currentThread().yield();
        /*******************************/
    	Machine.interrupt().restore(status);
//    	test3
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// for now, cheat just to get something working (busy waiting is bad)
	long wakeTime = Machine.timer().getTime() + x;//现在时间+设定的x
	//test3 4行
	boolean status = Machine.interrupt().disable();
	map.put(KThread.currentThread(), wakeTime);//设定wakeTime
	KThread.currentThread().sleep();
	Machine.interrupt().restore(status);
//	下面的是本来的
//	while (wakeTime > Machine.timer().getTime())
//	    KThread.yield();
    }
}
