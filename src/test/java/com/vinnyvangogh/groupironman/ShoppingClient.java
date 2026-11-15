package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertTrue;

/** One simulated client for the Shopping tab's sync: its book, and what it sent. */
class ShoppingClient
{
	final String name;
	final List<String> sent = new ArrayList<>();
	final ShoppingSync sync;

	ShoppingClient(Gson gson, String name)
	{
		this.name = name;
		this.sync = new ShoppingSync(gson, sent::add, () ->
		{
		});
	}

	/** A game tick while connected: whatever it sends reaches {@code to}. */
	void tick(ShoppingClient... to)
	{
		sync.flush(name);
		for (String payload : sent)
		{
			for (ShoppingClient c : to)
			{
				assertTrue(c.sync.receive(name, payload));
			}
		}
		sent.clear();
	}
}
