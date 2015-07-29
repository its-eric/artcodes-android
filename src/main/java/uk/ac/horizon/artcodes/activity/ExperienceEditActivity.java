/*
 * Artcodes recognises a different marker scheme that allows the
 * creation of aesthetically pleasing, even beautiful, codes.
 * Copyright (C) 2013-2015  The University of Nottingham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.horizon.artcodes.activity;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import uk.ac.horizon.artcodes.GoogleAnalytics;
import uk.ac.horizon.artcodes.R;
import uk.ac.horizon.artcodes.databinding.ExperienceEditBinding;
import uk.ac.horizon.artcodes.fragment.ExperienceEditActionFragment;
import uk.ac.horizon.artcodes.fragment.ExperienceEditAvailabilityFragment;
import uk.ac.horizon.artcodes.fragment.ExperienceEditInfoFragment;
import uk.ac.horizon.artcodes.json.ExperienceParserFactory;
import uk.ac.horizon.artcodes.model.Experience;
import uk.ac.horizon.artcodes.scanner.activity.ExperienceActivityBase;
import uk.ac.horizon.artcodes.storage.ExperienceStorage;

public class ExperienceEditActivity extends ExperienceActivityBase
{
	public static final int IMAGE_PICKER_REQUEST = 121;
	public static final int ICON_PICKER_REQUEST = 123;

	public class ExperienceEditPagerAdapter extends FragmentPagerAdapter
	{
		private final String[] tabTitles;

		public ExperienceEditPagerAdapter(FragmentManager fm, String[] titles)
		{
			super(fm);
			tabTitles = titles;
		}

		@Override
		public int getCount()
		{
			return tabTitles.length;
		}

		@Override
		public Fragment getItem(int position)
		{
			switch (position)
			{
				case 0:
					return new ExperienceEditInfoFragment();
				case 1:
					return new ExperienceEditAvailabilityFragment();
				case 2:
					return new ExperienceEditActionFragment();
			}
			return null;
		}

		@Override
		public CharSequence getPageTitle(int position)
		{
			// Generate title based on item position
			return tabTitles[position];
		}
	}

	private ExperienceEditBinding binding;

	public void editIcon(View view)
	{
		selectImage(ICON_PICKER_REQUEST);
	}

	public void editImage(View view)
	{
		selectImage(IMAGE_PICKER_REQUEST);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.save_menu, menu);
		//updateSave();
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onItemChanged(Experience experience)
	{
		super.onItemChanged(experience);
		if (experience != null)
		{
			GoogleAnalytics.trackEvent("Experience", "Loaded " + experience.getId());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				NavUtils.navigateUpTo(this, createIntent(Experience.class));
				return true;
			case R.id.save:
				ExperienceStorage.save(getExperience()).to(ExperienceStorage.getDefaultStore()).async();
				NavUtils.navigateUpTo(this, createIntent(Experience.class));
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		Log.i("", "Activity result for " + requestCode);
		Log.i("", "Activity result is " + resultCode);
		if (requestCode == IMAGE_PICKER_REQUEST)
		{
			if (resultCode == RESULT_OK)
			{
				Uri fullPhotoUri = data.getData();
				getExperience().setImage(fullPhotoUri.toString());
			}
		}
		else if (requestCode == ICON_PICKER_REQUEST)
		{
			if (resultCode == RESULT_OK)
			{
				Uri fullPhotoUri = data.getData();
				getExperience().setIcon(fullPhotoUri.toString());
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		binding = DataBindingUtil.setContentView(this, R.layout.experience_edit);

		String[] tabs = getResources().getStringArray(R.array.tab_names);

		binding.viewpager.setAdapter(new ExperienceEditPagerAdapter(getSupportFragmentManager(), tabs));
		binding.tabs.setupWithViewPager(binding.viewpager);

		if (savedInstanceState != null)
		{
			binding.viewpager.setCurrentItem(savedInstanceState.getInt("tab", 0));
		}

		setSupportActionBar(binding.toolbar);

		binding.toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
		binding.toolbar.setTitle("");
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);

		outState.putInt("tab", binding.viewpager.getCurrentItem());
		outState.putString("experience", ExperienceParserFactory.toJson(getExperience()));
	}
//
//	private void updateSave()
//	{
//		ExperienceListStore store = ExperienceListStore.with(this, "library");
//		if(getUri() == null)
//		{
//			saveItem.setTitle("Create");
//		}
//		else if(store.contains(getUri()))
//		{
//			saveItem.setTitle(R.string.save);
//		}
//		else
//		{
//			saveItem.setTitle("Create Copy");
//		}
//	}

	@Override
	protected void onStart()
	{
		super.onStart();
		GoogleAnalytics.trackScreen("Experience Edit Screen");
	}

	private void selectImage(int request_id)
	{
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("image/*");
		if (intent.resolveActivity(getPackageManager()) != null)
		{
			startActivityForResult(intent, request_id);
		}
	}
}
