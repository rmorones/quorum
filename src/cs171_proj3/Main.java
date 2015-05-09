package cs171_proj3;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 * @author Chris Kim <chriskim@umail.ucsb.edu>
 */
public class Main {
    private static final String infile = "site{}.txt";
    private static final String PORT = "999{}";
    private static final int N = 5;
    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     * @throws java.io.UnsupportedEncodingException
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, InterruptedException {

        List<Thread> sites = new ArrayList<>();
        for(Integer i = 0; i < 1; ++i) {
            int port = Integer.parseInt(PORT.replace("{}", i.toString()));
            Site site = new Site(port, "localhost", i, N, infile.replace("{}", i.toString()));
            sites.add(site);
        }
        
        for(int i = 0; i < 1;++i) {
            sites.get(i).start();
        }
    
        for(int i = 0; i < 1; ++i) {
            sites.get(i).join();
        }
    }
    
}
