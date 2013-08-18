package net.facework.core.streaming;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.Random;

import com.facework.mp4.MP4Info;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import net.facework.core.streaming.rtp.AbstractPacketizer;

/**
 * this class used to make the Spydroid RTSP server support mp4 file 
 **/
public abstract class FileMediaStream implements Stream {
	
	public final static int VIDEO = 0x01;
	public final static int AUDIO = 0x02;
	public final static String TAG = "FileMediaStream";
	protected boolean mStreaming = false;
	protected AbstractPacketizer mPacketizer = null;
	protected File file = null;
	public void setFile(File _f){ file=_f;}
	protected int modeFlag;
	protected static MP4Info mp4;
	public void setMP4Info(MP4Info _mp4){ mp4=_mp4;}
	private LocalServerSocket mLss = null;
	private LocalSocket mReceiver, mSender = null;
	private int mSocketId;
	private boolean running =false; 
	private static final int BUFFER_SIZE = 384000;
	
	private byte[] tsMarkNal= new byte[9];
	// add this nal to the H264 video steam
	// byte 0,1,2,3 use as the length of the NAL
	// byte 4 use as the header of the NAL
	// byte 5,6,7,8 use to carry ts
	
	/**
	 * Returns the packetizer associated with the {@link MediaStream}.
	 * @return The packetizer
	 */
	public AbstractPacketizer getPacketizer() { 
		return mPacketizer;
	}
	
