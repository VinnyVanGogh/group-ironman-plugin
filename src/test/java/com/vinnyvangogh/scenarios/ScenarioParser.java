package com.vinnyvangogh.scenarios;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads scenario JSON. Everything a run would trip over is refused here instead: an unknown kind, a missing or
 * mistyped field, an unknown constant. Each step's event is built once while parsing, so a scenario that parses
 * is one whose every step can fire.
 */
public final class ScenarioParser
{
	/** Where scenarios live on the test classpath: {@code src/test/resources/scenarios}. */
	public static final String DEFAULT_DIR = "scenarios";

	private static final Gson GSON = new Gson();

	private ScenarioParser()
	{
	}

	public static Scenario parse(String json, String source, EventKinds kinds)
	{
		JsonObject root;
		try
		{
			root = GSON.fromJson(json, JsonObject.class);
		}
		catch (JsonParseException | ClassCastException e)
		{
			// ClassCastException: valid JSON that is not an object, such as a bare string.
			throw new ScenarioException(source + ": not a JSON object: " + e.getMessage(), e);
		}
		if (root == null)
		{
			throw new ScenarioException(source + ": empty file");
		}
		try
		{
			return read(root, source, kinds);
		}
		catch (ScenarioException e)
		{
			throw new ScenarioException(source + ": " + e.getMessage(), e);
		}
	}

	private static Scenario read(JsonObject root, String source, EventKinds kinds)
	{
		String name = text(root, "name");
		if (name == null || name.trim().isEmpty())
		{
			throw new ScenarioException("a scenario needs a 'name'");
		}
		List<Step> steps = new ArrayList<>();
		for (JsonElement e : array(root, "steps"))
		{
			steps.add(step(e, steps.size() + 1, kinds));
		}
		if (steps.isEmpty())
		{
			throw new ScenarioException("'" + name + "' has no steps");
		}
		List<Scenario.Expect> expect = new ArrayList<>();
		for (JsonElement e : array(root, "expect"))
		{
			expect.add(expectation(e, expect.size() + 1));
		}
		return new Scenario(source, name, text(root, "description"), steps, expect);
	}

	/** The array at {@code key}; empty when absent or not an array. */
	private static JsonArray array(JsonObject o, String key)
	{
		return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
	}

	private static Step step(JsonElement e, int number, EventKinds kinds)
	{
		if (!e.isJsonObject())
		{
			throw new ScenarioException("step " + number + " is not an object");
		}
		JsonObject o = e.getAsJsonObject();
		String kind = text(o, Step.KIND);
		if (kind == null)
		{
			throw new ScenarioException("step " + number + " has no 'kind'");
		}
		JsonObject fields = o.deepCopy();
		fields.remove(Step.KIND);
		fields.remove(Step.WAIT);
		fields.remove(Step.NOTE);
		long wait = o.has(Step.WAIT) ? o.get(Step.WAIT).getAsLong() : 0;
		if (wait < 0)
		{
			throw new ScenarioException("step " + number + ": 'wait' cannot be negative");
		}
		Step step = new Step(number, kind, wait, text(o, Step.NOTE), fields);
		EventFactory factory;
		try
		{
			factory = kinds.factory(kind);
		}
		catch (ScenarioException ex)
		{
			throw new ScenarioException("step " + number + ": " + ex.getMessage(), ex);
		}
		build(step, factory, kinds);
		return step;
	}

	/** Builds the step's event, turning any failure into one that names the step. */
	static Object build(Step step, EventFactory factory, EventKinds kinds)
	{
		try
		{
			return factory.create(new Fields(step, kinds));
		}
		catch (ScenarioException e)
		{
			throw e;
		}
		catch (RuntimeException e)
		{
			throw new ScenarioException(step.label() + ": " + e, e);
		}
	}

	private static Scenario.Expect expectation(JsonElement e, int number)
	{
		if (!e.isJsonObject())
		{
			throw new ScenarioException("expect " + number + " is not an object");
		}
		JsonObject o = e.getAsJsonObject();
		String probe = text(o, "probe");
		String contains = text(o, "contains");
		String absent = text(o, "absent");
		Integer count = o.has("count") ? o.get("count").getAsInt() : null;
		int checks = (contains == null ? 0 : 1) + (absent == null ? 0 : 1) + (count == null ? 0 : 1);
		if (probe == null || checks != 1)
		{
			throw new ScenarioException("expect " + number + " needs a 'probe' and exactly one of contains, absent "
				+ "or count");
		}
		return new Scenario.Expect(number, probe, contains, absent, count);
	}

	private static String text(JsonObject o, String key)
	{
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
	}

	/** Every {@code *.json} in {@code dir} on the test classpath, in file name order. */
	public static List<Scenario> loadAll(String dir, EventKinds kinds)
	{
		ClassLoader loader = ScenarioParser.class.getClassLoader();
		URL url = loader.getResource(dir);
		if (url == null)
		{
			throw new ScenarioException("no scenario directory '" + dir + "' on the test classpath (src/test/resources/"
				+ dir + ")");
		}
		List<Scenario> out = new ArrayList<>();
		for (String file : list(url, dir))
		{
			out.add(parse(read(loader, dir + "/" + file), file, kinds));
		}
		return out;
	}

	private static List<String> list(URL url, String dir)
	{
		try
		{
			List<String> names = "jar".equals(url.getProtocol()) ? inJar(url, dir) : inDirectory(Paths.get(url.toURI()));
			Collections.sort(names);
			return names;
		}
		catch (IOException | URISyntaxException e)
		{
			throw new ScenarioException("cannot list scenarios in " + url + ": " + e, e);
		}
	}

	private static List<String> inDirectory(Path dir) throws IOException
	{
		try (Stream<Path> files = Files.list(dir))
		{
			return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
				.collect(Collectors.toList());
		}
	}

	/** The shadow jar ({@code ./gradlew shadowJar}) keeps scenarios inside it. */
	private static List<String> inJar(URL url, String dir) throws IOException
	{
		List<String> names = new ArrayList<>();
		String prefix = dir + "/";
		try (JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile())
		{
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements())
			{
				String name = entries.nextElement().getName();
				if (name.startsWith(prefix) && name.endsWith(".json") && name.indexOf('/', prefix.length()) < 0)
				{
					names.add(name.substring(prefix.length()));
				}
			}
		}
		return names;
	}

	private static String read(ClassLoader loader, String resource)
	{
		try (InputStream in = loader.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new ScenarioException("cannot read " + resource);
			}
			StringBuilder sb = new StringBuilder();
			char[] buf = new char[4096];
			try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				for (int n = r.read(buf); n > 0; n = r.read(buf))
				{
					sb.append(buf, 0, n);
				}
			}
			return sb.toString();
		}
		catch (IOException e)
		{
			throw new ScenarioException("cannot read " + resource + ": " + e, e);
		}
	}
}
