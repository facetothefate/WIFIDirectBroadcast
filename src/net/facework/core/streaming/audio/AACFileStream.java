package net.facework.core.streaming.audio;

import java.io.IOException;
import java.util.Date;

import com.facework.configuration.ServerConf;

import net.facework.core.streaming.FileMediaStream;
import net.facework.core.streaming.transportPacketizer.AACADTSPacketizer;
import net.facework.core.streaming.transportPacketizer.ECRtpPacketizer;

public class AACFileStream extends FileMediaStream {

	public final static String TAG = "AACStream";

	/** MPEG-4 Audio Object Types supported by ADTS. **/
	private static final String[] sAudioObjectTypes = {
		"NULL",							  // 0
		"AAC Main",						  // 1
		"AAC LC (Low Complexity)",		  // 2
		"AAC SSR (Scalable Sample Rate)", // 3
		"AAC LTP (Long Term Prediction)"  // 4	
	};

	/** There are 13 supported frequencies by ADTS. **/
	private static final int[] sADTSSamplingRates = {
		96000, // 0
		88200, // 1
		64000, // 2
		48000, // 3
		44100, // 4
		32000, // 5
		24000, // 6
		22050, // 7
		16000, // 8
		12000, // 9
		11025, // 10
		8000,  // 11
		7350,  // 12
		-1,   // 13
		-1,   // 14
		-1,   // 15
	};

	/** Default sampling rate. **/
	private int mRequestedSamplingRate = 16000;
	private int mActualSamplingRate = 16000;
	public AACFileStream() throws IOException {
		super();
		AACADTSPacketizer packetizer = new AACADTSPacketizer();
		packetizer.setSamplingRate((int)mp4.samplingRate);
		/*if(ServerConf.TRANS==ServerConf.EC_RTP)
		{
			this.mPacketizer= new ECRtpPacketizer();
			if(ServerConf.TUNNEL==ServerConf.ON){
				this.mPacketizer.setTunnelChannel("audio");
			}
		}
		else if(ServerConf.TRANS==ServerConf.RTP){
			this.mPacketizer = packetizer;
			this.mPacketizer.setFileMode();
		}*/
		this.mPacketizer = packetizer;
		this.mPacketizer.setFileMode();
		this.mPacketizer.setStartTs(new Date().getTime());
		this.modeFlag=AUDIO;
		for(int i=0;i<sADTSSamplingRates.length;i++){
			if(mp4.samplingRate==sADTSSamplingRates[i]){
				
			}
		}
		
	}

	public void setAudioSamplingRate(int samplingRate) {
		mRequestedSamplingRate = samplingRate;
	}

	@Override
	public String generateSessionDescription() {
		long time = FileMediaStream.mp4.getTime();
		return "m=audio "+String.valueOf(getDestinationPort())+" RTP/AVP 96\r\n" +
				"b=RR:0\r\n" +
				"a=range:npt=0-"+time+".0\r\n"+
				"a=range:length="+time+".0\r\n"+
				"a=rtpmap:96 mpeg4-generic/"+mp4.samplingRate+"\r\n" + // sADTSSamplingRates[mSamplingRateIndex]
				"a=fmtp:96 streamtype=5; profile-level-id=41; mode=AAC-hbr; config="+mp4.audioConfig+";objectType=64; constantDuration=1023; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n";
	}
}
