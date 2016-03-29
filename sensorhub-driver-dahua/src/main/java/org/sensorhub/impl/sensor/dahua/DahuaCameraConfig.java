/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
The Initial Developer is Botts Innovative Research Inc.. Portions created by the Initial
Developer are Copyright (C) 2014 the Initial Developer. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.dahua;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.impl.comm.URLConfig;
import org.sensorhub.impl.sensor.rtpcam.RTSPConfig;
import org.sensorhub.impl.sensor.videocam.BasicVideoConfig;
import org.sensorhub.impl.sensor.videocam.VideoResolution;


/**
 * <p>
 * Configuration parameters for Dahua cameras
 * </p>
 * 
 * @author Mike Botts <mike.botts@botts-inc.com>
 * @since March 2016
 */
public class DahuaCameraConfig extends SensorConfig
{	
    @DisplayInfo(label="Video", desc="Video settings")
    public BasicVideoConfig video = new BasicVideoConfig();
    
    @DisplayInfo(label="Network", desc="Network configuration")
    public URLConfig net = new URLConfig();
    
    @DisplayInfo(label="RTP/RTSP", desc="RTP/RTSP configuration")
    public RTSPConfig rtsp = new RTSPConfig();
    
	// TODO: Set variable for mounting (top up, top down, top sideways) or better set mounting angles relative to NED/ENU
    // ALSO, use flip=yes/no to flip image if necessary	
	

    public enum Resolution implements VideoResolution
    {
        D1("D1", 704, 480),
        HD_720P("HD", 1280, 720),
        HD_1080P("Full HD", 1920, 1080);
        
        private String text;
        private int width, height;
        
        private Resolution(String text, int width, int height)
        {
            this.text = text;
            this.width = width;
            this.height = height;
        }
        
        public int getWidth() { return width; };
        public int getHeight() { return height; };
        public String toString() { return text + " (" + width + "x" + height + ")"; }
    };
    
    
    public DahuaCameraConfig()
    {
        // default params for Dahua
        video.resolution = Resolution.HD_720P;
        rtsp.rtspPort = 554;
        rtsp.videoPath = "/cam/realmonitor?channel=1&subtype=0";
        rtsp.localUdpPort = 20000;
    }
}
