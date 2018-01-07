import gui.tree.AtlasStructureSelectorListener;
import gui.tree.AtlasStructureSelector;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import img.AnnotationImageTool;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.ui.UIService;
import rest.AllenAtlas;
import rest.AllenClient;
import rest.AtlasStructure;
import rest.AtlasStructureGraph;
import sc.fiji.io.Nrrd_Reader;
import img.ImagePlusUtils;

import javax.xml.transform.TransformerException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;

/**
 * Import annotations for a tissue sections that has been registered to the template.
 *
 * (well do everything the plugin name promises... the display of a annotation structure tree already works though ^^)
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Analysis > Import Annotations")
public class ImportAnnotations implements Command, AtlasStructureSelectorListener {

    @Parameter
    private StatusService status;

    @Parameter
    private LogService log;

    @Parameter
    private UIService ui;


    @Parameter(type = ItemIO.INPUT)
    private ImgPlus<IntType> section;

    // TODO: put these parameter in a dialog
    private final AllenAtlas atlas = AllenAtlas.MOUSE3D;
    private int sliceNumnber = 300;
    private String atlasResolution = "25";
    private String productID = "12";


    // Keep a reference for the contour display
    private ImagePlus imp;
    private RandomAccessibleInterval<IntType> annotationSection;


    @Override
    public void run() {
        imp = ImageJFunctions.wrap(section.getImg(), section.getName());

        try {
            status.showStatus("Load annotation section");
            annotationSection = getAnnotationSection(sliceNumnber, atlasResolution);
//            ui.show(annotationSection);

            status.showStatus("Get the structure graph");
            AllenClient client = AllenClient.getInstance();
            AtlasStructureGraph structureGraph = client.getAnnotationStructureGraph(atlas);
            log.info("Structure graph size: " + structureGraph.size());

            status.showStatus("Extract annotation contours");
            HashMap<Double, List<float[]>> annotationContours = AnnotationImageTool.getContourCoordinates(annotationSection);
            log.info("Annotations in the current section: " + annotationContours.size());

            int[] counts = structureGraph.initialize(annotationContours);//initializeGraph(annotationSection, structureGraph);
            log.info("Section annotations found in structure graph: " + counts[0]);
            if (counts[1] > 0) {
                log.warn("Section annotations not found in structure graph: " + counts[1]);
            }

            AtlasStructureSelector dialog = AtlasStructureSelector.createAndShowDialog(structureGraph.getGraph());
            dialog.addStructureSelectionListener(this);
        } catch (TransformerException e) {
            log.error("Annotation xml parsing trouble.");
            e.printStackTrace();
        } catch (IOException e) {
            log.error("Could not read annotation xml");
            e.printStackTrace();
        } catch (URISyntaxException e) {
            log.error("Unable to download annotation xml");
            e.printStackTrace();
        }
    }

    static private <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> getAnnotationSection(int sliceNumber, String resolution) throws
            TransformerException, IOException, URISyntaxException {
        // Fetch the annotation volume from the ABA API
        AllenClient client = AllenClient.getInstance();
        File path = client.getAnnotationGrid(resolution);

        // Load the image file
        Nrrd_Reader reader = new Nrrd_Reader();
        ImagePlus imp = reader.load(path.getParent(), path.getName());

        // Get the slice from the volume
        Img<T> img = ImageJFunctions.wrap(imp);
        RandomAccessibleInterval<T> sli = Views.hyperSlice(img, 0, sliceNumber);

        return Views.permute(sli, 0, 1);
    }

    @Override
    public void valueChanged(HashMap<Integer, AtlasStructure> structures) {
        Set<Integer> ids = structures.keySet();

        // Create overlay from ROI's
        Overlay overlay = new Overlay();
        for (Integer id : ids) {
            AtlasStructure structure = structures.get(id);

            if (structure.isActivated()) {
                Color color = structure.getColor();
                List<float[]> contour = structure.getContourCoordinates();

                // Get the bounding box coordinates
                int x_min = Integer.MAX_VALUE;
                int x_max = Integer.MIN_VALUE;
                int y_min = Integer.MAX_VALUE;
                int y_max = Integer.MIN_VALUE;
                for (float[] coordinates : contour) {
                    int x = (int) coordinates[0];
                    int y = (int) coordinates[1];
                    if (x < x_min) {
                        x_min = x;
                    }
                    if (x > x_max) {
                        x_max = x;
                    }
                    if (y < y_min) {
                        y_min = y;
                    }
                    if (y > y_max) {
                        y_max = y;
                    }
                }

                int w = x_max - x_min + 1;
                int h = y_max - y_min + 1;

                // Create LUT
                byte[] r = new byte[256];
                byte[] g = new byte[256];
                byte[] b = new byte[256];
                for (int i = 0; i < 256; i++) {
                    int s = (256 - i);
                    r[i] = (byte) (color.getRed() / s);
                    g[i] = (byte) (color.getGreen() / s);
                    b[i] = (byte) (color.getBlue() / s);
                }

                // Create a small sub image with the contour coordinates
                ShortProcessor ip = new ShortProcessor(w, h);
                ip.setLut(new LUT(r, g, b));
                for (float[] coordinates : contour) {
                    int x_off = ((int) coordinates[0]) - x_min;
                    int y_off = ((int) coordinates[1]) - y_min;
                    ip.set(x_off, y_off, 255);
                }

                ImageRoi roi = new ImageRoi(x_min, y_min, ip);
                roi.setOpacity(0.9);
                roi.setZeroTransparent(true);

//            float[] x = new float[contour.size()];
//            float[] y = new float[contour.size()];
//            for (int i = 0; i < contour.size(); i++) {
//                x[i] = contour.get(i)[0];
//                y[i] = contour.get(i)[1];
//            }
//
//            PolygonRoi roi = new PolygonRoi(x, y, PolygonRoi.FREELINE);

                roi.setFillColor(color);
                roi.setStrokeColor(color);
                overlay.add(roi);
            }
        }

        imp.setOverlay(overlay);
        if (imp.getWindow() == null) {
            imp.show();
            ImagePlusUtils.adjustContrast(imp.getProcessor(), 0.99);
            imp.updateAndDraw();
        } else {
            imp.updateAndDraw();
        }
    }

    @Override
    public void importAction(HashMap<Integer, AtlasStructure> structures, String type) {
        Img<IntType> img = ImgView.wrap(annotationSection, new ArrayImgFactory<>());

        List<Integer> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Img<BitType> rootMask = null;
        for (int id : structures.keySet()) {
            AtlasStructure structure = structures.get(id);
            if (structure.getName().equals("root")) {
                rootMask = AnnotationImageTool.getRootMask(img);
            } else {
                ids.add(id);
                names.add(structure.toString());
            }
        }

        List<RandomAccessibleInterval<BitType>> imgs = AnnotationImageTool.getMasks(img, ids);
        if (rootMask != null) {
            imgs.add(rootMask);
            names.add("root");
        }

        switch (type) {
            case "ROI":
                RoiManager roiManager = RoiManager.getRoiManager();
                roiManager.setVisible(true);
                for (int i = 0; i < imgs.size(); i++) {
                    ImagePlus maskImp = ImageJFunctions.wrap(imgs.get(i), names.get(i));
                    ImageProcessor imageProcessor = maskImp.getProcessor();
                    imageProcessor.setThreshold(128, 258, ImageProcessor.NO_LUT_UPDATE);
                    Roi roi = new ThresholdToSelection().convert(imageProcessor);
                    roi.setName(names.get(i));
                    roiManager.addRoi(roi);
                }
                WindowManager.getWindow("ROI Manager").repaint();
                break;

            case "Mask":
                Img<BitType> stk = ImgView.wrap(Views.stack(imgs), new ArrayImgFactory<>());
                ImgPlus<BitType> imgp = new ImgPlus<>(stk, "Masks", new AxisType[]{Axes.X, Axes.Y, Axes.Z});
                ui.show(imgp);
                break;

            default:
                log.error("Unknown import type " + type);
        }
    }

    public static void main(String[] args) throws IOException, TransformerException, URISyntaxException {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        String imagePath = "/Users/turf/switchdrive/SJMCS/data/devel/section2volume/26836491_25um_red_300.tif";
//        int sectionNumber = 300;
//        String pixelResolution = "25";

        Object section = ij.io().open(imagePath);
        ij.ui().show(section);
//
        ij.command().run(ImportAnnotations.class, true);

//        ImportAnnotations plugin = new ImportAnnotations();
//        RandomAccessibleInterval<IntType> rai = plugin.getAnnotationSection(sectionNumber, pixelResolution);

//        ij.ui().show(rai);
    }
}
