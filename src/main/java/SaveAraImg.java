import img.AraImgPlus;
import io.AraIO;
import io.AraMapping;

import net.imagej.Dataset;
import net.imagej.DefaultDataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;
import io.scif.services.DatasetIOService;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.io.IOException;

/**
 * Saving a ARA.SEC file.
 *
 * This file contains the mapping of a given section to the ABA CCF.
 *
 * The pixel data is never overwritten! This is because it should not be changed during the alignment.
 * In case the orignal file disappeared during the manipulation, the user is prompted to save it somewhere.
 *
 * The mapping data can be overwritten and the user will be prompted if necessary.
 *
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "File > Save As > ARA.SEC")
public class SaveAraImg extends AraIO implements Command {

    @Parameter(label = "section image")
    private ImgPlus secImg;


    @Parameter
    private LogService log;

    @Parameter
    private UIService ui;

    @Parameter
    private DatasetIOService dsio;


    @Override
    public void run() {

        if (secImg instanceof AraImgPlus) {
            AraImgPlus ara = (AraImgPlus) secImg;
            File imgPath = new File(ara.getSource());

            // In case the data that was opened disappeared while working on it
            if (!imgPath.exists()) {
                imgPath = ui.chooseFile(imgPath, FileWidget.SAVE_STYLE);
                if (imgPath == null) {
                    log.info("Saving ARA.SEC aborted.");
                    return;
                }
            }

            try {
                if (!imgPath.exists()) {
                    log.info("Saving pixel data to: " + imgPath.getAbsolutePath());
                    Dataset dataset = new DefaultDataset(dsio.getContext(), secImg);
                    dsio.save(dataset, imgPath.getAbsolutePath());
                }

                File mapFile = deriveMappingFile(imgPath);
                if (mapFile.exists()) {
                    DialogPrompt.Result answer =  ui.showDialog(mapFile.getName() + " exists. Overwrite?",
                            DialogPrompt.MessageType.QUESTION_MESSAGE, DialogPrompt.OptionType.YES_NO_OPTION);
                    if (!answer.equals(DialogPrompt.Result.YES_OPTION)) {
                        return;
                    }
                }
                log.info("Saving mapping metadata " + mapFile.getAbsolutePath());
                AraMapping metadata = ara.getAraMapping();
                metadata.save(mapFile);
                ara.setName(imgPath.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            ui.showDialog("The input image appears not to have any mapping yet. " +
                    "Use 'Plugins > Allen Brain Atlas > 2. Alignment > Interactive' to establish a mapping." +
                    "Otherwise the file can always be saved in another format.");
        }
    }

    public static void main(String[] args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();
    }
}
