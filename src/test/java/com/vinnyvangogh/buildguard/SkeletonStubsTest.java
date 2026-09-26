package com.vinnyvangogh.buildguard;

import com.vinnyvangogh.buildguard.lamp.LampAdvice;
import com.vinnyvangogh.buildguard.rules.BuildRules;
import com.vinnyvangogh.buildguard.verdict.QuestVerdictEngine;
import com.vinnyvangogh.buildguard.warning.QuestStartWarning;
import org.junit.Test;
import static org.junit.Assert.fail;

/**
 * Locks the one property the skeleton has: every stub refuses, rather than
 * returning something that reads as an answer.
 *
 * <p>This matters more here than in most scaffolds. A wrong verdict is acted
 * on by spending experience, and experience does not come back. An empty
 * result or a default "eligible" would be indistinguishable from a real
 * answer.
 *
 * <p>Each assertion is expected to be deleted as its piece is ported.
 */
public class SkeletonStubsTest
{
	@Test
	public void buildRulesRefuse()
	{
		assertRefuses(() -> BuildRules.headroom("attack"), "build rules");
	}

	@Test
	public void verdictEngineRefuses()
	{
		assertRefuses(() -> QuestVerdictEngine.verdictFor(1L), "verdict engine");
	}

	@Test
	public void questStartWarningRefuses()
	{
		assertRefuses(() -> QuestStartWarning.warningFor(1L), "quest start warning");
	}

	@Test
	public void lampAdvisorRefuses()
	{
		assertRefuses(() -> LampAdvice.adviseFor("attack"), "lamp advisor");
	}

	private static void assertRefuses(Runnable call, String what)
	{
		try
		{
			call.run();
			fail(what + " returned an answer it cannot compute yet");
		}
		catch (UnsupportedOperationException expected)
		{
			// The stub is doing its job.
		}
	}
}
