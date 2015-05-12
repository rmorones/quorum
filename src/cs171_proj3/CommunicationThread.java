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
                if(input == null) {
                    continue;
                }
                if(input.contains("DONE")) {
                    ++expired;
                    if(expired == site.getNumberOfSites()) {
                        mysocket.close();
                        break;
                    }
                }

                int firstspace = input.indexOf(" ");
                int secondspace = input.indexOf(" ", firstspace + 1);
                String comp = input.substring(0, secondspace);
                switch(comp) {
                    case "request reply": {
                        //request reply read/append id
                        int thirdspace = input.indexOf(" ", secondspace + 1);
                        Integer id = Integer.parseInt(input.substring(thirdspace + 1));
                        if(input.contains("read")) {
                            if(activeLocks.isEmpty() || containsReadOnly(activeLocks)) {
                                //send grant read id
                                System.out.println(site.getSiteId() + " granting read to " + id);
                                Socket siteSocket;
                                if(id != site.getSiteId())
                                    activeLocks.add(input.substring(secondspace + 1));
                                siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                                PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                                outSite.write("grant read " + id);
                                outSite.flush();
                                siteSocket.close();
                            }
                            else {
                                requestedLocks.add(input.substring(secondspace + 1));
                            }
                        }
                        else if(activeLocks.isEmpty()) {
                            //send grant append id
                            System.out.println(site.getSiteId() + " granting append to " + id);
                            Socket siteSocket;
                            if(id != site.getSiteId())
                                activeLocks.add(input.substring(secondspace + 1));
                            siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                            PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                            outSite.write("grant append " + id);
                            outSite.flush();
                            siteSocket.close();
                        }
                        else {
                            requestedLocks.add(input.substring(secondspace + 1));
                        }
                        break;
                    }
                    case "release reply": {
                        // release reply append/read id
                        String str = requestedLocks.poll();
                        if(str == null) {
                            System.out.println("error requested locks is empty");
                            return;
                        }
                        int index = activeLocks.indexOf(input.substring(secondspace + 1));
                        if(index >= 0) {
                            System.out.println("released " + activeLocks.remove(index));
                        }
                        //check if there are more requests in queue
                        str = requestedLocks.peek();
                        if(str != null) {
                            Integer id = Integer.parseInt(str.substring(str.indexOf(" ") + 1));
                            Socket siteSocket;
                            siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                            PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                            if(str.contains("append")) {
                                if(activeLocks.isEmpty()) {
                                    outSite.write("grant append " + id);
                                    outSite.flush();
                                }
                            }
                            else {
                                if(activeLocks.isEmpty() || containsReadOnly(activeLocks)) {
                                    outSite.write("grant read " + id);
                                    outSite.flush();
                                }
                            }
                            siteSocket.close();
                        }
                        break;
                    }
                    case "grant read": {
                        //grant read id
                        Integer id = Integer.parseInt(input.substring(secondspace + 1));
                        if(id == site.getSiteId()) {
                            ++received;
                        }
                        else {
                            System.out.println("received read grant with different id:: " + site.getSiteId() + " ==> " + id.toString());
                        }
                        if(received == site.getQSize()) {
                            System.out.println("Read lock obtained for site " + id.toString());
                            synchronized(site.getLock()) {
                                received = 0;
                                site.getLock().notify();
                            }
                        }
                        break;
                    }
                    case "grant append": {
                        //grant append id
                        Integer id = Integer.parseInt(input.substring(secondspace + 1));
                        if(id == site.getSiteId()) {
                            ++received;
                        }
                        else {
                            System.out.println("received append grant with different id:: " + site.getSiteId() + " ==> " + id.toString());
                        }
                        if(received == site.getQSize()) {
                            System.out.println("Append lock obtained for site " + id.toString());
                            synchronized(site.getLock()) {
                                received = 0;
                                site.getLock().notify();
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

    private boolean containsReadOnly(List<String> activeLocks) {
        for(String string : activeLocks) {
            if(string.contains("append")) {
                return false;
            }
        }
        return true;
    }
}
