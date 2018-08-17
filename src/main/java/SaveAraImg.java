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

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Felix Meyenhofer
 */
@Plugin(type = Command.class, menuPath = "File > Save As > ARA.SEC")
public class SaveAraImg extends AraIO implements Command {

    @Parameter(label = "section image")
    ImgPlus secImg;


    @Parameter
    LogService log;

    @Parameter
    UIService ui;

    @Parameter
    DatasetIOService dsio;


    @Override
    public void run() {

        if (secImg instanceof AraImgPlus) {
            AraImgPlus ara = (AraImgPlus) secImg;
//            File imgFile = ui.chooseFile(new File(ara.getName() + AraIO.DEFAULT_IMAGE_FORMAT), FileWidget.SAVE_STYLE);
            File currentFile = new File(ara.getSource());
            JFileChooser dialog = new JFileChooser();
            dialog.setCurrentDirectory(currentFile.getParentFile());
            dialog.setSelectedFile(new File(currentFile.getName()));
            int status = dialog.showSaveDialog(null);

            if (status != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File imgFile = dialog.getSelectedFile();
            

            try {
                if (imgFile.exists()) {
                    log.info("Skipped pixel data saving: " + imgFile.getAbsolutePath() + " already exists.");
                } else {
                    log.info("Saving pixel data to: " + imgFile.getAbsolutePath());
                    Dataset dataset = new DefaultDataset(dsio.getContext(), secImg);
                    dsio.save(dataset, imgFile.getAbsolutePath());
                }

                File mapFile = deriveMappingFile(imgFile);
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
                ara.setName(imgFile.getName());
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
