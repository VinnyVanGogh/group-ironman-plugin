package com.vinnyvangogh.groupironman;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** T-P8: the plugin derives the same groupId and groupAuth as the server, from the shared fixture file. */
public class ServerKeysTest
{
	@Test
	public void matchesTheServersVectors() throws IOException
	{
		String json = new String(Files.readAllBytes(Paths.get("server/test/fixtures/keys.json")), StandardCharsets.UTF_8);
		JsonObject fixture = new Gson().fromJson(json, JsonObject.class);
		int checked = 0;
		for (JsonElement e : fixture.getAsJsonArray("vectors"))
		{
			JsonObject v = e.getAsJsonObject();
			ServerKeys keys = ServerKeys.of(v.get("key").getAsString());
			assertEquals(v.get("groupId").getAsString(), keys.groupId);
			assertEquals(v.get("groupAuth").getAsString(), keys.groupAuth);
			checked++;
		}
		assertTrue(checked >= 3);
	}

	@Test
	public void refusesAnythingButA32HexKey()
	{
		assertNull(ServerKeys.of(null));
		assertNull(ServerKeys.of(""));
		assertNull(ServerKeys.of("0123456789abcdef"));
		assertNull(ServerKeys.of("0123456789ABCDEF0123456789ABCDEF"));
		assertNull(ServerKeys.of("0123456789abcdef0123456789abcdeg"));
	}

	@Test
	public void theIdAndTheSecretDifferAndNeitherIsTheKey()
	{
		String key = "0123456789abcdef0123456789abcdef";
		ServerKeys keys = ServerKeys.of(key);
		assertNotEquals(keys.groupId, keys.groupAuth);
		assertTrue(!keys.groupId.contains(key) && !keys.groupAuth.contains(key));
		assertEquals(26, keys.groupId.length());
		assertEquals(43, keys.groupAuth.length());
	}
}
