package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.LibNet.NetLibraryManager;
import ua.stetsenkoinna.PetriObj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.PetriObj.ArcIn;
import ua.stetsenkoinna.PetriObj.ArcOut;
import ua.stetsenkoinna.PetriObj.ExceptionInvalidTimeDelay;

import java.awt.FileDialog;

import ua.stetsenkoinna.PetriObj.PetriMainElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.Point;

import javax.swing.*;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import java.awt.Component;

import java.awt.geom.Point2D;
import java.util.Objects;

/**
 *
 * @author Olya &  Inna
 */
public class FileUse {

    private static final String PATTERN = ".pns";
    
    public static String openFile(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure {
        String pnetName = "";
        FileDialog fdlg;
        fdlg = new FileDialog(frame, "Open a file ",
                FileDialog.LOAD);
        fdlg.setVisible(true);
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            // System.out.println("Opening file '" + fdlg.getDirectory() + fdlg.getFile() + "'");
            fis = new FileInputStream(fdlg.getDirectory() + fdlg.getFile());
            ois = new ObjectInputStream(fis);
            GraphPetriNet net = ((GraphPetriNet) ois.readObject()).clone();
            
            // if there are transitions where b != 0, find them and
            // ask the user if they want to remove exit times from buffers
            GraphPetriTransition[] tWithNon0Buffers =  net.getGraphPetriTransitionList().stream()
                    .filter(
                            transition -> transition.getPetriTransition().getBuffer() != 0)
                    
                    .toArray(GraphPetriTransition[]::new);
            if (tWithNon0Buffers.length != 0) {
                // display dialog
                int result = JOptionPane.showConfirmDialog((Component) null, "There are transitions in this net with non-empty buffers. Do you want to clear them?",
                                "Buffers reset", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    for (GraphPetriTransition trans : tWithNon0Buffers) {
                        // removing all saved exit times 
                        trans.getPetriTransition().getTimeOut().clear();
                        trans.getPetriTransition().getTimeOut().add(Double.MAX_VALUE);
                        trans.getPetriTransition().setBuffer(0);
                    }
                }
            }
            
            panel.addGraphNet(net);
            pnetName = net.getPetriNet().getName();
            ois.close();
            panel.repaint();

        } catch (FileNotFoundException e) {
            System.out.println("Such file was not found");
        } catch (ClassNotFoundException | IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (CloneNotSupportedException ex) {
            Logger.getLogger(FileUse.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fis.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException e) {
                return null;
            }
            try {
                ois.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NullPointerException e) {
                return null;
            }

        }
        return pnetName.substring(0, pnetName.length());
    }

    public static void newWorksheet(PetriNetsPanel panel) {
        panel.setNullPanel();
    }

    public static void saveGraphNetAs(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        FileDialog fdlg;
        fdlg = new FileDialog(frame,
                "Save Graph Petri net as...",
                FileDialog.SAVE);
        fdlg.setVisible(true);
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fdlg.setFilenameFilter(null);
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            panel.getGraphNet().createPetriNet(fdlg.getFile());
            oos.writeObject(panel.getGraphNet());
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void saveGraphNetAs(GraphPetriNet net, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        FileDialog fdlg;
        fdlg = new FileDialog(frame,
                "Save Graph Petri net as...",
                FileDialog.SAVE);
        fdlg.setVisible(true);
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fdlg.setFilenameFilter(null);
            //System.out.println("Saving GraphNet as '" + fdlg.getDirectory() + fdlg.getFile() + "'");
            net.createPetriNet(fdlg.getFile());
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(net);
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static void savePetriNetAs(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        FileDialog fdlg;
        fdlg = new FileDialog(frame,
                "Save Petri net as...",
                FileDialog.SAVE);
        fdlg.setVisible(true);
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fdlg.setFilenameFilter(null);
            // System.out.println("Saving PetriNet as '" + fdlg.getDirectory() + fdlg.getFile() + "'");
            panel.getGraphNet().createPetriNet(fdlg.getFile());
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(panel.getGraphNet().getPetriNet());
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static boolean saveGraphNet(GraphPetriNet pnet, String name) throws ExceptionInvalidNetStructure {  // saving graph in the same folder as project
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        if (name.equalsIgnoreCase("")) {
            name = "Untitled";
        }

        try {
            pnet.createPetriNet(name);
            File file = new File(name + ".pns");
            // System.out.println("Saving path = " + file.getAbsolutePath());
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(pnet);
            oos.close();
        } catch (IOException ex) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            Logger.getLogger(FileUse.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }

    public static GraphPetriNet generateGraphNetBySimpleNet(PetriNetsPanel panel, PetriNet net, Point paneCenter) { // added by Katya 16.10.2016
        GraphPetriNet currentNet = panel.getGraphNet();
        List<GraphElement> choosenElements = panel.getChoosenElements();
        choosenElements.clear();
        ArrayList<GraphPetriPlace> grPlaces = currentNet.getGraphPetriPlaceList();
        ArrayList<GraphPetriTransition> grTransitions = currentNet.getGraphPetriTransitionList();
        ArrayList<GraphArcIn> grArcIns = currentNet.getGraphArcInList();
        ArrayList<GraphArcOut> grArcOuts = currentNet.getGraphArcOutList();

        ArrayList<PetriP> availPetriPlaces = new ArrayList<>(Arrays.asList(net.getListP())); // modified by Katya 20.11.2016 (including the "while" and 1st "for" loop)
        ArrayList<PetriT> availPetriTrans = new ArrayList<>(Arrays.asList(net.getListT()));
        ArrayList<VerticalSet> sets = new ArrayList<>();

        // first transition
        PetriT firstTran = availPetriTrans.remove(0);
        VerticalSet firstSet = new VerticalSet(false);
        firstSet.AddElement(firstTran);
        sets.add(firstSet);

        while (!availPetriPlaces.isEmpty() || !availPetriTrans.isEmpty()) {
            // step
            VerticalSet lastSet = null;
            int lastSetIndex = 0;
            for (VerticalSet set : sets) {
                if (!set.GetReadyStatus()) {
                    lastSet = set;
                    lastSetIndex = sets.indexOf(lastSet);
                    break;
                }
            }
            if (lastSet == null) {
                break;
            }
            if (lastSet.IsForPlaces()) {
                // new transitions
                ArrayList<PetriT> inTrans = new ArrayList<>();
                for (ArcOut outArc : net.getArcOut()) {
                    for (PetriMainElement placeElem : lastSet.GetElements()) {
                        PetriP place = (PetriP) placeElem;
                        if (place.getNumber() == outArc.getNumP()) {
                            for (PetriT tran : availPetriTrans) {
                                if (!inTrans.contains(tran) && tran.getNumber() == outArc.getNumT()) {
                                    inTrans.add(tran);
                                }
                            }
                        }
                    }
                }
                ArrayList<PetriT> outTrans = new ArrayList<>();
                for (ArcIn inArc : net.getArcIn()) {
                    for (PetriMainElement placeElem : lastSet.GetElements()) {
                        PetriP place = (PetriP) placeElem;
                        if (place.getNumber() == inArc.getNumP()) {
                            for (PetriT tran : availPetriTrans) {
                                if (!inTrans.contains(tran) && !outTrans.contains(tran) && tran.getNumber() == inArc.getNumT()) { // modified by Katya 08.12.2016
                                    outTrans.add(tran);
                                }
                            }
                        }
                    }
                }

                if (!inTrans.isEmpty()) {
                    if (lastSetIndex == 0) {
                        sets.add(0, new VerticalSet(!lastSet.IsForPlaces()));
                        lastSetIndex = 1;
                    }
                }
                if (!outTrans.isEmpty()) {
                    if (sets.size() == (lastSetIndex + 1)) {
                        sets.add(new VerticalSet(!lastSet.IsForPlaces()));
                    }
                }

                for (PetriT tran : inTrans) {
                    sets.get(lastSetIndex - 1).AddElement(tran);
                    sets.get(lastSetIndex - 1).SetAsNotReady();
                    availPetriTrans.remove(tran);
                }
                for (PetriT tran : outTrans) {
                    sets.get(lastSetIndex + 1).AddElement(tran);
                    sets.get(lastSetIndex + 1).SetAsNotReady();
                    availPetriTrans.remove(tran);
                }
            } else {
                // new places
                ArrayList<PetriP> inPlaces = new ArrayList<>();
                for (ArcIn inArc : net.getArcIn()) {
                    for (PetriMainElement tranElem : lastSet.GetElements()) {
                        PetriT tran = (PetriT) tranElem;
                        if (tran.getNumber() == inArc.getNumT()) {
                            for (PetriP place : availPetriPlaces) {
                                if (!inPlaces.contains(place) && place.getNumber() == inArc.getNumP()) {
                                    inPlaces.add(place);
                                }
                            }
                        }
                    }
                }
                ArrayList<PetriP> outPlaces = new ArrayList<>();
                for (ArcOut outArc : net.getArcOut()) {
                    for (PetriMainElement tranElem : lastSet.GetElements()) {
                        PetriT tran = (PetriT) tranElem;
                        if (tran.getNumber() == outArc.getNumT()) {
                            for (PetriP place : availPetriPlaces) {
                                if (!inPlaces.contains(place) && !outPlaces.contains(place) && place.getNumber() == outArc.getNumP()) { // modified by Katya 08.12.2016
                                    outPlaces.add(place);
                                }
                            }
                        }
                    }
                }

                if (!inPlaces.isEmpty()) {
                    if (lastSetIndex == 0) {
                        sets.add(0, new VerticalSet(!lastSet.IsForPlaces()));
                        lastSetIndex = 1;
                    }
                }
                if (!outPlaces.isEmpty()) {
                    if (sets.size() == (lastSetIndex + 1)) {
                        sets.add(new VerticalSet(!lastSet.IsForPlaces()));
                    }
                }

                for (PetriP place : inPlaces) {
                    sets.get(lastSetIndex - 1).AddElement(place);
                    sets.get(lastSetIndex - 1).SetAsNotReady();
                    availPetriPlaces.remove(place);
                }
                for (PetriP place : outPlaces) {
                    sets.get(lastSetIndex + 1).AddElement(place);
                    sets.get(lastSetIndex + 1).SetAsNotReady();
                    availPetriPlaces.remove(place);
                }
            }

            lastSet.SetAsReady();
        }

        double x = 0, y = 0;

        boolean hasLoops = false; // "hasLoops" added by Katya 04.12.2016
        firstSet = sets.get(0);
        VerticalSet lastSet = sets.get(sets.size() - 1);
        if (!Objects.equals(lastSet.IsForPlaces(), firstSet.IsForPlaces())) {
            VerticalSet setWithPlaces = firstSet.IsForPlaces() ? firstSet : lastSet;
            VerticalSet setWithTrans = firstSet.IsForPlaces() ? lastSet : firstSet;
            for (ArcIn arc : net.getArcIn()) {
                boolean isInSetWithPlaces = false;
                boolean isInSetWithTrans = false;
                for (PetriMainElement placeElem : setWithPlaces.GetElements()) {
                    PetriP place = (PetriP)placeElem;
                    if (place.getNumber() == arc.getNumP()) {
                        isInSetWithPlaces = true;
                        break;
                    }
                }
                for (PetriMainElement tranElem : setWithTrans.GetElements()) {
                    PetriT tran = (PetriT)tranElem;
                    if (tran.getNumber() == arc.getNumT()) {
                        isInSetWithTrans = true;
                        break;
                    }
                }
                if (isInSetWithPlaces && isInSetWithTrans) {
                    hasLoops = true;
                    break;
                }
            }
            if (!hasLoops) {
                for (ArcOut arc : net.getArcOut()) {
                    boolean isInSetWithPlaces = false;
                    boolean isInSetWithTrans = false;
                    for (PetriMainElement placeElem : setWithPlaces.GetElements()) {
                        PetriP place = (PetriP)placeElem;
                        if (place.getNumber() == arc.getNumP()) {
                            isInSetWithPlaces = true;
                            break;
                        }
                    }
                    for (PetriMainElement tranElem : setWithTrans.GetElements()) {
                        PetriT tran = (PetriT)tranElem;
                        if (tran.getNumber() == arc.getNumT()) {
                            isInSetWithTrans = true;
                            break;
                        }
                    }
                    if (isInSetWithPlaces && isInSetWithTrans) {
                        hasLoops = true;
                        break;
                    }
                }
            }
        }

        if (!hasLoops) {
            for (VerticalSet set : sets) {
                ArrayList<PetriMainElement> elements = set.GetElements();
                int size = elements.size();
                x += 80;
                y = ((size % 2) == 0) ? (- (size / 2 * 80) - 40) : (- (size / 2 * 80) - 80);
                for (PetriMainElement elem : elements) {
                    y += 80;
                    if (set.IsForPlaces()) {
                        PetriP place = (PetriP)elem;
                        GraphPetriPlace grPlace = new GraphPetriPlace(place, PetriNetsPanel.getIdElement());
                        grPlace.setNewCoordinates(new Point2D.Double(x, y));
                        grPlaces.add(grPlace);
                        //choosenElements.add(grPlace);
                    } else {
                        PetriT tran = (PetriT)elem;
                        GraphPetriTransition grTran = new GraphPetriTransition(tran, PetriNetsPanel.getIdElement());
                        grTran.setNewCoordinates(new Point2D.Double(x, y));
                        grTransitions.add(grTran);
                        //choosenElements.add(grTran);
                    }
                }
            }
        } else {
            int numberOfSets = sets.size();
            int numberOfFirstGroupSets = numberOfSets / 2;
            for (int i = 0; i < numberOfFirstGroupSets; i++) {
                VerticalSet set = sets.get(i);
                ArrayList<PetriMainElement> elements = set.GetElements();
                int size = elements.size();
                x += 80;
                y = ((size % 2) == 0) ? (- (size / 2 * 80) - 40) : (- (size / 2 * 80) - 80);
                for (PetriMainElement elem : elements) {
                    y += 80;
                    if (set.IsForPlaces()) {
                        PetriP place = (PetriP)elem;
                        GraphPetriPlace grPlace = new GraphPetriPlace(place, PetriNetsPanel.getIdElement());
                        grPlace.setNewCoordinates(new Point2D.Double(x, y));
                        grPlaces.add(grPlace);
                        //choosenElements.add(grPlace);
                    } else {
                        PetriT tran = (PetriT)elem;
                        GraphPetriTransition grTran = new GraphPetriTransition(tran, PetriNetsPanel.getIdElement());
                        grTran.setNewCoordinates(new Point2D.Double(x, y));
                        grTransitions.add(grTran);
                        //choosenElements.add(grTran);
                    }
                }
            }
            x += 80;

            for (int i = numberOfFirstGroupSets; i < numberOfSets; i++) {
                VerticalSet set = sets.get(i);
                ArrayList<PetriMainElement> elements = set.GetElements();
                int size = elements.size();
                x -= 80;
                y = ((size % 2) == 0) ? (- (size / 2 * 80) - 40) : (- (size / 2 * 80) - 80);
                y += 160;

                for (PetriMainElement elem : elements) {
                    y += 80;
                    if (set.IsForPlaces()) {
                        PetriP place = (PetriP)elem;
                        GraphPetriPlace grPlace = new GraphPetriPlace(place, PetriNetsPanel.getIdElement());
                        grPlace.setNewCoordinates(new Point2D.Double(x, y));
                        grPlaces.add(grPlace);
                        //choosenElements.add(grPlace);
                    } else {
                        PetriT tran = (PetriT)elem;
                        GraphPetriTransition grTran = new GraphPetriTransition(tran, PetriNetsPanel.getIdElement());
                        grTran.setNewCoordinates(new Point2D.Double(x, y));
                        grTransitions.add(grTran);
                        // choosenElements.add(grTran);
                    }
                }
            }
        }

        for (ArcIn inArc : net.getArcIn()) {
            GraphArcIn grInArc = new GraphArcIn(inArc);
            GraphPetriTransition endTransition = null;
            for (GraphPetriTransition grTran : grTransitions) {
                if (grTran.getNumber() == inArc.getNumT()) {
                    endTransition = grTran;
                }
            }
            GraphPetriPlace beginPlace = null;
            for (GraphPetriPlace grPlace : grPlaces) {
                if (grPlace.getNumber() == inArc.getNumP()) {
                    beginPlace = grPlace;
                }
            }
            grInArc.settingNewArc(beginPlace);
            grInArc.finishSettingNewArc(endTransition);
            grInArc.setPetriElements(); // added by Katya 04.12.2016 (this line and the next two)
            grInArc.changeBorder();
            grInArc.updateCoordinates();
            grArcIns.add(grInArc);
        }

        for (ArcOut outArc : net.getArcOut()) {
            GraphArcOut grOutArc = new GraphArcOut(outArc);
            GraphPetriTransition beginTransition = null;
            for (GraphPetriTransition grTran : grTransitions) {
                if (grTran.getNumber() == outArc.getNumT()) {
                    beginTransition = grTran;
                }
            }
            GraphPetriPlace endPlace = null;
            for (GraphPetriPlace grPlace : grPlaces) {
                if (grPlace.getNumber() == outArc.getNumP()) {
                    endPlace = grPlace;
                }
            }
            grOutArc.settingNewArc(beginTransition);
            grOutArc.finishSettingNewArc(endPlace);
            grOutArc.setPetriElements(); // added by Katya 04.12.2016 (this line and the next two)
            grOutArc.changeBorder();
            grOutArc.updateCoordinates();
            grArcOuts.add(grOutArc);
        }

        // added by Katya 04.12.2016
        for (GraphArcOut arcOut : grArcOuts) {
            for (GraphArcIn arcIn : grArcIns) {
                int inBeginId = ((GraphPetriPlace)arcIn.getBeginElement()).getId();
                int inEndId = ((GraphPetriTransition)arcIn.getEndElement()).getId();
                int outBeginId = ((GraphPetriTransition)arcOut.getBeginElement()).getId();
                int outEndId = ((GraphPetriPlace)arcOut.getEndElement()).getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut);
                    arcIn.updateCoordinates();
                }
            }
        }
        GraphPetriNet graphNet = new GraphPetriNet(net, grPlaces, grTransitions, grArcIns, grArcOuts);

        graphNet.changeLocation(paneCenter);
        return graphNet;
    }

    public static String openMethod(
            final PetriNetsPanel panel,
            final String methodFullName,
            final JFrame frame,
            final NetLibraryManager netLibraryManager
    ) throws ExceptionInvalidNetStructure {
        String pnetName = "";
        try {
            final PetriNetsFrame petriNetsFrame = (PetriNetsFrame)frame;
            final JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
            final Point panelCenter = new Point(pane.getLocation().x + pane.getBounds().width / 2, pane.getLocation().y + pane.getBounds().height / 2);
            final GraphPetriNet net = generateGraphNetBySimpleNet(panel, netLibraryManager.callMethod(methodFullName), panelCenter);
            panel.addGraphNet(net);
            pnetName = net.getPetriNet().getName();
            panel.repaint();
        } catch (final Exception exception) {
            Logger.getLogger(FileUse.class.getName()).log(Level.SEVERE, null, exception);
        }
        return pnetName;
    }
    
    /**
     * Process the code of a method (including header and { }) to replace
     * numeric arguments with string parameter names
     * @param code original method code
     * @return processed code
     */
    /*private String preProcessMethod(String code) {
        return code;
    }*/
    private static String generateArgumentsString(PetriNet net) { // added by Katya 08.12.2016
        StringBuilder str = new StringBuilder();
        for (PetriP petriPlace : net.getListP()) {
            if (petriPlace.markIsParam()) {
                str.append("int ").append(petriPlace.getMarkParamName()).append(", ");
            }
        }

        for (ArcIn In : net.getArcIn()) {
            if (In.kIsParam()) {
                str.append("int ").append(In.getKParamName()).append(", ");
            }
            if (In.infIsParam()) {
                str.append("boolean ").append(In.getInfParamName()).append(", ");
            }
        }
        for (ArcOut Out : net.getArcOut()) {
            if (Out.kIsParam()) {
                str.append("int ").append(Out.getKParamName()).append(", ");
            }
        }
        for (PetriT T : net.getListT()) {
            if (T.parametrIsParam()) {
                str.append("double ").append(T.getParametrParamName()).append(", ");
            }
            if (T.distributionIsParam()) {
                str.append("String ").append(T.getDistributionParamName()).append(", ");
            }
            if (T.priorityIsParam()) {
                str.append("int ").append(T.getPriorityParamName()).append(", ");
            }
            if (T.probabilityIsParam()) {
                str.append("double ").append(T.getProbabilityParamName()).append(", ");
            }
        }
        if (str.length() > 2) {
            str = new StringBuilder(str.substring(0, str.length() - 2));
        }
        return str.toString();
    }

    public static void saveNetAsMethod(GraphPetriNet pnet, JTextArea area) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        PetriNet net;
        if (pnet.getPetriNet() == null) {
            pnet.createPetriNet("Untitled");
        }
        net = pnet.getPetriNet();
        area.setText("\n");
        area.append(
                "public static PetriNet CreateNet" + net.getName() + "(" + generateArgumentsString(net) + ") throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {\n" // modified by Katya 08.12.2016
                + "\t" + "ArrayList<PetriP> d_P = new ArrayList<>();\n"
                + "\t" + "ArrayList<PetriT> d_T = new ArrayList<>();\n"
                + "\t" + "ArrayList<ArcIn> d_In = new ArrayList<>();\n"
                + "\t" + "ArrayList<ArcOut> d_Out = new ArrayList<>();\n");

        for (PetriP P : net.getListP()) {
            String markStr = P.markIsParam() // added by Katya 08.12.2016
                ? P.getMarkParamName()
                : Integer.toString(P.getMark());
            area.append("\t" + "d_P.add(new PetriP(" + "\"" + P.getName() + "\"," + markStr + "));\n");
        }

        int j = 0;
        for (PetriT T : net.getListT()) {
            String parametrStr = T.parametrIsParam() // added by Katya 08.12.2016
                ? T.getParametrParamName()
                : Double.toString(T.getParametr());
            area.append("\t" + "d_T.add(new PetriT(" + "\"" + T.getName() + "\"," + parametrStr + "));\n");
            if (T.getDistribution() != null || T.distributionIsParam()) {
                String distributionStr = T.distributionIsParam() // added by Katya 08.12.2016
                    ? T.getDistributionParamName()
                    : T.getDistribution();
                area.append("\t" + "d_T.get(" + j + ").setDistribution(\"" + distributionStr + "\", d_T.get(" + j + ").getTimeServ());\n");
                area.append("\t" + "d_T.get(" + j + ").setParamDeviation(" + T.getParamDeviation() + ");\n");
            }
            if (T.getPriority() != 0 || T.priorityIsParam()) {
                String priorityStr = T.priorityIsParam() // added by Katya 08.12.2016
                    ? T.getPriorityParamName()
                    : Integer.toString(T.getPriority());
                area.append("\t" + "d_T.get(" + j + ").setPriority(" + priorityStr + ");\n");
            }
            if (T.getProbability() != 1.0 || T.probabilityIsParam()) {
                String probabilityStr = T.probabilityIsParam() // added by Katya 08.12.2016
                    ? T.getProbabilityParamName()
                    : Double.toString(T.getProbability());
                area.append("\t" + "d_T.get(" + j + ").setProbability(" + probabilityStr + ");\n");
            }
            j++;
        }

        j = 0;
        for (ArcIn In : net.getArcIn()) {
            String quantityStr = In.kIsParam() // added by Katya 08.12.2016
                ? In.getKParamName()
                : Integer.toString(In.getQuantity());
            area.append("\t" + "d_In.add(new ArcIn(" + "d_P.get(" + In.getNumP() + ")," + "d_T.get(" + In.getNumT() + ")," + quantityStr + "));\n");

            if (In.infIsParam()) { // modified by Katya 08.12.2016
                area.append("\t" + "d_In.get(" + j + ").setInf(" + In.getInfParamName() + ");\n");
            } else if (In.getIsInf()) {
                area.append("\t" + "d_In.get(" + j + ").setInf(true);\n");
            }
            j++;
        }

        for (ArcOut Out : net.getArcOut()) {
            String quantityStr = Out.kIsParam() // added by Katya 08.12.2016
                ? Out.getKParamName()
                : Integer.toString(Out.getQuantity());
            area.append("\t" + "d_Out.add(new ArcOut(" + "d_T.get(" + Out.getNumT() + ")," + "d_P.get(" + Out.getNumP() + ")," + quantityStr + "));\n");
        }

        area.append(
                "\t" + "PetriNet d_Net = new PetriNet(\"" + net.getName() + "\",d_P,d_T,d_In,d_Out);\n");

      //  area.append("\n\t" + "return d_Net;\n"); // modified by Katya 05.12.2016
         area.append(
                "\t" + "PetriP.initNext();\n"
                + "\t" + "PetriT.initNext();\n"
                + "\t" + "ArcIn.initNext();\n"
                + "\t" + "ArcOut.initNext();\n"
                + "\n\t" + "return d_Net;\n");


        area.append("}");
    }
}
