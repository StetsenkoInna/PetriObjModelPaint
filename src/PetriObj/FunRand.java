package PetriObj;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



/**
 *This class contains methods for generating a random value according to a given distribution
 *  @author Inna V. Stetsenko
 */
public  class FunRand {


    /**
     * Generates random value according to an exponential distribution
     *
     * @param timeMean the mean value
     * @return a random value according to an exponential distribution
     */
    public static double exp(double timeMean) {
        double a = 0;
        while (a == 0) {
            a = Math.random();
        }
        a = -timeMean * Math.log(a);

        return a;
    }

    /**
     * Generates random value according to a uniform distribution
     *
     * @param timeMin the minimum value of random value
     * @param timeMax the maximum value of random value
     * @return a random value according to a uniform distribution
     * @throws PetriObj.ExceptionInvalidTimeDelay the negative value of the delay time is excluded
     */
    public static double unif(double timeMin, double timeMax) throws ExceptionInvalidTimeDelay {
        double a = 0;
        while (a == 0) {
            a = Math.random();
        }
        a = timeMin + a * (timeMax - timeMin);
        if (a<0)
            throw new ExceptionInvalidTimeDelay("Negative time delay is generatated: Check parameters for time delay.");
        return a;
    }

    /**
     * Generates random value according to a normal (Gauss) distribution
     *
     * @param timeMean the mean of random value
     * @param timeDeviation the deviation of random value
     * @return a random value according to a normal (Gauss) distribution
     * @throws PetriObj.ExceptionInvalidTimeDelay the negative value of the delay time is excluded
     */
    public static double norm(double timeMean, double timeDeviation) throws ExceptionInvalidTimeDelay {
        double a;
        Random r = new Random();
        a = timeMean + timeDeviation * r.nextGaussian();
        if (a<0)
            throw new ExceptionInvalidTimeDelay("Negative time delay is generatated: Check parameters for time delay.");
        return a;
    }
    /**
     * Generates random value according to the empiric distribution
     * determined by the sequence of points (xi,yi), 
     * where yi are from interval (0;1)
     * @param x the array of x coordinates of points
     * @param y the array of y coordinates of points
     * @return a random value according to a empiric distribution
     * @throws java.lang.Exception if maximum of y array is bigger than 1.0
     */
    public static double empiric(double[] x, double[] y) throws Exception {
        int n = x.length;
        if(y[n-1]!=1.0)
             throw new Exception("Illegal array of points for empiric distribution");
        double a;
        double r = Math.random();
       
        for(int i=1;i<n-1;i++){
           if(r>y[i-1]&&r<=y[i]){
               a=x[i-1]+(r-y[i-1])*(x[i]-x[i-1])/(y[i]-y[i-1]);
               return a;
           }
        }
        a=x[n-2]+(r-y[n-2])*(x[n-1]-x[n-2])/(y[n-1]-y[n-2]);
        
        if (a<0)
            throw new ExceptionInvalidTimeDelay("Negative time delay is generatated: Check parameters for time delay.");
        return a;
    }
   
}
