/******************************************************************************
 *  Copyright 2015 by OLTPBenchmark Project                                   *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 ******************************************************************************/

package com.oltpbenchmark;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.oltpbenchmark.types.State;
import com.oltpbenchmark.util.QueueLimitException;
import org.apache.log4j.Logger;

/**
 * This class is used to share a state among the workers of a single
 * workload. Worker use it to ask for work and as interface to the global
 * BenchmarkState
 *
 * Performance optimizations:
 * - Using ConcurrentLinkedQueue for lock-free queue operations
 * - Using AtomicInteger for lock-free counter updates
 * - Minimized synchronized block scope
 *
 * @author alendit
 */
public class WorkloadState {
    private static final int RATE_QUEUE_LIMIT = 10000;
    private static final Logger LOG = Logger.getLogger(ThreadBench.class);

    // Using ConcurrentLinkedQueue for better concurrency
    private final ConcurrentLinkedQueue<SubmittedProcedure> workQueue = new ConcurrentLinkedQueue<SubmittedProcedure>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private BenchmarkState benchmarkState;

    // Using AtomicInteger for lock-free counter updates
    private final AtomicInteger workersWaiting = new AtomicInteger(0);
    private final AtomicInteger workersWorking = new AtomicInteger(0);
    private int num_terminals;
    private final AtomicInteger workerNeedSleep = new AtomicInteger(0);
    
    private List<Phase> works = new ArrayList<Phase>();
    private Iterator<Phase> phaseIterator;
    private Phase currentPhase = null;
    private long phaseStartNs = 0;
    private TraceReader traceReader = null;
    
    public WorkloadState(BenchmarkState benchmarkState, List<Phase> works, int num_terminals, TraceReader traceReader) {
        this.benchmarkState = benchmarkState;
        this.works = works;
        this.num_terminals = num_terminals;
        this.workerNeedSleep.set(num_terminals);
        this.traceReader = traceReader;

        phaseIterator = works.iterator();
    }
    
    /**
     * Add a request to do work.
     * Optimized with ConcurrentLinkedQueue for better throughput.
     *
     * @throws QueueLimitException
     */
    public void addToQueue(int amount, boolean resetQueues) throws QueueLimitException {
        if (resetQueues) {
            workQueue.clear();
            queueSize.set(0);
        }

        assert amount > 0;

        // Only use the work queue if the phase is enabled and rate limited.
        if (traceReader != null && currentPhase != null) {
            if (benchmarkState.getState() != State.WARMUP) {
                List<SubmittedProcedure> procedures = traceReader.getProcedures(System.nanoTime());
                for (SubmittedProcedure proc : procedures) {
                    workQueue.add(proc);
                    queueSize.incrementAndGet();
                }
            }
        } else if (currentPhase == null || currentPhase.isDisabled()
                || !currentPhase.isRateLimited() || currentPhase.isSerial()) {
            return;
        } else {
            // Add the specified number of procedures to the end of the queue.
            for (int i = 0; i < amount; ++i) {
                workQueue.add(new SubmittedProcedure(currentPhase.chooseTransaction()));
                queueSize.incrementAndGet();
            }
        }

        // Can't keep up with current rate? Remove the oldest transactions
        // (from the front of the queue).
        while (queueSize.get() > RATE_QUEUE_LIMIT) {
            if (workQueue.poll() != null) {
                queueSize.decrementAndGet();
            } else {
                break;
            }
        }

        // Wake up sleeping workers to deal with the new work.
        int waiting = workersWaiting.get();
        int numToWake = Math.min(amount, waiting);
        if (numToWake > 0) {
            synchronized (this) {
                for (int i = 0; i < numToWake; ++i) {
                    this.notify();
                }
            }
        }
    }

    public boolean getScriptPhaseComplete() {
        assert (traceReader != null);
        return traceReader.getPhaseComplete() && queueSize.get() == 0 && workersWorking.get() == 0;
    }

    public void signalDone() {
        int current = this.benchmarkState.signalDone();
        if (current == 0) {
            if (workersWaiting.get() > 0) {
                synchronized (this) {
                    this.notifyAll();
                }
            }
        }
    }
   
