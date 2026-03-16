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

import static fiji.plugin.appose.dextrusion.AppUtils.rawWraps;
import static fiji.plugin.appose.dextrusion.AppUtils.transferCalibration;
import static fiji.plugin.appose.dextrusion.AppUtils.setChannelsLUT;
import static fiji.plugin.appose.dextrusion.AppUtils.getScript;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.TaskService;


import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.PointRoi;
import ij.io.FileInfo;
import ij.plugin.frame.RoiManager;
import net.imagej.ImgPlus;
import net.imglib2.appose.NDArrays;
import net.imglib2.appose.ShmImg;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/*
 * This class implements running DeXtrusion (python) from a Fiji plugin with Appose 
 * 
 */

@Plugin(type = Command.class, menuPath = "Plugins>DeXtrusion>Detect events")
public class Detection implements Command
{
	@Parameter
	private TaskService taskService;
	
	/**@Parameter(  
			label = "Choose model directory",
		    description = "Directory that contains the model(s) to run",
		    style = "directory"    
	)
	private File modelDirectory;*/
	private String model_dir;
	
	@Parameter( label="-------", description="Information", visibility=ItemVisibility.MESSAGE )
	private String movie_resolution = "--------- Movie resolution";
	
	
	@Parameter( label="cell_diameter", description="Typical cell diameter"  )
	private double cell_diameter = 25;
	
	@Parameter( label="extrusion_duration", description="Typical duration of an extrusion event (in frames)"  )
	private double extrusion_duration = 4.5;
	
	@Parameter( label="Show probability maps", description="Show the probability (of events) maps" )
	private boolean get_probabilities = true;
	
	@Parameter( label="-------", description="Information", visibility=ItemVisibility.MESSAGE )
	private String roi_parameters = "--------- ROI parameters";
	
	//@Parameter( label="Get ROIs", description="Given event point-like positions as ROIs", callback="show_roi_parameters"  )
	//private boolean get_rois = true;
	
	@Parameter( label="Extrusion ROIs", description="Add cell death positions as point ROI" )
	private boolean get_extrusion_roi = true;
	
	@Parameter( label="Extrusion detection threshold", description="Probability threshold to detect an extrusion event", style="column:0" )
	private int extrusion_threshold = 180;
	
	@Parameter( label="Extrusion detection volume", description="Probability volutme to detect an extrusion event (in pixels)", style="column:1" )
	private int extrusion_volume = 800;
	
	@Parameter( label="Division ROIs", description="Add cell division positions as point ROI" )
	private boolean get_division_roi = true;
	
	@Parameter( label="Division detection threshold", description="Probability threshold to detect a division event", style="column:0" )
	private int division_threshold = 180;
	
	@Parameter( label="Division detection volume", description="Probability volutme to detect a division event (in pixels)", style="column:1" )
	private int division_volume = 500;

	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_advanced = "-------- Advanced parameter";
	
	@Parameter( label="Show debug messages", description="Show full debug messages in the Console" )
	private boolean show_debug = false;
	
	@Parameter( label="Computation size", description="Depending on computer capabilities, can compute more at one time" )
	private int group_size = 150000;
	
	private List<String> color_list = Arrays.asList("magenta", "cyan", "orange", "green", "red", "yellow", "blue");

	// Fiji task
	private org.scijava.task.Task fijiTask;
	// dextrusion progress
	private int progress_total = 0;
	private int shift = 0;
	
	/*
	 * This is the entry point for the plugin. This is what is called when the
	 * user select the plugin menu entry: 'Plugins > Examples >
	 * ApposeFijiPluginExample' in our case. You can redefine this by editing
	 * the file 'plugins.config' in the resources directory
	 * (src/main/resources).
	 */
	@Override
	public void run()
	{
		// start task
		fijiTask = taskService.createTask("dextrusion-appose");
		fijiTask.setStatusMessage( "Launching DeXtrusion appose task." );
		fijiTask.start();
				
		// Grab the current image.
		final ImagePlus imp = WindowManager.getCurrentImage();
		
		if ( imp == null )
		{
			IJ.error( "No opened movie found. Open one before" );
			return;
		}
			
		
		// Download and install if necessary the notum model
		String model_name = "notum_all";
		String model_url = "https://github.com/Image-Analysis-Hub/DeXtrusion/raw/refs/heads/main/DeXNets/"+model_name+".zip";
		String model_local_dir = AppUtils.createLocalDirectory( "dextrusion" );
		try 
		{
			model_dir = AppUtils.downloadAndExtract( model_local_dir, model_name, model_url );
		}
		catch ( final IOException e )
		{
			IJ.error( "Failed to find/download the models: "+e );	
		}
		
		try
		{
			// Runs the processing code.
			process( imp );
		}
		catch ( final IOException | BuildException e ) {
			IJ.error("An error occurred: " + e.getMessage());
			e.printStackTrace();
		}
	}
	

