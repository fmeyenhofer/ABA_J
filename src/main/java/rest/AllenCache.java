package rest;

import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;

import javax.xml.transform.TransformerException;

/**
 * Cache functionality for data from the Allen Brain Atlas API used by
 * the {@link AllenClient}.
 * Every connection from the client to the ABA server should go through here
 * in order to cache the queries. This way a given query will be only requested
 * from the server exactly once and then it will be save to the local machine.
 * This way the server traffic is minimized and io-speed is optimized.
 *
 * The conventions of the ABA API are given by the {@link AllenAPI}.
 *
 * The cache also takes care to organize the downloaded data.
 * |-cache-root
 *      |- {@link AllenCache#root}/{@link DataType#rma}
 *          |- [query-url].xml
 *          |- [query-url].json
 *          |- ...
 *      |- AllenCache#root}/{@link DataType#img}
 *          |- [product-abbreviation]/
 *              |- [dataset-id]
 *                  |- [image-id].jpg
 *                  |- ...
 *              |- ...
 *          |- ...
 *      |- {AllenCache#root}/{@link DataType#svg}
 *          |- [product-abbreviation]/
 *              |- [dataset-id]
 *                  |- [image-id].svg
 *                  |- ...
 *              |- ...
 *          |- ...
 *      |- AllenCache#root}/{@link DataType#xml}
 *          |- [product-abbreviation]/
 *              |- [dataset-id]
 *                  |- [image-id].xml
 *                  |- ...
 *              |- ...
 *          |- ...
 *
 * @author Felix Meyenhofer
 */
public class AllenCache {

    /** Root directory of the cache */
    private File root;

    /**
     * Organization of the different data types encountered with the
     * {@link AllenAPI}
     */
    public enum DataType {
        img("images", AllenAPI.Download.Image.FILE_EXTENSION),
        svg("annotations", AllenAPI.Download.SVG.FILE_EXTENSION),
        xml("metadata", AllenAPI.RMA.FILE_EXTENSION),
        rma("rma", AllenAPI.RMA.FILE_EXTENSION),
        grd("expression-grid", AllenAPI.Download.GRID.FILE_EXTENSION),
        vol("reference-volumes", AllenAPI.Download.RefVol.FILE_EXTENSION);

        private String subdir;
        private String extension;

        DataType(String subdirectory, String fileextention) {
            this.subdir = subdirectory;
            this.extension = fileextention;
        }

        String getSubdirectory() {
            return this.subdir;
        }

        String getFileExtension() {
            return this.extension;
        }
    }

    /**
     * Constructor
     */
    public AllenCache() {
        //TODO put this in the fiji user settings. Use a setting dialog if not defined
        this.root = getDirectory(new File(System.getProperty("user.home"), "allen-cache"));
    }

    /**
     * Getter for the directories
     * The {@link AllenCache#root} contains subdirectories for each type of data.
     *
     * @param type data type (each data type has its own sub-directory)
     * @return subdirectory for a given data type
     */
    public File getDirectory(DataType type) {
        return new File(this.root, type.getSubdirectory());
    }

    /**
     * Build a directory for a given data {@param type} creating
     * subdirectories with the {@param directory_names}
     *
     * @param type of the data
     * @param directory_names names of the sub-directories
     * @return resulting directory
     */
    private File getDirectory(DataType type, String... directory_names) {
        File directory = new File(this.root, type.getSubdirectory());

        for (String dirname : directory_names) {
            directory = new File(directory, dirname);
        }

        return getDirectory(directory);
    }

    /**
     * get a directory in the cache and makes sure that it exists.
     *
     * @param dir_name directory name
     * @return a valid directory in the cache
     */
    private File getDirectory(File dir_name) {
        if (!dir_name.exists()) {
            boolean status = dir_name.mkdirs();
            if (!status) {
                throw new RuntimeException("Could not create the directory '" +
                        dir_name.getAbsolutePath() + "'.");
            }
        }

        return dir_name;
    }

