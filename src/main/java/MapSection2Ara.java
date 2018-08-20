import io.AraIO;
import io.AraMapping;
import img.AraImgPlus;
import rest.AllenRefVol;
import gui.AlphaNumericComparator;
import gui.OrderedListSelectionDialog;

import net.imagej.ImageJ;
import net.imagej.Dataset;
import net.imagej.ImgPlus;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import net.imglib2.img.Img;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.UIService;
import org.scijava.log.LogService;
import org.scijava.app.StatusService;
import io.scif.services.DatasetIOService;

import javax.naming.ConfigurationException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO: make it possible to output a 2D section instead of a volume
 * TODO: multi thread it (at least the maximum projection - the mapping is trickier)
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > Section(s) to ARA")
public class MapSection2Ara<T extends RealType<T> & NativeType<T>> extends AraIO implements Command {

//    @Parameter(label = "section image")
//    private ImgPlus section;

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus warp;


    @Parameter
    private UIService ui;

    @Parameter
    private LogService log;

    @Parameter
    private StatusService status;

    @Parameter
    private DatasetIOService dsio;

    @Parameter
    private ImageDisplayService disp;


    @Override
    public void run() {
        List<String> items = new ArrayList();

        if (disp.getImageDisplays().size() > 0) {
            for (ImageDisplay display : disp.getImageDisplays()) {
                Dataset dataset = disp.getActiveDataset(display);
                if (dataset.getImgPlus() instanceof AraImgPlus) {
                    items.add(display.getActiveView().getData().getName());
                }
            }

            if (items.size() == 0) {
                log.info("There are no open " + FILE_TYPE_NAME + " files");
            }
        }

        if (items.size() == 0) {
            File inputDirectory = ui.chooseFile(new File(System.getProperty("user.home")), "directory");
            File[] files = inputDirectory.listFiles(new FileFilter() {
                @Override
                public boolean accept(File path) {
                    return path.getAbsolutePath().endsWith(DEFAULT_IMAGE_FORMAT);
                }
            });

            if (files == null) {
                ui.showDialog("Could not find any " + DEFAULT_IMAGE_FORMAT + " files in " + inputDirectory);
                return;
            } else {
                for (File file : files) {
                    File mapFile = deriveMappingFile(file);
                    if (mapFile.exists()) {
                        items.add(file.getAbsolutePath());
                    }
                }
            }

            if (items.size() == 0) {
                ui.showDialog("Could not find any " + FILE_TYPE_NAME + " files in " + inputDirectory);
                return;
            }
        }

        items.sort(new AlphaNumericComparator());
        OrderedListSelectionDialog dialog = OrderedListSelectionDialog.createAndShow(items);
        if (dialog.isCancelled()) {
            log.info("Input selection was cancelled");
            return;
        }
        List<String> selection = dialog.getSelection();

        try {
            String message;
            Img secVol = null;
            long start = System.currentTimeMillis();
            int N = selection.size();
            int n = 0;
            status.showStatus(n, N, "Mapping sections to ARA");

            // Do mappings of the sections
            List<Img<T>> vols = new ArrayList<>(N);
            for (String selected : selection) {
                AraImgPlus sec = getAraImage(selected);

                message = "... mapping section " + (n+1) + " of " + N;
                status.showStatus(n++, N, message);
                log.info(message + " " + selected);

                vols.add(sec.mapSection2Template());
            }

            // Take the maximum of the section volumes
            List<Cursor<T>> cursors = new ArrayList<>(N);
            for (Img<T> vol : vols) {
                cursors.add(Views.flatIterable(vol).cursor());
            }

            ArrayImgFactory factory = new ArrayImgFactory();
            Img<T> rec = factory.create(vols.get(0), vols.get(0).firstElement());
            RandomAccess<T> randomAccess = rec.randomAccess();

            long P = rec.size();
            int p = 0;
            while (cursors.get(0).hasNext()) {
                double max = 0;
                for (Cursor<T> cursor : cursors) {
                    max = Math.max(cursor.next().getRealDouble(), max);
                }

                randomAccess.setPosition(cursors.get(0));
                randomAccess.get().setReal(max);
                
                if (p++ % 1000 == 0)
                status.showStatus(p, (int)P, "Assembling sections (max proj.)");
            }

            long stop = System.currentTimeMillis();
            message = "Mapped " + N + " sections in " + (stop - start) / 1000 + " sec.";
            status.showStatus(N, N, message);
            log.info(message);
            warp = new ImgPlus(rec, "Mapped section(s) 1-" + N, AllenRefVol.getAxes());
        } catch (ConfigurationException e) {
            log.error("The plugin configuration went wrong... should not happen");
            e.printStackTrace();
        } catch (IOException e) {
            log.error("Check the input files");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            log.error("Casting AraImgPlus f**ked up");
            e.printStackTrace();
        }
    }


    private AraImgPlus getAraImage(String str) throws ConfigurationException, IOException, ClassNotFoundException {
        // Try to fetch the image among the open displays
        for (ImageDisplay display : disp.getImageDisplays()) {
            Dataset dataset = disp.getActiveDataset(display);
            if (dataset.getImgPlus() instanceof AraImgPlus) {
                if (display.getActiveView().getData().getName().equals(str)) {
                    return (AraImgPlus) dataset.getImgPlus();
                }
            }
        }

        // Open the image from file
        File pixFile = new File(str);
        File mapFile = deriveMappingFile(pixFile);
        if (!mapFile.exists()) {
            throw new ConfigurationException("The plugin configuration let through the file " + pixFile
                    + " which turns out to have not ARA mapping. This should never occur");
        }

        Dataset dataset = dsio.open(pixFile.getAbsolutePath());
        AraMapping mapping = AraMapping.load(mapFile);

        AraImgPlus araImg = new AraImgPlus(dataset.getImgPlus().getImg(), mapping);
        araImg.setSource(pixFile.getAbsolutePath());
        araImg.setName(pixFile.getName());

        return araImg;
    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
    }
}
