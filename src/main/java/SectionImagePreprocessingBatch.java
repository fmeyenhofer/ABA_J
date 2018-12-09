import ij.IJ;
import ij.ImagePlus;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 1. Pre-Processing > | Batch > Orient + Auto-Crop Sections")
public class  SectionImagePreprocessingBatch<T extends RealType<T> & NativeType<T>> implements Command {

    @Parameter
    private IOService io;

    @Parameter
    private OpService ops;

    @Parameter
    private UIService uis;

    @Parameter
    private StatusService status;

    @Parameter
    private LogService log;


    @Parameter(label = "Input directory", style = "directory")
    private File inputDir;

    @Parameter(label = "File extension")
    private String extension;

    @Parameter(label = "Crop margin")
    private long margin = 50;

    @Parameter(label = "Mask section image")
    private boolean maskSection = true;

    @Parameter(label = "Output directory", style = "directory")
    private File outputDir;


    @Override
    public void run() {

        File[] fileList = inputDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(extension);
            }
        });
        if (fileList == null) {
            status.warn("Could not find any " + extension + "-files in " + inputDir);
            return;
        }

        int n = 0;
        for (File file : fileList) {
            try {
                log.info("processing file " + file);

                File outPath = new File(outputDir, file.getName());
                if (outPath.exists()) {
                    log.info("... " + outPath);
                    log.info("... already exists");
                    continue;
                }

                status.showStatus(n++, fileList.length, "pre-processing files");
                ImgPlus img = ((Dataset) io.open(file.getAbsolutePath())).getImgPlus();
                ImgPlus<T> out = SectionImagePreprocessing.cropAndRotate(img,
                        margin, maskSection, false, ops, uis, status);
                ImagePlus imp = ImageJFunctions.wrap(out.getImg(), outPath.getName());
                IJ.saveAs(imp, "ome.tif", outPath.getAbsolutePath());
//                io.save(out, outPath.getAbsolutePath());
                log.info("saved file " + outPath);

            } catch (Exception e) {
                log.info("... error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        ij.command().run(SectionImagePreprocessingBatch.class, true);
    }
}
