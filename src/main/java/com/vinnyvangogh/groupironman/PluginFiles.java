package com.vinnyvangogh.groupironman;

import com.google.common.base.Charsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.runelite.client.util.Filepath;

/**
 * The plugin's own files in its data directory (.runelite/plugin-data/gim-utilities), written the moment they
 * change. Unlike the RuneLite config, which keeps a change in memory and writes it every five minutes or at a
 * clean shutdown, a file written here survives the client being killed a second later.
 */
final class PluginFiles
{
	private PluginFiles()
	{
	}

	/** {@code file}'s text, or null when there is no such file. */
	static String read(Filepath file) throws IOException
	{
		if (!file.isFile())
		{
			return null;
		}
		try (InputStream in = file.openInputStream())
		{
			return new String(in.readAllBytes(), Charsets.UTF_8);
		}
	}

	/** Writes a temp file beside {@code file}, then moves it over, so a crash never leaves half a file. */
	static void write(Filepath file, String text, String tempPrefix) throws IOException
	{
		Filepath dir = file.getParent();
		dir.createDirectories();
		Filepath tmp = dir.createTempFile(tempPrefix, ".tmp");
		try
		{
			try (OutputStream out = tmp.openOutputStream())
			{
				out.write(text.getBytes(Charsets.UTF_8));
			}
			file.deleteIfExists();
			tmp.moveTo(file);
		}
		finally
		{
			tmp.deleteIfExists();
		}
	}
}
