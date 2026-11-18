package com.vinnyvangogh.groupironman;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Signatures for the group's roles and settings, so a member cannot promote
 * themselves or change the group's settings by sending a document of their own.
 *
 * <p>ECDSA on P-256 from the JDK's own {@code java.security} (SunEC, in every
 * Java 11+ runtime RuneLite ships); no library and no network. Each client
 * makes one key pair on first use and keeps it in its own RuneLite config. The
 * public half travels in the member's announce; the private half never leaves
 * the client and is never logged.
 */
final class GroupSigner
{
	private static final String CURVE = "secp256r1";
	private static final String ALGORITHM = "SHA256withECDSA";
	/** An X.509 P-256 public key is 91 bytes, 124 characters of base64; a little headroom. */
	static final int MAX_KEY_CHARS = 200;
	/** A DER P-256 signature is at most 72 bytes, 96 characters of base64. */
	static final int MAX_SIGNATURE_CHARS = 140;

	private GroupSigner()
	{
	}

	/** This client's key pair. */
	static final class Identity
	{
		private final String publicKey;
		private final PrivateKey privateKey;
		private final String savedPrivate;

		private Identity(String publicKey, PrivateKey privateKey, String savedPrivate)
		{
			this.publicKey = publicKey;
			this.privateKey = privateKey;
			this.savedPrivate = savedPrivate;
		}

		static Identity create()
		{
			try
			{
				KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
				gen.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
				KeyPair pair = gen.generateKeyPair();
				return new Identity(b64(pair.getPublic().getEncoded()), pair.getPrivate(),
					b64(pair.getPrivate().getEncoded()));
			}
			catch (GeneralSecurityException e)
			{
				throw new IllegalStateException("no P-256 in this Java runtime", e);
			}
		}

		/** The identity {@link #save} wrote, or null when {@code saved} is not one. */
		static Identity load(String saved)
		{
			if (saved == null)
			{
				return null;
			}
			int dot = saved.indexOf('.');
			if (dot <= 0)
			{
				return null;
			}
			String pub = saved.substring(0, dot);
			String priv = saved.substring(dot + 1);
			try
			{
				KeyFactory kf = KeyFactory.getInstance("EC");
				PrivateKey key = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priv)));
				kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pub)));
				Identity id = new Identity(pub, key, priv);
				// A pair whose halves do not match would sign documents nobody accepts.
				return verify(pub, "check", id.sign("check")) ? id : null;
			}
			catch (GeneralSecurityException | IllegalArgumentException e)
			{
				return null;
			}
		}

		/** What goes in the config: public and private key, base64, joined by a dot. */
		String save()
		{
			return publicKey + "." + savedPrivate;
		}

		String publicKey()
		{
			return publicKey;
		}

		String sign(String text)
		{
			try
			{
				Signature s = Signature.getInstance(ALGORITHM);
				s.initSign(privateKey);
				s.update(text.getBytes(StandardCharsets.UTF_8));
				return b64(s.sign());
			}
			catch (GeneralSecurityException e)
			{
				throw new IllegalStateException("signing failed", e);
			}
		}

		@Override
		public String toString()
		{
			// Never the private key, even in a debug log.
			return "Identity[" + publicKey.substring(Math.max(0, publicKey.length() - 8)) + "]";
		}
	}

	/** True when {@code signature} is {@code publicKey}'s over {@code text}; false for anything malformed. */
	static boolean verify(String publicKey, String text, String signature)
	{
		if (publicKey == null || signature == null || text == null || publicKey.length() > MAX_KEY_CHARS
			|| signature.length() > MAX_SIGNATURE_CHARS)
		{
			return false;
		}
		try
		{
			PublicKey key = KeyFactory.getInstance("EC")
				.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
			Signature s = Signature.getInstance(ALGORITHM);
			s.initVerify(key);
			s.update(text.getBytes(StandardCharsets.UTF_8));
			return s.verify(Base64.getDecoder().decode(signature));
		}
		catch (GeneralSecurityException | IllegalArgumentException e)
		{
			return false;
		}
	}

	/**
	 * The text a document is signed over: each part length-prefixed, so no
	 * name or value can be shifted from one field into the next.
	 */
	static String canonical(Object... parts)
	{
		StringBuilder out = new StringBuilder();
		for (Object part : parts)
		{
			String s = String.valueOf(part);
			out.append(s.length()).append(':').append(s).append(';');
		}
		return out.toString();
	}

	private static String b64(byte[] bytes)
	{
		return Base64.getEncoder().encodeToString(bytes);
	}
}
