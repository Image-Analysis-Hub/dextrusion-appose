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

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.ContrastEnhancer;
import ij.plugin.MontageMaker;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;

public class CheckRois
{
	private int winsize = 25; // size of the check roi window
	private int nframes = 6; // Number of frames in the montage to check
	
	private CellEvents cell_events = null; // handle the ROIs
	private ImagePlus imp=null; // processed image
	private ImagePlus montage = null; // current montage Image
	private int width=0;
	private int height=0;
	private int maxframes=0;
	private RoiManager rm = null;
	private TextWindow tw = null; // show messages
	
	private int iroi = 0; // current number of roi
	private int nrois = 0; // total number of ROIs
	private int[] done_rois = new int[]{0, 0};  // keep track of how many rois were good [0] and wrong [1]
	
	private KeyListener kl =  new KeyListener()
	{
		@Override
		public void keyPressed( KeyEvent e )
		{
			
			if ( e.getKeyChar() == 'y' ) 
			{
				done_rois[0] ++;
				iroi ++;

				checkRoi();
			}
			if ( e.getKeyChar() == 'n' )
			{
				done_rois[1] ++;
				cell_events.removeRoi( iroi );
				checkRoi();
			}
			// unrecognized key
		}

		@Override
		public void keyTyped(KeyEvent e) {
		}

		@Override
		public void keyReleased(KeyEvent e) {		
		}
	};
	
	

	/**
	 * Set the size parameters
	 */
	public void setParameters( int window_size, int number_frames )
	{
		winsize = window_size;
		nframes = number_frames;
	}
	
	/**
	 * Makes a crop around the given ROI and returns it
	 * @param roi: roi to crop around and check
	 * @return
	 */
	public ImagePlus cropRoi( Roi roi )
	{
	
		 int slice = roi.getZPosition();
		 int frame = roi.getTPosition();
		 Rectangle roi_bounds = roi.getBounds();	            
		  if (slice > frame) 
		  {
		        frame = slice;
		  }      
		           
		  // Create rectangle around ROI
		  int x = roi_bounds.x - winsize;
		  int y = roi_bounds.y - winsize;
		  if ( x < 0 ) x = 0;
		  if ( y<0 ) y = 0;
		  if ( x+2*winsize > width ) x = width- 2*winsize; 
		  if ( y+2*winsize > height ) y = height- 2*winsize; 
		  imp.setRoi(new Roi(x, y, 2 * winsize, 2 * winsize));
		            
		  int start_frame = Math.max(1, frame - nframes);
		  int end_frame = Math.min(frame + nframes + 1, maxframes );
		  
		  // Duplicate the region
		  return imp.crop("" + start_frame + "-" + end_frame);
	}
	
	public void getUserCheckOnMontage( ImagePlus dup )
	{
		 MontageMaker mm = new MontageMaker();
		 montage = mm.makeMontage2(
		      dup,                    // source image
		      nframes * 2,           // columns
		      1,                     // rows
		      4.0,                   // scale
		      1,                     // first slice
		      dup.getNFrames(),      // last slice
		      1,                     // increment
		      2,                     // border width
		      false                  // labels
		  );
		 montage.setTitle("Correct ROI ? y/n");
		 montage.show();
		  
		  // Add listener to get the user input
		  //w.addKeyListener( kl );  
		KeyListener[] kls = montage.getCanvas().getKeyListeners();
		for ( KeyListener ckl: kls ) montage.getCanvas().removeKeyListener(ckl);
		montage.getCanvas().addKeyListener( kl );
		montage.getCanvas().requestFocus();
		 // }
	}
	
	public void checkRoi()
	{
		if (montage != null) montage.close();
		IJ.showStatus( "!Checking ROIs validity ("+(done_rois[0]+done_rois[1])+"/"+nrois+")..." );
		IJ.showProgress( (done_rois[0]+done_rois[1]), nrois);
		// Reached the end of ROIs, finished
		if ( iroi >= rm.getCount() ) 
		{

			  IJ.log("Nb of correct ROIs: " + done_rois[0]);
			  IJ.log("Nb of deleted ROIs: " + done_rois[1]);
			  if ((done_rois[0] + done_rois[1]) > 0) 
			  {
			     double successRate = (double) done_rois[0] / (done_rois[0] + done_rois[1]);
			      IJ.log("Success rate (TP/TP+FP): " + IJ.d2s(successRate, 3));
			  }
			  tw.close();
			  return;
		}

		// Check current ROI
		Roi roi = rm.getRoi( iroi );
		imp.setSlice( roi.getTPosition() );
		ImagePlus dup = cropRoi( roi );
		ContrastEnhancer ce = new ContrastEnhancer(); // enhance contrast to each pop-up window
		ce.stretchHistogram(dup, 0.35);
		            
		// Make montage
		getUserCheckOnMontage( dup );
		
		// Close temporary images
		dup.close(); 
	}


	/** 
	 * Go through all ROIs one by one and let the user validate or remove it
	 */
	public void processRoi( CellEvents cenv )
	{
		cell_events = cenv;
		// Check validity of ROIs and of the image
		rm = RoiManager.getInstance();
		if (rm == null )
		{
			IJ.error( "No ROI Manager found, cannot check anything");
			return;
		}
		if ( rm.getCount() <= 0 )
		{
			IJ.error( "No ROIs in ROIManager, nothing to check");
			return;
		}
		
		imp = cell_events.getMovie();
		if ( imp == null )
		{
			IJ.error( "Current movie is null ");
			return;
		}
		iroi = 0;
		nrois = rm.getCount();
		IJ.log("Initial nb of ROIs: " + nrois);
		IJ.showStatus( "!Checking ROIs validity ("+(done_rois[0]+done_rois[1])+"/"+nrois+")..." );
		IJ.showProgress( 0, nrois );
		
        width = imp.getWidth();
        height = imp.getHeight();
        int nSlices = imp.getNSlices();
        int nFrames = imp.getNFrames();
        maxframes = Math.max(nSlices, nFrames);
		
		tw = new TextWindow("Instructions", 
			   "For each ROI,\n"+
			   "Press 'y' if this is a correct event\n" +
			   "Press 'n' if this is a false positive\n\n", 
			   400, 300);
		tw.setVisible( true );
		
		// Process all Rois
		checkRoi();
		
	}		
}
