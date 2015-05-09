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
 * @author Chris Kim <chriskim@umail.ucsb.edu>
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
        PrintWriter out;
        
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
                
                if(input.contains("DONE")) {
                    ++expired;
                    if(expired == site.getNumberOfSites()) {
                        mysocket.close();
                        break;
                    }
                }
                
                if(!input.equals("")) {
                    int firstspace = input.indexOf(" ");
                    int secondspace = input.indexOf(input.substring(firstspace + 1));
                    String comp = input.substring(0, secondspace);
                    switch(comp) {
//                        case "request send":
//                            myQuorum = randQuorum();
//                            for(int i = 0; i < qSize; ++i) {
//                                if(myQuorum[i] == site.getSiteId()) { //check local 
//                                    if(input.contains("read")) {
//                                        requestedLocks.add("read " + site.getSiteId());
//                                        if(activeLocks.isEmpty()) {
//                                            ++received;
//                                        }
//                                        else if(containsReadOnly(activeLocks)){
//                                            ++received;
//                                        }
//                                    }
//                                    else {
//                                        requestedLocks.add("append " + site.getSiteId());
//                                        if(activeLocks.isEmpty()) {
//                                            ++received;
//                                        }
//                                    }                                    
//                                }
//                                else {
//                                    Socket siteSocket = new Socket("localhost", site.getServerPorts()[myQuorum[i]]);
//                                    PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
//                                    if(input.contains("read")) {
//                                        outSite.write("request reply read " + site.getSiteId());
//                                    }
//                                    else {
//                                        outSite.write("request reply append " + site.getSiteId());
//                                    }
//                                    siteSocket.close();
//                                }
//                            }
//                            
//                            break;
//                        case "release send":
//                            //todo: send release to corresponding quorum
//                            requestedLocks.p();
//                            break;
                        case "request reply": {
                            //request reply read/append id
                            int thirdspace = input.indexOf(" ", secondspace + 1);
                            Integer id = Integer.parseInt(input.substring(thirdspace + 1));
                            requestedLocks.add(input.substring(secondspace + 1));
                            if(input.contains("read")) {
                                if(activeLocks.isEmpty() || containsReadOnly(activeLocks)) {
                                    //send grant read id msg
                                    Socket siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                                    PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                                    outSite.write("grant read " + id);
                                    siteSocket.close();
                                }
                            }
                            else {
                                if(activeLocks.isEmpty()) {
                                    //send grant append id msg
                                    Socket siteSocket = new Socket("localhost", site.getServerPorts()[id]);
                                    PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                                    outSite.write("grant append " + id);
                                    siteSocket.close();
                                }
                                
                            }
                            
                            
                            break;
                        }
                        case "release reply": {
                            // release reply append/read id
                            break;
                        }
                        case "grant read": {
                            //grant read id
                            Integer id = Integer.parseInt(input.substring(secondspace + 1));
                            if(id == site.getSiteId()) {
                                ++received;
                            }
                            
                            if(received == site.getQSize()) {
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
                            
                            if(received == site.getQSize()) {
                                synchronized(site.getLock()) {
                                    received = 0;
                                    site.getLock().notify();
                                }
                            }
                            break;
                        }
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
//        for(String string : activeLocks) {
//            if(string.contains("append")) {
//                return false;
//            }
//        }
//        return true;
        return activeLocks.stream().noneMatch((string) -> (string.contains("append")));

    }
}
