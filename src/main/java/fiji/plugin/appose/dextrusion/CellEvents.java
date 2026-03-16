/*-
 * #%L
 * Running DeXtrusion with a Fiji plugin based on Appose.
 * %%
 * Copyright (C) 2026 DeXtrusion team
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.appose.dextrusion;

import ij.IJ;
import ij.ImagePlus;

import java.awt.Color;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.plugin.frame.RoiManager;


/**
 * Contains the cellular events that got detected by DeXtrusion
 * Allows to edit them, save them, display
 */
public class CellEvents 
{
	private Map< String, ArrayList<PointRoi> > event_rois = null;
	private ImagePlus movie = null; // raw movie
	private ImagePlus proba_maps = null; // Probability maps returned by DeXtrusion if selected in parameters
	private Color[] colors = {new Color(200,50,0), new Color(0,50,200) };
	
	private String folder_name = ""; // where results are saved
	private String image_name = ""; // name of the movie, without the extension
	
	private Set<String> event_names = null; // list of possible event names from the model
	
	private RoiManager rm; // handles ROI interaction with the user
	
	public CellEvents()
	{
		event_rois = new HashMap<String, ArrayList<PointRoi>>();
		rm = RoiManager.getInstance();
		if (rm == null )
		{
			rm = new RoiManager();
		}
		rm.reset();
		rm.setVisible( true );
		rm.runCommand("Show All");
	}
	
	/**
	 * Access to the list of event names
	 * @return event_names, all possible event names
	 */
	public Set<String> get_event_names()
	{
		return event_names;
	}
	
	public void setMovie( ImagePlus imp )
	{
		movie = imp;
	}
	
	/**
	 * Gives access to the raw movie
	 * @return
	 */
	public ImagePlus getMovie()
	{
		return movie;
	}
	
	/**
	 * Set internally the probability maps
	 * @param pmaps
	 */
	public void setProbabilityMaps( ImagePlus pmaps )
	{
		proba_maps = pmaps;
	}
	
	/**
	 * Returns if the object has associated probability maps or not
	 */
	public boolean hasProbabilityMap()
	{
		return proba_maps != null;
	}
	
	/**
	 * Get the necessary parameters from the inputs shared objects
	 * @param inputs
	 */
	public void setParameters( final Map< String, Object > inputs )
	{
		String full_path = (String) inputs.get( "movie_path" );
		// Get folder path
		File file = new File( full_path );
		folder_name = AppUtils.getResultsDirectory( file );
		// Get filename without extension
		String nameWithExt = file.getName();
		image_name = nameWithExt.substring(0, nameWithExt.lastIndexOf('.'));
	}
	
	/**
	 * Load all deXtrusion objects from given image filename
	 * @param imagefile Input file containing the raw movie
	 */
	public void loadFromFile( File imagefile )
	{
		folder_name = AppUtils.getResultsDirectory( imagefile );
		// Get filename without extension
		String nameWithExt = imagefile.getName();
		image_name = nameWithExt.substring(0, nameWithExt.lastIndexOf('.'));
		movie = IJ.openImage( imagefile.getAbsolutePath() );
		movie.show();
		loadEvents();
	}
	
	public void saveProbabilityMaps()
	{
		if (proba_maps != null )
		{
			FileSaver fs = new FileSaver( proba_maps );
			fs.saveAsTiff( folder_name+File.separator+image_name+"_probabilities.tif" );
			IJ.log("Probability maps saved in "+folder_name+File.separator+image_name+"_probabilities.tif" );
	
		}
	}
	
	/**
	 * Display one type of event in the ROIManager or both
	 * @param event_type
	 */
	public void showEvents( String event_type )
	{
		rm.reset();
		for ( String event_name: event_names )
		{
			if ( ( event_type.equals( "Show "+event_name )) || ( event_type.equals("Show all events") ) )
			{
				addEventsToManager( event_name );
			}
		}	
	}
	
	/**
	 * Save ROI of selected events or all events to .zip file
	 * @param event_type
	 */
	public void saveROIs( String event_type )
	{
		for ( String event_name: event_names )
		{
			
			if ( ( event_type.equals( "Show "+event_name )) || ( event_type.equals("Show all events" )) )
			{
				saveEventsInManager( event_name );
			}
		}	
	}
	
	/**
	 * Save ROIs of selected event to zip file
	 * @param event_name
	 */
	public void saveEventsInManager( String event_name )
	{
		rm.deselect();
		int[] indices = IntStream.range(0, rm.getCount())
		        .filter(i -> rm.getName(i).equals( event_name) )
		        .toArray();
		    
		rm.setSelectedIndexes(indices);
		rm.save( folder_name+File.separator+image_name+"_"+event_name+".zip" );
        IJ.log("Events "+event_name+" saved in "+folder_name+File.separator+image_name+"_"+event_name+".zip" );
		rm.deselect();
	}
	
