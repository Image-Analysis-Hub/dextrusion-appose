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


	
	/**
	 * Defines all the interface
	 */
	private void createAndShowGUI() 
	{
	        frame = new JFrame( "DeXtrusion" );
	        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	        frame.setLayout( new GridLayout(6, 3, 10, 10) );

	     
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
	        display_choice.setBackground( new Color(205, 229, 252) );
	        

	        JButton save_rois_btn = new JButton( "Save displayed ROIs" );
	        //save_rois_btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
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
	        save_rois_btn.setBackground( new Color(175, 205, 255) );
	        
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
	        JButton check_rois_btn = new JButton( "☑ Check displayed ROIs" );
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
	        
	        // Zoom on a Roi
	        SpinnerNumberModel current_roi = new SpinnerNumberModel(
	        	    0,      // initial value
	        	    0,       // minimum
	        	    20000,     // maximum
	        	    1        // step
	        	);
	        JSpinner current_roi_spin = new JSpinner( current_roi );
	        JPanel current_roi_panel = new JPanel(new FlowLayout(FlowLayout.LEFT) );
	        JButton current_roi_zoom = new JButton("Zoom on event n°");
	        current_roi_zoom.setToolTipText( "Choose number of event to zoom on (in the order in the ROIManager)" );
	        current_roi_panel.add( current_roi_zoom);
	        current_roi_panel.add( current_roi_spin );
	        current_roi_zoom.addActionListener( new ActionListener() 
	        {
	            @Override
	            public void actionPerformed( ActionEvent e ) 
	            {
	                cell_events.zoomOnRoi( (int) current_roi_spin.getValue() );
	            }
	        });

	        // Previous/Next buttons
	        JButton next_roi = new JButton( "Next event ↪" );
	        next_roi.addActionListener( new ActionListener() 
	        {
	            @Override
	            public void actionPerformed( ActionEvent e ) 
	            {
	            	int nb = (int) current_roi_spin.getValue();
	            	nb ++;
	            	if ( nb >= cell_events.nbEvents() )
	            	{
	            		nb = 0;
	            	}
	            	current_roi_spin.setValue( nb );
	                cell_events.zoomOnRoi( nb );
	            }
	        });
	        // Previous/Next buttons
	        JButton prev_roi = new JButton( "↩ Previous event" );
	        prev_roi.addActionListener( new ActionListener() 
	        {
	            @Override
	            public void actionPerformed( ActionEvent e ) 
	            {
	            	int nb = (int) current_roi_spin.getValue();
	            	nb --;
	            	if ( nb < 0 )
	            	{
	            		nb = cell_events.nbEvents() -1 ;
	            	}
	            	current_roi_spin.setValue( nb );
	                cell_events.zoomOnRoi( nb );
	            }
	        });
	        // Color the zoom button line
	        Color zoom_color = new Color( 211, 227, 255 );
	        next_roi.setBackground( zoom_color );
	        prev_roi.setBackground( zoom_color );
	        current_roi_zoom.setBackground( zoom_color );	        
	        
	        // Add buttons to frame
	        frame.add( save_proba,0 );  
	        frame.add( Box.createGlue(), 1);
	        frame.add( Box.createGlue(), 2);
	        
	        frame.add( new JLabel("Display events:") );
	        frame.add( Box.createGlue());
	        frame.add( new JLabel("") );

	        frame.add( display_choice );
	        frame.add( save_rois_btn );	   
	        frame.add( Box.createGlue() );

	        frame.add( prev_roi );
	        frame.add( current_roi_panel );
	        frame.add( next_roi );
	        
	        frame.add( new JLabel("Verify ROIs:") );
	        frame.add( Box.createGlue());
	        frame.add( new JLabel("") );
	        
	        frame.add( wsize_panel );
	        frame.add( nframe_panel );
	        frame.add( check_rois_btn );	
	        
	        // Display
	        frame.pack();
	        ImageJ ij = IJ.getInstance();
	        frame.setLocationRelativeTo(ij); // Center on screen
	        frame.setVisible(true);
	   
	}
}
