import os
from dextrusion.DeXtrusion import DeXtrusion
import numpy as np

report = print
def listen(callback):
    global report
    report = callback

def get_model_list( model ):
    """ From the location of the model, get the list of models """
    from glob import glob
    # if folder "modeldir" is not one model directory but contains several models subdirectory, use them all
    if not os.path.exists( os.path.join(model,"config.cfg") ):
        model_list = glob(model+"/*/", recursive = False)
    else:
        model_list = [model]
    return model_list

def to_5d(arr):
    """Convert 2D or 3D array to 5D"""
    while arr.ndim < 5:
        arr = np.expand_dims(arr, axis=0)
    return arr

def share_as_ndarray(img):
    """Copies a NumPy array into a same-sized newly allocated block of shared memory"""
    from appose import NDArray
    shared = NDArray(str(img.dtype), img.shape)
    shared.ndarray()[:] = img
    return shared

appose_mode = 'task' in globals()
if appose_mode:
    listen(task.update)
else:
    from appose.python_worker import Task
    task = Task()

task.update(
    current = 1,
    maximum= 4,
    message=f"Init DeXtrusion process"
)

# default parameters
talkative = True  ## print info messages
dexter = DeXtrusion(verbose=talkative)


if appose_mode:
    input_movie = movie.ndarray()
else:
    ## Default parameters to test
    cell_diameter = 25
    model = "/home/gaelle/Proj/RL/dextrusion/DeXNets/notum_all/"
    movie_path = "/home/gaelle/Proj/HackatonAppose/004-crop-1.tif"
    get_probabilities = True

    from tifffile import imread
    input_movie = imread( movie_path )
    extrusion_duration = 4.5
    group_size = 150000
    extrusion_threshold = 180
    extrusion_volume = 800
    get_extrusions = True
    get_divisions = False
	
shift_xy = 10
shift_t = 2
## Merge "peaks" if distance < disxy (spatial) & dist (time)
disxy = 10
distime = 4

model_list = get_model_list( model )
dexter.set_output_names( movie_path )

task.update(
    current = 2,
    maximum= 4,
    message=f"DeXtrusion event detection (takes time)"
)

dexter.detect_events(input_movie, model_list, cell_diameter, extrusion_duration, shift_xy, shift_t, group_size=group_size)

task.update(
    current = 3,
    maximum= 4,
    message=f"Getting event positions from probability map"
)

## get extrusion events
if get_extrusions:
	ext_index = dexter.get_event_index( "cell_death" )
	rois = dexter.get_event_rois(icat=ext_index, volume_threshold=extrusion_volume, proba_threshold=extrusion_threshold, thres=125, disxy=disxy, dist=distime, astype="dict", catname="cell_death")
else:
	rois = []
## get division events
if get_divisions:
	div_index = dexter.get_event_index( "cell_division" )
	rois_div = dexter.get_event_rois(icat=div_index, volume_threshold=division_volume, proba_threshold=division_threshold, thres=125, disxy=disxy, dist=distime, astype="dict", catname="cell_division")
	for roi in rois_div:
		rois.append(roi)

if appose_mode:
    task.outputs["rois"] = rois

if get_probabilities:
    event_list = dexter.get_events_names()
    task.update( f"Getting probability maps for events: "+str(event_list) )
    probamaps = []
    for event in event_list:
        probamap = dexter.get_probability_map( event )
        probamap = dexter.to_init_shape(probamap)
        probamaps.append( probamap )
    probamaps = np.array( probamaps )

    if appose_mode:
        # transform mask CTYX -> TZCYX
        proba_5d = np.moveaxis(to_5d(probamaps), 2, 0)
        task.outputs["probamaps"] = share_as_ndarray(proba_5d)

task.update(
    current = 4,
    maximum= 4,
    message=f"Finished DeXtrusion task"
)

