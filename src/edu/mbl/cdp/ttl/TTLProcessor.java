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
 * TTL plug-in for Micro-Manager
 * @author Amitabh Verma (averma@mbl.edu), Grant Harris (gharris@mbl.edu)
 * Marine Biological Laboratory, Woods Hole, Mass.
 * 
 */
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

public class TTLProcessor extends DataProcessor<TaggedImage> {

    CMMCore core_;
    TTL fa;

    public TTLProcessor(TTL fa, CMMCore core) {
        this.fa = fa;
        core_ = core;
    }

    @Override
    protected void process() {
        final TaggedImage taggedImage = poll();
        try {            
            
            if (taggedImage == null) { // EOL check
                produce(taggedImage);
                return;
            }
            if (TaggedImageQueue.isPoison(taggedImage)) { // EOL check
                produce(taggedImage);
                return;
            }   
            produce(taggedImage);
                          
            new Thread() {
                public void run() {
                    try {
                        JSONObject json = taggedImage.tags;
                        final int z = MDUtils.getSliceIndex(json);

                        if (z == 0) {
                            final int frame = MDUtils.getFrameIndex(json);
                            final int ch = MDUtils.getChannelIndex(json);
                            final int pos = MDUtils.getPositionIndex(json);
                            final int slice = MDUtils.getSliceIndex(json);
                            trigger(frame, ch, pos, slice, "Post");
                        }
                    } catch (Exception ex) {
                        ReportingUtils.logError(TTL.METADATAKEY + " : ERROR in Process: ");
                        ex.printStackTrace();
                    }
                }
            }.start();
                        
            if (fa.debugLogEnabled_) {
                core_.logMessage(TTL.METADATAKEY + " : exiting processor");
            }

        } catch (Exception ex) {
            ReportingUtils.logError(TTL.METADATAKEY + " : ERROR in Process: ");
            ex.printStackTrace();
            produce(taggedImage);
        }
    }

    public void trigger(int frame_no, int channel_no, int position_no, int slice_no, String isPrePost) {

        if (!fa.controlFrame_.isPluginEnabled()) {
            return;
        }
        
        try {
            for (int pin_no = 8; pin_no < 14; pin_no++) {
                    String key = fa.getKeyBasedOnDim(frame_no,channel_no,position_no,slice_no,pin_no);
                    
                    if (fa.hm.containsKey(key)) {

                        JSONObject json = (JSONObject) fa.hm.get(key);
                        boolean isEnabled = json.getBoolean(TTL.Key_IsEnabled);
                        String PrePost = json.getString(TTL.Key_PrePost);
                        
                        if (!PrePost.equals(isPrePost)) {
                            return;
                        }
                        
                        if (isEnabled) {
                            if (fa.debugLogEnabled_) {
                                ReportingUtils.logMessage(TTL.METADATAKEY + " : start triggering on frame,channel,position,slice - " + frame_no + "," + channel_no + "," + position_no + "," + slice_no + " pin - " + pin_no);
                            }

                            String output = json.getString(TTL.Key_Output);
                            String output_dur = json.getString(TTL.Key_OutputDuration);
                            
                            boolean triggerResult = triggerArduino(output, pin_no, output_dur);                            

                            if (isPrePost.equals("Pre") && triggerResult) {
                                Double d = core_.getDeviceDelayMs(TTL.DeviceLabel + "-Shutter");
                                Thread.sleep(d.intValue());
                            }

                            if (fa.debugLogEnabled_ && !triggerResult) {
                                ReportingUtils.logMessage(TTL.METADATAKEY + " : end triggering on frame,channel,position,slice - " + frame_no + "," + channel_no + "," + position_no + "," + slice_no + " pin - " + pin_no);
                            }
                        }
                    }                
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            ReportingUtils.logError(TTL.METADATAKEY + " : Error while triggering frame,channel,position,slice - " + frame_no + "," + channel_no + "," + position_no + "," + slice_no);
        }
    }
    
    public DataProcessor<TaggedImage> getDataProcessor() {
        return (DataProcessor<TaggedImage>) this;
    }
    
    public synchronized boolean triggerArduino(String output, int pin_no, String output_dur) {
        boolean bool = true;
        try {
            core_.setProperty(TTL.DeviceLabel + "-Switch", "State", Math.pow(2, pin_no - 8));
            if (output.equals(TTL.OutPut_High)) {
                core_.setProperty(TTL.DeviceLabel + "-Shutter", "OnOff", 1);
            } else if (output.equals(TTL.OutPut_Low)) {
                core_.setProperty(TTL.DeviceLabel + "-Shutter", "OnOff", 0);
            } else if (output.equals(TTL.OutPut_SquareWave)) {
                int output_dur_d = (int) Integer.parseInt(output_dur);
                core_.setProperty(TTL.DeviceLabel + "-Shutter", "OnOff", 0);
                Thread.sleep(output_dur_d);
                core_.setProperty(TTL.DeviceLabel + "-Shutter", "OnOff", 1);
                Thread.sleep(output_dur_d);
                core_.setProperty(TTL.DeviceLabel + "-Shutter", "OnOff", 0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            bool = false;
        }
        
        return bool;
    }
}
