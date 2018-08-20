package io;

import loci.formats.ImageReader;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;

/**
 * @author Felix Meyenhofer
 */
public class AraIO {

    protected static String DEFAULT_IMAGE_FORMAT = ".ome.tif";

    protected static String MAPPING_FILE_FORMAT = ".ara.map";

    protected static String FILE_TYPE_NAME = "ARA.SEC";
    

    public File deriveMappingFile(File imageFile) {
        File mapFile;
        if (imageFile.getName().contains(DEFAULT_IMAGE_FORMAT)) {
            mapFile = new File(imageFile.getAbsolutePath().replace(DEFAULT_IMAGE_FORMAT, MAPPING_FILE_FORMAT));
        } else {
            mapFile = new File(imageFile.getAbsolutePath().replaceAll("\\.?[a-zA-Z0-9]{1,3}$", MAPPING_FILE_FORMAT));
        }

        return mapFile;
    }

    protected File getMappingFile(File imageFile) throws FileNotFoundException {
        File mapFile = deriveMappingFile(imageFile);

        if (!mapFile.exists()) {
            throw new FileNotFoundException(mapFile.getName() + " does not exist in " + imageFile.getParent());
        }

        return mapFile;
    }

    protected File getImageFile(File mappingFile) throws FileNotFoundException {
        ImageReader bfreader = new ImageReader();
        String[] imageFileSuffixes = bfreader.getSuffixes();
        
        String fileTrunk = mappingFile.getName().replace(MAPPING_FILE_FORMAT, "");
        File workingDirectory = mappingFile.getParentFile();

        File[] imageFiles = workingDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String fileName = pathname.getName();
                return hasSupportedSuffix(fileName, fileTrunk, imageFileSuffixes);
            }
        });

        if (imageFiles == null || imageFiles.length < 1) {
            throw new FileNotFoundException("Could not find a known image file matching " + mappingFile.getName() +
                    " in " + mappingFile.getParent());
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

    private boolean hasSupportedSuffix(String fileName, String trunk, String[] suffixes) {
        for (String suffix : suffixes) {
            if (fileName.equals(trunk + "." + suffix)) {
                return true;
            }
        }
        return false;
    }
}
