/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.stetsenkoinna.graphpresentation;

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
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.Point;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import ua.stetsenkoinna.config.FilePathConfig;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.utils.Utils;
import ua.stetsenkoinna.utils.MessageHelper;

import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.Objects;

/**
 *
 * @author Olya &  Inna
 */
public class FileUse {

    private final String PATTERN = ".pns";
    
    Class netLibraryClass;

    public String openFile(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure {
        String pnetName = "";
        FileDialog fdlg;
        fdlg = new FileDialog(frame, "Open a file ",
                FileDialog.LOAD);
        fdlg.setVisible(true);

        if (fdlg.getFile() == null) {
            return null; // User cancelled the dialog
        }

        String filePath = fdlg.getDirectory() + fdlg.getFile();
        File file = new File(filePath);

        // Validate file before attempting to read
        if (!file.exists()) {
            MessageHelper.showError(frame, "File does not exist: " + filePath);
            return null;
        }

        if (!file.canRead()) {
            MessageHelper.showError(frame, "Cannot read file: " + filePath);
            return null;
        }

        if (file.length() == 0) {
            MessageHelper.showError(frame, "File is empty: " + filePath);
            return null;
        }

        // Check if file is too small to contain a valid serialized object
        if (file.length() < 50) { // Minimum size for a serialized object
            MessageHelper.showError(frame,
                "File appears to be corrupted or incomplete (too small): " + filePath +
                "\nFile size: " + file.length() + " bytes");
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(fis)) {

            // System.out.println("Opening file '" + filePath + "'");
            Object loadedObject = ois.readObject();

            GraphPetriNet net;

            // Check if the loaded object is GraphPetriNet or PetriNet
            if (loadedObject instanceof GraphPetriNet) {
                net = ((GraphPetriNet) loadedObject).clone();
            } else if (loadedObject instanceof PetriNet) {
                // Convert PetriNet to GraphPetriNet
                PetriNet petriNet = (PetriNet) loadedObject;
                PetriNetsFrame petriNetsFrame = (PetriNetsFrame) frame;
                JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
                Point paneCenter = new Point(pane.getLocation().x + pane.getBounds().width / 2,
                                           pane.getLocation().y + pane.getBounds().height / 2);
                net = generateGraphNetBySimpleNet(panel, petriNet, paneCenter);
            } else {
                throw new ClassCastException("Unsupported file format. Expected GraphPetriNet or PetriNet, but found: "
                    + loadedObject.getClass().getName());
            }
            
            // if there are transitions where b != 0, find them and
            // ask the user if they want to remove exit times from buffers
            GraphPetriTransition[] tWithNon0Buffers =  net.getGraphPetriTransitionList().stream()
                    .filter(
                            transition -> transition.getPetriTransition().getBuffer() != 0)
                    
                    .toArray(GraphPetriTransition[]::new);
            if (tWithNon0Buffers.length != 0) {
                // display dialog
                if (MessageHelper.showConfirmation(frame,
                    "There are transitions in this net with non-empty buffers. Do you want to clear them?")) {
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
            panel.repaint();

        } catch (FileNotFoundException e) {
            MessageHelper.showException(frame, "File not found", e);
        } catch (ClassNotFoundException ex) {
            MessageHelper.showException(frame, "Cannot open file: incompatible file format or missing classes", ex);
        } catch (java.io.EOFException ex) {
            MessageHelper.showError(frame,
                "Error reading file: The file appears to be corrupted or incomplete.\n\n" +
                "Possible causes:\n" +
                "• File was not saved properly\n" +
                "• File was created with a different version of the application\n" +
                "• File was damaged or truncated\n" +
                "• Network interruption during file transfer\n\n" +
                "Please try:\n" +
                "• Using a backup copy of the file\n" +
                "• Re-saving the file from the original source\n" +
                "• Importing from PNML format instead (File → Import PNML)");
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, "EOF error during file reading", ex);
        } catch (IOException ex) {
            MessageHelper.showException(frame, "Error reading file", ex);
        } catch (CloneNotSupportedException ex) {
            MessageHelper.showException(frame, "Error processing file data", ex);
        } catch (ClassCastException ex) {
            MessageHelper.showException(frame, "Unsupported file format", ex);
        }
        return pnetName;
    }

    public void newWorksheet(PetriNetsPanel panel) {
        panel.setNullPanel();
    }

    public void saveGraphNetAs(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
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
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void saveGraphNetAs(GraphPetriNet net, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
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
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void savePetriNetAs(PetriNetsPanel panel, JFrame frame) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
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
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public boolean saveGraphNet(GraphPetriNet pnet, String name) throws ExceptionInvalidNetStructure {  // saving graph in the same folder as project
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        if (name.equalsIgnoreCase("")) {
            name = "Untitled";
        }

        try {
            pnet.createPetriNet(name);
            // Create temp directory if it doesn't exist
            File tempDir = new File("temp");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            File file = new File(tempDir, name + ".pns");
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
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return true;
    }

    public GraphPetriNet generateGraphNetBySimpleNet(PetriNetsPanel panel, PetriNet net, Point paneCenter) { // added by Katya 16.10.2016
        // Create new lists for the new GraphPetriNet instead of modifying existing ones
        ArrayList<GraphPetriPlace> grPlaces = new ArrayList<>();
        ArrayList<GraphPetriTransition> grTransitions = new ArrayList<>();
        ArrayList<GraphArcIn> grArcIns = new ArrayList<>();
        ArrayList<GraphArcOut> grArcOuts = new ArrayList<>();

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
                                if (tran.getNumber() == outArc.getNumT()) {
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
                                if (place.getNumber() == inArc.getNumP()) {
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

        double x = 0, y;

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
                y = ((size % 2) == 0) ? (- ((double) size / 2 * 80) - 40) : (- ((double) size / 2 * 80) - 80);
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
                y = ((size % 2) == 0) ? (- ((double) size / 2 * 80) - 40) : (- ((double) size / 2 * 80) - 80);
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
                y = ((size % 2) == 0) ? (- ((double) size / 2 * 80) - 40) : (- ((double) size / 2 * 80) - 80);
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
                int inBeginId = arcIn.getBeginElement().getId();
                int inEndId = arcIn.getEndElement().getId();
                int outBeginId = arcOut.getBeginElement().getId();
                int outEndId = arcOut.getEndElement().getId();
                if (inBeginId == outEndId && inEndId == outBeginId) {
                    arcIn.twoArcs(arcOut);
                    arcIn.updateCoordinates();
                }
            }
        }
        GraphPetriNet graphNet =  new GraphPetriNet(net, grPlaces, grTransitions, grArcIns, grArcOuts);

        graphNet.changeLocation(paneCenter);
        return graphNet;
    }

    @Deprecated
    public PetriNet convertMethodToPetriNet(String methodText) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay { // added by Katya 16.10.2016
        System.out.println(methodText);
             
        ArrayList<PetriP> d_P = new ArrayList<>();
        ArrayList<PetriT> d_T = new ArrayList<>();
        ArrayList<ArcIn> d_In = new ArrayList<>();
        ArrayList<ArcOut> d_Out = new ArrayList<>();

        String invalidMethodTextMessage = "Method text is invalid.";

        Pattern pattern = Pattern.compile(Pattern.quote("d_P.add(new PetriP(\"") + "(.*?)" + Pattern.quote("\",") + "(.*?)" + Pattern.quote("));"));
        Matcher matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            String pName = match1;
            String markStr = match2;
            int mark = Utils.tryParseInt(markStr) // added by Katya 08.12.2016
                ? Integer.parseInt(markStr)
                : 0;
            d_P.add(new PetriP(pName, mark));

            if (!Utils.tryParseInt(markStr)) { // added by Katya 08.12.2016
                d_P.get(d_P.size() - 1).setMarkParam(markStr);
            }
        }
        // pattern = Pattern.compile(Pattern.quote("d_T.add(new PetriT(\"") + "(.*?)" + Pattern.quote("\",") + "(.*?)" + Pattern.quote("));"));
        pattern = Pattern.compile("d_T\\.add\\(new PetriT\\(\"(.*?)\",([^,)]*),?(.*?)\\)\\);");
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            String probability = matcher.group(3);
            String tName = match1;
            String parametrStr = match2;
            double parametr = Utils.tryParseDouble(parametrStr) // added by Katya 08.12.2016
                ? Double.parseDouble(parametrStr)
                : 0;
            PetriT place = new PetriT(tName, parametr);
            if (!Utils.tryParseDouble(parametrStr)) { // added by Katya 08.12.2016
                place.setParametrParam(parametrStr);
            }
            if (!Utils.isBlank(probability)) {
                try {
                    double prob = Double.parseDouble(probability);
                    place.setProbability(prob);
                } catch (NumberFormatException e) {
                    // do nothing
                }
            }
            d_T.add(place);
            
        }

        pattern = Pattern.compile(Pattern.quote("d_T.get(") + "(.*?)" + Pattern.quote(").setDistribution(") + "(.*?)" + Pattern.quote(", d_T.get("));
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            int j = Integer.parseInt(match1);
            String distribution = match2;
            if ("\"exp\"".equals(distribution) || "\"unif\"".equals(distribution) || "\"norm\"".equals(distribution)) { // modified by Katya 08.12.2016
                String distributionValue = distribution.substring(1, distribution.length() - 1);
                d_T.get(j).setDistribution(distributionValue, d_T.get(j).getTimeServ());
            } else {
                d_T.get(j).setDistributionParam(distribution);
            }
        }

        pattern = Pattern.compile(Pattern.quote("d_T.get(") + "(.*?)" + Pattern.quote(").setParamDeviation(") + "(.*?)" + Pattern.quote(");"));
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            int j = Integer.parseInt(match1);
            try {
                double paramDeviation = Double.parseDouble(match2);
                d_T.get(j).setParamDeviation(paramDeviation);
            } catch (NumberFormatException e) {
                d_T.get(j).setParamDeviation(0);
            }
        }

        pattern = Pattern.compile(Pattern.quote("d_T.get(") + "(.*?)" + Pattern.quote(").setPriority(") + "(.*?)" + Pattern.quote(");"));
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            int j = Integer.parseInt(match1);
            String priorityStr = match2;
            int priority = Utils.tryParseInt(priorityStr) // added by Katya 08.12.2016
                ? Integer.parseInt(priorityStr)
                : 0;
            d_T.get(j).setPriority(priority);
            if (!Utils.tryParseInt(priorityStr)) { // added by Katya 08.12.2016
                d_T.get(j).setPriorityParam(priorityStr);
            }
        }

        pattern = Pattern.compile(Pattern.quote("d_T.get(") + "(.*?)" + Pattern.quote(").setProbability(") + "(.*?)" + Pattern.quote(");"));
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            int j = Integer.parseInt(match1);
            String probabilityStr = match2;
            double probability = Utils.tryParseDouble(probabilityStr) // added by Katya 08.12.2016
                ? Double.parseDouble(probabilityStr)
                : 1;
            d_T.get(j).setProbability(probability);
            if (!Utils.tryParseDouble(probabilityStr)) { // added by Katya 08.12.2016
                d_T.get(j).setProbabilityParam(probabilityStr);
            }
        }

