package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertTrue;

/**
 * One simulated client for the governance tests: its config store, its group cache file, what it sent, and its
 * sync. {@link #tick} hands what it sent to the others, as the party would.
 */
class GovernanceClient
{
	static final String GROUP = "GroupShip";

	final String name;
	final Map<String, String> config = new HashMap<>();
	final List<String> outbox = new ArrayList<>();
	/** The webhooks document as the group cache file keeps it. */
	String cache;
	GovernanceSync sync;
	private final Gson gson;

	GovernanceClient(String name, Gson gson)
	{
		this.name = name;
		this.gson = gson;
		start();
	}

	/** Starting the plugin: the config is read at once, the cache file a moment later. */
	void start()
	{
		sync = new GovernanceSync(gson, new GovernanceSync.Store()
		{
			@Override
			public String get(String key)
			{
				return config.get(key);
			}

			@Override
			public void set(String key, String value)
			{
				if (value == null)
				{
					config.remove(key);
				}
				else
				{
					config.put(key, value);
				}
			}
		}, outbox::add, () ->
		{
		}, json -> cache = json);
		sync.group(GROUP);
		if (cache != null)
		{
			sync.restoreWebhooks(cache);
		}
	}

	/** A game tick while connected: whatever is marked goes to {@code others}. */
	void tick(GovernanceClient... others)
	{
		sync.flush(MemberPrefs.NONE);
		for (String payload : outbox)
		{
			assertTrue(GovernanceSync.isGovernance(payload));
			for (GovernanceClient o : others)
			{
				o.sync.receive(name, payload);
			}
		}
		outbox.clear();
	}
}
