import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;

import net.imglib2.algorithm.labeling.ConnectedComponents;
import net.imglib2.algorithm.morphology.Closing;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.IOException;
import java.util.List;

/**
 * TODO: make autoCrop() generic
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Section Auto-Crop")
public class SectionAutoCrop implements Command {

    // User dialog
    @Parameter(label = "Input image", type = ItemIO.INPUT)
    private Dataset dataset;

    @Parameter(label = "Gaussian smoothing (sigma)")
    private double sigma = 13.0;

    @Parameter(label = "Closing radius (morph.)")
    private int radius = 3;

    @Parameter(label = "Crop margin")
    private long margin = 10;

    @Parameter(label = "Show intermediate images")
    boolean show_steps = false;


    // Services
    @Parameter
    private LogService logService;

    @Parameter
    private StatusService statusService;

    @Parameter
    private OpService opService;

    @Parameter
    private DisplayService displayService;


    @Override
    public void run() {
        IntervalView rai = autoCrop(dataset, margin, sigma, radius, show_steps,
                statusService, opService, logService, displayService);
        ImageJFunctions.show(rai, "cropped");
//        displayService.createDisplay("cropped", rai);
    }

    static IntervalView autoCrop(Dataset dataset,
                                    long margin, double sigma, int radius,
                                    StatusService ss, OpService os, LogService ls, DisplayService ds) {
        return autoCrop(dataset, margin, sigma, radius,false, ss, os, ls, ds);
    }

    private static IntervalView autoCrop(Dataset dataset,
                                         long margin, double sigma, int radius, boolean show_steps,
                                         StatusService ss, OpService os, LogService ls, DisplayService ds) {
        ss.clearStatus();

        Img img1 = dataset.getImgPlus().getImg();
        int n = img1.numDimensions();

        ss.showStatus("Section Auto-Crop: threshold");
        Img img2 = os.copy().img(img1);
        os.filter().gauss(img2, sigma);

        ss.showStatus("Section Auto-Crop: threshold");
        Img<BitType> img3 = os.create().img(img2, new BitType());
        os.threshold().huang(img3, img2);

        ss.showStatus("Section Auto-Crop: morphological operations");
        Img<BitType> img4 = os.create().img(img3);
        os.morphology().fillHoles(img4, img3);
        List<Shape> strel = StructuringElements.disk(radius, img1.numDimensions());
        Img<BitType> img5 = Closing.close(img4, strel, 1);
//        Img<BitType> img5 = os.create().img(img4);
//        os.morphology().open(img5, img4, strel);

        ss.showStatus("Section Auto-Crop: select biggest objects");
        ImgLabeling<BitType, UnsignedByteType> lbl = os.create().imgLabeling(img5);
        os.labeling().cca(lbl, img5, ConnectedComponents.StructuringElement.FOUR_CONNECTED);

        // Select the biggest roi
        LabelRegions regs = new LabelRegions<>(lbl);
        LabelRegion<UnsignedByteType> reg;
        long maxArea = 0;
        Object maxLabel = null;
        for (Object label : regs.getExistingLabels()) {
            reg = regs.getLabelRegion(label);

            if (show_steps) {
                ls.info("Process region: label=" + label + ", area=" + reg.size());
            }

            long area = reg.size();
            if (area > maxArea) {
                maxArea = area;
                maxLabel = label;
            }
        }

        ss.showStatus("Section Auto-Crop: crop");
        reg = regs.getLabelRegion(maxLabel);
        long[] minDim = new long[n];
        long[] maxDim = new long[n];
        reg.min(minDim);
        reg.max(maxDim);
        for (int i = 0; i < n; i++) {
            minDim[i] -= margin;
            maxDim[i] += margin;
        }

        IntervalView rai = Views.interval(img1, minDim, maxDim);

        if (show_steps) {
            ds.createDisplay("filtered", img2);
            ds.createDisplay("thresholded", img3);
            ds.createDisplay("fill holes", img4);
            ds.createDisplay("close (" + strel.get(0) + ")", img5);
            ds.createDisplay("labeling", lbl.getIndexImg());
        }

        return rai;
    }

    public static void main(String[] args) throws IOException {
        ImageJ ij = net.imagej.Main.launch(args);
        Dataset img = ij.scifio().datasetIO().open("/Users/turf/switchdrive/SJMCS_Thesis/data/devel/dapi.tif");
        ij.display().createDisplay(img);
        ij.command().run(SectionAutoCrop.class, true);
    }                                                                   
}
