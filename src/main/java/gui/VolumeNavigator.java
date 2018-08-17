package gui;

import ij.ImagePlus;
import sc.fiji.io.Nrrd_Reader;

import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imagej.axis.AxisType;
import net.imglib2.*;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.ImgView;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.list.ListCursor;
import net.imglib2.img.list.ListImg;
import net.imglib2.img.list.ListImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import de.mpicbg.jug.clearvolume.gui.GenericClearVolumeGui;

import javax.swing.*;
import java.io.File;

/**
 * TODO: this is only a scrapbook for now
 *
 * @author Felix Meyenhofer
 */
public class VolumeNavigator {

    static < T extends Comparable< T > > T max( final T a, final T b ) {
        return a.compareTo( b ) > 0 ? a : b;
    }


    private static  Img<FloatType> mip(final RandomAccessibleInterval< FloatType > img ) {
        final ListImg<FloatType> mip = new ListImgFactory<FloatType>().create(new long[]{img.dimension(0), 1L}, new FloatType());

        final ListCursor<FloatType> outputCursor = mip.cursor();
        final RandomAccess<FloatType> inputRA = img.randomAccess();
        while ( outputCursor.hasNext() ) {
            outputCursor.fwd();
            final FloatType target = outputCursor.get();

            // Initialize with very low value.
            target.setZero();

            inputRA.setPosition( outputCursor.getIntPosition( 0 ), 0 );
            for ( int y = 0; y < img.dimension( 1 ); y++ ) {
                inputRA.setPosition(y, 1);
                final FloatType st = inputRA.get();
                target.set(max(target, st));
            }
        }

        return mip;
    }

    public static <T extends NativeType<T> & RealType<T>> void showVolume(RandomAccessibleInterval<T> vol) {
        Img<T> img = ImgView.wrap(vol, new ArrayImgFactory<>());
        ImgPlus<T> imgp = new ImgPlus<>(img, "brain + slice", new AxisType[]{Axes.X, Axes.Y, Axes.Z, Axes.CHANNEL});

        JFrame frame = new JFrame( "ClearVolume Tutorial 1" );
        frame.setBounds( 50, 50, 1024, 768 );
        GenericClearVolumeGui<T> panelGui = new GenericClearVolumeGui<>(imgp);
        frame.add( panelGui );
        frame.setVisible( true );
    }


    public static void main(String[] args) {
//        ImageJ ij = new ImageJ();
//        ij.ui().showUI();

        String volPath = "/Users/turf/allen-cache/reference-volumes/average_template_50.nrrd";
        File volFile = new File(volPath);

        Nrrd_Reader reader = new Nrrd_Reader();
        ImagePlus imp = reader.load(volFile.getParent(), volFile.getName());
        RandomAccessibleInterval<UnsignedShortType> img = ImageJFunctions.wrap(imp);

        final Converter<UnsignedShortType, FloatType> int2float = new Converter<UnsignedShortType, FloatType>() {
            @Override
            public void convert(UnsignedShortType intput, FloatType output) {
                output.set((float) intput.getInteger());
            }
        };

        final RandomAccessibleInterval<FloatType> con = Converters.convert(img, int2float, new FloatType());

        RandomAccessible<FloatType> extended = Views.extendValue(con, new FloatType());
        RealRandomAccessible<FloatType> interpolated = Views.interpolate(extended, new NLinearInterpolatorFactory<>());

        long[] dims = new long[img.numDimensions()];
        img.dimensions(dims);

        final long w = dims[0], h = dims[1], d = dims[2];

        System.out.println("width=" + w + " height=" + h + " depth=" + d);

        final double s = w / 2;
        final AffineTransform3D affine = new AffineTransform3D();
//        affine.set(
//                s, 0.0, 0.0, w / 2,
//                0.0, s, 0.0, h / 2,
//                0.0, 0.0, s, 0 );
        affine.rotate(0, Math.PI/10);
//        affine.rotate(1, 0.5);
        affine.translate(0, h/20, 0);

        RealRandomAccessible<FloatType> warp = RealViews.affine(interpolated, affine);

        RandomAccessibleInterval<FloatType> view = Views.interval(Views.raster(warp), img);
        RandomAccessibleInterval<FloatType> slice = Views.hyperSlice(view, 0,0);

//        ImageJFunctions.show(slice);

        showVolume(view);

//        Img<FloatType> projection = mip(Views.interval(Views.raster(perspective), img));
//        ImageJFunctions.show(projection);

//        RealARGBConverter<LongType> converter = new RealARGBConverter<>(0, 400);
//        final Interval interval = Intervals.createMinMax( -2, -2, -2, 2, 2, 2 );
//        InteractiveRealViewer3D viewer = new InteractiveRealViewer3D(200, 150, interpolated, interval, converter);
    }
}
