package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.data.PetSource;
import com.pethunter.math.SourceEstimator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The expanded part of a row: every source with its rate, counts and notes, a manual count entry
 * where the game cannot supply a trustworthy count, and what the dryness figure rests on.
 */
class PetDetailPanel extends JPanel
{
	static final int TEXT_WIDTH = 140;

	static final String MANUAL_DISCLAIMER = "Your own count. The game cannot confirm it, so figures from it are marked ~ as estimates.";
	static final String MANUAL_REPLACES_WARNED = "Replaces the game count above for this source.";

	/** Largest count accepted from the text box; anything bigger is a typo. */
	static final long MAX_MANUAL_COUNT = 10_000_000L;

	static final String METHOD_PROMPT = "Which method did you train with?";
	static final String NO_CHOICE = "Not chosen";

	PetDetailPanel(PetEntry entry, ManualCountListener manualCounts, MethodChoiceListener methodChoices)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

		Pet pet = entry.getPet();
		add(heading("Sources"));
		if (pet.getSources().isEmpty())
		{
			add(wrapped("No sources for this pet in the dataset yet.", ColorScheme.LIGHT_GRAY_COLOR));
		}
		for (PetSource source : pet.getSources())
		{
			add(sourceLine(source, entry));
			if (!entry.isObtained() && source.isVerified() && SourceEstimator.acceptsManualCount(source))
			{
				add(manualCountInput(source, entry, manualCounts));
			}
		}

		List<PetSource> xpSources = SourceEstimator.xpDerivedSources(pet);
		if (!entry.isObtained() && !xpSources.isEmpty())
		{
			add(heading("Method"));
			add(methodChooser(entry, xpSources, methodChoices));
		}