        pattern = Pattern.compile(Pattern.quote("d_In.add(new ArcIn(d_P.get(") + "(.*?)" + Pattern.quote("),d_T.get(")
                + "(.*?)" + Pattern.quote("),") + "(.*?)" + Pattern.quote("));"));
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            String match3 = matcher.group(3);
            int numP = Integer.parseInt(match1);
            int numT = Integer.parseInt(match2);
            String quantityStr = match3;
            int quantity = Utils.tryParseInt(quantityStr) // added by Katya 08.12.2016
                ? Integer.parseInt(quantityStr)
                : 1;
            d_In.add(new ArcIn(d_P.get(numP), d_T.get(numT), quantity));
            if (!Utils.tryParseInt(quantityStr)) { // added by Katya 08.12.2016
                d_In.get(d_In.size() - 1).setKParam(quantityStr);
            }
        }

        pattern = Pattern.compile(Pattern.quote("d_In.get(") + "(.*?)" + Pattern.quote(").setInf(") + "(.*?)" + Pattern.quote(");")); // modified by Katya 08.12.2016
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            int j = Integer.parseInt(match1);
            if ("true".equals(match2)) {
                d_In.get(j).setInf(true);
            } else {
                d_In.get(j).setInfParam(match2);
            }
        }

        pattern = Pattern.compile(Pattern.quote("d_Out.add(new ArcOut(d_T.get(") + "(.*?)" + Pattern.quote("),d_P.get(")
                + "(.*?)" + Pattern.quote("),") + "(.*?)" + Pattern.quote("));"));
        matcher = pattern.matcher(methodText);
        while (matcher.find()) {
            String match1 = matcher.group(1);
            String match2 = matcher.group(2);
            String match3 = matcher.group(3);
            int numT = Integer.parseInt(match1);
            int numP = Integer.parseInt(match2);
            String quantityStr = match3;
            int quantity = Utils.tryParseInt(quantityStr) // added by Katya 08.12.2016
                ? Integer.parseInt(quantityStr)
                : 1;
            //System.out.println("q_Out "+quantity);
            d_Out.add(new ArcOut(d_T.get(numT), d_P.get(numP), quantity));
            if (!Utils.tryParseInt(quantityStr)) { // added by Katya 08.12.2016
                d_Out.get(d_Out.size() - 1).setKParam(quantityStr);
            }
        }

        String netName = "SampleNet" ;
        pattern = Pattern.compile(Pattern.quote("PetriNet d_Net = new PetriNet(\"") + "(.*?)" + Pattern.quote("\","));
        matcher = pattern.matcher(methodText);
        if (matcher.find()) {
            netName = matcher.group(1);
        }
        return new PetriNet(netName, d_P, d_T, d_In, d_Out);
    }

    public String openMethod(PetriNetsPanel panel, String methodFullName, JFrame frame) throws ExceptionInvalidNetStructure { // added by Katya 16.10.2016
        // also TODO: prevent networks with the same name from being saved
        // also TODO: check code syntax before saving?
        
        /*String methodName = methodFullName.substring(0, methodFullName.indexOf("("));
        
        String className = "LibNet.NetLibrary";
        
        String netName = "";
        
        
        try {
            // TODO: maybe pre-comile on program launch in background thread
            if (netLibraryClass == null) {
                //  reading NetLibrary.java 
                Path path = FileSystems.getDefault().getPath(
                        System.getProperty("user.dir"),"src","LibNet", "NetLibrary.java"); 
                String libraryText = Files.readString(path);
                
                libraryText = preProcessNetLibraryCode(libraryText);
                
                // we need a new instance of class loader each time. See NetLibraryClassLoader.java for details 
                NetLibraryClassLoader loader = new NetLibraryClassLoader(getClass().getClassLoader());
                netLibraryClass = CompilerUtils.CACHED_COMPILER.loadFromJava(loader, className, libraryText);

            }
            PetriNet net = (PetriNet)netLibraryClass.getMethod(methodName).invoke(null);
            
            // moving the prev. screen content and adding net  
            PetriNetsFrame petriNetsFrame = (PetriNetsFrame)frame;
            JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
            Point paneCenter = new Point(pane.getLocation().x+pane.getBounds().width/2, pane.getLocation().y+pane.getBounds().height/2);
           
            GraphPetriNet graphNet  = generateGraphNetBySimpleNet(panel ,net, paneCenter);
       
            panel.addGraphNet(graphNet);
            netName = graphNet.getPetriNet().getName();
            panel.repaint();
            
        } catch (FileNotFoundException e) {
            Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            e.printStackTrace();
        } catch (IOException e) {
                        Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, e.getMessage(), e);
e.printStackTrace();
        } catch (ClassNotFoundException e) { // from CACHED_COMPILER.loadFromJava()
                        Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, e.getMessage(), e);
e.printStackTrace();
        }  catch (NoSuchMethodException e) { // either no constuctor with 0 params or no method with given name
            e.printStackTrace();
        } catch (IllegalAccessException e) { // from newInstance() or invoke()
                        Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, e.getMessage(), e);
e.printStackTrace();
        } catch (InvocationTargetException e) { // from newInstance() or invoke()
                        Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, e.getMessage(), e);
e.printStackTrace();
        }*/
        
        
        // The following is old code 
        String methodName = methodFullName.substring(0, methodFullName.indexOf("(")); // modified by Katya 22.11.2016 (till the "try" block)
        String paramsString = methodFullName.substring(methodFullName.indexOf("(") + 1);
        paramsString = paramsString.substring(0, paramsString.length() - 1);
        String pnetName = "";
        FileInputStream fis = null;
        try {
            StringBuilder libraryText = new StringBuilder();

            // Use FilePathConfig for cross-platform path resolution
            Path path = FilePathConfig.getNetLibraryPath();

            // Check if file exists
            if (path == null) {
                throw new FileNotFoundException("NetLibrary.java not found in any configured location. Working directory: " +
                    System.getProperty("user.dir"));
            }

            String pathNetLibrary = path.toString();
            fis = new FileInputStream(pathNetLibrary); // modified by Katya 23.10.2016, by Inna 29.09.2018

            int content;
            while ((content = fis.read()) != -1) {
		libraryText.append((char) content);
            }
            String methodBeginning = "public static PetriNet " + methodName + "("; // modified by Katya 20.11.2016
            String methodEnding = "return d_Net;";
            String methodText;

            Pattern pattern = Pattern.compile(Pattern.quote(methodBeginning) + Pattern.quote(paramsString) + Pattern.quote(")") + "([[^}]^\\r]*)" + Pattern.quote(methodEnding)); // modified by Katya 22.11.2016
            Matcher matcher = pattern.matcher(libraryText.toString());
            if(matcher.find()){
                 methodText = methodBeginning + paramsString + ")" + matcher.group(1) + methodEnding + "}"; // modified by Katya 22.11.2016
            } 
            else {
                throw new FileNotFoundException("Method '" + methodName + "' with parameters '" + paramsString + "' not found in NetLibrary.java");
            }
            PetriNetsFrame petriNetsFrame = (PetriNetsFrame)frame;
            JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
            Point paneCenter = new Point(pane.getLocation().x+pane.getBounds().width/2, pane.getLocation().y+pane.getBounds().height/2);
            GraphPetriNet net = generateGraphNetBySimpleNet(panel ,convertMethodToPetriNet(methodText), paneCenter);
            panel.addGraphNet(net);
            pnetName = net.getPetriNet().getName();
            panel.repaint();
        } catch (FileNotFoundException e) {
            MessageHelper.showException(frame, "Method not found in NetLibrary", e);
        } catch (IOException ex) {
            MessageHelper.showException(frame, "Error reading NetLibrary file", ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            MessageHelper.showException(frame, "Invalid time delay in Petri net", ex);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(PetriNetsFrame.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return pnetName;
    }
    
    public static String replaceGroup(String regex, String source, int groupToReplace, String replacement) {
        StringBuilder result = new StringBuilder(source);
        
        boolean hasSequencesToProcess = true;
        Pattern pattern = Pattern.compile(regex);
        while (hasSequencesToProcess) {
            Matcher m = pattern.matcher(source);
            if (!m.find()) {
                hasSequencesToProcess = false;
            } else {
                result = new StringBuilder(result.replace(m.start(groupToReplace), m.end(groupToReplace), replacement).toString());
                
            }
        }
        
        return result.toString();
    }
    
    /**
     * Process the code of NetLibrary.java, specifically, in methods that have arguments, 
     * remove them from method's signature and replace their usage in the code with 
     * string parameter names, so that the compiled method can be called without supplying
     * any arguments.
     * @param code NetLibrary.java source code
     * @return processed code ready for compilation
     */
    public String preProcessNetLibraryCode(String code) {      
        // remove arguments from method header
        code = code.replaceAll("public\\s+static\\s+PetriNet\\s+(\\w+)\\s*\\((.+)\\)", "public static PetriNet $1()");
        
        // parametrized place
        // Node: doesn't support whitespace between any elements of this statement (e.g. dot and method name)
        // to add such support, add \s* where appropriate
        Matcher matcher = Pattern.compile("d_P\\.add\\(new PetriP\\(\"([^\"]+)\",\\s*(\\w+)\\)\\);").matcher(code);
        
        // Java 8 code
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String markersParameter = matcher.group(2);
            boolean isInt;
            try {
                int markers = Integer.parseInt(markersParameter);
                isInt = true;
            } catch (NumberFormatException e) {
                isInt = false;
            }
            if (!isInt) {
                String placeName = matcher.group(1);
                String variableName = placeName;
                
                // replace the entire line like so
                /*
                    PetriP @varName@ = new PetriP("@pName@", 0);\n
                    @varName@.setMarkParam("@markStr@");\n
                    d_P.add(@varName@);
                */
                String replacement = 
                        "PetriP " + variableName + " = new PetriP(\""+placeName+"\", 0);\n"
                        + variableName + ".setMarkParam(\""+markersParameter+"\");\n" 
                        + "d_P.add("+variableName+");";
                matcher.appendReplacement(sb, replacement);
            } 
            matcher.appendReplacement(sb, matcher.group(0));
        }
        matcher.appendTail(sb);
        
        code = sb.toString();
        
        // java 17 code
        /*code = matcher.replaceAll(matchRes -> {
            String markersParameter = matchRes.group(2);
            boolean isInt;
            try {
                int markers = Integer.parseInt(markersParameter);
                isInt = true;
            } catch (NumberFormatException e) {
                isInt = false;
            }
            if (!isInt) {
                String placeName = matcher.group(1);
                String variableName = placeName;
                
                // replace the entire line like so
                //
                //    PetriP @varName@ = new PetriP("@pName@", 0);\n
                //    @varName@.setMarkParam("@markStr@");\n
                //    d_P.add(@varName@);
                //
                String replacement = 
                        "PetriP " + variableName + " = new PetriP(\""+placeName+"\", 0);\n"
                        + variableName + ".setMarkParam(\""+markersParameter+"\");\n" 
                        + "d_P.add("+variableName+");";
                return replacement;
            } 
            return matchRes.group(0);
        });*/
        
        // parametrized transition delay mean
        /*
            PetriT @name@ = new PetriT("@name@",0); 
            @name@.setParametrParam("@paramname@");
            d_T.add(@name@);
            
            @name@ - group 1
            @paramname@ - group2
        */
        code = code.replaceAll("d_T\\.add\\(new PetriT\\(\"([^\"]+)\",\\s*(\\w+)\\)\\);",
                "PetriT $1 = new PetriT(\"$1\",0);\n" 
                        + "$1.setParametrParam(\"$2\");\n" 
                        + "d_T.add($1);");
        
        // parametrized transition priority
        
        // parametrized transition probability
        
        // parametrized distribution name?
        
        // parametrized number of GraphArcOut links
        
        // parametrized information link
        
        
        //System.out.println(code);
        
        
        return code; // TODO
    }

    private String generateArgumentsString(PetriNet net) { // added by Katya 08.12.2016
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

    public void saveNetAsMethod(GraphPetriNet pnet, JTextArea area) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
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

 public String saveNetAsMethod(GraphPetriNet pnet) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
     String s;
     PetriNet net;
        if (pnet.getPetriNet() == null) {
            pnet.createPetriNet("Untitled");
        }
        net = pnet.getPetriNet();
        s="\n";
       s = s.concat("public static PetriNet CreateNet" + net.getName() + "(" + generateArgumentsString(net) + ") throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {\n" // modified by Katya 08.12.2016
                + "\t" + "ArrayList<PetriP> d_P = new ArrayList<>();\n"
                + "\t" + "ArrayList<PetriT> d_T = new ArrayList<>();\n"
                + "\t" + "ArrayList<ArcIn> d_In = new ArrayList<>();\n"
                + "\t" + "ArrayList<ArcOut> d_Out = new ArrayList<>();\n");


        for (PetriP P : net.getListP()) {
            String markStr = P.markIsParam() // added by Katya 08.12.2016
                ? P.getMarkParamName()
                : Integer.toString(P.getMark());
            s = s.concat("\t" + "d_P.add(new PetriP(" + "\"" + P.getName() + "\"," + markStr + "));\n");
        }

        int j = 0;
        for (PetriT T : net.getListT()) {
            String parametrStr = T.parametrIsParam() // added by Katya 08.12.2016
                ? T.getParametrParamName()
                : Double.toString(T.getParametr());
            s = s.concat("\t" + "d_T.add(new PetriT(" + "\"" + T.getName() + "\"," + parametrStr + "));\n");
            if (T.getDistribution() != null || T.distributionIsParam()) {
                String distributionStr = T.distributionIsParam() // added by Katya 08.12.2016
                    ? T.getDistributionParamName()
                    : T.getDistribution();
                s = s.concat("\t" + "d_T.get(" + j + ").setDistribution(\"" + distributionStr + "\", d_T.get(" + j + ").getTimeServ());\n");
                s = s.concat("\t" + "d_T.get(" + j + ").setParamDeviation(" + T.getParamDeviation() + ");\n");
            }
            if (T.getPriority() != 0 || T.priorityIsParam()) {
                String priorityStr = T.priorityIsParam() // added by Katya 08.12.2016
                    ? T.getPriorityParamName()
                    : Integer.toString(T.getPriority());
                s = s.concat("\t" + "d_T.get(" + j + ").setPriority(" + priorityStr + ");\n");
            }
            if (T.getProbability() != 1.0 || T.probabilityIsParam()) {
                String probabilityStr = T.probabilityIsParam() // added by Katya 08.12.2016
                    ? T.getProbabilityParamName()
                    : Double.toString(T.getProbability());
                s = s.concat("\t" + "d_T.get(" + j + ").setProbability(" + probabilityStr + ");\n");
            }
            j++;
        }

        j = 0;
        for (ArcIn In : net.getArcIn()) {
            String quantityStr = In.kIsParam() // added by Katya 08.12.2016
                ? In.getKParamName()
                : Integer.toString(In.getQuantity());
           s =  s.concat("\t" + "d_In.add(new ArcIn(" + "d_P.get(" + In.getNumP() + ")," + "d_T.get(" + In.getNumT() + ")," + quantityStr + "));\n");

            if (In.infIsParam()) { // modified by Katya 08.12.2016
                s = s.concat("\t" + "d_In.get(" + j + ").setInf(" + In.getInfParamName() + ");\n");
            } else if (In.getIsInf()) {
                s = s.concat("\t" + "d_In.get(" + j + ").setInf(true);\n");
            }
            j++;
        }

        for (ArcOut Out : net.getArcOut()) {
            String quantityStr = Out.kIsParam() // added by Katya 08.12.2016
                ? Out.getKParamName()
                : Integer.toString(Out.getQuantity());
           s =  s.concat("\t" + "d_Out.add(new ArcOut(" + "d_T.get(" + Out.getNumT() + ")," + "d_P.get(" + Out.getNumP() + ")," + quantityStr + "));\n");
        }

        s = s.concat(
                "\t" + "PetriNet d_Net = new PetriNet(\"" + net.getName() + "\",d_P,d_T,d_In,d_Out);\n");

        s =  s.concat(
                "\t" + "PetriP.initNext();\n"
                + "\t" + "PetriT.initNext();\n"
                + "\t" + "ArcIn.initNext();\n"
                + "\t" + "ArcOut.initNext();\n"
                + "\n\t" + "return d_Net;\n");

       s =  s.concat("}");
        return s;
    }


public void saveNetAsMethod(PetriNet pnet, JTextArea area) throws ExceptionInvalidNetStructure {
        PetriNet net;
        if (pnet == null) {
            throw new ExceptionInvalidNetStructure("net from file is null") ;
        }
        net = pnet;
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

    public void saveMethodInNetLibrary(JTextArea area) {  //added by Inna 20.05.2013
        try {

            // Use FilePathConfig for cross-platform path resolution
            Path path = FilePathConfig.getNetLibraryPath();

            // Check if file exists
            if (path == null) {
                MessageHelper.showError(area, "NetLibrary.java not found in any configured location. Working directory: " +
                    System.getProperty("user.dir"));
                return;
            }

            String pathNetLibrary = path.toString(); //added by Inna 29.09.2018

            RandomAccessFile f = new RandomAccessFile(pathNetLibrary, "rw");
            // System.out.println("The path of Library of nets is\t"+path.toString());

            long n = f.length();
            if (n == 0) {
                f.writeBytes("package LibNet;\n"
                        +"import PetriObj.ExceptionInvalidNetStructure;\n"
                        + "import PetriObj.PetriNet;\n"
                        + "import PetriObj.PetriP;\n"
                        + "import PetriObj.PetriT;\n"
                        + "import PetriObj.ArcIn;\n"
                        + "import PetriObj.ArcOut;\n"
                        + "import java.util.ArrayList;\n"
                        + "public class NetLibrary {\n\n"
                        + "}");
                n = f.length();
            }

            n -= 1;
            f.seek(n);

            String c = f.readLine();
            while (c != null && !c.contains("}") && n > 0) {
                //   System.out.println("n= "+n+ ",   line= "+c);
                n -= 1;
                f.seek(n);
                c = f.readLine();
            }

            if (n > 0) {
                f.seek(n - 1);
                String s  = area.getText() + "\n" + c;
                f.write(s.getBytes());

                MessageHelper.showInfo(area, "Method was successfully added to NetLibrary class.");
            } else {
                MessageHelper.showError(area, "Could not find closing brace '}' in NetLibrary.java");
            }
            f.close();
        } catch (IOException ex) {
            MessageHelper.showException(area, "Error saving method to NetLibrary", ex);
        }
        
        // Force to recomile the class next time any method from there is used
        netLibraryClass = null;
    }
    
}
