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
 * This class implements an example of a classical Fiji plugin, 
 * that calls native Python code with Appose.
 * 
 */

@Plugin(type = Command.class, menuPath = "Plugins>DeXtrusion-Appose>Detect events")
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
		 * Copy the image into a shared memory image and wrap it into an
		 * NDArray, then store it in an input map that we will pass to the
		 * Python script.
		 * 
		 * Note that we could have passed multiple inputs to the Python script
		 * by putting more entries in the input map, and they would all be
		 * available in the Python script as shared memory NDArrays.
		 * 
		 * A ND array is a multi-dimensional array that is stored in shared
		 * memory, that can be unwrapped as a NumPy array in Python, and wrapped
		 * as a ImgLib2 image in Java.
		 * 
		 */
		final Map< String, Object > inputs = new HashMap<>();
		inputs.put( "movie", NDArrays.asNDArray( img ) );

		FileInfo fileInfo = imp.getOriginalFileInfo();
		if (fileInfo != null && fileInfo.fileName != null) 
		{
		    String fileName = fileInfo.fileName;
		    String directory = fileInfo.directory;
		    
		    if (directory != null && fileName != null) 
		    {
		        String fullPath = new File(directory, fileName).getAbsolutePath();
		        IJ.log("Movie full path: " + fullPath);
		        inputs.put( "movie_path", fullPath );
		    } 
		    else if (fileName != null) 
		    {
		    	fileName = imp.getTitle();
		        directory = IJ.getDirectory( "file" );  // most recent directory
		        String fullPath = new File(directory, fileName).getAbsolutePath();
		        IJ.log("Movie full path: " + fullPath);
		        inputs.put( "movie_path", fullPath );   
		    }
		} 
		else 
		{
		    System.out.println("No file information available");
		}
		
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
		 * 
		 * The first time this code is run, Appose will create the pixi
		 * environment as specified by the dextrusionEnv string, download and
		 * install the dependencies. This can take a few minutes, but it is only
		 * done once. The next time the code is run, Appose will just reuse the
		 * existing environment, so it will start much faster.
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
			
			/*
			 * With this service, we can now create a task that will run the
			 * Python script with the specified inputs. This command takes the
			 * script as first argument, and a map of inputs as second argument.
			 * The keys of the map will be the variable names in the Python
			 * script, and the values are the data that will be passed to
			 * Python.
			 */
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

			/*
			 * Unwrap output.
			 * 
			 * In the Python script (see below), we create a new NDArray called
			 * 'rotated' that contains the result of the processing. Here we
			 * retrieve this NDArray from the task outputs, and wrap it into a
			 * ShmImg, which is an ImgLib2 image that is backed by shared
			 * memory. We can then display this image with
			 * ImageJFunctions.show(). Note that this does not involve any
			 * copying of the data, as the NDArray and the ShmImg are both just
			 * views on the same shared memory array.
			 */
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
			
			RoiManager rm = RoiManager.getInstance();
			if (rm == null )
			{
				rm = new RoiManager();
			}
			rm.reset();
			List<Map<String, Object>> map_rois = (List<Map<String, Object>>) task.outputs.get( "rois" );
			Color[] colors = {new Color(200,50,0), new Color(0,50,200) };
			for ( Map<String, Object> roi : map_rois ) 
			{
	            try {
	            		//System.out.println(roi.get( "position_yx" ) );
	            		List<Integer> pos = (List<Integer>) roi.get(  "position_yx" );
	            		PointRoi proi = new PointRoi(pos.get( 1 ), pos.get( 0 ));
	            		proi.setSize( 3 );
	            		String roi_name = (String) roi.get( "name" );
	            		int roi_type = roi_name.equals("cell_death") ? 0 : 1;
	            		proi.setStrokeColor( colors[roi_type] );
	            		proi.setName( ""+roi_name );
	            		proi.setImage( imp );
	            		imp.setRoi( proi );
	            		int roi_frame = (int) roi.get( "position_frame" );
	            		proi.setPosition( 1, 1, roi_frame );
	            		rm.addRoi( proi ); // Add to RoiManager
	            		
	            } 
	            catch (Exception e) 
	            {
	                System.err.println("Error creating ROI: " + e.getMessage());
	                e.printStackTrace();
	            }
	        }
			
			rm.setVisible( true );
			rm.runCommand("Show All");  
			
			
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
