import bdv.util.BdvFunctions;
import gui.SectionDatasetSelector;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.meta.MetadataStore;
import loci.formats.services.OMEXMLService;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imagej.axis.CalibratedAxis;
import net.imagej.ops.OpService;
import net.imglib2.*;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import ome.xml.meta.IMetadata;
import org.scijava.command.Command;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.widget.FileWidget;
import rest.AllenCache;
import rest.AllenXml;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > ReferenceVolumeAssembler")
public class ReferenceSectionDatasetAssembler implements Command {

    @Parameter
    private LogService log;

    @Parameter
    private OpService op;

    @Parameter
    private IOService io;

    @Parameter
    private UIService ui;


//    @Parameter(style = FileWidget.DIRECTORY_STYLE, label = "Input directory")
    private File inputDir;


    //    @Parameter(style = FileWidget.DIRECTORY_STYLE, label = "Output directory")

//    private File outputDir;

    @Override
    public void run() {
        AllenCache cache = new AllenCache();

        SectionDatasetSelector dialog = SectionDatasetSelector.createAndShow();
        inputDir = dialog.getSelection();


        String dataset_id = inputDir.getName();
        File parentDir = inputDir.getParentFile();
        while (dataset_id.contains("downsample") || dataset_id.contains("quality")) {
            parentDir = parentDir.getParentFile();
            dataset_id = parentDir.getName();
        }

        String product_name = parentDir.getParentFile().getName();
        log.info("Work on " + product_name + " SectionDateSet " + dataset_id);


        File[] files = inputDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getAbsolutePath().contains(".jpg");
            }
        });

        if (files == null) {
            log.error("\tthe input directory (" + inputDir.getAbsolutePath() + ") does not contain any files");
            log.info("Aborted.");
            return;
        }


        log.info("\tretrieve dataset 3D transformation");
        AllenXml datasetMetadata;
        try {
            datasetMetadata = cache.getMetadataXml(product_name, dataset_id);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        } catch (TransformerException e) {
            e.printStackTrace();
            return;
        }

        AffineTransform3D affine3d = new AffineTransform3D();
        affine3d.set(datasetMetadata.getRowPackedVolume2ReferenceTransform());

        String redChannelName = datasetMetadata.getValue("red-channel");
        String greenChannelName = datasetMetadata.getValue("green-channel");
        String blueChannelName = datasetMetadata.getValue("blue-channel");
        double resolutionAxial = Double.parseDouble(datasetMetadata.getValue("section-thickness"));



        log.info("\tretrieve section image metadata");
        int zDim = files.length;
        ArrayList<Integer> sectionWidths = new ArrayList<>(zDim);
        ArrayList<Integer> sectionHeights = new ArrayList<>(zDim);
        ArrayList<Integer> zNums = new ArrayList<>(zDim);
        ArrayList<AffineTransform2D> affine2ds = new ArrayList<>(zDim);
        ArrayList<Integer> xDims = new ArrayList<>(zDim);
        ArrayList<Integer> yDims = new ArrayList<>(zDim);
        ArrayList<Integer> cDims = new ArrayList<>(zDim);
        ArrayList<Double> resolutionXY = new ArrayList<>(zDim);
        ArrayList<Double> scales = new ArrayList<>(zDim);

        for (File file : files) {
            String image_id = file.getName().split("\\.(?=[^.]+$)")[0];
            log.info("\t\t" + image_id);

            Integer xDim, yDim, cDim, width, height;

            // read image metadata
            ServiceFactory factory;
            try {
                factory = new ServiceFactory();
                OMEXMLService service = factory.getInstance(OMEXMLService.class);
                IMetadata meta = service.createOMEXMLMetadata();
                // create format reader
                IFormatReader reader = new ImageReader();
                reader.setMetadataStore((MetadataStore) meta);
                reader.setId(file.getAbsolutePath());
                xDim = reader.getSizeX();
                yDim = reader.getSizeY();
                cDim = reader.getSizeC();
                reader.close();
            } catch (DependencyException e) {
                e.printStackTrace();
                return;
            } catch (ServiceException e) {
                e.printStackTrace();
                return;
            } catch (FormatException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // get section metadata
            try {
                AllenXml sectionMetadata = cache.getMetadataXml(product_name, dataset_id, image_id);

                zNums.add(Integer.parseInt(sectionMetadata.getValue("section-number")));
                width = Integer.parseInt(sectionMetadata.getValue("width"));
                height = Integer.parseInt(sectionMetadata.getValue("height"));

                resolutionXY.add(Double.parseDouble(sectionMetadata.getValue("resolution")));

                AffineTransform2D affine = new AffineTransform2D();
                affine.set(sectionMetadata.getRowPackedSection2VolumeTransform());
                affine2ds.add(affine);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return;
            } catch (TransformerException e) {
                e.printStackTrace();
                return;
            }


            xDims.add(xDim);
            yDims.add(yDim);
            cDims.add(cDim);
            sectionWidths.add(width);
            sectionHeights.add(height);

            double scaleX = xDim.doubleValue() / width.doubleValue();
            double scaleY = yDim.doubleValue() / height.doubleValue();
            double scale = (scaleX + scaleY) / 2;
            scales.add(scale);

        }



        log.info("\talign section images");
        long xMax = Collections.max(xDims);
        long yMax = Collections.max(yDims);
        long zMax = Collections.max(zNums);
        long cMax = Collections.max(cDims);
        long[] dims = new long[]{xMax, yMax, zMax, cMax};

        final long[] volumeLowerBounds = new long[]{0, 0, 0, 0};
        final long[] volumeUpperBounds = new long[]{xMax - 1, yMax - 1, zMax - 1, cMax - 1};

        final long[] monochromeLowerBounds = new long[]{0, 0, 0};
        final long[] monochromeUpperBounds = new long[]{xMax - 1, yMax - 1, zMax - 1};

        final long[] sliceLowerBounds = new long[]{0, 0};
        final long[] sliceUpperBounds = new long[]{xMax-1, yMax-1};

        Img<UnsignedByteType> vol = new DiskCachedCellImgFactory<UnsignedByteType>().create(dims, new UnsignedByteType());

        for (int f = 0; f < files.length; f++) {
            File file = files[f];
            log.info("\tregister " + file.getName());

            // sub-volume
            int sliceNum = zNums.get(f) - 1;
            RandomAccessibleInterval<UnsignedByteType> slice = Views.hyperSlice(vol, 2, sliceNum);


            double scale = scales.get(f);
            AffineTransform2D scaleUp = new AffineTransform2D();
            scaleUp.scale(1/ scale);
            AffineTransform2D scaleDown = new AffineTransform2D();
            scaleDown.scale(scale);

            AffineTransform2D affine2d = affine2ds.get(f);
            affine2d.concatenate(scaleUp);
            affine2d.preConcatenate(scaleDown);

//            final UnsignedByteType type = new UnsignedByteType();
//            final ArrayImgFactory< UnsignedByteType > factory = new ArrayImgFactory<>();
//            final Img< UnsignedByteType > rai = IO.openImgs( file.getAbsolutePath(), factory, type ).get( 0 ).getImg();
            RandomAccessibleInterval<UnsignedByteType> rai = null;
            try {
                rai = (RandomAccessibleInterval<UnsignedByteType>) io.open(file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }

//            RealRandomAccessible<UnsignedByteType> view = Views.interpolate(Views.extendBorder(rai),
//                    new NLinearInterpolatorFactory<UnsignedByteType>());

            // Process each channel
            for (int channel = 0; channel < cMax; channel++) {
                RandomAccessibleInterval<UnsignedByteType> colorTarget = Views.hyperSlice(slice, 2, channel);
                RandomAccessibleInterval<UnsignedByteType> colorSource = Views.hyperSlice(rai, 2, channel);

                RealRandomAccessible<UnsignedByteType> colorSourceInterpolated = Views.interpolate(
                        Views.extendBorder(colorSource), new NLinearInterpolatorFactory<UnsignedByteType>());
                RandomAccessible<UnsignedByteType> colorSourveWrapped = RealViews.affine(
                        colorSourceInterpolated, affine2d);
                RandomAccessibleInterval<UnsignedByteType> colorSourceInterval = Views.interval(
                        colorSourveWrapped, sliceLowerBounds, sliceUpperBounds);

//                if (f > 3) {
//                    ImageJFunctions.show(colorSourceInterval, "color " + channel + " registered " + sliceNum);
//                }

                Cursor<UnsignedByteType> sc = Views.flatIterable(colorSourceInterval).cursor();
                Cursor<UnsignedByteType> tc = Views.flatIterable(colorTarget).cursor();
                while (tc.hasNext()) {
                    tc.fwd();
                    sc.fwd();
                    tc.get().set(sc.get());
                }
            }

//            if (f > 3) {
//                break;
//            }

        }

        // TODO: The 3D alignment (affine) does not work as expected

//        try {
//            Img<UnsignedByteType> in = (Img<UnsignedByteType>) io.open("/Users/turf/Desktop/aligned sections.tif");
//            vol = Views.permute(in, 2, 3);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        ImageJFunctions.show(vol, "aligned sections");
//
//        log.info("\tregister volume to reference");
        double scale = Collections.max(scales);
        double resolutionLateral = Collections.max(resolutionXY) / scale;
//
//        AffineTransform3D scaleUp = new AffineTransform3D();
//        scaleUp.scale(1 / scale);
//        AffineTransform3D scaleDown = new AffineTransform3D();
//        scaleDown.scale(scale);
//        affine3d.concatenate(scaleUp);
//        affine3d.preConcatenate(scaleDown);
//
//        ArrayList<RandomAccessibleInterval<UnsignedByteType>> vols = new ArrayList<>(Math.toIntExact(cMax));
//        for (int channel = 0; channel < cMax; channel++) {
//            RandomAccessibleInterval<UnsignedByteType> monochrome = Views.hyperSlice(vol, 3, channel);
//            RealRandomAccessible<UnsignedByteType> interpolated = Views.interpolate(Views.extendBorder(monochrome),
//                    new NLinearInterpolatorFactory<UnsignedByteType>());
//            RandomAccessible<UnsignedByteType> wrap = RealViews.affine(interpolated, affine3d);
//            vols.add(Views.interval(wrap, monochromeLowerBounds, monochromeUpperBounds));
//        }
//
//        RandomAccessibleInterval<UnsignedByteType> wrapped = Views.stack(vols);
//        Img<UnsignedByteType> img = ImgView.wrap(wrapped, new ArrayImgFactory<UnsignedByteType>());
//

        ImgPlus<UnsignedByteType> imgp = new ImgPlus<UnsignedByteType>(vol,
                "volume dataset " + dataset_id,
                new AxisType[]{Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL},
                new double[]{resolutionLateral, resolutionLateral, resolutionAxial, 0},
                new String[]{"um", "um", "um", ""});
        

        ui.show("reconstructed reference section dataset", imgp);
        BdvFunctions.show(imgp, "" + dataset_id );
    }


    public static void main(String[] args) {
        final ImageJ ij =  new net.imagej.ImageJ();
        // ij.ui().showUI();
        
        ij.command().run(ReferenceSectionDatasetAssembler.class, true);
    }
}