	/*
	 * Actually do something with the image.
	 */
	public < T extends RealType< T > & NativeType< T > > void process( final ImagePlus imp ) throws IOException, BuildException
	{
		// Print os and arch info
		System.out.println( "Starting process..." );

		/*
		 * The Python script that we want to run. It is loaded from the run_detection.py file in the resource folder. 
		 */
		final String script = getScript( this.getClass().getResource("run_detection.py" ) );

		// Wrap the ImagePlus into a ImgLib2 image.
		final ImgPlus< T > img = rawWraps( imp );
		
		/*
		 * Transfer the movie to shared object
		 */
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "movie", NDArrays.asNDArray( img ) );

		String fullPath = AppUtils.getFullPath( imp );
		inputs.put( "movie_path", fullPath );   
		
		// Put all parameters to pass to run dextrusion
		inputs.put( "cell_diameter", cell_diameter );
		inputs.put( "extrusion_duration", extrusion_duration );
		//inputs.put( "model", modelDirectory.getAbsolutePath() );
		inputs.put( "model", model_dir );
		inputs.put( "get_probabilities", get_probabilities );
		inputs.put( "group_size", group_size );
		inputs.put( "get_extrusions", get_extrusion_roi );
		inputs.put( "extrusion_threshold", extrusion_threshold );
		inputs.put( "extrusion_volume", extrusion_volume );
		inputs.put( "get_divisions", get_division_roi );
		inputs.put( "division_threshold", division_threshold );
		inputs.put( "division_volume", division_volume );
		
		
		/*
		 * Create or retrieve the environment.
		 */
		IJ.log( "Downloading/Installing the environement if necessary..." );
		
		final Environment env = Appose // the builder
				.pixi(this.getClass().getResource("pixi.toml")) // we chose pixi as the environment manager
				.subscribeProgress( this::showProgress ) // report progress visually
				.subscribeOutput( this::showProgress ) // report output visually
				.subscribeError( IJ::log ) // log problems
				.build(); // create the environment
		hideProgress();

