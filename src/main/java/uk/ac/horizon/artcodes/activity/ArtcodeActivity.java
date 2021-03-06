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
package uk.ac.horizon.artcodes.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.customtabs.CustomTabsIntent;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.gson.Gson;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import uk.ac.horizon.artcodes.Artcodes;
import uk.ac.horizon.artcodes.Feature;
import uk.ac.horizon.artcodes.GoogleAnalytics;
import uk.ac.horizon.artcodes.Hash;
import uk.ac.horizon.artcodes.R;
import uk.ac.horizon.artcodes.detect.ArtcodeDetector;
import uk.ac.horizon.artcodes.detect.handler.ActionDetectionHandler;
import uk.ac.horizon.artcodes.detect.handler.MarkerActionDetectionHandler;
import uk.ac.horizon.artcodes.detect.handler.MultipleMarkerActionDetectionHandler;
import uk.ac.horizon.artcodes.drawer.MarkerThumbnailDrawer;
import uk.ac.horizon.artcodes.model.Action;
import uk.ac.horizon.artcodes.model.Experience;
import uk.ac.horizon.artcodes.model.MarkerImage;
import uk.ac.horizon.artcodes.scanner.ScannerActivity;
import uk.ac.horizon.artcodes.server.ArtcodeServer;
import uk.ac.horizon.artcodes.server.LoadCallback;
import uk.ac.horizon.artcodes.ui.MarkerHistoryViewController;

public class ArtcodeActivity extends ScannerActivity implements LoadCallback<Experience>
{

