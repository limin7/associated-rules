/**
 * Copyright (c) 2019
 *
 * This file is part of Associated Rules Algorithms based on ECLAT(Zaki,2000)
 * built for Neo4j GraphHACK 2019.
 *
 * This is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mypackage;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.graphdb.Transaction;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import com.mypackage.datastructures.triangularmatrix.TriangularMatrix;
import com.mypackage.results.LongResult;
import com.mypackage.tools.MemoryLogger;
import com.mypackage.input.TransactionDatabase;

/**
 * The main class for Associated Rules algorithms. It runs as a database extension of Neo4j.
 * Refer to <https://neo4j.com/docs/java-reference/current> for API references.
 *
 * @author JY, LX
 */

public class AssociatedRulesAlgoProc {
    @Context
    public GraphDatabaseService db;
    @Context
    public Log log;

    /** relative minimum support **/
    private static int minsupRelative = 500;
    /** the transaction database **/
    private static TransactionDatabase database;

    /** Where to save results. Valid values are: 'file', 'cache', 'json', 'neo4j' */
    private static String destination = "file";

    /** object to save result in memory **/
    private static StringBuffer resultBuffer = new StringBuffer();

    /** object to write the output file */
    private static BufferedWriter writer = null;

    /** query log for debugging ONLY */
    private static BufferedWriter queryLogWriter = null;

    /** the number of patterns found */
    private static int itemsetCount = 0;
    private static int singleItemCount = 0;

    /** the accumulated time spent */
    private static long startTimestamp;
    private static long endTime;
    private static long totalAlgoTime = 0L;
    private static long totalDababaseTime = 0L;

    /** For optimization with a triangular matrix for counting itemsets of size 2.  */
    private static TriangularMatrix matrix;

    /** Only for neo4j: Cypher template used to create rules */
    private static String cypherTemplate =
            "WITH apoc.coll.sort(result.itemSet) AS itemSet, result.item AS item, result.support AS support\n" +
            "MATCH (t) WHERE id(t) = item\n" +
            "MERGE (ar:ARItem{id:[item]})\n" +
            "  ON CREATE SET ar.title = t.name, ar.support = support\n" +
            "WITH item, itemSet, support, ar, apoc.coll.sort(itemSet+item) AS itemSetSorted\n" +
            "WHERE size(itemSet) > 0\n" +
            "MATCH (ar2:ARItem{id:itemSet})\n" +
            "MERGE (ar3:ARItem{id:itemSetSorted})\n" +
            "  ON CREATE SET ar3.support = support\n" +
            "MERGE (ar) -[r1:ASSOCIATES_WITH]-> (ar3)\n" +
            "  ON CREATE SET r1.confidence = toFloat(ar3.support) / ar.support, r1.assocItemIds = itemSet, r1.level = size(itemSet) + 1\n" +
            "MERGE (ar2) -[r2:ASSOCIATES_WITH]-> (ar3)\n" +
            "  ON CREATE SET r2.confidence = toFloat(ar3.support) / ar2.support, r2.assocItemIds = [item], r2.level = size(itemSet) + 1\n" +
            "RETURN *";

