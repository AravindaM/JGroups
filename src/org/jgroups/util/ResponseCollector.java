package org.jgroups.util;

import org.jgroups.Address;
import org.jgroups.annotations.GuardedBy;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;

/** Similar to AckCollector, but collects responses, not just acks. Null is not a valid key.
 * @author Bela Ban
 */
public class ResponseCollector<T> {
    @GuardedBy("lock")
    private final Map<Address,T> responses;
    private final Lock           lock=new ReentrantLock(false);
    private final Condition      cond=lock.newCondition();


    /**
     *
     * @param members List of members from which we expect responses
     */
    public ResponseCollector(Collection<Address> members) {
        responses=members != null? new HashMap<Address,T>(members.size()) : new HashMap<Address,T>();
        reset(members);
    }

    public ResponseCollector(Address ... members) {
        responses=members != null? new HashMap<Address,T>(members.length) : new HashMap<Address,T>();
        reset(members);
    }

    public ResponseCollector() {
        responses=new HashMap<Address,T>();
    }

    public void add(Address member, T data) {
        if(member == null)
            return;
        lock.lock();
        try {
            if(responses.containsKey(member)) {
                responses.put(member, data);
                cond.signalAll();
            }
        }
        finally {
            lock.unlock();
        }
    }

    public void remove(Address member) {
        if(member == null)
            return;
        lock.lock();
        try {
            responses.remove(member);
            cond.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    public void remove(List<Address> members) {
        if(members == null || members.isEmpty())
            return;
        lock.lock();
        try {
            for(Address member: members)
                responses.remove(member);
            cond.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    public void retainAll(List<Address> members) {
        if(members == null || members.isEmpty())
            return;
        lock.lock();
        try {
            if(responses.keySet().retainAll(members))
                cond.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    public void suspect(Address member) {
        if(member == null)
            return;
        lock.lock();
        try {
            if(responses.remove(member) != null)
                cond.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    public boolean hasAllResponses() {
        lock.lock();
        try {
            if(responses.isEmpty())
                return true;
            for(Map.Entry<Address,T> entry: responses.entrySet()) {
                if(entry.getValue() == null)
                    return false;
            }
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    public int numberOfValidResponses() {
        int retval=0;
        lock.lock();
        try {
            for(Map.Entry<Address,T> entry: responses.entrySet()) {
                if(entry.getValue() != null)
                    retval++;
            }
            return retval;
        }
        finally {
            lock.unlock();
        }
    }

    /** Returns a list of members which didn't send a valid response */
    public List<Address> getMissing() {
        List<Address> retval=new ArrayList<Address>();
        for(Map.Entry<Address,T> entry: responses.entrySet()) {
            if(entry.getValue() == null)
                retval.add(entry.getKey());
        }
        return retval;
    }

    public List<Address> getValidResults() {
        List<Address> retval=new ArrayList<Address>();
        for(Map.Entry<Address,T> entry: responses.entrySet()) {
            if(entry.getValue() != null)
                retval.add(entry.getKey());
        }
        return retval;
    }


    public Map<Address,T> getResults() {
        return responses;
    }

    public int size() {
        lock.lock();
        try {
            return responses.size();
        }
        finally {
            lock.unlock();
        }
    }


    /**
     * Waits until all responses have been received, or until a timeout has elapsed.
     * @param timeout Number of milliseconds to wait max. This value needs to be greater than 0, or else
     * it will be adjusted to 2000
     * @return boolean True if all responses have been received within timeout ms, else false (e.g. if interrupted)
     */
    public boolean waitForAllResponses(long timeout) {
        if(timeout <= 0)
            timeout=2000L;
        long end_time=System.currentTimeMillis() + timeout;
        long wait_time;

        lock.lock();
        try {
            while(!hasAllResponses()) {
                wait_time=end_time - System.currentTimeMillis();
                if(wait_time <= 0)
                    return false;
                try {
                    cond.await(wait_time, TimeUnit.MILLISECONDS);
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt(); // set interrupt flag again
                    return false;
                }
            }
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    public void reset() {
        reset((Collection<Address>)null);
    }

    public void reset(Collection<Address> members) {
        lock.lock();
        try {
            responses.clear();
            if(members != null) {
                for(Address mbr: members)
                    responses.put(mbr, null);
            }
            cond.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    public void reset(Address ... members) {
        lock.lock();
        try {
            responses.clear();
            if(members != null) {
                for(Address mbr: members)
                    responses.put(mbr, null);
            }
            cond.signalAll();
        }
        finally {
            lock.unlock();
        }
    }



    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append(responses).append(", complete=").append(hasAllResponses());
        return sb.toString();
    }
}