	/**
	 * Add all events of event name to ROI Manager
	 * @param event_name
	 */
	public void addEventsToManager( String event_name )
	{
		ArrayList<PointRoi> rois = event_rois.get( event_name );
		for ( PointRoi roi : rois )
		{
			rm.addRoi( roi );
		}
	}
	
	public void removeRoi( int iroi )
	{
		Roi roi = rm.getRoi( iroi );
		String roi_name = roi.getName();
		ArrayList<PointRoi> rois = event_rois.get( roi_name );
		if ( rois.size() > 0 )
			rois.remove(iroi);
		rois = event_rois.get( roi_name );
		rm.select( iroi );
	    rm.runCommand(movie, "Delete");				
	}

	/**
	 * Load all saved ROIs (.zip files) in the default folder for the current movie
	 */
	public void loadEvents()
	{	
		rm.reset();
		File folder = new File( folder_name );
		File[] matchingFiles = folder.listFiles(new FilenameFilter() 
		{
			@Override
		    public boolean accept(File dir, String name) 
		    {
		        return name.startsWith(image_name+"_") && name.endsWith(".zip");
		    }
		});

		// Go through the corresponding files
		if (matchingFiles != null) 
		{
			int nrois = rm.getCount();
		    for (File file : matchingFiles) 
		    {
		    	try
		    	{
		    		IJ.log("Loading ROI from file "+file.getName());
		    		rm.open( file.getAbsolutePath() );
		    		// New rois were loaded
		    		if ( rm.getCount() > nrois )
		    		{
		    			int tot_rois = rm.getCount();
		    			for ( int iroi = nrois; iroi < tot_rois; iroi++ )
		    			{
		    				PointRoi proi = (PointRoi) rm.getRoi( iroi );
		    				if ( movie != null )
		            		{	
		            			proi.setImage( movie );
		            			//imp.setRoi( proi );
		            		}
		    				String roi_name = proi.getName();
		            		if ( event_rois.get( roi_name ) == null )
		            		{
		            			ArrayList<PointRoi> pts = new ArrayList<PointRoi>();
		            			pts.add( proi );
		            			event_rois.put( roi_name, pts );
		            		}
		            		else
		            		{
		            			event_rois.get( roi_name ).add( proi );
		            		}
		    			}
		    			nrois = tot_rois;
		    		}
		    	}
		    	catch (Exception e)
		    	{
		    		IJ.log( "Warning: Could not load ROIs from "+file.toString()+" : "+e.toString() );
		    	}
		    }
		}
		// Get the list of possible events
		event_names = event_rois.keySet();
		rm.runCommand("Show All");
	}
	
	/**
	 * Read dextrusion python results in shared memory and create ROI for each detection
	 * @param map_rois
	 * @param add_to_manager
	 */
	public void readRois( List<Map<String, Object>> map_rois, boolean add_to_manager )
	{
		
		for ( Map<String, Object> roi : map_rois ) 
		{
			try 
			{
        		List<Integer> pos = (List<Integer>) roi.get(  "position_yx" );
        		PointRoi proi = new PointRoi( pos.get( 1 ), pos.get( 0 ) );
        		proi.setSize( 3 );
        		String roi_name = (String) roi.get( "name" );
        		
        		int roi_type = roi_name.equals("cell_death") ? 0 : 1;
        		proi.setStrokeColor( colors[roi_type] );
        		proi.setName( ""+roi_name );
        		if ( movie != null )
        		{	
        			proi.setImage( movie );
        			//imp.setRoi( proi );
        		}
        		int roi_frame = (int) roi.get( "position_frame" );
        		proi.setPosition( 1, 1, roi_frame );
        		if ( add_to_manager )
        			rm.addRoi( proi ); // Add to RoiManager
        		if ( event_rois.get( roi_name ) == null )
        		{
        			ArrayList<PointRoi> pts = new ArrayList<PointRoi>();
        			pts.add( proi );
        			event_rois.put( roi_name, pts );
        		}
        		else
        		{
        			event_rois.get( roi_name ).add( proi );
        		}
        		
			} 
			catch (Exception e) 
			{
				System.err.println("Error creating ROI: " + e.getMessage());
				e.printStackTrace();
			}
		}
		// Get the list of possible events
		event_names = event_rois.keySet();
	}
	
}
