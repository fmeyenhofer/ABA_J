package rest;

import img.AnnotationImageTool;

import bdv.BigDataViewer;
import bdv.export.*;
import bdv.ij.export.imgloader.ImagePlusImgLoader;
import bdv.ij.util.PluginHelper;
import bdv.ij.util.ProgressWriterIJ;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.viewer.ViewerOptions;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import sc.fiji.io.Nrrd_Reader;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;

import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Class that takes care of loading reference volumes and
 * converting them into hdf5 file format. Hdf5 loads a lot faster
 * with the {@link BigDataViewer}
 *
 * @author Felix Meyenhofer
 */
public class AllenRefVol {

    private final File nrrdFile;
    private final File xmlFile;
    private final File hdf5File;

    public AllenRefVol(File path) {
        this(generatePath(path, ".nrrd"), generatePath(path, ".xml"));
    }

    private AllenRefVol(File nrrdFile, File xmlFile) {
        this.nrrdFile = nrrdFile;
        this.xmlFile = xmlFile;
        this.hdf5File = generatePath(xmlFile, ".h5");

        if (!xmlFile.exists()) {
            ImagePlus imp = loadNrrd(nrrdFile);
            convert2Hdf5(imp, this.xmlFile);
        }
    }

    private static File generatePath(File file, String ext) {
        String[] parts = file.getAbsolutePath().split("\\.");
        String extension = "." + parts[parts.length - 1];
        String trunk = file.getAbsolutePath().replace(extension, "");

        return new File(trunk + ext);
    }

    private static ImagePlus loadNrrd(File file) {
        Nrrd_Reader reader = new Nrrd_Reader();
        return reader.load(file.getParent(), file.getName());
    }

    public <T extends NumericType<T> & NativeType<T>> RandomAccessibleInterval<T> getRai() {
        ImagePlus imp = loadNrrd(nrrdFile);
        return ImageJFunctions.wrap(imp);
    }

//    public RandomAccessibleInterval<UnsignedShortType> getRai() throws SpimDataException {
//        SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load(this.xmlFile.getAbsolutePath());
//        final AbstractSequenceDescription< ?, ?, ? > seq = spimData.getSequenceDescription();
//        Hdf5ImageLoader loader = new Hdf5ImageLoader(hdf5File, null, seq, true);
//
//        return loader.getSetupImgLoader(0).getImage(0, 0, ImgLoaderHints.LOAD_COMPLETELY);
//    }

    public SpimDataMinimal getHdf5() throws SpimDataException {
        return new XmlIoSpimDataMinimal().load(this.xmlFile.getAbsolutePath());
    }

    public void show() throws SpimDataException {
        BigDataViewer.open(xmlFile.getAbsolutePath(), xmlFile.getName(), new ProgressWriterIJ(), ViewerOptions.options());
    }

    public AffineTransform3D getTransform() throws SpimDataException {
        return getHdf5()
                .getViewRegistrations()
                .getViewRegistration(new ViewId(0, 0)).getModel().copy();
    }

    public Dimensions getDimensions() throws SpimDataException {
        return getHdf5().getSequenceDescription().getViewSetups().get(0).getSize();
    }

    /**
     * Simplified version of {@link bdv.ij.ExportImagePlusPlugIn}
     * (this does not need to be as general)
     *
     * @param imp input image
     * @param seqFile output xml file
     */
    private void convert2Hdf5(ImagePlus imp, File seqFile) {
        final int[][] resolutions = new int[][]{{1, 1, 1}, {2, 2, 1}, {4, 4, 2}};
        final int[][] subdivisions = new int[][]{{32, 32, 4}, {16, 16, 8}, {8, 8, 8}};
        final double rangeMin = 0;
        final double rangeMax = 65535;
        final boolean deflate = true;
        final ImagePlusImgLoader.MinMaxOption minMaxOption = ImagePlusImgLoader.MinMaxOption.COMPUTE;

        File hdf5File = new File(seqFile.getAbsolutePath().replace(".xml", ".h5"));
        String viewName = hdf5File.getName().replace(".h5", "");

        // Get calibration and image size
        final double pw = imp.getCalibration().pixelWidth;
        final double ph = imp.getCalibration().pixelHeight;
        final double pd = imp.getCalibration().pixelDepth;
        String punit = imp.getCalibration().getUnit();
        if (punit == null || punit.isEmpty()) {
            punit = "px";
        }
        final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pw, ph, pd );
        final int w = imp.getWidth();
        final int h = imp.getHeight();
        final int d = imp.getNSlices();
        final FinalDimensions size = new FinalDimensions(w, h, d);


