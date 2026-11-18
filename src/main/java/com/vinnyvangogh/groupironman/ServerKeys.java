package com.vinnyvangogh.groupironman;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * What the sync server knows a group by, derived from its {@link GroupKey} so the key itself never leaves this
 * client (docs/plans/gim-server.md §4.2). HKDF-SHA256 (RFC 5869) with no salt:
 * <ul>
 * <li>{@code groupId}: 16 bytes, info {@code gimu/v1/id}, lower-case unpadded base32. The Durable Object's name.</li>
 * <li>{@code groupAuth}: 32 bytes, info {@code gimu/v1/auth}, unpadded base64url. The bearer secret.</li>
 * </ul>
 * The server's {@code src/keys.ts} derives the same; {@code server/test/fixtures/keys.json} holds vectors both
 * sides' tests reproduce.
 */
final class ServerKeys
{
	static final String ID_INFO = "gimu/v1/id";
	static final String AUTH_INFO = "gimu/v1/auth";
	private static final String BASE32 = "abcdefghijklmnopqrstuvwxyz234567";

	final String groupId;
	final String groupAuth;

	private ServerKeys(String groupId, String groupAuth)
	{
		this.groupId = groupId;
		this.groupAuth = groupAuth;
	}

	/** The keys for a 32-hex group key, or null when {@code key} is not one. */
	static ServerKeys of(String key)
	{
		if (key == null || !key.matches("[0-9a-f]{32}"))
		{
			return null;
		}
		byte[] ikm = new byte[16];
		for (int i = 0; i < 16; i++)
		{
			ikm[i] = (byte) Integer.parseInt(key.substring(i * 2, i * 2 + 2), 16);
		}
		try
		{
			return new ServerKeys(base32(hkdf(ikm, ID_INFO, 16)),
				Base64.getUrlEncoder().withoutPadding().encodeToString(hkdf(ikm, AUTH_INFO, 32)));
		}
		catch (GeneralSecurityException e)
		{
			// HmacSHA256 is in every JRE (a required algorithm); unreachable in practice.
			return null;
		}
	}

	/** RFC 5869 extract-then-expand, SHA-256, empty salt. */
	static byte[] hkdf(byte[] ikm, String info, int length) throws GeneralSecurityException
	{
		Mac mac = Mac.getInstance("HmacSHA256");
		// An empty salt is HashLen zero bytes (RFC 5869 §2.2).
		mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
		byte[] prk = mac.doFinal(ikm);
		mac.init(new SecretKeySpec(prk, "HmacSHA256"));
		byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
		byte[] out = new byte[length];
		byte[] t = new byte[0];
		int done = 0;
		for (int counter = 1; done < length; counter++)
		{
			mac.update(t);
			mac.update(infoBytes);
			mac.update((byte) counter);
			t = mac.doFinal();
			int n = Math.min(t.length, length - done);
			System.arraycopy(t, 0, out, done, n);
			done += n;
		}
		return out;
	}

	static String base32(byte[] bytes)
	{
		StringBuilder out = new StringBuilder();
		int buffer = 0;
		int bits = 0;
		for (byte b : bytes)
		{
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5)
			{
				out.append(BASE32.charAt((buffer >>> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		if (bits > 0)
		{
			out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
		}
		return out.toString();
	}
}
