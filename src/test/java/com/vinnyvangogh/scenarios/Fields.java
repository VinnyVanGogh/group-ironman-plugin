package com.vinnyvangogh.scenarios;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A step's fields, read with types. Every getter refuses a missing or wrong value with a message naming the step,
 * so a typo in a scenario fails at parse time with "step 3 (stat): missing field 'level'".
 *
 * <p>Wherever a number is read, a name registered with {@link EventKinds#constant} works too
 * ({@code "group": "SHARED_BANK"}), so scenarios read as words rather than interface ids.
 */
public final class Fields
{
	private final Step step;
	private final EventKinds kinds;

	Fields(Step step, EventKinds kinds)
	{
		this.step = step;
		this.kinds = kinds;
	}

	public boolean has(String key)
	{
		return step.fields.has(key) && !step.fields.get(key).isJsonNull();
	}

	public String string(String key)
	{
		JsonElement e = need(key);
		if (!e.isJsonPrimitive())
		{
			throw wrong(key, "a string");
		}
		return e.getAsString();
	}

	public String string(String key, String fallback)
	{
		return has(key) ? string(key) : fallback;
	}

	public int integer(String key)
	{
		return Math.toIntExact(number(need(key), key));
	}

	public int integer(String key, int fallback)
	{
		return has(key) ? integer(key) : fallback;
	}

	public long longValue(String key, long fallback)
	{
		return has(key) ? number(need(key), key) : fallback;
	}

	public boolean bool(String key, boolean fallback)
	{
		if (!has(key))
		{
			return fallback;
		}
		JsonElement e = need(key);
		if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isBoolean())
		{
			throw wrong(key, "true or false");
		}
		return e.getAsBoolean();
	}

	/** An enum constant by name, case-sensitive as RuneLite spells them ({@code PRAYER}, {@code GAMEMESSAGE}). */
	public <E extends Enum<E>> E enumValue(String key, Class<E> type)
	{
		String name = string(key);
		for (E e : type.getEnumConstants())
		{
			if (e.name().equals(name))
			{
				return e;
			}
		}
		throw fail("field '" + key + "' is '" + name + "', not a " + type.getSimpleName() + "; one of "
			+ Arrays.toString(type.getEnumConstants()));
	}

	/** {@code {"SHARK": 3, "995": 100}}: item (id or constant) to quantity, in file order. */
	public Map<Integer, Integer> items(String key)
	{
		JsonElement e = need(key);
		if (!e.isJsonObject())
		{
			throw wrong(key, "an object of item to quantity");
		}
		Map<Integer, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : ((JsonObject) e).entrySet())
		{
			int id = constantOrNumber(entry.getKey(), key);
			out.put(id, Math.toIntExact(number(entry.getValue(), key + "." + entry.getKey())));
		}
		return out;
	}

	/** {@code {"GroupOtt": 615}}: names to numbers (or constants), in file order. */
	public Map<String, Integer> named(String key)
	{
		JsonElement e = need(key);
		if (!e.isJsonObject())
		{
			throw wrong(key, "an object of name to number");
		}
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : ((JsonObject) e).entrySet())
		{
			out.put(entry.getKey(), Math.toIntExact(number(entry.getValue(), key + "." + entry.getKey())));
		}
		return out;
	}

	/** {@code {"eventLevelMin": 70}}: keys to longs, in file order. */
	public Map<String, Long> longs(String key)
	{
		JsonElement e = need(key);
		if (!e.isJsonObject())
		{
			throw wrong(key, "an object of key to number");
		}
		Map<String, Long> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : ((JsonObject) e).entrySet())
		{
			out.put(entry.getKey(), number(entry.getValue(), key + "." + entry.getKey()));
		}
		return out;
	}

	/** {@code ["Alex", "Sam"]}; empty when the field is absent. */
	public List<String> strings(String key)
	{
		List<String> out = new ArrayList<>();
		if (!has(key))
		{
			return out;
		}
		JsonElement e = need(key);
		if (!e.isJsonArray())
		{
			throw wrong(key, "a list of strings");
		}
		for (JsonElement item : e.getAsJsonArray())
		{
			out.add(item.getAsString());
		}
		return out;
	}

	/** A refusal about this step, for a factory's own checks. */
	public ScenarioException fail(String why)
	{
		return new ScenarioException(step.label() + ": " + why);
	}

	private JsonElement need(String key)
	{
		if (!has(key))
		{
			throw fail("missing field '" + key + "'");
		}
		return step.fields.get(key);
	}

	private long number(JsonElement e, String key)
	{
		if (!e.isJsonPrimitive())
		{
			throw wrong(key, "a number or a constant name");
		}
		JsonPrimitive p = (JsonPrimitive) e;
		if (p.isNumber())
		{
			return p.getAsLong();
		}
		return constantOrNumber(p.getAsString(), key);
	}

	private int constantOrNumber(String text, String key)
	{
		Integer constant = kinds.constantValue(text);
		if (constant != null)
		{
			return constant;
		}
		try
		{
			return Integer.parseInt(text.trim());
		}
		catch (NumberFormatException ex)
		{
			throw fail("field '" + key + "': unknown constant '" + text + "'; known: " + kinds.constantNames());
		}
	}

	private ScenarioException wrong(String key, String expected)
	{
		return fail("field '" + key + "' must be " + expected + ", not " + step.fields.get(key));
	}
}