    /**
     * Create file path for a given data {@param type}.
     * The {@param path_parts} are typically id's of the data model classes,
     * e.g. /[cache-dir]/[product-id]/[dataset-id/[image-id].[type-file-extension]
     *
     * @param type of the data
     * @param path_parts parts of the file path
     * @return {@link File}
     */
    public File getPath(DataType type, String... path_parts) {
        int end = path_parts.length - 1;
        String filename = path_parts[end];

        if (!path_parts[end].contains(".")) {
            filename += type.getFileExtension();
        }

        File directory = getDirectory(type, Arrays.copyOfRange(path_parts, 0, end));

        return new File(directory, filename);
    }

    /**
     * Get the response for a query url either from the cache or from the www.
     *
     * @param url query url
     * @return {@link AllenXml} response file
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
     */
    public AllenXml getResponseXml(URL url)
            throws IOException, TransformerException, URISyntaxException {

        url = AllenAPI.RMA.adjustResponseSize(url);
        String filename = AllenAPI.RMA.url2filename(url);
        File path = getPath(DataType.rma, filename);

        if (path.exists()) {
            return new AllenXml(path);
        } else {
            return new AllenXml(url, path);
        }
    }

    /**
     * Get the meta data for a given image file.
     * The parts usually go something like [product abbreviation]/[dataset id]/[image id].
     *
     * @param path_parts subdirectories in the cache.
     * @return {@link AllenXml} containing the metadata
     * @throws IOException
     * @throws URISyntaxException
     */
    public AllenXml getMetadataXml(String... path_parts)
            throws IOException, URISyntaxException, TransformerException {
        File file = getPath(DataType.xml, path_parts);

        if (file.exists()) {
            return new AllenXml(file);
        } else {
            int level = path_parts.length;
            URL query;
            if (level == 2) {
                query = AllenAPI.RMA.createSectionDataSetsQuery(Integer.parseInt(path_parts[1]));
            } else if (level == 3) {
                query = AllenAPI.RMA.createSectionImageQuery(path_parts[2]);  //TODO: this is not a general solution for different data models
            } else {
                throw new IOException("The " + AllenCache.class + "cannot retrieve the metadata for " + path_parts);
            }

            query = AllenAPI.RMA.adjustResponseSize(query);
            return new AllenXml(query, file);
        }
    }

    /**
     * Save an xml element to a metadata file
     *
     * @param element xml content
     * @param path_parts name(s) of folders plus the file name
     * @throws TransformerException
     * @throws IOException
     */
    public AllenXml getMetadataXml(Element element, String... path_parts)
            throws TransformerException, IOException, URISyntaxException {
        File path = getPath(DataType.xml, path_parts);
        if (path.exists()) {
            return new AllenXml(path);
        } else {
            return new AllenXml(element, path);
        }
    }

    /**
     * Get an image either from the cache or from the ABA API.
     *
     * @param path_parts parts of the path of the image.
     *                   The last one is expected to be the image id.
     *                   The rest (subdirectories) is just to have a meaningful structure of the cache
     * @return {@link AllenImage} file
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
     */
    public AllenImage getImage(String... path_parts)
            throws IOException, TransformerException, URISyntaxException {

        return getImage(AllenAPI.Download.ARG_DOWNSAMPLE_DEFAULT, AllenAPI.Download.ARG_QUALITY_DEFAULT, path_parts);
    }

