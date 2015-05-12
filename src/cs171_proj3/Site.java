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
import java.util.Random;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 * @author Chris Kim <chriskim06@gmail.com>
 */
public class Site extends Thread {
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
    
    public Site(int port, String hostname, int id, int numberOfSites, String file) {
        this.serverPorts = new int[numberOfSites];
        for(int i = 0; i < numberOfSites; ++i) {
            serverPorts[i] = 9990 + i;
        }
        this.serverHostname = hostname;
        this.siteId = id;
        this.numberOfSites = numberOfSites;
        for(int i = 0; i < qSize; ++i) {
            this.myQuorum[i] = 0;
        }
        //for testing
        this.infile = file;
        
        this.server = new CommunicationThread(port, this);
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
            String line = file.readLine();
            while(line != null) {
                if(line.equals("Read")) {
                    read();
                }
                else if(line.startsWith("Append")) {
                    String msg = line.substring(line.indexOf(" ") + 1);
                    if (msg.length() > 140) {
                        msg = msg.substring(0, 140);
                    }
                    append("Append " + msg);
                }
//                else if(line.equals("exit")) {
//                    break;
//                } 
                line = file.readLine();
            } // while(line != null)
            file.close();
            System.out.println("Sending done from " + siteId);
            //send end to server
            Socket mysocket;
            mysocket = new Socket(serverHostname, serverPorts[siteId]);
            PrintWriter out;
            out = new PrintWriter(mysocket.getOutputStream());
            out.write("DONE");
            mysocket.close();
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    } // run()
    
    @SuppressWarnings("CallToPrintStackTrace")
    private void read() {
        Socket mysocket;
        myQuorum = randQuorum();
        for(int i = 0; i < qSize; ++i) {
            try {
                mysocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream());
                out.write("request reply read " + siteId);
                out.flush();
                mysocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            synchronized(lock) {
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

            List<String> log = (List<String>)in.readObject();
            mysocket.close();

            for(String item : log) {
                if(log.get(log.size() - 1).equals(item)) {
                    System.out.print(item + '\n');
                }
                else {
                    System.out.print(item + ',');
                }
            }
        } catch (InterruptedException | IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        //send release message to log
        try {
            mysocket = new Socket(serverHostname, 9989);
            ObjectOutputStream out;
            ObjectInputStream in;
            in = new ObjectInputStream(mysocket.getInputStream());
            out = new ObjectOutputStream(mysocket.getOutputStream());
            out.writeObject("Release ");
            out.flush();
            //receive ack from log
            String str = (String)in.readObject();
            if(!str.equals("acknowledged")) {
                System.out.println("error");
                return;
            }
            mysocket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        
        //send release messages to sites
        for(int i = 0; i < qSize; ++i) {
            try {
                mysocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream());
                out.write("release reply read " + siteId);
                out.flush();
                mysocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    @SuppressWarnings("CallToPrintStackTrace")
    private void append(String line) {
        Socket mysocket;
        myQuorum = randQuorum();
        for(int i = 0; i < qSize; ++i) {
            try {
                mysocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream());
                out.write("request reply append " + siteId);
                out.flush();
                mysocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        try {
            synchronized(lock) {
                //wait for granted append lock
                lock.wait();
                System.out.println(siteId + " unblocked");
            }
            //assuming port of log is 9989
            mysocket = new Socket(serverHostname, 9989);
            ObjectOutputStream out;
            ObjectInputStream in;
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject(line);
            out.flush();
            //receive ack from log
            String str = (String)in.readObject();
            if(!str.equals("acknowledged")) {
                System.out.println("error");
                return;
            }
            mysocket.close();
        } catch (InterruptedException | IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        System.out.println("ok 1");
        //send release message to log
        try {
            mysocket = new Socket(serverHostname, 9989);
            ObjectOutputStream out;
            ObjectInputStream in;
            System.out.println("ok 2");
            in = new ObjectInputStream(mysocket.getInputStream());
            System.out.println("ok 3");
            out = new ObjectOutputStream(mysocket.getOutputStream());
            out.writeObject("Release ");
            out.flush();
            //receive ack from log
            String str = (String)in.readObject();
            if(!str.equals("acknowledged")) {
                System.out.println("error");
                return;
            }
            mysocket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("ok");
        //send release messages to sites
        for(int i = 0; i < qSize; ++i) {
            try {
                mysocket = new Socket(serverHostname, serverPorts[myQuorum[i]]);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream());
                System.out.println("sending release to " + myQuorum[i]);
                out.write("release reply append " + siteId);
                out.flush();
                mysocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private int[] randQuorum() {
//        Random rand = new Random();
//        int[] retval = new int[qSize];
//        
//        retval[0] = siteId;
//        for(int i = 1; i < qSize; ++i) {
//            retval[i] = rand.nextInt(5);
//            while(retval[i] == siteId || retval[i] == retval[i-1])
//                retval[i] = rand.nextInt(5);
//        }
        int[] retval = null;
//        switch(siteId) {
//            case 0:
//                retval = new int[] { siteId, 3, 2};
//                break;
//            case 1:
//                retval = new int[] { siteId, 3, 2};
//                break;
//            case 2:
//                retval = new int[] { siteId, 1, 4};
//                break;
//            case 3:
//                retval = new int[] { siteId, 0, 2};
//                break;
//            case 4:
//                retval = new int[] { siteId, 1, 2};
//                break;
//        }
        switch(siteId) {
            case 0:
                retval = new int[] { siteId, 3, 2};
                break;
            case 1:
                retval = new int[] { siteId, 4, 2};
                break;
            case 2:
                retval = new int[] { siteId, 1, 4};
                break;
            case 3:
                retval = new int[] { siteId, 0, 3};
                break;
            case 4:
                retval = new int[] { siteId, 1, 2};
                break;
        }
        return retval;
    }
    
}// end Class Site