	Thread local_send = new Thread (){  

		// sepreat video and auido send to the packetizer
		
		 /*private void findNextStart() throws IOException{
			 if(modeFlag==VIDEO){
				 raf.seek(videoOffset);
				 raf.read(videoBuffer,0,4);
			 }
			 else if(modeFlag==AUDIO){
				 
			 }
		 }
		 private void findNextLast()throws IOException{
			 if(modeFlag==VIDEO){
				 
			 }
			 else if(modeFlag==AUDIO){
				 
			 }
		 }*/
	    @Override
		public void run() {  
	        OutputStream m_Send = null;  
	        try {
	        	tsMarkNal[0]=0;
	        	tsMarkNal[1]=0;
	        	tsMarkNal[2]=0;
	        	tsMarkNal[3]=(byte) Integer.parseInt("00001001",2);
	        	int fps=30;
	        	int ts=0;
	        	int totalFrame=1;
	        	int ps = 90000/fps*185/100;
	        	///////////special nal header//////////
	        	///set type =30; we use this one to carry the time stamp///
	        	tsMarkNal[4]=(byte) Integer.parseInt("00011110",2);
	        	
	        	///timestamp///
	        	tsMarkNal[5]=0;
	        	tsMarkNal[6]=0;
	        	tsMarkNal[7]=0;
	        	tsMarkNal[8]=0;
	        	
	        	
	        	RandomAccessFile raf=new RandomAccessFile(file,"r");
	            m_Send = mSender.getOutputStream();  
	            long videoCount=mp4.videoCount;
	   		 	long audioCount=mp4.audioCount;
	   			//long videoReadCount=0;
	   		 	//long audioReadCount=0;
	   			long videoOffset=mp4.videoOffset;
	   		 	long audioOffset=mp4.audioOffset;
	   		 	long start;
	   		 	long end;
	   		 	long audioSampleOffset[]=new long[100];
	   		 	byte[] ADTSHeader=new byte[7];
	   		 	byte[] videoBuffer=new byte[4];
	   		 	byte[] audioBuffer=new byte[4];
	            raf.seek(videoOffset);
            	raf.read(videoBuffer,0,4);
            	raf.seek(audioOffset);
            	raf.read(audioBuffer,0,4);
            	Log.d(TAG,"video first:"+MP4Info.toHexString(videoBuffer,0,videoBuffer.length)+" long:"+mp4.bufferToLong(videoBuffer));
            	Log.d(TAG,"audio first:"+MP4Info.toHexString(audioBuffer,0,audioBuffer.length)+" long:"+mp4.bufferToLong(audioBuffer));
            	
            		if(modeFlag==VIDEO)
            		{	
            			Log.d(TAG,"start video Streaming");
            			start=mp4.bufferToLong(videoBuffer);
            			//Log.d(TAG,"start"+MP4Info.toHexString(videoBuffer,0,videoBuffer.length));
            			for(int i=1;i<audioCount;i++){
            				if(mp4.bufferToLong(videoBuffer)<mp4.bufferToLong(audioBuffer))
		            			for(int j=1;j<videoCount;j++){
		                        	if(mp4.bufferToLong(videoBuffer)>mp4.bufferToLong(audioBuffer)){
		                        		end=mp4.bufferToLong(audioBuffer);
		                        		//Log.d(TAG,"start:"+start);
		                            	//Log.d(TAG,"end:"+end);
		                        		int length=(int)(end-start);
		                        		byte[] data;
		                        		mSender.setSendBufferSize(BUFFER_SIZE);  
		             	                mSender.setReceiveBufferSize(BUFFER_SIZE); 
		             	                raf.seek(start);
		             	                m_Send.write(tsMarkNal);  
		             	                m_Send.flush();
		                        		if(length<BUFFER_SIZE){
		                        			//Log.d(TAG,"length:"+length);
		                        			data=new byte[length];
			             	                raf.read(data,0,length);
			             	                m_Send.write(data);  
			             	                m_Send.flush();
		                        		}
		                        		else{
		                        			data=new byte[BUFFER_SIZE];
		                        			for(int sum=length,i1=0;sum>0;sum=length-BUFFER_SIZE*i1,i1++){
		                        				if(sum<BUFFER_SIZE){
		                        					raf.read(data,0,sum);
		                        				}
		                        				else{
		                        					raf.read(data,0,BUFFER_SIZE);
		                        				}
		                        				raf.read(data,0,data.length);
				             	                m_Send.write(data);  
				             	                m_Send.flush(); 
		                        			}
		                        		}
		                        		//Log.d(TAG,"data"+MP4Info.toHexString(data,0,data.length));
		                        		break;
		                        	}
		                        	else{
		                        		videoOffset+=4;
			            				raf.seek(videoOffset);
			                        	raf.read(videoBuffer,0,4);
			                        	//Log.d(TAG,"now: "+MP4Info.toHexString(videoBuffer,0,videoBuffer.length));
			                        	tsMarkNal[5] = (byte)((ts >> 24) & 0xFF);
			                        	tsMarkNal[6] = (byte)((ts >> 16) & 0xFF);
			                        	tsMarkNal[7] = (byte)((ts >> 8) & 0xFF); 
			                        	tsMarkNal[8] = (byte)(ts & 0xFF);
			                        	ts+=ps;
		                        	}
		                        	
		            			}
	            			start=mp4.bufferToLong(videoBuffer);
	            			audioOffset+=4;
	            			raf.seek(audioOffset);
	                    	raf.read(audioBuffer,0,4);
	                    	
	            		}
            		}
            		else if(modeFlag==AUDIO){
            			Log.d(TAG,"start audio Streaming");
            			ADTSHeader[0]=(byte)0xFF;
            			ADTSHeader[1]=(byte)0xF1;
            			ADTSHeader[2]=(byte)0x4C;
            			ADTSHeader[6]=(byte)0xFC;
            			int innerSampleCount=0;
            			start=mp4.bufferToLong(audioBuffer);
            			//Log.d(TAG,"start"+MP4Info.toHexString(audioBuffer,0,audioBuffer.length));
            			for(int i=1;i<videoCount;i++){
            				if(mp4.bufferToLong(audioBuffer)<mp4.bufferToLong(videoBuffer)){
            					for(int j=1;j<audioCount;j++){
		                        	if(mp4.bufferToLong(audioBuffer)>mp4.bufferToLong(videoBuffer)){
		                        		end=mp4.bufferToLong(videoBuffer);
		                        		innerSampleCount--;
		                        		audioSampleOffset[innerSampleCount]=end;
		                        		//Log.d(TAG,"innerSampleCount: "+innerSampleCount);
		                        		for(int w=0;w<=innerSampleCount;w++){
		                        			long length = 0;
		                        			if(audioSampleOffset[innerSampleCount]==0&&innerSampleCount!=0) break;
		                        			else{
		                        				//Log.d(TAG,"w "+w);
		                        				//Log.d(TAG,"start "+start);
		                        				//Log.d(TAG,"audioSampleOffset[w] "+audioSampleOffset[w]);
		                        				length=(audioSampleOffset[w]-start);
		                        				
		                        				//Log.d(TAG,"end "+end);
		                        				//Log.d(TAG,"audioSampleOffset[w]-start "+length);
		                        			}
			                        		byte[] lengthToByte=new byte[4];
			                        		lengthToByte[0]=(byte)(((length+7) & 0xFF000000) >>24);
			                        		lengthToByte[1]=(byte)(((length+7) & 0x00FF0000) >>16);
			                        		lengthToByte[2]=(byte)(((length+7) & 0x0000FF00) >>8);
			                        		lengthToByte[3]=(byte)((length+7) & 0x000000FF);
			                        		//Log.d(TAG,"lengthToByte: "+ MP4Info.toHexString(lengthToByte,0,4)+" length: "+length);
			                        		ADTSHeader[3]=(byte) (0x80 | (lengthToByte[2]>>3) & 0x03 );
			                        		ADTSHeader[4]=(byte) (((lengthToByte[3] >>3)&0x1F) | (( lengthToByte[2]& 0x07) << 5));
			                        		ADTSHeader[5]=(byte) (((lengthToByte[3] & 0x07) << 5 )| 0x1F);
			                        		//Log.d(TAG,"ADTs Header: "+ MP4Info.toHexString(ADTSHeader,0,7));
			                        		m_Send.write(ADTSHeader);  
			             	                //m_Send.flush();
			                        		byte[] data;
			                        		mSender.setSendBufferSize(BUFFER_SIZE);  
			             	                mSender.setReceiveBufferSize(BUFFER_SIZE); 
			             	                raf.seek(start);
			                        		if(length+7<BUFFER_SIZE){
			                        			//Log.d(TAG,"length:"+length);
			                        			data=new byte[(int)length];
				             	                raf.read(data,0,(int)length);
				             	                m_Send.write(data);  
				             	                //m_Send.flush();
			                        		}
			                        		else{
			                        			data=new byte[BUFFER_SIZE];
			                        			for(long sum=length,i1=0;sum>0;sum=length-BUFFER_SIZE*i1,i1++){
			                        				if(sum<BUFFER_SIZE){
			                        					raf.read(data,0,(int)sum);
			                        				}
			                        				else{
			                        					raf.read(data,0,BUFFER_SIZE);
			                        				}
			                        				raf.read(data,0,data.length);
					             	                m_Send.write(data);  
					             	                m_Send.flush();
			                        			}
			                        		}
			                        		//Log.d(TAG,"data"+MP4Info.toHexString(data,0,data.length));
			                        		start=audioSampleOffset[w];
			                        		audioSampleOffset[w]=0;
		                        		}
		                        		innerSampleCount=0;
		                        		break;
		                        	}
		                        	else{
		                        		audioOffset+=4;
			            				raf.seek(audioOffset);
			                        	raf.read(audioBuffer,0,4);
			                        	//Log.d(TAG,"now: "+MP4Info.toHexString(videoBuffer,0,videoBuffer.length));
			                        	audioSampleOffset[innerSampleCount]=mp4.bufferToLong(audioBuffer);
			                        	innerSampleCount++;
			                        	//Log.d(TAG,"innerSampleCount: "+innerSampleCount);
		                        	}
		                        	
		            			}
            				}
	            			start=mp4.bufferToLong(audioBuffer);
	            			videoOffset+=4;
	            			raf.seek(videoOffset);
	                    	raf.read(videoBuffer,0,4);
	            		}
            		}
	            m_Send.close();  
	            mSender.close();  
	        } catch (IOException e) {  
	        	e.printStackTrace();  
	        }
	    }  
	};  
	
