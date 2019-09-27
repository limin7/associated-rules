package com.mypackage.datastructures.triangularmatrix;

public class TriangularMatrix implements AbstractTriangularMatrix {

    // the triangular matrix is a two dimension array of integers
    private int[][] matrix;
    // the number of lines in the matrix
    private int elementCount;

    /**
     * Constructor of a new triangular matrix.
     * @param elementCount the desired number of lines in the matrix.
     */
    public TriangularMatrix(int elementCount){
        // save the number of lines
        this.elementCount = elementCount;
        // initialize the matrix
        matrix = new int[elementCount-1][]; // -1 cause we want it shorter of 1 element
        for(int i=0; i< elementCount-1; i++){ // -1 cause we want it shorter of 1 element
            // allocate an array for each row
            matrix[i] = new int[elementCount - i -1];
        }

    }

    /**
     * Get the value at a given position in the matrix
     * @param i  the row
     * @param j  the column
     * @return the value
     */
    private int get(int i, int j){
        return matrix[i][j];
    }

    /**
     * Main method for testing and debugging only!
     */
    public static void main(String[] args) {
        TriangularMatrix a = new TriangularMatrix(5);

        System.out.println(a.toString());
        // AB, AD, AE, BD, BE, DE
        a.incrementCount(1, 0);
        System.out.println(a.toString());
        a.incrementCount(1, 4);
        a.incrementCount(1, 3);
        a.incrementCount(2, 4);
        a.incrementCount(2, 4);
        a.incrementCount(4, 3);
        System.out.println(a.toString());
        a.incrementCount(0, 2);
        a.incrementCount(0, 3);
        a.incrementCount(0, 4);
        System.out.println(a.toString());
    }

    /* (non-Javadoc)
     * @see
     */
    public String toString() {
        // print the number of elements
        System.out.println("Element count = " + elementCount);
        // create a string buffer
        StringBuffer temp = new StringBuffer();
        // for each row
        for (int i = 0; i < matrix.length; i++) {
            temp.append(i);
            temp.append(": ");
            // for each column
            for (int j = 0; j < matrix[i].length; j++) {
                temp.append(matrix[i][j]); // add the value at position i,j
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
        if(j < i){
            matrix[elementCount - i -1][j]++;  // so that id is always smaller than j
        }else{
            matrix[elementCount - j -1][i]++;
        }
    }

    /* (non-Javadoc)
     * @see
     */
    public int getSupportForItems(int i, int j){
        if(j < i){
            return matrix[elementCount - i -1][j];  // so that id is always smaller than id2
        }else{
            return matrix[elementCount - j -1][i];
        }
    }

    /* (non-Javadoc)
     * @see
     */
    public void setSupport(Integer i, Integer j, int support) {
        if(j < i){
            matrix[elementCount - i -1][j] = support;  // so that id is always smaller than j
        }else {
            matrix[elementCount - j - 1][i] = support;
        }
    }
}

