import ij.ImagePlus;
import ij.io.FileSaver;

import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;

import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.view.IntervalView;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.display.DisplayService;
import org.scijava.io.IOService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > Batch Processing > Sections Auto-Crop")
public class SectionsAutoCropBatch implements Command {

    @Parameter(label = "Input directory", style = FileWidget.DIRECTORY_STYLE)
    private File inDir;

    @Parameter(label = "Output directory", style = FileWidget.DIRECTORY_STYLE)
    private File ouDir;

    @Parameter(label = "Extension filter")
    private String extension = ".tif";

    @Parameter(label = "Gaussian smoothing (sigma)")
    private double sigma = 13.0;

    @Parameter(label = "Closing radius (morph.)")
    private int radius = 3;

    @Parameter(label = "Crop margin")
    private long margin = 10;


    @Parameter
    private IOService ioService;

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
        if (inDir.getAbsolutePath().equals(ouDir.getAbsolutePath())) {
            logService.error("BatchSectionAutoCrop: Input and output directory cannot be the same. Aborted.");
            return;
        }

        File[] fileList = inDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getAbsolutePath().endsWith(extension);
            }
        });

        if (fileList == null) {
            logService.info("BatchSectionAutoCrop: Found no " + extension.substring(1) + "-files.");
            return;
        }

        logService.info("BatchSectionAutoCrop: Found " + fileList.length + " " + extension.substring(1) + "-files");

        int s = 0;
        int N = fileList.length;
        statusService.clearStatus();
        for (File file : fileList) {
            try {
                statusService.showProgress(s, N);
                logService.info("BatchSectionAutoCrop:\tprocessing " + file.getName());
                File ouFile = new File(ouDir, file.getName());
                if (ouFile.exists()) {
                    logService.info("BatchSectionAutoCrop:\tfile" + ouFile.getAbsolutePath() + " already exists");
                } else {
                    Dataset dataset = (Dataset) ioService.open(file.getAbsolutePath());
                    IntervalView img = SectionAutoCrop.autoCrop(dataset, margin, sigma, radius,
                            statusService, opService, logService, displayService);
                    ImagePlus imp = ImageJFunctions.wrapUnsignedByte(img, "lsls");
                    FileSaver saver = new FileSaver(imp);
                    saver.saveAsTiff(ouFile.getAbsolutePath());
//                    ioService.save(img, ouFile.getAbsolutePath());
                    logService.info("BatchSectionAutoCrop:\tsaved " + ouFile.getAbsolutePath());
                }
            } catch (IOException e) {
                logService.error(e);
                statusService.clearStatus();
                return;
            }
        }
        statusService.showProgress(N,N);
        statusService.clearStatus();
    }

    public static void main(String[] args) {
        ImageJ ij = net.imagej.Main.launch(args);
        ij.command().run(SectionsAutoCropBatch.class, true);
    }
}
