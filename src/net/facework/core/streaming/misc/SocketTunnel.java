package net.facework.core.streaming.misc;

////////////////////////////////////////////////////
//Caution: this class is DO not use ANYMORE////////
///////////////////////////////////////////////////
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;

import com.facework.configuration.ServerConf;

import net.facework.core.http.TinyHttpServer;
import net.facework.core.streaming.misc.RtspServer.CallbackListener;
import net.facework.core.streaming.transportPacketizer.AACADTSPacketizer;
import net.facework.core.streaming.transportPacketizer.AbstractPacketizer;
import net.facework.core.streaming.transportPacketizer.ECRtpV2H264Packetizer;
import net.facework.core.streaming.transportPacketizer.H264Packetizer;
import net.facework.core.streaming.transportPacketizer.ECRtpSocket;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/************************************************************************************************/
/**This class is used as a tunnel, redirect the data from the orginal port to the tunnel`s port
 * at each side of the tunnel we can modify the data we want to sent 
 * and the data we received
 * 
 * we carried the ip pocket inside the channel 
 ************************************************************************************************/



public class SocketTunnel extends Service {

	private final static String TAG = "SocketTunnel";
	
	/** Port used by default. */
	public static final int DEFAULT_Video_Tunnel_PORT = 3088;
	public static final int DEFAULT_Audio_Tunnel_PORT = 3089;
	private int mVideoPort = DEFAULT_Video_Tunnel_PORT;
	private int mAudioPort = DEFAULT_Audio_Tunnel_PORT;
	public static final int MTU = ServerConf.MTU;
	
	/** Port already in use. */
	public final static int ERROR_BIND_FAILED = 0x00;
	
	/** Key used in the SharedPreferences to store whether the Socket Tunnel server is enabled or not. */
	protected String mEnabledKey = "SocketTunnel_enabled";

	/** Key used in the SharedPreferences for the port used by the Socket Tunnel server. */
	protected String mVideoPortKey = "SocketTunnel_VideoPort";
	protected String mAudioPortKey = "SocketTunnel_AudioPort";
	
	protected boolean mEnabled = true, mRestart = false;
	private ChannelListener mVideoThread;
	private ChannelListener mAudioThread;
	private SharedPreferences mSharedPreferences;
	private final LinkedList<CallbackListener> mListeners = new LinkedList<CallbackListener>();
	
	private int number=ServerConf.XOR_REGION;// how many packets xor half to half 
	private int[] A =new int[number/2+1]; 
	private int[] B =new int[number/2+1];
	private int[] A_size=new int[number/2];
	private int[] B_size=new int[number/2];
	
	
	public SocketTunnel(){}
	public interface CallbackListener {

		/** Called when an error occurs. */
		void onError(SocketTunnel server, Exception e, int error);

	}
	/**
	 * See {@link TinyHttpServer.CallbackListener} to check out what events will be fired once you set up a listener.
	 * @param listener The listener
	 */
	public void addCallbackListener(CallbackListener listener) {
		synchronized (mListeners) {
			mListeners.add(listener);			
		}
	}
	public void removeCallbackListener(CallbackListener listener) {
		synchronized (mListeners) {
			mListeners.remove(listener);				
		}
	}
	protected void postError(Exception exception, int id) {
		synchronized (mListeners) {
			if (mListeners.size() > 0) {
				for (CallbackListener cl : mListeners) {
					cl.onError(this, exception, id);
				}
			}			
		}
	}
	private LocalBinder mBinder;
	public class LocalBinder extends Binder { 
        public SocketTunnel getService() { 
                return SocketTunnel.this; 
        } 
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return this.mBinder;
	}
	
	/** Starts (or restart if needed) the Socket Tunnel server. */
	public void start() {
		Log.i(TAG,"Socket Tunnel server is starting...");
		if (mRestart){ stop();Log.i(TAG,"Restart Socket Tunnel");}
		if (mEnabled && mVideoThread == null && mAudioThread == null) {
			try {
				mVideoThread = new ChannelListener("VIDEO");
				mAudioThread = new ChannelListener("AUDIO");
				Log.i(TAG,"Socket Tunnel server is started.");
			} catch (Exception e) {
				mVideoThread = null;
				mAudioThread = null;
				e.printStackTrace();
				Log.i(TAG,"Socket Tunnel is failed to create main thread");
			}
		}
		mRestart = false;
	}

