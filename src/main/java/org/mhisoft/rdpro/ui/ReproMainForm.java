/*
 * Copyright (c) 2014- MHISoft LLC and/or its affiliates. All rights reserved.
 * Licensed to MHISoft LLC under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. MHISoft LLC licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.mhisoft.rdpro.ui;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.mhisoft.rdpro.RdPro;

/**
 * Description:
 *
 * @author Tony Xue
 * @since Nov, 2014
 */
public class ReproMainForm {

	JFrame frame;
	RdPro rdpro;
	RdPro.RdProRunTimeProperties props;


	JCheckBox chkForceDelete;
	JCheckBox chkShowInfo;

	JPanel layoutPanel1;
	JLabel labelDirName;
	JTextArea outputTextArea;
	JScrollPane outputTextAreaScrollPane;
	private JButton btnOk;
	private JButton btnCancel;
	private JButton btnHelp;
	private JTextField fldTargetDir;
	private JLabel labelStatus;
	private JTextField fldRootDir;
	private JButton btnEditRootDir;

	JList list1;

	public ReproMainForm() {
		chkForceDelete.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				//outputTextArea.append("Value of the checkbox:" + chkForceDelete.isSelected());
			}
		});
		chkShowInfo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showHideInfo(chkShowInfo.isSelected());
			}
		});
		btnCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				frame.dispose();
			}
		});
		btnOk.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				//Don't block the EDT
				//probably using the Swing thread which is waiting
				// for your code to execute before it can update the UI. Try using a separate thread for that loop.
				//just do invokeLater() as below does not work.


//				SwingUtilities.invokeLater(new Runnable() {
//					@Override
//					public void run() {
						//doit();
//					}
//				});

				DoItJobThread t = new DoItJobThread();
				t.start();

			}
		});
		btnHelp.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				outputTextArea.setText("");
				showHideInfo(true);
				rdpro.getRdProUI().help();
				scrollToTop();

			}
		});
		btnEditRootDir.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				fldRootDir.setEditable(!fldRootDir.isEditable());
			}
		});
	}


	public void showHideInfo(boolean visible) {
		outputTextArea.setVisible(visible);
		outputTextAreaScrollPane.setVisible(visible);

		if (visible && frame.getSize().getWidth() < 500) {
			frame.setPreferredSize(new Dimension(500, 500));
			frame.pack();
		}

		chkShowInfo.setSelected(visible);
	}

	public void scrollToBottom() {
		outputTextArea.validate();
		JScrollBar vertical = outputTextAreaScrollPane.getVerticalScrollBar();
		vertical.setValue(vertical.getMaximum());
	}

	public void scrollToTop() {
		outputTextArea.validate();
		JScrollBar vertical = outputTextAreaScrollPane.getVerticalScrollBar();
		vertical.setValue(vertical.getMinimum());
	}




	public void init() {
		frame = new JFrame("Recursive Directory Removal Pro "+RdProUI.version);
		frame.setContentPane(layoutPanel1);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		frame.pack();

		/*position it*/
		//frame.setLocationRelativeTo(null);  // *** this will center your app ***
		PointerInfo a = MouseInfo.getPointerInfo();
		Point b = a.getLocation();
		int x = (int) b.getX();
		int y = (int) b.getY();
		frame.setLocation(x + 100, y);

		btnEditRootDir.setBorder(null);

		frame.setVisible(true);

	}


	class DoItJobThread extends Thread {
		@Override
		public void run() {
			doit();
		}
	}


	public void doit() {
		if (props.isSuccess()) {
			props.setForceDelete(chkForceDelete.isSelected());
			props.setInteractive(!chkForceDelete.isSelected());

			String targetDir = fldTargetDir.getText() == null || fldTargetDir.getText().trim().length() == 0 ? null : fldTargetDir.getText().trim();
			props.setTargetDir(targetDir);
			props.setVerbose(chkShowInfo.isSelected());
			props.setRootDir( fldRootDir.getText() );


			rdpro.getRdProUI().println("working.");

			labelStatus.setText("Working...");
			labelStatus.setText("");

			rdpro.run(props);

			labelStatus.setText("Done. Dir Removed:" + rdpro.getStatistics().getDirRemoved()
					+ ", Files removed:" + rdpro.getStatistics().getFilesRemoved());
		}
	}

	public static void main(String[] args) {
		ReproMainForm rdProMain = new ReproMainForm();
		rdProMain.init();
		GraphicsRdProUIImpl rdProUI = new GraphicsRdProUIImpl();
		rdProUI.setOutputTextArea(rdProMain.outputTextArea);
		rdProUI.setLabelStatus(rdProMain.labelStatus);

		if (RdPro.debug) {
			int i = 0;
			for (String arg : args) {
				rdProUI.println("arg[" + i + "]=" + arg);
				i++;
			}
		}

		//default it to current dir
		String defaultRootDir = System.getProperty("user.dir");
		rdProMain.rdpro = new RdPro(rdProUI);
		rdProMain.props = rdProUI.parseCommandLineArguments(args);

		if (rdProMain.props.getRootDir() == null)
			rdProMain.props.setRootDir(defaultRootDir);

		if (RdPro.debug) {
			rdProUI.println("set root dir=" + rdProMain.props.getRootDir());
		}

		//display it
		rdProMain.fldRootDir.setText(rdProMain.props.getRootDir());

		//rdProUI.help();


	}
}
