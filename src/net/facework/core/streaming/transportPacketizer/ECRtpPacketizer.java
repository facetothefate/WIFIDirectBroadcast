package net.facework.core.streaming.transportPacketizer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import android.util.Log;

import com.facework.configuration.ServerConf;

import net.facework.core.streaming.misc.SocketTunnel;

public class ECRtpPacketizer extends AbstractPacketizer implements Runnable{
	public final static String TAG = "ECRtpPacketizer";
	
	private Thread t = null;
	private final static int MTU = ServerConf.MTU-50;
	private final static int ECHeader = 7;
	
	private DatagramPacket upack;
	private MulticastSocket usock = new MulticastSocket();
	private InetAddress originalAdd;
	private int originalPort;
	private InetAddress tunnelAdd;
	private int tunnelPort;
	private byte[] buffer = new byte[MTU];
	
	int number=ServerConf.XOR_REGION;// how many packets xor half to half 
	int cacheLength=(MTU-ECHeader)*number;
	private byte[] cache =new byte[cacheLength];// half A half B
	private byte[] C_cache =new byte[cacheLength/2];// for C
	
	public ECRtpPacketizer() throws IOException {
		super();
		usock.setSendBufferSize(MTU*1000);
	}
	
	@Override
	public void setDestination(InetAddress dest, int dport) {
		this.originalAdd=dest;
		this.originalPort=dport;
		if(ServerConf.TUNNEL_ON_PLAYER){
			this.tunnelAdd=dest;
		}
		Log.d(TAG,"original address"+ originalAdd +":"+originalPort);
	}
	public void setTunnelChannel(String flag){
		if(flag=="video"){
			this.tunnelPort=SocketTunnel.DEFAULT_Video_Tunnel_PORT;
		}else if(flag=="audio"){
			this.tunnelPort=SocketTunnel.DEFAULT_Audio_Tunnel_PORT;
		}
	}

