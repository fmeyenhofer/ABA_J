package gui;

import ij.ImagePlus;
import ij.io.FileSaver;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.view.Views;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * @author Felix Meyenhofer
 */
public class VolumeSlicerDialog extends JPanel implements ActionListener, ChangeListener {

    private static final String BUTTON_NAME = "save";
    private static final String PERSPECTIVE_NAME = "perspective";
    private static final String SPINNER_NAME = "slice-spinner";
    private static final String SLIDER_NAME = "slice-slider";

    private final JComboBox<String> perspectiveSelector;
    private final JSlider sectionSlider;
    private Perspective perspective;
    private final JSpinner sectionSpinner;
    private final JComboBox<String> fileFormatSelector;
    private final JFileChooser fileChooser;

    private ImagePlus imp;
    private RandomAccessibleInterval img;

    private Integer currentPos;


    private VolumeSlicerDialog(ImagePlus imageUi) {
        super();
        this.imp = imageUi;

        // Panel margin
        EmptyBorder margin = new EmptyBorder(10, 10, 10, 10);

        // Dialog layout
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.setBorder(margin);

        // Initialize the file chooser
        fileChooser = new JFileChooser();
        
        // Initialize the perspective selector
        String[] perspectives = {Perspective.CORONAL.getLabel(),
                Perspective.HORIZONTAL.getLabel(),
                Perspective.SAGITAL.getLabel()}; // The index in the combobox has to be the same as the fixed-value
        perspectiveSelector = new JComboBox<>(perspectives);
        perspectiveSelector.setName(PERSPECTIVE_NAME);

        JPanel perspectivePanel = new JPanel();
        perspectivePanel.setLayout(new BoxLayout(perspectivePanel, BoxLayout.LINE_AXIS));
        perspectivePanel.setBorder(margin);
        perspectivePanel.add(new JLabel("Perspective:"));
        perspectivePanel.add(perspectiveSelector);

        // Initialize the section selector
        sectionSlider = new JSlider();
        sectionSlider.setValue(0);
        sectionSlider.setName(SLIDER_NAME);
        sectionSlider.setMinimum(0);

        // Initialize the spinner
        sectionSpinner = new JSpinner();
        sectionSpinner.setValue(0);
        ((JSpinner.DefaultEditor)sectionSpinner.getEditor()).getTextField().setColumns(5);
        sectionSpinner.setName(SPINNER_NAME);

        // Box the slider and the spinner
        JPanel sectionPanel = new JPanel();
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.LINE_AXIS));
        sectionPanel.setBorder(margin);
        sectionPanel.add(new JLabel("Section:"));
        sectionPanel.add(sectionSlider);
        sectionPanel.add(sectionSpinner);

        // Assemble the section controls in a panel
        JPanel controlPanel1 = new JPanel();
        controlPanel1.setLayout(new BoxLayout(controlPanel1, BoxLayout.Y_AXIS));
        controlPanel1.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        controlPanel1.add(perspectivePanel);
        controlPanel1.add(sectionPanel);

        // Create the file saving controls
        String[] fileformats = {"tif", "png", "jpg"};
        fileFormatSelector = new JComboBox<>(fileformats);
        fileFormatSelector.setSelectedIndex(1);
        fileFormatSelector.setName("Output File Format");

        JButton saveButton = new JButton(BUTTON_NAME);
        saveButton.setName(BUTTON_NAME);

        JPanel filePanel = new JPanel();
        filePanel.setBorder(margin);
        filePanel.add(new JLabel("Save current section:"));
        filePanel.add(fileFormatSelector);
        filePanel.add(saveButton);

        JPanel controlPanel2 = new JPanel();
        controlPanel2.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        controlPanel2.add(filePanel);

        // Space between the panels
        JPanel space = new JPanel();
        space.setBorder(new EmptyBorder(0, 10, 5, 10));

        // Assemble the UI
        add(controlPanel1);
        add(space);
        add(controlPanel2);

        // Add listeners
        saveButton.addActionListener(this);
        perspectiveSelector.addActionListener(this);
        sectionSlider.addChangeListener(this);
        sectionSpinner.addChangeListener(this);
    }

    private void initialize() {
        // Determine the initial section position and perspective
        if (imp != null) {
            img = ImagePlusAdapter.wrapImgPlus(imp);

            int nSlices = imp.getNSlices();
            System.out.println("Image dimensions " + img.numDimensions());
            for (int i = 0; i < img.numDimensions(); i++) {
                if (nSlices == img.dimension(i)) {
                    perspective = Perspective.getByFixed(i);
                    perspectiveSelector.setSelectedIndex(i);
                    sectionSlider.setMaximum(nSlices-1);
                }
//                System.out.println("\t" + i + " -> " + img.dimension(i));
            }
//            System.out.println("\tperspective: " + perspective.getName());

            changePerspective();
        }
    }

    public static void createAndShow(ImagePlus imp) {
        JFrame frame = new JFrame();
        frame.setTitle("Volume Slicer");
        if (imp == null) {
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        }

        VolumeSlicerDialog dialog = new VolumeSlicerDialog(imp);
        frame.add(dialog);
        frame.setSize(new Dimension(400,120));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                if (dialog.imp != null) {
                    dialog.imp.close();
                }
                ImageJFunctions.show(dialog.img);
            }
        });

        frame.setVisible(true);
        frame.pack();

        dialog.initialize();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Component component = (Component) e.getSource();
        if (component.getName().equals(BUTTON_NAME)) {
            saveSlice();
        } else if (component.getName().equals(PERSPECTIVE_NAME)) {
            changePerspective();
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        Component component = (Component) e.getSource();
        int pos = 0;
        int max = sectionSlider.getMaximum();
        
        if (component.getName().equals(SLIDER_NAME)) {
            pos = ((JSlider) component).getValue();
        } else if (component.getName().equals(SPINNER_NAME)) {
            pos = (int) ((JSpinner) component).getValue();
        }

        if (pos < 0) {
            pos = 0;
        } else if (pos > max) {
            pos = max;
        }

        if (sectionSlider.getValue() != pos) {
            sectionSlider.setValue(pos);
        }
        if ((int) sectionSpinner.getValue() != pos) {
            sectionSpinner.setValue(pos);
        }

        changeSlice(pos);
    }

    private void changePerspective() {
        String selection = (String) perspectiveSelector.getSelectedItem();
        perspective = Perspective.getByLabel(selection);

        int pos = 0;
        if (img != null) {
            currentPos = null;
            int max = Math.toIntExact(img.dimension(perspective.getFixed())) - 1;
            pos = (int) sectionSpinner.getValue();
            pos = (pos > max) ? max : pos;
            sectionSlider.setValue(pos);
            sectionSlider.setMaximum(max);
            changeSlice(pos);
        }
        
        sectionSlider.setValue(pos);
    }

    private void changeSlice(int pos) {
        if ((currentPos != null) && (currentPos == pos)) {
            return;
        }
        currentPos = pos;

        RandomAccessibleInterval rai = Views.hyperSlice(img, perspective.getFixed(), pos);
        if (perspective == Perspective.CORONAL) {
            rai = Views.permute(rai, 0, 1);
        }
        ImagePlus newImp = ImageJFunctions.wrap(rai, perspective.getLabel());

        // Number of pixels
        int n = 1;
        for (int d : newImp.getDimensions()) {
            n *= d;
        }
        int most = (int) (n * 0.95);

        // find the 90% quantile
        int val = 0;
        int sum = 0;
        for (int h : newImp.getProcessor().getHistogram()) {
            sum += h;
            val += 1;
            if (sum > most) {
                break;
            }
        }

        newImp.setDisplayRange(0, val);
        imp.setImage(newImp);
    }

    private void saveSlice() {
        String fileFormat = (String) fileFormatSelector.getSelectedItem();

        int status = fileChooser.showSaveDialog(VolumeSlicerDialog.this);
        if (status == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();

            String absolutePath = file.getAbsolutePath();
            if (!absolutePath.contains("." + fileFormat)) {
                absolutePath += "." + fileFormat;
            }

            FileSaver fileSaver = new FileSaver(this.imp);
            switch (fileFormat) {
                case "png":
                    fileSaver.saveAsPng(absolutePath);
                    break;
                case "tif":
                    fileSaver.saveAsTiff(absolutePath);
                    break;
                case "jpg":
                    fileSaver.saveAsJpeg(absolutePath);
                    break;
                default:
                    System.out.println("Cannot save image in " + fileFormat + " format");
            }

//            System.out.println("Output path: " + file.getAbsolutePath());
//            System.out.println("File format: " + fileFormat);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShow(null);
            }
        });
    }


    enum Perspective {
        CORONAL("coronal", "(xz)", 0),
        SAGITAL("sagital", "(xy)", 2),
        HORIZONTAL("horizontal", "(xz)", 1);

        private String name;
        private String plane;
        private int fixed;

        Perspective(String name, String plane, int cte) {
            this.name = name;
            this.plane = plane;
            this.fixed = cte;
        }

        String getName() {
            return this.name;
        }

        String getPlane() {
            return this.plane;
        }

        int getFixed() {
            return this.fixed;
        }

        String getLabel() {
            return getName() + " " + getPlane();
        }

        static Perspective getByLabel(String name) {
            if (name.equals(CORONAL.getLabel())) {
                return CORONAL;
            } else if (name.equals(SAGITAL.getLabel())) {
                return SAGITAL;
            } else {
                return HORIZONTAL;
            }
        }

        static Perspective getByFixed(int index) {
            if (index == CORONAL.getFixed()) {
                return CORONAL;
            } else if (index == SAGITAL.getFixed()) {
                return SAGITAL;
            } else {
                return HORIZONTAL;
            }
        }
    }
}
