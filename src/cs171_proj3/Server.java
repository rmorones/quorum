package cs171_proj3;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 */
public class Server extends Thread {
    private final int port;
    private int expired;
    private final Site site;
    
    public Server(int port, Site site) {
        this.port = port;
        this.site = site;
        this.expired = 0;
    }
    
    @Override 
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() { 
        ServerSocket serverSocket;
        Socket mysocket;
        try {
          serverSocket = new ServerSocket(port);
        } catch (IOException e) {
          e.printStackTrace();
          return;
        }
         // Infinite loop, processes a single client connection at a time
        while (true) {
            if(expired == 3) {
                break;
            }
            
            try {
                // Wait for a client to connect (blocking)
                System.out.println("waiting for connection");
                mysocket = serverSocket.accept();
                System.out.println("Got it");
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            
            // Establish input and output streams with the client
            ObjectInputStream in;
            try {
                in = new ObjectInputStream(mysocket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            System.out.println("Server: Established connection with a site");
            
            // Close connection
            try {
              mysocket.close();
            } catch (IOException e) {
              e.printStackTrace();
            }
        } //while(true)
        System.out.println("Closing server of site" + (site.getSiteId() + 1));
        // Close connection
        try {
          serverSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
    } // run()
}