	@Override
	public void start() throws IOException {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}
	@Override
	public void stop() {
		try {
			is.close();
			//usock.close();
			Log.i(TAG,"EC-RTP Stopped!");
		} catch (IOException ignore) {}
		t.interrupt();
		// We wait until the packetizer thread returns
		try {
			t.join();
		} catch (InterruptedException e) {}
		t = null;
	}
	private int fill(int offset,int length) throws IOException {

		int sum = 0, len;

		while (sum<length) {
			len = is.read(cache, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream");
			}
			else sum+=len;
		}

		return sum;

	}
	@Override
	public void run() {
		try{
			//int i=1;
			
			
			if(ServerConf.TUNNEL){
				//set the tunnel address and port	
				System.arraycopy(this.originalAdd.getAddress(),0,buffer,0,4);
				buffer[4]=(byte)(this.originalPort>>8);
				buffer[5]=(byte)(this.originalPort);
			}
			while (!Thread.interrupted()) {
				
				// read in 10 packets' data
				int sum=fill(0,cacheLength);
				
				// now we have 10 packets' data we compute c = A(first 5)^B(last 5) 
				for(int i=0;i<(cacheLength/2);i++){
					C_cache[i]=(byte) (cache[i]^cache[cacheLength/2+i]);
				}
					/////////////////////////////////////////////////
					//send A B C A B C A B C  this is not great 
					// fast to send 
					// easy to lost all of them so we cannot recover the data
					// real lost always lost continuous packets
					// 
					/////////////////////////////////////////////
					/*for(int i=0;i<5;i++){
						if(ServerConf.TUNNEL){
							//set the tunnel address and port
							
							System.arraycopy(this.originalAdd.getAddress(),0,buffer,0,4);
							buffer[4]=(byte)(this.originalPort>>8);
							buffer[5]=(byte)(this.originalPort);
							
							//send A
							System.arraycopy(cache,i*cacheLength,buffer,ECHeader,cacheLength);
							upack = new DatagramPacket(buffer, MTU);
							//Log.i(TAG,"EC-RTP Data:"+toHexString(upack.getData(),6,MTU-6));
							upack.setLength(MTU);
							upack.setPort(tunnelPort);
							//Log.i(TAG,"tunnel port:"+ tunnelPort);
							upack.setAddress(tunnelAdd);
							//Log.i(TAG,"tunnel Address"+ tunnelAdd);
							usock.send(upack);
							
							//send B
							System.arraycopy(cache,(i+5)*cacheLength,buffer,ECHeader,cacheLength);
							upack = new DatagramPacket(buffer, MTU);
							//Log.i(TAG,"EC-RTP Data:"+toHexString(upack.getData(),6,MTU-6));
							upack.setLength(MTU);
							upack.setPort(tunnelPort);
							//Log.i(TAG,"tunnel port:"+ tunnelPort);
							upack.setAddress(tunnelAdd);
							//Log.i(TAG,"tunnel Address"+ tunnelAdd);
							usock.send(upack);
							
							//send C
							System.arraycopy(C_cache,i*cacheLength,buffer,ECHeader,cacheLength);
							upack = new DatagramPacket(buffer, MTU);
							//Log.i(TAG,"EC-RTP Data:"+toHexString(upack.getData(),6,MTU-6));
							upack.setLength(MTU);
							upack.setPort(tunnelPort);
							//Log.i(TAG,"tunnel port:"+ tunnelPort);
							upack.setAddress(tunnelAdd);
							//Log.i(TAG,"tunnel Address"+ tunnelAdd);
							usock.send(upack);
							
							// sleep for a while so that we won`t send a lot packets to lead error
							Thread.sleep(14);
						}
						
					}*/
					
					/***************************************************************************
					 *
					 * New way to send:
					 * 	AAAAA BBBBB CCCCC
					 * 
					 ***************************************************************************/
				
				//send ALL A out
				for(int i=0;i<number/2;i++){
					
					buffer[6]=(byte) (i& 0xff);
					//Log.i(TAG,"buffer[6]"+buffer[6]);
					//send A
					System.arraycopy(cache,i*(MTU-ECHeader),buffer,ECHeader,MTU-ECHeader);
					upack = new DatagramPacket(buffer, MTU);
					//Log.i(TAG,"EC-RTP Data:"+toHexString(upack.getData(),6,MTU-6));
					upack.setLength(MTU);
					upack.setPort(tunnelPort);
					//Log.i(TAG,"tunnel port:"+ tunnelPort);
					upack.setAddress(tunnelAdd);
					//Log.i(TAG,"tunnel Address"+ tunnelAdd);
					for(int j=0;j<1;j++)
						usock.send(upack);	
					//Thread.sleep(14);
				}
				Thread.sleep(10);
				//send ALL B out
				for(int i=number/2;i<number;i++){
					buffer[6]=(byte) ((i-number/2)& 0xff);
					buffer[6]=(byte) (buffer[6] | 0x40);
					//Log.i(TAG,"buffer[6]"+buffer[6]);
					//send B
					System.arraycopy(cache,i*(MTU-ECHeader),buffer,ECHeader,MTU-ECHeader);
					upack = new DatagramPacket(buffer, MTU);
					//Log.i(TAG,"EC-RTP Data:"+toHexString(upack.getData(),6,MTU-6));
					upack.setLength(MTU);
					upack.setPort(tunnelPort);
					//Log.i(TAG,"tunnel port:"+ tunnelPort);
					upack.setAddress(tunnelAdd);
					//Log.i(TAG,"tunnel Address"+ tunnelAdd);
					for(int j=0;j<1;j++)
						usock.send(upack);	
					//Thread.sleep(14);
				}
				Thread.sleep(10);
				//send ALL C out
				for(int i=0;i<number/2;i++){
					buffer[6]=(byte) (i& 0xff);
					buffer[6]=(byte) ((buffer[6] | 0x80)& 0xff);
					//Log.i(TAG,"buffer[6]"+buffer[6]);
					//send C
					System.arraycopy(cache,i*(MTU-ECHeader),buffer,ECHeader,MTU-ECHeader);
					upack = new DatagramPacket(buffer, MTU);
					//Log.i(TAG,"EC-RTP Data:"+toHexString(upack.getData(),6,MTU-6));
					upack.setLength(MTU);
					upack.setPort(tunnelPort);
					//Log.i(TAG,"tunnel port:"+ tunnelPort);
					upack.setAddress(tunnelAdd);
					//Log.i(TAG,"tunnel Address"+ tunnelAdd);
					for(int j=0;j<1;j++)
						usock.send(upack);	
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG,"IO error");
		}catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
			
		
	}

}
