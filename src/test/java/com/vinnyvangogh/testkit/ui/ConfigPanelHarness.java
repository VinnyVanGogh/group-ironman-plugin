package com.vinnyvangogh.testkit.ui;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RuneLite's own settings panel ({@code ConfigPanel}) for a plugin's config
 * interface, built without the client: the page a player opens from the
 * wrench, with the same sections, widgets and order.
 *
 * <p>ConfigPanel is package-private with a private Guice constructor, and the
 * client jar is signed, so a test class cannot join its package: it is
 * reached by reflection here. That is test code only; it never ships. Its
 * other collaborators are used only on clicks a test never makes (plugin
 * toggle, reset, colour picker), so they are null. {@link ConfigManager} is a
 * mock that answers every read from the interface's own defaults, overridden
 * per test. If RuneLite changes the panel's shape, this fails loudly saying so.
 *
 * <p>Copy-in template: no plugin types; pass any plugin's config interface.
 */
public final class ConfigPanelHarness
{
	private static final String PANEL = "net.runelite.client.plugins.config.ConfigPanel";
	private static final String DESCRIPTOR = "net.runelite.client.plugins.config.PluginConfigurationDescriptor";

	private final PluginPanel panel;

	private ConfigPanelHarness(PluginPanel panel)
	{
		this.panel = panel;
	}

	/**
	 * The panel for {@code defaults}' config interface ({@code new MyConfig(){}}
	 * serves), showing {@code overrides} (keyName to value) over the defaults.
	 * Call on the Swing thread.
	 */
	public static ConfigPanelHarness of(String title, Config defaults, Map<String, Object> overrides)
	{
		Map<String, Object> values = defaults(defaults);
		values.putAll(overrides);
		ConfigManager configManager = configManager(values);
		ConfigDescriptor descriptor = configManager.getConfigDescriptor(defaults);
		try
		{
			Class<?> panelClass = Class.forName(PANEL);
			resetSectionMemory(panelClass);
			Constructor<?> make = panelClass.getDeclaredConstructors()[0];
			make.setAccessible(true);
			Object[] args = new Object[make.getParameterCount()];
			for (int i = 0; i < args.length; i++)
			{
				args[i] = make.getParameterTypes()[i] == ConfigManager.class ? configManager : null;
			}
			PluginPanel panel = (PluginPanel) make.newInstance(args);
			Method init = panelClass.getDeclaredMethod("init", Class.forName(DESCRIPTOR));
			init.setAccessible(true);
			init.invoke(panel, descriptor(title, defaults, descriptor));
			return new ConfigPanelHarness(panel);
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException("RuneLite's ConfigPanel changed shape; update ConfigPanelHarness", e);
		}
	}

	private static Object descriptor(String title, Config config, ConfigDescriptor cd)
		throws ReflectiveOperationException
	{
		Constructor<?> make = Class.forName(DESCRIPTOR).getDeclaredConstructor(String.class, String.class,
			String[].class, Config.class, ConfigDescriptor.class);
		make.setAccessible(true);
		return make.newInstance(title, "", new String[0], config, cd);
	}

	public PluginPanel panel()
	{
		return panel;
	}

	/** The section titled {@code name}: its header and its contents. */
	public JComponent section(String name)
	{
		return (JComponent) title(name).getParent().getParent();
	}

	/** Whether the section's contents are showing. */
	public boolean isOpen(String name)
	{
		Container header = title(name).getParent();
		for (Component c : header.getParent().getComponents())
		{
			if (c != header)
			{
				return c.isVisible();
			}
		}
		return false;
	}

	/** Opens or closes a section with its own expand button, as a click would. */
	public void setOpen(String name, boolean open)
	{
		if (isOpen(name) == open)
		{
			return;
		}
		for (Component c : title(name).getParent().getComponents())
		{
			if (c instanceof JButton)
			{
				((JButton) c).doClick(0);
				return;
			}
		}
		throw new AssertionError("Section '" + name + "' has no expand button");
	}

	/** A section's title: RuneLite draws them in brand orange, which tells them apart from setting names. */
	private JLabel title(String name)
	{
		for (JLabel l : new SwingDriver(panel).all(JLabel.class))
		{
			if (name.equals(l.getText()) && ColorScheme.BRAND_ORANGE.equals(l.getForeground()))
			{
				return l;
			}
		}
		throw new AssertionError("No section '" + name + "'");
	}

	// ---- The mocked ConfigManager ----

	/** Each item's default, by keyName, read by calling the interface's default methods. */
	static Map<String, Object> defaults(Config defaults)
	{
		Map<String, Object> out = new HashMap<>();
		Class<?> iface = defaults.getClass().getInterfaces()[0];
		for (Method m : iface.getDeclaredMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item == null || m.getParameterCount() != 0 || !m.isDefault())
			{
				continue;
			}
			try
			{
				out.put(item.keyName(), m.invoke(defaults));
			}
			catch (ReflectiveOperationException e)
			{
				throw new IllegalStateException("Cannot read the default of " + item.keyName(), e);
			}
		}
		return out;
	}

	private static ConfigManager configManager(Map<String, Object> values)
	{
		ConfigManager cm = mock(ConfigManager.class);
		when(cm.getConfigDescriptor(any())).thenCallRealMethod();
		when(cm.getConfiguration(anyString(), anyString())).thenAnswer(inv ->
			asString(values.get(inv.<String>getArgument(1))));
		when(cm.getConfiguration(anyString(), anyString(), any(Type.class))).thenAnswer(inv ->
			values.get(inv.<String>getArgument(1)));
		return cm;
	}

	/** A value as ConfigManager stores it: enums by name, everything else as its text. */
	static String asString(Object value)
	{
		if (value == null)
		{
			return null;
		}
		if (value instanceof Enum)
		{
			return ((Enum<?>) value).name();
		}
		return String.valueOf(value);
	}

	/** The panel remembers opened sections for the whole JVM; each harness starts from the defaults. */
	@SuppressWarnings("unchecked")
	private static void resetSectionMemory(Class<?> panelClass) throws ReflectiveOperationException
	{
		Field f = panelClass.getDeclaredField("sectionExpandStates");
		f.setAccessible(true);
		((Map<Object, Object>) f.get(null)).clear();
	}
}
