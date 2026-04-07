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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.io.IOUtils;
import org.apposed.appose.Appose;
import org.apposed.appose.BuildException;
import org.apposed.appose.Environment;
import org.apposed.appose.NDArray;
import org.apposed.appose.Service;
import org.apposed.appose.Service.Task;
import org.apposed.appose.Service.TaskStatus;
import org.scijava.Initializable;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.DefaultMutableModuleItem;
import org.scijava.module.ModuleItem;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
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
public class Detection extends DynamicCommand implements Initializable
{
	@Parameter
	private TaskService taskService;

	private String model_dir;
	
	@Parameter
    private PrefService prefService; // injected automatically
	
	@Parameter( label="Loaded model:", description="Information", visibility=ItemVisibility.MESSAGE )
	private String movie_info = "--------- Model info";
	
	@Parameter( label="Process", choices= {"current image", "several images (batch)"}, description="Choose to run on the current opened image or as batch on a folder" )
	private String process = "current image";
	
	@Parameter( label="Keep probability maps", description="Show/save the probability (of events) maps" )
	private boolean get_probabilities = true;

	@Parameter( label="Typical cell diameter (pixels)", description="Approximative average cell diameter, in pixels, used to scale the movie to the model scale" )
	private double cell_diameter = 25.0;
	
	@Parameter( label="Typical extrusion duration (frames)", description="Approximate cell extrusion duration (number of frames where it's visible), used to scale the movie temporally")
	private double extrusion_duration = 4.5;
	
	
	@Parameter( label="-------", description="Information", visibility=ItemVisibility.MESSAGE )
	private String roi_parameters = "--------- ROI parameters";
	
	private List<String> color_list = Arrays.asList("magenta", "cyan", "orange", "green", "red", "yellow", "blue");

	// Fiji task
	private org.scijava.task.Task fijiTask;
	// dextrusion progress
	private int progress_total = 0;
	private int shift = 0;
	// parameters read from the model config file
	private DeXtrusionConfig config = null;
	// name of events detected by the model
	private String[] event_names = {""};
	
	private Map<String, ModuleItem<Boolean>> checkboxROIItems = new HashMap<>();
	private Map<String, ModuleItem<Integer>> thresholdROIItems = new HashMap<>();
	

	

	/**
	 * Before to ask for the parameters, check the model to use
	 */
	@Override
	public void initialize() 
	{
		// Download and install if necessary the notum model
		String model_name = prefService.get(AdvancedParameters.class, "model_choice", "notum_all");
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
		
		// Read the configuration file of the model to extract infos
		try 
		{
			config = new DeXtrusionConfig( model_dir );
			event_names = config.getCleanEventNames();   
		}
		catch (Exception e)
		{
			IJ.error( "Failed to read model configuration: "+e );
		}
			
		final MutableModuleItem<String> infoItem = //
			getInfo().getMutableInput("movie_info", String.class);
		String evt_names = "";
		for (int j=0; j<event_names.length; j++ ) evt_names += " "+event_names[j];
		System.out.println(""+model_name);
		infoItem.setValue( this, " "+model_name+" with events:"+evt_names);
		infoItem.setPersisted(false);
		
		for ( int i=0; i<event_names.length; i++ )
		{
			String event = event_names[i];
			if ( event.contains("cell_SOP") || event.equals("") )
			{
				continue;
			}
			// Add check box for the event
			final ModuleItem<Boolean> item = new DefaultMutableModuleItem<>(getInfo(),
				event+"_ROIs", boolean.class);
			item.setLabel("Get "+event+" ROIs" );
			boolean checked = Boolean.valueOf( prefService.get(this.getClass(), event+"_ROIs", "true") );			
			item.setDescription( "Extract point-like detection of this event from the probability map" );
			item.setValue(this, checked);
			checkboxROIItems.put(""+event, item);
			getInfo().addInput(item);
			
			// Add threshold detection for the event
			final ModuleItem<Integer> thresitem = new DefaultMutableModuleItem<>(getInfo(),
				"threshold_"+event, Integer.class);
			thresitem.setLabel( ""+event+" detection threshold" );
			int value = Integer.valueOf( prefService.get(this.getClass(), "threshold_"+event, "180") );
			thresitem.setValue(this, value);
			thresitem.setDescription( "Set the probability threshold to consider a pixel as positive for this event" );
			thresholdROIItems.put( "threshold_"+event, thresitem );
			getInfo().addInput( thresitem );
			
			// Add threshold volume for the event
			final ModuleItem<Integer> volitem = new DefaultMutableModuleItem<>(getInfo(),
				"volume_"+event, Integer.class);
			volitem.setLabel( ""+event+" detection volume" );
			int vol = Integer.valueOf( prefService.get(this.getClass(), "volume_"+event, "700") );
			volitem.setValue(this, vol);
			volitem.setDescription( "Set the minimum volume of positive pixels to consider an event present at the center" );	
			thresholdROIItems.put( "volume_"+event, volitem );
			getInfo().addInput( volitem );
		}
	}

	
	
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
		fijiTask.setStatusMessage( "⌛ Launching DeXtrusion appose task." );
		fijiTask.start();
		