	/** Stops the SocketTunnel server but not the service. */
	public void stop() {
		Log.i(TAG,"Socket Tunnel server is stopping...");
		if (mVideoThread != null && mAudioThread!= null) {
			try {
				mVideoThread.kill();
				mAudioThread.kill();
				Log.i(TAG,"Socket Tunnel server is stopped...");
			} catch (Exception e) {
			} finally {
				mVideoThread = null;
				mAudioThread = null;
			}
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	@Override
	public void onCreate() {

		// Let's restore the state of the service 
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mVideoPort = Integer.parseInt(mSharedPreferences.getString(mVideoPortKey, String.valueOf(mVideoPort)));
		mAudioPort = Integer.parseInt(mSharedPreferences.getString(mAudioPortKey, String.valueOf(mAudioPort)));
		mEnabled = mSharedPreferences.getBoolean(mEnabledKey, mEnabled);
		// If the configuration is modified, the server will adjust
		mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
		start();
	}

	@Override
	public void onDestroy() {
		stop();
		Log.i(TAG,"Socket Tunnel Destroied!");
		mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
	}

	private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

			if (key.equals(mVideoPortKey)) {
				int port = Integer.parseInt(sharedPreferences.getString(mVideoPortKey, String.valueOf(mVideoPort)));
				if (port != mVideoPort) {
					mVideoPort = port;
					mRestart = true;
					start();
				}
			}
			else if (key.equals(mAudioPortKey)) {
				int port = Integer.parseInt(sharedPreferences.getString(mAudioPortKey, String.valueOf(mAudioPort)));
				if (port != mAudioPort) {
					mAudioPort = port;
					mRestart = true;
					start();
				}
			}
			else if (key.equals(mEnabled)) {
				mEnabled = sharedPreferences.getBoolean(mEnabledKey, true);
				start();
			}
		}
	};
	
	class ChannelListener extends Thread implements Runnable {

		public static final int H264 = 0x1;
		public static final int H263 = 0x2;
		public static final int AAC = 0x3;
		public static final int AMR = 0x4;
		private final static int ECHeader = 7;
		private DatagramSocket mServer=null;
		private String flag;
		private DatagramPacket mPacket=null;
		private DatagramPacket mForwardPacket=null;
		private int orignalDestinationPort=0;
		private InetAddress orignalDestinationAdd=null;
		private byte[] buffer =new byte[MTU];
		private AbstractPacketizer mPacketizer = null;
		private static final int BUFFER_SIZE = 10*1024*MTU;
		private boolean isStart =false;
		private Send sendProcess =new Send();
		
		private byte [] dataToSend = null;
		
		private int dataLength=( MTU - ECHeader )*number;
		
		private long start=0;
		private long end=0;
		private long ts=0;
		
		private char pervious;
		
