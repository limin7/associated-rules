package com.mypackage.test;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import com.mypackage.AssociatedRulesAlgoProc;
import com.mypackage.input.TransactionDatabase;

/**
 * Test class of ECLAT which reads transactions from and writes results to local files.
 *
 * @author JY, LX
 */
public class TestEclatSaveToFile {

    private static final boolean _DEBUG_MODE_ = false;

    public static void main(String [] arg) throws Exception{

        // Test files: topics_10.txt, topics_12k.txt
        String input = fileToPath("topics_22.txt");  // the dataset to load
        String output = ".//output_22.txt";  // the path for saving the frequent itemsets found

        // minimum support
        double minsupratio = 0.10; // means a minsup of 1% of all transactions (we used a relative support)

        // Load the transaction database
        TransactionDatabase database = new TransactionDatabase();
        BufferedWriter  queryLogWriter = null;
        try {
            database.loadFile(input, ",");
        } catch (IOException e) {
            e.printStackTrace();
        }
//		context.printContext();

        // Run the ECLAT algorithm
        AssociatedRulesAlgoProc algo = new AssociatedRulesAlgoProc();
        algo.setDatabase(database);
        algo.setDestination("neo4j");

        if(_DEBUG_MODE_) {
            queryLogWriter = new BufferedWriter(new FileWriter(".//query_log.txt"));
            algo.setQueryLogWriter(queryLogWriter);
        }
        algo.runAlgorithm(output, minsupratio, true);

        algo.printStats();

        if(queryLogWriter != null)
            queryLogWriter.close();

    }   // main

    public static String fileToPath(String filename) throws UnsupportedEncodingException{
            URL url = TestEclatSaveToFile.class.getResource(filename);
            return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }

}
