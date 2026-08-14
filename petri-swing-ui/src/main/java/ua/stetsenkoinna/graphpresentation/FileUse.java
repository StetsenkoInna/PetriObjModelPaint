package ua.stetsenkoinna.graphpresentation;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;

import java.awt.FileDialog;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Point;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;

import ua.stetsenkoinna.config.FilePathConfig;
import ua.stetsenkoinna.libnet.NetLibrary;
import java.lang.reflect.Method;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.utils.MessageHelper;

import java.nio.file.Path;

/**
 *
 * @author Olya &  Inna
 */
public class FileUse {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUse.class);

    private final String PATTERN = ".pns";

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
            GraphCanvasModel loadedCanvas = null;

            // Check if the loaded object is GraphPetriNet or PetriNet
            if (loadedObject instanceof GraphCanvasModel canvas) {
                // A file saved from a canvas that had Petri-objects on it. The net travels
                // inside the model, so this branch has to come first: a canvas model is not a
                // GraphPetriNet and would otherwise fall through to the error below.
                loadedCanvas = canvas;
                net = canvas.getNet();
            } else if (loadedObject instanceof GraphPetriNet) {
                net = ((GraphPetriNet) loadedObject).clone();
            } else if (loadedObject instanceof PetriNet) {
                // Convert PetriNet to GraphPetriNet
                PetriNet petriNet = (PetriNet) loadedObject;
                PetriNetsFrame petriNetsFrame = (PetriNetsFrame) frame;
                JScrollPane pane = petriNetsFrame.GetPetriNetPanelScrollPane();
                Point paneCenter = new Point(pane.getLocation().x + pane.getBounds().width / 2,
                                           pane.getLocation().y + pane.getBounds().height / 2);
                net = SimpleNetGraphBuilder.build(petriNet, paneCenter);
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

            if (loadedCanvas != null) {
                panel.addCanvasModel(loadedCanvas);
            } else {
                panel.addGraphNet(net);
            }
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
            // A canvas with objects on it is saved as the whole canvas document, because the
            // frames, the shared places and the nesting live there and not in the net: writing
            // the net alone is what used to drop every object silently on reopen. A canvas
            // without objects is still written as a bare net, so a file that has nothing to say
            // about objects stays readable by builds that know nothing about them. The model
            // holds the net as a field, so one writeObject keeps the two sharing one object
            // graph rather than saving the elements twice.
            if (panel.getCanvasModel().getFrames().isEmpty()) {
                oos.writeObject(panel.getGraphNet());
            } else {
                oos.writeObject(panel.getCanvasModel());
            }
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

    /**
     * Builds a net from a library method without putting it anywhere.
     *
     * <p>Returns the net rather than adding it to the canvas, because where it should land is
     * the caller's decision — the user pointing at a spot, or the centre of a canvas that was
     * just emptied for it. It used to add the net itself, which is why every load ended up
     * wherever an automatic calculation guessed.
     *
     * @param methodFullName a net library method signature
     * @param frame parent for any error dialog
     * @param location where the built net's centroid should sit initially
     * @return the built net, or {@code null} if the method could not be resolved or invoked
     */
    public GraphPetriNet buildLibraryNet(String methodFullName, JFrame frame, Point location)
            throws ExceptionInvalidNetStructure {
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
            return SimpleNetGraphBuilder.build(net, location);
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
    }
}