package com.wd.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.wd.i18n.DataChartBundle;
import java.util.Objects;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.Nullable;

/**
 * @author lww
 * @date 2025-03-21 11:44
 */
public class Donation extends DialogWrapper {

	private JPanel contentPanel;
	private JLabel donationLabel;
	private JLabel wxLabel;
	private JLabel zfbLabel;
	private JLabel lastLabel;
	private JLabel infoLabel;
	private JTextField emailText;

	public Donation(@Nullable Project project) {
		super(project);
		init();
		setResizable(false);
		setTitle(DataChartBundle.message("DataChart.donation.title"));
		//contentPanel.setPreferredSize(JBUI.size(300, 400));
		donationLabel.setText(DataChartBundle.message("DataChart.donation.description"));
		infoLabel.setText(DataChartBundle.message("DataChart.donation.contact"));
		emailText.setText("lerder@foxmail.com");
		ImageIcon wx = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/wx.jpg")));
		//wx.setImage(wx.getImage().getScaledInstance(268, 365, Image.SCALE_DEFAULT));
		wxLabel.setIcon(wx);
		ImageIcon zfb = new ImageIcon(Objects.requireNonNull(getClass().getResource("/icons/zfb.jpg")));
		//zfb.setImage(zfb.getImage().getScaledInstance(232, 349, Image.SCALE_DEFAULT));
		zfbLabel.setIcon(zfb);
	}

	@Override
	protected @Nullable JComponent createCenterPanel() {
		return contentPanel;
	}
}
