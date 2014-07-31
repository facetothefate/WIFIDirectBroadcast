package net.facework.core.streaming.misc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import com.facework.configuration.ServerConf;
import com.facework.tool.CPUMeasurement;

import net.facework.core.http.TinyHttpServer;
import net.facework.core.streaming.misc.SocketTunnel.CallbackListener;
import net.facework.core.streaming.misc.SocketTunnel.ChannelListener;
import net.facework.core.streaming.misc.SocketTunnel.LocalBinder;
import net.facework.core.streaming.misc.SocketTunnel.Send;
import net.facework.core.streaming.transportPacketizer.AbstractPacketizer;
import net.facework.core.streaming.transportPacketizer.ECRtpSocket;
import net.facework.core.streaming.transportPacketizer.ECRtpV2H264Packetizer;
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

public class ECRTP2RTPTunnel extends Service {

private final static String TAG = "ECRTP2RTPTunnel";
	
	/** Port used by default. */
	public static final int DEFAULT_Video_Tunnel_PORT = 3088;
	public static final int DEFAULT_Audio_Tunnel_PORT = 3089;
	private int mVideoPort = DEFAULT_Video_Tunnel_PORT;
	private int mAudioPort = DEFAULT_Audio_Tunnel_PORT;
	public static final int MTU = ServerConf.MTU;
	
	/** Port already in use. */
	public final static int ERROR_BIND_FAILED = 0x00;
	
	/** Key used in the SharedPreferences to store whether the Socket Tunnel server is enabled or not. */
	protected String mEnabledKey = "ECRTP2RTPTunnel_enabled";

	/** Key used in the SharedPreferences for the port used by the Socket Tunnel server. */
	protected String mVideoPortKey = "ECRTP2RTPTunnel_VideoPort";
	protected String mAudioPortKey = "ECRTP2RTPTunnel_AudioPort";
	
	protected boolean mEnabled = true, mRestart = false;
	private Receiver mVideoThread;
	private Receiver mAudioThread;
	private SharedPreferences mSharedPreferences;
	private final LinkedList<CallbackListener> mListeners = new LinkedList<CallbackListener>();
	
	/** used to computing loss percent**/
	private int lossCount=0;
	private int totalReceive=0;
	private int totalLoss=0;

	public interface CallbackListener {

		/** Called when an error occurs. */
		void onError(ECRTP2RTPTunnel ecrtp2rtpTunnel, Exception e, int error);

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
        public ECRTP2RTPTunnel getService() { 
                return ECRTP2RTPTunnel.this; 
        } 
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return this.mBinder;
	}
	
	/** Starts (or restart if needed) the Socket Tunnel server. */
	public void start() {
		Log.i(TAG,"ECRTP2RTP Tunnel is starting...");
		if (mRestart){ stop();Log.i(TAG,"Restart ECRTP2RTP Tunnel");}
		if (mEnabled && mVideoThread == null && mAudioThread == null) {
			try {
				mVideoThread = new Receiver("VIDEO");
				mAudioThread = new Receiver("AUDIO");
				Log.i(TAG,"ECRTP2RTP Tunnel is started.");
			} catch (Exception e) {
				mVideoThread = null;
				mAudioThread = null;
				e.printStackTrace();
				Log.i(TAG,"ECRTP2RTP Tunnel is failed to create main thread");
			}
		}
		mRestart = false;
	}