        final ProgressWriter progressWriter = new ProgressWriterIJ();
        progressWriter.out().println( "starting export..." );

        // Create ImgLoader wrapping the image
        final ImagePlusImgLoader<?> imgLoader;
        switch (imp.getType()) {
            case ImagePlus.GRAY8:
                imgLoader = ImagePlusImgLoader.createGray8(imp, minMaxOption, rangeMin, rangeMax);
                break;
            case ImagePlus.GRAY16:
                imgLoader = ImagePlusImgLoader.createGray16(imp, minMaxOption, rangeMin, rangeMax);
                break;
            case ImagePlus.GRAY32:
            default:
                imgLoader = ImagePlusImgLoader.createGray32(imp, minMaxOption, rangeMin, rangeMax);
                break;
        }

        final int numTimepoints = imp.getNFrames();
        final int numSetups = imp.getNChannels();

        // Create SourceTransform from the images calibration
        final AffineTransform3D sourceTransform = new AffineTransform3D();
        sourceTransform.set( pw, 0, 0, 0, 0, ph, 0, 0, 0, 0, pd, 0 );

        // Write hdf5
        final HashMap< Integer, BasicViewSetup > setups = new HashMap<>( numSetups );
        for (int s = 0; s < numSetups; ++s) {
            final BasicViewSetup setup = new BasicViewSetup(s, viewName, size, voxelSize);
            setup.setAttribute(new Channel(s + 1));
            setups.put(s, setup);
        }

