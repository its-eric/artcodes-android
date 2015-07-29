package uk.ac.horizon.artcodes.adapter;

import android.content.Context;
import android.content.Intent;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import uk.ac.horizon.artcodes.activity.ArtcodeActivity;
import uk.ac.horizon.artcodes.activity.ExperienceActivity;
import uk.ac.horizon.artcodes.databinding.ExperienceItemBinding;
import uk.ac.horizon.artcodes.json.ExperienceParserFactory;
import uk.ac.horizon.artcodes.model.Experience;

public class ExperienceAdapter extends RecyclerView.Adapter<ExperienceAdapter.ExperienceViewHolder>
{
	public class ExperienceViewHolder extends RecyclerView.ViewHolder
	{
		private ExperienceItemBinding binding;

		public ExperienceViewHolder(ExperienceItemBinding binding)
		{
			super(binding.getRoot());
			this.binding = binding;
		}
	}

	private final SortedList<Experience> experiences;
	private final Context context;

	public ExperienceAdapter(Context context)
	{
		super();
		this.context = context;
		experiences = new SortedList<>(Experience.class, new SortedListAdapterCallback<Experience>(this)
		{
			@Override
			public boolean areContentsTheSame(Experience oldItem, Experience newItem)
			{
				return oldItem.getId().equals(newItem.getId());
			}

			@Override
			public boolean areItemsTheSame(Experience item1, Experience item2)
			{
				return item1.getId().equals(item2.getId());
			}

			@Override
			public int compare(Experience o1, Experience o2)
			{
				return o1.getName().compareTo(o2.getName());
			}
		});
	}

	public void add(Experience item)
	{
		experiences.add(item);
	}

	@Override
	public int getItemCount()
	{
		return experiences.size();
	}

	@Override
	public void onBindViewHolder(ExperienceViewHolder holder, int position)
	{
		final Experience experience = experiences.get(position);
		holder.binding.setExperience(experience);
		holder.binding.getRoot().setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				startActivity(ExperienceActivity.class, experience);
			}
		});
		holder.binding.scanButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				startActivity(ArtcodeActivity.class, experience);
			}
		});
	}

	@Override
	public ExperienceViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		return new ExperienceViewHolder(ExperienceItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
	}

	private void startActivity(Class<?> activity, Experience experience)
	{
		Intent intent = new Intent(context, activity);
		intent.putExtra("experience", ExperienceParserFactory.toJson(experience));
		context.startActivity(intent);
	}
}
