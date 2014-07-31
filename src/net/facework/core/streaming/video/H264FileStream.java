package net.facework.core.streaming.video;

import java.io.File;
import java.io.IOException;

import com.facework.configuration.ServerConf;

import android.content.SharedPreferences;
import android.util.Log;
//import android.content.SharedPreferences.Editor;

import net.facework.core.streaming.FileMediaStream;
import net.facework.core.streaming.transportPacketizer.ECRtpPacketizer;
import net.facework.core.streaming.transportPacketizer.ECRtpV2H264Packetizer;
import net.facework.core.streaming.transportPacketizer.H264Packetizer;

public class H264FileStream extends FileMediaStream{
	//static private SharedPreferences settings = null;
	public H264FileStream(File f){
		super();
		try {
			if(ServerConf.TRANS==ServerConf.EC_RTP)
			{
				this.mPacketizer= new ECRtpPacketizer();
				if(ServerConf.TUNNEL==ServerConf.ON){
					this.mPacketizer.setTunnelChannel("video");
				}
			}
			else if(ServerConf.TRANS==ServerConf.RTP){
				this.mPacketizer = new H264Packetizer();
				this.mPacketizer.setFileMode();
				
			}
			else if(ServerConf.TRANS==ServerConf.EC_RTP_V2){
				this.mPacketizer = new ECRtpV2H264Packetizer();
				this.mPacketizer.setFileMode();
			}
			else{
				this.mPacketizer = new H264Packetizer();
				this.mPacketizer.setFileMode();
			}
			//this.mPacketizer.setMaxFrame(mp4.videoCount);
			this.modeFlag=VIDEO;
			//Boolean checking=MP4SpsPps.checkMP4_MOOV(this.file);
			//Log.i("H26FileStream","checking..."+checking.toString()+"..."+this.file.exists()+"..."+this.file.isFile()+"..."+this.file.isDirectory());
			/*if (settings != null) {
				Editor editor = settings.edit();
				editor.putString("fileConfig", mMp4Config.getProfileLevel()+","+mMp4Config.getB64SPS()+","+mMp4Config.getB64PPS());
				editor.commit();
			}*/
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.e("H26FileStream","there is an error");
		}
		
	}
	/**
	 * When start() is called, the SPS and PPS parameters used by the phone are determined and
	 * can be stored if this method has been called at some point.
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	static public void setPreferences(SharedPreferences prefs) {
		//settings = prefs;
	}
	@Override
	public String generateSessionDescription() throws IllegalStateException,
			IOException {
		String profile="",sps="",pps="";
		profile = FileMediaStream.mp4.getProfileLevel();
		pps = FileMediaStream.mp4.getB64PPS();
		sps = FileMediaStream.mp4.getB64SPS();
		long time = FileMediaStream.mp4.getTime();
		return "t=0 0\r\n" +
				"a=range:npt=0-"+time+".0\r\n"+
				"a=recvonly\r\n" +
			"m=video "+String.valueOf(getDestinationPort())+" RTP/AVP 96\r\n" +
			"a=range:npt=0-"+time+".0\r\n"+
			"a=range:length="+time+".0\r\n"+
			"b=RR:0\r\n" +
			"a=rtpmap:96 H264/90000\r\n" +
			"a=fmtp:96 packetization-mode=1;profile-level-id="+profile+";sprop-parameter-sets="+sps+","+pps+";\r\n";
	}
	

}
