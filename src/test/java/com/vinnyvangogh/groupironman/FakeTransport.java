package com.vinnyvangogh.groupironman;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** The real {@link DiscordSender} over a transport that keeps every request and answers only when told to. */
final class FakeTransport
{
	/** Every request the sender made, in order. */
	final List<DiscordQueue.Post> sent = new ArrayList<>();
	/** The replies still owed, oldest first. */
	final List<DiscordSender.Done> pending = new ArrayList<>();
	final DiscordSender sender;

	FakeTransport(LongSupplier clock)
	{
		sender = new DiscordSender((post, done) ->
		{
			sent.add(post);
			pending.add(done);
		}, clock, () -> 12);
	}

	/** Answers the oldest request still owed a reply. */
	void reply(int status, String messageId)
	{
		pending.remove(0).done(status, null, messageId);
	}
}
