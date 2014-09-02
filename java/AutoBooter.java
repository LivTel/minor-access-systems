import java.io.*;

public class AutoBooter {

    Process launcherProcess;
    Runtime runtime;

    public static void main(String args[]) {
	new AutoBooter();
    }

    public AutoBooter() {
	launcherProcess = null; 
	
	runtime = Runtime.getRuntime();
	int status = 0;
	for (int i = 0; i < 2000; i++) {
	    System.out.println("Run: "+i+" AB heap total: "+runtime.totalMemory()+" bytes.");
	    System.out.println("Run: "+i+" AB heap free:  "+runtime.freeMemory()+" bytes.");
	    try {
		
		String[] prog = new String[3];
		prog[0] = new String("/usr/java1.2/bin/java");
		prog[1] = new String("Echo_ServerTest");
		prog[2] = new String("NETWORK-TEST");
		System.err.println("Starting Autobooter: Command ["+prog[0]+"/"+prog[1]+"/"+prog[2]);
		launcherProcess = runtime.exec(prog);
		//System.out.println("substart AB tmem: "+runtime.totalMemory());
		// System.out.println("substart AB free: "+runtime.freeMemory());
		
	    } catch (IOException ie) {
		System.out.println("Error Building process: "+ie);
		return;
	    }
	    try {
		//System.out.println("ready wait AB tmem: "+runtime.totalMemory());
		//System.out.println("ready wait AB free: "+runtime.freeMemory());
		new Watcher().start();
		status = launcherProcess.waitFor();
	    } catch (InterruptedException ine) {
		System.out.println("Interrupted Waiting for Process: "+launcherProcess);
	    }
	    //System.out.println("subdone AB tmem: "+runtime.totalMemory());
	    //System.out.println("subdone AB free: "+runtime.freeMemory());
	
	    System.out.println("Process exited with status: "+status);
	    //if (status == 0) break;
	    if (runtime.freeMemory() < 5000000L) {
		System.out.println("Free Memory Low .. "+runtime.freeMemory()+" bytes .. running GC");
		runtime.gc();
		//System.out.println("after GC AB tmem: "+runtime.totalMemory());
		System.out.println("After GC AB free: "+runtime.freeMemory());
	    }
	    try {Thread.sleep(10000L);} catch (InterruptedException ine){}
	    System.out.println("Attempting to restart Echo Server");
	}
    }
	
    private class Watcher extends Thread {
	
	public void run() {
	    try {
		InputStream in = launcherProcess.getErrorStream();
		int c = 0;
		while ((c = in.read()) != -1) {
		    System.err.print((char)c);
		}
	    } catch (IOException ie) {
		System.out.println("Lost IO from Echo Server: ");
		return;
	    }
	}
	    
    }
    
}


