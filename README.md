[toc]

ABA-J is an [ImageJ][ij2] plugin to register 1 single or a series of 
subsequent histology sections to the [Allen Brain Atlas][aba] (ABA), 
an online resources providing a reference space, 
annotations and access to many mapped genes. 
Once the section images are registered to the reference atlas, 
the plugin allows to retrieve annotations and use them as 
regions of interest (ROI's). 


| Acronym | Description                 | 
|:-------:|:---------------------------:|
| CCF     | Common Coordinate Framework | 
| ARA     | Allen Reference Atlas       |


# Installation
To use the plugin you need to download and install ImageJ first. We 
recommend the [Fiji][fiji] distribution, shipping with a collection of plugins
that will be useful in this context.

## Plugin installation in ImageJ
You can download the binary version of the plugin as jar-file 
on the [releases][bin] page. For the installation of the plugin please refer to 
the ImageJ documentation [how to install 3rd party plugins][inst] on 
the official wiki.

## Plugin compilation from source 
To compile the source code and package it into a jar, that can be imported in ImageJ, clone this repository using [Git][git]. Then the jar can be easily packages using [Maven][mvn] with the command `mvn package`.

# Usage
Once installed in [Fiji][fiji], you can access a collection of functions through `Main Menu > Plugins > Allen Brain Atlas`

__Note__: This neuroinformatics plugin performs computationally intense operations and deals with a considerable amount of data
(input and web resources). Therefore the users are advised to run the software on a adequate machine with a configuration
that fulfils the following requirements:

* fast internet connection (LAN)
* a good CPU
* enough memory (at least 8 GB) 


[aba]: http://www.brain-map.org/
[ij2]: http://imagej.net
[git]: https://git-scm.com/
[mvn]: https://maven.apache.org/
[inst]: http://imagej.net/Installing_3rd_party_plugins
[fiji]: http://imagej.net/Fiji/Downloads
[bin]:  https://github.com/fmeyenhofer/ABA_J.git/releases
