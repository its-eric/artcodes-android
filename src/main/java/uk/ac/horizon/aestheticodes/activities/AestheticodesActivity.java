/*
 * Aestheticodes recognises a different marker scheme that allows the
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
package uk.ac.horizon.aestheticodes.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import uk.ac.horizon.aestheticodes.Aestheticodes;
import uk.ac.horizon.aestheticodes.AnalyticsTrackers;
import uk.ac.horizon.aestheticodes.R;
import uk.ac.horizon.aestheticodes.controllers.ExperienceListAdapter;
import uk.ac.horizon.aestheticodes.controllers.ExperienceListController;
import uk.ac.horizon.aestheticodes.core.activities.ScanActivity;
import uk.ac.horizon.aestheticodes.model.Experience;
import uk.ac.horizon.aestheticodes.model.Marker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AestheticodesActivity extends ScanActivity implements ExperienceListController.Listener
{
	private ExperienceListAdapter experiences;
	private Button markerButton;
	private boolean autoOpen = false;
	private String currentCode = null;
	private String experienceURL = null;

	@Override
	public void experienceListChanged()
	{
		if (experienceURL != null)
		{
			for (Experience experience : experiences.getExperiences())
			{
				if (experienceURL.equals(experience.getOrigin()))
				{
					this.experience.set(experience);
					return;
				}
			}
		}
		String selectedID = getPreferences(Context.MODE_PRIVATE).getString("experience", experience.get().getId());
		Experience newSelected = experiences.getSelected(selectedID);
		if (newSelected != null && newSelected != experience.get())
		{
			experience.set(newSelected);
		}
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		Tracker tracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
		tracker.setScreenName("Scan Screen");
		tracker.send(new HitBuilders.ScreenViewBuilder().build());
	}

	@Override
	public void experienceSelected(Experience experience)
	{
		super.experienceSelected(experience);

		Log.i("", "Selected Experience changed to " + experience.getId());
		Tracker tracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
		tracker.send(new HitBuilders.EventBuilder("Experience", "Selected " + experience.getId())
				.build());

		List<Experience> experienceList = experiences.getExperiences();
		int index = experienceList.indexOf(experience);
		getSupportActionBar().setSelectedNavigationItem(index);
	}


	@Override
	protected void onNewIntent(Intent intent)
	{
		if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW))
		{
			Uri data = intent.getData();
			if (data != null)
			{
				Log.i("", "Intent URL = " + data);
				this.experienceURL = data.toString();
			}
		}
		else
		{
			super.onNewIntent(intent);
		}
	}


	@Override
	public void markerChanged(final String markerCode)
	{
		Log.i("", "Marker changing from " + currentCode + " to " + markerCode);
		final String oldCode = currentCode;
		currentCode = markerCode;

		if (oldCode != currentCode || (oldCode != null && !oldCode.equals(currentCode)))
		{
			Tracker tracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
			tracker.send(new HitBuilders.EventBuilder("Marker", "Detected " + markerCode)
					.setLabel(experience.get().getName() + "(" + experience.get().getId() + ")")
					.build());
			final Marker marker = experience.get().getMarkers().get(markerCode);
			if (marker != null)
			{
				final byte[] data = camera.getData();
				Log.i("", "Data = " + data);
				if (data != null)
				{
					runOnUiThread(new Runnable() {
						public boolean isExternalStorageWritable() {
							String state = Environment.getExternalStorageState();
							if (Environment.MEDIA_MOUNTED.equals(state)) {
								return true;
							}
							return false;
						}

						@Override
						public void run()
						{
							try
							{
								final String title = "IMG_" + System.currentTimeMillis();
								final File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
								final File directory = new File(picturesDir, "Artcodes");
								if (!directory.exists())
								{
									boolean success = directory.mkdir();
								}
								final File file = new File(directory, title + ".jpg");
								file.createNewFile();
								OutputStream out = new FileOutputStream(file);

								final Camera.Size size = camera.getSize();
								final YuvImage yuvimage = new YuvImage(data, camera.getPreviewFormat(), size.width, size.height, null);
								yuvimage.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, out);
								out.close();
							}
							catch (Exception e)
							{
								Log.w("", e.getMessage(), e);
							}
						}
					});
				}

				if (autoOpen)
				{
					openMarker(marker);
				}
				else
				{
					runOnUiThread(new Runnable()
					{
						@Override
						public void run()
						{
							if (marker.getTitle() != null && !marker.getTitle().isEmpty())
							{
								markerButton.setText(getString(R.string.marker_open, marker.getTitle()));
							}
							else
							{
								markerButton.setText(getString(R.string.marker_open_code, marker.getCode()));
							}
							markerButton.setOnClickListener(new View.OnClickListener()
							{
								@Override
								public void onClick(View v)
								{
									openMarker(marker);
								}
							});

							if (markerButton.getVisibility() == View.INVISIBLE || markerButton.getAnimation() != null)
							{
								Log.i("", "Slide in");
								markerButton.setVisibility(View.VISIBLE);
								markerButton.startAnimation(AnimationUtils.loadAnimation(AestheticodesActivity.this, R.anim.slide_in));
							}
						}
					});
				}
			}
			else if (markerButton.getVisibility() == View.VISIBLE)
			{
				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						Animation animation = AnimationUtils.loadAnimation(AestheticodesActivity.this, R.anim.slide_out);
						animation.setAnimationListener(new Animation.AnimationListener()
						{
							@Override
							public void onAnimationStart(Animation animation)
							{

							}

							@Override
							public void onAnimationEnd(Animation animation)
							{
								markerButton.setVisibility(View.INVISIBLE);
							}

							@Override
							public void onAnimationRepeat(Animation animation)
							{

							}
						});
						markerButton.startAnimation(animation);
					}
				});
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		View view = getLayoutInflater().inflate(R.layout.marker_button, bottomView);
		markerButton = (Button) view.findViewById(R.id.markerButton);

		experiences = new ExperienceListAdapter(getSupportActionBar().getThemedContext(), Aestheticodes.getExperiences());
		//noinspection deprecation
		getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		//noinspection deprecation
		getSupportActionBar().setListNavigationCallbacks(experiences, new ActionBar.OnNavigationListener()
		{
			@Override
			public boolean onNavigationItemSelected(int position, long l)
			{
				final Experience selected = (Experience) experiences.getItem(position);
				Log.i("", "User selected " + selected.getId() + ": " + l);
				if (selected != experience.get())
				{
					experience.set(selected);
				}
				return true;
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.capture_actions, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.experiences:
				startActivity(new Intent(this, ExperienceListActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		experiences.addListener(this);
		experiences.update(experienceURL);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		experiences.removeListener(this);
		if (experience.get() != null)
		{
			getPreferences(Context.MODE_PRIVATE).edit().putString("experience", experience.get().getId()).commit();
		}
	}

	@Override
	protected void updateMenu()
	{
		super.updateMenu();

		Button autoOpenButton = (Button) findViewById(uk.ac.horizon.aestheticodes.core.R.id.autoOpenButton);
		autoOpenButton.setVisibility(View.VISIBLE);
		autoOpenButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				autoOpen = !autoOpen;
				updateMenu();
				if (currentCode != null)
				{
					String code = currentCode;
					currentCode = null;
					markerChanged(code);
				}
			}
		});
		if (autoOpen)
		{
			autoOpenButton.setText(getString(R.string.auto_open));
			autoOpenButton.setCompoundDrawablesWithIntrinsicBounds(uk.ac.horizon.aestheticodes.core.R.drawable.ic_open_in_new_white_24dp, 0, 0, 0);
		}
		else
		{
			autoOpenButton.setText(getString(R.string.auto_open_off));
			autoOpenButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_popup_white_24dp, 0, 0, 0);
		}
	}

	private void openMarker(Marker marker)
	{
		Tracker tracker = AnalyticsTrackers.getInstance().get(AnalyticsTrackers.Target.APP);
		tracker.send(new HitBuilders.EventBuilder("Marker", "Opened " + marker.getCode())
				.setLabel(experience.get().getName() + "(" + experience.get().getId() + ")")
				.build());
		if (marker.getShowDetail())
		{
			camera.stop();
			Intent intent = new Intent(this, MarkerActivity.class);
			intent.putExtra("experience", experience.get().getId());
			intent.putExtra("marker", marker.getCode());

			startActivity(intent);
		}
		else if (marker.getAction() != null && marker.getAction().contains("://"))
		{
			camera.stop();
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(marker.getAction())));
		}
		else
		{
			new AlertDialog.Builder(this)
					.setTitle("No action")
					.setMessage("There is no valid action associated with this marker.")
					.setPositiveButton("OK", null)
					.show();
		}
	}
}
