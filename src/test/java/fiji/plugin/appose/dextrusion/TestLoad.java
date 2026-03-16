package fiji.plugin.appose.dextrusion;

import ij.IJ;
import net.imagej.ImageJ;

public class TestLoad 
{

	public static void main( final String[] args )
	{
		final ImageJ ij = new ImageJ();
		ij.launch();
		LoadResults lr = new LoadResults();
		lr.setImageFile( "/home/gaelle/Proj/IAH/HackatonAppose/dextrusion-appose/data/002-crop-1.tif" );
		lr.run();
	}
	
}
