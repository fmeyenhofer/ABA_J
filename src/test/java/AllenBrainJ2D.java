import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageConverter;

import io.scif.img.ImgOpener;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;

import net.imglib2.*;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.algorithm.morphology.Closing;
import net.imglib2.algorithm.morphology.Opening;
import net.imglib2.algorithm.morphology.StructuringElements;
import net.imglib2.algorithm.neighborhood.Shape;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import rest.AllenClient;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;


/**
 *
 * http://www.alleninstitute.org/legal/terms-use/
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Map 2D Section")
public class AllenBrainJ2D implements Command {

//    /** ImageJ io service instance */
//    @Parameter
//    private DatasetIOService io;

    /**
     *  Services
     */
    @Parameter
    private OpService ops;

    @Parameter
    private LogService log;

//    @Parameter
//    private DatasetService datasetService;

//    @Parameter
//    private OverlayService overlayService;


    /**
     * IO (Dialog)
     */
    @Parameter(type = ItemIO.INPUT, choices = {"Mouse, P56, Coronal",
                                               "Mouse, P56, Sagital",
                                               "Developing Mouse, P56",
                                               "Developing Mouse, P14",
                                               "Developing Mouse, P4",
                                               "Developing Mouse, E18.5",
                                               "Developing Mouse, E15.5",
                                               "Developing Mouse, E13.5",
                                               "Developing Mouse, E11.5",
                                               "Human, 34 years, Cortex - Gyral",
                                               "Human, 34 years, Cortex - Mod. Brodmann",
                                               "Human, 21 pcw",
                                               "Human, 21 pcw - Brainstem",
                                               "Human, 15 pcw",
                                               "Human Brain Atlas Guide"})
    private String atlasName = "Mouse, P56, Coronal"; // TODO choices are a duplication of the AllenAtlas enumeration.

//    @Parameter(type = ItemIO.INPUT, choices = {"coronal", "sagital"})
//    private String perspective = "coronal";

    @Parameter(type = ItemIO.INPUT, label = "Input section image")
    private Dataset data;

    @Parameter(type = ItemIO.INPUT, label = "Show SVG files")
    private boolean showSvg = true;


