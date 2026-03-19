package fiji.plugin.appose.dextrusion;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.TaskService;

@Plugin(type = Command.class, menuPath = "Plugins>DeXtrusion>Advanced parameters")
public class AdvancedParameters implements Command
{
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_model = "-------- Model parameters";
	
	@Parameter( label = "Use model(s) ", choices = {"notum_all", "notum_Ext", "notum_ExtSOP"}, description="Choose trained model to use for the detection, see https://github.com/Image-Analysis-Hub/DeXtrusion/tree/main?tab=readme-ov-file#dexnets-dextrusion-neural-networks " )
	public String model_choice = "notum_all";
	
	@Parameter( label="-------", description="Information",  visibility=ItemVisibility.MESSAGE )
	private String info_advanced = "-------- Running parameters";
	
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
		System.out.println("Choosing advanced parameters");
	}

}
