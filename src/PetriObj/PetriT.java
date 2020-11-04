package PetriObj;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 * This class for creating the transition of Petri net
 *
 *  @author Inna V. Stetsenko
 */
public class PetriT extends PetriMainElement implements Cloneable, Serializable {

   // private static double timeModeling = Double.MAX_VALUE - 1;

    
    private String id; //  unique number for server
    private String name;

    private int buffer;
    private int priority;
    private double probability;

    private double minTime;
    private double timeServ;
    private double parametr; //середнє значення часу обслуговування
    private double paramDeviation; //середнє квадратичне відхилення часу обслуговування
    private String distribution;
    private ArrayList<Double> timeOut = new ArrayList<>();
    private ArrayList<Integer> inP = new ArrayList<>();
    private ArrayList<Integer> inPwithInf = new ArrayList<>();
    private ArrayList<Integer> quantIn = new ArrayList<>();
    private ArrayList<Integer> quantInwithInf = new ArrayList<>();
    private ArrayList<Integer> outP = new ArrayList<>();
    private ArrayList<Integer> quantOut = new ArrayList<>();

    private int num;  // номер каналу багатоканального переходу, що відповідає найближчий події
    private int number; // номер переходу за списком
    private double mean;  // спостережуване середнє значення кількості активних каналів переходу
    private int observedMax;
    private int observedMin;
    private static int next = 0; //додано 1.10.2012
    
    private ArrayList<Double> inMoments = new ArrayList<>();
    private ArrayList<Double> outMoments = new ArrayList<>();
    private boolean moments = false;
    
    // whether parametr, distribution, priority & probability are parameters; added by Katya 08.12.2016
    private boolean parametrIsParam = false;
    private boolean distributionIsParam = false;
    private boolean priorityIsParam = false;
    private boolean probabilityIsParam = false;
    // param names
    private String parametrParamName = null;
    private String distributionParamName = null;
    private String priorityParamName = null;
    private String probabilityParamName = null;

    /**
     *
     * @param n name of transition
     * @param tS timed delay
     */
    public PetriT(String n, double tS) {
        name = n;
        parametr = tS;
        paramDeviation = 0;
        timeServ = parametr;
        buffer = 0;

        minTime = Double.MAX_VALUE; // не очікується вихід маркерів переходу
        num = 0;
        mean = 0;
        observedMax = buffer;
        observedMin = buffer;
        priority = 0;
        probability = 1.0;
        distribution = null;
        number = next;
        next++;
        timeOut.add(Double.MAX_VALUE); // не очікується вихід маркерів з каналів переходу
        this.minEvent();
    }

    
     /**
     *
     * @param n name of transition
     * @param tS timed delay
     * @param prior value of priority
     */
    public PetriT(String n, double tS, int prior) { //changed by Inna 21.03.2018
        this(n, tS); 
      
        priority = prior;
    }

    /**
     *
     * @param n name of transition
     * @param tS timed delay
     * @param prob value of probability
     */
    public PetriT(String n, double tS, double prob) { //changed by Inna 21.03.2018
        this(n,tS);
        
         probability = prob;
    }
    /**
     *
     * @param n name of transition
     */
    public PetriT(String n) { //changed by Inna 21.03.2018
        this(n,0.0); //parametr = 0.0
    
    }

     /**
     *
     * @param id unique number for saving on server
     * @param n name of transition
     * @param tS timed delay
     */
    public PetriT(String id, String n, double tS) {
       this(n,tS);
       this.id = id;
    }

    /**
     * @param id unique number for saving on server*
     * @param n name of transition
     * @param tS timed delay
     * @param prior value of priority
     */
    public PetriT(String id, String n, double tS, int prior) { //changed by Inna 21.03.2018
        this(id, n, tS); 

        priority = prior;
    }

    public PetriT(PetriT transition) {
        this(transition.getName(), transition.getParametr());
        setDistribution(transition.getDistribution(), transition.getParametr());
        priority = transition.getPriority();
        probability = transition.getProbability();
        number = next;
        next++;
        buffer = transition.getBuffer();
        paramDeviation = transition.getParamDeviation();
    }

