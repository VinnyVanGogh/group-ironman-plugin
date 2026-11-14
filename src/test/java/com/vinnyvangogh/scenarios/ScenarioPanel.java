package com.vinnyvangogh.scenarios;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import net.runelite.client.ui.PluginPanel;

/**
 * The Scenarios sidebar: pick a scenario, Run it (steps keep their waits) or Step through it one event at a time.
 * Swing thread only; events leave through {@code post}, which hands them to the client thread.
 */
final class ScenarioPanel extends PluginPanel
{
	private static final String WARNING = "Events go to every plugin in this client, and the plugin's outputs are "
		+ "real: a webhook you set gets real posts. Point webhooks at a test channel first.";

	private final EventKinds kinds;
	private final Consumer<Object> post;
	private final JComboBox<Scenario> picker = new JComboBox<>();
	private final JTextArea about = text(4);
	private final JTextArea log = text(14);
	private ScenarioRun run;
	private Timer timer;

	ScenarioPanel(EventKinds kinds, Consumer<Object> post)
	{
		this.kinds = kinds;
		this.post = post;
		setLayout(new BorderLayout(0, 6));

		JTextArea warning = text(4);
		warning.setText(WARNING);
		picker.addActionListener(e -> chosen());

		JPanel buttons = new JPanel(new GridLayout(1, 4, 4, 0));
		buttons.add(button("Run", this::runAll));
		buttons.add(button("Step", this::stepOnce));
		buttons.add(button("Reset", this::reset));
		buttons.add(button("Reload", this::reload));

		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.add(warning, BorderLayout.NORTH);
		top.add(picker, BorderLayout.CENTER);
		top.add(buttons, BorderLayout.SOUTH);

		JPanel middle = new JPanel(new BorderLayout(0, 6));
		middle.add(about, BorderLayout.NORTH);
		middle.add(new JScrollPane(log), BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(middle, BorderLayout.CENTER);
	}

	private static JTextArea text(int rows)
	{
		JTextArea area = new JTextArea(rows, 20);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		return area;
	}

	private static JButton button(String label, Runnable action)
	{
		JButton b = new JButton(label);
		b.addActionListener(e -> action.run());
		return b;
	}

	/** Reads every scenario again: edit a JSON file, rebuild resources, Reload. */
	void reload()
	{
		reset();
		picker.removeAllItems();
		try
		{
			for (Scenario s : ScenarioParser.loadAll(ScenarioParser.DEFAULT_DIR, kinds))
			{
				picker.addItem(s);
			}
			say("Loaded " + picker.getItemCount() + " scenarios.");
		}
		catch (ScenarioException e)
		{
			say("Cannot load scenarios: " + e.getMessage());
		}
	}

	private void chosen()
	{
		reset();
		Scenario s = (Scenario) picker.getSelectedItem();
		about.setText(s == null ? "" : s.description == null ? s.name : s.description);
	}

	private ScenarioRun current()
	{
		Scenario s = (Scenario) picker.getSelectedItem();
		if (s != null && (run == null || !run.hasNext()))
		{
			run = new ScenarioRun(s, kinds, post).live(true);
			say("— " + s.name + " —");
		}
		return run;
	}

	private void stepOnce()
	{
		stopTimer();
		fire(current());
	}

	private void runAll()
	{
		stopTimer();
		ScenarioRun r = current();
		if (r != null)
		{
			schedule(r);
		}
	}

	/** The next step after its wait, then the one after that, until the scenario ends or Reset. */
	private void schedule(ScenarioRun r)
	{
		if (!r.hasNext())
		{
			say("Done. Check the plugin's panel, overlay and Discord channel.");
			return;
		}
		timer = new Timer((int) Math.min(Integer.MAX_VALUE, r.nextWait()), e ->
		{
			fire(r);
			schedule(r);
		});
		timer.setRepeats(false);
		timer.start();
	}

	private void fire(ScenarioRun r)
	{
		if (r == null || !r.hasNext())
		{
			return;
		}
		try
		{
			say(r.step().toString());
		}
		catch (ScenarioException e)
		{
			say(e.getMessage());
		}
	}

	private void reset()
	{
		stopTimer();
		run = null;
		log.setText("");
	}

	private void stopTimer()
	{
		if (timer != null)
		{
			timer.stop();
			timer = null;
		}
	}

	private void say(String line)
	{
		log.append(line + "\n");
	}
}
