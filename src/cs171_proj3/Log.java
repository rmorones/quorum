package cs171_proj3;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 * @author Chris Kim <chriskim@umail.ucsb.edu>
 */
public class Log extends Thread {
    
    private static final String ACKNOWLEDGE = "acknowledged";
    private List<String> log = new ArrayList<>();
    private final int port;
    
    public Log(int port) {
        this.port = port;
    }
    
    @Override
    @SuppressWarnings("CallToPrintStackTrace")
    public void run() {
        ServerSocket serverSocket;
        Socket site;
        ObjectInputStream inputStream;
        ObjectOutputStream outputStream;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        while (true) {
            try {
                site = serverSocket.accept();
                inputStream = new ObjectInputStream(site.getInputStream());
                outputStream = new ObjectOutputStream(site.getOutputStream());
                String msg;
                String message = (String) inputStream.readObject();
                int space = message.indexOf(" ");
                String command = message.substring(0, space);
                switch (command) {
                    case "Read":
                        //read
                        outputStream.writeObject(log);
                        break;
                    case "Append":
                        //add message to log
                        msg = message.substring(space + 1);
                        if (msg.length() > 140) {
                            msg = msg.substring(0, 140);
                        }
                        log.add(msg);
                        //send ack after communication with site
                        outputStream.writeObject(ACKNOWLEDGE);
                        break;
                    case "Release":
                        //print release message
                        msg = message.substring(space + 1);
                        System.out.println(msg);
                        //send ack after communication with site
                        outputStream.writeObject(ACKNOWLEDGE);
                        break;
                    default:
                        break;
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
    
}