    /**
     * @param id unique number for saving on server*
     * @param n name of transition
     * @param tS timed delay
     * @param prob value of probability
     */
    public PetriT(String id, String n, double tS, double prob) { //changed by Inna 21.03.2018
        this(id, n,tS);
        
         probability = prob;
    }
    /**
     * @param id unique number for saving on server*
     * @param n name of transition
     */
    public PetriT(String id, String n) { //changed by Inna 21.03.2018
        this(id, n,0.0); //parametr = 0.0
    
    }

   
    public boolean parametrIsParam() {
        return parametrIsParam;
    }
    
    public boolean distributionIsParam() {
        return distributionIsParam;
    }
    
    public boolean priorityIsParam() {
        return priorityIsParam;
    }
    
    public boolean probabilityIsParam() {
        return probabilityIsParam;
    }
    
    public String getParametrParamName() {
        return parametrParamName;
    }
    
    public String getDistributionParamName() {
        return distributionParamName;
    }
    
    public String getPriorityParamName() {
        return priorityParamName;
    }
    
    public String getProbabilityParamName() {
        return probabilityParamName;
    }
    
    public void setParametrParam(String paramName) {
        if (paramName == null) {
            parametrIsParam = false;
            parametrParamName = null;
        } else {
            parametrIsParam = true;
            parametrParamName = paramName;
            parametr = 0.0;
        }
    }
    
    public void setDistributionParam(String paramName) {
        if (paramName == null) {
            distributionIsParam = false;
            distributionParamName = null;
        } else {
            distributionIsParam = true;
            distributionParamName = paramName;
            distribution = null;
        }
    }
    
    public void setPriorityParam(String paramName) {
        if (paramName == null) {
            priorityIsParam = false;
            priorityParamName = null;
        } else {
            priorityIsParam = true;
            priorityParamName = paramName;
            priority = 0;
        }
    }
    
    public void setProbabilityParam(String paramName) {
        if (paramName == null) {
            probabilityIsParam = false;
            probabilityParamName = null;
        } else {
            probabilityIsParam = true;
            probabilityParamName = paramName;
            probability = 1;
        }
    }
    /**
     * Set the counter of transitions to zero.
     */
    public static void initNext() { //ініціалізація лічильника нульовим значенням
         next = 0;
    }

    /**
     * Recalculates the mean value
     *
     * @param a value for recalculate of mean value (value equals product of
     * buffer and time divided by time modeling)
     */
    public void changeMean(double a) {//if(buffer>0)
        // mean=mean+buffer*a;
        mean = mean + (buffer - mean) * a;
    }
    /**
     * @return the timeOut
     */
    public ArrayList<Double> getTimeOut() {
        return timeOut;
    }

    /**
     *
     * @return mean value of quantity of markers
     */
    public double getMean() {
        return mean;
    }

    public double getObservedMax() {
        return observedMax;
    }

    public double getObservedMin() {
        return observedMin;
    }

    /**
     *
     * @return the value of priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Set the new value of priority
     *
     * @param r - the new value of priority
     */
    public void setPriority(int r) {
        priority = r;
    }

    /**
     *
     * @return the value of priority
     */
    public double getProbability() {
        return probability;
    }

    /**
     * Set the new value of probability
     *
     * @param v the value of probability
     */
    public void setProbability(double v) {
        probability = v;
    }

    /**
     *
     * @return the numbers of planed moments of markers outputs
     */
    public int getBuffer() {
        return buffer;
    }

    /**
     * This method sets the distribution of service time
     *
     * @param s the name of distribution as "exp", "norm", "unif". If <i>s</i>
     * equals null then the service time is determine value
     * @param param - the mean value of service time. If s equals null then the
     * service time equals <i>param</i>.
     */
    public void setDistribution(String s, double param) {
        distribution = s;
        parametr = param;
        timeServ = parametr; // додано 26.12.2011, тоді, якщо s==null, то передається час обслуговування
    }

