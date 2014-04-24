package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		
		// The lock for communication
		comLock = new Lock();
		
		// Speak condition
		speakCond = new Condition2(comLock);
		
		speakQueue = new LinkedList<KThread>();
		
		// Listen condition
		listenCond = new Condition2(comLock);
		
		listenQueue = new LinkedList<KThread>();
		
		
		buffer = new LinkedList<Integer>();
		
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		
		// Aquire the lock
		comLock.acquire();
		
		buffer.add(new Integer(word));
		
		if (listenQueue.size() == 0)
		{
			speakQueue.add(KThread.currentThread());
			speakCond.sleep();
		}
		else
		{
			listenQueue.remove();
			listenCond.wake();
		}
		
		
		comLock.release();
		
		
		
//		KThread nextThread = listenQueue.nextThread();
//		if ( nextThread == null )
//		{
//			speakQueue.waitForAccess(KThread.currentThread());
//			
//			speakCond.sleep();
//		}
//		else
//		{
//			speakQueue.acquire(KThread.currentThread());
//			
//			Lib.assertTrue(message == null);
//			message = new Integer(word);
//			
//			listenCond.wake(); // should we be waking? Or call ready() directly on the next thread.
//		}
//		
//		comLock.release();
//		
		
		
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		
		int wordToReturn;
		
		// Aquire the lock
		comLock.acquire();
		
		if (speakQueue.size() == 0)
		{
			listenQueue.add(KThread.currentThread());
			listenCond.sleep();
		}
		else
		{
			speakQueue.remove();
			speakCond.wake();
		}
		
		wordToReturn = buffer.remove().intValue();
		
		comLock.release();
		
		return wordToReturn;
		
//		KThread nextThread = speakQueue.nextThread();
//		if ( nextThread == null )
//		{
//			listenQueue.waitForAccess(KThread.currentThread());
//			
//			listenCond.sleep();
//		}
//		else
//		{
//			listenQueue.acquire(KThread.currentThread());
//			
//			Lib.assertTrue(message != null);
//			
//			wordToReturn = message.intValue();
//			message = null;
//			
//			listenCond.wake(); // should we be waking? Or call ready() directly on the next thread.
//		}
//		
//		comLock.release();
	}
	
	private Lock comLock;
	private Condition2 speakCond;
	private Condition2 listenCond;
	private LinkedList<Integer> buffer;
	private LinkedList<KThread> speakQueue;
	private LinkedList<KThread> listenQueue;
	
	
	
	//A class that contains the tester. You pass this to the KThread constructor
	//to give the thread code to execute. 
	protected static class MyTester implements Runnable {
	 // Thread local copies of global variables go here. You need to access these
	 // variables in run, but they are passed to the tester in the constructor.
	 private int id;
	 
	 // Construct the object. Pass the ID of the thread plus any variables you
	 // want to share between threads. You may want to pass a KThread as a global
	 // variable to test join.
		MyTester(int id) {
		    this.id = id;
		}
		
		// This method contains the actual code run by the thread. The constructor
		// is run by the main thread! You will want to test methods, such as,
		// join, waitUntil, speak, and listen, in here. 
		public void run() {
		    // Use an if statement to make the different threads execute different
		    // code.
		    if (id == 0) {
		        for (int i = 0; i < 5; i++) {
		            System.out.println("Thread 0");
		            KThread.currentThread().yield();;
		        }
		    } else {
		        for (int i = 0; i < 5; i++) {
		            System.out.println("Thread 1");
		            KThread.currentThread().yield();
		        }
		    }
		}
	}

	//This method is called by the kernel when Nachos starts. 
	public static void selfTest() {
		// Initialize your global variables. You may want to make a Communicator
		// object, for example, and then share it between two threads.
		
		// Initialize your threads.
		KThread thread1 = new KThread(new MyTester(1));
		
		// Fork your new threads.
		thread1.fork();
		
		// This is the main thread. We can also consider this to be thread 0. So
		// let's have it run the code in the tester class as well.
		new MyTester(0).run();
	}

	
}
