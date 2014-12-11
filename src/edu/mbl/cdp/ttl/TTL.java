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

import static edu.mbl.cdp.ttl.TTLTagged.gui;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.MMOptions;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ReportingUtils;

public class TTL {

    static final String METADATAKEY = "TTL";
    CMMCore core_;
    AcquisitionWrapperEngine engineWrapper_;

    TTLProcessor processor;
    TTLControls controlFrame_;
    TTLTagged main_;
    OPS ops_;
    
    public static NumberFormat formatterD2 = new DecimalFormat("00.##");
    public static NumberFormat formatterD3 = new DecimalFormat("000.##");
    
    public boolean debugLogEnabled_ = false;
        
    static String DeviceLabel = "Arduino";
    
    static String OutPut_Low = "Low";
    static String OutPut_High = "High";
    static String OutPut_SquareWave = "Square Wave";
    
    final static String Key_FrameNo = "Key_Frame_No";
    final static String Key_ChannelNo = "Key_Channel_No";
    final static String Key_PositionNo = "Key_Position_No";
    final static String Key_SliceNo = "Key_Slice_No";
    final static String Key_PinNo = "Key_Pin_No";
    final static String Key_Output = "Key_Output";
    final static String Key_OutputDuration = "Key_OutputDuration";
    final static String Key_PrePost = "Key_PrePost";
    final static String Key_IsEnabled = "Key_IsEnabled";
    
    HashMap hm = new HashMap();  
    HashMap hm_runnableAddedMap = new HashMap();  
    
    boolean isPreModeEnabled = false;
    //boolean useWithOPS = true;
    
    Preferences PrefsRoot = Preferences.userNodeForPackage(TTL.class);
    Preferences PrefsAPIroot = PrefsRoot.node(PrefsRoot.absolutePath() + "/" + "TTLPrefs");
    Preferences PrefsTableVals = PrefsAPIroot.node(PrefsAPIroot.absolutePath() + "/" + "TableVals");

    public TTL(AcquisitionWrapperEngine engineWrapper, CMMCore core, TTLTagged main){

        engineWrapper_ = engineWrapper;
        core_ = core;
        main_ = main;
        
        getDebugOptions();

    }     
    
    public AcquisitionWrapperEngine getAcquisitionWrapperEngine() {
        AcquisitionWrapperEngine engineWrapper = null;
        
        if (useWithOPS()) {
            if (ops_ == null) {
                try {
                    ops_ = new OPS(this);
                    ops_.checkForOps();
                } catch (Exception ex) {
                    gui.showError("Pol-Acquisition (OpenPolScope) could not be located !");
                } 
            }
            if (ops_ != null) {
                try {
                    engineWrapper = ops_.getAcquisitionWrapperEngine();                
                } catch (Exception ex) {
                    gui.showError("Pol-Acquisition (OpenPolScope) could not be located !");
                }    
            }
            if (engineWrapper == null) {
                setUseWithOPS(false);                
                engineWrapper = (AcquisitionWrapperEngine) MMStudioMainFrame.getInstance().getAcquisitionEngine();
            }
        } else {        
            engineWrapper = (AcquisitionWrapperEngine) MMStudioMainFrame.getInstance().getAcquisitionEngine();            
        }
        
        return engineWrapper;
    }
        
    public void UpdateEngineAndCore() {
        engineWrapper_ = getAcquisitionWrapperEngine();
        core_ = TTLTagged.getCMMCore();
    }
    
    public void getDebugOptions() {
        Preferences root = Preferences.userNodeForPackage(MMOptions.class);
        Preferences prefs = root.node(root.absolutePath() + "/" + "MMOptions");      
        debugLogEnabled_ = prefs.getBoolean("DebugLog", debugLogEnabled_);
    }

    public void enable(boolean enableTriggering) {
        if (enableTriggering) {
            startProcessor();
        } else {
            // Disable
            stopAndClearProcessor();
//            stopAndClearRunnable();
        }
    }

    public void startProcessor() {
        try {
            attachDataProcessor();
        } catch (Exception ex) {
        }
    }

    public void attachDataProcessor() {
        processor = new TTLProcessor(this, core_);
        processor.setName(METADATAKEY);
                
        engineWrapper_.addImageProcessor(getDataProcessor());        
    }

    public void stopAndClearProcessor() {
        if (processor != null) {
            processor.requestStop();
        }
        try {
            engineWrapper_.removeImageProcessor(getDataProcessor());
            
            if (debugLogEnabled_) {
                ReportingUtils.logMessage(METADATAKEY + " : processor removed");
            }
        } catch (Exception ex) {
        }
    }

    public void stopAndClearRunnable() {
        try {
            engineWrapper_.clearRunnables();  // Potentially dangerous - needs specific clearRunnable(X)
            hm_runnableAddedMap.clear();
            if (debugLogEnabled_) {
                ReportingUtils.logMessage(METADATAKEY + " : runnable removed");
            }
        } catch (Exception ex) {
        }
    }
    
    public DataProcessor<TaggedImage> getDataProcessor() {
        return processor.getDataProcessor();
    }

    public JFrame getControlFrame() {
        if (controlFrame_ == null) {
            controlFrame_ = new TTLControls(this);
        }
        return controlFrame_;
    }           
    
    public boolean useWithOPS() {
        if (controlFrame_ != null) {
            return controlFrame_.isUseWithOPS();
        }
        return true;
    }
    
    public void setUseWithOPS(boolean bool) {
        //useWithOPS = bool;
                
        if (controlFrame_ != null) {
            controlFrame_.setUseWithOPS(bool);
        }
    }
    
    public void setEnabledUseWithOPS(boolean bool) {
        if (controlFrame_ != null) {
            controlFrame_.setEnabledUseWithOPS(bool);
        }
    }
    
    public String getKeyBasedOnDim(int frame_no, int channel_no, int position_no, int slice_no, int pin_no) {
        String key ="";
        key = String.valueOf(TTL.formatterD3.format(frame_no)) + "-" + String.valueOf(TTL.formatterD3.format(channel_no)) + "-" + String.valueOf(TTL.formatterD3.format(position_no)) + "-" + String.valueOf(TTL.formatterD3.format(slice_no)) + "-" + String.valueOf(TTL.formatterD2.format(pin_no));
        return key;
    }
    
}