	public FileMediaStream(){
		for (int i=0;i<10;i++) {
			try {
				mSocketId = new Random().nextInt();
				mLss = new LocalServerSocket("net.facework.librtp-"+mSocketId);
				break;
			} catch (IOException e1) {}
		}
	}
	
	@Override
	public void start() throws IllegalStateException {
		// start streaming, make the file stream to the packetizer.
		mStreaming = true;
		try {
			//InputStream in = new FileInputStream(file);
			mPacketizer.setInputStream(mReceiver.getInputStream());
			mPacketizer.start();
		} catch (FileNotFoundException e) {
			throw new IllegalStateException("File not found,Start failed");
		} catch (IOException e) {
			throw new IllegalStateException("Start failed");
		}
	}

	@Override
	public void prepare() throws IllegalStateException, IOException {
		// TODO Auto-generated method stub
		mReceiver = new LocalSocket();
		mReceiver.connect( new LocalSocketAddress("net.facework.librtp-" + mSocketId ) );
		mReceiver.setReceiveBufferSize(BUFFER_SIZE);  
		mReceiver.setSendBufferSize(BUFFER_SIZE);  
        mSender = mLss.accept();  
        mSender.setReceiveBufferSize(BUFFER_SIZE);  
        mSender.setSendBufferSize(BUFFER_SIZE);  
        running = true;
        Log.d(TAG,"starting"+this.modeFlag+" streaming");
        new Thread (local_send).start();
	}

	@Override
	public void stop() {
		mPacketizer.stop();
		mStreaming = false;
	}

	@Override
	public void release() {
		// TODO Auto-generated method stub
		
	}

	/** Sets the destination UDP packets will be sent to. **/
	@Override
	public void setDestination(InetAddress dest, int dport) {
		this.mPacketizer.setDestination(dest, dport);
	}

	/** 
	 * Sets the Time To Live of the underlying {@link net.facework.core.streaming.rtp.RtpSocket}. 
	 * @throws IOException 
	 **/
	@Override
	public void setTimeToLive(int ttl) throws IOException {
		this.mPacketizer.setTimeToLive(ttl);
	}

	/** Gets the destination port of the stream. */
	@Override
	public int getDestinationPort() {
		return this.mPacketizer.getRtpSocket().getPort();
	}

	/** Gets the source port of UDP packets. */
	@Override
	public int getLocalPort() {
		return this.mPacketizer.getRtpSocket().getLocalPort();
	}

	/**
	 * Returns the SSRC of the underlying {@link net.facework.core.streaming.rtp.RtpSocket}.
	 * @return the SSRC of underlying RTP socket
	 */
	@Override
	public int getSSRC() {
		return getPacketizer().getRtpSocket().getSSRC();
	}


	@Override
	public abstract String generateSessionDescription()  throws IllegalStateException, IOException;

	@Override
	public boolean isStreaming() {
		return mStreaming;
	}

}