        final ArrayList< TimePoint > timepoints = new ArrayList<>( numTimepoints );
        for (int t = 0; t < numTimepoints; ++t) {
            timepoints.add(new TimePoint(t));
        }
        final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, null );

        Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo;
        perSetupExportMipmapInfo = new HashMap<>();
        final ExportMipmapInfo mipmapInfo = new ExportMipmapInfo( resolutions, subdivisions );
        for (final BasicViewSetup setup : seq.getViewSetupsOrdered()) {
            perSetupExportMipmapInfo.put( setup.getId(), mipmapInfo );
        }

        // LoopBackHeuristic:
        // - If saving more than 8x on pixel reads use the loopback image over
        //   original image
        // - For virtual stacks also consider the cache size that would be
        //   required for all original planes contributing to a "plane of
        //   blocks" at the current level. If this is more than 1/4 of
        //   available memory, use the loopback image.
        final boolean isVirtual = imp.getStack().isVirtual();
        final long planeSizeInBytes = imp.getWidth() * imp.getHeight() * imp.getBytesPerPixel();
        final long ijMaxMemory = IJ.maxMemory();
        final int numCellCreatorThreads = Math.max( 1, PluginHelper.numThreads() - 1 );
        final WriteSequenceToHdf5.LoopbackHeuristic loopbackHeuristic = new WriteSequenceToHdf5.LoopbackHeuristic() {
            @Override
            public boolean decide(final RandomAccessibleInterval<?> originalImg,
                                  final int[] factorsToOriginalImg,
                                  final int previousLevel,
                                  final int[] factorsToPreviousLevel,
                                  final int[] chunkSize) {
                if (previousLevel < 0) {
                    return false;
                }

                if (WriteSequenceToHdf5.numElements(factorsToOriginalImg) / WriteSequenceToHdf5.numElements(factorsToPreviousLevel) >= 8) {
                    return true;
                }

                if (isVirtual) {
                    final long requiredCacheSize = planeSizeInBytes * factorsToOriginalImg[2] * chunkSize[2];
                    if (requiredCacheSize > ijMaxMemory / 4)
                        return true;
                }

                return false;
            }
        };

        final WriteSequenceToHdf5.AfterEachPlane afterEachPlane = new WriteSequenceToHdf5.AfterEachPlane() {
            @Override
            public void afterEachPlane(final boolean usedLoopBack) {
                if (!usedLoopBack && isVirtual) {
                    final long free = Runtime.getRuntime().freeMemory();
                    final long total = Runtime.getRuntime().totalMemory();
                    final long max = Runtime.getRuntime().maxMemory();
                    final long actuallyFree = max - total + free;

                    if (actuallyFree < max / 2) {
                        imgLoader.clearCache();
                    }
                }
            }
        };

        WriteSequenceToHdf5.writeHdf5File(seq, perSetupExportMipmapInfo, deflate,
                hdf5File, loopbackHeuristic, afterEachPlane, numCellCreatorThreads,
                new SubTaskProgressWriter(progressWriter, 0, 0.95));

        // Write xml sequence description
        final Hdf5ImageLoader hdf5Loader = new Hdf5ImageLoader(hdf5File, null, null, false);
        final SequenceDescriptionMinimal seqh5 = new SequenceDescriptionMinimal( seq, hdf5Loader );

        final ArrayList<ViewRegistration> registrations = new ArrayList<>();
        for (int t = 0; t < numTimepoints; ++t) {
            for (int s = 0; s < numSetups; ++s) {
                registrations.add( new ViewRegistration( t, s, sourceTransform ) );
            }
        }

        final File basePath = seqFile.getParentFile();
        final SpimDataMinimal spimData = new SpimDataMinimal(basePath, seqh5, new ViewRegistrations(registrations));

        try {
            new XmlIoSpimDataMinimal().save(spimData, seqFile.getAbsolutePath());
            progressWriter.setProgress( 1.0 );
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        progressWriter.out().println( "done" );
    }


    public static Img<BitType> getSectionMask(Atlas.VoxelResolution resolution, Atlas.PlaneOfSection plane)
            throws TransformerException, IOException, URISyntaxException, SpimDataException {
        return getSetcionMask(resolution, plane, -1);
    }

    public static Img<BitType> getSetcionMask(Atlas.VoxelResolution resolution, Atlas.PlaneOfSection plane, long sectionNumber)
            throws TransformerException, IOException, URISyntaxException {
        AllenImage aimg = new AllenCache().getReferenceVolume(Atlas.Modality.ANNOTATION, resolution);
        AllenRefVol refVol = new AllenRefVol(aimg.getFile());
        RandomAccessibleInterval<UnsignedShortType> rai = refVol.getRai();

        int fixedAxis = plane.getFixedAxisIndex();
        if (sectionNumber < 0) {
            sectionNumber = rai.dimension(fixedAxis) / 2;
        }

        RandomAccessibleInterval<UnsignedShortType> sec = Views.hyperSlice(rai, fixedAxis, sectionNumber);
        if (plane.swapAxes()) {
            sec = Views.permute(sec,0, 1);
        }
        Img<UnsignedShortType> img = ImgView.wrap(sec, new ArrayImgFactory<>());

        return AnnotationImageTool.getRootMask(img);
    }

    public static AxisType[] getAxes() {
        return new AxisType[]{Axes.X, Axes.Y, Axes.Z};
    }


    public static void main(String[] args) throws SpimDataException, TransformerException, IOException, URISyntaxException {
//        File nrrdFile = new File("/Users/turf/allen-cache/reference-volumes/average_template_10.nrrd");
//        File xmlFile = new File("/Users/turf/Desktop/avg_template_10.xml");
//
//        AllenRefVol refVol = new AllenRefVol(nrrdFile, xmlFile);
//        refVol.show();

        Img<BitType> msk = getSectionMask(Atlas.VoxelResolution.FIFTY, Atlas.PlaneOfSection.CORONAL);
        ImageJFunctions.show(msk);
    }
}
