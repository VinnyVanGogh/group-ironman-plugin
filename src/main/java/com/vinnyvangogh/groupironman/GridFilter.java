package com.vinnyvangogh.groupironman;

/**
 * What the Banks or Storage tab is asked to show: the search box, the category pill, and whether the bank
 * preview is lifted ("Show all"). Immutable, so a model built off the Swing thread keeps the filter it was given.
 */
final class GridFilter
{
	static final GridFilter NONE = new GridFilter("", ItemCategory.ALL, false);

	final String query;
	final ItemCategory category;
	final boolean showAll;

	GridFilter(String query, ItemCategory category, boolean showAll)
	{
		this.query = query == null ? "" : query;
		this.category = category == null ? ItemCategory.ALL : category;
		this.showAll = showAll;
	}

	/** A search box only: how the tabs were filtered before there were pills. */
	static GridFilter of(String query)
	{
		return new GridFilter(query, ItemCategory.ALL, false);
	}

	/** Whether the search box holds a real query (long enough to search with). */
	boolean searching()
	{
		return ItemQuery.parse(query) != null;
	}

	/** A bank draws only its top stacks unless the Board's rules lift it: a search or "Show all". */
	boolean previewing()
	{
		return !searching() && !showAll;
	}
}
