package fiji.plugin.appose.dextrusion;

import java.io.File;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


/**
 * Load previous results from DeXtrusion and allows to navigate/edit
 */

@Plugin(type = Command.class, menuPath = "Plugins>DeXtrusion>Load events")
public class LoadResults implements Command
{
	@Parameter(  label = "Choose movie",
    description = "Movie file associated with results to load",
    style = "file"    
	)
	private File image_file;
	
	/** 
	 * Set the image file from a File
	 */
	public void setImageFile( String filepath )
	{
		File imgfile = new File( filepath );
		image_file = imgfile;
	}
	
	@Override
	public void run()
	{
		// Create the class to handle all loaded events
		CellEvents cell_events = new CellEvents();
		cell_events.loadFromFile( image_file );
		
		// Display the interface to save/modify the results
		DetectionGUI gui = new DetectionGUI();
		gui.setCellEvents( cell_events );
		gui.run();
	}
}
