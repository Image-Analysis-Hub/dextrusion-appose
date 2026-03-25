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

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.TaskService;

import ij.IJ;

@Plugin(type = Command.class, menuPath = "Plugins>DeXtrusion>Set advanced parameters")
public class AdvancedParameters implements Command
{
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_model = "-------- Model parameters";
	
	@Parameter( label = "Use model(s) ", choices = {"notum_all", "notum_Ext", "notum_ExtSOP"}, description="Choose trained model to use for the detection, see https://github.com/Image-Analysis-Hub/DeXtrusion/tree/main?tab=readme-ov-file#dexnets-dextrusion-neural-networks " )
	public String model_choice = "notum_all";
	
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String para_advanced = "-------- Running parameters";
	
	@Parameter( label="Detection window spatial shift", description="Shift spatial distance of moving windows spanning the whole movie" )
	private int shift_xy = 10;
	
	@Parameter( label="Detection window temporal shift", description="Shift temporal distance of moving windows spanning the whole movie" )
	private int shift_t = 2;
	
	@Parameter( label="Peak spatial distance", description="Try to separate close events: spatial distance between possible events" )
	private int disxy = 10;
	
	@Parameter( label="Peak temporal distance", description="Try to separate close events: temporal distance between possible events" )
	private int distime = 4;
	
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_advanced = "-------- Computation parameters";
	
	@Parameter( label="Show debug messages", description="Show full debug messages in the Console" )
	private boolean show_debug = false;
	
	@Parameter( label="Computation size", description="Depending on computer capabilities, can compute more at one time" )
	private int group_size = 150000;
	
	
	/**@Parameter(  
	label = "Choose model directory",
    description = "Directory that contains the model(s) to run",
    style = "directory"    
)
private File modelDirectory;*/
	
	@Override
	public void run()
	{
		IJ.log("Advanced parameters setted. Now use DeXtrusion>Detect events to start detection");
	}

}
