Run [DeXtrusion](https://github.com/Image-Analysis-Hub/DeXtrusion) in Fiji thanks to Appose

DeXtrusion is a python module to detect cellular events like cell extrusions in epithelia movies.

## Installation

## Usage

### Events detection
First open the movie that you want to analyze.
Go to `Plugins>Dextrusion_Appose>Detect events`

The program will first ask you to choose the parameters to run `DeXtrusion` on your movie.
The parameters are:

* `cell_diameter`: typical cell diameter. This parameter is used to resize the movie if necessary, so that a cell is the same cell as the cells used in the training data (around 25 pixels). 
* `extrusion_duration`: typical duration of a cell extrusion in the biological tissu, in frames. This is more or less the number of frames in which an extrusion event is visible. This parameter is used to resize temporally the movie is necessary, so that it fits the training data temporal resolution.
