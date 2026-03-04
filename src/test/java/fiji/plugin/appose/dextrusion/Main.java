package fiji.plugin.appose.dextrusion;

import ij.IJ;
import net.imagej.ImageJ;

public class Main
{

	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		IJ.openImage( "/home/gaelle/Proj/RL/dextrusion/data/004-crop.tif" ).show();
		ij.command().run( Detection.class, true );
	}
}
