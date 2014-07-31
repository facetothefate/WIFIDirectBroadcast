/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.facework.core.streaming.transportPacketizer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Random;

import net.facework.core.streaming.misc.SocketTunnel;

import com.facework.configuration.ServerConf;

import android.util.Log;

/**
 * A basic implementation of an RTP socket.
 */
public class ECRtpSocket {
	public final static String TAG = "Rtp Socket";
	private MulticastSocket usock;
	private DatagramPacket upack;

	private byte[] buffer = new byte[MTU];
	private int seq = -1;
	private boolean upts = false;
	private int ssrc;
	private int port = -1;
	private InetAddress dest ;
	
	

	public static final int RTP_HEADER_LENGTH = 12;
	public static final int MTU = ServerConf.MTU;
	
	//////use for loss simulation//////
	public static int loss=0;
	
	//////use for EC group //////
	public static final int EC_HEADER_LENGTH = 10;
	public int index=0;
	public int blockSize = ServerConf.BLOCK_SIZE;
	public int addtionalPackets=ServerConf.ADDTIONAL_PACKETS_NUMBER;
	private boolean isEcMode = false;
	public void setEcMode(){isEcMode=true;}
	private byte[] cache = new byte[blockSize*MTU];
	private byte[] C_cache = new byte[addtionalPackets*MTU];
	
	private InetAddress originalAdd;
	private int originalPort;
	private InetAddress tunnelAdd;
	private int tunnelPort;
	
	private boolean lossOrSend(){
		// true ===>send 
		// false ===>loss
		Random rdm = new Random(System.nanoTime());
		if(loss==0){
			return true;
		}
		for(int i=1;i<=loss;i++)
		{
			if(Math.abs(rdm.nextInt()%100)==1)
				return false;
		}
		
		return true;
	}


	public ECRtpSocket() throws IOException {

		/*							     Version(2)  Padding(0)					 					*/
		/*									 ^		  ^			Extension(0)						*/
		/*									 |		  |				^								*/
		/*									 | --------				|								*/
		/*									 | |---------------------								*/
		/*									 | ||  -----------------------> Source Identifier(0)	*/
		/*									 | ||  |												*/
		buffer[0] = (byte) Integer.parseInt("10000000",2);

		/* Payload Type */
		buffer[1] = (byte) 96;

		/* Byte 2,3        ->  Sequence Number                   */
		/* Byte 4,5,6,7    ->  Timestamp                         */
		/* Byte 8,9,10,11  ->  Sync Source Identifier            */
		
		
		/////////////////////////////////////////////////////////////////////
		// Addtional information for the EC RTP
		// Byte 12,13,14,15 -> Tunnel orgianl Address
		// Byte 16,17 		-> Tunnel orginal Port
		// Byte	18,19		-> EC group and index
		// Byte 20,21,	-> The orignal Packet length;
		setLong((ssrc=(new Random()).nextInt()),8,12);

		usock = new MulticastSocket();
		usock.setSendBufferSize(10*1024*MTU);
		upack = new DatagramPacket(buffer, 1);

	}

	public void close() {
		usock.close();
	}

	public void setSSRC(int ssrc) {
		this.ssrc = ssrc; 
		setLong(ssrc,8,12);
	}

	public int getSSRC() {
		return ssrc;
	}

	public void setTimeToLive(int ttl) throws IOException {
		usock.setTimeToLive(ttl);
	}

	public void setDestination(InetAddress dest, int dport) {
		this.port = dport;
		this.dest = dest;
		upack.setPort(dport);
		upack.setAddress(dest);
		originalAdd=dest;
		originalPort=dport;
		if(ServerConf.TUNNEL_ON_PLAYER){
			this.tunnelAdd=dest;
			upack.setPort(SocketTunnel.DEFAULT_Video_Tunnel_PORT);
		}
	}

	/** Returns the buffer that you can directly modify before calling send. */
	public byte[] getBuffer() {
		return buffer;
	}

	public int getPort() {
		return port;
	}

	public int getLocalPort() {
		return usock.getLocalPort();
	}