		add(heading("Dryness"));
		add(wrapped(DrynessFormat.explanation(entry), ColorScheme.LIGHT_GRAY_COLOR));
	}

	private static Component sourceLine(PetSource source, PetEntry entry)
	{
		String count = DrynessFormat.counter(source, entry.getProgress());
		String rate = DrynessFormat.rate(source);
		Color rateColor = DrynessFormat.UNKNOWN.equals(rate) ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.TEXT_COLOR;
		StringBuilder html = new StringBuilder()
			.append(escape(source.getLabel()))
			.append("<br><font color='").append(hex(rateColor)).append("'>")
			.append(escape(rate)).append("</font>")
			.append(" <font color='").append(hex(ColorScheme.MEDIUM_GRAY_COLOR)).append("'>")
			.append(escape(DrynessFormat.rateModelDescription(source)));
		if (source.isVerified() && !source.getCitations().isEmpty())
		{
			html.append(", ").append(source.getCitations().size()).append(source.getCitations().size() == 1 ? " source" : " sources");
		}
		html.append("</font>");
		if (count != null)
		{
			html.append("<br>").append(escape(count));
		}
		if (source.getCountWarning() != null)
		{
			html.append("<br><font color='").append(hex(ColorScheme.BRAND_ORANGE)).append("'>Warning: ")
				.append(escape(source.getCountWarning())).append("</font>");
		}
		if (source.getNotes() != null)
		{
			html.append("<br><font color='").append(hex(ColorScheme.MEDIUM_GRAY_COLOR)).append("'>")
				.append(escape(source.getNotes())).append("</font>");
		}

		JLabel label = htmlLabel(html.toString(), ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return label;
	}

	/**
	 * Lets the player say which activity their XP came from. Only one XP-derived source can be used
	 * per pet, because they all draw on the same skill XP.
	 */
	private static Component methodChooser(PetEntry entry, List<PetSource> xpSources, MethodChoiceListener listener)
	{
		JPanel container = new JPanel(new BorderLayout());
		container.setOpaque(false);
		container.setAlignmentX(LEFT_ALIGNMENT);

		JComboBox<String> box = new JComboBox<>();
		box.setName("method:" + entry.getPet().getId());
		box.setFont(FontManager.getRunescapeSmallFont());
		box.addItem(NO_CHOICE);
		for (PetSource source : xpSources)
		{
			box.addItem(source.getLabel());
		}
		String chosen = entry.getAssumedSourceId();
		box.setSelectedItem(xpSources.stream().filter(s -> s.getId().equals(chosen)).map(PetSource::getLabel)
			.findFirst().orElse(NO_CHOICE));
		box.addActionListener(e ->
		{
			int index = box.getSelectedIndex();
			listener.onMethodChosen(entry.getPet().getId(), index <= 0 ? null : xpSources.get(index - 1).getId());
		});

		container.add(box, BorderLayout.NORTH);
		container.add(htmlLabel("<font color='" + hex(ColorScheme.MEDIUM_GRAY_COLOR) + "'>" + escape(METHOD_PROMPT)
			+ " The estimate assumes all of your XP in this skill came from it.</font>", ColorScheme.MEDIUM_GRAY_COLOR),
			BorderLayout.CENTER);
		return container;
	}

	private static Component manualCountInput(PetSource source, PetEntry entry, ManualCountListener listener)
	{
		OptionalLong current = entry.getProgress().getManualCount(source.getId());

		JPanel container = new JPanel(new BorderLayout());
		container.setOpaque(false);
		container.setAlignmentX(LEFT_ALIGNMENT);
		container.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setOpaque(false);
		JLabel prompt = new JLabel("Your count:");
		prompt.setFont(FontManager.getRunescapeSmallFont());
		prompt.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JTextField field = new JTextField(current.isPresent() ? Long.toString(current.getAsLong()) : "", 6);
		field.setName("manualCount:" + source.getId());
		field.setBackground(ColorScheme.DARK_GRAY_COLOR);
		field.setForeground(ColorScheme.TEXT_COLOR);
		field.setCaretColor(ColorScheme.TEXT_COLOR);

		JButton save = smallButton("Save");
		save.setName("saveManualCount:" + source.getId());
		Runnable submit = () ->
		{
			Long parsed = parseCount(field.getText());
			if (parsed == null)
			{
				field.setBorder(BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR));
				return;
			}
			listener.onManualCount(source.getId(), parsed);
		};
		save.addActionListener(e -> submit.run());
		field.addActionListener(e -> submit.run());

		row.add(prompt);
		row.add(field);
		row.add(save);
		if (current.isPresent())
		{
			JButton clear = smallButton("Clear");
			clear.setName("clearManualCount:" + source.getId());
			clear.addActionListener(e -> listener.onManualCount(source.getId(), null));
			row.add(clear);
		}
		container.add(row, BorderLayout.NORTH);

		String disclaimer = source.getCounterKey() != null ? MANUAL_DISCLAIMER + " " + MANUAL_REPLACES_WARNED : MANUAL_DISCLAIMER;
		container.add(htmlLabel("<font color='" + hex(ColorScheme.MEDIUM_GRAY_COLOR) + "'>" + escape(disclaimer) + "</font>",
			ColorScheme.MEDIUM_GRAY_COLOR), BorderLayout.CENTER);
		return container;
	}

	/**
	 * @return the count typed by the player, ignoring thousands separators and spaces, or null if it
	 * is not a whole number from 0 to {@link #MAX_MANUAL_COUNT}
	 */
	@Nullable
	static Long parseCount(String text)
	{
		String digits = text == null ? "" : text.replace(",", "").replace(" ", "").trim();
		if (digits.isEmpty() || digits.length() > 9 || !digits.chars().allMatch(Character::isDigit))
		{
			return null;
		}
		long value = Long.parseLong(digits);
		return value <= MAX_MANUAL_COUNT ? value : null;
	}

	private static JButton smallButton(String text)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		return button;
	}

	private static Component heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static Component wrapped(String text, Color color)
	{
		return htmlLabel(escape(text), color);
	}

	private static JLabel htmlLabel(String bodyHtml, Color color)
	{
		// A fixed width makes the HTML renderer wrap instead of widening the sidebar. HTML measures
		// with a default font, so leave room for the wider RuneScape font (170px was clipped in-game)
		JLabel label = new JLabel("<html><body style='width:" + TEXT_WIDTH + "px'>" + bodyHtml + "</body></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String hex(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}
}
