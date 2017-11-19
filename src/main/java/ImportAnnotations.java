import gui.AtlasStructureSelectionListener;
import gui.AtlasStructureSelector;
import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.process.*;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import org.jdom.Element;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.ui.UIService;
import rest.AllenClient;
import rest.AllenXml;
import rest.AtlasStructure;
import sc.fiji.io.Nrrd_Reader;
import util.ij2.AnnotationImageTool;

import javax.xml.transform.TransformerException;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Import Annotations")
public class ImportAnnotations implements Command, AtlasStructureSelectionListener {

    @Parameter
    private StatusService status;

    @Parameter
    private LogService log;

    @Parameter
    private IOService io;

    @Parameter
    private UIService ui;


    @Parameter(type = ItemIO.INPUT)
    private ImgPlus<IntType> section;


    private int sliceNumnber = 300;
    private String atlasResolution = "25";
    private String productID = "12";

    private ImagePlus imp;

    // TODO: integrate this in the AtlasStructure
    private HashMap<Integer, List<float[]>> contours;

    @Override
    public void run() {

        imp = ImageJFunctions.wrap(section.getImg(), section.getName());

        try {
            RandomAccessibleInterval<IntType> annotationSection = getAnnotationSection(sliceNumnber, atlasResolution);
            ui.show(annotationSection);

            HashMap<Integer, AtlasStructure> graph = getStructureGraph(annotationSection, productID);

            contours = AnnotationImageTool.getContourCoordinates(annotationSection, graph.keySet());
            AtlasStructureSelector dialog = AtlasStructureSelector.createAndShowDialog(graph);
            dialog.addStructureSelectionListener(this);

        } catch (TransformerException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private  <T extends RealType<T> & NativeType<T>> HashMap<Integer, AtlasStructure>  getStructureGraph(RandomAccessibleInterval<T> section, String product_id)
            throws TransformerException, IOException, URISyntaxException {

        AllenClient client = AllenClient.getInstance();
        AllenXml structuresMetadata = client.getAtlasAnnotationMetadata(product_id);

        // Extract Structure ID's from the image (integer pixel values)
        Set<Double> ids = new HashSet<>();
        Cursor<T> cursor = Views.flatIterable(section).cursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            ids.add(cursor.get().getRealDouble());
        }

        // Convert T to integers.
        Set<Integer> iids = new HashSet<>(ids.size());
        for (double id : ids) {
            int iid = (int) id;
            iids.add(iid);
//            System.out.println(id + " " + iid);
        }

        // Fetch all structure info of the ids in the image section from the xml
        HashMap<Integer, AtlasStructure> graph = new HashMap<>();
        Collection<Integer> parents = new HashSet<>();
        for (Element element : structuresMetadata.getElements()) {
            AtlasStructure structure = new AtlasStructure(element);

            if (iids.contains(structure.getId())) {
                graph.put(structure.getId(), structure);
                parents.addAll(structure.getStructurePath());
            }
        }

        // Make sure all the parent structures are included
        for (Element element : structuresMetadata.getElements()) {
            AtlasStructure structure = new AtlasStructure(element);
            if (parents.contains(structure.getId())) {
                if (!graph.containsKey(structure.getId())) {
                    graph.put(structure.getId(), structure);
                }
            }
        }

        // Check which of the ID's of the annotation image are not in the graph
        for (int id : iids) {
            if (!graph.containsKey(id)) {
                log.warn("The following ID was not found in the metadata: " + id);
            }
        }

        return graph;
    }

    private <T extends RealType<T> & NativeType<T>> RandomAccessibleInterval<T> getAnnotationSection(int sliceNumber, String resolution) throws
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
            Color color = Color.decode("#" + structure.getColor());
            List<float[]> contour = contours.get(id);

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

        imp.setOverlay(overlay);
        if (imp.getWindow() == null) {
            imp.show();
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
