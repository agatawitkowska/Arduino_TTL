package edu.mbl.cdp.ttl;

/*
 * Copyright © 2009 – 2013, Marine Biological Laboratory
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, 
 * this list of conditions and the following disclaimer in the documentation 
 * and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS “AS IS” AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are those of 
 * the authors and should not be interpreted as representing official policies, 
 * either expressed or implied, of any organization.
 * 
 * Multiple-Frame Averaging plug-in for Micro-Manager
 * @author Amitabh Verma (averma@mbl.edu), Grant Harris (gharris@mbl.edu)
 * Marine Biological Laboratory, Woods Hole, Mass.
 * 
 */

import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.internalinterfaces.AcqSettingsListener;
import org.micromanager.utils.ReportingUtils;

public class TTLControls extends javax.swing.JFrame implements AcqSettingsListener {

    /* TODO
     * Pre Image Acquisition option
     */

    private TTL fa;
    private static JFrame About_Frame;
    Point p;
    boolean initialized = false;
    boolean debugModeNoDev = true;
    
    /**
     * Creates new form TTLControls
     */
    public TTLControls(final TTL fa_) {
        this.fa = fa_;
        initComponents();
        URL url = this.getClass().getResource("frameIcon.png");
        Image im = Toolkit.getDefaultToolkit().getImage(url);
        setIconImage(im);
        
        jComboBox_OutputVal.setPrototypeDisplayValue("Square Wave");
        jComboBox_OutputVal1.setPrototypeDisplayValue("Square Wave");
        jTextField_duration.setEnabled(false);
        jTextField_duration1.setEnabled(false);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment( JLabel.CENTER );
        int cols = jTable1.getColumnModel().getColumnCount();
        for (int i=0; i < cols-1; i++) {
            jTable1.getColumnModel().getColumn(i).setCellRenderer( centerRenderer );
        }
        
        jTable1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        jTable1.getModel().addTableModelListener(new TableModelListener() {
            public void tableChanged(TableModelEvent e) {
                if (initialized) {
                    tableUpdate();
                }
            }
        });
        
        if (!isDeviceAvailable(TTL.DeviceLabel)) {
            TTLTagged.gui.showMessage("Device \""+TTL.DeviceLabel+"\" was not found or is not configured with \nShutter & Switch property in the Hardware Configuration Wizard." );
        } else {
            enabledCheckBox_.setSelected(true);
            update();
        }

        addFromPrefs();
        initialized = true;
        
        Runnable runn = new Runnable() {
            public void run() {
                try {
                    if (fa.ops_ == null) {
                        fa.ops_ = new OPS(fa);
                        fa.ops_.checkForOps();
                    }
                } catch (Exception ex) {
                }
            }
        };
        SwingUtilities.invokeLater(runn);
        
        //jComboBox_PrePost.setEnabled(false);
    }
        
