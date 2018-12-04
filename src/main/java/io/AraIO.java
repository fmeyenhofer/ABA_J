package io;

import img.AraImgPlus;
import io.scif.services.DatasetIOService;
import loci.formats.ImageReader;
import net.imagej.Dataset;
import net.imagej.display.ImageDisplay;
import net.imagej.display.ImageDisplayService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UIService;

import javax.naming.ConfigurationException;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Abstract class for IO plugins.
 *
 * These methods allow to 'tie' together the different files (image, mapping, result).
 * By convention results an mapping files differ from the image file only by their file extensions
 *
 * @author Felix Meyenhofer
 */
public class AraIO {

    @Parameter
    protected UIService ui;

    @Parameter
    protected DatasetIOService dsio;

    @Parameter
    protected ImageDisplayService imdise;


    @SuppressWarnings("FieldCanBeLocal")
    private static String DEFAULT_IMAGE_FORMAT = ".ome.tif";

    protected static String MAPPING_FILE_FORMAT = ".ara.map";

    protected static String FILE_TYPE_NAME = "ARA.SEC";

    private static String[] RESULT_FILE_FORMATS = new String[]{".ara.txt", ".txt",".ara.csv", ".csv"};


    protected AraImgPlus getImage(String id) throws ConfigurationException, IOException, ClassNotFoundException {
        // Try to fetch the image among the open displays
        for (ImageDisplay display : imdise.getImageDisplays()) {
            Dataset dataset = imdise.getActiveDataset(display);
            if (dataset.getImgPlus() instanceof AraImgPlus) {
                if (display.getActiveView().getData().getName().equals(id)) {
                    return (AraImgPlus) dataset.getImgPlus();
                }
            }
        }

        // Open the image from file
        File pixFile = new File(id);
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

    protected List<String> getImageDisplays() {
        List<String> displays = new ArrayList<>();

        if (imdise.getImageDisplays().size() > 0) {
            for (ImageDisplay display : imdise.getImageDisplays()) {
                Dataset dataset = imdise.getActiveDataset(display);
                if (dataset.getImgPlus() instanceof AraImgPlus) {
                    displays.add(display.getActiveView().getData().getName());
                }
            }
        }

        return displays;
    }

    protected List<String> getMappedImagePaths() {
        // Get the map files
        File inputDirectory = ui.chooseFile(new File(System.getProperty("user.home")), "directory");
        File[] mapFiles = inputDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File path) {
                return path.getAbsolutePath().endsWith(MAPPING_FILE_FORMAT);
            }
        });

        // Check if we can find the corresponding image files to the mappings
        List<String> paths = new ArrayList<>();
        if (mapFiles == null) {
            ui.showDialog("Could not find any " + MAPPING_FILE_FORMAT + " files in " + inputDirectory);
            return null;
        } else {
            for (File file : mapFiles) {
                try {
                    File imgFile = getImageFile(file);
                    paths.add(imgFile.getAbsolutePath());
                } catch (FileNotFoundException e) {
                    ui.showDialog(file.getName() + " cannot be associated with any image file.\n" +
                            "Check our data directory" + file.getParent());
                    return null;
                }
            }
        }

        if (paths.size() == 0) {
            ui.showDialog("Could not find any " + FILE_TYPE_NAME + " files in " + inputDirectory);
            return null;
        }

        return paths;
    }

    public File deriveMappingFile(File imageFile) {
        File mapFile;
        if (imageFile.getName().contains(DEFAULT_IMAGE_FORMAT)) {
            mapFile = new File(imageFile.getAbsolutePath().replace(DEFAULT_IMAGE_FORMAT, MAPPING_FILE_FORMAT));
        } else {
            mapFile = new File(imageFile.getAbsolutePath().replaceAll("\\.?[a-zA-Z0-9]{1,3}$", MAPPING_FILE_FORMAT));
        }

        return mapFile;
    }

    protected File deriveResultFile(File file) {
        File workingDirectory = file.getParentFile();
        String trunk = removeFileExtension(file.getName());

        File resultFile;
        for (String extension : RESULT_FILE_FORMATS) {
            resultFile = new File(workingDirectory, trunk + extension);
            if (resultFile.exists()) {
                return resultFile;
            }
        }

        // If no result file exists yet, go with the default extension
        return new File(workingDirectory, trunk + RESULT_FILE_FORMATS[0]);
    }

    private String[] getImageSuffixes() {
        ImageReader bfreader = new ImageReader();
        String[] allSuffixes = bfreader.getSuffixes();

        List<String> bannedSuffixes = new ArrayList<>();
        for (String str : RESULT_FILE_FORMATS) {
            bannedSuffixes.add(str.replaceFirst("^\\.", ""));
        }
        bannedSuffixes.add(MAPPING_FILE_FORMAT.replaceFirst("^\\.", ""));

        List<String> nonConflictingSuffixes = new ArrayList<>();
        for (String suffix : allSuffixes) {
            if (bannedSuffixes.contains(suffix)) {
                continue;
            }
            nonConflictingSuffixes.add(suffix);
        }

        return nonConflictingSuffixes.toArray(new String[0]);
    }

    protected File getImageFile(File metaFile) throws FileNotFoundException {
        String[] imageFileSuffixes = getImageSuffixes();

        File workingDirectory = metaFile.getParentFile();
        String fileTrunk = removeFileExtension(metaFile.getName());

        File[] imageFiles = workingDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String fileName = pathname.getName();
                return hasSupportedSuffix(fileName, fileTrunk, imageFileSuffixes);
            }
        });

        if (imageFiles == null || imageFiles.length < 1) {
            throw new FileNotFoundException("Could not find a known image file matching " + metaFile.getName() +
                    " in " + metaFile.getParent());
        } else if (imageFiles.length > 1) {
            StringBuilder fileList = new StringBuilder();
            for (File file : imageFiles) {
                fileList.append("\t").append(file.getName()).append("\n");
            }
            throw new FileNotFoundException("Ambiguous image file matching. Got " +
                    imageFiles.length + " candidates:\n" + fileList);
        } else {
            return imageFiles[0];
        }
    }

    private String removeMappingExtension(String fileName) {
        List<String> exts = new ArrayList<>();
        exts.add(MAPPING_FILE_FORMAT);

        return removeFileExtension(fileName, exts);
    }

    private String removeResultExtension(String fileName) {
        return removeFileExtension(fileName, Arrays.asList(RESULT_FILE_FORMATS));
    }

    private String removeImageExtension(String fileName) {
        String[] suffixes = getImageSuffixes();

        return removeFileExtension(fileName, Arrays.asList(suffixes));
    }

    private String removeFileExtension(String fileName) {
        String trunk = removeMappingExtension(fileName);

        if (trunk == null) {
            trunk = removeResultExtension(fileName);
        }

        if (trunk == null) {
            trunk = removeImageExtension(fileName);
        }

        if (trunk == null) {
            throw new RuntimeException("Could not remove file extension for " + fileName);
        }

        return trunk;
    }

    private String removeFileExtension(String fileName, List<String> extensions) {
        String fileTrunk = fileName;
        int initialLength = fileName.length();

        for (String extension : extensions) {
            String pattern = extension.replace(".", "\\.") + "$";
            fileTrunk = fileTrunk.replaceFirst(pattern, "");
            if (initialLength > fileTrunk.length()) {
                return fileTrunk.replaceFirst("\\.$", "");
            }
        }

        return null;
    }

    private boolean hasSupportedSuffix(String fileName, String trunk, String[] suffixes) {
        for (String suffix : suffixes) {
            if (fileName.equals(trunk + "." + suffix)) {
                return true;
            }
        }
        return false;
    }
}
