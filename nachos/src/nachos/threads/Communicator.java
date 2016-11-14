package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
//	test4
	Lock lock = null;
	Lock lock2 = null;
	Condition2 speak = null;
	Condition2 listen = null;
	private LinkedList<Integer> list = null;
	private boolean first = true;
    public Communicator() {
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
		boolean status = Machine.interrupt().disable();
		System.out.print("speak init with msg:" + word + " current time is "
				+ Machine.timer().getTime() + "\n");
		// lock2.acquire();
		lock.acquire();
		System.out.print("speak will sleep" + " current time is "
				+ Machine.timer().getTime() + "\n");
		speak.sleep();
		System.out.println("speak is awake");
		list.add(word);
		listen.wake();
		System.out.print("speak will awake listen" + " current time is "
				+ Machine.timer().getTime() + "\n");
		lock.release();
		// lock2.release();
		Machine.interrupt().restore(status);
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
//	return 0;
//    	test4
    	boolean status = Machine.interrupt().disable();
		int msg = 0;
		System.out.print("listenning start" + " current time is "
				+ Machine.timer().getTime() + "\n");
		// lock2.acquire();
		lock.acquire();
		System.out.print("listen will sleep" + " current time is "
				+ Machine.timer().getTime() + "\n");
		if (first) {
			System.out.println("listen will awake speak");
			speak.wake();
			first = false;
		}
		listen.sleep();
		System.out.println("listen is woken");
		if (!list.isEmpty()) {
			System.out.println("queue is not empty");
			msg = list.removeFirst();
		} else {
			System.out.println("queue is empty");
		}
		System.out.print("listen the msg " + msg);
		System.out.print("listen will awake speak" + " current time is "
				+ Machine.timer().getTime() + "\n");
		speak.wake();
		lock.release();
		// lock2.release();
		Machine.interrupt().restore(status);
		return msg;
    }
}
