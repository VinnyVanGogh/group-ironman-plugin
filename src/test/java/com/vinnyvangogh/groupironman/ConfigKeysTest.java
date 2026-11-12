package com.vinnyvangogh.groupironman;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every setting can be read the way RuneLite reads it, through a
 * {@link Proxy} of the config interface: a setting whose type the proxy
 * cannot see (a package-private enum, combat-vitals PR #18) black-screens the
 * client. And every key is read somewhere: a key nothing reads is a switch
 * that does nothing.
 */
public class ConfigKeysTest
{
	private static final Path SOURCES = Paths.get("src/main/java/com/vinnyvangogh/groupironman");
	/** Keys kept only so they are never reused: hidden and unread (the Activities webhook, 2026-11-12). */
	private static final Set<String> RETIRED = new HashSet<>(Arrays.asList("discordActivitiesUrl",
		"discordNameActivities"));

	@Test
	public void everySettingCanBeReadThroughAConfigProxy() throws Exception
	{
		GroupIronmanConfig proxy = (GroupIronmanConfig) Proxy.newProxyInstance(
			GroupIronmanConfig.class.getClassLoader(), new Class<?>[]{GroupIronmanConfig.class},
			(p, method, args) -> valueFor(method.getReturnType()));
		for (Method m : items())
		{
			Class<?> type = m.getReturnType();
			assertTrue(m.getName() + " returns a type the config proxy cannot see",
				type.isPrimitive() || Modifier.isPublic(type.getModifiers()));
			m.invoke(proxy);
		}
	}

	@Test
	public void everyKeyIsUniqueAndNamedForItsMethod()
	{
		Set<String> keys = new HashSet<>();
		for (Method m : items())
		{
			String key = m.getAnnotation(ConfigItem.class).keyName();
			assertEquals(m.getName(), key);
			assertTrue("duplicate key " + key, keys.add(key));
		}
	}

	@Test
	public void everyKeyIsRead() throws IOException
	{
		String code;
		try (Stream<Path> files = Files.list(SOURCES))
		{
			code = files
				.filter(p -> !p.getFileName().toString().equals("GroupIronmanConfig.java"))
				.map(ConfigKeysTest::read)
				.collect(Collectors.joining("\n"));
		}
		for (Method m : items())
		{
			if (RETIRED.contains(m.getName()))
			{
				assertTrue(m.getName() + " is retired, so hidden", m.getAnnotation(ConfigItem.class).hidden());
				assertFalse(m.getName() + " is retired, so never read", code.contains("config." + m.getName() + "()"));
				continue;
			}
			// EventSettings reads the per-event switches as method references.
			assertTrue(m.getName() + " is never read", code.contains("config." + m.getName() + "()")
				|| code.contains("GroupIronmanConfig::" + m.getName() + ",")
				|| code.contains("GroupIronmanConfig::" + m.getName() + ")"));
		}
	}

	@Test
	public void theGroupKeyIsASecretField()
	{
		ConfigItem item = items().stream().filter(m -> m.getName().equals("groupKey")).findFirst()
			.orElseThrow(AssertionError::new).getAnnotation(ConfigItem.class);
		assertTrue("shown as a password field", item.secret());
		assertEquals(GroupIronmanConfig.KEY_GROUP_KEY, item.keyName());
	}

	private static Object valueFor(Class<?> type)
	{
		if (type == boolean.class)
		{
			return false;
		}
		if (type == int.class)
		{
			return 0;
		}
		if (type == String.class)
		{
			return "";
		}
		return null;
	}

	private static Set<Method> items()
	{
		Set<Method> out = new HashSet<>();
		for (Method m : GroupIronmanConfig.class.getMethods())
		{
			if (m.isAnnotationPresent(ConfigItem.class))
			{
				out.add(m);
			}
		}
		return out;
	}

	private static String read(Path path)
	{
		try
		{
			return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			throw new IllegalStateException(e);
		}
	}
}
