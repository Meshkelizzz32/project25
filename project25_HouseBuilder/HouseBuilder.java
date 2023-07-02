package project30;

import java.util.concurrent.*;
import java.util.*;


class House {
  private final int id;
  private boolean
    foundament = false, walls = false, ceiling = false,roof=false;
  public House(int idn)  { id = idn; }
  // Empty Car object:
  public House()  { id = -1; }
  public synchronized int getId() { return id; }
  public synchronized void buildFoundament() { foundament = true; }
  public synchronized void buildWalls() {
    walls = true;
  }

  public synchronized void buildRoof() { roof = true; }
  public synchronized String toString() {
    return "House " + id + " [" + " Foundament: " + foundament
      + " Walls: " + walls
      + " Roof: " +roof + " ]";
  }
}

class HouseQueue extends LinkedBlockingQueue<House> {}

class ChassisBuilder implements Runnable {
  private HouseQueue houseQueue;
  private int counter = 0;
  public ChassisBuilder(HouseQueue hq) { houseQueue = hq; }
  public void run() {
    try {
      while(!Thread.interrupted()) {
        TimeUnit.MILLISECONDS.sleep(500);
        // Make chassis:
        House h = new House(counter++);
        System.out.println("ChassisBuilder created " + h);
        // Insert into queue
        houseQueue.put(h);
      }
    } catch(InterruptedException e) {
    	System.out.println("Interrupted: ChassisBuilder");
    }
    System.out.println("ChassisBuilder off");
  }
}

class Assembler implements Runnable {
  private HouseQueue chassisQueue, finishingQueue;
  private House house;
  private CyclicBarrier barrier = new CyclicBarrier(4);
  private ErectorPool erectorPool;
  public Assembler(HouseQueue hq, HouseQueue fq, ErectorPool ep){
    chassisQueue = hq;
    finishingQueue = fq;
    erectorPool = ep;
  }
  public House house() { return house; }
  public CyclicBarrier barrier() { return barrier; }
  public void run() {
    try {
      while(!Thread.interrupted()) {
        // Blocks until chassis is available:
        house = chassisQueue.take();
        // Hire robots to perform work:
        erectorPool.hire(FoundamentErector.class, this);
        erectorPool.hire(WallsErector.class, this);
        erectorPool.hire(RoofErector.class, this);
        barrier.await(); // Until the robots finish
        // Put car into finishingQueue for further work
        finishingQueue.put(house);
      }
    } catch(InterruptedException e) {
    	System.out.println("Exiting Assembler via interrupt");
    } catch(BrokenBarrierException e) {
      // This one we want to know about
      throw new RuntimeException(e);
    }
    System.out.println("Assembler off");
  }
}

class Reporter implements Runnable {
  private HouseQueue houseQueue;
  public Reporter(HouseQueue cq) { houseQueue = cq; }
  public void run() {
    try {
      while(!Thread.interrupted()) {
    	  System.out.println(houseQueue.take());
      }
    } catch(InterruptedException e) {
    	System.out.println("Exiting Reporter via interrupt");
    }
    System.out.println("Reporter off");
  }
}

abstract class Erector implements Runnable {
  private ErectorPool pool;
  public Erector(ErectorPool ep) { pool = ep; }
  protected Assembler assembler;
  public Erector assignAssembler(Assembler assembler) {
    this.assembler = assembler;
    return this;
  }
  private boolean engage = false;
  public synchronized void engage() {
    engage = true;
    notifyAll();
  }
  // The part of run() that's different for each robot:
  abstract protected void performService();
  public void run() {
    try {
      powerDown(); // Wait until needed
      while(!Thread.interrupted()) {
        performService();
        assembler.barrier().await(); // Synchronize
        // We're done with that job...
        powerDown();
      }
    } catch(InterruptedException e) {
    	System.out.println("Exiting " + this + " via interrupt");
    } catch(BrokenBarrierException e) {
      // This one we want to know about
      throw new RuntimeException(e);
    }
    System.out.println(this + " off");
  }
  private synchronized void
  powerDown() throws InterruptedException {
    engage = false;
    assembler = null; // Disconnect from the Assembler
    // Put ourselves back in the available pool:
    pool.release(this);
    while(engage == false)  // Power down
      wait();
  }
  public String toString() { return getClass().getName(); }
}

class FoundamentErector extends Erector {
  public FoundamentErector(ErectorPool pool) { super(pool); }
  protected void performService() {
	  System.out.println(this + " build foundament");
    assembler.house().buildFoundament();
  }
}

class WallsErector extends Erector {
  public WallsErector(ErectorPool pool) { super(pool); }
  protected void performService() {
	  System.out.println(this + "build walls");
    assembler.house().buildWalls();
  }
}

class RoofErector extends Erector {
	  public RoofErector(ErectorPool pool) { super(pool); }
	  protected void performService() {
	    System.out.println(this + " build roof");
	    assembler.house().buildRoof();;
	  }
	}


class ErectorPool {
  // Quietly prevents identical entries:
  private Set<Erector> pool = new HashSet<Erector>();
  public synchronized void add(Erector e) {
    pool.add(e);
    notifyAll();
  }
  public synchronized void
  hire(Class<? extends Erector> erectorType, Assembler d)
  throws InterruptedException {
    for(Erector e : pool)
      if(e.getClass().equals(erectorType)) {
        pool.remove(e);
        e.assignAssembler(d);
        e.engage(); // Power it up to do the task
        return;
      }
    wait(); // None available
    hire(erectorType, d); // Try again, recursively
  }
  public synchronized void release(Erector e) { add(e); }
}

public class HouseBuilder {
  public static void main(String[] args) throws Exception {
    HouseQueue chassisQueue = new HouseQueue(),
             finishingQueue = new HouseQueue();
    ExecutorService exec = Executors.newCachedThreadPool();
    ErectorPool erectorPool = new ErectorPool();
   exec.execute(new FoundamentErector(erectorPool));
   exec.execute(new WallsErector(erectorPool));
   exec.execute(new RoofErector(erectorPool));
    exec.execute(new Assembler(
      chassisQueue, finishingQueue, erectorPool));
    exec.execute(new Reporter(finishingQueue));
    // Start everything running by producing chassis:
    exec.execute(new ChassisBuilder(chassisQueue));
    TimeUnit.SECONDS.sleep(7);
    exec.shutdownNow();
  }
}