	/** Stops the SocketTunnel server but not the service. */
	public void stop() {
		Log.i(TAG,"ECRTP2RT Tunnel is stopping...");
		if (mVideoThread != null && mAudioThread!= null) {
			try {
				mVideoThread.kill();
				mAudioThread.kill();
				Log.i(TAG,"ECRTP2RT Tunnel is stopped...");
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
	
	//this thread used to receive the ECRTP/RTP packets
	class Receiver extends Thread implements Runnable{
		
		//used for comunication with ECdecode thread
		private LocalServerSocket mLss = null;
		private LocalSocket mReceiver, mSender = null;
		private OutputStream m_Send =null;
		
		//the UDP socket
		private MulticastSocket mServer=null;
		
		private String flag;
		private static final int BUFFER_SIZE = 10*1024*MTU;
		private int timeout;
		
		//this L1 cache is used for cache and order the packets received
		private int cachePacketsNumber=(ServerConf.ADDTIONAL_PACKETS_NUMBER+ServerConf.BLOCK_SIZE);
		private byte[] l1Cache=new byte[MTU*cachePacketsNumber];
		private byte[] l1CacheWaitToSend=new byte[MTU*cachePacketsNumber];
		private boolean sendSet=false;
		
		//this array is used to record which packet is cached
		private int isPacketCached[]=new int[cachePacketsNumber];
		
		//use to store a packet
		private DatagramPacket mPacket=null;
		private byte[] buffer =new byte[MTU];
		
		//use to measure wheather it will be output
		private int loop=0;
		
		//decoder thread.
		private ECdecoder decodeThread = new ECdecoder();

		public Receiver(String flag)throws IOException{
			Log.i(TAG,"CPU usage:"+CPUMeasurement.getAppCpuTime()+";"+CPUMeasurement.getTotalCpuTime());
			this.flag=flag;
			//init the store area for a packet;
			mPacket = new DatagramPacket(buffer,MTU);
			//Start the local socket
			mLss = new LocalServerSocket("net.facework.libTunnel-"+flag);
			mReceiver = new LocalSocket();
			mReceiver.connect( new LocalSocketAddress("net.facework.libTunnel-" + flag ) );
			mReceiver.setReceiveBufferSize(BUFFER_SIZE);  
			mReceiver.setSendBufferSize(BUFFER_SIZE);  
	        mSender = mLss.accept();  
	        mSender.setReceiveBufferSize(BUFFER_SIZE);  
	        mSender.setSendBufferSize(BUFFER_SIZE);  
	        m_Send = mSender.getOutputStream(); 
	        
	        if(this.flag=="VIDEO"){
				mServer = new MulticastSocket(mVideoPort);
				Log.i(TAG,"new video channel Receiver");
				mServer.setReceiveBufferSize(BUFFER_SIZE);
				//timeout=90000/30*100;
			}
			else if(this.flag=="AUDIO"){
				mServer = new MulticastSocket(mAudioPort);
				mServer.setReceiveBufferSize(BUFFER_SIZE);
			}
	        this.start();
		}
		private boolean lossOrSend(){
			// true ===>send 
			// false ===>loss
			int loss= ServerConf.CLIENT_LOSS*100;
			if(loss==0){
				return true;
			}
			Random rdm = new Random(System.nanoTime());
			boolean[] rad=new boolean[10000];
			for(int i=0;i<10000;i++)
			{
				if(i<loss){
					rad[i]=false;
				}
				else
					rad[i]=true;
			}
			int index=rdm.nextInt(9999);
			return rad[index];
		}
		
		@Override
		public void run(){
			Log.i(TAG,"Receiver started, listened on "+mServer.getLocalPort());
			int seq;
			int timestamp;
			int perTS=0;
			boolean isFirst=true;
			try {
				while (!Thread.interrupted()) {
					//read the packet
					buffer=new byte[MTU]; 
					mPacket.setData(buffer);
					mServer.receive(mPacket);
					//loss simulation
					if(!lossOrSend()){ 
						continue;
					}
					totalReceive++;
					buffer=mPacket.getData();
					//Log.i(TAG,""+AbstractPacketizer.toHexString(buffer,0,MTU));
					//read the seq number and timestamp
					//Log.d(TAG,"buffer[2]:"+buffer[2]+",buffer[3]:"+buffer[3]);
					seq=((buffer[2]&0xFF)<<8) | (buffer[3]&0xFF);
					//Log.d(TAG,"packet seq:"+seq);
					timestamp=(buffer[4]<<24)&0xFF | (buffer[5] <<16&0xFF) | (buffer[6]<<8&0xFF) | (buffer[7]&0xFF); 
					
					//
					if(isFirst){
						perTS=timestamp;
						loop=seq/cachePacketsNumber;
						decodeThread.setIs(mReceiver.getInputStream());
						decodeThread.start();
						isFirst=false;
					}
					if(true){
						int index=seq%cachePacketsNumber;
						//Log.d(TAG,"packet index:"+index);
						int nowLoop=seq/cachePacketsNumber;
						if(nowLoop==loop&&isPacketCached[index]==0){
							System.arraycopy(buffer,0,l1Cache,index*mPacket.getLength(),MTU);
							isPacketCached[index]=1;
						}
						else if(nowLoop>loop){
							if(sendSet){
								m_Send.write(l1CacheWaitToSend);
								m_Send.flush();
								sendSet=false;
								l1CacheWaitToSend=new byte[MTU*cachePacketsNumber];
							}
							if(!sendSet){
								System.arraycopy(l1Cache,0,l1CacheWaitToSend,0,MTU*cachePacketsNumber);
								sendSet=true;
							}
							/*m_Send.write(l1Cache);
							m_Send.flush();*/
							loop++;
							//renew l1Cache  
							isPacketCached=new int[cachePacketsNumber];
							l1Cache=new byte[MTU*cachePacketsNumber];
							System.arraycopy(buffer,0,l1Cache,index*mPacket.getLength(),MTU);
							isPacketCached[index]=1;
						}else if(nowLoop==loop-1){
							System.arraycopy(buffer,0,l1CacheWaitToSend,index*mPacket.getLength(),MTU);
							isPacketCached[index]=1;
						}
						// for the packet late than cachePacketsNumber we see them as timeout
						
					}

				}
			}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Log.i(TAG,"Receiver server stopped !");
		}
		public void kill(){
			try {
				this.join();
				Thread.sleep(50);
			} catch (InterruptedException ignore) {}
			mServer.close();
			decodeThread.kill();
		}
		
	}
	
	
	//this thread used to decode the ECRTP packets to RTP packets
	class ECdecoder extends Thread implements Runnable{
		
		private InputStream is =null;
		
		
		//send thread
		private Sender sendProcess = new Sender();
		
		//used for comunication with the Send thread
		private LocalServerSocket mLss = null;
		private LocalSocket mReceiver, mSender = null;
		private OutputStream m_Send =null;
		
		//size of the sending cache
		private static final int BUFFER_SIZE = 10*1024*MTU;
		
		//total send out packets
		private int totalNumber=ServerConf.ADDTIONAL_PACKETS_NUMBER+ServerConf.BLOCK_SIZE;
		
		//use for decoding
		private int blockSize=ServerConf.BLOCK_SIZE;
		private int[] A =new int[blockSize/2+1]; 
		private int[] B =new int[blockSize/2+1];
		private int packetHeader=ECRtpSocket.EC_HEADER_LENGTH+ECRtpSocket.RTP_HEADER_LENGTH;
		private byte[] dataToSend =new byte[blockSize*MTU];
		
		//temp window buffer
		private byte[] buffer= new byte[totalNumber*MTU];
		
		public void setIs(InputStream _is){this.is=_is;}
		public void kill() {
			try {
				this.join();
				mSender.close();
				mReceiver.close();
			} 
			catch (InterruptedException ignore) {
				
			} 
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			sendProcess.kill();
		}
		public ECdecoder() throws IOException{
			//Start the local socket
			Random rdm = new Random(System.nanoTime());
			int flag=rdm.nextInt();
			mLss = new LocalServerSocket("net.facework.libTunnelDecoder"+flag);
			mReceiver = new LocalSocket();
			mReceiver.connect( new LocalSocketAddress("net.facework.libTunnelDecoder" + flag ) );
			mReceiver.setReceiveBufferSize(BUFFER_SIZE);  
			mReceiver.setSendBufferSize(BUFFER_SIZE);  
	        mSender = mLss.accept();  
	        mSender.setReceiveBufferSize(BUFFER_SIZE);  
	        mSender.setSendBufferSize(BUFFER_SIZE);  
	        m_Send = mSender.getOutputStream(); 
	        sendProcess.setIs(mReceiver.getInputStream());
		}
		@Override
		public void run(){
			
			try{
				int blockNo=0;
				while (!Thread.interrupted()) {
					
					fill(0,MTU*totalNumber);
					A=new int[blockSize/2+1];
					B=new int[blockSize/2+1];
					
					//read one block of packets
					for(int i=0;i<totalNumber;i++){
						//read the group number
						int index_A=buffer[18+i*MTU];
						int index_B=buffer[19+i*MTU];
						//Log.i(TAG,"i:"+i);
						//this is a A packet
						if(index_A!=0&&index_B==0){
							//send to the Sender
							//Log.i(TAG,"A:"+index_A);
							int seq=blockNo*blockSize+(index_A-1);
							buffer[2+i*MTU] =(byte) (seq>>8);
							buffer[3+i*MTU] =(byte) (seq&0xFF);
							System.arraycopy(buffer,i*MTU,dataToSend,(index_A-1)*MTU,MTU);
							A[index_A]=1;
							A[0]++;
							//Log.i(TAG,"Total A:"+A[0]);
						}
						//this is a B packet
						else if(index_B!=0&&index_A==0){
							//Log.i(TAG,"B:"+index_B);
							int seq=blockNo*blockSize+(index_B-1+blockSize/2);
							buffer[2+i*MTU] =(byte) (seq>>8);
							buffer[3+i*MTU] =(byte) (seq&0xFF);
							System.arraycopy(buffer,i*MTU,dataToSend,(index_B-1+blockSize/2)*MTU,MTU);
							B[index_B]=1;
							B[0]++;
							//Log.i(TAG,"Total B:"+B[0]);
						}
						// this is a C packet
						else if(index_A!=0&&index_B!=0){
							//decode anything new out from C,
							//if there is nothing new simplly drop C
							//Log.i(TAG,"C["+index_A+"]["+index_B+"]");
							if(A[0]+B[0]==blockSize){
								//no need to restore; just drop to next block;
								//Log.i(TAG,"no loss,no need to restore");
								A=new int[blockSize/2+1];
								B=new int[blockSize/2+1];
								break;
							}
							else if(A[index_A]==0&&B[index_B]==1){
								//Log.i(TAG,"restore A:"+index_A);
								index_A--;
								index_B--;
								//restore A
								byte[] A_buffer=new byte[MTU];
								//make the header
								A_buffer[0]=(byte) Integer.parseInt("10000000",2);
								//restore the payload type
								A_buffer[1]=(byte) (buffer[1+i*MTU]^buffer[1+(index_B+blockSize/2)*MTU]);
								//genreate seq number
								int seq=blockNo*blockSize+index_A;
								//Log.i(TAG,"restored seq:"+seq);
								A_buffer[2]=(byte) (seq>>8);
								A_buffer[3]=(byte) (seq&0xFF);
								//restore the timeStamp
								A_buffer[4]=(byte) (buffer[4+i*MTU]^buffer[4+(index_B+blockSize/2)*MTU]);
								A_buffer[5]=(byte) (buffer[5+i*MTU]^buffer[5+(index_B+blockSize/2)*MTU]);
								A_buffer[6]=(byte) (buffer[6+i*MTU]^buffer[6+(index_B+blockSize/2)*MTU]);
								A_buffer[7]=(byte) (buffer[7+i*MTU]^buffer[7+(index_B+blockSize/2)*MTU]);
								//restore the SSRC
								A_buffer[8]=(byte) (buffer[8+i*MTU]^buffer[8+(index_B+blockSize/2)*MTU]);
								A_buffer[9]=(byte) (buffer[9+i*MTU]^buffer[9+(index_B+blockSize/2)*MTU]);
								A_buffer[10]=(byte) (buffer[10+i*MTU]^buffer[10+(index_B+blockSize/2)*MTU]);
								A_buffer[11]=(byte) (buffer[11+i*MTU]^buffer[11+(index_B+blockSize/2)*MTU]);
								
								//copy the EC header
								A_buffer[12]=(byte) (buffer[12+i*MTU]);
								A_buffer[13]=(byte) (buffer[13+i*MTU]);
								A_buffer[14]=(byte) (buffer[14+i*MTU]);
								A_buffer[15]=(byte) (buffer[15+i*MTU]);
								A_buffer[16]=(byte) (buffer[16+i*MTU]);
								A_buffer[17]=(byte) (buffer[17+i*MTU]);
								A_buffer[18]=(byte) (index_A);
								A_buffer[19]=(byte) (0);
								
								//restore the original size:
								A_buffer[20]=(byte) (buffer[20+i*MTU]^buffer[20+(index_B+blockSize/2)*MTU]);
								A_buffer[21]=(byte) (buffer[21+i*MTU]^buffer[21+(index_B+blockSize/2)*MTU]);
								//Log.i(TAG,"C header:"+AbstractPacketizer.toHexString(buffer,i*MTU,packetHeader));
								//Log.i(TAG,"B header:"+AbstractPacketizer.toHexString(buffer,(index_B+blockSize/2)*MTU,packetHeader));
								//Log.i(TAG,"restore header: "+AbstractPacketizer.toHexString(A_buffer,0,packetHeader));
								
								//restore the body
								for(int j=packetHeader;j<MTU;j++){
									A_buffer[j]=(byte) (buffer[j+i*MTU]^buffer[j+(index_B+blockSize/2)*MTU]);
								}
								System.arraycopy(A_buffer,0,dataToSend,index_A*MTU,MTU);
							}
							else if(A[index_A]==1&&B[index_B]==0){
								//Log.i(TAG,"restore B:"+index_B);
								index_A--;
								index_B--;
								//restore B
								byte[] B_buffer=new byte[MTU];
								//make the header
								B_buffer[0] =(byte) Integer.parseInt("10000000",2);
								//restore the payload type
								B_buffer[1] =(byte) (buffer[1+i*MTU]^buffer[1+index_A*MTU]);
								//genreate seq number
								int seq=blockNo*blockSize+index_B+blockSize/2;
								B_buffer[2] =(byte) (seq>>8);
								B_buffer[3] =(byte) (seq&0xFF);
								//restore the timeStamp
								B_buffer[4] =(byte) (buffer[4+i*MTU]^buffer[4+index_A*MTU]);
								B_buffer[5] =(byte) (buffer[5+i*MTU]^buffer[5+index_A*MTU]);
								B_buffer[6] =(byte) (buffer[6+i*MTU]^buffer[6+index_A*MTU]);
								B_buffer[7] =(byte) (buffer[7+i*MTU]^buffer[7+index_A*MTU]);
								//restore the SSRC
								B_buffer[8] =(byte) (buffer[8+i*MTU]^buffer[8+index_A*MTU]);
								B_buffer[9] =(byte) (buffer[9+i*MTU]^buffer[9+index_A*MTU]);
								B_buffer[10]=(byte) (buffer[10+i*MTU]^buffer[10+index_A*MTU]);
								B_buffer[11]=(byte) (buffer[11+i*MTU]^buffer[11+index_A*MTU]);
								
								//copy the EC header
								B_buffer[12]=(byte) (buffer[12+i*MTU]);
								B_buffer[13]=(byte) (buffer[13+i*MTU]);
								B_buffer[14]=(byte) (buffer[14+i*MTU]);
								B_buffer[15]=(byte) (buffer[15+i*MTU]);
								B_buffer[16]=(byte) (buffer[16+i*MTU]);
								B_buffer[17]=(byte) (buffer[17+i*MTU]);
								B_buffer[18]=(byte) (0);
								B_buffer[19]=(byte) (index_B);
								
								//restore the original size:
								B_buffer[20]=(byte) (buffer[20+i*MTU]^buffer[20+index_A*MTU]);
								B_buffer[21]=(byte) (buffer[21+i*MTU]^buffer[21+index_A*MTU]);
								
								//restore the body
								for(int j=packetHeader;j<MTU;j++){
									B_buffer[j]=(byte) (buffer[j+i*MTU]^buffer[j+index_A*MTU]);
								}
								System.arraycopy(B_buffer,0,dataToSend,(index_B+blockSize/2)*MTU,MTU);
							}
							
						}
						// this is an empty(lost) packet 
						else if(index_A==0&&index_B==0){
							//simply drop the empty one;
							totalLoss++;
							continue;
						}
					}
					m_Send.write(dataToSend);
					m_Send.flush();
					A=new int[blockSize/2+1];
					B=new int[blockSize/2+1];
					blockNo++;
					dataToSend =new byte[blockSize*MTU];
					sendProcess.open();
				}
			}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
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
	}
	
	//this thread used to send out RTP packets
	class Sender extends Thread implements Runnable{
		public Sender(){
			//this.start();
		}
		public void kill() {
			// TODO Auto-generated method stub
			try {
				this.join();

			} 
			catch (InterruptedException ignore) {
				
			} 
			mServer.close();
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
		private int seq=0;
		
		
		public void open(){
			if(isStart==false){
				Log.i(TAG,"Sender is now opening");
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
					//loss pockets, drop
					if(ipAdd[0]==0&&ipAdd[1]==0&&ipAdd[2]==0&&ipAdd[3]==0) {
						//skip to next data;
						lossCount++;
						Log.i(TAG,"Restore loss:"+lossCount+";Total loss:"+totalLoss+";Total Received"+totalReceive
								+";Restore loss percent:"+Float.toString((float)lossCount/(float)(totalReceive+totalLoss)*100)+"%"
								+";Total loss percent:"+Float.toString((float)totalLoss/(float)(totalReceive+totalLoss)*100)+"%");
						skip(ECRtpV2H264Packetizer.MAXPACKETSIZE-(RTP_HEADER_LENGTH+EC_HEADER_LENGTH));
						continue;
					}
					//smoth seq
					buffer[2] =(byte) (seq>>8);
					buffer[3] =(byte) (seq&0xFF);
					seq++;
					InetAddress orignalDestinationAdd = InetAddress.getByAddress(ipAdd);
					
					int orignalDestinationPort = buffer[16]<<8 | (buffer[17]&0xFF);
					
					//int seq=((buffer[2]&0xFF)<<8) | (buffer[3]&0xFF);
					
					//Log.i(TAG,"seq:"+seq);
					//Log.i(TAG,"orignal Address:"+orignalDestinationAdd+":"+orignalDestinationPort);
					//read orignal size
					int orignalPacketSize = ((buffer[20]&0xFF)<<8) | (buffer[21]&0xFF);
					//Log.i(TAG,"orignal size: "+orignalPacketSize);
					//read all data
					//Log.i(TAG,"orignal size:"+orignalPacketSize);
					fill(RTP_HEADER_LENGTH,orignalPacketSize-RTP_HEADER_LENGTH);
					//send them out!
					mForwardPacket=new DatagramPacket(buffer,orignalPacketSize);
					mForwardPacket.setAddress(orignalDestinationAdd);
					mForwardPacket.setPort(orignalDestinationPort);
					//Log.i(TAG,"sending "+AbstractPacketizer.toHexString(mForwardPacket.getData(),0,mForwardPacket.getLength()));
					mServer.send(mForwardPacket);
					
					
					//skip the left 0
					skip(ECRtpV2H264Packetizer.MAXPACKETSIZE-(orignalPacketSize+EC_HEADER_LENGTH));
					sleep(5);
					
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
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