		//use local socket to transpot the data to the RTP packetizer.
		private LocalServerSocket mLss = null;
		private LocalSocket mReceiver, mSender = null;
		private OutputStream m_Send =null;
		public ChannelListener(String flag) throws IOException {
			try {
				this.flag=flag;
				if(this.flag=="VIDEO"){
					mServer = new DatagramSocket(mVideoPort);
					mServer.setReceiveBufferSize(BUFFER_SIZE);
					setUpRTPPacketizer(ChannelListener.H264);
				}
				else if(this.flag=="AUDIO"){
					mServer = new DatagramSocket(mAudioPort);
					mServer.setReceiveBufferSize(BUFFER_SIZE);
					setUpRTPPacketizer(ChannelListener.AAC);
				}
				mPacket = new DatagramPacket(buffer,MTU);
				/// local socket use to send the data to the RTP packetizer.
				mLss = new LocalServerSocket("net.facework.libTunnel-"+flag);
				mReceiver = new LocalSocket();
				mReceiver.connect( new LocalSocketAddress("net.facework.libTunnel-" + flag ) );
				mReceiver.setReceiveBufferSize(BUFFER_SIZE);  
				mReceiver.setSendBufferSize(BUFFER_SIZE);  
		        mSender = mLss.accept();  
		        mSender.setReceiveBufferSize(BUFFER_SIZE);  
		        mSender.setSendBufferSize(BUFFER_SIZE);  
		        m_Send = mSender.getOutputStream(); 
		        sendProcess.setIs(mReceiver.getInputStream());
				this.start();
				start= new Date().getTime();
			} catch (BindException e) {
				Log.e(TAG,"Port already in use !");
				throw e;
			}
		}
		public void setUpRTPPacketizer(int flag) throws IOException{
			switch(flag){
				// for now we just support the H264&AAC
				case H264: 
					this.mPacketizer=new H264Packetizer(); 
					this.mPacketizer.setFileMode();
					break;
				case H263: break;
				case AAC: 
					this.mPacketizer=new AACADTSPacketizer();
					this.mPacketizer.setFileMode();
					break;
				case AMR: break;
			}
		}
		private void send()throws IOException{
			m_Send.write(dataToSend,0,dataLength);
			m_Send.flush();
			//now=0;
			Log.d(TAG,"send to packetizer !");
			if(!isStart){
				//Log.d(TAG,"now Buffer Size"+ nowBufferSize );
				Log.d(TAG,"Start packetizer");
				mPacketizer.setInputStream(mReceiver.getInputStream());
				mPacketizer.start();
				isStart=true;
				
			}
		}
		@Override
		public void run() {
			Log.i(TAG,"socket server listening on port "+mServer.getLocalPort());
			
			
			for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
			int times=0;
			boolean isSent=true;
			dataToSend = new byte[MTU*number];
			// use for EC_RTP_V2 only
			int realSize=ECRtpV2H264Packetizer.MAXPACKETSIZE-ECRtpSocket.EC_HEADER_LENGTH;
			int bodySize=ECRtpV2H264Packetizer.MAXPACKETSIZE-ECRtpSocket.EC_HEADER_LENGTH-ECRtpSocket.RTP_HEADER_LENGTH;
			int allHeaderSize=ECRtpSocket.EC_HEADER_LENGTH+ECRtpSocket.RTP_HEADER_LENGTH;
			int orignalPacketSize;
			int ts=0;
			boolean complete=true;
			try {
				while (!Thread.interrupted()) {
					//if(!lossOrSend()){ continue;}//Log.i(TAG,"Data droped");}
					//Log.i(TAG,"Data send");
					mServer.receive(mPacket);
					//if(!lossOrSend()){ continue;}//Log.i(TAG,"Data droped");}
					/*if(end==0){
						end = new Date().getTime();
						ts = end-start;
						this.mPacketizer.setStartTs(ts);
					}*/
					buffer=mPacket.getData();
					byte[] ipAdd= new byte[4];
					
					// use the directly = to improve speed !
				
					if(ServerConf.TRANS==ServerConf.EC_RTP_V2)
					{
						/*ipAdd[0]=buffer[12];
						ipAdd[1]=buffer[13];
						ipAdd[2]=buffer[14];
						ipAdd[3]=buffer[15];*/
						int group= (buffer[18]>>6)&0x03;
						int index= buffer[18]&0x3F;
						//Log.i(TAG,""+AbstractPacketizer.toHexString(buffer,0,MTU));
						/*if(group!=2)
						{
							orignalDestinationAdd=InetAddress.getByAddress(ipAdd);
							orignalDestinationPort=buffer[16]<<8 | (buffer[17]&0xFF);
						}*/
						//Log.i(TAG,"orignal address:"+orignalDestinationAdd+":"+orignalDestinationPort);
						
						orignalPacketSize= ((buffer[19]&0xFF)<<8) | (buffer[20]&0xFF);
						if(group==0){
							if(isSent) isSent=false;
							else{
								
							}
							//Log.i(TAG,"received EC packet A"+index);
							/*//save the header
							System.arraycopy(buffer,0,dataToSend,index*(realSize),ECRtpSocket.RTP_HEADER_LENGTH);
							//save the body we don`t need the EC header any more
							System.arraycopy(buffer,ECRtpSocket.RTP_HEADER_LENGTH+ECRtpSocket.EC_HEADER_LENGTH,
											dataToSend,index*(realSize)+ECRtpSocket.RTP_HEADER_LENGTH,realSize-ECRtpSocket.RTP_HEADER_LENGTH);
							*/
							System.arraycopy(buffer,0,dataToSend,index*(ECRtpV2H264Packetizer.MAXPACKETSIZE),ECRtpV2H264Packetizer.MAXPACKETSIZE);
							A[index]=1;
							A[number/2]++;
							Log.i(TAG,"Total A:"+A[number/2]);
							//A_size[index]=orignalPacketSize;
							pervious='A';
							//Log.i(TAG,"A orign Size:"+A_size[index]);

						}
						else if(group==1){// Receive B simple to the buffer
							if(isSent) isSent=false;
							//Log.i(TAG,"received EC packet B"+index);
							/*//save the header
							System.arraycopy(buffer,0,dataToSend,(index+number/2)*(realSize),ECRtpSocket.RTP_HEADER_LENGTH);
							//save the body we don`t need the EC header any more
							System.arraycopy(buffer,ECRtpSocket.RTP_HEADER_LENGTH+ECRtpSocket.EC_HEADER_LENGTH,
									dataToSend,(index+number/2)*(realSize)+ECRtpSocket.RTP_HEADER_LENGTH,realSize-ECRtpSocket.RTP_HEADER_LENGTH);
							*/
							System.arraycopy(buffer,0,dataToSend,(index+number/2)*(ECRtpV2H264Packetizer.MAXPACKETSIZE),ECRtpV2H264Packetizer.MAXPACKETSIZE);
							B[index]=1;
							B[number/2]++;
							Log.i(TAG,"Total B:"+B[number/2]);
							B_size[index]=orignalPacketSize;
							pervious='B';
							//Log.i(TAG,"receive B["+index+"]"+AbstractPacketizer.toHexString(buffer,0,ECRtpV2H264Packetizer.MAXPACKETSIZE));

						}
						else if(group==2){// Receive C check which one need to be restore
							pervious='C';
							//Log.i(TAG,"received EC packet C"+index);
							if(isSent){continue;}// have been sent drop C
							else if(A[number/2]+B[number/2]==number){
								Log.i(TAG,"cache forward out");
								//Send A out
								m_Send.write(dataToSend,0,ECRtpV2H264Packetizer.MAXPACKETSIZE*number);
								m_Send.flush();
								if(times==2){
									sendProcess.open();
								}
								else{
									times++;
								}
								/*for(int i=0;i<number/2;i++){
									byte[] sendData= new byte[A_size[i]];
									System.arraycopy(dataToSend,i*realSize,sendData,0,A_size[i]);
									mForwardPacket=new DatagramPacket(sendData,A_size[i]);
									mForwardPacket.setAddress(orignalDestinationAdd);
									mForwardPacket.setPort(orignalDestinationPort);
									mServer.send(mForwardPacket);
									//Log.i(TAG,"send A "+i+" to "+orignalDestinationAdd+":"+orignalDestinationPort);
									//Log.i(TAG,"orginal size :"+A_size[i]);
									//Log.i(TAG,"sending A["+i+"]"+AbstractPacketizer.toHexString(mForwardPacket.getData(),0,mForwardPacket.getLength()));
								}
								
								//Send B out
								for(int i=number/2;i<number;i++){
									byte[] sendData= new byte[B_size[i-number/2]];
									System.arraycopy(dataToSend,i*realSize,sendData,0,B_size[i-number/2]);
									mForwardPacket=new DatagramPacket(sendData,B_size[i-number/2]);
									mForwardPacket.setAddress(orignalDestinationAdd);
									mForwardPacket.setPort(orignalDestinationPort);
									mServer.send(mForwardPacket);
									//Log.i(TAG,"send B"+(i-number/2)+" to "+orignalDestinationAdd+":"+orignalDestinationPort);
									//Log.i(TAG,"sending B["+i+"]"+AbstractPacketizer.toHexString(mForwardPacket.getData(),0,mForwardPacket.getLength()));
								}*/
								//init A B
								for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
								isSent=true;
							}
							else if((A[index]^B[index])==0){
								if(A[index]==1)
									continue;// all fine drop C
								else if(A[index]==0){
									// do something here we lost A B, we just have c
									// we will lose data
								}
							}
							else if((A[index]^B[index])==1 && (A[index]>B[index])){
								//C^A => restore B
								Log.i(TAG,"RESTORE B");
								for(int i=0;i<bodySize;i++){
									dataToSend[(index+number/2)*(ECRtpV2H264Packetizer.MAXPACKETSIZE)+allHeaderSize+i]=(byte) (dataToSend[index*(ECRtpV2H264Packetizer.MAXPACKETSIZE)+allHeaderSize+i]^buffer[allHeaderSize+i]);
								}
								B[index]=1;
								B[number/2]++;
								if(A[number/2]+B[number/2]==number){
									m_Send.write(dataToSend,0,ECRtpV2H264Packetizer.MAXPACKETSIZE*number);
									m_Send.flush();
									if(times==2){
										sendProcess.open();
									}
									else{
										times++;
									}
									//init A B
									for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
									isSent=true;
								}
							}
							else if((A[index]^B[index])==1 && (A[index]<B[index])){
								//C^B => restore A
								Log.i(TAG,"RESTORE A");
								for(int i=0;i<MTU-ECHeader;i++){
									dataToSend[index*(ECRtpV2H264Packetizer.MAXPACKETSIZE)+allHeaderSize+i]=(byte) (dataToSend[(index+number/2)*(ECRtpV2H264Packetizer.MAXPACKETSIZE)+allHeaderSize+i]^buffer[allHeaderSize+i]);
								}
								A[index]=1;
								A[number/2]++;
								if(A[number/2]+B[number/2]==number){
									m_Send.write(dataToSend,0,ECRtpV2H264Packetizer.MAXPACKETSIZE*number);
									m_Send.flush();
									if(times==2){
										sendProcess.open();
									}
									else{
										times++;
									}
									//init A B
									for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
									isSent=true;
								}
							}
							
							
						}
						
					}
					else if(ServerConf.TRANS==ServerConf.EC_RTP){
						ipAdd[0]=buffer[0];
						ipAdd[1]=buffer[1];
						ipAdd[2]=buffer[2];
						ipAdd[3]=buffer[3];
						System.arraycopy(buffer,0,ipAdd,0,4);
						orignalDestinationAdd=InetAddress.getByAddress(ipAdd);
						orignalDestinationPort=buffer[5]&0xFF | (buffer[4]&0xFF)<<8;
						this.mPacketizer.setDestination(orignalDestinationAdd, orignalDestinationPort);
						/*******************************
						 * 7th byte in the header
						 *
						 *   ____group A=00 B=01 C=02
						 *  |		 ______index max:63
						 *  |		|
						 * 00 0000000
						 * 
						 *******************************/
						int group= (buffer[6]>>6)&0x03;
						int index= buffer[6]&0x3F;
						//Log.i(TAG,"buffer[6]"+buffer[6]+" group:"+group);
						if(group==0){// Receive A simple to the buffer
							Log.i(TAG,"received A"+index);
							if(isSent) isSent=false;
							else if(!isSent && (A[number/2]!=0||B[number/2]!=0)){
							}
							//if(A[index]==1) continue;
							System.arraycopy(buffer,ECHeader,dataToSend,index*(MTU-ECHeader),MTU-ECHeader);
							A[index]=1;
							A[number/2]++;
							pervious='A';
						}
						else if(group==1){// Receive B simple to the buffer
							Log.i(TAG,"received B"+index);
							//if(B[index]==1) continue;
							System.arraycopy(buffer,ECHeader,dataToSend,(index+number/2)*(MTU-ECHeader),MTU-ECHeader);
							B[index]=1;
							B[number/2]++;
							pervious='B';
						}
						else if(group==2){// Receive C check which one need to be restore
							pervious='C';
							Log.i(TAG,"received C"+index);
							//decode :
							/*****************************************************************
							 * 
							 * A 	1 1 1 0 1 4
							 * 
							 * B 	1 0 1 1 1 3
							 * 
							 * A^B	0 1 0 1 0
							 * 
							 * A[C_index]^B[C_index]==1 && A[C_index]>B[C_index] C ^ A ==> B
							 * A[C_index]^B[C_index]==1 && A[C_index]<B[C_index] C ^ B ==> A
							 * A[C_index]^B[C_index]==0 drop C
							 * 
							 *****************************************************************/
							if(isSent){continue;}// have been sent drop C
							else if(A[number/2]+B[number/2]==number){
								send();
								//init A B
								for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
								isSent=true;
							}
							else if((A[index]^B[index])==0){
								if(A[index]==1)
									continue;// all fine drop C
								else if(A[index]==0){
									// do something here we lost A B, we just have c
									// we will lose data
								}
							}
							else if((A[index]^B[index])==1 && (A[index]>B[index])){
								//C^A => restore B
								Log.i(TAG,"RESTORE B");
								for(int i=0;i<MTU-ECHeader;i++){
									dataToSend[(index+number/2)*(MTU-ECHeader)+i]=(byte) (dataToSend[index*(MTU-ECHeader)+i]^buffer[ECHeader+i]);
								}
								B[index]=1;
								B[number/2]++;
								if(A[number/2]+B[number/2]==number){
									send();
									//init A B
									for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
									isSent=true;
								}
							}
							else if((A[index]^B[index])==1 && (A[index]<B[index])){
								//C^B => restore A
								Log.i(TAG,"RESTORE A");
								for(int i=0;i<MTU-ECHeader;i++){
									dataToSend[index*(MTU-ECHeader)+i]=(byte) (dataToSend[(index+number/2)*(MTU-ECHeader)+i]^buffer[ECHeader+i]);
								}
								A[index]=1;
								A[number/2]++;
								if(A[number/2]+B[number/2]==number){
									send();
									//init A B
									for(int i=0;i<number/2+1;i++){A[i]=0;B[i]=0;}
									isSent=true;
								}
							}
						}
					}
					
				}	
				
			} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
			Log.i(TAG,"Socket Tunnel server stopped !");
		}

