package com.mypackage.datastructures.triangularmatrix;

import java.util.HashMap;
import java.util.Map;

public class SparseTriangularMatrix implements AbstractTriangularMatrix {

    // the triangular matrix is a hashmap of hashmaps
    // where the key is an item I, then the value is a map where each entry is a key representing an item J
    // and a value representing the count of {I, J}.
    private Map<Integer, Map<Integer, Integer>> matrix = new HashMap<Integer, Map<Integer, Integer>>();

    /**
     * Constructor of a new triangular matrix.
     * @param : the desired number of lines in the matrix.
     */
    public SparseTriangularMatrix(){

    }

    /**
     * This constructor is for compatibility with the other TriangularMatrix implementation.
     * The parameter "itemCount" is not used.
     * @param itemCount
     */
    public SparseTriangularMatrix(int itemCount){

    }

    /* (non-Javadoc)
     * @see
     */
    public String toString() {
        // create a string buffer
        StringBuffer temp = new StringBuffer();
        // for each row
        for (int i = 0; i < matrix.keySet().size(); i++) {
            temp.append(i);
            temp.append(": ");
            // for each column
            for (int j = 0; j < matrix.get(i).size(); j++) {
                temp.append(matrix.get(i).get(j)); // add the value at position i,j
                temp.append(" ");
            }
            temp.append("\n");
        }
        return temp.toString();
    }

    /* (non-Javadoc)
     * @see
     */
    public void incrementCount(int i, int j) {
        if(i < j) {
            // First get the map of i
            Map<Integer, Integer> mapCount = matrix.get(i);
            if(mapCount == null) {
                mapCount = new HashMap<Integer,Integer>();
                matrix.put(i, mapCount);
                mapCount.put(j, 1);
            }else {
                // Second, get the count of i,j
                Integer count = mapCount.get(j);
                if(count == null) {
                    mapCount.put(j, 1);
                }else {
                    mapCount.put(j, ++count);
                }
            }
        }else {
            // First get the map of j
            Map<Integer, Integer> mapCount = matrix.get(j);
            if(mapCount == null) {
                mapCount = new HashMap<Integer,Integer>();
                matrix.put(j, mapCount);
                mapCount.put(i, 1);
            }else {
                // Second, get the count of i,j
                Integer count = mapCount.get(i);
                if(count == null) {
                    mapCount.put(i, 1);
                }else {
                    mapCount.put(i, ++count);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see
     */
    public int getSupportForItems(int i, int j){
        if(i < j) {
            // First get the map of i
            Map<Integer, Integer> mapCount = matrix.get(i);
            if(mapCount == null) {
                return 0;
            }else {
                // Second, get the count of i,j
                Integer count = mapCount.get(j);
                if(count == null) {
                    return 0;
                }else {
                    return count;
                }
            }
        }else {
            // First get the map of i
            Map<Integer, Integer> mapCount = matrix.get(j);
            if(mapCount == null) {
                return 0;
            }else {
                // Second, get the count of i,j
                Integer count = mapCount.get(i);
                if(count == null) {
                    return 0;
                }else {
                    return count;
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see
     */
    public void setSupport(Integer i, Integer j, int support) {
        if(i < j) {
            // First get the map of i
            Map<Integer, Integer> mapCount = matrix.get(i);
            if(mapCount == null) {
                mapCount = new HashMap<Integer,Integer>();
                matrix.put(i, mapCount);
                mapCount.put(j, support);
            }else {
                // Second, set the count of i,j
                mapCount.put(j, support);
            }
        }else {
            // First get the map of j
            Map<Integer, Integer> mapCount = matrix.get(j);
            if(mapCount == null) {
                mapCount = new HashMap<Integer,Integer>();
                matrix.put(j, mapCount);
                mapCount.put(i, support);
            }else {
                // Second, set the count of i,j
                mapCount.put(i, support);
            }
        }
    }
}