    /**
     * Invoke Equivalent CLAass Transformation algorithm（ECLAT) to generate associated rules for given item set.
     *
     * Original algorithm is from Zaki, M. J. (2000). "Scalable algorithms for association mining".
     * IEEE Transactions on Knowledge and Data Engineering. 12 (3): 372–390.
     *
     * @param cypherItemSet Cypher to execute that returns item sets.
     * @param minSupportRatio minimal support ratio, i.e. min percentage of transactions the item should be included.
     * @param optimized whether to use Triangular Matrix to optimize execution.
     */
    @Procedure(mode = Mode.WRITE)
    @Description("mypackage.assocrule.eclat(cypherItemset, minSupportRatio, optimized) YIELD value")
    public Stream<LongResult> eclat(
            @Name("cypherItemset") String cypherItemSet,
            @Name("minSupportRatio") Double minSupportRatio,
            @Name("optimized") Boolean optimized) {

        if (cypherItemSet == null || cypherItemSet.isEmpty())
            return null;

        if(database == null)
            database = new TransactionDatabase();

        minSupportRatio = (minSupportRatio == null) ? 0.01 : minSupportRatio;  // default min support ratio is 1%
        optimized = (optimized == null) ? true : optimized;                     // default to use triangular matrix

        log.info("##assocrule.eclat## Initialized. Parameters: {minSupportRatio}=" + minSupportRatio + ", {optimized}=" + optimized);
        log.info("##assocrule.eclat## {cypher} = ...");
        log.info(cypherItemSet);

        try (Result result = db.execute(cypherItemSet)) {
            log.info("##assocrule.eclat## cypher execution completed. Start building rule graph...");

            // Execute Cypher and load results into TransactionDatabase
            database.loadResult(result, ",");
            log.info("##assocrule.eclat## Total # items: " + database.getItems().size() + ", from # transactions:" + database.getTransactions().size());

            // Running the ECLAT algorithm
            destination = "neo4j";    // used for Neo4j
            runAlgorithm(null, minSupportRatio, optimized);

            log.info("##assocrule.eclat## Completed. Total # single item = " + singleItemCount + ", # item set = " + itemsetCount);
            log.info("##assocrule.eclat## Total algorithm time = " + totalAlgoTime + "s, total db-time = " + totalDababaseTime / 1000 + "s.");

            return Stream.of(new LongResult((long) database.itemSize()));
        }
        catch (Exception e) {
            e.printStackTrace();
            return Stream.of(new LongResult( -1L));
        }
    }

