/*
 * Artcodes recognises a different marker scheme that allows the
 * creation of aesthetically pleasing, even beautiful, codes.
 * Copyright (C) 2013-2016  The University of Nottingham
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

package uk.ac.horizon.artcodes;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.common.base.Throwables;

import java.util.HashMap;
import java.util.Map;

public final class GoogleAnalytics
{
	public enum Target
	{
		APP,
		// Add more trackers here if you need, and update the code in #get(Target) below
	}

	private static final String dimensionValue = "experience";
	private static GoogleAnalytics googleAnalytics;
	private final Map<Target, Tracker> trackers = new HashMap<>();
	private final Context context;

	private GoogleAnalytics(Context context)
	{
		this.context = context.getApplicationContext();
	}

	public static synchronized void initialize(Context context)
	{
		if (googleAnalytics != null)
		{
			throw new IllegalStateException("Extra call to initialize analytics trackers");
		}

		googleAnalytics = new GoogleAnalytics(context);
		if (BuildConfig.DEBUG)
		{
			com.google.android.gms.analytics.GoogleAnalytics.getInstance(context).setDryRun(true);
		}
	}

	public static void trackEvent(Map<String, String> analytics)
	{
		get(Target.APP).send(analytics);
	}

	public static void trackEvent(String category, String action)
	{
		get(Target.APP).send(new HitBuilders.EventBuilder(category, action)
				.build());
	}

	public static void trackException(Throwable e)
	{
		Log.e("Google Analytics", e.getMessage(), e);
		get(Target.APP).send(new HitBuilders.ExceptionBuilder()
				.setDescription(Throwables.getStackTraceAsString(e))
				.build());
	}

	public static void trackEvent(String category, String action, String uri)
	{
		get(Target.APP).send(new HitBuilders.EventBuilder(category, action)
				.setCustomDimension(1, uri)
				.build());
	}

	public static void trackEvent(String category, String action, String uri, String actionName)
	{
		get(Target.APP).send(new HitBuilders.EventBuilder(category, action)
				.setCustomDimension(1, uri)
				.setCustomDimension(2, actionName)
				.build());
	}

	public static void trackScreen(String screen)
	{
		final Tracker tracker = get(Target.APP);
		tracker.setScreenName(screen);
		tracker.send(new HitBuilders.ScreenViewBuilder().build());
	}

	public static void trackScreen(String screen, String uri)
	{
		final Tracker tracker = get(Target.APP);
		tracker.setScreenName(screen);
		tracker.send(new HitBuilders.ScreenViewBuilder()
				.setCustomDimension(1, uri).build());
	}

	private static synchronized Tracker get(Target target)
	{
		if (googleAnalytics == null)
		{
			throw new IllegalStateException("Call initialize() before getInstance()");
		}

		return googleAnalytics.getTracker(target);
	}

	private synchronized Tracker getTracker(Target target)
	{
		if (!trackers.containsKey(target))
		{
			Tracker tracker;
			switch (target)
			{
				case APP:
					tracker = com.google.android.gms.analytics.GoogleAnalytics.getInstance(context).newTracker(R.xml.app_tracker);
					break;
				default:
					throw new IllegalArgumentException("Unhandled analytics target " + target);
			}
			trackers.put(target, tracker);
		}

		return trackers.get(target);
	}
}
