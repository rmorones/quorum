package cs171_proj3;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
//import java.util.Random;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 * @author Chris Kim <chriskim06@gmail.com>
 */
public class Site extends Thread {
    private static final int[][] SET_QUORUMS = {
        { 0, 1, 2 }, { 1, 4, 3 }, { 2, 4, 0 }, { 3, 0, 1 }, { 4, 3, 2 }
    };
    
    private final int[] serverPorts;
    private final String serverHostname;
    private final CommunicationThread server;
    private final Object lock = new Object();
    private final int siteId;
    private final int numberOfSites;
    private final int qSize = 3;
    private int[] myQuorum = new int[qSize];
    //for testing purposes
    private final String infile;
    
    public Site(int port, String serverHostname, int siteId, int numberOfSites, String infile) {
        this.serverPorts = new int[numberOfSites];
        for (int i = 0; i < numberOfSites; i++) {
            serverPorts[i] = 9990 + i;
        }
        this.serverHostname = serverHostname;
        this.siteId = siteId;
        this.numberOfSites = numberOfSites;
        //for testing
        this.infile = infile;
        this.server = new CommunicationThread(port, this);
        myQuorum = SET_QUORUMS[siteId];
    }

    public int getQSize() {
        return qSize;
    }
    
    public int[] getServerPorts() {
        return serverPorts;
    }
    
    public Object getLock() {
        return lock;
    }

    public int getSiteId() {
        return siteId;
    }

    public int getNumberOfSites() {
        return numberOfSites;
    }
    
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() {
        server.start();
        try {
            BufferedReader file;
            file = new BufferedReader(new InputStreamReader(new FileInputStream(infile)));
            String line;
            while((line = file.readLine()) != null) {
                if (line.equals("Read")) {
                    read();
                } else if (line.startsWith("Append")) {
                    String msg = line.substring(line.indexOf(" ") + 1);
                    if (msg.length() > 140) {
                        msg = msg.substring(0, 140);
                    }
                    append("Append " + msg);
                }
            } // while(line != null)
            file.close();
            System.out.println("Sending done from " + siteId);
            //send end to server
            for(int i = 0; i < serverPorts.length; i++) {
                Socket mysocket;
                mysocket = new Socket(serverHostname, serverPorts[i]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream(), true);
                out.write("DONE    ");
                out.flush();
                mysocket.close();
            }
            Socket mysocket;
            mysocket = new Socket(serverHostname, 9989);
            ObjectOutputStream out;
            out = new ObjectOutputStream(mysocket.getOutputStream());
            out.writeObject("DONE  ");
            out.flush();
            mysocket.close();
            server.join();
        } catch(IOException | InterruptedException ex) {
            ex.printStackTrace();
        }
    } // run()
    
    @SuppressWarnings("CallToPrintStackTrace")
    private void read() {
        Socket mysocket, sitesocket;
        try {
            for (int i = 0; i < qSize; i++) {
                mysocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream());
                out.write("request read " + siteId);
                out.flush();
                mysocket.close();
            }
            synchronized (lock) {
                //wait for granted read lock
                lock.wait();
            }
            //assuming port of log is 9989
            mysocket = new Socket(serverHostname, 9989);
            ObjectInputStream in;
            ObjectOutputStream out;
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject("Read ");
            out.flush();
            //Read in log and print to stdout
            List<String> log = (List<String>) in.readObject();
            System.out.println("Site " + siteId + ":");
            for (String item : log) {
                System.out.println(item);
            }
            System.out.println();
            mysocket.close();
            //Send release message to log thread
            mysocket = new Socket(serverHostname, 9989);
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject("Release ");
            out.flush();
            //receive ack from log
            String str = (String) in.readObject();
            if (!str.equals("acknowledged")) {
                System.out.println("error");
                return;
            }
            mysocket.close();
            //send release messages to sites
            for (int i = 0; i < qSize; i++) {
                sitesocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter siteout;
                siteout = new PrintWriter(sitesocket.getOutputStream());
                siteout.write("release read " + siteId);
                siteout.flush();
                sitesocket.close();
            }
        } catch (InterruptedException | IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }
    
    @SuppressWarnings("CallToPrintStackTrace")
    private void append(String line) {
        Socket mysocket, sitesocket;
        try {
            for (int i = 0; i < qSize; i++) {
                mysocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream());
                out.write("request append " + siteId);
                out.flush();
                mysocket.close();
            }
            synchronized (lock) {
                //wait for granted append lock
                lock.wait();
            }
            //assuming port of log is 9989
            mysocket = new Socket(serverHostname, 9989);
            ObjectOutputStream out;
            ObjectInputStream in;
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject(line);
            out.flush();
            mysocket.close();
            //send release message
            mysocket = new Socket(serverHostname, 9989);
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject("Release ");
            out.flush();
            //receive ack from log
            String str = (String) in.readObject();
            if (!str.equals("acknowledged")) {
                System.out.println("error");
                return;
            }
            mysocket.close();
            //send release messages to sites
            for (int i = 0; i < qSize; i++) {
                sitesocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter siteout;
                siteout = new PrintWriter(sitesocket.getOutputStream());
//                System.out.println("sending release to " + myQuorum[i]);
                siteout.write("release append " + siteId);
                siteout.flush();
                sitesocket.close();
            }
        } catch (InterruptedException | IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }
    
}// end Class Site