package com.vinnyvangogh.groupironman;

import java.util.*;

/**
 * One member's quest progress, as far as the Shopping tab cares: which of the
 * bundled quests they have finished and which they have started. Read from
 * the game for this player; members send theirs as {@code qstate}
 * ({@link ShoppingCodec}).
 *
 * <p>{@link #UNKNOWN} is a member whose client never said: every quest counts
 * as not started, and the tab says so rather than pretending it knows.
 */
final class QuestProgress
{
	static final QuestProgress UNKNOWN = new QuestProgress(false, Collections.emptySet(), Collections.emptySet());

	private final boolean known;
	private final Set<String> finished;
	private final Set<String> started;

	private QuestProgress(boolean known, Collection<String> finished, Collection<String> started)
	{
		this.known = known;
		this.finished = Collections.unmodifiableSet(new TreeSet<>(finished));
		Set<String> s = new TreeSet<>(started);
		s.removeAll(this.finished);
		this.started = Collections.unmodifiableSet(s);
	}

	static QuestProgress of(Collection<String> finished, Collection<String> started)
	{
		return new QuestProgress(true, finished, started);
	}

	boolean known()
	{
		return known;
	}

	boolean finished(String quest)
	{
		return finished.contains(quest);
	}

	/** Started or finished. */
	boolean started(String quest)
	{
		return finished.contains(quest) || started.contains(quest);
	}

	Set<String> finished()
	{
		return finished;
	}

	/** Started and not finished. */
	Set<String> inProgress()
	{
		return started;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof QuestProgress))
		{
			return false;
		}
		QuestProgress p = (QuestProgress) o;
		return known == p.known && finished.equals(p.finished) && started.equals(p.started);
	}

	@Override
	public int hashCode()
	{
		return finished.hashCode() * 31 + started.hashCode() + (known ? 1 : 0);
	}
}
