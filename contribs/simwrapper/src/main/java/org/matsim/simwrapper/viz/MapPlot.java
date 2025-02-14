package org.matsim.simwrapper.viz;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Provide map based plots.
 */
public final class MapPlot extends Viz {

	public double[] center;
	public Double zoom;

	public Display display = new Display();
	@JsonProperty(required = true)
	private Object shapes;
	private Map<String, String> datasets = new HashMap<>();

	public MapPlot() {
		super("map");
	}

	/**
	 * Set the shape url, providing the path.
	 */
	public MapPlot setShapes(String file) {
		shapes = file;
		return this;
	}

	/**
	 * Set shape url and join path for dataset.
	 */
	public MapPlot setShape(String file, String join) {
		shapes = Map.of("file", file, "join", join);
		return this;
	}

	/**
	 * Add named dataset to the map. This name can be referenced in the display section.
	 */
	public MapPlot addDataset(String name, String file) {
		datasets.put(name, file);
		return this;
	}

	/**
	 * Display section for various attributes.
	 */
	public static final class Display {

		public DisplaySettings lineWidth = new DisplaySettings();

		public DisplaySettings lineColor = new DisplaySettings();

		public DisplaySettings fill = new DisplaySettings();

		public DisplaySettings fillHeight = new DisplaySettings();

	}


	/**
	 * Generalized display settings.
	 */
	public static final class DisplaySettings {

		@JsonProperty(required = true)
		public String dataset;

		@JsonProperty(required = true)
		public String columnName;

		@JsonProperty(required = true)
		public String join;

		public Double scaleFactor;

		private Map<String, Object> colorRamp;

		/**
		 * Set the color ramp name.
		 */
		public DisplaySettings setColorRamp(String ramp) {
			colorRamp = Map.of("ramp", ramp);
			return this;
		}

		/**
		 * Sets the full color ramps settings.
		 */
		public DisplaySettings setColorRamp(String ramp, int steps, boolean reverse) {
			colorRamp = Map.of("ramp", ramp, "reverse", reverse, "steps", steps);
			return this;
		}
	}
}
