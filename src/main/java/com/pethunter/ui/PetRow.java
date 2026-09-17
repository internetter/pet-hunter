package com.pethunter.ui;

import com.pethunter.math.Confidence;
import com.pethunter.math.DrynessResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A collapsed pet row that expands into a {@link PetDetailPanel} when clicked.
 */
class PetRow extends JPanel
{
	private static final Dimension ICON_SIZE = new Dimension(36, 32);
	private static final Color ESTIMATED_COLOR = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private final PetEntry entry;
	private final ManualCountListener manualCounts;
	private final JPanel summary = new JPanel(new BorderLayout(6, 0));
	private PetDetailPanel detail;
	private boolean expanded;

	PetRow(PetEntry entry, PetIconLoader icons, ManualCountListener manualCounts, boolean expanded, Runnable onToggle)
	{
		this.entry = entry;
		this.manualCounts = manualCounts;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));

		summary.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		summary.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 6));

		JLabel icon = new JLabel();
		icon.setPreferredSize(ICON_SIZE);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (entry.getPet().getItemId() != null)
		{
			icons.load(entry.getPet().getItemId(), icon);
		}
		summary.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(entry.getPet().getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(ColorScheme.TEXT_COLOR);
		JLabel source = new JLabel(PetListModel.sourceSummary(entry.getPet()));
		source.setFont(FontManager.getRunescapeSmallFont());
		source.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		text.add(name);
		text.add(source);
		summary.add(text, BorderLayout.CENTER);

		JLabel status = new JLabel(DrynessFormat.rowStatus(entry));
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(statusColor(entry));
		summary.add(status, BorderLayout.EAST);

		String tooltip = "<html><body style='width:220px'>" + PetDetailPanel.escape(DrynessFormat.explanation(entry)) + "</body></html>";
		summary.setToolTipText(tooltip);

		summary.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!PetRow.this.expanded);
				onToggle.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				summary.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				summary.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		add(summary, BorderLayout.NORTH);
		setExpanded(expanded);
	}

	String getPetId()
	{
		return entry.getPet().getId();
	}

	boolean isExpanded()
	{
		return expanded;
	}

	void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		if (expanded && detail == null)
		{
			detail = new PetDetailPanel(entry, manualCounts);
			add(detail, BorderLayout.CENTER);
		}
		if (detail != null)
		{
			detail.setVisible(expanded);
		}
		revalidate();
		repaint();
	}

	static Color statusColor(PetEntry entry)
	{
		if (entry.isObtained())
		{
			return ColorScheme.PROGRESS_COMPLETE_COLOR;
		}
		DrynessResult dryness = entry.getDryness();
		if (!dryness.hasFigure())
		{
			return ColorScheme.MEDIUM_GRAY_COLOR;
		}
		if (DrynessFormat.hasCountWarning(entry))
		{
			return ColorScheme.BRAND_ORANGE;
		}
		// ESTIMATED figures are muted; EXACT figures are plain text
		return dryness.getConfidence().orElseThrow() == Confidence.EXACT ? ColorScheme.TEXT_COLOR : ESTIMATED_COLOR;
	}
}
