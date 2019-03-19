//#@ File(label='input directory', style='directory') inputDir
//#@ String(label='file extension', value='ome.tif') extension
//#@ File(label='output directory', style='directory') outputDir

import ij.IJ
import net.imagej.Dataset
import net.imagej.ImageJ
import net.imglib2.RandomAccessibleInterval
import net.imglib2.img.display.imagej.ImageJFunctions
import org.scijava.io.IOService


ij = new ImageJ().
ij.get()




def fileList = inputDir.listFiles(new FilenameFilter() {
    @Override
    boolean accept(File dir, String name) {
        return name.endsWith(extension)
    }
})


for (File file : fileList) {
	log.info(file)
    Dataset ds = (Dataset) ij.io().open(file.getAbsolutePath())
	println(ds)
    imp = ImageJFunctions.wrap(ds.getImgPlus().getImg() as RandomAccessibleInterval, 'input')

    println(imp)
    imp.show()

    out = ij.command().run(SectionImagePreprocessing.class, true,
            'inputsection', ds.getImgPlus().toString(),
            'margin', '50',
            'masksection', 'true',
            'showmask', 'false')
            .get()

    println(out)
//    IJ.run("Orient + Auto-Crop", "inputsection=" + ds.getImgPlus().toString() + " margin=50 masksection=true showmask=false")
//    imp = IJ.getImage()
//    ij.io().save(imp, new File(outputDir, file.getName()).getAbsolutePath())
	break
    ds.close()
}