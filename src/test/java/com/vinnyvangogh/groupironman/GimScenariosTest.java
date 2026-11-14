package com.vinnyvangogh.groupironman;

import com.vinnyvangogh.scenarios.EventKinds;
import com.vinnyvangogh.scenarios.Expectations;
import com.vinnyvangogh.scenarios.Scenario;
import com.vinnyvangogh.scenarios.ScenarioParser;
import com.vinnyvangogh.scenarios.ScenarioRun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every scenario in {@code src/test/resources/scenarios}, headless: its steps posted on a real RuneLite
 * {@link EventBus} to {@link GimScenarioRig}, then its {@code expect} entries checked. A new scenario file is run
 * here with no change to this class.
 */
@RunWith(Parameterized.class)
public class GimScenariosTest
{
	private static final EventKinds KINDS = GimEventKinds.kinds();

	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> scenarios()
	{
		List<Object[]> out = new ArrayList<>();
		for (Scenario s : ScenarioParser.loadAll(ScenarioParser.DEFAULT_DIR, KINDS))
		{
			out.add(new Object[]{s.source, s});
		}
		return out;
	}

	private final Scenario scenario;

	public GimScenariosTest(String file, Scenario scenario)
	{
		this.scenario = scenario;
	}

	@Test
	public void theRigDoesWhatTheScenarioExpects()
	{
		GimScenarioRig rig = new GimScenarioRig("GroupShip");
		// The bus logs and swallows a subscriber's exception; here it fails the scenario instead.
		List<Throwable> thrown = new ArrayList<>();
		EventBus bus = new EventBus(thrown::add);
		bus.register(rig);
		new ScenarioRun(scenario, KINDS, bus::post).runAll(ms -> rig.now += ms);
		rig.settle();
		List<String> failures = new ArrayList<>();
		thrown.forEach(t -> failures.add("a subscriber threw " + t));
		failures.addAll(Expectations.check(scenario, rig.probes()));
		assertTrue(scenario.name + ":\n  " + String.join("\n  ", failures), failures.isEmpty());
	}

	@Test
	public void everyScenarioSaysWhatItChecks()
	{
		assertTrue(scenario.source + " has no expect entries", !scenario.expect.isEmpty());
		assertTrue(scenario.source + " has no description", scenario.description != null);
		assertEquals("files are named NN-words.json", true, scenario.source.matches("\\d\\d-[a-z0-9-]+\\.json"));
	}
}
