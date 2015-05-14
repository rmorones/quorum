package cs171_proj3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 * @author Chris Kim <chriskim06@gmail.com>
 */
public class CommunicationThread extends Thread {
    private final int port;
    private final Site site;
    private final Queue<String> requestedLocks = new LinkedList<>();
    private final List<String> activeLocks = new ArrayList<>();
    private int received;
    private int expired;
    private static boolean gaveWriteLock = false;
    private final Queue<String> deadLocks = new LinkedList<>();
    
    public CommunicationThread(int port, Site site) {
        this.port = port;
        this.site = site;
        this.received = 0;
        this.expired = 0;
    }
    
    @Override 
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() { 
        ServerSocket serverSocket;
        Socket mysocket;
        BufferedReader in;
        
        try {
          serverSocket = new ServerSocket(port);
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
         // Infinite loop, processes a single client connection at a time
        while (true) {
            try {
                // Wait for a client to connect (blocking)
                mysocket = serverSocket.accept();
                in = new BufferedReader(
                        new InputStreamReader(mysocket.getInputStream()));            
                String input = in.readLine();
                if (input == null) {
                    continue;
                }
                if (input.contains("DONE")) {
                    ++expired;
                    if (expired == site.getNumberOfSites()) {
                        mysocket.close();
                        break;
                    }
                }

                int firstspace = input.indexOf(" ");
                int secondspace = input.indexOf(" ", firstspace + 1);
                String comp = input.substring(0, secondspace);
                switch (comp) {
                    case "request reply": {
                        //request reply read/append id
                        int thirdspace = input.indexOf(" ", secondspace + 1);
                        int id = Integer.parseInt(input.substring(thirdspace + 1));
                        String to_add = input.substring(secondspace + 1);
                        Socket siteSocket;
                        siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                        PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                        if (input.contains("read") && !gaveWriteLock) { // grant a read request if no write locks
                            //send grant read id
                            System.out.println("Site " + site.getSiteId() + ": granting read to site " + id);
                            if (id != site.getSiteId()) {
                                activeLocks.add(to_add);
                            }
                            outSite.write("grant read " + id);
                            outSite.flush();
                        } else if (input.contains("append") && activeLocks.isEmpty()) {
                            //send grant append id
                            System.out.println("Site " + site.getSiteId() + ": granting append to site " + id);
                            if (id != site.getSiteId()) {
                                activeLocks.add(to_add);
                            }
                            outSite.write("grant append " + id);
                            outSite.flush();
                            gaveWriteLock = true;
                        } else {
                            requestedLocks.add(to_add);
                        }
                        siteSocket.close();
                        break;
                    }
                    case "release reply": {
                        // release reply append/read id
                        String cmd = input.substring(secondspace + 1, input.indexOf(" ", secondspace + 1));
                        if (cmd.equals("append")) {
                            System.out.println("write lock is false");
                            gaveWriteLock = false;
                        }
                        int index = activeLocks.indexOf(input.substring(secondspace + 1));
                        if (index >= 0) {
                            System.out.println("released " + activeLocks.remove(index));
                            if(!activeLocks.isEmpty()) {
                                for(String locks : activeLocks) {
                                    if(locks.contains("append")) {
                                        gaveWriteLock = true;
                                    }
                                }
                            }
                        }
                        String str = deadLocks.peek();
                        if(str != null) { //grants already receieved so just notify site
                            if (str.contains("append") && activeLocks.isEmpty()) {
                                gaveWriteLock = true;
                                str = deadLocks.remove();
                                activeLocks.add(str);
                                synchronized (site.getLock()) {
                                    received = 0;
                                    site.getLock().notify();
                                }
                            } else if (str.contains("read") && !gaveWriteLock) {
                                str = deadLocks.remove();
                                activeLocks.add(str);
                                synchronized (site.getLock()) {
                                    received = 0;
                                    site.getLock().notify();
                                }
                            }
                        }
                        else {
                            //check if there are more requests in queue
                            str = requestedLocks.peek();
                            if (str != null) {
                                int id = Integer.parseInt(str.substring(str.indexOf(" ") + 1));
                                Socket siteSocket;
                                siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                                PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                                if (str.contains("append") && activeLocks.isEmpty()) {
                                    str = requestedLocks.remove();
                                    if(id != site.getSiteId()) {
                                        activeLocks.add(str);
                                    } else {
                                        str = requestedLocks.peek(); //grant permission to next event, else deadlock
                                        if(str != null) {
                                            str = requestedLocks.remove();
                                            activeLocks.add(str);
                                            int id_2 = Integer.parseInt(str.substring(str.indexOf(" ") + 1));
                                            Socket site_2;
                                            site_2 = new Socket("localhost", site.getServerPorts()[id_2]);
                                            PrintWriter outSite_2 = new PrintWriter(site_2.getOutputStream(), true);
                                            System.out.println("Site " + site.getSiteId() + ": granting " + str);
                                            outSite_2.write("grant " + str);
                                            outSite_2.flush();
                                            site_2.close();
                                        }
                                    }
                                    gaveWriteLock = true;
                                    System.out.println("Site " + site.getSiteId() + ": granting append to site " + id);
                                    outSite.write("grant append " + id);
                                    outSite.flush();
                                } else if (str.contains("read") && !gaveWriteLock) {
                                    str = requestedLocks.remove();
                                    if(id != site.getSiteId()) {
                                        activeLocks.add(str);
                                    }
                                    else {
                                        str = requestedLocks.peek(); //grant permission to next event, else deadlock
                                        if(str != null) {
                                            str = requestedLocks.remove();
                                            activeLocks.add(str);
                                            int id_2 = Integer.parseInt(str.substring(str.indexOf(" ") + 1));
                                            Socket site_2;
                                            site_2 = new Socket("localhost", site.getServerPorts()[id_2]);
                                            PrintWriter outSite_2 = new PrintWriter(site_2.getOutputStream(), true);
                                            System.out.println("Site " + site.getSiteId() + ": granting " + str);
                                            outSite_2.write("grant " + str);
                                            outSite_2.flush();
                                            site_2.close();
                                        }
                                    }
                                    System.out.println("Site " + site.getSiteId() + ": granting read to site " + id);
                                    outSite.write("grant read " + id);
                                    outSite.flush();
                                }
                                siteSocket.close();
                            }
                        }
                        break;
                    }
                    case "grant read": {
                        //grant read id
                        int id = Integer.parseInt(input.substring(secondspace + 1));
                        String to_add = input.substring(firstspace + 1);
                        if (id == site.getSiteId()) {
                            ++received;
                            System.out.println(to_add + " received = " + received);
                        } else {
                            System.out.println("received read grant with different id:: " + site.getSiteId() + " ==> " + id);
                        }
                        if (received == site.getQSize() && !gaveWriteLock) {
                            activeLocks.add(to_add);
                            System.out.println("Read lock obtained for site " + id);
                            synchronized (site.getLock()) {
                                received = 0;
                                site.getLock().notify();
                            }
                        }
                        else if (received == site.getQSize()) {
                            boolean process = true;
                            for(int i = 0; i < activeLocks.size() && process ; i++) {
                                String granted = activeLocks.get(i);
                                id = Integer.parseInt(granted.substring(granted.indexOf(" ") + 1));
                                if(site.getSiteId() > id) { //smaller site id gets to process first
                                    process = false;
                                }
                            }
                            if(process) { //activeLocks should only contain one append lock at a time
                                activeLocks.add(to_add);
                                System.out.println("Read lock obtained for site " + id);
                                synchronized (site.getLock()) {
                                    received = 0;
                                    site.getLock().notify();
                                }
                            }
                            else {
                                System.out.println(to_add + " added to deadlocks");
                                deadLocks.add(to_add); //should contain only commands from this site
                            }
                        }
                        break;
                    }
                    case "grant append": {
                        //grant append id
                        int id = Integer.parseInt(input.substring(secondspace + 1));
                        String to_add = input.substring(firstspace + 1);
                        if (id == site.getSiteId()) {
                            ++received;
                            System.out.println(to_add + " received = " + received);
                        } else {
                            System.out.println("received append grant with different id:: " + site.getSiteId() + " ==> " + id);
                        }
                        if (received == site.getQSize() && activeLocks.isEmpty()) {
                            activeLocks.add(to_add); //add yourslef to activeLocks list
                            gaveWriteLock = true;
                            System.out.println("Append lock obtained for site " + id);
                            synchronized (site.getLock()) {
                                received = 0;
                                site.getLock().notify();
                            }
                        } else if (received == site.getQSize()) {
                            boolean process = true;
                            for(int i = 0; i < activeLocks.size() && process ; i++) {
                                String granted = activeLocks.get(i);
                                id = Integer.parseInt(granted.substring(granted.indexOf(" ") + 1));
                                if(site.getSiteId() > id) { //smaller site id gets to process first
                                    process = false;
                                }
                            }
                            if (process) { //activeLocks should only contain one append lock at a time
                                activeLocks.add(to_add);
                                gaveWriteLock = true;
                                System.out.println("Append lock obtained for site " + site.getSiteId());
                                synchronized (site.getLock()) {
                                    received = 0;
                                    site.getLock().notify();
                                }
                            }
                            else {
                                System.out.println(to_add + " added to deadlocks");
                                deadLocks.add(to_add); //should contain only commands from this site
                            }
                        }
                        break;
                    }
                }
                mysocket.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
        }
        
        try {
            serverSocket.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        
    }//run()
    
}
