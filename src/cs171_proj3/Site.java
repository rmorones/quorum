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
    
    private int portNum;
    private String[] publicIPList = new String[6];
    private String privateIP;
    private final CommunicationThread server;
    private final Object lock = new Object();
    private int siteId = 100;
    private final int qSize = 3;
    private int[] myQuorum = new int[qSize];
    
    public Site() {
        readConfig();
        this.server = new CommunicationThread(portNum, privateIP, this);
    }

    public int getQSize() {
        return qSize;
    }
    
    public int getServerPort() {
        return portNum;
    }
    
    public String getIP(int index) {
        return publicIPList[index];
    }
    
    public Object getLock() {
        return lock;
    }

    public int getSiteId() {
        return siteId;
    }
    
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() {
        server.start();
        randomQuorum();
        try {
            BufferedReader input;
            input = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while((line = input.readLine()) != null) {
                if (line.equals("Read")) {
                    read();
                } else if (line.startsWith("Append")) {
                    String msg = line.substring(line.indexOf(" ") + 1);
                    if (msg.length() > 140) {
                        msg = msg.substring(0, 140);
                    }
                    append("Append " + msg);
                } else {
                    System.out.println("Error");
                }
            } // while(line != null)
            input.close();
            //send end to server
            for(int i = 0; i < 5; i++) {
                Socket mysocket;
                mysocket = new Socket(publicIPList[i], portNum);
                PrintWriter out;
                out = new PrintWriter(mysocket.getOutputStream(), true);
                out.write("DONE    ");
                out.flush();
                mysocket.close();
            }
            Socket mysocket;
            mysocket = new Socket(publicIPList[5], portNum);
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
//        randomQuorum();
        Socket mysocket, sitesocket;
        try {
            for (int i = 0; i < qSize; i++) {
                mysocket = new Socket(publicIPList[i], portNum);
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
            //assuming port of log is portNum
            mysocket = new Socket(publicIPList[5], portNum);
            ObjectInputStream in;
            ObjectOutputStream out;
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject("Read ");
            out.flush();
            //Read in log and print to stdout
            List<String> log = (List<String>) in.readObject();
            for (String item : log) {
                System.out.println(item);
            }
            System.out.println();
            mysocket.close();
            //Send release message to log thread
            mysocket = new Socket(publicIPList[5], portNum);
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject("Release ");
            out.flush();
            //receive ack from log
            String str = (String) in.readObject();
            if (!str.equals("acknowledged")) {
                return;
            }
            mysocket.close();
            //send release messages to sites
            for (int i = 0; i < qSize; i++) {
                sitesocket = new Socket(publicIPList[i], portNum);
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
//        randomQuorum();
        Socket mysocket, sitesocket;
        try {
            for (int i = 0; i < qSize; i++) {
                mysocket = new Socket(publicIPList[i], portNum);
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
            //assuming port of log is portNum
            mysocket = new Socket(publicIPList[5], portNum);
            ObjectOutputStream out;
            ObjectInputStream in;
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject(line);
            out.flush();
            mysocket.close();
            //send release message
            mysocket = new Socket(publicIPList[5], portNum);
            out = new ObjectOutputStream(mysocket.getOutputStream());
            in = new ObjectInputStream(mysocket.getInputStream());
            out.writeObject("Release ");
            out.flush();
            //receive ack from log
            String str = (String) in.readObject();
            if (!str.equals("acknowledged")) {
                return;
            }
            mysocket.close();
            //send release messages to sites
            for (int i = 0; i < qSize; i++) {
                sitesocket = new Socket(publicIPList[i], portNum);
                PrintWriter siteout;
                siteout = new PrintWriter(sitesocket.getOutputStream());
                siteout.write("release append " + siteId);
                siteout.flush();
                sitesocket.close();
            }
        } catch (InterruptedException | IOException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
    }
    
    private void randomQuorum() {
        myQuorum[0] = siteId;
        Random random = new Random();
        int num;
        for (int i = 1; i < 3; i++) {
            myQuorum[i] = random.nextInt(5);
            while (myQuorum[i] == siteId || myQuorum[i] == myQuorum[i - 1]) {
                myQuorum[i] = random.nextInt(5);
            }
        }
    }
    
    private void readConfig() {
        BufferedReader file;
        try {
            file = new BufferedReader(new InputStreamReader(new FileInputStream("config.txt")));
            String line;
            int i = 0;
            while ((line = file.readLine()) != null) {
                int space = line.indexOf(" ");
                String ip = line.substring(0, space);
                portNum = Integer.parseInt(line.substring(space + 1));
                if (siteId == 100) {
                    siteId = Integer.parseInt(line);
                }
                if (i != 6) {
                    publicIPList[i] = ip;
                } else {
                    privateIP = ip;
                }
                i++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void main(String[] args) {
        Site site = new Site();
        site.start();
        try {
            site.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}// end Class Site