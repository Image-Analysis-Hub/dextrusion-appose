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

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DeXtrusionConfig 
{

    // --- Fields ---
    private int         nbEventsCategory;
    private String[]    eventsCategoryNames;
    private int[]       windowHalfSize;
    private int[]       nbTemporalFrames;
    private double         cellDiameter;
    private double      extrusionDuration;
    private int         neuralnetworkNbFilters;
    private int         neuralnetworkBatchSize;
    private int         neuralnetworkNbEpochs;
    private int         datatrainingAugmentation;
    private int         datatrainingAddnothingwindows;
    private String      datatrainingPath;

    // --- Constructor ---
    public DeXtrusionConfig(String modelPath) throws IOException 
    {
    	Path filePath = findConfig( modelPath );
        parse(filePath);
    }
    
    public static Path findConfig(String rootDir) throws IOException 
    {
        return Files.walk(Paths.get(rootDir))
                    .filter(p -> p.getFileName().toString().equals("config.cfg"))
                    .findFirst()
                    .orElse(null);
    }

    // --- Parser ---
    private void parse(Path filePath) throws IOException 
    {
        List<String> lines = Files.readAllLines(filePath);

        for (String line : lines) {
            // Skip comments and empty lines
            if (line.startsWith("#") || line.trim().isEmpty()) continue;

            // Split on first '=' only
            int sep = line.indexOf('=');
            if (sep == -1) continue;

            String key   = line.substring(0, sep).trim();
            String value = line.substring(sep + 1).trim();

            switch (key) {
                case "nb_events_category":
                    nbEventsCategory = Integer.parseInt(value);
                    break;

                case "events_category_names":
                    eventsCategoryNames = parseStringList(value);
                    break;

                case "window_half_size":
                    windowHalfSize = parseIntTuple(value);
                    break;

                case "nb_temporal_frames":
                    nbTemporalFrames = parseIntTuple(value);
                    break;

                case "cell_diameter":
                    cellDiameter = Double.parseDouble(value);
                    break;

                case "extrusion_duration":
                    extrusionDuration = Double.parseDouble(value);
                    break;

                case "neuralnetwork_nb_filters":
                    neuralnetworkNbFilters = Integer.parseInt(value);
                    break;

                case "neuralnetwork_batch_size":
                    neuralnetworkBatchSize = Integer.parseInt(value);
                    break;

                case "neuralnetwork_nb_epochs":
                    neuralnetworkNbEpochs = Integer.parseInt(value);
                    break;

                case "datatraining_augmentation":
                    datatrainingAugmentation = Integer.parseInt(value);
                    break;

                case "datatraining_addnothingwindows":
                    datatrainingAddnothingwindows = Integer.parseInt(value);
                    break;

                case "datatraining_path":
                    datatrainingPath = value;
                    break;

                default:
                    System.out.println("Unknown key: " + key);
            }
        }
    }

    // --- Helpers ---

    // Parses: (22, 22) → int[]{22, 22}
    private int[] parseIntTuple(String value) {
        value = value.replaceAll("[()\\s]", "");       // remove ( ) and spaces
        String[] parts = value.split(",");
        return Arrays.stream(parts)
                     .mapToInt(Integer::parseInt)
                     .toArray();
    }

    // Parses: ['', '_cell_death.zip', ...] → String[]{"", "_cell_death.zip", ...}
    private String[] parseStringList(String value) {
        value = value.replaceAll("[\\[\\]\\s]", "");   // remove [ ] and spaces
        String[] parts = value.split(",");
        return Arrays.stream(parts)
                     .map(s -> s.replaceAll("'", ""))  // remove quotes
                     .toArray(String[]::new);
    }

    // --- Getters ---
    public int       getNbEventsCategory()            { return nbEventsCategory; }
    public String[]  getEventsCategoryNames()          { return eventsCategoryNames; }
    public int[]     getWindowHalfSize()               { return windowHalfSize; }
    public int[]     getNbTemporalFrames()             { return nbTemporalFrames; }
    public double       getCellDiameter()                 { return cellDiameter; }
    public double    getExtrusionDuration()            { return extrusionDuration; }
    public int       getNeuralnetworkNbFilters()       { return neuralnetworkNbFilters; }
    public int       getNeuralnetworkBatchSize()       { return neuralnetworkBatchSize; }
    public int       getNeuralnetworkNbEpochs()        { return neuralnetworkNbEpochs; }
    public int       getDatatrainingAugmentation()     { return datatrainingAugmentation; }
    public int       getDatatrainingAddnothingwindows(){ return datatrainingAddnothingwindows; }
    public String    getDatatrainingPath()             { return datatrainingPath; }

    
    /**
     * Get clean events name: only the one that should be converted to ROI
     */
    public String[] getCleanEventNames()
    {
    	ArrayList<String> results = new ArrayList<String>();
    	for ( String event: eventsCategoryNames )
    	{
    		if ( event.equals("") || event.contains("cell_SOP") || event.contains("cell_sop") )
    			continue;
    		
    		if ( event.endsWith(".zip") )
    		{
    			event = event.substring(0, event.length()-4 );
    		}
    		if ( event.startsWith("_") )
    		{
    			event = event.substring(1);
    		}
    		results.add( event );
    	}
    	
    	String[] res = new String[results.size()];
    	for (int i=0; i<results.size(); i++)
    		res[i] = (String) results.get(i);
    	return res;
    }
    
    // --- Debug print ---
    @Override
    public String toString() {
        return String.format(
            "nbEventsCategory=%d%n" +
            "eventsCategoryNames=%s%n" +
            "windowHalfSize=%s%n" +
            "nbTemporalFrames=%s%n" +
            "cellDiameter=%d%n" +
            "extrusionDuration=%.1f%n" +
            "neuralnetworkNbFilters=%d%n" +
            "neuralnetworkBatchSize=%d%n" +
            "neuralnetworkNbEpochs=%d%n" +
            "datatrainingAugmentation=%d%n" +
            "datatrainingAddnothingwindows=%d%n" +
            "datatrainingPath=%s",
            nbEventsCategory,
            Arrays.toString(eventsCategoryNames),
            Arrays.toString(windowHalfSize),
            Arrays.toString(nbTemporalFrames),
            cellDiameter,
            extrusionDuration,
            neuralnetworkNbFilters,
            neuralnetworkBatchSize,
            neuralnetworkNbEpochs,
            datatrainingAugmentation,
            datatrainingAddnothingwindows,
            datatrainingPath
        );
    }
}
