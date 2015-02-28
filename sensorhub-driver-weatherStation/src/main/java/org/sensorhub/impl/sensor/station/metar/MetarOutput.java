/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
The Initial Developer is Sensia Software LLC. Portions created by the Initial
Developer are Copyright (C) 2014 the Initial Developer. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.station.metar;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Time;

import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.station.Station;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataRecordImpl;
import org.vast.data.QuantityImpl;
import org.vast.data.TextEncodingImpl;
import org.vast.data.TextImpl;
import org.vast.data.TimeImpl;
import org.vast.swe.SWEConstants;

/**
 * 
 * @author Tony Cook
 *
 *  	DataPoller queries station service at POLLING_INTERVAL and checks for new record.  
 *      If record time is greater than latestRecord.time, we update latestRecord and latestBlock
 *      and send event to bus.  
 */

public class MetarOutput extends AbstractSensorOutput<MetarSensor> //extends StationOutput
{
    private static final Logger log = LoggerFactory.getLogger(MetarOutput.class);
    DataComponent metarRecordStruct;
    DataBlock latestBlock;
    MetarDataRecord latestRecord;
    MetarDataPoller metarPoller = new MetarDataPoller();
    private static final long POLLING_INTERVAL_SECONDS = 120;
    private static final int AVERAGE_SAMPLING_PERIOD = (int)TimeUnit.MINUTES.toSeconds(20);
    protected Timer timer;

    public MetarOutput(MetarSensor parentSensor)
    {
        super(parentSensor);
    }


    @Override
    public String getName()
    {
        return "MetarWeatherStation";
    }


    protected void init()
    {
        // SWE Common data structure
        metarRecordStruct = new DataRecordImpl(13);
        metarRecordStruct.setName(getName());
        metarRecordStruct.setDefinition("http://sensorml.com/ont/swe/property/Weather/MetarStationRecord");
        
        Time c1 = new TimeImpl();
        c1.getUom().setHref(Time.ISO_TIME_UNIT);
        c1.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
        metarRecordStruct.addComponent("time", c1);

        Quantity c;
        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/Temperature");
        metarRecordStruct.addComponent("temperature", c);  

        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/DewPoint"); //  does not resolve
        metarRecordStruct.addComponent("dewpoint", c);  

        c = new QuantityImpl();
        c.getUom().setCode("degF");
        c.setDefinition("http://sensorml.com/ont/swe/property/HumidityValue"); 
        metarRecordStruct.addComponent("relativeHumidity", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("in_i'Hg");
        c.setDefinition("http://sensorml.com/ont/swe/property/AirPressureValue.html"); 
        metarRecordStruct.addComponent("pressure", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("mi_i/h");
        c.setDefinition("http://sensorml.com/ont/swe/property/WindSpeed"); 
        metarRecordStruct.addComponent("windSpeed", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("deg");
        c.setDefinition("http://sensorml.com/ont/swe/property/WindDirectionAngle"); 
        metarRecordStruct.addComponent("windDirection", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("mi_i/h");
        c.setDefinition("http://sensorml.com/ont/swe/property/WindGust"); //  not there
        metarRecordStruct.addComponent("windGust", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("in");
        c.setDefinition("http://sensorml.com/ont/swe/property/Precipitation.html"); 
        metarRecordStruct.addComponent("hourlyPrecipitation", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("ft_i");
        c.setDefinition("http://sensorml.com/ont/swe/property/TopCloudHeightDimension.html"); 
        metarRecordStruct.addComponent("cloudCeiling", c);  
        
        c = new QuantityImpl();
        c.getUom().setCode("ft_i");
        c.setDefinition("http://sensorml.com/ont/swe/property/Visibility");   // does not resolve
        metarRecordStruct.addComponent("visibility", c);  
        
        TextImpl t = new TextImpl();  
        t.setDefinition("http://sensorml.com/ont/swe/property/presentWeather");   // does not resolve
        metarRecordStruct.addComponent("presentWeather", t); 
        
        t = new TextImpl();
        t.setDefinition("http://sensorml.com/ont/swe/property/skyConditions");   // does not resolve
        metarRecordStruct.addComponent("skyConditions", t); 
    }


    private DataBlock recordToBlock(MetarDataRecord rec)
    {
    	DataBlock dataBlock = metarRecordStruct.createDataBlock();
        Station stn = rec.getStation();
        dataBlock.setDoubleValue(0, rec.getTimeUtc()/1000.); 
        dataBlock.setDoubleValue(1, rec.getTemperature()); 
        dataBlock.setDoubleValue(2, rec.getDewPoint()); 
        dataBlock.setDoubleValue(3, rec.getRelativeHumidity()); 
        dataBlock.setDoubleValue(4, rec.getPressure()); 
        dataBlock.setDoubleValue(5, rec.getWindSpeed()); 
        dataBlock.setDoubleValue(6, rec.getWindDirection()); 
        dataBlock.setDoubleValue(7, rec.getWindGust()); 
        dataBlock.setDoubleValue(8, rec.getHourlyPrecip()); 
        dataBlock.setIntValue(9, rec.getCloudCeiling()); 
        dataBlock.setIntValue(10, rec.getVisibility()); 
        dataBlock.setStringValue(11, rec.getPresentWeather()); 
        dataBlock.setStringValue(12, rec.getSkyConditions()); 
        
        return dataBlock;
    }


    protected void start()
    {
        if (timer != null)
            return;
        timer = new Timer();
        
        // start main measurement generation thread
        TimerTask task = new TimerTask() {
            public void run()
            {
            	MetarDataRecord rec = metarPoller.pullStationData();
            	if(latestRecord == null || rec.getTimeUtc() > latestRecord.getTimeUtc()) {
            		latestRecord = rec;  
            		latestBlock = recordToBlock(rec);
            		eventHandler.publishEvent(new SensorDataEvent((double)rec.getTimeUtc(), MetarOutput.this, latestBlock));
            	}
            }            
        };
        
        timer.scheduleAtFixedRate(task, 0, TimeUnit.SECONDS.toMillis(POLLING_INTERVAL_SECONDS));        
    }


    protected void stop()
    {
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
    }


    @Override
    public double getAverageSamplingPeriod()
    {
        return AVERAGE_SAMPLING_PERIOD;
    }


    @Override 
    public DataComponent getRecordDescription()
    {
        return metarRecordStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return new TextEncodingImpl(",", "\n");
    }


    @Override
    public DataBlock getLatestRecord()
    {
        return latestBlock;
    }
    
    
    @Override
    public double getLatestRecordTime()
    {
        if (latestBlock != null)
            return latestBlock.getDoubleValue(0);
        
        return Double.NaN;
    }

}
