import gui.AlphaNumericComparator;
import gui.OrderedListSelectionDialog;
import img.AraImgPlus;
import io.AraIO;
import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import rest.AllenRefVol;

import javax.naming.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TODO: multi thread it (at least the maximum projection - the mapping is trickier)
 *
 * @author Felix Meyenhofer
 */
@SuppressWarnings("unused")
@Plugin(type = Command.class, menuPath = "Plugins > Allen Brain Atlas > 4. Mapping > | Batch > Sections to ARA")
public class MapSection2AraBatch <T extends RealType<T> & NativeType<T>> extends AraIO implements Command{

    @Parameter(type = ItemIO.OUTPUT)
    private ImgPlus warp;


    @Parameter
    private LogService log;

    @Parameter
    private StatusService status;

    @Override
    public void run() {
        List<String> items = getImageDisplays();

        if (items.size() == 0) {
            log.info("There are no open " + FILE_TYPE_NAME + " files");
        }

        // Get stuff from directories (in case we got no open images)
        if (items.size() == 0) {
            items = getMappedImagePaths(new File(items.get(0)));
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
                AraImgPlus sec = getImage(selected);

                message = "... mapping section " + (n+1) + " of " + N;
                status.showStatus(n++, N, message);
                log.info(message + " " + selected);
                Img<T> vol = sec.mapSection2Template();
//                ui.show(vol);
                vols.add(vol);
            }

            // Take the maximum of the section volumes
            List<Cursor<T>> cursors = new ArrayList<>(N);
            for (Img<T> vol : vols) {
                cursors.add(Views.flatIterable(vol).cursor());
            }

            ArrayImgFactory<T> factory = new ArrayImgFactory(vols.get(0).firstElement());
            Img<T> rec = factory.create(vols.get(0));
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
}
