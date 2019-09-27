package com.mypackage.input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import org.neo4j.graphdb.Result;

/**
 * The main class for storing transactions in memory. A transaction contains a list of items.
 *
 * @author JY, LX
 */
public class TransactionDatabase {
    // The list of items in this database
    private final Set<Long> items = new LinkedHashSet<Long>();
    private Object itemsArray[] = null;
    // the list of transactions
    private final List<List<Integer>> transactions = new ArrayList<List<Integer>>();

    /**
     * Method to load Result set containing a transaction database into memory
     * @param result the path of the file
     * @separator String the separator of items in the file. Default value is space ' '
     * @throws IOException exception if error reading the file
     */
    public void loadResult(Result result, String separator) throws IOException {
        String thisLine; // variable to read each line
        String del = separator == null? " " : separator;

        try {
            // for each line
            while (result.hasNext()) {
                // if the line is not a comment, is not empty or is not other
                // kind of metadata
                thisLine = result.columnAs("itemSet").next().toString();
                if (!thisLine.isEmpty()) {
                    // remove leading '[' and ending ']'
                    thisLine = thisLine.substring(1,thisLine.length()-1);
                    // System.out.println(thisLine);
                    addTransaction(thisLine.split(del));
                }
            }

            itemsArray = items.toArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {

        }
    }

    /**
     * Method to load a file containing a transaction database into memory
     * @param path the path of the file
     * @separator String the separator of items in the file. Default value is space ' '
     * @throws IOException exception if error reading the file
     */
    public void loadFile(String path, String separator) throws IOException {
        String thisLine; // variable to read each line
        String del = separator == null? " " : separator;
        BufferedReader myInput = null; // object to read the file

        try {
            FileInputStream fin = new FileInputStream(new File(path));
            myInput = new BufferedReader(new InputStreamReader(fin));
            // for each line
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is not a comment, is not empty or is not other
                // kind of metadata
                if (thisLine.isEmpty() == false &&
                        thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%'
                        && thisLine.charAt(0) != '@') {
                    // split the line according to spaces and then
                    // call "addTransaction" to process this line.
                    addTransaction(thisLine.split(del));
                }
            }

            itemsArray = items.toArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }
    }

    /**
     * This method process a line from a file that is read.
     * @param itemsString the items contained in this line
     */
    private void addTransaction(String itemsString[]) {
        // create an empty transaction
        List<Integer> itemset = new ArrayList<Integer>();
        Integer index = 0;

        // for each item in this line
        for (String attribute : itemsString) {
            // convert from string to int
            Long item = Long.parseLong(attribute.trim());

            // add item to the set of all items in this database
            items.add(item);

            // add the index of item in items(LinkedHashSet) to current transaction
            index = findItemIndex(item);
            itemset.add(index);
        }
        // add the transactions to the list of all transactions in this database.
        transactions.add(itemset);
    }

    /**
     * This method searches an item in items and return its location if found.
     * @param item the item to find
     */
    private Integer findItemIndex(Long item){

        if(items == null || items.size() == 0)
            return -1;

        // Iterator it = items.iterator();
        Integer  index = 0;

        for(Long i:items){
            if(i.equals(item))
                return index;
            index++;
        }

        return -1;      // not found
    }

    /**
     * Method to print the content of the transaction database to the console.
     */
    public void printDatabase() {
        System.out
                .println("===================  TRANSACTION DATABASE ===================");
        int count = 0;
        // for each transaction
        for (List<Integer> itemset : transactions) { // pour chaque objet
            System.out.print("0" + count + ":  ");
            print(itemset); // print the transaction
            System.out.println("");
            count++;
        }
    }

    /**
     * Method to print a transaction to System.out.
     * @param itemset a transaction
     */
    private void print(List<Integer> itemset){
        StringBuffer r = new StringBuffer();
        // for each item in this transaction
        for (Integer item : itemset) {
            // append the item to the stringbuffer
            r.append(item.toString());
            r.append(' ');
        }
        System.out.println(r); // print to System.out
    }

    /**
     * Get the number of transactions in this transaction database.
     * @return the number of transactions.
     */
    public int size() {
        return transactions.size();
    }

    /**
     * Get the number of transactions in this transaction database.
     * @return the number of transactions.
     */
    public int itemSize() {
        return items.size();
    }

    /**
     * Get the list of transactions in this database
     * @return A list of transactions (a transaction is a list of Integer).
     */
    public List<List<Integer>> getTransactions() {
        return transactions;
    }

    /**
     * Get the set of items contained in this database.
     * @return The set of items.
     */
    public Set<Long> getItems() {
        return items;
    }

    /**
     * Get the set of items contained in this database.
     * @return The set of items.
     */
    public Long getItemAt(Integer pos) {
        if(items == null || items.size() ==0 || pos < 0)
            return -1L;

        return (Long)itemsArray[pos];
    }
}