    /**
     * Run the algorithm.
     * @param output an output file path for writing the result or if null the result is saved into memory and returned
     * @param minsupp the minimum support
     * @param useTriangularMatrixOptimization if true the triangular matrix optimization will be applied.
     * @throws IOException exception if error while writing the file.
     */
    public void runAlgorithm( String output,
                              double minsupp,
                              boolean useTriangularMatrixOptimization
    ) throws Exception {

        MemoryLogger.getInstance().reset();
        // if the user want to keep the result into memory
        if(output != null){
            writer = new BufferedWriter(new FileWriter(output));
        }

        // reset the number of itemset found to 0
        itemsetCount =0;

        startTimestamp = System.currentTimeMillis();

        // calculate the min transaction number by multiplying minsupp by the database size
        minsupRelative = (int) Math.ceil(minsupp * database.size());

        // (1) First database pass : calculate TransactionIdSet(tidsets) of each item.
        // Key: item   Value :  tidset
        final Map<Integer, Set<Integer>> mapItemCount = new HashMap<Integer, Set<Integer>>();

        int maxItemId = calculateSupportSingleItems(database, mapItemCount);

        if (useTriangularMatrixOptimization) {
            // create the triangular matrix.
            matrix = new TriangularMatrix(maxItemId + 1);
            // for each transaction, take each itemset of size 2,
            // and update the triangular matrix.
            for (List<Integer> itemset : database.getTransactions()) {
                Object[] array = itemset.toArray();
                // for each item i in the transaction
                for (int i = 0; i < itemset.size(); i++) {
                    Integer itemI = (Integer) array[i];
                    // compare with each other item j in the same transaction
                    for (int j = i + 1; j < itemset.size(); j++) {
                        Integer itemJ = (Integer) array[j];
                        // update the matrix count by 1 for the pair i, j
                        matrix.incrementCount(itemI, itemJ);
                    }
                }
            }
        }

        // (2) create the list of single items
        List<Integer> frequentItems = new ArrayList<Integer>();

        // for each item
        for(Map.Entry<Integer, Set<Integer>> entry : mapItemCount.entrySet()) {
            // get the tidset of that item
            Set<Integer> tidset = entry.getValue();
            // get the support of that item (the cardinality of the tidset)
            int support = tidset.size();
            int item = entry.getKey();
            // if the item is frequent
            if(support >= minsupRelative) {
                // add the item to the list of frequent single items
                frequentItems.add(item);
                // output the item
                saveSingleItem(item, tidset, tidset.size());
            }
        }

        // Sort the list of items by the total order of increasing support.
        // This total order is suggested in the article by Zaki.
        Collections.sort(frequentItems, new Comparator<Integer>() {
            @Override
            public int compare(Integer arg0, Integer arg1) {
                return mapItemCount.get(arg0).size() - mapItemCount.get(arg1).size();
            }});

        // 3) Now we will combine each pairs of single items to generate equivalence classes
        // of 2-itemsets

        for(int i=0; i < frequentItems.size(); i++) {
            Integer itemI = frequentItems.get(i);

            // obtain the tidset and support of that item
            Set<Integer> tidsetI = mapItemCount.get(itemI);
            int supportI = tidsetI.size();

            List<Integer> equivalenceClassIitems = new ArrayList<Integer>();
            List<Set<Integer>> equivalenceClassItidsets = new ArrayList<Set<Integer>>();

            loopJ:
            for(int j=i+1; j < frequentItems.size(); j++) {
                int itemJ = frequentItems.get(j);

                // Retrieve support of item "ij" from the triangular matrix.
                if(useTriangularMatrixOptimization) {
                    int support = matrix.getSupportForItems(itemI, itemJ);
                    // if not frequent
                    if (support < minsupRelative) {
                        continue loopJ;
                    }
                }

                // Obtain the tidset of item J and its support.
                Set<Integer> tidsetJ = mapItemCount.get(itemJ);
                int supportJ = tidsetJ.size();

                // Calculate the tidset of itemset "IJ" by performing the intersection of
                // the tidsets of I and the tidset of J.
                Set<Integer> tidsetIJ = performAND(tidsetI, supportI, tidsetJ, supportJ);

                // Add itemJ to the equivalence class of 2-itemsets starting with the prefix "i".
                equivalenceClassIitems.add(itemJ);
                // Save the tidset of "ij".
                equivalenceClassItidsets.add(tidsetIJ);
            }
            // Process all itemsets from the equivalence class of 2-itemsets starting with prefix I
            // to find larger itemsets if that class has more than 0 itemsets.
            if(equivalenceClassIitems.size() > 0) {
                // This is done by a recursive call. Note that we pass
                // item I to that method as the prefix of that equivalence class.
                processEquivalenceClass(new int[]{itemI}, supportI, equivalenceClassIitems, equivalenceClassItidsets);
            }
        }

        // Check the memory usage
        MemoryLogger.getInstance().checkMemory();

        if(writer != null){
            writer.close();
        }

        // Record the end time for statistics
        endTime = System.currentTimeMillis();
        totalAlgoTime = (endTime - startTimestamp) / 1000;
    }

    /**
     * This method scans the database to calculate the support of each single item.
     *
     * @param database the transaction database
     * @param mapItemTIDS  a map to store the tidset corresponding to each item
     * @return the maximum item id appearing in this database
     */
    private int calculateSupportSingleItems(TransactionDatabase database,
                                            final Map<Integer, Set<Integer>> mapItemTIDS) {
        int maxItemId = 0;
        for (int i = 0; i < database.size(); i++) {
            // for each item in that transaction
            for (Integer item : database.getTransactions().get(i)) {
                // get the current tidset of that item
                Set<Integer> set = mapItemTIDS.get(item);
                // if no tidset, then we create one
                if (set == null) {
                    set = new HashSet<Integer>();
                    mapItemTIDS.put(item, set);
                    // if the current item is larger than all items until
                    // now, remember that!
                    if (item > maxItemId) {
                        maxItemId = item;
                    }
                }
                // add the current transaction id (tid) to the tidset of the item
                set.add(i);
            }
        }
        return maxItemId;
    }