    /**
     * Called by ThreadPoolThreads when waiting for work.
     * Optimized with reduced synchronization scope.
     */
    public SubmittedProcedure fetchWork(int workerId) {
        // Handle serial phases - requires synchronization
        if (currentPhase != null && currentPhase.isSerial()) {
            synchronized (this) {
                workersWaiting.incrementAndGet();
                while (getGlobalState() == State.LATENCY_COMPLETE) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                workersWaiting.decrementAndGet();

                if (getGlobalState() == State.EXIT || getGlobalState() == State.DONE)
                    return null;

                workersWorking.incrementAndGet();
                return new SubmittedProcedure(currentPhase.chooseTransaction(getGlobalState() == State.COLD_QUERY, workerId));
            }
        }

        // Unlimited-rate phases don't use the work queue - lock-free path
        if (currentPhase != null && traceReader == null && !currentPhase.isRateLimited()) {
            workersWorking.incrementAndGet();
            return new SubmittedProcedure(currentPhase.chooseTransaction(getGlobalState() == State.COLD_QUERY, workerId));
        }

        // Rate-limited phases use the work queue
        // Try to get work without blocking first (fast path)
        SubmittedProcedure work = workQueue.poll();
        if (work != null) {
            queueSize.decrementAndGet();
            workersWorking.incrementAndGet();
            return work;
        }

        // Slow path: need to wait for work
        synchronized (this) {
            workersWaiting.incrementAndGet();
            try {
                while ((work = workQueue.poll()) == null) {
                    State state = this.benchmarkState.getState();
                    if (state == State.EXIT || state == State.DONE) {
                        return null;
                    }
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                workersWaiting.decrementAndGet();
            }
        }

        queueSize.decrementAndGet();
        workersWorking.incrementAndGet();

        // For scripted runs in warmup, return without removing
        if (traceReader != null && this.benchmarkState.getState() == State.WARMUP) {
            // Put it back and return a peek
            workQueue.add(work);
            queueSize.incrementAndGet();
        }

        return work;
    }

    public void finishedWork() {
        int prev = workersWorking.decrementAndGet();
        assert prev >= 0 : "workersWorking went negative!";
    }
   
   public Phase getNextPhase() {
       if (phaseIterator.hasNext())
           return phaseIterator.next();
       return null;
   }
   
   public Phase getCurrentPhase() {
       synchronized (benchmarkState){
           return currentPhase;
       }
   }
   
    /**
     * Called by workers to ask if they should stay awake in this phase
     */
    public void stayAwake() {
        // Fast path: no one needs to sleep
        if (workerNeedSleep.get() <= 0) {
            return;
        }

        synchronized (this) {
            while (workerNeedSleep.get() > 0) {
                workerNeedSleep.decrementAndGet();
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
   
    public void switchToNextPhase() {
        synchronized (this) {
            this.currentPhase = this.getNextPhase();

            // Clear the work from the previous phase.
            workQueue.clear();
            queueSize.set(0);

            // Determine how many workers need to sleep, then make sure they do.
            if (this.currentPhase == null) {
                // Benchmark is over---wake everyone up so they can terminate
                workerNeedSleep.set(0);
            } else {
                this.currentPhase.resetSerial();
                if (this.currentPhase.isDisabled()) {
                    // Phase disabled---everyone should sleep
                    workerNeedSleep.set(this.num_terminals);
                } else {
                    // Phase running---activate the appropriate # of terminals
                    workerNeedSleep.set(this.num_terminals - this.currentPhase.getActiveTerminals());
                }

                if (traceReader != null) {
                    traceReader.changePhase(this.currentPhase.id, System.nanoTime());
                }
            }

            this.notifyAll();
        }
    }
   
   /**
    * Delegates pre-start blocking to the global state handler
    */
   
   public void blockForStart() {
       benchmarkState.blockForStart();

        // For scripted runs, the first one out the gate should tell the
        // benchmark to skip the warmup phase.
        if (traceReader != null) {
            synchronized(benchmarkState) {
                if (benchmarkState.getState() == State.WARMUP)
                    benchmarkState.startMeasure();
            }
        }
   }
   
   /**
    * Delegates a global state query to the benchmark state handler
    * 
    * @return global state
    */
   public State getGlobalState() {
       return benchmarkState.getState();
   }
   
    public void signalLatencyComplete() {
        assert currentPhase.isSerial();
        benchmarkState.signalLatencyComplete();
    }

    public void startColdQuery() {
        assert currentPhase.isSerial();
        benchmarkState.startColdQuery();
    }

    public void startHotQuery() {
        assert currentPhase.isSerial();
        benchmarkState.startHotQuery();
    }

   public long getTestStartNs() {
       return benchmarkState.getTestStartNs();
   }
   
}
