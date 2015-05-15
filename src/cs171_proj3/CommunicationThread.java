package cs171_proj3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 * @author Chris Kim <chriskim06@gmail.com>
 */
public class CommunicationThread extends Thread {
    private final int port;
    private final Site site;
    private final List<String> requestedLocks = new ArrayList<>();
    private int received;
    private int expired;
    private String privateIP;
    private String locked = "";
    private boolean yielded = false;
    private boolean failed = false;
    
    public CommunicationThread(int port, String privateIP, Site site) {
        this.port = port;
        this.site = site;
        this.received = 0;
        this.expired = 0;
        this.privateIP = privateIP;
    }
    
    @Override 
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() { 
        ServerSocket serverSocket;
        Socket mysocket;
        BufferedReader in;
        
        try {
          serverSocket = new ServerSocket();
          serverSocket.bind(new InetSocketAddress(privateIP, port));
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
                    expired++;
                    if (expired == 5) {
                        mysocket.close();
                        break;
                    }
                }

                int firstspace = input.indexOf(" ");
                int secondspace = input.indexOf(" ", firstspace + 1);
                String lockMessage = input.substring(firstspace + 1);
                String comp = input.substring(0, firstspace);
                switch (comp) {
                    case "request": {
                        //request read/append id
                        //id is the site requesting read/append
                        int id = Integer.parseInt(input.substring(secondspace + 1));
                        Socket siteSocket;
                        siteSocket = new Socket(site.getIP(id), site.getServerPort());
                        PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                        if (input.contains("read") && !locked.contains("append")) { // grant a read request if no write locks
                            //send grant read id
                            locked = lockMessage;
                            outSite.write("grant read " + id);
                            outSite.flush();
                        } else if (input.contains("append") && locked.equals("")) {
                            //send grant append id
                            locked = lockMessage;
                            outSite.write("grant append " + id);
                            outSite.flush();
                        } else if (!locked.equals("")) {
                            if (hasHigherPriority(lockMessage)) {
                                int lockID = Integer.parseInt(locked.substring(locked.indexOf(" ") + 1));
                                Socket inquireSocket;
                                inquireSocket = new Socket(site.getIP(lockID), site.getServerPort());
                                PrintWriter outInquire = new PrintWriter(inquireSocket.getOutputStream(), true);
                                outInquire.write("inquire " + id + " " + site.getSiteId());
                                outInquire.flush();
                                inquireSocket.close();
                            } else {
                                outSite.write("failed message " + site.getSiteId());
                                outSite.flush();
                            }
                            editRequestQueue(lockMessage, 1);
                        }
                        siteSocket.close();
                        break;
                    }
                    case "release": {
                        // release append/read id
                        locked = "";
                        editRequestQueue(lockMessage, 0);
                        //check if there are more requests in queue
                        if (!requestedLocks.isEmpty()) {
                            String str = requestedLocks.get(0);
                            int id = Integer.parseInt(str.substring(str.indexOf(" ") + 1));
                            Socket siteSocket;
                            siteSocket = new Socket(site.getIP(id), site.getServerPort());
                            PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                            if (str.contains("append") && locked.equals("")) {
                                locked = str;
                                outSite.write("grant append " + id);
                                outSite.flush();
                            } else if (str.contains("read") && !locked.contains("append")) {
                                locked = str;
                                outSite.write("grant read " + id);
                                outSite.flush();
                            }
                            siteSocket.close();
                        }
                        break;
                    }
                    case "grant": {
                        //grant read id test
                        failed = false;
                        received++;
                        if (received == site.getQSize()) {
                            received = 0;
                            synchronized (site.getLock()) {
                                site.getLock().notify();
                            }
                        }
                        break;
                    }
                    case "inquire": {
                        //inquire checkID sentFromID
                        int checkID = Integer.parseInt(input.substring(firstspace + 1, secondspace));
                        int sentFromID = Integer.parseInt(input.substring(secondspace + 1));
                        if (failed || yielded) {
                            Socket inquireSocket;
                            inquireSocket = new Socket(site.getIP(sentFromID), site.getServerPort());
                            PrintWriter outInquire = new PrintWriter(inquireSocket.getOutputStream(), true);
                            outInquire.write("yield message " + site.getSiteId());
                            outInquire.flush();
                            inquireSocket.close();
                            yielded = true;
                        }
                    }
                    case "yield": {
                        yielded = false;
                        if (!requestedLocks.isEmpty()) {
                            locked = "";
                            String str = requestedLocks.get(0);
                            int id = Integer.parseInt(str.substring(str.indexOf(" ") + 1));
                            Socket siteSocket;
                            siteSocket = new Socket(site.getIP(id), site.getServerPort());
                            PrintWriter outSite = new PrintWriter(siteSocket.getOutputStream(), true);
                            if (str.contains("append") && locked.equals("")) {
                                locked = str;
                                outSite.write("grant append " + id);
                                outSite.flush();
                            } else if (str.contains("read") && !locked.contains("append")) {
                                locked = str;
                                outSite.write("grant read " + id);
                                outSite.flush();
                            }
                            siteSocket.close();
                        }
                    }
                    case "failed": {
                        failed = true;
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
        
    }
    
    /**
     * Check if a new request has a greater priority than the request that locked
     * this communication thread.
     */
    private boolean hasHigherPriority(String message) {
        int space = message.indexOf(" ");
        int requestID = Integer.parseInt(message.substring(space + 1));
        space = locked.indexOf(" ");
        int lockID = Integer.parseInt(locked.substring(space + 1));
        return (requestID < lockID);
    }
    
    /**
     * Function to add or remove an event into the queue and sort it
     * @param message the string to be inserted or deleted
     * @param flag    determines whether to use insert or delete
     */
    private void editRequestQueue(String message, int flag) {
        if (flag == 1 && !requestedLocks.contains(message)) {
            requestedLocks.add(message);
        } else {
            requestedLocks.remove(message);
        }
        Collections.sort(requestedLocks, new Comparator<String>() {
            
            @Override
            public int compare(String a, String b) {
                return Integer.signum(stringID(a) - stringID(b));
            }
            
            private int stringID(String input) {
                return Integer.parseInt(input.substring(input.indexOf(" ") + 1));
            }
            
        });
    }
    
    private static void main(String[] args) {
        //
    }
    
}
