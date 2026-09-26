package com.vinnyvangogh.buildguard;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every experience lamp this plugin can price, keyed by the game's own item id.
 *
 * <p>Bundled rather than fetched, and there is nothing to fetch from: Build
 * Guard makes no requests at all. A lamp's value does not change between
 * releases, so the bundle is the whole source.
 *
 * <p>It travels in a file of its own because the bundled quest graph carries
 * no lamps at all. {@code internal/export/questgraph.go} strips them
 * deliberately: "Rewards carries only unavoidable experience — the rewards the
 * build guard can actually block on. Lamps and optional rewards are choices,
 * and a choice cannot break a cap unless you make it." That is right for the
 * build guard and leaves the advisor with nothing, so the two are separate
 * exports.
 *
 * <p>A lamp absent from this file is not an error and not an empty result: it
 * is {@link Lamp#unknown(int, String)}, which still greys every NO_XP skill
 * and ranks nothing. The catalogue is a list of what this plugin can price,
 * never a list of what exists.
 */
class LampCatalogue
{
	/**
	 * The bundle version this build understands.
	 *
	 * <p>Checked rather than tolerated, for the reason {@link QuestGraph}
	 * checks its own: a document missing a field has to be refused outright
	 * rather than parsed into a confident wrong answer, and a lamp amount is
	 * exactly the kind of number that is worse wrong than absent.
	 */
	static final int SUPPORTED_VERSION = 1;

	@SerializedName("version")
	private int version;

	@SerializedName("lamps")
	private List<Lamp> lamps;

	private transient Map<Integer, Lamp> byItemId;

	int version()
	{
		return version;
	}

	List<Lamp> lamps()
	{
		return lamps == null ? Collections.emptyList() : lamps;
	}

	/**
	 * The lamp for an item id, or {@link Lamp#unknown} when the catalogue
	 * does not carry one.
	 *
	 * <p>Never null. A null here would become "skip the advisor" at the call
	 * site within one refactor, and skipping it is precisely wrong: the NO_XP
	 * greying does not depend on knowing the amount and must still happen.
	 */
	Lamp forItem(int itemId, String itemName)
	{
		Lamp known = index().get(itemId);
		return known != null ? known : Lamp.unknown(itemId, itemName);
	}

	/** Whether the catalogue carries a priced lamp for this item. */
	boolean knows(int itemId)
	{
		return index().containsKey(itemId);
	}

	private Map<Integer, Lamp> index()
	{
		if (byItemId == null)
		{
			Map<Integer, Lamp> built = new HashMap<>();
			for (Lamp lamp : lamps())
			{
				built.putIfAbsent(lamp.itemId(), lamp);
			}
			byItemId = built;
		}
		return byItemId;
	}

	/**
	 * Loads the bundled catalogue.
	 *
	 * <p>{@code getResourceAsStream}, never {@code getResource}: resources
	 * ship inside a jar and a URL to one is not a file path.
	 */
	static LampCatalogue load(Gson gson) throws IOException
	{
		try (InputStream stream = LampCatalogue.class.getResourceAsStream("lamps.json"))
		{
			if (stream == null)
			{
				throw new IOException("bundled lamps.json is missing from the jar");
			}
			try
			{
				return parse(gson, new InputStreamReader(stream, StandardCharsets.UTF_8));
			}
			catch (IOException e)
			{
				throw new IOException("bundled lamps.json: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Parses and validates a catalogue already in hand.
	 *
	 * @throws IOException when the document does not parse, carries a version
	 *     this build does not understand, or carries no lamps
	 */
	static LampCatalogue parse(Gson gson, Reader reader) throws IOException
	{
		LampCatalogue catalogue;
		try
		{
			catalogue = gson.fromJson(reader, LampCatalogue.class);
		}
		catch (JsonSyntaxException e)
		{
			throw new IOException("did not parse as a lamp catalogue: " + e.getMessage(), e);
		}
		if (catalogue == null)
		{
			throw new IOException("did not parse as a lamp catalogue");
		}
		if (catalogue.version() != SUPPORTED_VERSION)
		{
			throw new IOException(catalogue.version() > SUPPORTED_VERSION
				? "is version " + catalogue.version() + ", which this build (" + SUPPORTED_VERSION
					+ ") does not understand yet -- update the plugin"
				: "is version " + catalogue.version() + ", which is older than this build ("
					+ SUPPORTED_VERSION + ") expects -- the bundle needs re-exporting");
		}
		if (catalogue.lamps().isEmpty())
		{
			// An empty catalogue would make every lamp unknown, which renders
			// as "this lamp is not modelled" on all of them rather than as
			// "the bundle is broken" -- a lie that looks like caution.
			throw new IOException("carries no lamps");
		}
		return catalogue;
	}

	/**
	 * An empty catalogue, for the one case where having none is correct: the
	 * bundle failed to load and the plugin carries on with every lamp
	 * unknown rather than with the advisor crashing.
	 */
	static LampCatalogue empty()
	{
		LampCatalogue catalogue = new LampCatalogue();
		catalogue.version = SUPPORTED_VERSION;
		catalogue.lamps = Collections.emptyList();
		return catalogue;
	}
}