    public void addFromPrefs() {
        DefaultTableModel dm = (DefaultTableModel) jTable1.getModel();
        try {
            List<String> keys = Arrays.asList(fa.PrefsTableVals.keys());
            Collections.sort(keys);            
            
            for (int i=0; i < keys.size(); i++) {
                String key = (String) keys.get(i);
                if (key!=null && !key.isEmpty()) {
                    if (key.split("-").length == 5) {
                        JSONObject json;
                        try {
                            json = new JSONObject(fa.PrefsTableVals.get(key, null));

                            try {
                                int frame_no = json.getInt(TTL.Key_FrameNo);
                                int channel_no = json.getInt(TTL.Key_ChannelNo);
                                int position_no = json.optInt(TTL.Key_PositionNo,0);
                                int slice_no = json.optInt(TTL.Key_SliceNo,0);
                                int pin_no = json.getInt(TTL.Key_PinNo);
                                String prepost = json.getString(TTL.Key_PrePost);
                                String output = json.getString(TTL.Key_Output);
                                String output_dur = json.optString(TTL.Key_OutputDuration,"NA");
                                boolean isEnabled = json.getBoolean(TTL.Key_IsEnabled);

                                fa.hm.put(key, json);
                                dm.addRow(new Object[]{frame_no, channel_no, position_no, slice_no, pin_no, prepost, output, output_dur, isEnabled});

                            } catch (Exception ex) {
                                Logger.getLogger(TTLControls.class.getName()).log(Level.SEVERE, null, ex);
                            }

                        } catch (JSONException ex) {
                            Logger.getLogger(TTLControls.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        fa.PrefsTableVals.remove(key);
                    }
                }
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(TTLControls.class.getName()).log(Level.SEVERE, null, ex);
        }
        refreshTable();
    }

    
    @Override
    public void settingsChanged() {        
        update();
    }
    
    public void update() {
        if (this.enabledCheckBox_.isSelected()) {
            fa.UpdateEngineAndCore();
            fa.enable(false);
        }

        fa.enable(this.enabledCheckBox_.isSelected());        
    }
    
    public boolean isPluginEnabled() {
        return this.enabledCheckBox_.isSelected();
    }    
        
    public void setUseWithOPS(boolean bool) {
        jCheckBox_useWithOPS.setSelected(bool);
    }
    
    public boolean isUseWithOPS() {
        return jCheckBox_useWithOPS.isSelected();
    }
    
    public void setEnabledUseWithOPS(boolean bool) {
        jCheckBox_useWithOPS.setEnabled(bool);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jComboBox_OutputVal = new javax.swing.JComboBox();
        jButton1 = new javax.swing.JButton();
        jComboBox_PinVal = new javax.swing.JComboBox();
        jTextField_FrameNo = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        jTextField_ChannelNo = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        jComboBox_PrePost = new javax.swing.JComboBox();
        jTextField_duration = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jTextField_PositionNo = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        jTextField_SliceNo = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jButton3 = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jComboBox_OutputVal1 = new javax.swing.JComboBox();
        jButton4 = new javax.swing.JButton();
        jLabel13 = new javax.swing.JLabel();
        jComboBox_PinVal1 = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        jTextField_duration1 = new javax.swing.JTextField();
        jPanel4 = new javax.swing.JPanel();
        jTextField1 = new javax.swing.JTextField();
        jPanel5 = new javax.swing.JPanel();
        enabledCheckBox_ = new javax.swing.JCheckBox();
        jCheckBox_useWithOPS = new javax.swing.JCheckBox();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jMenuItem2 = new javax.swing.JMenuItem();
        jMenuItem3 = new javax.swing.JMenuItem();
        jMenuItem4 = new javax.swing.JMenuItem();

        setTitle("Arduino TTL Control Plugin");
        setBounds(new java.awt.Rectangle(300, 300, 150, 150));
        setMinimumSize(new java.awt.Dimension(150, 150));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                formFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                formFocusLost(evt);
            }
        });

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Automated"));

        jComboBox_OutputVal.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "High", "Low", "Square Wave" }));
        jComboBox_OutputVal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox_OutputValActionPerformed(evt);
            }
        });

        jButton1.setText("Add/Edit");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        jComboBox_PinVal.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "8", "9", "10", "11", "12", "13" }));

        jTextField_FrameNo.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField_FrameNo.setText("0");

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel5.setText("Pin");

        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel6.setText("Frame No.");

        jLabel7.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel7.setText("Pre/Post");

        jButton2.setText("Remove");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jLabel8.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel8.setText("Channel No.");

        jTextField_ChannelNo.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField_ChannelNo.setText("0");

        jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel9.setText("Output");

        jComboBox_PrePost.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Post", "Pre" }));

        jTextField_duration.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField_duration.setText("NA");

        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("Duration (ms.)");

        jTextField_PositionNo.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField_PositionNo.setText("0");

        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("Position No.");

        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("Slice No.");

        jTextField_SliceNo.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField_SliceNo.setText("0");

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Frame No.", "Channel No.", "Position No.", "Slice No.", "Pin", "Pre/Post", "Output", "Duration", "Enabled"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Integer.class, java.lang.Integer.class, java.lang.Integer.class, java.lang.Integer.class, java.lang.Integer.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Boolean.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, false, false, false, false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jTable1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                jTable1MouseReleased(evt);
            }
        });
        jScrollPane1.setViewportView(jTable1);

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jLabel6, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 82, Short.MAX_VALUE)
                    .add(jTextField_FrameNo, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 86, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jTextField_ChannelNo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 75, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel8, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 69, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jTextField_PositionNo)
                    .add(jLabel10, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jTextField_SliceNo, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 61, Short.MAX_VALUE)
                    .add(jLabel11, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jComboBox_PinVal, 0, 77, Short.MAX_VALUE)
                    .add(jLabel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jComboBox_PrePost, 0, 85, Short.MAX_VALUE)
                    .add(jLabel7, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jComboBox_OutputVal, 0, 0, Short.MAX_VALUE)
                    .add(jLabel9, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jTextField_duration)
                    .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 82, Short.MAX_VALUE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jButton2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jButton1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 81, Short.MAX_VALUE)))
            .add(jScrollPane1)
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {jLabel6, jLabel8, jTextField_ChannelNo, jTextField_PositionNo, jTextField_SliceNo}, org.jdesktop.layout.GroupLayout.HORIZONTAL);

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel1Layout.createSequentialGroup()
                .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                        .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(jLabel9)
                            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                                .add(jPanel1Layout.createSequentialGroup()
                                    .add(jButton1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                    .add(jButton2))
                                .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                    .add(jTextField_FrameNo)
                                    .add(jTextField_ChannelNo, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 23, Short.MAX_VALUE)
                                    .add(jTextField_PositionNo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                            .add(jLabel11)
                            .add(jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                                .add(jLabel8)
                                .add(jLabel6)
                                .add(jLabel10))
                            .add(jPanel1Layout.createSequentialGroup()
                                .add(jLabel5)
                                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                                .add(jComboBox_PinVal))
                            .add(org.jdesktop.layout.GroupLayout.TRAILING, jTextField_SliceNo, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                        .add(jPanel1Layout.createSequentialGroup()
                            .add(jLabel7)
                            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                            .add(jComboBox_PrePost))
                        .add(org.jdesktop.layout.GroupLayout.TRAILING, jComboBox_OutputVal))
                    .add(jPanel1Layout.createSequentialGroup()
                        .add(jLabel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 23, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jTextField_duration)))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 199, Short.MAX_VALUE))
        );

        jPanel1Layout.linkSize(new java.awt.Component[] {jButton1, jComboBox_OutputVal, jComboBox_PinVal, jComboBox_PrePost, jLabel10, jLabel11, jLabel5, jLabel6, jLabel7, jLabel8, jLabel9, jTextField_ChannelNo, jTextField_FrameNo, jTextField_PositionNo, jTextField_SliceNo, jTextField_duration}, org.jdesktop.layout.GroupLayout.VERTICAL);

        jButton3.setText("Remove All");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        jLabel3.setText("Note: Micro-Manager dimension indexing is 0 based");

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Manual"));

        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setText("Output");

        jComboBox_OutputVal1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "High", "Low", "Square Wave" }));
        jComboBox_OutputVal1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBox_OutputVal1ActionPerformed(evt);
            }
        });

        jButton4.setText("Trigger");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jLabel13.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel13.setText("Pin");

        jComboBox_PinVal1.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "8", "9", "10", "11", "12", "13" }));

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("Duration (ms.)");

        jTextField_duration1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField_duration1.setText("NA");

        org.jdesktop.layout.GroupLayout jPanel2Layout = new org.jdesktop.layout.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel2Layout.createSequentialGroup()
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jLabel13, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jComboBox_PinVal1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 59, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jComboBox_OutputVal1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 74, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                    .add(jLabel12, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 70, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jTextField_duration1)
                    .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 82, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jButton4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 136, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jButton4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 43, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(jLabel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 14, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING, false)
                .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2Layout.createSequentialGroup()
                    .add(jLabel13)
                    .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                    .add(jComboBox_PinVal1))
                .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel2Layout.createSequentialGroup()
                    .add(jLabel12)
                    .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                    .add(jPanel2Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                        .add(jComboBox_OutputVal1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                        .add(jTextField_duration1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))))
        );

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder("Arduino Device Label"));

        jTextField1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField1.setText("Arduino");
        jTextField1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jTextField1ActionPerformed(evt);
            }
        });
        jTextField1.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                jTextField1FocusLost(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel4Layout = new org.jdesktop.layout.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .add(jTextField1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 170, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .add(jTextField1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel5.setBorder(new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.RAISED));

        enabledCheckBox_.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        enabledCheckBox_.setText("Enabled");
        enabledCheckBox_.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enabledCheckBox_ActionPerformed(evt);
            }
        });

        jCheckBox_useWithOPS.setText("Use with OpenPolScope");
        jCheckBox_useWithOPS.setEnabled(false);
        jCheckBox_useWithOPS.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBox_useWithOPSActionPerformed(evt);
            }
        });

        org.jdesktop.layout.GroupLayout jPanel5Layout = new org.jdesktop.layout.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(enabledCheckBox_)
                    .add(jCheckBox_useWithOPS))
                .addContainerGap(48, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel5Layout.createSequentialGroup()
                .add(enabledCheckBox_)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(jCheckBox_useWithOPS)
                .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.jdesktop.layout.GroupLayout jPanel3Layout = new org.jdesktop.layout.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jPanel3Layout.createSequentialGroup()
                .add(jPanel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel4, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel2, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, jPanel3Layout.createSequentialGroup()
                .add(jPanel3Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel5, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.LEADING, jPanel4, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        jMenu1.setText("Help");

        jMenuItem1.setText("About");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem1);

        jMenuItem2.setText("Description");
        jMenuItem2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem2ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem2);

        jMenuItem3.setText("Reset Preferences");
        jMenuItem3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem3ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem3);

        jMenuItem4.setText("Online Micro-Manager Wiki");
        jMenuItem4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem4ActionPerformed(evt);
            }
        });
        jMenu1.add(jMenuItem4);

        jMenuBar1.add(jMenu1);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jPanel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(jLabel3, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(jButton3)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jPanel3, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 82, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jPanel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(jLabel3)
                    .add(jButton3))
                .add(4, 4, 4))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

  private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
  }//GEN-LAST:event_formWindowClosed

  private void enabledCheckBox_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enabledCheckBox_ActionPerformed
      if (!isDeviceAvailable(TTL.DeviceLabel)) {
          enabledCheckBox_.setSelected(false);
          TTLTagged.gui.showMessage("Device \"" + TTL.DeviceLabel + "\" was not found or is not configured with \nShutter & Switch property in the Hardware Configuration Wizard.");
      } else {
          update();
      }
  }//GEN-LAST:event_enabledCheckBox_ActionPerformed

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        if (About_Frame == null) {
            About_Frame = new About(this);
        }
        About_Frame.setVisible(true);
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    private void formFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_formFocusGained
        fa.getDebugOptions();
    }//GEN-LAST:event_formFocusGained

    private void formFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_formFocusLost
        fa.getDebugOptions();
    }//GEN-LAST:event_formFocusLost

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        if (!isDeviceAvailable(TTL.DeviceLabel)) {
            TTLTagged.gui.showMessage("Device \"" + TTL.DeviceLabel + "\" was not found or is not configured with \nShutter & Switch property in the Hardware Configuration Wizard.");
        } else {
            addField(true);
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        addField(false);
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        
        fa.hm.clear();
        
        try {
            fa.PrefsTableVals.removeNode();
        } catch (BackingStoreException ex) {
            Logger.getLogger(TTLControls.class.getName()).log(Level.SEVERE, null, ex);
        }
        DefaultTableModel dm = (DefaultTableModel) jTable1.getModel();
        clearModel(dm);
        dm.fireTableDataChanged();
        jTable1.setModel(dm);
        
        jTable1.revalidate();
        jTable1.repaint();
        
        if (fa.isPreModeEnabled) {
            TTLTagged.gui.showMessage("This will also clear Runnables that might be attached by other processes!");
            fa.stopAndClearRunnable();
        }
        
        fa.isPreModeEnabled = false;
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jTextField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField1ActionPerformed
        updateDeviceLabel();
    }//GEN-LAST:event_jTextField1ActionPerformed

    private void jTextField1FocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_jTextField1FocusLost
        updateDeviceLabel();
    }//GEN-LAST:event_jTextField1FocusLost

    private void jTable1MouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jTable1MouseReleased
        p = evt.getPoint();
        onMouseRelease();
    }//GEN-LAST:event_jTable1MouseReleased

    private void jMenuItem2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem2ActionPerformed
        showDescriptionText();
    }//GEN-LAST:event_jMenuItem2ActionPerformed

    private void jCheckBox_useWithOPSActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBox_useWithOPSActionPerformed
        if (enabledCheckBox_.isSelected()) {
            enabledCheckBox_.setSelected(false);
            update();
        } else {
            boolean bool = jCheckBox_useWithOPS.isSelected();
            fa.UpdateEngineAndCore();
            boolean bool2 = jCheckBox_useWithOPS.isSelected();
            if (bool != bool2) {
                TTLTagged.gui.showError("Pol-Acquisition (OpenPolScope) could not be located ! \n"
                        + "Pol-Acquisition needs to be running !");
            }
        }
    }//GEN-LAST:event_jCheckBox_useWithOPSActionPerformed

    private void jComboBox_OutputValActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox_OutputValActionPerformed
        if (jComboBox_OutputVal.getSelectedIndex() == 2) {
            jTextField_duration.setEnabled(true);
            String str = jTextField_duration.getText().trim();
            if (str.equals("NA")) {
                jTextField_duration.setText("10");
            }
        } else {
            jTextField_duration.setEnabled(false);
            jTextField_duration.setText("NA");
        }
    }//GEN-LAST:event_jComboBox_OutputValActionPerformed

    private void jMenuItem3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem3ActionPerformed
        try {
            fa.PrefsRoot.removeNode();
            TTLTagged.gui.showMessage("Preferences have been reset !");
        } catch (BackingStoreException ex) {
            TTLTagged.gui.showError("Preferences could not be reset !");
        }
    }//GEN-LAST:event_jMenuItem3ActionPerformed

    private void jMenuItem4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem4ActionPerformed
        About.openHttpUrl(About.MMgrArduinoPageLink_);
    }//GEN-LAST:event_jMenuItem4ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        if (!isDeviceAvailable(TTL.DeviceLabel)) {
            TTLTagged.gui.showMessage("Device \"" + TTL.DeviceLabel + "\" was not found or is not configured with \nShutter & Switch property in the Hardware Configuration Wizard.");
        } else {
            manualTrigger();
        }
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jComboBox_OutputVal1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBox_OutputVal1ActionPerformed
        if (jComboBox_OutputVal1.getSelectedIndex() == 2) {
            jTextField_duration1.setEnabled(true);
            String str = jTextField_duration1.getText().trim();
            if (str.equals("NA")) {
                jTextField_duration1.setText("10");
            }
        } else {
            jTextField_duration1.setEnabled(false);
            jTextField_duration1.setText("NA");
        }
    }//GEN-LAST:event_jComboBox_OutputVal1ActionPerformed

    void showDescriptionText() {
        String msg = "";
        
        msg =     "Enabled Checkbox         -   Enables/Disables plugin \n"
                + "Use with OpenPolScope    -   Enable for OpenPolScope usage \n"
                + "Arduino Device Label     -   Arduino device name \n"
                + "Pin                      -   Arduino Control Pin \n"
                + "Frame No.                -   Triggering Frame No. \n"
                + "Channel No.              -   Triggering Channel No. \n"
                + "Pre/Post                 -   Pre or Post Image Acquisition. 'Pre' also obeys device delay for triggering device. \n"
                + "Output                   -   High (5V), Low (0V) or Square Wave (pulse) output \n"
                + "Duration                 -   Time interval for pulse \n"
                + "Add                      -   Adds the selection to table \n"
                + "Remove                   -   Removes the selection from table \n"
                + "Enabled (Table)          -   Enables/Disables selection \n"
                + "Remove All               -   Removes the entire table selection \n\n"
                + "More on Post: Post method of TTL is fired when the Image has been acquired and is in the Micro-Manager pipeline.\n"
                + "The TTL signal is a thread that runs parallel and so any Arduino device delay will have no effect.\n"
                + "More on Pre: Pre method of TTL is fired before the Image is acquired or other hardwares begin their activity for that dimension.\n"
                + "The TTL signal is fired in the same thread and blocks it for the device delay period.";
        
        TTLTagged.gui.showMessage(msg);
    }
    
    private void updateDeviceLabel() {
        String devlabel = jTextField1.getText();
        if (!devlabel.isEmpty() && isDeviceAvailable(devlabel)) {
            TTL.DeviceLabel = devlabel;
        } else {
            jTextField1.setText(TTL.DeviceLabel);
        }
    }
    
    private boolean isDeviceAvailable(String devName) {
        try {
        if (fa.core_.getDeviceName(devName+"-Shutter") == null) {
            return false;
        }
        
        if (fa.core_.getDeviceName(devName+"-Switch") == null) {
            return false;
        }
        
        if (!debugModeNoDev) {
            if (fa.core_.getProperty(devName+"-Shutter", "OnOff") == null) {
                return false;
            }
        }
        
        } catch (Exception ex) {
            return false;
        }
        
        return true;
    }
    
    
    private int getFramesField() {
        int num = 0;
        String str = jTextField_FrameNo.getText().trim().toString();
        num = (int) Integer.parseInt(str);
        return num;
    }
    
    private int getChannelsField() {
        int num = 0;
        String str = jTextField_ChannelNo.getText().trim().toString();
        num = (int) Integer.parseInt(str);
        return num;
    }
    
    private int getPositionsField() {
        int num = 0;
        String str = jTextField_PositionNo.getText().trim().toString();
        num = (int) Integer.parseInt(str);
        return num;
    }
    
    private int getSlicesField() {
        int num = 0;
        String str = jTextField_SliceNo.getText().trim().toString();
        num = (int) Integer.parseInt(str);
        return num;
    }

    private int getPinNoField() {
        int pin = jComboBox_PinVal.getSelectedIndex() + 8;
        return pin;
    }
    
    private String getPrePostField() {
        String out = (String) jComboBox_PrePost.getSelectedItem();                
        return out;
    }
    
    private String getOutputField() {
        String out = (String) jComboBox_OutputVal.getSelectedItem();                
        return out;
    }
    
    private String getOutputDuration() {
        String str = jTextField_duration.getText().trim().toString();
        return str;
    }
    
    private String getManualOutputDuration() {
        String str = jTextField_duration1.getText().trim().toString();
        return str;
    }
    
    private void manualTrigger() {        
        int pin_no = jComboBox_PinVal1.getSelectedIndex() + 8;
        String output = (String) jComboBox_OutputVal1.getSelectedItem();    
        String output_dur = getManualOutputDuration();
        boolean triggerResult = fa.processor.triggerArduino(output, pin_no, output_dur);
        
        if (fa.debugLogEnabled_ && !triggerResult) {
            ReportingUtils.displayNonBlockingMessage(TTL.METADATAKEY + " : error on manual triggering");
        }
    }
    
    private void addField(boolean bool) {
        int frame_no = getFramesField();
        int channel_no = getChannelsField();
        int position_no = getPositionsField();
        int slice_no = getSlicesField();
        int pin_no = getPinNoField();
        String output_dur = getOutputDuration();
        String prepost = getPrePostField();
        String output = getOutputField();

        String key = fa.getKeyBasedOnDim(frame_no,channel_no,position_no,slice_no,pin_no);
                
        if (bool) {
            JSONObject json = new JSONObject();
            try {
                json.put(TTL.Key_FrameNo, frame_no);
                json.put(TTL.Key_ChannelNo, channel_no);
                json.put(TTL.Key_PositionNo, position_no);
                json.put(TTL.Key_SliceNo, slice_no);
                json.put(TTL.Key_PinNo, pin_no);
                json.put(TTL.Key_Output, output);
                json.put(TTL.Key_PrePost, prepost);
                json.put(TTL.Key_IsEnabled, true);
                if (output.equals("Square Wave")) {
                    json.put(TTL.Key_OutputDuration, output_dur);
                } else {
                    json.put(TTL.Key_OutputDuration, "NA");
                }
                
                if (jCheckBox_useWithOPS.isSelected()) {
                    if (prepost.equals("Pre") && frame_no==0 && channel_no==0 && position_no==0 && slice_no==0) {
                        TTLTagged.gui.showMessage("OpenPolScope uses Pre mode (runnable) at (Frame, Channel, Position, Slice == 0)");
                        return;
                    }
                }
                
            } catch (JSONException ex) {
            }
            fa.hm.put(key, json);
            fa.PrefsTableVals.put(key, json.toString());
        } else {
            if (fa.hm.containsKey(key)) {                
                fa.hm.remove(key);
                fa.hm_runnableAddedMap.remove(key);
                fa.PrefsTableVals.remove(key);
            }
        }
        
        refreshTable();
    }
    
    private void refreshTable() {
        DefaultTableModel dm = (DefaultTableModel) jTable1.getModel();
        clearModel(dm);

        HashMap hashMap = (HashMap) fa.hm.clone();
        List keys = new ArrayList(hashMap.keySet());
        Collections.sort(keys);
        
        Iterator it = keys.iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            JSONObject json = (JSONObject) hashMap.get(key);
            try {
                int frame_no = json.getInt(TTL.Key_FrameNo);
                int channel_no = json.getInt(TTL.Key_ChannelNo);
                int position_no = json.getInt(TTL.Key_PositionNo);
                int slice_no = json.getInt(TTL.Key_SliceNo);
                int pin_no = json.getInt(TTL.Key_PinNo);
                String prepost = json.getString(TTL.Key_PrePost);
                String output = json.getString(TTL.Key_Output);
                boolean isEnabled = json.getBoolean(TTL.Key_IsEnabled);
                String output_dur = "NA";
                if (output.equals("Square Wave")) {
                    output_dur = json.optString(TTL.Key_OutputDuration,"NA");
                }
                dm.addRow(new Object[]{frame_no, channel_no, position_no, slice_no, pin_no, prepost, output, output_dur, isEnabled});
                                
            } catch (JSONException ex) {
                Logger.getLogger(TTLControls.class.getName()).log(Level.SEVERE, null, ex);
            }            
            
            //System.out.println(pairs.getKey() + " = " + pairs.getValue());
            hashMap.remove(key); // avoids a ConcurrentModificationException
            it.remove();
        }
        
        dm.fireTableDataChanged();
        jTable1.setModel(dm);
        
        jTable1.revalidate();
        jTable1.repaint();
        
        fa.stopAndClearRunnable();
        tableUpdate();
    }
    
    private void tableUpdate() {
        DefaultTableModel tabModel = (DefaultTableModel) jTable1.getModel();
        int rows = tabModel.getRowCount();
        fa.isPreModeEnabled = false;
        
        if (rows > 0) {
            for (int i = 0; i < rows; i++) {
                int frame_no = Integer.parseInt(tabModel.getValueAt(i, 0).toString());
                int channel_no = Integer.parseInt(tabModel.getValueAt(i, 1).toString());
                int position_no = Integer.parseInt(tabModel.getValueAt(i, 2).toString());
                int slice_no = Integer.parseInt(tabModel.getValueAt(i, 3).toString());
                int pin_no = Integer.parseInt(tabModel.getValueAt(i, 4).toString());
                String PrePost = tabModel.getValueAt(i, 5).toString();
                boolean isEnabled = Boolean.parseBoolean(tabModel.getValueAt(i, 8).toString());
                String key = fa.getKeyBasedOnDim(frame_no,channel_no,position_no,slice_no,pin_no);
                
                if (fa.hm.containsKey(key)) {
                    JSONObject json = (JSONObject) fa.hm.get(key);
                    try {
                        json.put(TTL.Key_IsEnabled, isEnabled);
                        fa.hm.put(key, json);
                        fa.PrefsTableVals.put(key, json.toString());
                        if (isEnabled && PrePost.equals("Pre")) {
                           fa.isPreModeEnabled = true;
                           TTL_runnable_triggering(true, frame_no, channel_no, position_no, slice_no, pin_no);
                        }
                    } catch (JSONException ex) {
                        Logger.getLogger(TTLControls.class.getName()).log(Level.SEVERE, null, ex);
                    }                    
                }                
            }
        }
    }
    
    public void onMouseRelease() {

        DefaultTableModel currentModel = (DefaultTableModel) jTable1.getModel();
        int currentRow = jTable1.rowAtPoint(p);
        int rows = currentModel.getRowCount();

        if (rows > 0) {
            int frame_no = Integer.parseInt(currentModel.getValueAt(currentRow, 0).toString());
            int channel_no = Integer.parseInt(currentModel.getValueAt(currentRow, 1).toString());
            int position_no = Integer.parseInt(currentModel.getValueAt(currentRow, 2).toString());
            int slice_no = Integer.parseInt(currentModel.getValueAt(currentRow, 3).toString());
            int pin_no = Integer.parseInt(currentModel.getValueAt(currentRow, 4).toString());            
            String prepost = currentModel.getValueAt(currentRow, 5).toString();
            String output = currentModel.getValueAt(currentRow, 6).toString();
            String output_dur = currentModel.getValueAt(currentRow, 7).toString();
            
            String key = fa.getKeyBasedOnDim(frame_no,channel_no,position_no,slice_no,pin_no);

            if (fa.hm.containsKey(key)) {
                jComboBox_PinVal.setSelectedIndex(pin_no-8);
                jTextField_FrameNo.setText(String.valueOf(frame_no));
                jTextField_ChannelNo.setText(String.valueOf(channel_no));
                jTextField_PositionNo.setText(String.valueOf(position_no));
                jTextField_SliceNo.setText(String.valueOf(slice_no));
                jComboBox_OutputVal.setSelectedItem(output);
                jComboBox_PrePost.setSelectedItem(prepost);
                jTextField_duration.setText(output_dur);
                if (output.equals(TTL.OutPut_SquareWave) && output_dur.equals("NA")) {
                    jTextField_duration.setText("10");
                }
            }
        }
    }
    
    void clearModel(DefaultTableModel tabModel) {
        int rows = tabModel.getRowCount();
        if (rows > 0) {
            for (int i = rows - 1; i > -1; i--) {
                tabModel.removeRow(i);
            }
        }
    }
    
    public void TTL_runnable_triggering(boolean isPre, int frame_no, int channel_no, int position_no, int slice_no, int cur_pin_no) {
        if (isPre) {
            TTLRunnable runn = new TTLRunnable(fa);
            runn.setFrameNum(frame_no);
            runn.setChannelNum(channel_no);
            runn.setPositionNum(position_no);
            runn.setSliceNum(slice_no);
            
            boolean isRunnableAdded = false;
            for (int pin_no=8; pin_no < 14; pin_no++) {
                String key = fa.getKeyBasedOnDim(frame_no, channel_no, position_no, slice_no, pin_no);
                if (fa.hm_runnableAddedMap.containsKey(key)) {
                    isRunnableAdded = true;
                    break;
                }
            }
            if (!isRunnableAdded) {
                fa.engineWrapper_.attachRunnable(frame_no, position_no, channel_no, slice_no, runn);
            }
            String key = fa.getKeyBasedOnDim(frame_no, channel_no, position_no, slice_no, cur_pin_no);
            fa.hm_runnableAddedMap.put(key, true);
        }
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox enabledCheckBox_;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JCheckBox jCheckBox_useWithOPS;
    private javax.swing.JComboBox jComboBox_OutputVal;
    private javax.swing.JComboBox jComboBox_OutputVal1;
    private javax.swing.JComboBox jComboBox_PinVal;
    private javax.swing.JComboBox jComboBox_PinVal1;
    private javax.swing.JComboBox jComboBox_PrePost;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem3;
    private javax.swing.JMenuItem jMenuItem4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField_ChannelNo;
    private javax.swing.JTextField jTextField_FrameNo;
    private javax.swing.JTextField jTextField_PositionNo;
    private javax.swing.JTextField jTextField_SliceNo;
    private javax.swing.JTextField jTextField_duration;
    private javax.swing.JTextField jTextField_duration1;
    // End of variables declaration//GEN-END:variables

}
