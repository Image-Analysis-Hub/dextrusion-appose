package fiji.plugin.appose.dextrusion;

import java.awt.Color;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.imagej.ImgPlus;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.type.numeric.real.DoubleType;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.zip.*;


public class AppUtils
{

	/**
	 * A utility to wrap an ImagePlus into an ImgPlus, without too many
	 * warnings. Hacky.
	 */
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public static final < T > ImgPlus< T > rawWraps( final ImagePlus imp )
	{
		final ImgPlus< DoubleType > img = ImagePlusAdapter.wrapImgPlus( imp );
		final ImgPlus raw = img;
		return raw;
	}

	private static LUT loadLutFromResource( final String resourcePath )
	{
		try (InputStream is = AppUtils.class.getResourceAsStream( resourcePath );
				BufferedReader reader = new BufferedReader( new InputStreamReader( is ) ))
		{

			if ( is == null )
			{
				IJ.error( "LUT resource not found: " + resourcePath );
				return null;
			}

			final byte[] reds = new byte[ 256 ];
			final byte[] greens = new byte[ 256 ];
			final byte[] blues = new byte[ 256 ];
			String line;
			int index = 0;

			while ( ( line = reader.readLine() ) != null && index < 256 )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue; // Skip empty lines

				// Split by whitespace
				final String[] parts = line.split( "\\s+" );
				if ( parts.length >= 3 )
				{
					reds[ index ] = ( byte ) Integer.parseInt( parts[ 0 ] );
					greens[ index ] = ( byte ) Integer.parseInt( parts[ 1 ] );
					blues[ index ] = ( byte ) Integer.parseInt( parts[ 2 ] );
					index++;
				}
			}

			if ( index != 256 )
			{
				IJ.error( "Invalid LUT file: expected 256 entries, found " + index );
				return null;
			}

			return new LUT( reds, greens, blues );
		}
		catch ( final IOException e )
		{
			IJ.error( "Failed to load LUT: " + e.getMessage() );
			return null;
		}
	}

	public static final void useGlasbeyDarkLUT( final ImagePlus imp )
	{
		final LUT lut = loadLutFromResource( "/glasbey_on_dark.lut" );
		useLUT( imp, lut );
	}

	public static final void useLUT( final ImagePlus imp, final LUT lut )
	{
		imp.setLut( lut );
		imp.updateAndDraw();
	}
	
	/**
     * Create LUT for a named color
     */
    public static LUT createColorLUT(String colorName) {
        Color color = getColorByName(colorName);
        if (color == null) {
            return null;
        }
        
        // Create arrays for RGB components
        int size = 256; // Standard LUT size
        byte[] red = new byte[size];
        byte[] green = new byte[size];
        byte[] blue = new byte[size];
        
        // Fill arrays with the color gradient
        for (int i = 0; i < size; i++) {
            // Linear gradient from black to full color
            red[i] = (byte) ((color.getRed() * i) / 255);
            green[i] = (byte) ((color.getGreen() * i) / 255);
            blue[i] = (byte) ((color.getBlue() * i) / 255);
        }
        
        // Create LUT
        return new LUT(red, green, blue);
    }
	 
    /**
     * Get Color object from color name
     */
    public static Color getColorByName(String colorName) {
        if (colorName == null) return null;
        
        switch (colorName.toLowerCase()) {
            case "red": return Color.RED;
            case "green": return Color.GREEN;
            case "blue": return Color.BLUE;
            case "yellow": return Color.YELLOW;
            case "magenta": return Color.MAGENTA;
            case "cyan": return Color.CYAN;
            case "orange": return Color.ORANGE;
            case "pink": return Color.PINK;
            case "white": return Color.WHITE;
            case "black": return Color.BLACK;
            default: return Color.BLACK;
        }
    }
    
    /**
	 * Set all the channels of imp with LUT from colors
	 * @param imp
	 * @param colors
	 */
	public static final void setChannelsLUT( ImagePlus imp, List<String> colors )
	{
		for (int i=1; i <= imp.getNChannels(); i++ )
		{
			imp.setPosition( i, 1, 1 );
			ImageProcessor proc = imp.getChannelProcessor();	
			LUT lut = createColorLUT(colors.get(i-1));
			proc.setLut( lut );
			imp.updateAndDraw();	
			//imp.setLut( lut );
			
		}
	}
	
	/** 
	 * Set a LUT from a given color
	 * @param imp
	 * @param color
	 */
	public static void setLUTFromColor( ImagePlus imp, Color color )
	{
		LUT lut = LUT.createLutFromColor( color );
		imp.setLut( lut );
		imp.getProcessor().resetMinAndMax();
		imp.updateAndDraw();
	}

	/**
	 * Transfers the calibration of an {@link ImagePlus} to another one,
	 * generated from a capture of the first one.
	 *
	 * @param from
	 *            the imp to copy from.
	 * @param to
	 *            the imp to copy to.
	 */
	public static final void transferCalibration( final ImagePlus from, final ImagePlus to )
	{
		final Calibration fc = from.getCalibration();
		final Calibration tc = to.getCalibration();

		tc.setUnit( fc.getUnit() );
		tc.setTimeUnit( fc.getTimeUnit() );
		tc.frameInterval = fc.frameInterval;

		tc.pixelWidth = fc.pixelWidth;
		tc.pixelHeight = fc.pixelHeight;
		tc.pixelDepth = fc.pixelDepth;
	}
	
	/*
	 * The Python script.
	 * 
	 * This is the Python code that will be run by the service. It is loaded from an existing
	 * .py file, placed in the URL location */
	public static String getScript( URL python_script )
	{
		String script = "";
		try {
			final URL scriptFile = python_script;
			script = IOUtils.toString(scriptFile, StandardCharsets.UTF_8);
			
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return script;
	}

	/**
	 * Download and extract a .zip file if necessary 
	 * @param fileUrl
	 * @param zipFilePath
	 * @param destinationDir
	 * @throws IOException
	 */
	public static String downloadAndExtract( String destinationDir, String modelName, String model_URL ) throws IOException 
	{
	        
	        Path modelPath = Path.of( destinationDir, modelName ); // model specific directory in .local/dextrusion
	        System.out.println(""+modelPath.toString());
	        
	        // Check if destination exists and is not empty
	        if (Files.exists( modelPath ) && 
	            Files.isDirectory( modelPath ) && 
	            Files.list( modelPath ).findAny().isPresent()) 
	        {
	            //System.out.println("Destination already exists and is not empty.");
	            return modelPath.toAbsolutePath().toString();
	        }
	        
	        // Download with progress
	        String zipfile = Path.of( destinationDir, modelName+".zip").toString();
	        System.out.println(""+zipfile);
	        
	        downloadFileWithProgress( model_URL, zipfile );
	        
	        // Extract
	        extractZip( zipfile, destinationDir.toString() );
	        
	        // Cleanup
	        Files.deleteIfExists( Paths.get( zipfile ) );
	        return modelPath.toAbsolutePath().toString();
	    }
	    
		/** Download .zip file and show progress of download */
	private static void downloadFileWithProgress(String fileUrl, String destinationPath) 
	        throws IOException 
	{
	    
		 URI uri = URI.create( fileUrl );
         URL url = uri.toURL();
         System.out.println(""+url.toString());
         HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	    
	    // Disable automatic decompression
	    connection.setRequestProperty("Accept-Encoding", "identity");
	    connection.setInstanceFollowRedirects(true);
	    //connection.setConnectTimeout(10000); // 10 seconds
        //connection.setReadTimeout(30000); // 30 seconds
       
	    //connection.connect();
	    
	    int responseCode = connection.getResponseCode();
	    if (responseCode != HttpURLConnection.HTTP_OK) 
	    {
	        throw new IOException("Failed to download. HTTP error code: " + responseCode);
	    }
	    
	    long fileSize = connection.getContentLengthLong();
	    
	    try (InputStream in = connection.getInputStream()) {
	        
	        // Use custom InputStream wrapper to track progress
	        ProgressInputStream progressIn = new ProgressInputStream(in, fileSize);
	        
	        // Copy directly to file
	        Files.copy(progressIn, Paths.get(destinationPath), 
	                   StandardCopyOption.REPLACE_EXISTING);
	        
	        System.out.println("\nDownload complete!");
	    } finally {
	        connection.disconnect();
	    }
	}

	// Progress tracking wrapper
	static class ProgressInputStream extends FilterInputStream {
	    private long totalBytesRead = 0;
	    private long fileSize;
	    private int lastProgress = 0;
	    
	    public ProgressInputStream(InputStream in, long fileSize) {
	        super(in);
	        this.fileSize = fileSize;
	    }
	    
	    @Override
	    public int read(byte[] b, int off, int len) throws IOException {
	        int bytesRead = super.read(b, off, len);
	        if (bytesRead > 0) {
	            totalBytesRead += bytesRead;
	            if (fileSize > 0) {
	                int progress = (int) ((totalBytesRead * 100) / fileSize);
	                if (progress != lastProgress) {
	                    System.out.print("\rDownloading: " + progress + "%");
	                    lastProgress = progress;
	                }
	            }
	        }
	        return bytesRead;
	    }
	}
	    /** 
	     * Extract the content of the .zip file 
	     * @param zipFilePath
	     * @param destinationDir
	     * @throws IOException
	     */
	    private static void extractZip( String zipFilePath, String destinationDir ) 
	            throws IOException 
	    {
	        byte[] buffer = new byte[8192];
	        
	        try (ZipInputStream zis = new ZipInputStream(
	                new BufferedInputStream(new FileInputStream(zipFilePath)))) 
	        {
	            
	            ZipEntry entry;
	           
	            while ((entry = zis.getNextEntry()) != null) 
	            {
	                Path filePath = Paths.get( destinationDir, entry.getName()) ;
	                //System.out.println(""+entry.getName());
	                
	                // Security check
	                if ( !filePath.normalize().startsWith(destinationDir) ) 
	                {
	                    throw new IOException("Bad zip entry");
	                }
	                
	                if (entry.isDirectory()) 
	                {
	                    Files.createDirectories(filePath);
	                    System.out.println(""+filePath);
	                } 
	                else 
	                {
	                    Files.createDirectories( filePath.getParent() );
	                    
	                    try (BufferedOutputStream bos = new BufferedOutputStream(
	                            new FileOutputStream(filePath.toFile()))) {
	                        int len;
	                        while ((len = zis.read(buffer)) > 0) {
	                            bos.write(buffer, 0, len);
	                        }
	                    }
	                }
	                System.out.println("Extracted: " + entry.getName());
	            }
	        }
	    }
	
	    public static String createLocalDirectory( String dirname ) 
	    {
	        try 
	        {
	            // Create directory in user's .local folder if not there
	            String userHome = System.getProperty( "user.home" );
	            Path locals = Paths.get( userHome, ".local" );
	            if ( !Files.exists( locals ) ) 
		        {
		            Files.createDirectories( locals );
		        } 
		        
		        Path localshare = Paths.get( userHome, ".local", "share" );
	            if ( !Files.exists( localshare ) ) 
		        {
		            Files.createDirectories( localshare );
		        } 
		        Path localDir = Paths.get(userHome, ".local", "share", dirname );
		        
		        // Check if exists, create if not
		        if ( !Files.exists(localDir) ) 
		        {
		            Files.createDirectories(localDir);
		            System.out.println("Created directory: " + localDir);
		        } 
		        
		        return localDir.toString();    
	         } 
	        catch (IOException e) 
	        {
	            e.printStackTrace();
	        }
	        return "";
	   }
	    
	   
}
