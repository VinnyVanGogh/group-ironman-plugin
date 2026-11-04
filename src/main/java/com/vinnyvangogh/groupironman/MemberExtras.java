package com.vinnyvangogh.groupironman;

import java.util.Objects;
import java.util.Map;

/**
 * The item stores that are neither bank, inventory nor equipment: the rune
 * pouch, Dizana's quiver and the seed vault.
 *
 * <p>The rune pouch and quiver are read from varbits the game keeps current,
 * so they are always live. The seed vault is only known after this member has
 * opened it, so it carries its own "last seen" time, -1 when never seen; a
 * receiver keeps the vault it already knows rather than replacing it with an
 * unknown one ({@link GroupRoster#putExtras}).
 */
final class MemberExtras
{
	final Map<Integer, Integer> runePouch;
	final Map<Integer, Integer> quiver;
	final Map<Integer, Integer> seedVault;
	final long seedVaultAt;

	MemberExtras(Map<Integer, Integer> runePouch, Map<Integer, Integer> quiver, Map<Integer, Integer> seedVault,
		long seedVaultAt)
	{
		this.runePouch = ItemCounts.sorted(runePouch);
		this.quiver = ItemCounts.sorted(quiver);
		this.seedVault = ItemCounts.sorted(seedVault);
		this.seedVaultAt = seedVaultAt < 0 ? -1 : seedVaultAt;
	}

	MemberExtras withSeedVault(Map<Integer, Integer> vault, long at)
	{
		return new MemberExtras(runePouch, quiver, vault, at);
	}

	boolean isEmpty()
	{
		return runePouch.isEmpty() && quiver.isEmpty() && seedVault.isEmpty() && seedVaultAt < 0;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof MemberExtras))
		{
			return false;
		}
		MemberExtras m = (MemberExtras) o;
		return runePouch.equals(m.runePouch) && quiver.equals(m.quiver) && seedVault.equals(m.seedVault)
			&& seedVaultAt == m.seedVaultAt;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(runePouch, quiver, seedVault, seedVaultAt);
	}
}
