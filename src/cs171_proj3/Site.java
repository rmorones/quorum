package cs171_proj3;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 */
public class Site extends Thread {
    private final int[] serverPorts;
    private final String serverHostname;
    private final Server server;
    private final Object lock;
    private final int siteId;
    private final int numberOfSites;
    
    public Site(int port, String hostname, Object lock, int id, int numberOfSites) throws FileNotFoundException, UnsupportedEncodingException {
        this.serverPorts = new int[numberOfSites];
        for(int i = 0; i < numberOfSites; ++i) {
            //serverPorts[i] = 9991 + i;
            serverPorts[i] = port;
        }
        this.server = new Server(port, this);
        this.lock = lock;
        this.serverHostname = hostname;
        this.siteId = id;
        this.numberOfSites = numberOfSites;
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
            file = new BufferedReader(new InputStreamReader(System.in));
            
            while(true) {
                String line = file.readLine();
                if(line.equals("Read")) {
                    read();
                }
                else if(line.startsWith("Append")) {
                    String msg = line.substring(line.indexOf(' ') + 1);
                    if(msg.length() > 140) {
                        msg = msg.substring(0, 140);
                    }
                    System.out.println(msg);
                    append(msg);
                }
                else if(line.equals("exit")) {
                    break;
                }                
            } // end of while(true)
            file.close();
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    } // run()

    private void read() {
        askQuorum();
        synchronized(lock) {
            
        }
    }

    private void append(String msg) {
        
    }
    
    @SuppressWarnings("CallToPrintStackTrace")
    private void askQuorum() {
        
        Socket[] mysocket = new Socket[2];
        //should only ask 2 other sites for the lock
        for(int i = 0; i < 2; ++i) {
            try {
                mysocket[i] = new Socket(serverHostname, serverPorts[i]);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        
        for(int i = 0; i < 2; ++i) {
            // Establish input and output streams with the server
            PrintWriter out;
            try {
                out = new PrintWriter(mysocket[i].getOutputStream());
                out.write("read " + siteId);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        for(int i = 0; i < 2; ++i) {
            // Close TCP connection
            try {
              mysocket[i].close();
            } catch (IOException e) {
              e.printStackTrace();
            }
        }
    }
    
}// end Class Site