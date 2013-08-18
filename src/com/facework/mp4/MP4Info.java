package com.facework.mp4;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.util.Base64;
import android.util.Log;

public class MP4Info {
	public final static String TAG = "MP4Info";
	public final static int FILE_NOT_FOUND = 404;
	public final static int OK = 200;
	public final static int FILE_ERROR = 500;
	
	public long mdatSize;
	public long mdatOffset;
	public long videoOffset;
	public long videoCount;
	public long audioOffset;
	public long audioCount;
	public String audioConfig;
	public long samplingRate;
	private long time=0;
	public long getTime(){
		return time;
	}
	private boolean gotInfo=false;
	private int offset=0;
	private File f;
	
	private byte[] spsData;
	private int spsDataLength=0;
	private byte[] ppsData;
	private int ppsDataLength=0;
	
	private byte[] samplingRateByte=new byte[4];
	private byte[] videoType=new byte[4];
	private byte[] audioType=new byte[4];
	private byte[] timescale=new byte[4];
	private byte[] duration=new byte[4];
	private byte[] buffer=new byte[4];
	private byte[] video=null;
	private byte[] audio=null;
	private boolean videoFound=false;
	private boolean audioFound=false;
	private byte[] nowBoxName=new byte[4]; //which box  we are in
	private long nowBoxSize;	// How that box big
	private long nowBoxRead;	// How many bytes we have read;
	private InputStream is;
	private long audioTimeConut;
	private Object auidoTimeOffset;
	public MP4Info(File _f){
		this.f=_f;
	}
	public int getInfo(){
		if(gotInfo){return OK;}
		try {
			this.is = new FileInputStream(this.f);
			Log.d(TAG,"file:"+this.f.getPath());
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			Log.e(TAG,"file not found");
			return FILE_NOT_FOUND;
		}
		analyseTrak(); 
		gotInfo=true;
		return OK;
	}
	private int analyseTrak(){
		try {
			this.find("moov");
			this.find("mvhd");
			this.skip(12);
			this.fill(this.timescale,0,4);
			this.fill(this.duration,0,4);
			long timeScale=this.bufferToLong(this.timescale);
			long timeDuration=this.bufferToLong(this.duration);
			Log.d(TAG,"found time scale:"+toHexString(timescale,0,4));
			Log.d(TAG,"found duration:"+toHexString(duration,0,4));
			Log.d(TAG,"found time scale:"+timeScale);
			Log.d(TAG,"found duration:"+timeDuration);
			float timeSecond=timeDuration/timeScale;
			this.time=(long) (timeSecond*1000);
			Log.d(TAG,"found time:"+time);
			this.skipNowBox();
			for(int i=0;i<2;i++){
				this.find("trak");
				this.find("mdia");
				this.find("mdhd");
				this.skip(12);
				this.fill(this.samplingRateByte,0,4);
				this.skipNowBox();
				this.find("hdlr");
				this.skip(8);
				this.fill(buffer,0,4);
				if(buffer[0]=='v'&&buffer[1]=='i'&&buffer[2]=='d'&&buffer[3]=='e'){
					Log.d(TAG,"video");
					this.videoFound=true;
					this.skipNowBox();
					this.find("minf");
					this.find("stbl");
					this.find("stsd");
					this.skip(12);
					this.fill(this.videoType,0,4);
					if(this.videoType[0]=='a'&&this.videoType[1]=='v'&&this.videoType[2]=='c'&&this.videoType[3]=='1'){
						this.skip(86);//to avcC;
						//this.find("avcC");
						this.skip(5);
						this.fill(buffer,0,1);
						int spsNumber= buffer[0]&0x1F;
						Log.d(TAG,"sps number:"+spsNumber);
						this.fill(buffer,0,2);
						this.spsDataLength= (buffer[0]&0xFF)<<2 | (buffer[1]&0xFF);
						this.spsData=new byte[this.spsDataLength*spsNumber];
						Log.d(TAG,"sps Length:"+spsDataLength);
						for(int i1=0;i1<spsNumber;i1++){
							this.fill(this.spsData,i1*spsDataLength,spsDataLength);
							Log.d(TAG,"sps data:"+toHexString(this.spsData,0,this.spsData.length));
						}
						this.fill(buffer,0,1);
						int ppsNumber=buffer[0]&0xFF;
						this.fill(buffer,0,2);
						this.ppsDataLength=(buffer[0]&0xFF)<<2 | (buffer[1]&0xFF);
						this.ppsData=new byte[this.ppsDataLength*ppsNumber];
						for(int i1=0;i1<ppsNumber;i1++){
							this.fill(this.ppsData,i1*ppsDataLength,ppsDataLength);
							Log.d(TAG,"pps data:"+toHexString(this.ppsData,0,this.ppsData.length));
						}
					}
					this.skipNowBox();
					this.find("stco");
					this.skip(4);
					this.fill(buffer,0,4);
					this.videoOffset=this.offset;
					Log.d(TAG,"video trak offset:"+this.videoOffset);
					this.videoCount=bufferToLong(buffer);
					this.skipNowBox();
				}
				else if(buffer[0]=='s'&&buffer[1]=='o'&&buffer[2]=='u'&&buffer[3]=='n'){
					this.audioFound=true;
					this.samplingRate=this.bufferToLong(samplingRateByte);
					Log.d(TAG,"sound");
					this.skipNowBox();
					this.find("minf");
					this.find("stbl");
					this.find("stsd");
					this.skip(12);
					this.fill(this.audioType,0,4);
					if(this.audioType[0]=='m'&&this.audioType[1]=='p'&&this.audioType[2]=='4'&&this.audioType[3]=='a'){
						this.skip(32);
						//enter esds
						this.skip(8);
						while(true)
						{
							this.fill(this.buffer,0,1);
							if(this.buffer[0]==0x05){
								break;
							}
						}
						this.skip(1);
						this.fill(buffer,0,2);
						this.audioConfig=MP4Info.toHexString(buffer,0,2);
						Log.d(TAG,"audio config:"+audioConfig);
					}
					this.skipNowBox();
					this.find("stts");
					this.skip(4);
					this.fill(buffer,0,4);
					this.audioTimeConut=this.bufferToLong(buffer);
					this.auidoTimeOffset=this.offset;
					this.skipNowBox();
					this.find("stco");
					this.skip(4);
					this.fill(buffer,0,4);
					this.audioOffset=this.offset;
					Log.d(TAG,"audio trak offset:"+this.videoOffset);
					this.audioCount=bufferToLong(buffer);
					this.skipNowBox();
				}
			}
			return OK;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return FILE_ERROR;
		}
		
	}
	private void find(String _boxName) throws IOException{
		char [] string=_boxName.toCharArray();
		Log.d(TAG,"finding "+_boxName);
		while(true){
			readNextBox();
			Log.d(TAG,"now box :"+(char)this.nowBoxName[0]+(char)this.nowBoxName[1]+(char)this.nowBoxName[2]+(char)this.nowBoxName[3]);
			Log.d(TAG,"now box Size: "+this.nowBoxSize);
			if(this.nowBoxName[0]==(byte)string[0]&&this.nowBoxName[1]==(byte)string[1]&&this.nowBoxName[2]==(byte)string[2]&&this.nowBoxName[3]==(byte)string[3])
				break;
			skipNowBox();
			
		}
	}
	private void fill(byte[] _buffer,int offset,int length) throws IOException{
		this.is.read(_buffer,offset,length);
		this.nowBoxRead+=length;
		this.offset+=length;
	}
	private void readNextBox() throws IOException{
		this.nowBoxRead=0;
		this.fill(buffer,0,4);
		this.nowBoxSize=buffer[3]&0xFF | (buffer[2]&0xFF)<<8 | (buffer[1]&0xFF)<<16 | (buffer[0]&0xFF)<<24;
		this.fill(this.nowBoxName,0,4);
		if(this.nowBoxName[0]=='m'&&this.nowBoxName[0]=='d'&&this.nowBoxName[0]=='a'&&this.nowBoxName[0]=='t'){
			this.mdatSize=this.nowBoxSize;
			this.mdatOffset=this.offset;
		}
	}
	private void skipNowBox() throws IOException{
		//Log.d(TAG,"now box read: "+this.nowBoxRead);
		this.skip(this.nowBoxSize-this.nowBoxRead);
	}
	private void skip(long length) throws IOException{
		this.is.skip(length);
		this.nowBoxRead+=length;
		this.offset+=length;
		//Log.d(TAG,"skipped"+ length +" byte");
	}
	public long bufferToLong(byte[] _buffer){
		return _buffer[3]&0xFF | (_buffer[2]&0xFF)<<8 | (_buffer[1]&0xFF)<<16 | (_buffer[0]&0xFF)<<24;
	}
	public static String toHexString(byte[] buffer,int start, int len) {
		String c;
		StringBuilder s = new StringBuilder();
		for (int i=start;i<start+len;i++) {
			c = Integer.toHexString(buffer[i]&0xFF);
			s.append( c.length()<2 ? "0"+c : c );
		}
		return s.toString();
	}
	public  String getB64PPS() {
		return Base64.encodeToString(this.ppsData, 0, this.ppsDataLength, Base64.NO_WRAP);
	}

	public  String getB64SPS() {
		return Base64.encodeToString(this.spsData, 0, this.spsDataLength, Base64.NO_WRAP);
	}
	public String getProfileLevel() {
		return toHexString(this.spsData,1,3);
	}

}
