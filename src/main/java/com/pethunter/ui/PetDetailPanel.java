package com.pethunter.ui;

import com.pethunter.data.Pet;
import com.pethunter.data.PetSource;
import java.awt.Color;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The expanded part of a row: every source with its rate, and what the dryness figure rests on.
 *
 * <p>P1 shows sources and assumptions only. Method selection (P3), manual counts (P2) and the
 * method comparison table (P4) are added in their phases.
 */
class PetDetailPanel extends JPanel
{
	PetDetailPanel(PetEntry entry)
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
			add(sourceLine(source));
		}

		add(heading("Dryness"));
		add(wrapped(DrynessFormat.explanation(entry), ColorScheme.LIGHT_GRAY_COLOR));
	}

	private static Component sourceLine(PetSource source)
	{
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

		JLabel label = htmlLabel(html.toString(), ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		if (source.getNotes() != null)
		{
			label.setToolTipText(source.getNotes());
		}
		return label;
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
		// A fixed width makes the HTML renderer wrap instead of widening the sidebar
		JLabel label = new JLabel("<html><body style='width:170px'>" + bodyHtml + "</body></html>");
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