		public void kill() {
			
			mPacketizer.stop();
			try {
				this.join();
			} catch (InterruptedException ignore) {}
			mServer.close();
		}
		private boolean lossOrSend(){
			// true ===>send 
			// false ===>loss
			Random rdm = new Random(System.nanoTime());
			int loss= ServerConf.CLIENT_LOSS;
			if(loss==0){
				return true;
			}
			for(int i=1;i<=loss;i++)
			{
				if(Math.abs(rdm.nextInt()%100+1)==1)
					return false;
			}
			
			return true;
		}
		public void updateTimestamp(long timestamp) {
			setLong(timestamp, 4, 8);
		}
		private void setLong(long n, int begin, int end) {
			for (end--; end >= begin; end--) {
				buffer[end] = (byte) (n % 256);
				n >>= 8;
			}
		}
	}
	class Send extends Thread implements Runnable{
		public Send(){
			//this.start();
		}
		private InputStream is =null;
		public void setIs(InputStream _is){this.is=_is;}
		private boolean isStart =false;
		private byte[] buffer= new byte[MTU];
		private DatagramPacket mForwardPacket=null;
		private DatagramSocket mServer;
		private int RTP_HEADER_LENGTH=ECRtpSocket.RTP_HEADER_LENGTH;
		private int EC_HEADER_LENGTH= ECRtpSocket.EC_HEADER_LENGTH;
		private byte[] ipAdd= new byte[4];
		
