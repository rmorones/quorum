package cs171_proj3;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Ricardo Morones <rmorones@umail.ucsb.edu>
 */
public class Main {

    /**
     * @param args the command line arguments
     * @throws java.io.FileNotFoundException
     * @throws java.io.UnsupportedEncodingException
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, InterruptedException {
        Object[] lockingObjects = new Object[1];
        
        for (int i = 0; i < lockingObjects.length; ++i) {
            lockingObjects[i] = new Object();
	}
        
        List<Thread> sites = new ArrayList<>();
        for(int i = 0; i < 1; ++i) {
            Site site = new Site(9999, "localhost", lockingObjects[i], i, 1);
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
