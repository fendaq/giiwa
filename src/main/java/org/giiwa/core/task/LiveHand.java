package org.giiwa.core.task;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.giiwa.core.bean.TimeStamp;
import org.giiwa.core.bean.X;

/**
 * used to communicating between multiple thread<br>
 * it contains a semaphore used to control the man in hold <br>
 * 
 * @author joe
 *
 */
public class LiveHand {

	private boolean live = true;
	private long timeout = 0;

	private Semaphore door;

	private TimeStamp created = TimeStamp.create();

	private transient Map<String, Object> attachs = new HashMap<String, Object>();

	public void timeoutAfter(long timeout) {
		this.timeout = created.pastms() + timeout;
	}

	public void put(String name, Object value) {
		attachs.put(name, value);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String name) {
		return (T) attachs.get(name);
	}

	public Set<String> keys() {
		return attachs.keySet();
	}

	public long getTimeout() {
		return timeout;
	}

	public LiveHand(long timeout, int max) {
		this.timeout = timeout;
		if (max < 0)
			max = Integer.MAX_VALUE;
		this.door = new Semaphore(max);
	}

	public boolean isLive() {
		return live && (timeout <= 0 || created.pastms() < timeout);
	}

	public void stop() {
		live = false;
		door.release(Integer.MAX_VALUE);
	}

	public boolean hold() throws InterruptedException {
		while (isLive()) {
			long waittime = X.AMINUTE;
			if (timeout > 0) {
				waittime = timeout - created.pastms();
			}
			if (waittime > 0) {
				if (door.tryAcquire(waittime, TimeUnit.MILLISECONDS)) {
					return true;
				}
			} else {
				throw new InterruptedException("timeout for hold the hand");
			}
		}
		return false;
	}

	public void drop() {
		door.release();

		synchronized (door) {
			door.notifyAll();
		}
	}

	public boolean await() throws InterruptedException {
		if (timeout < 0) {
			return await(Long.MAX_VALUE - created.pastms());
		}
		return await(timeout - created.pastms());
	}

	public boolean await(long timeout) throws InterruptedException {

		TimeStamp t = TimeStamp.create();

		long t1 = timeout - t.pastms();
		while (t1 > 0 && isLive()) {
			if (door.drainPermits() == 0) {
				return true;
			}
			synchronized (door) {
				door.wait(t1);
			}
			t1 = timeout - t.pastms();
		}

		return isLive();
	}

	@Override
	public String toString() {
		return "LiveHand [live=" + live + ", count=" + door.drainPermits() + ", timeout=" + timeout + ", past="
				+ created.pastms() + "ms, attachs=" + attachs + "]";
	}

	public static void main(String[] args) {
		LiveHand h = new LiveHand(-1, 20);
		System.out.println("holding");
		try {
			h.hold();
			h.await(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("done");

	}
}