    /**
     * This method processes all itemsets from an equivalence class to generate larger itemsets.
     *
     * @param prefix  a common prefix to all itemsets of the equivalence class
     * @param supportPrefix the support of the prefix (not used by eclat, but used by dEclat)
     * @param equivalenceClassItems  a list of suffixes of itemsets in the current equivalence class.
     * @param equivalenceClassTidsets a list of tidsets of itemsets of the current equivalence class.
     */
    private void processEquivalenceClass(int[] prefix, int supportPrefix, List<Integer> equivalenceClassItems,
                                         List<Set<Integer>> equivalenceClassTidsets) throws Exception {

        // If there is only one itemset in equivalence class
        if(equivalenceClassItems.size() == 1) {
            int itemI = equivalenceClassItems.get(0);
            Set<Integer> tidsetItemset = equivalenceClassTidsets.get(0);

            // Just save that itemset by calling save() with the prefix "prefix" and the suffix
            int support = calculateSupport(prefix.length, supportPrefix, tidsetItemset);
            save(prefix, itemI, tidsetItemset, support);
            return;
        }

        // If there are only two itemsets in the equivalence class
        if(equivalenceClassItems.size() == 2) {
            // Get the prefix of the itemset (an item called I)
            int itemI = equivalenceClassItems.get(0);
            Set<Integer> tidsetI = equivalenceClassTidsets.get(0);
            int supportI = calculateSupport(prefix.length, supportPrefix, tidsetI);
            // Save item I
            save(prefix, itemI, tidsetI, supportI);

            // Get the suffix of the itemset (an item called J)
            int itemJ = equivalenceClassItems.get(1);
            Set<Integer> tidsetJ = equivalenceClassTidsets.get(1);
            int supportJ = calculateSupport(prefix.length, supportPrefix, tidsetJ);
            // Save item J
            save(prefix, itemJ, tidsetJ, supportJ);

            // Calculate the tidset of the itemset by uniting itemset I and J.
            Set<Integer> tidsetIJ = performAND(tidsetI, tidsetI.size(), tidsetJ, tidsetJ.size());
            // Save the itemset prefix+IJ to the output if it has enough support
            if(tidsetIJ.size() >= minsupRelative) {
                int newPrefix[] = new int[prefix.length +1];
                System.arraycopy(prefix, 0, newPrefix, 0, prefix.length);
                newPrefix[prefix.length] = itemI;
                int supportIJ = calculateSupport(newPrefix.length, supportI, tidsetIJ);
                save(newPrefix, itemJ, tidsetIJ, supportIJ);
            }
            return;
        }

        // The next loop combines each pairs of itemsets of the equivalence class
        // to form larger itemsets

        // For each itemset "prefix" + "i"
        for(int i=0; i< equivalenceClassItems.size(); i++) {
            int suffixI = equivalenceClassItems.get(i);
            // get the tidset and support of that itemset
            Set<Integer> tidsetI = equivalenceClassTidsets.get(i);

            // save the itemset to the file because it is frequent
            int supportI = calculateSupport(prefix.length, supportPrefix, tidsetI);
            save(prefix, suffixI, tidsetI, supportI);

            // create the empty equivalence class for storing all itemsets of the
            // equivalence class starting with prefix + i
            List<Integer> equivalenceClassISuffixItems= new ArrayList<Integer>();
            List<Set<Integer>> equivalenceITidsets = new ArrayList<Set<Integer>>();

            // For each itemset "prefix" + j"
            for(int j=i+1; j < equivalenceClassItems.size(); j++) {
                int suffixJ = equivalenceClassItems.get(j);

                // Get the tidset and support of the itemset prefix + "j"
                Set<Integer> tidsetJ = equivalenceClassTidsets.get(j);
                int supportJ = calculateSupport(prefix.length, supportPrefix, tidsetJ);

                // Calculate the tidset of the itemset {prefix, i,j} by intersecting
                // the tidset of the itemset prefix+i with the itemset prefix+j.
                Set<Integer> tidsetIJ = performAND(tidsetI, supportI, tidsetJ, supportJ);

                // If the itemset prefix+i+j is frequent, then we add it to the
                // equivalence class of itemsets having the prefix "prefix"+i
                if(tidsetIJ.size() >= minsupRelative) {
                    equivalenceClassISuffixItems.add(suffixJ);
                    equivalenceITidsets.add(tidsetIJ);
                }
            }

            // If there is more than an itemset in the equivalence class
            // then we recursively process that equivalence class to find larger itemsets
            if(equivalenceClassISuffixItems.size() >0) {
                // We create the itemset prefix + i
                int newPrefix[] = new int[prefix.length +1];
                System.arraycopy(prefix, 0, newPrefix, 0, prefix.length);
                newPrefix[prefix.length] = suffixI;
                // Recursive call
                processEquivalenceClass(newPrefix, supportI, equivalenceClassISuffixItems, equivalenceITidsets);
            }
        }

        // we check the memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Calculate the support of an itemset X using the tidset of X.
     *
     * @param lengthOfX  the length of the itemset X - 1 (used by dEclat)
     * @param supportPrefix the support of the prefix (not used by Eclat, but used by dEclat).
     * @param tidsetI the tidset of X
     * @return the support
     */
    private int calculateSupport(int lengthOfX, int supportPrefix, Set<Integer> tidsetI) {
        return tidsetI.size();
    }

    /**
     * This method performs the intersection of two tidsets.
     *
     * @param tidsetI the first tidset
     * @param supportI  the cardinality of the first tidset
     * @param tidsetJ  the second tidset
     * @param supportJ the cardinality of the second tidset
     * @return the resulting tidset.
     */
    private Set<Integer> performAND(Set<Integer> tidsetI, int supportI,
                                    Set<Integer> tidsetJ, int supportJ) {

        Set<Integer> tidsetIJ = new HashSet<Integer>();
        // To reduce the number of comparisons of the two tidsets,
        // if the tidset of I is larger than the tidset of J,
        // we will loop on the tidset of J. Otherwise, we will loop on the tidset of I
        if(supportI > supportJ) {
            // for each tid containing j
            for(Integer tid : tidsetJ) {
                // if the transaction also contains i, add it to tidset of {i,j}
                if(tidsetI.contains(tid)) {
                    // add it to the intersection
                    tidsetIJ.add(tid);
                }
            }
        }else {
            // for each tid containing i
            for(Integer tid : tidsetI) {
                // if the transaction also contains j, add it to tidset of {i,j}
                if(tidsetJ.contains(tid)) {
                    // add it to the intersection
                    tidsetIJ.add(tid);
                }
            }
        }
        // return the new tidset
        return tidsetIJ;
    }

    /**
     * Save an itemset to disk or memory (depending on what the user chose).
     *
     * @param prefix the prefix of the itemset to be saved
     * @param suffixItem  the last item to be appended to the itemset
     * @param tidset the tidset of this itemset
     * @param support calculated support for itemset
     * @throws IOException if an error occurrs when writing to disk.
     */
    private void save(int[] prefix, int suffixItem, Set<Integer> tidset, int support) throws Exception {
        // increase the itemset count
        itemsetCount++;
        if(destination.equals("json")){
            String contentToWrite = "";
            Long itemData;

            // write prefix as array
            contentToWrite = "{itemSet:[";
            for(int item: prefix) {
                itemData = database.getItemAt(item);
                contentToWrite = contentToWrite + itemData + ",";
            }
            contentToWrite = contentToWrite.substring(0,contentToWrite.length()-1) + "],";
            resultBuffer.append(contentToWrite);

            // write suffix
            contentToWrite = "item:";
            itemData = database.getItemAt(suffixItem);
            contentToWrite = contentToWrite + itemData + ",";
            resultBuffer.append(contentToWrite);

            // write support
            contentToWrite = "support:" + tidset.size() + "}\n";
            resultBuffer.append(contentToWrite);
        }
        else if(destination.equals("neo4j")){
            Long itemData;
            itemData = database.getItemAt(suffixItem);

            saveToNeo4j(prefix, itemData, support);
        }
        else if(writer != null && destination.equals("file"))
        {
            // if the result should be saved to a file
            // write it to the output file
            StringBuffer buffer = new StringBuffer();
            String contentToWrite = "";
            Long itemData;

            // write prefix as array
            contentToWrite = "{itemSet:[";
            for(int item: prefix) {
                itemData = database.getItemAt(item);
                contentToWrite = contentToWrite + itemData + ",";
            }
            contentToWrite = contentToWrite.substring(0,contentToWrite.length()-1) + "],";
            buffer.append(contentToWrite);

            // write suffix
            contentToWrite = "item:";
            itemData = database.getItemAt(suffixItem);
            contentToWrite = contentToWrite + itemData + ",";
            buffer.append(contentToWrite);

            // write support
            contentToWrite = "support:" + tidset.size() + "}";
            buffer.append(contentToWrite);

            writer.write(buffer.toString());
            writer.newLine();
        }
    }

    /**
     * Return saved results.
     */
    public String getResultString(){

        return resultBuffer.toString();
    }

    /**
     * Save an itemset containing a single item to disk or memory (depending on what the user chose).
     *
     * @param item the item to be saved
     * @param tidset the tidset of this itemset
     * @param support calculated support for itemset
     * @throws IOException if an error occurrs when writing to disk.
     */
    private void saveSingleItem(int item, Set<Integer> tidset, int support) throws Exception {
        // increase the itemset count
        singleItemCount++;

        if(destination.equals("json")){
            Long itemData = database.getItemAt(item);
            resultBuffer.append("{itemSet:[],item:");
            resultBuffer.append(itemData);
            resultBuffer.append(",support:");
            resultBuffer.append(support);
            resultBuffer.append("}\n");
        }
        else if(destination.equals("neo4j")){
            Long itemData = database.getItemAt(item);

            saveToNeo4j(null,itemData, support);
        }
        else if(destination.equals("file")){
            // if the result should be saved to a file
            // write it to the output file
            StringBuffer buffer = new StringBuffer();

            Long itemData = database.getItemAt(item);
            buffer.append("{itemSet:[],item:");
            buffer.append(itemData);
            buffer.append(",support:");
            buffer.append(support);
            buffer.append("}");
            writer.write(buffer.toString());
            writer.newLine();
        }
    }

    private void saveToNeo4j(int[] prefix, Long itemData, int support)
            throws Exception
    {
        String cypherToRun = "";
        String contentToWrite = "";
        Long itemData2;

        // For single item, prefix should be null so to write prefix as blank array
        if(prefix == null) {
            cypherToRun = cypherTemplate.replaceAll("result.itemSet", "[] ");
        } else {
            // write prefix as array
            contentToWrite = "[";
            for(int item: prefix) {
                itemData2 = database.getItemAt(item);
                contentToWrite = contentToWrite + itemData2 + ",";
            }

            contentToWrite = contentToWrite.substring(0,contentToWrite.length()-1) + "] ";
            cypherToRun = cypherTemplate.replaceAll("result.itemSet",contentToWrite);
        }

        // write suffix
        contentToWrite = itemData + " ";
        cypherToRun = cypherToRun.replaceAll("result.item",contentToWrite);

        // write support
        contentToWrite = support + " ";
        cypherToRun = cypherToRun.replaceAll("result.support",contentToWrite);

        if(queryLogWriter != null){
            queryLogWriter.write(cypherToRun);
            queryLogWriter.newLine();
            queryLogWriter.newLine();
            queryLogWriter.newLine();
        }else {
            try {
                Long currentTime = System.currentTimeMillis();
                Transaction tx = db.beginTx();
                Result res = db.execute(cypherToRun);
                tx.success();
                totalDababaseTime += (System.currentTimeMillis() - currentTime);
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
    }

    public String getResult() {
        return resultBuffer.toString();
    }

    public void setDestination(String dest) {
        destination = (dest == null || dest.isEmpty())? "file" : dest;
    }

    public void setDatabase(TransactionDatabase db) {
        database = db;
    }

    public void setQueryLogWriter(BufferedWriter writer)
    {
        queryLogWriter = writer;
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  ECLAT STATS =============");
        long temps = endTime - startTimestamp;
        System.out.println(" Transactions count from database : "
                + database.size());
        System.out.println(" Frequent itemsets count : "
                + itemsetCount);
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println(" Maximum memory usage : "
                + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println("===================================================");
    }
}