		public void open(){
			if(isStart==false){
				try {
					mServer=new DatagramSocket();
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				this.start();
				isStart=true;
			}
		}
		
		@Override
		public void run() {
			try {
				while (!Thread.interrupted()){
					
					//read the RTP header
					fill(0,RTP_HEADER_LENGTH);
					//read the EC RTP header
					fill(RTP_HEADER_LENGTH,EC_HEADER_LENGTH);
					
					//read orignal address and port
					ipAdd[0]=buffer[12];
					ipAdd[1]=buffer[13];
					ipAdd[2]=buffer[14];
					ipAdd[3]=buffer[15];
					InetAddress orignalDestinationAdd = InetAddress.getByAddress(ipAdd);
					int orignalDestinationPort = buffer[16]<<8 | (buffer[17]&0xFF);
					
					Log.i(TAG,"orignal Address:"+orignalDestinationAdd+":"+orignalDestinationPort);
					//read orignal size
					int orignalPacketSize = ((buffer[19]&0xFF)<<8) | (buffer[20]&0xFF);
					//Log.i(TAG,"orignal size: "+orignalPacketSize);
					//read all data
					fill(RTP_HEADER_LENGTH,orignalPacketSize-RTP_HEADER_LENGTH);
					
					//send them out!
					mForwardPacket=new DatagramPacket(buffer,orignalPacketSize);
					mForwardPacket.setAddress(orignalDestinationAdd);
					mForwardPacket.setPort(orignalDestinationPort);
					//Log.i(TAG,"sending "+AbstractPacketizer.toHexString(mForwardPacket.getData(),0,mForwardPacket.getLength()));
					mServer.send(mForwardPacket);
					
					
					//skip the left 0
					skip(ECRtpV2H264Packetizer.MAXPACKETSIZE-(orignalPacketSize+EC_HEADER_LENGTH));
					
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//
			
		}
		private int fill(int offset,int length) throws IOException {

			int sum = 0, len;

			while (sum<length) {
				len = is.read(buffer, offset+sum, length-sum);
				if (len<0) {
					throw new IOException("End of stream");
				}
				else sum+=len;
			}

			return sum;

		}
		private long skip(long number) throws IOException{
			long sum=0; long len;
			while(sum<number){
				len=is.skip(number-sum);
				if(len<0){
					throw new IOException("End of stream");
				}
				else sum+=len;
			}
			return sum;
		}
	}
}