    /**
     * Get an image either from the cache or from the ABA API
     *
     * @param downsample downsampling of the image [0...], 1 -> 0.5, 2 -> 0.25, etc
     * @param quality jpeg quality [0...100]
     * @param path_parts parts of the image file path. The last part is the file name.
     *                   Downsample and quality are inserted before.
     * @return {@link AllenImage} jpeg file
     * @throws IOException
     * @throws URISyntaxException
     * @throws TransformerException
     */
    public AllenImage getImage(int downsample, int quality, String... path_parts)
            throws IOException, URISyntaxException, TransformerException {

        int n = path_parts.length;
        String[] new_parts = new String[n + 2];
        System.arraycopy(path_parts, 0, new_parts, 0, n - 1);
        new_parts[n - 1] = "downsample-" + Integer.toString(downsample);
        new_parts[n] = "quality-" + Integer.toString(quality);
        new_parts[n + 1] = path_parts[n - 1];

        File path = getPath(DataType.img, new_parts);

        if (path.exists()) {
            return new AllenImage(path);
        } else {
            int end = path_parts.length - 1;
            String image_id = path_parts[end].replace(AllenAPI.Download.Image.FILE_EXTENSION, "");
            URL query = AllenAPI.Download.Image.createImageUrl(image_id, downsample, quality);
            return new AllenImage(query, path);
        }
    }

    /**
     * Get the svg annotation file
     *
     * @param path_parts parts of the path to the image file.
     *                   The last part is expected to be the svg id.
     * @return {@link AllenSvg} file
     * @throws IOException
     * @throws TransformerException
     * @throws URISyntaxException
     */
    public AllenSvg getAnnotationSvg(String... path_parts)
            throws IOException, TransformerException, URISyntaxException {
        File file = getPath(DataType.svg, path_parts);

        if (file.exists()) {
            return new AllenSvg(file);
        } else {
            int end = path_parts.length - 1;
            String section_id = path_parts[end].replace(AllenAPI.Download.SVG.FILE_EXTENSION, "");
            URL url = AllenAPI.Download.SVG.createSvgUrl(section_id);
            return new AllenSvg(url, file);
        }
    }

    /**
     * Get expression grid data.
     * (they are stored in the expression grid data folders,
     * file names are [ID].zip)
     *
     * @param grid_id ID of the expression grid
     * @return expression grid as {@link AllenImage}
     * @throws IOException
     * @throws URISyntaxException
     * @throws TransformerException
     */
    public AllenImage getExpressionGrid(String... grid_id)
            throws IOException, URISyntaxException, TransformerException {
        File file = getPath(DataType.grd, grid_id);

        if (file.exists()) {
            return new AllenImage(file);
        } else {
            int end = grid_id.length - 1;
            String dataset_id = grid_id[end].replace(AllenAPI.Download.GRID.FILE_EXTENSION, "");
            URL url = AllenAPI.Download.GRID.createUrl(dataset_id);
            return new AllenImage(url, file);
        }
    }

    /**
     * Get the absolute path to a reference volume.
     * For convenience this allows to define the parameter as Strings
     * in contrast to {@link AllenCache#getReferenceVolume(AllenAPI.Download.RefVol.DataType, AllenAPI.Download.RefVol.VoxelResolution)}
     *
     * @param modality of the data (see {@link AllenAPI.Download.RefVol.DataType})
     * @param resolution voxel resolution in micron of the image (see {@link AllenAPI.Download.RefVol.VoxelResolution}
     * @return {@link File} of the reference volume image file
     */
    public File getReferenceVolume(String modality, String resolution) {
        String filename = AllenAPI.Download.RefVol.getFileName(modality, resolution);
        return getPath(DataType.vol, filename);
    }

    /**
     * Get the reference volume data set
     *
     * @param type data type of the volume (see{@link AllenAPI.Download.RefVol.DataType})
     * @param resolution voxel resolution (see {@link AllenAPI.Download.RefVol.VoxelResolution}
     * @return {@link File} of the reference volume image file
     * @throws IOException
     * @throws URISyntaxException
     * @throws TransformerException
     */
    public AllenImage getReferenceVolume(AllenAPI.Download.RefVol.DataType type,
                                         AllenAPI.Download.RefVol.VoxelResolution resolution)
            throws IOException, URISyntaxException, TransformerException {
        String filename = AllenAPI.Download.RefVol.createFileName(type, resolution);
        File path = getPath(DataType.vol, filename);

        if (path.exists()) {
            return new AllenImage(path);
        } else {
            URL query = AllenAPI.Download.RefVol.createUrl(type, resolution);
            return new AllenImage(query, path);
        }
    }
}
