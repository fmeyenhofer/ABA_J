ABA-J is an [ImageJ][ij] plugin to register 1 single or a series of 
subsequent histology sections to the [Allen Brain Atlas][aba] (ABA), 
an online ressource providing a reference space, 
annotations and access to many mapped genes. 
Once the section images are registered to the reference atlas, 
the plugin allows to retrieve annotations and use them as 
regions of interest (ROI's). 

# Installation
To use the plugin you need to download and install ImageJ first. We 
recommend the [Fiji][fiji] distribution, shipping with a collection of plugins
that will be useful in this context.

## Plugin installation from binaries
You can download the binary version of the plugin as jar-file 
from [here][bin]. For the installation of the plugin please refer to 
the ImageJ documentation [how to install 3rd party plugins][inst] on 
the official wiki.

## Plugin compilation from source 
To compile the source code and package it into a jar, that can be imported in ImageJ,
 you need to have [Git][git] and [Maven][mvn] installed on your computer. Then the following two commands should do:

```
git clone https://github.com/Meyenhofer/AllenJ

mvn package
```

After compilation the plugin (jar-file) can be dragged and dropped from the target folder on the imageJ main UI. 
After restarting, the plugin is available through the menu: Plugins > AllenJ

# Usage
Once installed in [Fiji][fiji], you can access a collection of functions through _Main Menu > Plugins > Allen Brain Atlas_

## Section to Volume Registration

## Annotation Importer

## Miscellaneous

### Section Auto-Crop

### Volume Slicer

### Reference Dataset Assembler 


## Slice to volume registration 
* the section should have approximately the same size as the reference volume (which one? automatic?)
* the SIFT feature extraction, more specifically the DOG filtering for the feature scaling, 
is very sensitive to image contrast. (add contrast adjustment to configuration dialog?)


[aba]: http://www.brain-map.org/
[ij]: http://imagej.net
[git]: https://git-scm.com/
[mvn]: https://maven.apache.org/
[inst]: http://imagej.net/Installing_3rd_party_plugins
[fiji]: http://imagej.net/Fiji/Downloads
[bin]:  https://github.com/Meyenhofer/ABA_J/release 
