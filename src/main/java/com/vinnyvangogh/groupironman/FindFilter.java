package com.vinnyvangogh.groupironman;

/**
 * The Find tab's three dropdowns as a value: a category, a sub-type of it, and who or where to look. The search
 * text is separate. Immutable.
 */
final class FindFilter
{
	static final FindFilter NONE = new FindFilter(FindQuick.Kind.ANYTHING, FindQuick.ANY, null);

	final FindQuick.Kind kind;
	/** A {@link FindQuick#subtypes} entry, or {@link FindQuick#ANY}. */
	final String subtype;
	/** A member's name, {@link FindQuick#GROUP_STORAGE}, or null for anywhere. */
	final String holder;

	FindFilter(FindQuick.Kind kind, String subtype, String holder)
	{
		this.kind = kind == null ? FindQuick.Kind.ANYTHING : kind;
		this.subtype = subtype == null ? FindQuick.ANY : subtype;
		this.holder = holder == null || holder.isEmpty() || FindQuick.ANYWHERE.equals(holder) ? null : holder;
	}

	/** Whether any dropdown narrows the search, which lets Find answer without search text. */
	boolean active()
	{
		return kind != FindQuick.Kind.ANYTHING || holder != null;
	}

	boolean matches(int id, ItemLookup items)
	{
		return FindQuick.matches(kind, subtype, id, items);
	}

	/** Whether {@code source} is where the holder dropdown points: a member's things, or the group storage. */
	boolean keeps(GroupSearch.Source source)
	{
		if (holder == null)
		{
			return true;
		}
		if (FindQuick.GROUP_STORAGE.equals(holder))
		{
			return "storage".equals(source.where) && FindQuick.STORAGE_OWNER.equals(source.member);
		}
		return holder.equalsIgnoreCase(source.member);
	}
}