    /**
     *
     * @return current value of service time
     */
    public double getTimeServ() {
        double a = timeServ;
        if (distribution != null) //додано 6 серпня
        {
            a = generateTimeServ();  // додано 6 серпня
        }
        if (a < 0) {
            JOptionPane.showMessageDialog(null, "Negative time delay was generated : time == " + a + ".\n Transition '" + this.name + "'.");
        }
        return a;

    }

    /**
     *
     * @return mean value of service time
     */
    public double getParametr() {
        return parametr;

    }

    /**
     * Set distribution parameter of service time
     *
     * @param p the mean value of service time
     */
    public void setParametr(double p) {
        parametr = p;  // додано 26.12.2011
        timeServ = parametr; // 20.11.2012 

    }

    /**
     * Generating the value of service time
     *
     * @return value of service time which has been generated
     */
    public double generateTimeServ() {
        try {
            if (distribution != null) {
                if (distribution.equalsIgnoreCase("exp")) {
                    timeServ = FunRand.exp(parametr);
                } else if (distribution.equalsIgnoreCase("unif")) {
                    timeServ = FunRand.unif(parametr - paramDeviation, parametr + paramDeviation);// 18.01.2013
                } else if (distribution.equalsIgnoreCase("norm")) {

                    timeServ = FunRand.norm(parametr, paramDeviation);// added 18.01.2013

                } else;
            } else {
                timeServ = parametr; // 20.11.2012 тобто детерміноване значення
            }
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(PetriT.class.getName()).log(Level.SEVERE, null, ex);
        }
        return timeServ;
    }

    /**
     *
     * @return the name of transition
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @param s name of transition
     */
    public void setName(String s) {
        name = s;
    }

    /**
     *
     * @return the time of nearest event
     */
    public double getMinTime() {
        this.minEvent();
        return minTime;
    }

    /**
     *
     * @return num the channel number of transition accordance to nearest event
     */
    public int getNum() {
        return num;
    }

    /**
     *
     * @return the number of transition
     */
    public int getNumber() {
        return number;
    }

    /**
     *
     * @param n - the number of place that is added to input places of
     * transition
     */
    public void addInP(int n) {
        inP.add(n);
    }

    /**
     *
     * @param n - the number of place that is added to output places of
     * transition
     */
    public void addOutP(int n) {
        getOutP().add(n);
    }

    /**
     * This method determines the places which is input for the transition. <br>
     * The class PetriNet use this method for creating net with given arrays of
     * places, transitions, input arcs and output arcs.
     *
     * @param inPP array of places
     * @param arcs array of input arcs
     * @throws PetriObj.ExceptionInvalidTimeDelay if Petri net has invalid structure
     */
    public void createInP(PetriP[] inPP, ArcIn[] arcs) throws ExceptionInvalidTimeDelay {
        inPwithInf.clear();    //додано 28.11.2012  список має бути порожнім!!!
        quantInwithInf.clear(); //додано 28.11.2012
        inP.clear();            //додано 28.11.2012
        quantIn.clear();        //додано 28.11.2012
        for (ArcIn arc: arcs) {
            if (arc.getNumT() == this.getNumber()) {
                if (arc.getIsInf() == true) {
                    inPwithInf.add(arc.getNumP());
                    quantInwithInf.add(arc.getQuantity());
                } else {
                    //if (arcs[j].getQuantity() > 0) { //вхідна позиція додається у разі позитивної кількості зв'язків, 9.11.2015
                    inP.add(arc.getNumP());
                    quantIn.add(arc.getQuantity());
                   // }
                }
            }
        }
        if (inP.isEmpty()) {
            throw new ExceptionInvalidTimeDelay("Transition " + this.getName() + " hasn't input positions!");
        }

    }

    /**
     * This method determines the places which is output for the transition.
     * <br>
     * The class PetriNet use this method for creating net with given arrays of
     * places, transitions, input arcs and output arcs.
     *
     * @param inPP array of places
     * @param arcs array of output arcs
     * @throws PetriObj.ExceptionInvalidTimeDelay if Petri net has invalid structure
     */
    public void createOutP(PetriP[] inPP, ArcOut[] arcs) throws ExceptionInvalidTimeDelay {
        getOutP().clear(); //додано 28.11.2012
        quantOut.clear();   //додано 28.11.2012
        for (ArcOut arc: arcs) {
            if ( arc.getNumT() == this.getNumber()) {
                getOutP().add(arc.getNumP());
                quantOut.add(arc.getQuantity());
            }
        }
        if (getOutP().isEmpty()) {
            throw new ExceptionInvalidTimeDelay("Transition " + this.getName() + " hasn't output positions!");
        }
    }