		if ( process.equals("current image") )
		{
			// Grab the current image.
			final ImagePlus imp = WindowManager.getCurrentImage();
		
			if ( imp == null )
			{
				IJ.error( "No opened movie found. Open one before" );
				return;
			}
			try
			{
				// Runs the processing code.
				process_one_image( imp );
			}
			catch ( final IOException | BuildException e ) {
				IJ.error("An error occurred: " + e.getMessage());
				e.printStackTrace();
			}
		}
		else 
		{
			 JFileChooser chooser = new JFileChooser( IJ.getDirectory("file") );
		        chooser.setDialogTitle("Select images to process");
		        chooser.setMultiSelectionEnabled(true); 
		        FileNameExtensionFilter filter = new FileNameExtensionFilter(
		                "Image Files", "tif", "tiff" );
		        chooser.setFileFilter(filter);
		        
		        // Show dialog and capture result
		        int returnValue = chooser.showOpenDialog(null);

		        if (returnValue == JFileChooser.APPROVE_OPTION) 
		        {
		            File[] files = chooser.getSelectedFiles();
		            try
		            {
		            	// Runs the processing code.
		            	process_files( files );
		            }
		            catch ( final IOException | BuildException e ) {
		            	IJ.error("An error occurred: " + e.getMessage());
		            	e.printStackTrace();
		            }
		        }
		}
			
		
		
	}
	
	/**
	 * Only one image will be processed, with interactive postprocessing after
	 * @param <T>
	 * @param imp
	 * @throws IOException
	 * @throws BuildException
	 */
	public  < T extends RealType< T > & NativeType< T > > void process_one_image( final ImagePlus imp ) throws IOException, BuildException
	{
		// Wrap the ImagePlus into a ImgLib2 image.
		final ImgPlus< T > img = rawWraps( imp );
		/*
		 * Transfer the movie to shared object
		 */
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "movie", NDArrays.asNDArray( img ) );

		String fullPath = AppUtils.getFullPath( imp );
		inputs.put( "movie_path", fullPath ); 	
		inputs.put( "input_image", true );  // process the input image
		
		Task task = process( inputs );

		// Handle post-processing of the results
		CellEvents cell_events = new CellEvents();
		cell_events.setParameters( inputs );
		cell_events.setMovie( imp );
		// Get the output and process them
		List<Map<String, Object>> map_rois = (List<Map<String, Object>>) task.outputs.get( "rois" );		
		cell_events.readRois( map_rois, true );
		
		// If probamaps were calculated
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
		} 
	
		// Display the interface to save/modify the results
		DetectionGUI gui = new DetectionGUI();
		gui.setDetector( this );
		gui.setCellEvents( cell_events );
		gui.run();
		
	}

	
	public  void process_files( final File[] files ) throws IOException, BuildException
	{
		final Map< String, Object > inputs = new HashMap<>();
		String[] movies = new String[files.length];
		for ( int i=0; i<files.length; i++ )
		{
			movies[i] = files[i].getAbsolutePath();
		}
		inputs.put( "movies", movies );
		inputs.put( "input_image", false );  // process the files from path
		process( inputs );
	}
	
	/*
	 * Calls the python service to process the movie(s)
	 */
	public < T extends RealType< T > & NativeType< T > > Task process( final Map<String, Object> inputs ) throws IOException, BuildException
	{
		// Print os and arch info
		System.out.println( "Starting process..." );

		/*
		 * The Python script that we want to run. It is loaded from the run_detection.py file in the resource folder. 
		 */
		final String script = getScript( this.getClass().getResource("run_detection.py" ) );

		// Get the advanced parameters
		int group_size = Integer.valueOf( prefService.get(AdvancedParameters.class, "group_size", "150000") );
		int dist_xy = Integer.valueOf( prefService.get(AdvancedParameters.class, "disxy", "10") );
		int dist_time = Integer.valueOf( prefService.get(AdvancedParameters.class, "distime", "4") );

		int shift_xy = Integer.valueOf( prefService.get(AdvancedParameters.class, "shift_xy", "10") );
		int shift_t = Integer.valueOf( prefService.get(AdvancedParameters.class, "shift_t", "2") );
		
		// Still ask these two parameters to allow to use movie of different scales than the model
		//double cell_diameter = config.getCellDiameter();
		//double extrusion_duration = config.getExtrusionDuration();
		
		// Put all parameters to pass to run dextrusion
		inputs.put( "cell_diameter", cell_diameter );
		inputs.put( "extrusion_duration", extrusion_duration );
		//inputs.put( "model", modelDirectory.getAbsolutePath() );
		inputs.put( "model", model_dir );
		inputs.put( "get_probabilities", get_probabilities );
		inputs.put( "group_size", group_size );
		inputs.put( "dist_xy", dist_xy );
		inputs.put( "dist_time", dist_time );
		inputs.put( "shift_xy", shift_xy );
		inputs.put( "shift_time", shift_t );
		
		inputs.put( "event_roi_names", event_names );
		
		// Add event specific parameters
		for ( int i=0; i<event_names.length; i++ )
		{
			String event = event_names[i];
			if ( event.equals("") || event.equals("cell_SOP") )
			{
				continue;
			}
			System.out.println(""+event);
			boolean get_event = (boolean) checkboxROIItems.get(""+event).getValue(this);
			inputs.put( "get_"+event, get_event );
			int threshold = (int) thresholdROIItems.get("threshold_"+event).getValue(this);
			inputs.put( event+"_threshold", threshold );
			int volume = (int) thresholdROIItems.get("volume_"+event).getValue(this);
			inputs.put( event+"_volume", volume );
		}
		
		/*
		 * Create or retrieve the environment.
		 */
		IJ.log( "Downloading/Installing the environement if necessary..." );
		
		// Check if cuda or not
		String os = System.getProperty("os.name").toLowerCase();
        boolean isGpuPlatform = os.contains("linux") || os.contains("win");
        String envName = isGpuPlatform ? "cuda" : "default";
		
		final Environment env = Appose // the builder
				.pixi(this.getClass().getResource("pixi.toml")) // we chose pixi as the environment manager
				.subscribeProgress( this::showProgress ) // report progress visually
				.subscribeOutput( this::showProgress ) // report output visually
				.subscribeError( IJ::log ) // log problems
	            .environment(envName)  // choose env based on OS (to get cuda or not)
				.build(); // create the environment
		hideProgress();

		/*
		 * Using this environment, we create a service that will run the Python
		 * script.
		 */
		try ( Service python = env.python() ) 
		{
			// Import all that depends on numpy for Windows
			python.init("import os\n"
					+ "import numpy as np\n"
					+ "from dextrusion.DeXtrusion import DeXtrusion");
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
			
			if ( process.equals("current image") )
				return task;
			
			return null;
		}
		catch ( Exception e)
		{
			IJ.error( "" + e );
		}
		return null;
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
		boolean show_debug = Boolean.valueOf( prefService.get(AdvancedParameters.class, "show_debug", "false") );
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
