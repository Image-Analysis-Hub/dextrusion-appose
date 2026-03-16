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

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import ij.IJ;
import ij.ImageJ;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;

@Plugin(type = Command.class)
public class DetectionGUI implements Command
{

	private JFrame frame;
	private Detection detector;
	private CellEvents cell_events;
	
	/** Set the main detector object that interacts with the dextrusion python */
	public void setDetector( Detection detec )
	{
		detector = detec;
	}
	
	/** Set the cell events object that contains all the events data */
	public void setCellEvents( CellEvents cenv )
	{
		cell_events = cenv;
	}

	@Override
	public void run() 
	{
	        SwingUtilities.invokeLater(() -> createAndShowGUI());
	}

	private void createAndShowGUI() 
	{
	        frame = new JFrame( "DeXtrusion" );
	        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	        frame.setLayout( new GridLayout(3, 3, 10, 10) );

	     
	        // Create buttons: save probability maps
	        JButton save_proba = new JButton( "Save probability maps" );
	        save_proba.addActionListener( new ActionListener() 
	        {
	            @Override
	            public void actionPerformed( ActionEvent e ) 
	            {

	                cell_events.saveProbabilityMaps();
	            }
	        });
	        save_proba.setEnabled( cell_events.hasProbabilityMap() );
	        save_proba.setToolTipText( "Save the image of probability maps if available" );


	        // Choose with events to display
	        JComboBox<String> display_choice = new JComboBox<String>();
	        display_choice.addItem("Show all events");
	        for ( String event_name : cell_events.get_event_names() ) 
	        {
	            display_choice.addItem("Show " + event_name);
	        }

	        display_choice.addItemListener(new ItemListener() 
	        {
	            @Override
	            public void itemStateChanged(ItemEvent e) 
	            {
	                if (e.getStateChange() == ItemEvent.SELECTED) 
	                {
	                    String selected = (String) e.getItem();
	                   //System.out.println("Changed to: " + selected);
	                    cell_events.showEvents( selected );
	                }
	            }
	        });
	        display_choice.setToolTipText( "Choose which events (ROIs) to display" );

	        JButton save_rois_btn = new JButton( "Save displayed ROIs" );
	        save_rois_btn.addActionListener(new ActionListener() 
	        {
	            @Override
	            public void actionPerformed(ActionEvent e) 
	            {
                    String selected = (String) display_choice.getSelectedItem();
	                cell_events.saveROIs( selected );
	            }
	        });
	        save_rois_btn.setToolTipText( "Save currently displayed events (ROIs). All if all is selected" );
	        
	        // Parameters for Check Rois: window size and number of frames
	        SpinnerNumberModel window_size_model = new SpinnerNumberModel(
	        	    25,      // initial value
	        	    1,       // minimum
	        	    100,     // maximum
	        	    1        // step
	        	);
	        JSpinner wsize_spinner = new JSpinner(window_size_model);
	        JPanel wsize_panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	        JLabel wsize_label = new JLabel("Window size");
	        wsize_label.setToolTipText( "Size of window around each event to check to display in the montage" );
	        
	        SpinnerNumberModel nframes_model = new SpinnerNumberModel(
	        	    6,      // initial value
	        	    1,       // minimum
	        	    20,     // maximum
	        	    1        // step
	        	);
	        JSpinner nframe_spinner = new JSpinner( nframes_model );
	        JPanel nframe_panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	        JLabel nframe_label = new JLabel("Number of frames");
	        nframe_label.setToolTipText( "Number of frames before and after the event to display in the montage" );
	        
	        wsize_panel.add( wsize_label);
	        wsize_panel.add( wsize_spinner);
	        nframe_panel.add( nframe_label);
	        nframe_panel.add( nframe_spinner);
	        
	        // Check ROIs
	        JButton check_rois_btn = new JButton( "Check displayed ROIs" );
	        check_rois_btn.addActionListener(new ActionListener() 
	        {
	            @Override
	            public void actionPerformed(ActionEvent e) 
	            {
	            	if ( display_choice.getSelectedItem().equals("Show all events") )
	            	{
	            		IJ.error( "Cannot verify all event types at once, select one before" );
	            		return;
	            	}
                    CheckRois cr = new CheckRois();
                	int wsize = (Integer) wsize_spinner.getValue();
                	int nframes = (Integer) nframe_spinner.getValue();
                    cr.setParameters( wsize, nframes);
                    cr.processRoi( cell_events );
	            }
	        });
	        check_rois_btn.setToolTipText( "Go through all displayed ROI of a given type and validate it or not by pressing y or n" );
	        
	        // Add buttons to frame
	        frame.add( save_proba,0 );  
	        frame.add( Box.createGlue(), 1);
	        frame.add( Box.createGlue(), 2);
	        
	        frame.add( display_choice, 3 );
	        frame.add( save_rois_btn, 4 );	   
	        frame.add( Box.createGlue(), 5);
		       
	        frame.add( wsize_panel, 6);
	        frame.add( nframe_panel, 7);
	        frame.add( check_rois_btn, 8 );	  
	        
	        // Display
	        frame.pack();
	        //ImageJ ij = IJ.getInstance();
	        //frame.setLocationRelativeTo(ij); // Center on screen
	        frame.setVisible(true);
	   
	}
}