	/** Sends the RTP packet over the network. */
	public void send(int length) throws IOException {
		
		updateSequence();
		if(!isEcMode){
			upack.setLength(length);
			//Log.i("RtpSocket",upack.getAddress().toString()+upack.getPort());
			usock.send(upack);
			//Log.i(TAG,"now is the "+port+" : "+seq+" packets");
			//Log.i(TAG,""+AbstractPacketizer.toHexString(buffer,0,length));
		}
		else {
			//make the group
			if(index<blockSize/2){
				buffer[18]=(byte) (index+1 & 0xff);
				buffer[19]=0;
			}
			else{
				buffer[18]=(byte) (0);
				buffer[19]=(byte) (index+1-blockSize/2);
			}
			//set the orignal size
			buffer[20]=(byte)(((length-EC_HEADER_LENGTH)>>8)&0xFF);
			buffer[21]=(byte)(length-EC_HEADER_LENGTH);
			
			//set the orginal address and port	
			System.arraycopy(this.originalAdd.getAddress(),0,buffer,12,4);
			buffer[16]=(byte)((this.originalPort>>8)&0xFF);
			buffer[17]=(byte)(this.originalPort&0xFF);
			//Log.i(TAG,"orginal port:"+ ((this.originalPort>>8)&0xFF));
			//store to send C out
			System.arraycopy(buffer,0,cache,index*MTU,length);
			upack=new DatagramPacket(buffer, MTU);
			upack.setPort(SocketTunnel.DEFAULT_Video_Tunnel_PORT);
			upack.setLength(MTU);
			upack.setAddress(dest);
			usock.send(upack);
			index++;
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//Log.i(TAG,"index:"+index);
			if(index==blockSize)
			{
				
				//Log.i(TAG,"orginal port:"+ this.originalPort);
				index=0;
				//fullfiled cache, generate C
				int total=0;
				for(int start=0; start<blockSize/2;start++){
					if(total==addtionalPackets){
						//Log.i(TAG,"skip"); 
						break;
					}
					for(int i=start,j=0;j<blockSize/2;i++,j++){
					//make the header
						if(i==blockSize/2) i=0;
						//Log.i(TAG,"start:"+start+",i:"+i+",j:"+j+",total:"+total+",addtionalPackets:"+addtionalPackets);
						
						C_cache[0 +(start*blockSize/2+j)*MTU]	=(byte) Integer.parseInt("10000000",2);
						//payload
						C_cache[1 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+1]^cache[(j+blockSize/2)*MTU+1]);
						//seq number
						++seq;
						//Log.d(TAG,"C packet seq:"+seq);
						C_cache[2 +(start*blockSize/2+j)*MTU]	=(byte) ((seq >> 8)&0xFF);
						C_cache[3 +(start*blockSize/2+j)*MTU]	=(byte) (seq&0xFF);
						//Log.d(TAG,"C_cache[2]:"+(int)C_cache[2+(start*blockSize/2+i)*MTU]+" ,C_cache[3]:"+(int)C_cache[2+(start*blockSize/3+i)*MTU]);
						//timeStamp
						C_cache[4 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+4]^cache[(j+blockSize/2)*MTU+4]);
						C_cache[5 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+5]^cache[(j+blockSize/2)*MTU+5]);
						C_cache[6 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+6]^cache[(j+blockSize/2)*MTU+6]);
						C_cache[7 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+7]^cache[(j+blockSize/2)*MTU+7]);
						//SSRC
						C_cache[8 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+8]^cache[(j+blockSize/2)*MTU+8]);
						C_cache[9 +(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+9]^cache[(j+blockSize/2)*MTU+9]);
						C_cache[10+(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+10]^cache[(j+blockSize/2)*MTU+10]);
						C_cache[11+(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+11]^cache[(j+blockSize/2)*MTU+11]);
						//EC header
						//IP tunnel
						System.arraycopy(this.originalAdd.getAddress(),0,C_cache,12+(start*blockSize/2+j)*MTU,4);
						C_cache[16+(start*blockSize/2+j)*MTU]	=(byte)(this.originalPort>>8);
						C_cache[17+(start*blockSize/2+j)*MTU]	=(byte)(this.originalPort);
						
						//Block group
						C_cache[18+(start*blockSize/2+j)*MTU]	=(byte) (i+1);
						C_cache[19+(start*blockSize/2+j)*MTU]	=(byte) (j+1);
						
						//orginal size
						C_cache[20+(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+20]^cache[(j+blockSize/2)*MTU+20]);
						C_cache[21+(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+21]^cache[(j+blockSize/2)*MTU+21]);
						
						//packet body
						for(int w=EC_HEADER_LENGTH+RTP_HEADER_LENGTH;w<MTU;w++){
							C_cache[w+(start*blockSize/2+j)*MTU]	=(byte) (cache[i*MTU+w]^cache[(j+blockSize/2)*MTU+w]);
						}
						upack=new DatagramPacket(C_cache,(start*blockSize/2+j)*MTU,MTU);
						upack.setPort(SocketTunnel.DEFAULT_Video_Tunnel_PORT);
						upack.setAddress(dest);
						upack.setLength(MTU);
						usock.send(upack);
						try {
							Thread.sleep(20);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						total++;
						if(total==addtionalPackets){
							//Log.i(TAG,"skip"); 
							break;
						}
					}
				}
				//send C out
				/*for(int i=0;i<addtionalPackets;i++){
					byte[] sendingByte= new byte[MTU];
					System.arraycopy(C_cache,i*MTU,sendingByte,0,MTU);
					upack=new DatagramPacket(sendingByte, MTU);
					upack.setPort(SocketTunnel.DEFAULT_Video_Tunnel_PORT);
					upack.setAddress(dest);
					usock.send(upack);
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}*/
				
			}
		}

		if (upts) {
			upts = false;
			buffer[1] -= 0x80;
		}

	}

	/** Increments the sequence number. */
	private void updateSequence() {
		setLong(++seq, 2, 4);
		//Log.i(TAG,"now is the "+port+" : "+seq+" packets");
	}

	/** 
	 * Overwrites the timestamp in the packet.
	 * @param timestamp The new timestamp
	 **/
	public void updateTimestamp(long timestamp) {
		setLong(timestamp, 4, 8);
	}

	public void markNextPacket() {
		upts = true;
		buffer[1] += 0x80; // Mark next packet
	}
	public void setType(byte type){
		buffer[1]=type;
	}
	public void setPayload(int payload){
		buffer[1] = (byte) (buffer[1]|(payload&0x1F));
	}
	private void setLong(long n, int begin, int end) {
		for (end--; end >= begin; end--) {
			buffer[end] = (byte) (n % 256);
			n >>= 8;
		}
	}
	
	/////////////////////////////////////////////////
	

}
