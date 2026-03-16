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
