package ua.stetsenkoinna.LibNet;

import ua.stetsenkoinna.PetriObj.ArcIn;
import ua.stetsenkoinna.PetriObj.ArcOut;
import ua.stetsenkoinna.PetriObj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.PetriObj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriT;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.IntStream;

import ua.stetsenkoinna.annotation.NetLibraryMethod;

public class NetLibrary {

    /**
     * Creates Petri net that describes the dynamics of system of the mass
     * service (with unlimited queue)
     *
     * @param numChannel the quantity of devices
     * @param timeMean the mean value of service time of unit
     * @param name the individual name of SMO
     * @throws ExceptionInvalidTimeDelay if one of net's transitions has no input position.
     * @return Petri net dynamics of which corresponds to system of mass service with given parameters
     * @throws ExceptionInvalidNetStructure
     */
    public static PetriNet CreateNetSMOwithoutQueue(int numChannel, double timeMean, String name) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 0));
        d_P.add(new PetriP("P2", numChannel));
        d_P.add(new PetriP("P3", 0));
        d_T.add(new PetriT("T1", timeMean));
        d_T.get(0).setDistribution("exp", d_T.get(0).getTimeServ());
        d_T.get(0).setParamDeviation(0.0);
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(2), 1));
        PetriNet d_Net = new PetriNet("SMOwithoutQueue" + name, d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    /**
     * Creates Petri net that describes the dynamics of arrivals of demands for
     * service
     *
     * @param timeMean mean value of interval between arrivals
     * @return Petri net dynamics of which corresponds to generator
     * @throws ExceptionInvalidTimeDelay if Petri net has invalid structure
     * @throws ExceptionInvalidNetStructure
     */
    public static PetriNet CreateNetGenerator(double timeMean) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 1));
        d_P.add(new PetriP("P2", 0));
        d_T.add(new PetriT("T1", timeMean, Double.MAX_VALUE));
        d_T.get(0).setDistribution("exp", d_T.get(0).getTimeServ());
        d_T.get(0).setParamDeviation(0.0);
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(0), 1));
        PetriNet d_Net = new PetriNet("Generator", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    /**
     * Creates Petri net that describes the route choice with given
     * probabilities
     *
     * @param p1 the probability of choosing the first route
     * @param p2 the probability of choosing the second route
     * @param p3 the probability of choosing the third route
     * @return Petri net dynamics of which corresponds to fork of routs
     * @throws ExceptionInvalidTimeDelay if Petri net has invalid structure
     * @throws ExceptionInvalidNetStructure
     */
    public static PetriNet CreateNetFork(double p1, double p2, double p3) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 0));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("P3", 0));
        d_P.add(new PetriP("P4", 0));
        d_P.add(new PetriP("P5", 0));
        d_T.add(new PetriT("T1", 0.0, Double.MAX_VALUE));
        d_T.get(0).setProbability(p1);
        d_T.add(new PetriT("T2", 0.0, Double.MAX_VALUE));
        d_T.get(1).setProbability(p2);
        d_T.add(new PetriT("T3", 0.0, Double.MAX_VALUE));
        d_T.get(2).setProbability(p3);
        d_T.add(new PetriT("T4", 0.0, Double.MAX_VALUE));
        d_T.get(3).setProbability(1 - p1 - p2 - p3);
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(1), 1));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(2), 1));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(3), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(1), d_P.get(2), 1));
        d_Out.add(new ArcOut(d_T.get(2), d_P.get(3), 1));
        d_Out.add(new ArcOut(d_T.get(3), d_P.get(4), 1));
        PetriNet d_Net = new PetriNet("Fork", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetTest3() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 100));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("P3", 0));
        d_T.add(new PetriT("T1", 0.0));
        d_T.add(new PetriT("T2", 0.0));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(1), d_P.get(2), 1));
        PetriNet d_Net = new PetriNet("test3", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    public static PetriNet CreateNetGenerator2(double d) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 1));
        d_P.add(new PetriP("P2", 0));
        d_T.add(new PetriT("T1", d));
        d_T.get(0).setDistribution("exp", d_T.get(0).getTimeServ());
        d_T.get(0).setParamDeviation(0.0);
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(0), 1));
        PetriNet d_Net = new PetriNet("Generator", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetThread3() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("bow{", 0));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("lockA", 1));
        d_P.add(new PetriP("P4", 0));
        d_P.add(new PetriP("P5", 0));
        d_P.add(new PetriP("P6", 0));
        d_P.add(new PetriP("failure++", 0));
        d_P.add(new PetriP("lockB", 1));
        d_P.add(new PetriP("bowA++", 0));
        d_P.add(new PetriP("P10", 0));
        d_P.add(new PetriP("bowB++", 0));
        d_P.add(new PetriP("P15", 0));
        d_P.add(new PetriP("bowLoop{", 100));
        d_P.add(new PetriP("bow", 0));
        d_P.add(new PetriP("Core", 1));
        d_T.add(new PetriT("imp{", 0.1));
        d_T.add(new PetriT("tryLockA", 0.0));
        d_T.add(new PetriT("0&?", 0.0));
        d_T.add(new PetriT("tryLockB", 0.0));
        d_T.add(new PetriT("bowBack{", 0.1));
        d_T.add(new PetriT("unlockA", 0.0));
        d_T.add(new PetriT("0&1", 0.0));
        d_T.add(new PetriT("failure", 0.0));
        d_T.add(new PetriT("unlockAB", 0.1));
        d_T.add(new PetriT("unlockB", 0.1));
        d_T.add(new PetriT("for{", 0.0));
        d_T.add(new PetriT("for", 0.0));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(1), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(2), 1));
        d_In.add(new ArcIn(d_P.get(2), d_T.get(1), 1));
        d_In.add(new ArcIn(d_P.get(3), d_T.get(3), 1));
        d_In.add(new ArcIn(d_P.get(7), d_T.get(3), 1));
        d_In.add(new ArcIn(d_P.get(5), d_T.get(4), 1));
        d_In.add(new ArcIn(d_P.get(3), d_T.get(5), 1));
        d_In.add(new ArcIn(d_P.get(4), d_T.get(6), 1));
        d_In.add(new ArcIn(d_P.get(4), d_T.get(7), 1));
        d_In.add(new ArcIn(d_P.get(7), d_T.get(6), 1));
        d_In.add(new ArcIn(d_P.get(9), d_T.get(8), 1));
        d_In.add(new ArcIn(d_P.get(11), d_T.get(9), 1));
        d_In.add(new ArcIn(d_P.get(12), d_T.get(10), 1));
        d_In.add(new ArcIn(d_P.get(13), d_T.get(11), 1));
        d_In.add(new ArcIn(d_P.get(14), d_T.get(10), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(1), d_P.get(3), 1));
        d_Out.add(new ArcOut(d_T.get(2), d_P.get(4), 1));
        d_Out.add(new ArcOut(d_T.get(3), d_P.get(5), 1));
        d_Out.add(new ArcOut(d_T.get(5), d_P.get(2), 1));
        d_Out.add(new ArcOut(d_T.get(4), d_P.get(9), 1));
        d_Out.add(new ArcOut(d_T.get(7), d_P.get(6), 1));
        d_Out.add(new ArcOut(d_T.get(3), d_P.get(8), 1));
        d_Out.add(new ArcOut(d_T.get(4), d_P.get(10), 1));
        d_Out.add(new ArcOut(d_T.get(8), d_P.get(2), 1));
        d_Out.add(new ArcOut(d_T.get(8), d_P.get(7), 1));
        d_Out.add(new ArcOut(d_T.get(6), d_P.get(11), 1));
        d_Out.add(new ArcOut(d_T.get(9), d_P.get(7), 1));
        d_Out.add(new ArcOut(d_T.get(10), d_P.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(8), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(11), d_P.get(14), 1));
        d_Out.add(new ArcOut(d_T.get(5), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(9), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(7), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(9), d_P.get(6), 1));
        d_Out.add(new ArcOut(d_T.get(5), d_P.get(6), 1));
        PetriNet d_Net = new PetriNet("friendThread", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetFriend() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        double delay = 100.0;
        double x = 0.00000001;
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        Random r = new Random();
        d_P.add(new PetriP("bow[", 0));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("lock", 1));
        d_P.add(new PetriP("P4", 0));
        d_P.add(new PetriP("P5", 0));
        d_P.add(new PetriP("P6", 0));
        d_P.add(new PetriP("failure++", 0));
        d_P.add(new PetriP("lockOther", 1));
        d_P.add(new PetriP("bowA++", 0));
        d_P.add(new PetriP("P10", 0));
        d_P.add(new PetriP("bowB++", 0));
        d_P.add(new PetriP("P15", 0));
        d_P.add(new PetriP("bowLoop[", 10));
        d_P.add(new PetriP("bow]", 0));
        d_P.add(new PetriP("Core", 1));
        // was delay
        d_T.add(new PetriT("imp[", delay * x));
        d_T.get(0).setDistribution("norm", d_T.get(0).getTimeServ() * 0.1);
        //priority = 1
        d_T.add(new PetriT("tryLockA", delay * x, 1));
        d_T.get(1).setDistribution("norm", d_T.get(1).getTimeServ() * 0.1);
        d_T.add(new PetriT("0&?", 0.0));
        //priority = 1
        d_T.add(new PetriT("tryLockB", delay * x, 1));
        d_T.get(3).setDistribution("norm", d_T.get(3).getTimeServ() * 0.1);
        //delay*x
        d_T.add(new PetriT("bowBack[]", delay));
        d_T.get(4).setDistribution("norm", d_T.get(4).getTimeServ() * 0.1);
        d_T.add(new PetriT("unlockA", 0.0));
        //priority = 1
        d_T.add(new PetriT("0&1", 0.0, 1));
        d_T.add(new PetriT("failure", 0.0));
        d_T.add(new PetriT("unlockAB", 0.0));
        d_T.add(new PetriT("unlockB", 0.0));
        //sleep
        d_T.add(new PetriT("for[", delay));
        d_T.get(10).setDistribution("norm", d_T.get(10).getTimeServ() * 0.1);
        d_T.add(new PetriT("for]", 0.0 + r.nextDouble()));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(1), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(2), 1));
        d_In.add(new ArcIn(d_P.get(2), d_T.get(1), 1));
        d_In.add(new ArcIn(d_P.get(3), d_T.get(3), 1));
        d_In.add(new ArcIn(d_P.get(7), d_T.get(3), 1));
        d_In.add(new ArcIn(d_P.get(5), d_T.get(4), 1));
        d_In.add(new ArcIn(d_P.get(3), d_T.get(5), 1));
        d_In.add(new ArcIn(d_P.get(4), d_T.get(6), 1));
        d_In.add(new ArcIn(d_P.get(4), d_T.get(7), 1));
        d_In.add(new ArcIn(d_P.get(7), d_T.get(6), 1));
        d_In.add(new ArcIn(d_P.get(9), d_T.get(8), 1));
        d_In.add(new ArcIn(d_P.get(11), d_T.get(9), 1));
        d_In.add(new ArcIn(d_P.get(12), d_T.get(10), 1));
        d_In.add(new ArcIn(d_P.get(13), d_T.get(11), 1));
        d_In.add(new ArcIn(d_P.get(14), d_T.get(10), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(1), d_P.get(3), 1));
        d_Out.add(new ArcOut(d_T.get(2), d_P.get(4), 1));
        d_Out.add(new ArcOut(d_T.get(3), d_P.get(5), 1));
        d_Out.add(new ArcOut(d_T.get(5), d_P.get(2), 1));
        d_Out.add(new ArcOut(d_T.get(4), d_P.get(9), 1));
        d_Out.add(new ArcOut(d_T.get(7), d_P.get(6), 1));
        d_Out.add(new ArcOut(d_T.get(3), d_P.get(8), 1));
        d_Out.add(new ArcOut(d_T.get(4), d_P.get(10), 1));
        d_Out.add(new ArcOut(d_T.get(8), d_P.get(2), 1));
        d_Out.add(new ArcOut(d_T.get(8), d_P.get(7), 1));
        d_Out.add(new ArcOut(d_T.get(6), d_P.get(11), 1));
        d_Out.add(new ArcOut(d_T.get(9), d_P.get(7), 1));
        d_Out.add(new ArcOut(d_T.get(10), d_P.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(8), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(11), d_P.get(14), 1));
        d_Out.add(new ArcOut(d_T.get(5), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(9), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(7), d_P.get(13), 1));
        d_Out.add(new ArcOut(d_T.get(9), d_P.get(6), 1));
        d_Out.add(new ArcOut(d_T.get(5), d_P.get(6), 1));
        PetriNet d_Net = new PetriNet("Friend ", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetTestInfArc() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 1));
        d_P.add(new PetriP("P2", 1));
        d_P.add(new PetriP("P3", 0));
        d_P.add(new PetriP("P4", 0));
        d_T.add(new PetriT("T1", 1.0));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(2), d_T.get(0), 1));
        d_In.get(1).setInf(true);
        d_In.add(new ArcIn(d_P.get(1), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(3), 1));
        PetriNet d_Net = new PetriNet("TestInfArc", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetTestStatistics() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 100));
        d_P.add(new PetriP("P2", 1));
        d_P.add(new PetriP("P3", 0));
        d_P.add(new PetriP("P4", 0));
        d_P.add(new PetriP("P5", 1));
        d_T.add(new PetriT("T1", 10.0));
        d_T.add(new PetriT("T2", 5.0));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(2), d_T.get(1), 1));
        d_In.add(new ArcIn(d_P.get(4), d_T.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(2), 1));
        d_Out.add(new ArcOut(d_T.get(1), d_P.get(3), 1));
        d_Out.add(new ArcOut(d_T.get(1), d_P.get(4), 1));
        PetriNet d_Net = new PetriNet("TestStatistics", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    public static PetriNet CreateNetTask(double a) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 1000));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("Resource", 0));
        d_T.add(new PetriT("T1", a));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        for (PetriT tr : d_T) {
            d_In.add(new ArcIn(d_P.get(2), tr, 1));
            d_Out.add(new ArcOut(tr, d_P.get(2), 1));
        }
        PetriNet d_Net = new PetriNet("Task", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetGeneratorInf() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 1));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("P1", 0));
        d_T.add(new PetriT("T1", 2.0));
        d_T.get(0).setDistribution("exp", d_T.get(0).getTimeServ());
        d_T.get(0).setParamDeviation(0.0);
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(2), d_T.get(0), 1));
        d_In.get(1).setInf(true);
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(0), 1));
        PetriNet d_Net = new PetriNet("Generator", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetSimple() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 100));
        d_P.add(new PetriP("P2", 0));
        d_T.add(new PetriT("T1", 2.0));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(1), 1));
        PetriNet d_Net = new PetriNet("Simple", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNet() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        PetriNet d_Net = new PetriNet("", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetUntitled() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 0));
        PetriNet d_Net = new PetriNet("Untitled", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetUntitledHappy() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 0));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("P4", 0));
        d_T.add(new PetriT("T1", 0.0));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(2), 1));
        PetriNet d_Net = new PetriNet("UntitledHappy", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet CreateNetNewFeature() throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();
        d_P.add(new PetriP("P1", 0));
        d_P.add(new PetriP("P2", 0));
        d_P.add(new PetriP("P3", 0));
        d_P.add(new PetriP("P4", 0));
        d_T.add(new PetriT("T1", 0.0));
        d_In.add(new ArcIn(d_P.get(0), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(1), d_T.get(0), 1));
        d_In.add(new ArcIn(d_P.get(2), d_T.get(0), 1));
        d_Out.add(new ArcOut(d_T.get(0), d_P.get(3), 1));
        PetriNet d_Net = new PetriNet("NewFeature", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return d_Net;
    }

    @NetLibraryMethod
    public static PetriNet create_net() throws ExceptionInvalidTimeDelay {
        final ArrayList<PetriP> d_P = new ArrayList<PetriP>();
        final ArrayList<PetriT> d_T = new ArrayList<PetriT>();
        final ArrayList<ArcIn> d_In = new ArrayList<ArcIn>();
        final ArrayList<ArcOut> d_Out = new ArrayList<ArcOut>();

        final PetriP generated_task = create_task_generator(d_P, d_T, d_In, d_Out);
//        final PetriP generated_io_request = create_io_request_generator(d_P, d_T, d_In, d_Out);
//        final PetriP generated_interrupt = create_interrupt_generator(d_P, d_T, d_In, d_Out);
//
        final PetriP processors = create_processors(d_P);
        final PetriP pages = create_pages(d_P);
//        final PetriP free_disks = create_free_disks(d_P);
//        final PetriP busy_disks = create_busy_disks(d_P);
//
//        final PetriP disk_placed = new PetriP("disk_placed");
//        d_P.add(disk_placed);
//        final PetriP finished_tasks = new PetriP("finished_tasks");
//        d_P.add(finished_tasks);
//        final PetriP is_disk_placement_available = new PetriP("is_disk_placement_available", 1);
//        d_P.add(is_disk_placement_available);
//
//        final PetriT place_disk = new PetriT("place_disk", 0.0375);
//        place_disk.setDistribution("unif", place_disk.getTimeServ());
//        place_disk.setParamDeviation(0.021650635);
//        d_T.add(place_disk);
//        d_In.add(new ArcIn(is_disk_placement_available, place_disk));
//        d_In.add(new ArcIn(busy_disks, place_disk));
//        d_Out.add(new ArcOut(place_disk, disk_placed, 1));
//
//        final PetriT io_channel_transfer = new PetriT("io_channel_transfer", 0.015);
//        io_channel_transfer.setDistribution("unif", io_channel_transfer.getTimeServ());
//        io_channel_transfer.setParamDeviation(0.007216878);
//        d_T.add(place_disk);
//        d_In.add(new ArcIn(disk_placed, io_channel_transfer));
//        d_Out.add(new ArcOut(io_channel_transfer, is_disk_placement_available, 1));
//        d_Out.add(new ArcOut(io_channel_transfer, finished_tasks, 1));

        final int pages_start = 20;
        final int pages_end = 22;
        final double probability = 1.0 / (double)(pages_end - pages_start);

        IntStream.rangeClosed(pages_start, pages_end).forEach((pages_count) -> {
            final String task_n_pages_name = "task_".concat(Integer.toString(pages_count)).concat("_pages");
            final int priority = pages_end - pages_count;

            final PetriT generate_task_n_pages = new PetriT("generate_".concat(task_n_pages_name));
            generate_task_n_pages.setProbability(probability);
            d_T.add(generate_task_n_pages);
            d_In.add(new ArcIn(generated_task, generate_task_n_pages));

            final PetriP task_n_pages = new PetriP(task_n_pages_name);
            d_P.add(task_n_pages);
            d_Out.add(new ArcOut(generate_task_n_pages, task_n_pages, 1));

            final PetriT process_task_n = new PetriT("process_".concat(task_n_pages_name), 10.0);
            process_task_n.setDistribution("norm", process_task_n.getTimeServ());
            process_task_n.setParamDeviation(3.0);
            process_task_n.setPriority(priority);
            d_T.add(process_task_n);
            d_In.add(new ArcIn(task_n_pages, process_task_n));
            d_In.add(new ArcIn(processors, process_task_n));
            d_In.add(new ArcIn(pages, process_task_n, pages_count));

            final PetriP processed_task_n = new PetriP("processed_".concat(task_n_pages_name));
            d_P.add(processed_task_n);
            d_Out.add(new ArcOut(process_task_n, processed_task_n, 1));
//
//            final PetriT create_io_task_n = new PetriT("create_io_".concat(task_n_pages_name));
//            create_io_task_n.setPriority(priority);
//            d_T.add(create_io_task_n);
//            d_In.add(new ArcIn(processed_task_n, create_io_task_n));
//            d_In.add(new ArcIn(generated_io_request, create_io_task_n));
//
//            final PetriP io_task_n = new PetriP("io_".concat(task_n_pages_name));
//            d_Out.add(new ArcOut(create_io_task_n, io_task_n, 1));
//
//            final PetriT take_up_disks_task_n = new PetriT("take_up_disks_".concat(task_n_pages_name));
//            take_up_disks_task_n.setPriority(priority);
//            d_In.add(new ArcIn(io_task_n, take_up_disks_task_n));
//            d_In.add(new ArcIn(generated_interrupt, take_up_disks_task_n));
//            d_In.add(new ArcIn(free_disks, take_up_disks_task_n));
//
//            d_Out.add(new ArcOut(take_up_disks_task_n, processors, 1));
//            d_Out.add(new ArcOut(take_up_disks_task_n, busy_disks, 1));
//            d_Out.add(new ArcOut(take_up_disks_task_n, pages, pages_count));
        });

        PetriNet d_Net = new PetriNet("CourseWork", d_P, d_T, d_In, d_Out);
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        return d_Net;
    }

    private static PetriP create_task_generator(
            ArrayList<PetriP> d_P,
            ArrayList<PetriT> d_T,
            ArrayList<ArcIn> d_In,
            ArrayList<ArcOut> d_Out
    ) {
        final PetriP task_generator = new PetriP("generator_task", 1);
        d_P.add(task_generator);

        final PetriT generate_task = new PetriT("generate_task", 5.0);
        d_T.add(generate_task);
        d_In.add(new ArcIn(task_generator, generate_task, 1));
        d_Out.add(new ArcOut(generate_task, task_generator, 1));

        final PetriP generated_task = new PetriP("generated_task", 0);
        d_P.add(generated_task);
        d_Out.add(new ArcOut(generate_task, generated_task, 1));
        return generated_task;
    }

    private static PetriP create_processors(ArrayList<PetriP> d_P) {
        final PetriP processors = new PetriP("processors", 2);
        d_P.add(processors);
        return processors;
    }

    private static PetriP create_pages(ArrayList<PetriP> d_P) {
        final PetriP pages = new PetriP("pages", 131);
        d_P.add(pages);
        return pages;
    }

    private static PetriP create_free_disks(ArrayList<PetriP> d_P) {
        final PetriP disks = new PetriP("free_disks", 4);
        d_P.add(disks);
        return disks;
    }

    private static PetriP create_busy_disks(ArrayList<PetriP> d_P) {
        final PetriP disks = new PetriP("busy_disks", 0);
        d_P.add(disks);
        return disks;
    }

    private static PetriP create_io_request_generator(
            ArrayList<PetriP> d_P,
            ArrayList<PetriT> d_T,
            ArrayList<ArcIn> d_In,
            ArrayList<ArcOut> d_Out
    ) {
        final PetriP generator_io_request = new PetriP("generator_io_request", 1);
        d_P.add(generator_io_request);

        final PetriT generate_io_request = new PetriT("generate_io_request", 6.0);
        generate_io_request.setDistribution("unif", generate_io_request.getTimeServ());
        generate_io_request.setParamDeviation(5.33);

        d_T.add(generate_io_request);
        d_In.add(new ArcIn(generator_io_request, generate_io_request, 1));
        d_Out.add(new ArcOut(generate_io_request, generator_io_request, 1));

        final PetriP generated_io_request = new PetriP("generated_io_request", 0);
        d_P.add(generator_io_request);
        d_Out.add(new ArcOut(generate_io_request, generated_io_request, 1));
        return generated_io_request;
    }

    private static PetriP create_interrupt_generator(
            ArrayList<PetriP> d_P,
            ArrayList<PetriT> d_T,
            ArrayList<ArcIn> d_In,
            ArrayList<ArcOut> d_Out
    ) {
        final PetriP generator_interrupt = new PetriP("generator_interrupt", 1);
        d_P.add(generator_interrupt);

        final PetriT generate_interrupt = new PetriT("generate_interrupt", 6.0);
        generate_interrupt.setDistribution("exp", generate_interrupt.getTimeServ());
        d_T.add(generate_interrupt);
        d_In.add(new ArcIn(generator_interrupt, generate_interrupt, 1));
        d_Out.add(new ArcOut(generate_interrupt, generator_interrupt, 1));

        final PetriP generated_interrupt = new PetriP("generated_interrupt", 0);
        d_P.add(generator_interrupt);
        d_Out.add(new ArcOut(generate_interrupt, generated_interrupt, 1));

        final PetriT drop_interrupt = new PetriT("drop_interrupt", 0.0);
        drop_interrupt.setPriority(Integer.MIN_VALUE);
        d_In.add(new ArcIn(generated_interrupt, drop_interrupt));

        final PetriP drop_counter = new PetriP("drop_counter", 0);
        d_Out.add(new ArcOut(drop_interrupt, drop_counter, 1));

        return generated_interrupt;
    }
}