	public static void start(Context context, Experience experience)
	{
		/*
		final String experienceJSON = new Gson().toJson(experience);
		TaskStackBuilder.create(context)
				.addNextIntent(new Intent(context, NavigationActivity.class))
				.addNextIntent(new Intent(context, ExperienceActivity.class)
						.putExtra("experience", experienceJSON))
				.addNextIntent(new Intent(context, ArtcodeActivity.class)
						.putExtra("experience", experienceJSON))
				.startActivities();
		*/

		StaticActivityMessage.experience = experience;
		TaskStackBuilder.create(context)
				.addNextIntent(new Intent(context, NavigationActivity.class))
				.addNextIntent(new Intent(context, ExperienceActivity.class))
						.addNextIntent(new Intent(context, ArtcodeActivity.class))
								.startActivities();
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		/*
		ViewGroup bottomView = (ViewGroup) findViewById(R.id.bottomView);
		if (bottomView != null)
		{
			actionBinding = ScannerActionBinding.inflate(getLayoutInflater(), bottomView, false);
			bottomView.addView(actionBinding.getRoot());
			actionAnimator = new VisibilityAnimator(actionBinding.getRoot());
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			progressBar.setIndeterminateTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.apptheme_accent)));
		}
		*/

		if (getSupportActionBar() != null)
		{
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home)
		{
			final Experience experience = getExperience();
			NavUtils.navigateUpTo(this, ExperienceActivity.intent(this, experience));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void loaded(final Experience experience)
	{
		super.loaded(experience);

		if (experience != null)
		{
			GoogleAnalytics.trackScreen("Scan", getExperience().getId());
			getServer().loadRecent(new LoadCallback<List<String>>()
			{
				@Override
				public void loaded(List<String> item)
				{
					item.remove(experience.getId());
					item.add(0, experience.getId());
					getServer().saveRecent(item);
				}

				@Override
				public void error(Throwable e)
				{
					GoogleAnalytics.trackException(e);
				}
			});
		}
	}

	@Override
	public void error(Throwable e)
	{
		GoogleAnalytics.trackException(e);
	}

	@Override
	protected void loadExperience(Bundle savedInstanceState)
	{
		if (savedInstanceState != null && savedInstanceState.containsKey("experience"))
		{

			loaded(new Gson().fromJson(savedInstanceState.getString("experience"), Experience.class));
		}
		else
		{
			Intent intent = getIntent();
			if (intent.hasExtra("experience"))
			{
				loaded(new Gson().fromJson(intent.getStringExtra("experience"), Experience.class));
			}
			else
			{
				final Uri data = intent.getData();
				if (data != null)
				{
					getServer().loadExperience(data.toString(), this);
				} else {

					if (StaticActivityMessage.experience != null)
					{
						loaded(StaticActivityMessage.experience);
					}
				}
			}
		}
	}

	private Artcodes getArtcodes()
	{
		return (Artcodes) getApplication();
	}

	private ArtcodeServer getServer()
	{
		return getArtcodes().getServer();
	}

	private void onActionChanged(final Action action)
	{
		Log.i("action", "" + action);
		if (action != null)
		{

			if ((getExperience().getOpenWithoutUserInput() != null && getExperience().getOpenWithoutUserInput()) ||
					Feature.get(getApplicationContext(), R.bool.feature_open_without_touch).isEnabled())
			{
				if (action.getUrl() != null)
				{
					final Experience experience = getExperience();
					GoogleAnalytics.trackEvent("Action", "Opened", experience.getId(), action.getName());
					CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
					// TODO Warmup urls
					//builder.setSession(session);
					builder.setToolbarColor(ContextCompat.getColor(ArtcodeActivity.this, R.color.apptheme_primary));
					CustomTabsIntent customTabsIntent = builder.build();
					customTabsIntent.launchUrl(ArtcodeActivity.this, Uri.parse(processURL(action.getUrl(), action)));
				}
			}
			else
			{
				final Experience experience = getExperience();
				getServer().logScan(experience.getId(), action);
				GoogleAnalytics.trackEvent("Action", "Scanned", experience.getId(), action.getName());

				final Button actionButton = (Button) actionView.findViewById(R.id.scan_event_button);
				if (actionButton != null)
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							actionButton.setText(action.getName() != null && !action.getName().equals("") ? action.getName() : (action.getDisplayUrl() != null && !action.getDisplayUrl().equals("") ? action.getDisplayUrl() : action.getCodes().get(0)));
							actionButton.setOnClickListener(new View.OnClickListener()
							{
								@Override
								public void onClick(View v)
								{
									if (action.getUrl() != null)
									{
										GoogleAnalytics.trackEvent("Action", "Opened", experience.getId(), action.getName());
										CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
										// TODO Warmup urls
										//builder.setSession(session);
										builder.setToolbarColor(ContextCompat.getColor(ArtcodeActivity.this, R.color.apptheme_primary));
										CustomTabsIntent customTabsIntent = builder.build();
										customTabsIntent.launchUrl(ArtcodeActivity.this, Uri.parse(processURL(action.getUrl(), action)));
									}
								}
							});
						}
					});
				}
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						actionAnimator.showView();
						progressBar.setVisibility(View.INVISIBLE);
					}
				});
			}
		}
		else
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					actionAnimator.hideView();
					progressBar.setVisibility(View.VISIBLE);
				}
			});
		}
	}

	@Override
	protected ArtcodeDetector getNewDetector(Experience experience)
	{
		if (Feature.get(getApplicationContext(), R.bool.feature_combined_markers).isEnabled())
		{
			return new ArtcodeDetector(this, experience, new MultipleMarkerActionDetectionHandler(new ActionDetectionHandler()
			{
				private final MarkerHistoryViewController markerHistoryViewController = new MarkerHistoryViewController(ArtcodeActivity.this, (RelativeLayout) findViewById(R.id.thumbnailImageLayout), new Handler(Looper.getMainLooper()));

				@Override
				public void onMarkerActionDetected(Action detectedAction, Action futureAction, List<MarkerImage> detectedMarkers)
				{
					markerHistoryViewController.update(detectedMarkers, futureAction);
					onActionChanged(detectedAction);
				}
			}, experience, new MarkerThumbnailDrawer()), this.cameraView);
		}
		else
		{
			return new ArtcodeDetector(this, experience, new MarkerActionDetectionHandler(new ActionDetectionHandler()
			{
				@Override
				public void onMarkerActionDetected(Action detectedAction, Action possibleFutureAction, List<MarkerImage> imagesForFutureAction)
				{
					onActionChanged(detectedAction);
				}
			}, experience, null), this.cameraView);
		}
	}

	protected String processURL(String url, Action action)
	{
		String result = url;

		if (action != null && action.getCodes() != null && action.getCodes().size() > 0)
		{
			result = result.replace("{code}", action.getCodes().get(0));
		}

		if (result.contains("{timestamp}"))
		{
			result = result.replace("{timestamp}", "" + (System.currentTimeMillis() / 1000));
		}

		if (result.contains("{timehash1}"))
		{
			result = result.replace("{timehash1}", sha256(Hash.salts.get("timehash1") + (((System.currentTimeMillis() / 1000) / 1000) * 1000)));
		}

		return result;
	}

	private String sha256(String string)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(string.getBytes());
			byte[] bytes = md.digest();
			StringBuffer result = new StringBuffer();
			for (byte byt : bytes)
			{
				result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			Log.e("ArtcodeActivity", "Exception getting sha256.", e);
			return "";
		}
	}

}