//    private static final String sectionId = "112360908";
    private static final String sectionId = "100960053";

    /** RESTful client for the http://atlas.brain-map.org */
    private AllenClient client;

    /** Temp file (export svg to tiff) */
    private File maskFile;


    /**
     * Method to do all the work.
     */
    @Override
    public void run() {
//        ImgOpener opener = new ImgOpener();

        log.info("spinning up AllenJ");

        // TODO: Create the control panel

        // Allen RESTful client
        if (client == null) {
            client = AllenClient.getInstance();
//            client.setLoggerService(log);
            client.setSvgDisplay(showSvg);
//            client = new AllenClient();
        }

        // TODO: Determine on which section to start

        try {
            // Load the section

//            client.getSvg(atlasName, sectionId);

            // Get the atlas contours
//            maskFile = client.createMaskFile();
            log.info("Allen slice-mask file: " + maskFile);
            svgFile2Mask(maskFile);

            // Create a mask of the section image
            Img<FloatType> img;
            if (data.getType() instanceof UnsignedByteType) {
                //noinspection unchecked
                Img<UnsignedByteType> uint = (Img<UnsignedByteType>) data.getImgPlus().getImg();

                Converter<UnsignedByteType, FloatType> converter = new RealFloatConverter<>();
                RandomAccessibleInterval<UnsignedByteType> rai2 = Views.interval(uint, uint);
                RandomAccessibleInterval<FloatType> rai = Converters.convert(rai2, converter, new FloatType());

                img = ImgView.wrap(rai, new ArrayImgFactory<>());
            } else if (data.getType() instanceof FloatType) {
                //noinspection unchecked
                img = (Img<FloatType>) data.getImgPlus().getImg().randomAccess();
            } else {
                throw new IncompatibleTypeException(data, "The input image must be of RealType");
            }

            Img<FloatType> img2 = duplicate(img);
            sectionImage2Mask(img2);



            // Overlay the contours on the image


        } catch (IncompatibleTypeException e) {
            e.printStackTrace();//TODO
        } finally {
            if (maskFile != null) {
                boolean ok = maskFile.delete();
                if (!ok) {
                    log.warn("Unable to remove the temp-file: " + maskFile.getAbsolutePath() +
                            "\n (keep an eye on that if diskspace is short)");
                }
            }
        }
//        opener.getContext().dispose();
    }


    private <T extends Type> Img<T> sectionImage2Mask(final RandomAccessibleInterval<FloatType> rai)
            throws IncompatibleTypeException {
        // Smooth
        double[] sigma = new double[rai.numDimensions()];
        for ( int d = 0; d < rai.numDimensions(); ++d ) {
            sigma[d] = 25.0;
        }


//        RandomAccessible<FloatType> inf = Views.extendValue(sec, new FloatType());
//        FinalInterval interval = new FinalInterval(sec);
        RandomAccessible<FloatType> view = Views.extendMirrorSingle(rai);
//        ImageJFunctions.show(view, "sectionImage2Mask: input");

        Gauss3.gauss(sigma, view, rai);
        ImageJFunctions.show(rai, "sectionImage2Mask: smoothing");

        Img<FloatType> fil = ImgView.wrap(rai, new ArrayImgFactory<>());


//        RandomAccessibleInterval<FloatType> con = Views.interval()
//        Img<FloatType> con = Gauss.toFloat(sigma, sec);


//        // Create a binary image
//        long[] dim = new long[sec.numDimensions()];
//        for (int d = 0; d < sec.numDimensions(); d++) {
//            dim[d] = sec.dimension(d);
//        }

//        final ImgFactory<BitType> bitFactory = new ArrayImgFactory<>();
//        Img<BitType> bw = bitFactory.create(rai, new BitType());
//
//        Img<FloatType> dataset = ops.create().dataset(view);
//        Img<FloatType> dataset = ImgView.wrap(rai, new ArrayImgFactory<>());
//        IterableInterval<BitType> bw = ops.threshold().huang(dataset);
//        Img bwi = ops.create().dataset(bw);

        Img<BitType> bw = ops.create().img(fil, new BitType());
        bw = (Img<BitType>) ops.threshold().huang(bw, fil);

        ImageJFunctions.show(bw, "sectionImage2Mask: mask");

        return null;
    }

    private Img<FloatType> duplicate(final Img<FloatType> img) {
        Img<FloatType> dup = img.factory().create(img, img.firstElement());

        Cursor<FloatType> inc = img.cursor();
        Cursor<FloatType> ouc = dup.cursor();

        while (inc.hasNext()) {
            inc.fwd();
            ouc.fwd();

            ouc.get().set(inc.get());
        }

        return dup;
    }

    private static void svgFile2Mask(File maskFile) {
        // read the image

//            Img<UnsignedByteType> img1 = ImagePlusAdapter.wrap;
//            List<SCIFIOImgPlus<?>> imgs = new ImgOpener().openImgs(id);
//            Img<UnsignedByteType> img1 = (Img<UnsignedByteType>) imgs.get(0).getImg();
        final ImagePlus imp = new Opener().openImage(maskFile.getAbsolutePath());

//        imp.show();

        ImageConverter ic = new ImageConverter(imp);
        ic.convertToGray8();
        imp.updateImage();

        Img<UnsignedByteType> img1 = ImagePlusAdapter.wrap(imp);
//            Img<UnsignedByteType> img1 = ImageJFunctions.convertFloat(imp);

//            Img<UnsignedByteType> img1 = new ImgOpener().openImg(id,
//                    new ArrayImgFactory<UnsignedByteType>(),
//                    new UnsignedByteType());

        // open
        List<Shape> strel = StructuringElements.diamond(3, 2);//StructuringElements.diamond(5, 2);
        Img<UnsignedByteType> img1c = Opening.open(img1, strel, 1);
        final Img<UnsignedByteType> img1o = Closing.close(img1c, strel, 1);

        // TODO fill holes


//        ImageJFunctions.show(img1o, "SVG mask");

        // reflect the image TODO: this should be easier with a imglib.View
        long[] dimensions = new long[img1o.numDimensions()];
        for (int d =0; d < img1o.numDimensions(); d++) {
            dimensions[d] = img1o.dimension(d);
        }
        long offset = dimensions[0] - 1;
        dimensions[0] *= 2;
        final Img<UnsignedByteType> img2 = new ArrayImgFactory<UnsignedByteType>().create(dimensions, new UnsignedByteType());

        Cursor<UnsignedByteType> cur1 = img1o.cursor();
        RandomAccess<UnsignedByteType> cur2 = img2.randomAccess();
        long[] p = new long[img1o.numDimensions()];
//            long[] p2 = new long[img1o.numDimensions()];
        while (cur1.hasNext()) {
            cur1.fwd();

            p[0] = offset - cur1.getLongPosition(0);
            p[1] = cur1.getLongPosition(1);
            cur2.setPosition(p);
            cur2.get().set(cur1.get());

            p[0] = offset + cur1.getLongPosition(0);
            cur2.setPosition(p);
            cur2.get().set(cur1.get());
        }

        ImageJFunctions.show(img2, "SVG Mask");
    }

    /**
     * Test
     */
    public static void main(final String... args) throws Exception {
        // Get the ImageJ instance
//        final ImageJ ij = net.imagej.Main.launch(args);

        // Open an image
        String imgPath = "/Users/turf/switchdrive/SJMCS_Thesis/test-data/20150530_cfos_tomato_fullBrain_11 - 2015-05-31 20.33.06-Cy3_roi02.ndpi - Series 2.tif";
        final Img< FloatType > img = new ImgOpener().openImg( imgPath, new ArrayImgFactory<>(), new FloatType() );
//        ImagePlus imp = new Opener().openImage("/Users/meyenhof/Desktop/AllenJ-Test/ome/10x/20150530_cfos_tomato_fullBrain_4 - 2015-05-31 07.23.33-Cy3_ROI-03.ome.tif");
//        imp.show();

        // Test methods
        AllenBrainJ2D abaj = new AllenBrainJ2D();
        abaj.sectionImage2Mask(img);

        // Run the plugin
//        ij.command().run(AllenBrainJ2D.class, true);
    }
}
