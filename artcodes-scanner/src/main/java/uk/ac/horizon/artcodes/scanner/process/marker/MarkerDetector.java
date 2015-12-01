/*
 * Artcodes recognises a different marker scheme that allows the
 * creation of aesthetically pleasing, even beautiful, codes.
 * Copyright (C) 2013-2015  The University of Nottingham
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published
 *     by the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.horizon.artcodes.scanner.process.marker;

import android.util.Log;
import android.widget.ImageButton;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import uk.ac.horizon.artcodes.model.Action;
import uk.ac.horizon.artcodes.model.Experience;
import uk.ac.horizon.artcodes.scanner.ImageBuffers;
import uk.ac.horizon.artcodes.scanner.R;
import uk.ac.horizon.artcodes.scanner.TextAnimator;
import uk.ac.horizon.artcodes.scanner.detect.MarkerDetectionHandler;
import uk.ac.horizon.artcodes.scanner.process.ImageProcessor;
import uk.ac.horizon.artcodes.scanner.process.ImageProcessorSetting;

public class MarkerDetector implements ImageProcessor
{
	private enum CodeDisplay
	{
		hidden, visible;

		private static CodeDisplay[] vals = values();

		public CodeDisplay next()
		{
			return vals[(this.ordinal() + 1) % vals.length];
		}
	}

	private enum OutlineDisplay
	{
		none, marker, regions;

		private static OutlineDisplay[] vals = values();

		public OutlineDisplay next()
		{
			return vals[(this.ordinal() + 1) % vals.length];
		}
	}

	private static final Scalar detectedColour = new Scalar(255, 255, 0, 255);
	private static final Scalar regionColour = new Scalar(255, 128, 0, 255);
	private static final Scalar outlineColour = new Scalar(0, 0, 0, 255);

	protected static final int NEXT_NODE = 0;
	protected static final int FIRST_NODE = 2;

	protected final int checksum;
	protected final Collection<String> validCodes = new HashSet<>();
	protected final int minRegions;
	protected final int maxRegions;
	protected final int maxRegionValue;

	private final MarkerDetectionHandler handler;

	private CodeDisplay codeDisplay = CodeDisplay.hidden;
	private OutlineDisplay outlineDisplay = OutlineDisplay.none;

	public MarkerDetector(Experience experience, MarkerDetectionHandler handler)
	{
		int maxValue = 3;
		int minRegionCount = 100;
		int maxRegionCount = 3;
		int checksum = 0;
		for (Action action : experience.getActions())
		{
			for (String code : action.getCodes())
			{
				int total = 0;
				String[] values = code.split(":");
				minRegionCount = Math.min(minRegionCount, values.length);
				maxRegionCount = Math.max(maxRegionCount, values.length);
				for (String value : values)
				{
					try
					{
						int codeValue = Integer.parseInt(value);
						maxValue = Math.max(maxValue, codeValue);
						total += codeValue;
					}
					catch (Exception e)
					{
						Log.w("", e.getMessage(), e);
					}
				}

				if (total > 0)
				{
					checksum = gcd(checksum, total);
				}

				validCodes.add(code);
			}
		}

		this.handler = handler;

		this.maxRegionValue = maxValue;
		this.minRegions = minRegionCount;
		this.maxRegions = maxRegionCount;
		this.checksum = checksum;
		Log.i("", "Regions " + minRegionCount + "-" + maxRegionCount + ", <" + maxValue + ", checksum " + checksum);
	}

	private static int gcd(int a, int b)
	{
		if (b == 0)
		{
			return a;
		}
		return gcd(b, a % b);
	}

	@Override
	public void process(ImageBuffers buffers)
	{
		final ArrayList<MatOfPoint> contours = new ArrayList<>();
		final Mat hierarchy = new Mat();
		// Make sure the image is rotated before the contours are generated, if necessary
		if(outlineDisplay != OutlineDisplay.none || codeDisplay == CodeDisplay.visible)
		{
			buffers.getOverlay();
		}
		try
		{
			final List<String> foundMarkers = new ArrayList<>();
			Imgproc.findContours(buffers.getImage(), contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);
			for (int i = 0; i < contours.size(); i++)
			{
				final Marker marker = createMarkerForNode(i, contours, hierarchy);
				if (marker != null)
				{
					final String markerCode = getCodeKey(marker);
					if (validCodes.contains(markerCode))
					{
						foundMarkers.add(markerCode);

						if(outlineDisplay != OutlineDisplay.none)
						{
							Mat overlay = buffers.getOverlay();
							if (outlineDisplay == OutlineDisplay.regions)
							{
								double[] nodes = hierarchy.get(0, i);
								int currentRegionIndex = (int) nodes[FIRST_NODE];

								while (currentRegionIndex >= 0)
								{
									Imgproc.drawContours(overlay, contours, currentRegionIndex, outlineColour, 4);
									Imgproc.drawContours(overlay, contours, currentRegionIndex, regionColour, 2);

									nodes = hierarchy.get(0, currentRegionIndex);
									currentRegionIndex = (int) nodes[NEXT_NODE];
								}
							}

							Imgproc.drawContours(overlay, contours, i, outlineColour, 7);
							Imgproc.drawContours(overlay, contours, i, detectedColour, 5);
						}

						if(codeDisplay == CodeDisplay.visible)
						{
							Mat overlay = buffers.getOverlay();
							Rect bounds = Imgproc.boundingRect(contours.get(i));
							Core.putText(overlay, markerCode, bounds.tl(), Core.FONT_HERSHEY_SIMPLEX, 1, outlineColour, 5);
							Core.putText(overlay, markerCode, bounds.tl(), Core.FONT_HERSHEY_SIMPLEX, 1, detectedColour, 3);
						}
					}
				}
			}

			buffers.setDetected(!foundMarkers.isEmpty());
			handler.onMarkersDetected(foundMarkers);
		}
		finally
		{
			contours.clear();
			hierarchy.release();
		}
	}

	public String getCodeKey(Marker marker)
	{
		sortCode(marker);
		StringBuilder builder = new StringBuilder(marker.regions.size() * 2);
		for (MarkerRegion region : marker.regions)
		{
			builder.append(region.value);
			builder.append(':');
		}
		builder.deleteCharAt(builder.length() - 1);
		return builder.toString();
	}

	protected boolean isValidDot(int nodeIndex, Mat hierarchy)
	{
		double[] nodes = hierarchy.get(0, nodeIndex);
		return nodes[FIRST_NODE] < 0;
	}

	protected Marker createMarkerForNode(int nodeIndex, List<MatOfPoint> contours, Mat hierarchy)
	{
		List<MarkerRegion> regions = null;
		for (int currentNodeIndex = (int) hierarchy.get(0, nodeIndex)[FIRST_NODE]; currentNodeIndex >= 0; currentNodeIndex = (int) hierarchy.get(0, currentNodeIndex)[NEXT_NODE])
		{
			final MarkerRegion region = createRegionForNode(currentNodeIndex, contours, hierarchy);
			if (region != null)
			{
				if (regions == null)
				{
					regions = new ArrayList<>();
				}
				else if (regions.size() >= maxRegions)
				{
					return null;
				}

				regions.add(region);
			}
		}

		if (isValidRegionList(regions))
		{
			return new Marker(nodeIndex, regions, null);
		}

		return null;
	}

	protected MarkerRegion createRegionForNode(int regionIndex, List<MatOfPoint> contours, Mat hierarchy)
	{
		// Find the first dot index:
		double[] nodes = hierarchy.get(0, regionIndex);
		int currentNodeIndex = (int) nodes[FIRST_NODE];
		if (currentNodeIndex < 0)
		{
			return null; // There are no dots in this region.
		}

		// Count all the dots and check if they are leaf nodes in the hierarchy:
		int dotCount = 0;
		while (currentNodeIndex >= 0)
		{
			if (isValidDot(currentNodeIndex, hierarchy))
			{
				dotCount++;
				// Get next dot node:
				nodes = hierarchy.get(0, currentNodeIndex);
				currentNodeIndex = (int) nodes[NEXT_NODE];

				if (dotCount > maxRegionValue)
				{
					// Too many dots
					return null;
				}
			}
			else
			{
				// Not a dot
				return null;
			}
		}

		return new MarkerRegion(regionIndex, dotCount);
	}

	/**
	 * Override this method to change the sorted order of the code.
	 */
	protected void sortCode(Marker marker)
	{
		Collections.sort(marker.regions, new Comparator<MarkerRegion>()
		{
			@Override
			public int compare(MarkerRegion region1, MarkerRegion region2)
			{
				return region1.value < region2.value ? -1 : (region1.value == region2.value ? 0 : 1);
			}
		});
	}

	/**
	 * Override this method to change validation method.
	 */
	protected boolean isValidRegionList(List<MarkerRegion> regions)
	{
		if (regions == null)
		{
			return false; // No CodeDisplay
		}
		else if (regions.size() < minRegions)
		{
			return false; // Too Short
		}
		else if (regions.size() > maxRegions)
		{
			return false; // Too long
		}

		for (MarkerRegion region : regions)
		{
			//check if leaves are using in accepted range.
			if (region.value > maxRegionValue)
			{
				return false; // value is too Big
			}
		}

		return hasValidChecksum(regions);
	}

	/**
	 * This function divides the total number of leaves in the marker by the
	 * value given in the checksum preference. CodeDisplay is valid if the modulo is 0.
	 *
	 * @return true if the number of leaves are divisible by the checksum value
	 * otherwise false.
	 */
	protected boolean hasValidChecksum(List<MarkerRegion> regions)
	{
		if (checksum <= 1)
		{
			return true;
		}
		int numberOfLeaves = 0;
		for (MarkerRegion region : regions)
		{
			numberOfLeaves += region.value;
		}
		return (numberOfLeaves % checksum) == 0;
	}

	@Override
	public void getSettings(List<ImageProcessorSetting> settings)
	{
		settings.add(new ImageProcessorSetting()
		{
			@Override
			public void nextValue()
			{
				codeDisplay = codeDisplay.next();
			}

			@Override
			public void updateUI(ImageButton button, TextAnimator textAnimator)
			{
				int text = 0;
				switch (codeDisplay)
				{
					case hidden:
						button.setImageResource(R.drawable.ic_image_24dp);
						text = R.string.draw_code_off;
						break;
					case visible:
						button.setImageResource(R.drawable.ic_looks_one_24dp);
						text = R.string.draw_code;
						break;
				}

				if(text != 0 && textAnimator != null)
				{
					textAnimator.setText(text);
				}
			}
		});
		settings.add(new ImageProcessorSetting()
		{
			@Override
			public void nextValue()
			{
				outlineDisplay = outlineDisplay.next();
			}

			@Override
			public void updateUI(ImageButton button, TextAnimator textAnimator)
			{
				int text = 0;
				switch (outlineDisplay)
				{
					case none:
						button.setImageResource(R.drawable.ic_border_clear_24dp);
						text = R.string.draw_marker_off;
						break;
					case marker:
						button.setImageResource(R.drawable.ic_border_outer_24dp);
						text = R.string.draw_marker_outline;
						break;
					case regions:
						button.setImageResource(R.drawable.ic_border_all_24dp);
						text = R.string.draw_marker_regions;
						break;
				}

				if(text != 0 && textAnimator != null)
				{
					textAnimator.setText(text);
				}
			}
		});
	}
}