    /**
     *
     * @param n the channel number of transition accordance to nearest event
     */
    public void setNum(int n) {
        num = n;

    }

    /**
     *
     * @param n number of transition
     */
    public void setNumber(int n) {
        number = n;

    }

    /**
     * This method determines is firing condition of transition true.<br>
     * Condition is true if for each input place the quality of tokens in ....
     *
     * @param pp array of places of Petri net
     * @return true if firing condition is executed
     */
    public boolean condition(PetriP[] pp) { //Нумерація позицій тут відносна!!!  inP.get(i) - номер позиції у списку позицій, який побудований при конструюванні мережі Петрі, 

        boolean a = true;
        boolean b = true;  // Саме тому при з"єднанні спільних позицій зміна номера не призводить до трагічних наслідків (руйнування зв"язків)!!! 
        for (int i = 0; i < inP.size(); i++) {
            if (pp[inP.get(i)].getMark() < quantIn.get(i)) {
                a = false;
                break;
            }
        }
        for (int i = 0; i < inPwithInf.size(); i++) {
            if (pp[inPwithInf.get(i)].getMark() < quantInwithInf.get(i)) {
                b = false;
                break;
            }
        }
        return a == true && b == true;

    }

    /**
     * The firing transition consists of two actions - tokens input and
     * output.<br>
     * This method provides tokens input in the transition.
     *
     * @param pp array of Petri net places
     * @param currentTime current time
     */
    public void actIn(PetriP[] pp, double currentTime) {
        if (this.condition(pp) == true) {
            for (int i = 0; i < inP.size(); i++) {
                pp[inP.get(i)].decreaseMark(quantIn.get(i));
            }
            if (buffer == 0) {
                timeOut.set(0, currentTime + this.getTimeServ());
            } else {
                timeOut.add(currentTime + this.getTimeServ());
            }
            if(moments){
                inMoments.add(currentTime);
            }
            buffer++;
            if (observedMax < buffer) {
                observedMax = buffer;
            }

            this.minEvent();

        } else {
            //  System.out.println("Condition not true");
        }
    }

    /**
     * The firing transition consists of two actions - tokens input and
     * output.<br>
     * This method provides tokens output in the transition.
     *
     * @param pp array of Petri net places
     * @param currentTime current time
     */
    public void actOut(PetriP[] pp, double currentTime) {  // parameter current time ia added by Inna 11.07.2018 for protocol events
        if (buffer > 0) {
            for (int j = 0; j < getOutP().size(); j++) {
                pp[getOutP().get(j)].increaseMark(quantOut.get(j));
            }
            if (num == 0 && (timeOut.size() == 1)) {
                timeOut.set(0, Double.MAX_VALUE);
            } else {
                timeOut.remove(num);
            }
            if(moments){
                outMoments.add(currentTime);
            }
            buffer--;
            if (observedMin > buffer) {
                observedMin = buffer;
            }
        } else {
            // System.out.println("Buffer is null");
        }

    }

    /**
     * Determines the transition nearest event among the events of its tokens
     * outputs. and the number of transition channel
     */
    public final void minEvent() {
        minTime = Double.MAX_VALUE;
        if (timeOut.size() > 0) {
            for (int i = 0; i < timeOut.size(); i++) {
                if (timeOut.get(i) < minTime) {
                    minTime = timeOut.get(i);
                    num = i;
                }
            }
        }

    }

    /**
     *
     */
    public void print() {
        for (double time: timeOut) {
            System.out.println(time + "   " + this.getName());
        }
    }

