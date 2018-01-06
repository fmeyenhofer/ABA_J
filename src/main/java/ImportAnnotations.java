import gui.tree.AtlasStructureSelectionListener;
import gui.tree.AtlasStructureSelector;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.process.*;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.DiamondShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Intervals;
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
 * TODO: Add mask import
 * TODO: Add ROI import
 * (well do everything the plugin name promises... the display of a annotation structure tree already works though ^^)
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Analysis > Import Annotations")
public class ImportAnnotations implements Command, AtlasStructureSelectionListener {

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


    @Override
    public void run() {

        imp = ImageJFunctions.wrap(section.getImg(), section.getName());

        try {
            status.showStatus("Load annotation section");
            RandomAccessibleInterval<IntType> annotationSection = getAnnotationSection(sliceNumnber, atlasResolution);
            ui.show(annotationSection);

            status.showStatus("Get the structure graph");
            AllenClient client = AllenClient.getInstance();
            HashMap<Integer, AtlasStructure> structureGraph = client.getAnnotationStructureGraph(atlas);
            log.info("Structure graph size: " + structureGraph.size());

            int[] counts = initializeGraph(annotationSection, structureGraph);
            log.info("Annotations in the current section: " + counts[0]);
            log.info("Section annotations found in structure graph: " + counts[1]);
            if (counts[2] > 0) {
                log.warn("Section annotations not found in structure graph: " + counts[2]);
            }

//            HashMap<Integer, AtlasStructure> graph = getStructureGraph(annotationSection, productID);

//            contours = AnnotationImageTool.getContourCoordinates(annotationSection, graph.keySet());
            AtlasStructureSelector dialog = AtlasStructureSelector.createAndShowDialog(structureGraph);
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

    private static <T extends RealType<T> & NativeType<T>> int[] initializeGraph(RandomAccessibleInterval<T> section, HashMap<Integer, AtlasStructure> graph) {
        HashMap<Double, List<float[]>> contours = getContourCoordinates(section);
//        System.out.println("Number of contours: " + contours.size());

        // Update the atlas structure graph
        Set<Integer> ids = graph.keySet();
        Set<Integer> parents = new HashSet<>();
        int found = 0;
        int notFound = 0;
        for (double id : contours.keySet()) {
            int iid = (int) id;

            if (ids.contains(iid)) {
                AtlasStructure structure = graph.get(iid);
                structure.setActivated(true);
                structure.setContourCoordinates(contours.get(id));
                parents.addAll(structure.getParentIds());
                found++;
            } else if (iid != 0) {
                AtlasStructure structure = new AtlasStructure("" + iid,
                        "1",
                        "id = " + iid,
                        "",
                        "/-2/" + iid + "/",
                        "0f0f0f",
                        true);
                structure.setContourCoordinates(contours.get(id));
                graph.put(structure.getId(), structure);
                notFound++;
            }
        }

        // Update parents
        for (int parent : parents) {
            graph.get(parent).setHasActiveChildren(true);
        }

        // Add an artificial structure that can serve as parent to orphans
        if (notFound > 0) {
            AtlasStructure structure = new AtlasStructure("-2" ,
                    "1",
                    "orphans",
                    "(not found in structure graph)",
                    "/-2/",
                    "0f0f0f",
                    true);
            graph.put(-2, structure);
        }

        return new int[]{contours.size(), found, notFound};
    }

//    private  <T extends RealType<T> & NativeType<T>> HashMap<Integer, AtlasStructure>  getStructureGraph(RandomAccessibleInterval<T> section, String product_id)
//            throws TransformerException, IOException, URISyntaxException {
//
//        AllenClient client = AllenClient.getInstance();
//        AllenXml structuresMetadata = client.getAtlasAnnotationMetadata(product_id);
//
//        // Extract Structure ID's from the image (integer pixel values)
//        Set<Double> ids = new HashSet<>();
//        Cursor<T> cursor = Views.flatIterable(section).cursor();
//        while (cursor.hasNext()) {
//            cursor.fwd();
//            ids.add(cursor.get().getRealDouble());
//        }
//
//        // Convert T to integers.
//        Set<Integer> iids = new HashSet<>(ids.size());
//        for (double id : ids) {
//            int iid = (int) id;
//            iids.add(iid);
////            System.out.println(id + " " + iid);
//        }
//
//        // Fetch all structure info of the ids in the image section from the xml
//        HashMap<Integer, AtlasStructure> graph = new HashMap<>();
//        Collection<Integer> parents = new HashSet<>();
//        for (Element element : structuresMetadata.getElements()) {
//            AtlasStructure structure = new AtlasStructure(element);
//
//            if (iids.contains(structure.getId())) {
//                graph.put(structure.getId(), structure);
//                parents.addAll(structure.getGraphPath());
//            }
//        }
//
//        // Make sure all the parent structures are included
//        for (Element element : structuresMetadata.getElements()) {
//            AtlasStructure structure = new AtlasStructure(element);
//            if (parents.contains(structure.getId())) {
//                if (!graph.containsKey(structure.getId())) {
//                    graph.put(structure.getId(), structure);
//                }
//            }
//        }
//
//        // Check which of the ID's of the annotation image are not in the graph
//        for (int id : iids) {
//            if (!graph.containsKey(id)) {
//                log.warn("The following ID was not found in the metadata: " + id);
//            }
//        }
//
//        return graph;
//    }

    private static<T extends RealType<T> & NativeType<T>> HashMap<Double, List<float[]>> getContourCoordinates(RandomAccessibleInterval<T> annotationSection) {
        int nDim = annotationSection.numDimensions();

        // Initialize contour container
        HashMap<Double, List<float[]>> contours = new HashMap<>();

        // Take care of border conditions and get a cursor for the neighborhood centers
        Interval interval = Intervals.expand(annotationSection, -1);
        annotationSection = Views.interval(annotationSection, interval);
        Cursor<T> center = Views.iterable(annotationSection).cursor();

        // Define the pixel neighborhood (4-connected)
        DiamondShape shape = new DiamondShape(1);

        // Extract all contour pixel positions of annotations defined by their ID
        for (Neighborhood<T> neighborhood : shape.neighborhoods(annotationSection)) {
            center.fwd();
            T centerValue = center.get();
            Double id = centerValue.getRealDouble();

            // Initialize contour coordinate container if necessary
            if (!contours.containsKey(id)) {
                contours.put(id, new ArrayList<>());
            }

            // Check if the pixel lies on the contour
            for (T neighborValue : neighborhood) {
                if (!centerValue.valueEquals(neighborValue)) {
                    float[] position = new float[nDim];
                    center.localize(position);
                    contours.get(id).add(position);
                    break;
                }
            }
        }

        return contours;
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
