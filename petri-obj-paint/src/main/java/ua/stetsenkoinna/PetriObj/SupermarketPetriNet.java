import PetriObj.PetriNet;
import PetriObj.PetriP;
import PetriObj.PetriT;
import PetriObj.ArcIn;
import PetriObj.ArcOut;

public class SupermarketPetriNet {
    public static PetriNet createNet() {
        PetriP p1 = new PetriP("Queue", 10);
        PetriP p2 = new PetriP("FirstCashier", 0);
        PetriP p3 = new PetriP("SecondCashier", 0);
        PetriP p4 = new PetriP("ThirdCashier", 0);

        PetriT t1 = new PetriT("StartService1", 3.0);
        PetriT t2 = new PetriT("EndService1", 3.0);
        PetriT t3 = new PetriT("StartService2", 7.0);
        PetriT t4 = new PetriT("EndService2", 7.0);
        PetriT t5 = new PetriT("StartService3", 5.0);
        PetriT t6 = new PetriT("EndService3", 5.0);

        ArcIn arcIn1 = new ArcIn(p1, t1, 1);
        ArcOut arcOut1 = new ArcOut(t1, p2, 1);
        ArcIn arcIn2 = new ArcIn(p2, t2, 1);
        ArcOut arcOut2 = new ArcOut(t2, p1, 1);

        ArcIn arcIn3 = new ArcIn(p1, t3, 1);
        ArcOut arcOut3 = new ArcOut(t3, p3, 1);
        ArcIn arcIn4 = new ArcIn(p3, t4, 1);
        ArcOut arcOut4 = new ArcOut(t4, p1, 1);

        ArcIn arcIn5 = new ArcIn(p1, t5, 1);
        ArcOut arcOut5 = new ArcOut(t5, p4, 1);
        ArcIn arcIn6 = new ArcIn(p4, t6, 1);
        ArcOut arcOut6 = new ArcOut(t6, p1, 1);

        PetriNet net = new PetriNet("SupermarketPetriNet");
        net.addP(p1, p2, p3, p4);
        net.addT(t1, t2, t3, t4, t5, t6);
        net.addArc(arcIn1, arcOut1, arcIn2, arcOut2, arcIn3, arcOut3, arcIn4, arcOut4, arcIn5, arcOut5, arcIn6, arcOut6);

        return net;
    }
}
