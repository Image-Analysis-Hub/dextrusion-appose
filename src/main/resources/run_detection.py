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

appose_mode = 'task' in globals()
if appose_mode:
    listen(task.update)
else:
    from appose.python_worker import Task
    task = Task()

task.update(f"Starting python process")

# default parameters
talkative = True  ## print info messages
dexter = DeXtrusion(verbose=talkative)


if appose_mode:
    input_movie = movie.ndarray()
else:
    ## Default parameters to test
    cell_diameter = 25
    model = "/home/gaelle/Proj/RL/dextrusion/DeXNets/notum_all/"
    movie_path = "/home/gaelle/Proj/RL/dextrusion/data/004-crop.tif"
    from tifffile import imread
    input_movie = imread( movie_path )

extrusion_duration = 4.5
shift_xy = 10
shift_t = 2
groupsize = 150000

model_list = get_model_list( model )

## Tmp solution to test -> TO change in DeXtrusion
imdir = os.path.dirname(movie_path)
output_name = os.path.basename(movie_path)
output_name = os.path.splitext(output_name)[0]
dexter.outpath = os.path.join(imdir, "results")
if not os.path.exists(dexter.outpath):
    os.makedirs(dexter.outpath)
dexter.outname = os.path.join(dexter.outpath, output_name)
dexter.detect_events(input_movie, model_list, cell_diameter, extrusion_duration, shift_xy, shift_t, group_size=groupsize)


extrusion_probamap = dexter.probamap[0]
extrusion_probamap = dexter.to_init_shape(extrusion_probamap)

if appose_mode:
    # transform mask TYX -> TZCYX
    proba_5d = np.rollaxis(to_5d(extrusion_probamap), -3, -4)
    task.outputs["probamap"] = share_as_ndarray(proba_5d)

task.update(f"Finished DeXtrusion script")