		/*
		 * Using this environment, we create a service that will run the Python
		 * script.
		 */
		try ( Service python = env.python().init("import numpy") )
		{

			python.debug( msg -> show_messages( msg ) );
			
			final Task task = python.task( script, inputs );

			// Start the script, and return to Java immediately.
			IJ.log( "Starting DeXtrusion-Appose task..." );
			final long start = System.currentTimeMillis();
			// To catch update message from the python script
			task.listen( e->{
				System.out.println("\tInfo: "+e.message);
			} );
			task.listen( e -> {
				if (e.message != null) 
				{ 
					this.fijiTask.setStatusMessage(e.message);
					IJ.log( e.message );
				}
				if (e.current > 0) {this.fijiTask.setProgressValue(e.current);}
				if (e.maximum > 0) {this.fijiTask.setProgressMaximum(e.maximum);}
			} );
			task.start();

			/*
			 * Wait for the script to finish. This will block the Java thread
			 * until the Python script is done, but it allows the Python code to
			 * run in parallel without blocking the Java thread while it is
			 * running.
			 */
			IJ.showStatus( "Event detection (1/2)" );
			task.waitFor();
			IJ.showStatus( "Detection finished" );
			this.fijiTask.finish();

			// Verify that it worked.
			if ( task.status != TaskStatus.COMPLETE )
				throw new RuntimeException( "Python script failed with error: " + task.error );

			// Benchmark.
			final long end = System.currentTimeMillis();
			System.out.println( "Task finished in " + ( end - start ) / 1000. + " s" );

			// Get the output and process them
			List<Map<String, Object>> map_rois = (List<Map<String, Object>>) task.outputs.get( "rois" );
			CellEvents cell_events = new CellEvents();
			cell_events.setParameters( inputs );
			cell_events.setMovie( imp );
			cell_events.readRois( map_rois, true );
			
			if ( get_probabilities )
			{
				final NDArray maskArr = ( NDArray ) task.outputs.get( "probamaps" );
				final Img< T > output = new ShmImg<>( maskArr );
				final ImagePlus tmp = ImageJFunctions.wrap( output, "probability_maps" );
				CompositeImage probamap = new CompositeImage( tmp );
		        // Set display mode
		        probamap.setDisplayMode( IJ.COMPOSITE );
		        setChannelsLUT( probamap, color_list );
				
				transferCalibration( imp, probamap );
				probamap.show();
				cell_events.setProbabilityMaps( probamap );
				
				/**
				 * Mouse/Keyboard interactions
				 * ImageWindow w = probamap.getWindow();
				 
				if (w!=null)
				{
					KeyListener kl =  new KeyListener()
					{
						
						@Override
						public void keyTyped( KeyEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
							System.out.println(""+e.getKeyChar());
						}
						
						@Override
						public void keyReleased( KeyEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
						}
						
						@Override
						public void keyPressed( KeyEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
						}
					};
					w.addKeyListener( kl );
					w.getCanvas().addKeyListener( kl );
					w.getCanvas().addMouseListener( new MouseListener()
					{
						
						@Override
						public void mouseReleased( MouseEvent e )
						{
							// TODO Auto-generated method stub
							
							System.out.println(""+ e );
						}
						
						@Override
						public void mousePressed( MouseEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
						}
						
						@Override
						public void mouseExited( MouseEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
						}
						
						@Override
						public void mouseEntered( MouseEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
						}
						
						@Override
						public void mouseClicked( MouseEvent e )
						{
							// TODO Auto-generated method stub
							System.out.println(""+ e );
						}
					}) ;
				} else {
					System.out.println("NO window");
				} */
				
			} 
			
			// Display the interface to save/modify the results
			DetectionGUI gui = new DetectionGUI();
			gui.setDetector( this );
			gui.setCellEvents( cell_events );
			gui.run();
			
		}
		catch ( final Exception e )
		{
			IJ.handleException( e );
		}
	}


	
	
	
	
	// Helper functions to display progress while building the Appose environment.
	// Temporary solution until Appose has a nicer built-in way to do this.

	private volatile JDialog progressDialog;

	private volatile JProgressBar progressBar;

	private void showProgress( final String msg )
	{
		showProgress( msg, null, null );
	}

	private void showProgress( final String msg, final Long cur, final Long max )
	{
		EventQueue.invokeLater( () ->
		{
			if ( progressDialog == null ) {
				final Window owner = IJ.getInstance();
				progressDialog = new JDialog( owner, "Fiji ♥ Appose" );
				progressDialog.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
				progressBar = new JProgressBar();
				progressDialog.getContentPane().add( progressBar );
				progressBar.setFont( new Font( "Courier", Font.PLAIN, 14 ) );
				progressBar.setString(
					"--------------------==================== " +
					"Building Python environment " +
					"====================--------------------"
				);
				progressBar.setStringPainted( true );
				progressBar.setIndeterminate( true );
				progressDialog.pack();
				progressDialog.setLocationRelativeTo( owner );
				progressDialog.setVisible( true );
			}
			if ( msg != null && !msg.trim().isEmpty() ) progressBar.setString( "Building Python environment: " + msg.trim() );
			if ( cur != null || max != null ) progressBar.setIndeterminate( false );
			if ( max != null ) progressBar.setMaximum( max.intValue() );
			if ( cur != null ) progressBar.setValue( cur.intValue() );
		} );
	}
	private void hideProgress()
	{
		EventQueue.invokeLater( () ->
		{
			if ( progressDialog != null )
				progressDialog.dispose();
			progressDialog = null;
		} );
	}
	
	private static final String PROGRESS_PATTERN = ".*\\d+/\\d+.*\\[.*\\].*";
	private static final String INVALID_PATTERN = ".*<INVALID>.*";
    private static final Pattern pattern = Pattern.compile(".*<INVALID>\\s*(.*)");
    /**
	 * Show messages captured by the debug service, sorting them depending on show_debug option
	 * @param msg
	 */
	private void show_messages( String msg )
	{
		boolean is_progress = msg.matches(PROGRESS_PATTERN);
		if ( show_debug )
		{
			System.out.println("[DBG] " +msg );
		}  
		else 
		{
			if (!is_progress)
			{
				if (msg.matches(INVALID_PATTERN)) 
				{
					Matcher matcher = pattern.matcher(msg);
					if (matcher.matches()) {
						IJ.log( matcher.group(1) );
					}
				}
			}
		}
		
		// Progress bar
		if (is_progress) 
		{
			int[] progresses = parseNumbersSpecific( msg );
			if ( progress_total == 0 )
			{
				progress_total = progresses[1] * 2; // should run two networks most of the time @TODO: check how many networks should be run
			}
			IJ.showProgress( shift+1+progresses[0], progress_total );
			if ( progresses[0] == progress_total /2 )
			{
				IJ.showStatus( "Event detection (2/2)" );
				shift = progress_total/2;
			}
		}
	}
	
	/** Parse the progress report to show it */
	public static int[] parseNumbersSpecific(String input) {
	    // More specific pattern matching the exact format
	    Pattern pattern = Pattern.compile("<[^>]+>\\s+(\\d+)/(\\d+)\\s+\\[");
	    Matcher matcher = pattern.matcher(input);
	    
	    if (matcher.find()) {
	        return new int[]{
	            Integer.parseInt(matcher.group(1)),
	            Integer.parseInt(matcher.group(2))
	        };
	    }
	    
	    return null;
	}


}