    public void printParameters() {
        System.out.println("Transition " + name + " has such parameters: \n"
                + " number " + number + ", probability " + probability + ", priority " + priority
                + "\n parameter " + parametr + ", distribution " + distribution
                + ", time of service (generate) " + this.getTimeServ());
        System.out.println("This transition has input places with such numbers: ");
        for (Integer in : inP) {
            System.out.print(in.toString() + "  ");
        }
        System.out.println("\n and output places with such numbers: ");
        for (Integer out : getOutP()) {
            System.out.print(out.toString() + "  ");
        }
        System.out.println("\n");
    }

    /**
     *
     * @return list of transition input places
     */
    public ArrayList<Integer> getInP() {
        return inP;
    }

    /**
     *
     * @return list of transition output places
     */
    public ArrayList<Integer> getOutP() {
        return outP;
    }

    /**
     *
     * @return true if list of input places is empty
     */
    public boolean isEmptyInputPlacesList() {
        return inP.isEmpty();

    }

    /**
     *
     * @return true if list of output places is empty
     */
    public boolean isEmptyOutputPlacesList() {
        return getOutP().isEmpty();

    }

    /**
     *
     * @return PetriT object with parameters which copy current parameters of
     * this transition
     * @throws java.lang.CloneNotSupportedException if Petri net has invalid structure
     */
    @Override
    public PetriT clone() throws CloneNotSupportedException { // 30.11.2015
    
        super.clone();
        PetriT T = new PetriT(name, parametr);
        T.setDistribution(distribution, parametr);
        T.setPriority(priority);
        T.setProbability(probability);
        T.setNumber(number); //номер зберігається для відтворення зв"язків між копіями позицій та переходів
        T.setBuffer(buffer);
        T.setParamDeviation(paramDeviation);

        return T;

    }

    public void setBuffer(int buff) {
        buffer = buff;
    }

    public String getDistribution() {
        return distribution;
    }

    public double getParamDeviation() {
        return paramDeviation;
    }

    public void setParamDeviation(double parameter) {
        paramDeviation = parameter;
    }

    /**
     * @return the inMoments
     */
    public ArrayList<Double> getInMoments() {
        return inMoments;
    }

    /**
     * @param inMoments the inMoments to set
     */
    public void setInMoments(ArrayList<Double> inMoments) {
        this.inMoments = inMoments;
    }

    /**
     * @return the outMoments
     */
    public ArrayList<Double> getOutMoments() {
        return outMoments;
    }

    /**
     * @param outMoments the outMoments to set
     */
    public void setOutMoments(ArrayList<Double> outMoments) {
        this.outMoments = outMoments;
    }

    /**
     * @return the moments
     */
    public boolean isMoments() {
        return moments;
    }

    /**
     * @param moments the moments to set
     */
    public void setMoments(boolean moments) {
        this.moments = moments;
    }
    
    
    public class EventMoments{
       private List<Interval> list = new ArrayList<>();

        /**
         * @return the list
         */
        public List<Interval> getList() {
            return list;
        }

        /**
         * @param list the list to set
         */
        public void setList(List<Interval> list) {
            this.list = list;
        }
        
        class Interval{
            private Double in;
            private Double out;
            Interval(Double t1, Double t2){
                in = t1;
                out = t2;
            }

            /**
             * @return the in
             */
            public Double getIn() {
                return in;
            }

            /**
             * @return the out
             */
            public Double getOut() {
                return out;
            }
       }
       
        public EventMoments(List<Double> listIn, List<Double> listOut){
            int j;
            for(j=0; j< listOut.size(); j++){
                list.add( new Interval(listIn.get(j),listOut.get(j)) );
            }
            if(j<listIn.size()){
                for(j=listOut.size(); j<listIn.size();j++)
                    list.add(new Interval(listIn.get(j),Double.MAX_VALUE));
            }
     
        }
        
        
    } 
        
    public void printEventMoments() {
        if (moments) {
            EventMoments events = new EventMoments(inMoments, outMoments);
            System.out.println(this.getName());
            for (EventMoments.Interval interval : events.getList()) {
                System.out.println(interval.getIn() + "  " + interval.getOut());
            }
        } else{
            System.out.println("Set parameter 'moments' = true");
        }
    }
    
    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

}
