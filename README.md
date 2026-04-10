Run [DeXtrusion](https://github.com/Image-Analysis-Hub/DeXtrusion) in Fiji thanks to [Appose](https://github.com/apposed/)

DeXtrusion is a python module to detect cellular events like cell extrusions in epithelia movies.

## Installation

You can install the plugin for the unliste update site `Appose-Playground`:
in Fiji, go to `Help>Update...` then to `Manage Update Sites` in the window that opens.
Click `Add unliste update site`, name it `Appose-Playground` and write its address `https://sites.imagej.net/Appose-Playground`.

Select the DeXtrusion_Appose-* `.jar` file to install only this plugin, or keep all proposed plugins.
Press `Apply changes` and restart Fiji when it's done.

> [!NOTE]
> You should have a recent version of Fiji, based on Java 21 or more. Download a new version if you're current installation is too old.

## Usage

You can either run it on one opened image or on a batch of images.
You can also record calling DeXtrusion and runs it in a macro.

### Current image
To run it on one image, first open the image in Fiji, then starts the plugin in `Plugins>DeXtrusion>Detect events`

Select `current image` in `Process`

### Batch images
To run it on several images in batch mode, starts the plugin in `Plugins>DeXtrusion>Detect events`

Select `several images (batch)` in `Process`. 
When you will click on `Ok`, another window will pop-up to let you select the images to process.


### Events detection
First open the movie that you want to analyze.
Go to `Plugins>Dextrusion>Detect events`

The program will first ask you to choose the parameters to run `DeXtrusion` on your movie.
The parameters are:

* `cell_diameter`: typical cell diameter. This parameter is used to resize the movie if necessary, so that a cell is the same cell as the cells used in the training data (around 25 pixels). 
* `extrusion_duration`: typical duration of a cell extrusion in the biological tissu, in frames. This is more or less the number of frames in which an extrusion event is visible. This parameter is used to resize temporally the movie is necessary, so that it fits the training data temporal resolution.

### Changing the model
The plugin will run the detection with the default DeXtrusion models, `notumAll` that detect `cell_death`, `cell_division` events and `cell_SOP` probability.

You can change the used model, to select another one from DeXtrusion github or a customed model by going in `Plugins>DeXtrusion>Set advanced parameters`.
