###
# #%L
# Running DeXtrusion with a Fiji plugin based on Appose.
# %%
# Copyright (C) 2026 DeXtrusion team
# %%
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public
# License along with this program.  If not, see
# <http://www.gnu.org/licenses/gpl-3.0.html>.
# #L%
###
import os


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
    #import numpy as np
    while arr.ndim < 5:
        arr = np.expand_dims(arr, axis=0)
    return arr

def share_as_ndarray(img):
    """Copies a NumPy array into a same-sized newly allocated block of shared memory"""
    from appose import NDArray
    shared = NDArray(str(img.dtype), img.shape)
    shared.ndarray()[:] = img
    return shared

if True:
	report = print

	appose_mode = 'task' in globals()
	if appose_mode:
		listen(task.update)
	else:
   		from appose.python_worker import Task
   		task = Task()

	task.update( current = 1, maximum= 4,
    message=f"Init DeXtrusion process"
	)
	import numpy as np
	from dextrusion.DeXtrusion import DeXtrusion


	# default parameters
	talkative = True  ## print info messages
	dexter = DeXtrusion(verbose=talkative)


	if appose_mode:
		if input_image:
			## process only current image, passed in the shared memory
			input_movie = movie.ndarray()
			movies = [movie_path]
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
		event_roi_names = ["cell_death", "cell_division"]
		## Merge "peaks" if distance < disxy (spatial) & dist (time)
		dist_xy = 10
		dist_time = 4	
		shift_xy = 10
		shift_time = 2
		input_image = False


	model_list = get_model_list( model )
	save_outpus = True ## save the outputs rather than sending them back (for batch processing)
	
	nsteps = 2*len(movies)
	cur = 1
	for imgfile in movies:
		cur = cur + 1
		if input_image:
			## processing only one movie
			save_outputs = False
			dexter.set_output_names( movie_path )
			task.update(
    		current = cur,
    		maximum= nsteps,
    		message=f"DeXtrusion event detection (takes time)"
			)
			dexter.detect_events(input_movie, model_list, cell_diameter, extrusion_duration, shift_xy, shift_time, group_size=group_size)
		else:
			## open and process a movie
			if os.path.exists( imgfile ):
				task.update( current=cur, maximum=nsteps, message=f"Detecting events on movie "+str(imgfile)+" (takes time)" )
				## Detect events
				dexter.detect_events_onmovie( imgfile, models=model_list, 
                                     cell_diameter=cell_diameter, extrusion_duration=extrusion_duration, 
                                     dxy=shift_xy, dz=shift_time, 
                                     group_size=group_size )
	
		cur = cur + 1
		task.update(
   			current = cur,
    		maximum= nsteps,
    		message=f"Getting event positions from probability map"
		)

		## get each listed event to ROI
		rois = []
		for event in event_roi_names:
			get_event : boolean = globals().get('get_'+event, False)
			if get_event:
				threshold : int = globals().get( event+'_threshold', 0 )
				volume : int = globals().get( event+'_volume', 0 )
				evt_index = dexter.get_event_index( ""+event )
					
				if input_image:
					rois_evt = dexter.get_event_rois(icat=evt_index, volume_threshold=volume, proba_threshold=threshold, 
					thres=125, disxy=dist_xy, dist=dist_time, astype="dict", catname=event)
					for roi in rois_evt:
						rois.append(roi)
				else:
					dexter.get_rois( cat=evt_index, volume_threshold=volume, proba_threshold=threshold, disxy=dist_xy, dist=dist_time, catname=event )

		if input_image:
			task.outputs["rois"] = rois

		## Get probability maps (return them or save them)
		if get_probabilities:
			event_list = dexter.get_events_names()
			task.update( f"Getting probability maps for events: "+str(event_list) )
			if input_image:	
				probamaps = []
				for event in event_list:
					probamap = dexter.get_probability_map( event )
					probamap = dexter.to_init_shape(probamap)
					probamaps.append( probamap )
				probamaps = np.array( probamaps )
				# transform mask CTYX -> TZCYX
				proba_5d = np.moveaxis(to_5d(probamaps), 2, 0)
				task.outputs["probamaps"] = share_as_ndarray(proba_5d)
			else:
				dexter.write_probamaps(cat=None, astime=True)

	task.update(
    	current = nsteps,
    	maximum= nsteps,
    	message=f"Finished DeXtrusion task"
	)

