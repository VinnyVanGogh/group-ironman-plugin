package com.vinnyvangogh.groupironman;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * A grep over the plugin's source (Board 2026-11-10): an item quantity or gp
 * value glued straight onto text prints raw ("450001 in group storage").
 * Every one goes through {@link Amounts}, so these shapes are banned.
 */
public class NoRawAmountsTest
{
	private static final Path MAIN = Paths.get("src/main/java/com/vinnyvangogh/groupironman");
	/** A quantity, a map entry's count or a value concatenated into a string. */
	private static final Pattern RAW = Pattern.compile(
		"(?:\\bquantity|\\bqty|getValue\\(\\)|\\bvalue|\\bcount|getQuantity\\(\\))\\s*\\+\\s*\""
			+ "|\"\\s*\\+\\s*(?:\\w+\\.)?(?:quantity|qty|getQuantity\\(\\))\\b"
			+ "|BoostText\\.gp\\(");
	/** The string builders the Board named: panel, chat, overlays and Discord. */
	private static final String[] BUILDERS = {"StorageLog.java", "PanelModel.java", "FindStyle.java",
		"GroupSearch.java", "EventText.java", "DiscordWebhook.java", "GroupIronmanPanel.java"};

	@Test
	public void noQuantityIsPrintedRaw() throws IOException
	{
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.list(MAIN))
		{
			for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator)
			{
				List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++)
				{
					Matcher m = RAW.matcher(lines.get(i));
					if (m.find() && !lines.get(i).trim().startsWith("*") && !lines.get(i).trim().startsWith("//"))
					{
						found.add(f.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
					}
				}
			}
		}
		assertTrue("raw amounts, format them with Amounts:\n" + String.join("\n", found), found.isEmpty());
	}

	@Test
	public void everyStringBuilderUsesTheFormatter() throws IOException
	{
		for (String name : BUILDERS)
		{
			String src = new String(Files.readAllBytes(MAIN.resolve(name)), StandardCharsets.UTF_8);
			assertTrue(name + " builds amount text without Amounts", src.contains("Amounts.")
				|| src.contains(".amount("));
		}
	}
}
