package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;

import java.awt.FileDialog;

import ua.stetsenkoinna.petriobj.PetriMainElement;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Point;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import ua.stetsenkoinna.config.FilePathConfig;
import ua.stetsenkoinna.libnet.NetLibrary;
import java.lang.reflect.Method;
import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.utils.MessageHelper;

import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.Objects;

/**
 *
 * @author Olya &  Inna
 */
public class FileUse {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUse.class);

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
            LOGGER.error("EOF error during file reading", ex);
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
            LOGGER.error("Unexpected error", ex);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
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
            LOGGER.info("Saving GraphNet as '{}{}'", fdlg.getDirectory(), fdlg.getFile());
            net.createPetriNet(fdlg.getFile());
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(net);
            oos.close();
        } catch (IOException ex) {
            LOGGER.error("Unexpected error", ex);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
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
            LOGGER.info("Saving PetriNet as '{}{}'", fdlg.getDirectory(), fdlg.getFile());
            panel.getGraphNet().createPetriNet(fdlg.getFile());
            fos = new FileOutputStream(fdlg.getDirectory() + fdlg.getFile() + PATTERN);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(panel.getGraphNet().getPetriNet());
            oos.close();
        } catch (IOException ex) {
            LOGGER.error("Unexpected error", ex);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
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
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(pnet);
            oos.close();
        } catch (IOException ex) {
            LOGGER.error("Unexpected error", ex);
        } catch (ExceptionInvalidTimeDelay ex) {
            LOGGER.error("Unexpected error", ex);
        } finally {
            try {
                assert fos != null;
                fos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
            }
            try {
                assert oos != null;
                oos.close();
            } catch (IOException ex) {
                LOGGER.error("Unexpected error", ex);
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
        PetriT firstTran = availPetriTrans.removeFirst();
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
                        sets.addFirst(new VerticalSet(!lastSet.IsForPlaces()));
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
                        sets.addFirst(new VerticalSet(!lastSet.IsForPlaces()));
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
        firstSet = sets.getFirst();
        VerticalSet lastSet = sets.getLast();
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
                    } else {
                        PetriT tran = (PetriT)elem;
                        GraphPetriTransition grTran = new GraphPetriTransition(tran, PetriNetsPanel.getIdElement());
                        grTran.setNewCoordinates(new Point2D.Double(x, y));
                        grTransitions.add(grTran);
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
                    } else {
                        PetriT tran = (PetriT)elem;
                        GraphPetriTransition grTran = new GraphPetriTransition(tran, PetriNetsPanel.getIdElement());
                        grTran.setNewCoordinates(new Point2D.Double(x, y));
                        grTransitions.add(grTran);
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
                    } else {
                        PetriT tran = (PetriT)elem;
                        GraphPetriTransition grTran = new GraphPetriTransition(tran, PetriNetsPanel.getIdElement());
                        grTran.setNewCoordinates(new Point2D.Double(x, y));
                        grTransitions.add(grTran);
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
            grInArc.setPetriElements(); // this line and the next two
            grInArc.changeBorder();
            grInArc.updateCoordinates();
            grArcIns.add(grInArc);
        }

        for (ArcOut outArc : net.getArcOut()) {
            GraphArcOut grOutArc = getGraphArcOut(outArc, grTransitions, grPlaces);
            grArcOuts.add(grOutArc);
        }

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

    private static GraphArcOut getGraphArcOut(ArcOut outArc, ArrayList<GraphPetriTransition> grTransitions, ArrayList<GraphPetriPlace> grPlaces) {
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
        grOutArc.setPetriElements(); // this line and the next two
        grOutArc.changeBorder();
        grOutArc.updateCoordinates();
        return grOutArc;
    }


    public String openMethod(PetriNetsPanel panel, String methodFullName, JFrame frame) throws ExceptionInvalidNetStructure {
        String methodName = methodFullName.substring(0, methodFullName.indexOf("("));
        try {
            Method method = null;
            for (Method m : NetLibrary.class.getDeclaredMethods()) {
                if (m.getName().equals(methodName) &&
                        java.lang.reflect.Modifier.isPublic(m.getModifiers()) &&
                        java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                MessageHelper.showError(frame, "Method '" + methodName + "' not found in NetLibrary");
                return null;
            }
            Object[] args = buildDefaultArgs(method.getParameterTypes());
            PetriNet net = (PetriNet) method.invoke(null, args);
            PetriNetsFrame petriNetsFrame = (PetriNetsFrame) frame;
            JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
            Point paneCenter = new Point(pane.getLocation().x + pane.getBounds().width / 2,
                    pane.getLocation().y + pane.getBounds().height / 2);
            GraphPetriNet graphNet = generateGraphNetBySimpleNet(panel, net, paneCenter);
            panel.addGraphNet(graphNet);
            String pnetName = graphNet.getPetriNet().getName();
            panel.repaint();
            return pnetName;
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ExceptionInvalidNetStructure ex) throw ex;
            MessageHelper.showException(frame, "Error creating Petri net from library", e);
            return null;
        } catch (IllegalAccessException e) {
            MessageHelper.showException(frame, "Cannot access NetLibrary method", e);
            return null;
        }
    }

    private Object[] buildDefaultArgs(Class<?>[] types) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) args[i] = 1;
            else if (types[i] == double.class) args[i] = 1.0;
            else args[i] = "Net";
        }
        return args;
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
        Matcher matcher = Pattern.compile("d_P\\.add\\(new PetriP\\(\"([^\"]+)\",\\s*(\\w+)\\)\\);").matcher(code);

        StringBuilder sb = new StringBuilder();
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
        code = code.replaceAll("d_T\\.add\\(new PetriT\\(\"([^\"]+)\",\\s*(\\w+)\\)\\);",
                "PetriT $1 = new PetriT(\"$1\",0);\n" 
                        + "$1.setParametrParam(\"$2\");\n" 
                        + "d_T.add($1);");
        return code;
    }

    public void saveNetAsMethod(GraphPetriNet pnet, JTextArea area) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        if (pnet.getPetriNet() == null) {
            pnet.createPetriNet("Untitled");
        }
        area.setText(NetMethodCodeGenerator.generate(pnet.getPetriNet()));
    }

 public String saveNetAsMethod(GraphPetriNet pnet) throws ExceptionInvalidNetStructure, ExceptionInvalidTimeDelay {
        if (pnet.getPetriNet() == null) {
            pnet.createPetriNet("Untitled");
        }
        return NetMethodCodeGenerator.generate(pnet.getPetriNet());
    }


public void saveNetAsMethod(PetriNet pnet, JTextArea area) throws ExceptionInvalidNetStructure {
        if (pnet == null) {
            throw new ExceptionInvalidNetStructure("net from file is null") ;
        }
        area.setText(NetMethodCodeGenerator.generate(pnet));
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
        
        // Force to recompile the class next time any method from there is used
        netLibraryClass = null;
    }
}